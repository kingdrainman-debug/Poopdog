// Minimal ISO-BMFF (MP4) muxer for WebCodecs output (spec §13).
// Supports: one H.264 (avc1) video track + optional AAC (mp4a) or Opus
// audio track. Layout: ftyp | mdat (video samples, then audio samples) |
// moov. One chunk per track keeps the sample tables trivial and valid.
"use strict";

// ---- byte writer ----
class W {
  constructor() { this.parts = []; this.len = 0; }
  bytes(b) { this.parts.push(b); this.len += b.length; return this; }
  u8(v) { return this.bytes(new Uint8Array([v & 0xff])); }
  u16(v) { return this.bytes(new Uint8Array([(v >> 8) & 0xff, v & 0xff])); }
  u24(v) { return this.bytes(new Uint8Array([(v >> 16) & 0xff, (v >> 8) & 0xff, v & 0xff])); }
  u32(v) { return this.bytes(new Uint8Array([(v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff])); }
  u64(v) { this.u32(Math.floor(v / 4294967296)); return this.u32(v >>> 0); }
  str(s) { return this.bytes(new TextEncoder().encode(s)); }
  out() {
    const buf = new Uint8Array(this.len);
    let o = 0;
    for (const p of this.parts) { buf.set(p, o); o += p.length; }
    return buf;
  }
}

function box(type, ...children) {
  const w = new W();
  for (const c of children) w.bytes(c instanceof Uint8Array ? c : c.out());
  const body = w.out();
  const full = new W();
  full.u32(8 + body.length).str(type).bytes(body);
  return full.out();
}
const fullBox = (type, version, flags, body) => {
  const w = new W();
  w.u8(version).u24(flags).bytes(body.out());
  return box(type, w.out());
};

// build stts (decode deltas) from per-sample durations in timescale units
function stts(durations) {
  const runs = [];
  for (const d of durations) {
    if (runs.length && runs[runs.length - 1].d === d) runs[runs.length - 1].n++;
    else runs.push({ d, n: 1 });
  }
  const b = new W();
  b.u32(runs.length);
  for (const r of runs) b.u32(r.n).u32(r.d);
  return fullBox("stts", 0, 0, b);
}

function stsz(sizes) {
  const b = new W();
  b.u32(0).u32(sizes.length);
  for (const s of sizes) b.u32(s);
  return fullBox("stsz", 0, 0, b);
}

function stblCommon(sampleEntry, durations, sizes, chunkOffset, keyframes) {
  const stsc = (() => { const b = new W(); b.u32(1).u32(1).u32(sizes.length).u32(1); return fullBox("stsc", 0, 0, b); })();
  const stco = (() => { const b = new W(); b.u32(1).u32(chunkOffset); return fullBox("stco", 0, 0, b); })();
  const stsd = (() => { const b = new W(); b.u32(1); b.bytes(sampleEntry); return fullBox("stsd", 0, 0, b); })();
  const children = [stsd, stts(durations), stsc, stsz(sizes), stco];
  if (keyframes && keyframes.length && keyframes.length < sizes.length) {
    const b = new W();
    b.u32(keyframes.length);
    for (const k of keyframes) b.u32(k + 1);   // 1-indexed
    children.push(fullBox("stss", 0, 0, b));
  }
  return box("stbl", ...children);
}

function tkhd(id, durationMvhd, w, h, isAudio) {
  const b = new W();
  b.u32(0).u32(0).u32(id).u32(0).u32(durationMvhd);
  b.u32(0).u32(0).u16(0).u16(0);
  b.u16(isAudio ? 0x0100 : 0).u16(0);
  // identity matrix
  b.u32(0x00010000).u32(0).u32(0).u32(0).u32(0x00010000).u32(0).u32(0).u32(0).u32(0x40000000);
  b.u32((w || 0) << 16).u32((h || 0) << 16);
  return fullBox("tkhd", 0, 3, b);
}

function mdhd(timescale, duration) {
  const b = new W();
  b.u32(0).u32(0).u32(timescale).u32(duration).u16(0x55c4).u16(0);
  return fullBox("mdhd", 0, 0, b);
}

function hdlr(type, name) {
  const b = new W();
  b.u32(0).str(type).u32(0).u32(0).u32(0).str(name).u8(0);
  return fullBox("hdlr", 0, 0, b);
}

const dinf = () => box("dinf", fullBox("dref", 0, 0, (() => {
  const b = new W();
  b.u32(1);
  b.bytes(fullBox("url ", 0, 1, new W()));
  return b;
})()));

function visualSampleEntryHeader(w, h) {
  const b = new W();
  b.bytes(new Uint8Array(6)).u16(1);                    // reserved + data_ref
  b.u16(0).u16(0).u32(0).u32(0).u32(0);                 // pre_defined/reserved
  b.u16(w).u16(h).u32(0x00480000).u32(0x00480000);      // 72 dpi
  b.u32(0).u16(1);                                      // frame_count
  b.bytes(new Uint8Array(32));                          // compressorname
  b.u16(0x0018).u16(0xffff);                            // depth, pre_defined
  return b.out();
}

function avc1Entry(w, h, avcC) {
  const entry = new W();
  entry.bytes(visualSampleEntryHeader(w, h)).bytes(box("avcC", new Uint8Array(avcC)));
  return box("avc1", entry.out());
}

function vp09Entry(w, h) {
  // vpcC: version 1, profile 0, level 41, 8-bit 4:2:0, BT.709
  const v = new W();
  v.u8(0).u8(41).u8((8 << 4) | (1 << 1) | 0);           // profile, level, depth|subsampling|fullrange
  v.u8(1).u8(1).u8(1);                                  // primaries, transfer, matrix
  v.u16(0);                                             // codecInitializationDataSize
  const entry = new W();
  entry.bytes(visualSampleEntryHeader(w, h)).bytes(fullBox("vpcC", 1, 0, v));
  return box("vp09", entry.out());
}

function esdsDescriptor(asc, avgBitrate) {
  const d = new W();
  d.u8(0x03).u8(23 + asc.length);                       // ES_Descriptor
  d.u16(1).u8(0);
  d.u8(0x04).u8(15 + asc.length);                       // DecoderConfig
  d.u8(0x40).u8(0x15).u24(0).u32(avgBitrate).u32(avgBitrate);
  d.u8(0x05).u8(asc.length).bytes(new Uint8Array(asc)); // DecSpecificInfo
  d.u8(0x06).u8(1).u8(0x02);                            // SLConfig
  const b = new W();
  b.u32(0).bytes(d.out());
  return box("esds", b.out());
}

function audioEntry(codec, channels, sampleRate, description, avgBitrate) {
  const b = new W();
  b.bytes(new Uint8Array(6)).u16(1);
  b.u32(0).u32(0);
  b.u16(channels).u16(16).u16(0).u16(0);
  b.u32(sampleRate << 16);
  const entry = new W();
  entry.bytes(b.out());
  if (codec === "aac") {
    entry.bytes(esdsDescriptor(description || [0x12, 0x10], avgBitrate));
    return box("mp4a", entry.out());
  }
  // Opus: dOps box (values big-endian, from OpusHead if provided)
  let preSkip = 3840, gain = 0;
  if (description && description.length >= 19) {         // OpusHead layout (LE)
    const d = new Uint8Array(description);
    preSkip = d[10] | (d[11] << 8);
    gain = d[16] | (d[17] << 8);
  }
  const dops = new W();
  dops.u8(0).u8(channels).u16(preSkip).u32(sampleRate).u16(gain).u8(0);
  entry.bytes(box("dOps", dops.out()));
  return box("Opus", entry.out());
}

// samples: [{ data: Uint8Array, duration (timescale units), key: bool }]
export function muxMP4({ video, audio }) {
  const videoBytes = video.samples.reduce((s, x) => s + x.data.length, 0);
  const audioBytes = audio ? audio.samples.reduce((s, x) => s + x.data.length, 0) : 0;

  const ftyp = (() => {
    const b = new W();
    b.str("isom").u32(512).str("isom").str("iso2").str("avc1").str("mp41");
    return box("ftyp", b.out());
  })();

  const mdatHeader = 8;
  const mdatStart = ftyp.length;
  const videoOffset = mdatStart + mdatHeader;
  const audioOffset = videoOffset + videoBytes;

  const mdat = new W();
  mdat.u32(mdatHeader + videoBytes + audioBytes).str("mdat");
  for (const s of video.samples) mdat.bytes(s.data);
  if (audio) for (const s of audio.samples) mdat.bytes(s.data);

  const vDur = video.samples.reduce((s, x) => s + x.duration, 0);
  const movieDurMs = Math.round(vDur / video.timescale * 1000);

  const mvhd = (() => {
    const b = new W();
    b.u32(0).u32(0).u32(1000).u32(movieDurMs);
    b.u32(0x00010000).u16(0x0100).u16(0).u32(0).u32(0);
    b.u32(0x00010000).u32(0).u32(0).u32(0).u32(0x00010000).u32(0).u32(0).u32(0).u32(0x40000000);
    b.bytes(new Uint8Array(24));    // pre_defined
    b.u32(audio ? 3 : 2);           // next_track_ID
    return fullBox("mvhd", 0, 0, b);
  })();

  const vTrak = box("trak",
    tkhd(1, movieDurMs, video.width, video.height, false),
    box("mdia",
      mdhd(video.timescale, vDur),
      hdlr("vide", "Poopdog Video"),
      box("minf",
        fullBox("vmhd", 0, 1, (() => { const b = new W(); b.u16(0).u16(0).u16(0).u16(0); return b; })()),
        dinf(),
        stblCommon(
          video.codec === "vp9" ? vp09Entry(video.width, video.height)
            : avc1Entry(video.width, video.height, video.avcC),
          video.samples.map(s => s.duration),
          video.samples.map(s => s.data.length),
          videoOffset,
          video.samples.map((s, i) => s.key ? i : -1).filter(i => i >= 0),
        ))));

  let aTrak = null;
  if (audio) {
    const aDur = audio.samples.reduce((s, x) => s + x.duration, 0);
    aTrak = box("trak",
      tkhd(2, movieDurMs, 0, 0, true),
      box("mdia",
        mdhd(audio.timescale, aDur),
        hdlr("soun", "Poopdog Audio"),
        box("minf",
          fullBox("smhd", 0, 0, (() => { const b = new W(); b.u16(0).u16(0); return b; })()),
          dinf(),
          stblCommon(
            audioEntry(audio.codec, audio.channels, audio.sampleRate, audio.description, audio.avgBitrate || 128000),
            audio.samples.map(s => s.duration),
            audio.samples.map(s => s.data.length),
            audioOffset,
            null,
          ))));
  }

  const moov = aTrak ? box("moov", mvhd, vTrak, aTrak) : box("moov", mvhd, vTrak);

  const out = new W();
  out.bytes(ftyp).bytes(mdat.out()).bytes(moov);
  return new Blob([out.out()], { type: "video/mp4" });
}
