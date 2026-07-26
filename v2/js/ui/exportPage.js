// Export page (spec §4.6, §13): MP4-first offline render with progress,
// ETA, cancel; WebM realtime capture as the labelled legacy fallback.
"use strict";

import { $ } from "./app.js";
import { exportMP4, exportWebM } from "../export/exporter.js";

const PRESETS = [
  { name: "YouTube 1080p60", res: "1920x1080", fps: 60, q: 75 },
  { name: "YouTube 4K30", res: "3840x2160", fps: 30, q: 80 },
  { name: "TikTok 9:16", res: "1080x1920", fps: 60, q: 70 },
  { name: "Instagram 1:1", res: "1080x1080", fps: 30, q: 70 },
];

export function initExport(app) {
  let cancelFlag = false, rendering = false, webmRec = null;

  const presetsEl = $("ex-presets");
  for (const p of PRESETS) {
    const b = document.createElement("button");
    b.className = "mini";
    b.textContent = p.name;
    b.onclick = () => {
      $("ex-res").value = p.res;
      $("ex-fps").value = String(p.fps);
      $("ex-q").value = p.q;
      $("ex-qv").textContent = p.q;
    };
    presetsEl.appendChild(b);
  }
  $("ex-q").addEventListener("input", e => { $("ex-qv").textContent = e.target.value; });

  app.on("openExport", () => {
    if (!app.project) return;
    const clips = app.project.tracks.reduce((s, tr) => s + tr.clips.length, 0);
    $("ex-summary").textContent =
      `${app.project.name} — ${fmt(app.playback.duration)} · ${app.project.tracks.length} tracks · ${clips} clips` +
      (app.analysis ? ` · ${app.analysis.bpm} BPM` : "");
    $("ex-note").textContent = typeof VideoEncoder === "undefined"
      ? "WebCodecs unavailable here — MP4 disabled, WebM fallback only." : "";
    $("ex-progress").hidden = true;
    $("scr-export").hidden = false;
  });

  $("ex-close").addEventListener("click", () => {
    if (rendering) { cancelFlag = true; if (webmRec) webmRec.stop(); }
    $("scr-export").hidden = true;
  });

  $("ex-start").addEventListener("click", async () => {
    if (rendering) { cancelFlag = true; if (webmRec) webmRec.stop(); return; }
    const [width, height] = $("ex-res").value.split("x").map(Number);
    const fps = parseInt($("ex-fps").value, 10);
    const quality = parseInt($("ex-q").value, 10);
    const range = $("ex-range").value;
    const durationS = range === "all" ? app.playback.duration : Math.min(app.playback.duration, parseFloat(range));
    const format = $("ex-format").value;

    cancelFlag = false;
    rendering = true;
    $("ex-start").textContent = "Cancel";
    $("ex-progress").hidden = false;

    try {
      if (format === "mp4") {
        const t0 = performance.now();
        const { blob, videoCodec, audioCodec } = await exportMP4({
          engine: app.engine, project: app.project, analysis: app.analysis,
          audioBuffer: app.audioBuffer, width, height, fps, quality, durationS,
          shouldCancel: () => cancelFlag,
          onProgress: p => {
            const pct = p.frame / p.total * 100;
            $("ex-bar-fill").style.width = pct + "%";
            const eta = p.fps > 0 ? (p.total - p.frame) / p.fps : 0;
            $("ex-stats").textContent = p.stage === "mux" ? "Muxing…"
              : `frame ${p.frame}/${p.total} · ${p.fps.toFixed(0)} fps · ETA ${fmt(eta)}`;
          },
        });
        const secs = ((performance.now() - t0) / 1000).toFixed(1);
        download(blob, `${app.project.name}_${height}p${fps}.mp4`);
        $("ex-stats").textContent =
          `Done in ${secs}s — ${(blob.size / 1e6).toFixed(1)} MB (${videoCodec === "avc" ? "H.264" : "VP9"} + ${audioCodec})`;
        $("ex-bar-fill").style.width = "100%";
        app.lastExport = blob;      // test hook
        app.toast(`Exported MP4 · ${(blob.size / 1e6).toFixed(1)} MB`);
      } else {
        await webmCapture(durationS);
      }
    } catch (err) {
      if (err.message !== "cancelled") {
        $("ex-stats").textContent = "Export failed: " + err.message;
        app.toast("Export failed: " + err.message, 5000);
      } else {
        $("ex-stats").textContent = "Cancelled";
      }
    }
    rendering = false;
    $("ex-start").textContent = "Render MP4";
  });

  // legacy realtime capture (V1 path)
  function webmCapture(durationS) {
    return new Promise(resolve => {
      const canvas = document.createElement("canvas");
      canvas.width = 1280; canvas.height = 720;
      const ctx = canvas.getContext("2d");
      app.playback.seek(0);
      app.playback.play();
      webmRec = exportWebM({
        canvas, audioEngine: app.playback,
        onDone: blob => {
          download(blob, `${app.project.name}.webm`);
          $("ex-stats").textContent = `WebM saved — ${(blob.size / 1e6).toFixed(1)} MB`;
          webmRec = null;
          resolve();
        },
      });
      const t0 = performance.now();
      (function loop() {
        const t = app.playback.t;
        app.engine.render(app.project, app.analysis, t, ctx, canvas.width, canvas.height);
        $("ex-bar-fill").style.width = (t / durationS * 100) + "%";
        $("ex-stats").textContent = `recording ${fmt(t)} / ${fmt(durationS)} (realtime)`;
        if (t < durationS && !cancelFlag && (performance.now() - t0) / 1000 < durationS + 5) {
          requestAnimationFrame(loop);
        } else {
          app.playback.pause();
          webmRec && webmRec.stop();
        }
      })();
    });
  }

  function download(blob, name) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = name; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }
  const fmt = t => `${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, "0")}`;
}
