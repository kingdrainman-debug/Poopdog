// Offline deterministic exporter (spec §13.4). Fixed-timestep re-render of
// the project through the same engine as preview — never a capture of
// playback. Video: WebCodecs H.264 -> minimal MP4 muxer. Audio: AAC when
// the platform encoder exists, else Opus-in-MP4.
"use strict";

import { muxMP4 } from "./mp4muxer.js";

function avcCodecString(w, h, fps) {
  // main profile; level by pixel rate
  const mbs = Math.ceil(w / 16) * Math.ceil(h / 16) * fps;
  if (mbs > 245760 || w >= 3000) return "avc1.640033";   // high 5.1 (4K)
  if (mbs > 108000) return "avc1.4d0029";                // main 4.1
  return "avc1.4d001f";                                  // main 3.1
}

export async function exportMP4({ engine, project, analysis, audioBuffer, width, height, fps, quality, durationS, onProgress, shouldCancel }) {
  if (typeof VideoEncoder === "undefined") {
    throw new Error("WebCodecs is not available — use the WebM fallback format.");
  }
  const totalFrames = Math.ceil(durationS * fps);
  const timescale = fps * 1000, frameDelta = 1000;

  const canvas = new OffscreenCanvas(width, height);
  const ctx = canvas.getContext("2d");

  // ---- video encoder ----
  const bitrate = Math.round((width * height * fps) / 30 * (0.06 + quality / 100 * 0.22));
  const videoSamples = [];
  let avcC = null;
  let encodeError = null;
  const encoder = new VideoEncoder({
    output: (chunk, meta) => {
      if (meta && meta.decoderConfig && meta.decoderConfig.description) {
        avcC = new Uint8Array(meta.decoderConfig.description);
      }
      const data = new Uint8Array(chunk.byteLength);
      chunk.copyTo(data);
      videoSamples.push({ data, duration: frameDelta, key: chunk.type === "key" });
    },
    error: e => { encodeError = e; },
  });
  // H.264 first (the §13 default); VP9-in-MP4 fallback for Chromium builds
  // without proprietary encoders. Codec is reported back to the UI.
  let videoCodec = "avc";
  let cfg = {
    codec: avcCodecString(width, height, fps), width, height, framerate: fps,
    bitrate, avc: { format: "avc" }, latencyMode: "quality",
  };
  let support = await VideoEncoder.isConfigSupported(cfg).catch(() => ({ supported: false }));
  if (!support.supported) {
    videoCodec = "vp9";
    cfg = { codec: "vp09.00.41.08", width, height, framerate: fps, bitrate, latencyMode: "quality" };
    support = await VideoEncoder.isConfigSupported(cfg).catch(() => ({ supported: false }));
  }
  if (!support.supported) throw new Error("No supported video encoder (tried H.264, VP9).");
  encoder.configure(cfg);

  // ---- render + encode loop (fixed timestep) ----
  const t0 = performance.now();
  for (let i = 0; i < totalFrames; i++) {
    if (shouldCancel && shouldCancel()) { encoder.close(); throw new Error("cancelled"); }
    if (encodeError) throw encodeError;
    const t = i / fps;
    engine.render(project, analysis, t, ctx, width, height);
    const frame = new VideoFrame(canvas, { timestamp: Math.round(i * 1e6 / fps) });
    encoder.encode(frame, { keyFrame: i % (fps * 2) === 0 });
    frame.close();
    if (encoder.encodeQueueSize > 6) {
      await new Promise(r => { encoder.addEventListener("dequeue", r, { once: true }); });
    }
    if (onProgress && (i & 7) === 0) {
      const elapsed = (performance.now() - t0) / 1000;
      onProgress({ frame: i, total: totalFrames, fps: i / Math.max(0.001, elapsed), stage: "video" });
    }
    if ((i & 31) === 0) await new Promise(r => setTimeout(r, 0));
  }
  await encoder.flush();
  encoder.close();
  if (encodeError) throw encodeError;

  // ---- audio ----
  let audioTrack = null;
  try {
    audioTrack = await encodeAudio(audioBuffer, durationS, onProgress);
  } catch (e) {
    console.warn("Audio encode failed, exporting video-only MP4:", e);
  }

  if (onProgress) onProgress({ frame: totalFrames, total: totalFrames, fps: 0, stage: "mux" });
  return {
    blob: muxMP4({
      video: { samples: videoSamples, timescale, width, height, avcC, codec: videoCodec },
      audio: audioTrack,
    }),
    videoCodec,
    audioCodec: audioTrack ? audioTrack.codec : "none",
  };
}

