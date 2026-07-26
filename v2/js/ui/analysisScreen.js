// Analysis screen (spec §4.4): watch the structure draw itself, then
// correct it — drag boundaries, click a section to retype, fix BPM octave.
"use strict";

import { $ } from "./app.js";
import { analyze } from "../core/analysis.js";
import { SECTION_COLORS, SECTION_LABELS, SECTION_TYPES } from "../core/palettes.js";
import { generateShow } from "../core/director.js";

export function initAnalysisScreen(app) {
  const canvas = $("an-canvas");
  const ctx = canvas.getContext("2d");
  let drag = null;   // { index } boundary being dragged

  app.on("audioFile", async file => {
    app.show("analysis");
    $("an-file").textContent = file.name;
    $("an-status").textContent = "Decoding…";
    $("an-generate").disabled = true;
    $("an-empty").disabled = true;
    app.analysis = null;
    draw();
    try {
      const ab = await file.arrayBuffer();
      app.audioBuffer = await app.playback.ctx.decodeAudioData(ab);
      app.playback.buffer = app.audioBuffer;
      app.analysis = await analyze(app.audioBuffer, m => { $("an-status").textContent = m; draw(); });
      $("an-status").textContent = `Analysis complete — ${app.analysis.events.kick.length} kicks, ${app.analysis.events.snare.length} snares, ${app.analysis.sections.length} sections`;
      $("an-generate").disabled = false;
      $("an-empty").disabled = false;
      updateStats();
      draw();
    } catch (err) {
      $("an-status").textContent = "Could not load that file: " + err.message;
      app.toast("Import failed: " + err.message);
    }
  });

  function updateStats() {
    const a = app.analysis;
    if (!a) return;
    $("an-bpm").textContent = a.bpm;
    $("an-dur").textContent = fmt(a.duration);
    $("an-secs").textContent = `${a.sections.length} sections`;
  }

  const fmt = t => `${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, "0")}`;

  // BPM octave fix: rescale bpm and rebuild grid at same phase
  function rescaleBPM(factor) {
    const a = app.analysis;
    if (!a) return;
    a.bpm = Math.round(a.bpm * factor * 10) / 10;
    const t0 = a.beats.length ? a.beats[0] : 0;
    const period = 60 / a.bpm;
    const beats = [];
    for (let t = t0; t < a.duration; t += period) beats.push(t);
    a.beats = Float64Array.from(beats);
    a.bars = Float64Array.from(beats.filter((_, i) => i % 4 === 0));
    updateStats();
    draw();
  }
  $("an-half").addEventListener("click", () => rescaleBPM(0.5));
  $("an-double").addEventListener("click", () => rescaleBPM(2));

  // ---- canvas drawing ----
  function draw() {
    const r = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = r.width * dpr; canvas.height = r.height * dpr;
    const w = canvas.width, h = canvas.height;
    ctx.clearRect(0, 0, w, h);
    const a = app.analysis;
    if (!a) {
      ctx.fillStyle = "#15151f";
      ctx.fillRect(0, 0, w, h);
      return;
    }
    const dur = a.duration;
    const X = t => t / dur * w;

    // section blocks
    for (const s of a.sections) {
      ctx.fillStyle = SECTION_COLORS[s.type] || "#444";
      ctx.globalAlpha = 0.4;
      ctx.fillRect(X(s.start), 0, X(s.end) - X(s.start), h);
    }
    ctx.globalAlpha = 1;

    // waveform
    ctx.fillStyle = "rgba(255,255,255,.55)";
    const n = a.waveform.length;
    for (let i = 0; i < n; i++) {
      const x = i / n * w, v = a.waveform[i] * h * 0.42;
      ctx.fillRect(x, h / 2 - v, Math.max(1, w / n - 0.5), v * 2);
    }

    // beat grid (thin) + bars (heavier) — only when zoomed enough to matter
    if (dur < 240) {
      ctx.fillStyle = "rgba(255,255,255,.14)";
      for (const b of a.beats) ctx.fillRect(X(b), h - 14, 1, 14);
      ctx.fillStyle = "rgba(255,255,255,.4)";
      for (const b of a.bars) ctx.fillRect(X(b), h - 22, 1, 22);
    }

    // energy curve
    ctx.strokeStyle = "rgba(200,255,0,.8)";
    ctx.lineWidth = 1.5 * dpr;
    ctx.beginPath();
    const E = a.curves.energy;
    for (let x = 0; x < w; x += 2) {
      const idx = Math.min(E.length - 1, Math.floor(x / w * E.length));
      const y = h - E[idx] * h * 0.9 - 3;
      x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();

    // boundaries + labels
    ctx.font = `${11 * dpr}px system-ui`;
    for (let i = 0; i < a.sections.length; i++) {
      const s = a.sections[i];
      ctx.fillStyle = "#fff";
      if (i > 0) ctx.fillRect(X(s.start) - 1, 0, 2, h);
      const bw = X(s.end) - X(s.start);
      if (bw > 40 * dpr) {
        ctx.fillStyle = "rgba(255,255,255,.9)";
        ctx.fillText(SECTION_LABELS[s.type] || s.type, X(s.start) + 5 * dpr, 14 * dpr);
      }
    }
  }
  window.addEventListener("resize", draw);
  app.on("screen", s => { if (s === "analysis") setTimeout(draw, 30); });

  // ---- interactions ----
  const posToTime = e => {
    const r = canvas.getBoundingClientRect();
    return (e.clientX - r.left) / r.width * (app.analysis ? app.analysis.duration : 1);
  };
  const nearBoundary = e => {
    const a = app.analysis;
    if (!a) return -1;
    const r = canvas.getBoundingClientRect();
    for (let i = 1; i < a.sections.length; i++) {
      const bx = a.sections[i].start / a.duration * r.width + r.left;
      if (Math.abs(e.clientX - bx) < 6) return i;
    }
    return -1;
  };

  canvas.addEventListener("pointermove", e => {
    if (drag) {
      const a = app.analysis;
      let t = posToTime(e);
      // snap to nearest beat
      let best = t, bd = 0.35;
      for (const b of a.beats) { const d = Math.abs(b - t); if (d < bd) { bd = d; best = b; } }
      t = best;
      const i = drag.index;
      const min = a.sections[i - 1].start + 1, max = a.sections[i].end - 1;
      t = Math.max(min, Math.min(max, t));
      a.sections[i - 1].end = t;
      a.sections[i].start = t;
      draw();
    } else {
      canvas.style.cursor = nearBoundary(e) >= 0 ? "col-resize" : "crosshair";
    }
  });
  canvas.addEventListener("pointerdown", e => {
    const b = nearBoundary(e);
    if (b >= 0) { drag = { index: b }; canvas.setPointerCapture(e.pointerId); }
  });
  canvas.addEventListener("pointerup", e => {
    if (drag) { drag = null; updateStats(); return; }
    // click: cycle the section's type
    const a = app.analysis;
    if (!a) return;
    const t = posToTime(e);
    const s = a.sections.find(x => t >= x.start && t < x.end);
    if (s) {
      s.type = SECTION_TYPES[(SECTION_TYPES.indexOf(s.type) + 1) % SECTION_TYPES.length];
      a.events.drop = a.sections.filter(x => x.type === "drop").map(x => ({ t: x.start, strength: 1 }));
      draw();
    }
  });

  // ---- exits ----
  $("an-generate").addEventListener("click", () => {
    generateShow(app.project, app.analysis);
    app.commit();
    app.show("editor");
    app.emit("openEditor");
  });
  $("an-empty").addEventListener("click", () => {
    app.commit();
    app.show("editor");
    app.emit("openEditor");
  });
}