async function encodeAudio(audioBuffer, durationS, onProgress) {
  if (!audioBuffer || typeof AudioEncoder === "undefined") return null;

  // resample to 48k stereo so both AAC and Opus are happy
  const RATE = 48000, CH = 2;
  const frames = Math.min(Math.ceil(durationS * RATE), Math.ceil(audioBuffer.duration * RATE));
  const oac = new OfflineAudioContext(CH, frames, RATE);
  const src = oac.createBufferSource();
  src.buffer = audioBuffer;
  src.connect(oac.destination);
  src.start();
  const resampled = await oac.startRendering();

  // prefer AAC, fall back to Opus
  let codec = "aac", codecString = "mp4a.40.2";
  let sup = await AudioEncoder.isConfigSupported({ codec: codecString, sampleRate: RATE, numberOfChannels: CH, bitrate: 192000 })
    .catch(() => ({ supported: false }));
  if (!sup.supported) {
    codec = "opus"; codecString = "opus";
    sup = await AudioEncoder.isConfigSupported({ codec: "opus", sampleRate: RATE, numberOfChannels: CH, bitrate: 160000 })
      .catch(() => ({ supported: false }));
    if (!sup.supported) return null;
  }

  const samples = [];
  let description = null, err = null;
  const enc = new AudioEncoder({
    output: (chunk, meta) => {
      if (meta && meta.decoderConfig && meta.decoderConfig.description) {
        description = new Uint8Array(meta.decoderConfig.description);
      }
      const data = new Uint8Array(chunk.byteLength);
      chunk.copyTo(data);
      // duration comes back in µs; convert to timescale (= sample rate) units
      samples.push({ data, duration: Math.round((chunk.duration || 21333) * RATE / 1e6), key: true });
    },
    error: e => { err = e; },
  });
  enc.configure({ codec: codecString, sampleRate: RATE, numberOfChannels: CH, bitrate: codec === "aac" ? 192000 : 160000 });

  const CHUNK = 4800;   // 100ms per AudioData
  const planes = [resampled.getChannelData(0), resampled.getChannelData(1)];
  for (let off = 0; off < frames; off += CHUNK) {
    const n = Math.min(CHUNK, frames - off);
    const buf = new Float32Array(n * CH);           // f32-planar: plane 0 then plane 1
    buf.set(planes[0].subarray(off, off + n), 0);
    buf.set(planes[1].subarray(off, off + n), n);
    enc.encode(new AudioData({
      format: "f32-planar", sampleRate: RATE, numberOfFrames: n,
      numberOfChannels: CH, timestamp: Math.round(off * 1e6 / RATE), data: buf,
    }));
    if (err) throw err;
  }
  await enc.flush();
  enc.close();
  if (err) throw err;

  return { samples, timescale: RATE, sampleRate: RATE, channels: CH, codec, description, avgBitrate: codec === "aac" ? 192000 : 160000 };
}

// Legacy fallback (V1 path): realtime capture to WebM. Kept only for
// engines without WebCodecs; clearly labelled in the UI.
export function exportWebM({ canvas, audioEngine, onDone }) {
  const stream = new MediaStream([
    ...canvas.captureStream(60).getVideoTracks(),
    ...audioEngine.streamDest.stream.getAudioTracks(),
  ]);
  const mime = ["video/webm;codecs=vp9,opus", "video/webm"].find(m => MediaRecorder.isTypeSupported(m)) || "";
  const chunks = [];
  const rec = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 12_000_000 });
  rec.ondataavailable = e => { if (e.data.size) chunks.push(e.data); };
  rec.onstop = () => onDone(new Blob(chunks, { type: "video/webm" }));
  rec.start(250);
  return rec;
}
