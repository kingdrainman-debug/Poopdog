// Timeline editor + navigator (spec §9). Canvas-drawn: ruler with sections,
// track lanes, clips (move/trim/blade/duplicate/delete), beat-grid
// snapping, wheel zoom/scroll. The navigator is the whole-track V1 strip
// with a viewport rectangle.
"use strict";

import { $ } from "./app.js";
import { REGISTRY } from "../core/generators.js";
import { SECTION_COLORS } from "../core/palettes.js";
import { makeClip, sortTrack, findClip, removeClip } from "../core/project.js";

const RULER_H = 26, GUTTER = 118, TRACK_H = 34;
const TRACK_COLORS = { camera: "#9aa7ff", adjust: "#ff7ad0", visual: "#c8ff00", transition: "#ffffff" };

export function initTimeline(app) {
  const tl = $("tl-canvas"), nav = $("nav-canvas");
  const tctx = tl.getContext("2d"), nctx = nav.getContext("2d");
  let view = { t0: 0, pxPerSec: 8 };
  let drag = null;
  let hover = null;

  const dur = () => (app.analysis ? app.analysis.duration : app.playback.duration) || 60;
  const X = t => GUTTER + (t - view.t0) * view.pxPerSec * dpr();
  const T = x => view.t0 + (x - GUTTER) / (view.pxPerSec * dpr());
  const dpr = () => window.devicePixelRatio || 1;

  // ---------------- drawing ----------------
  function draw() {
    const r = tl.getBoundingClientRect();
    tl.width = r.width * dpr(); tl.height = r.height * dpr();
    const w = tl.width, h = tl.height, d = dpr();
    tctx.clearRect(0, 0, w, h);
    if (!app.project) return;
    const a = app.analysis;

    // ---- ruler: sections + bars ----
    if (a) {
      for (const s of a.sections) {
        tctx.fillStyle = SECTION_COLORS[s.type] || "#444";
        tctx.globalAlpha = 0.7;
        tctx.fillRect(X(s.start), 0, (s.end - s.start) * view.pxPerSec * d, RULER_H * d * 0.5);
      }
      tctx.globalAlpha = 1;
      const pxPerBeat = (60 / a.bpm) * view.pxPerSec;
      if (pxPerBeat > 4) {
        tctx.fillStyle = "rgba(255,255,255,.25)";
        for (const b of a.beats) {
          const x = X(b);
          if (x < GUTTER || x > w) continue;
          tctx.fillRect(x, RULER_H * d * 0.5, 1, RULER_H * d * 0.5);
        }
      }
      tctx.fillStyle = "rgba(255,255,255,.6)";
      for (const b of a.bars) {
        const x = X(b);
        if (x < GUTTER || x > w) continue;
        tctx.fillRect(x, RULER_H * d * 0.35, 1, RULER_H * d * 0.65);
      }
    }
    // time labels
    tctx.fillStyle = "#8888a0";
    tctx.font = `${9 * d}px system-ui`;
    const step = view.pxPerSec > 40 ? 5 : view.pxPerSec > 12 ? 15 : 60;
    for (let t = Math.ceil(view.t0 / step) * step; X(t) < w; t += step) {
      tctx.fillText(`${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, "0")}`, X(t) + 3, RULER_H * d - 4);
    }

    // ---- tracks ----
    app.project.tracks.forEach((tr, i) => {
      const y = (RULER_H + i * TRACK_H) * d;
      // lane
      tctx.fillStyle = i % 2 ? "#10101a" : "#0e0e16";
      tctx.fillRect(GUTTER, y, w - GUTTER, TRACK_H * d);
      // gutter
      tctx.fillStyle = "#15151f";
      tctx.fillRect(0, y, GUTTER, TRACK_H * d);
      tctx.fillStyle = TRACK_COLORS[tr.type] || "#888";
      tctx.fillRect(0, y, 3 * d, TRACK_H * d);
      tctx.fillStyle = tr.mute ? "#555" : "#c8c8d8";
      tctx.font = `${10 * d}px system-ui`;
      tctx.fillText(tr.name, 8 * d, y + 14 * d);
      // M / S toggles
      tctx.font = `${9 * d}px system-ui`;
      tctx.fillStyle = tr.mute ? "#ff6060" : "#555";
      tctx.fillText("M", 8 * d, y + 27 * d);
      tctx.fillStyle = tr.solo ? "#c8ff00" : "#555";
      tctx.fillText("S", 22 * d, y + 27 * d);

      // clips
      for (const c of tr.clips) {
        const x0 = X(c.start), x1 = X(c.end);
        if (x1 < GUTTER || x0 > w) continue;
        const cw = Math.max(3, x1 - x0);
        const gen = REGISTRY.get(c.gen);
        const col = TRACK_COLORS[tr.type] || "#888";
        const sel = app.selection === c.id;
        tctx.fillStyle = sel ? col : col + "55";
        roundRect(tctx, Math.max(GUTTER, x0), y + 3 * d, cw - (Math.max(GUTTER, x0) - x0), (TRACK_H - 6) * d, 4 * d);
        tctx.fill();
        if (sel) { tctx.strokeStyle = "#fff"; tctx.lineWidth = 1.5 * d; tctx.stroke(); }
        if (cw > 34 * d) {
          tctx.fillStyle = sel ? "#000" : "#e8e8f0";
          tctx.font = `${9.5 * d}px system-ui`;
          const label = (c.auto ? "⚡" : "") + (gen ? gen.name : c.gen);
          tctx.save();
          tctx.beginPath();
          tctx.rect(Math.max(GUTTER, x0), y, cw, TRACK_H * d);
          tctx.clip();
          tctx.fillText(label, Math.max(GUTTER, x0) + 5 * d, y + 16 * d);
          tctx.restore();
        }
      }
    });

    // playhead
    const px = X(app.playback.t);
    if (px >= GUTTER) {
      tctx.fillStyle = "#fff";
      tctx.fillRect(px, 0, Math.max(1, 1.5 * d), h);
    }
    drawNav();
  }

  function roundRect(c, x, y, w, h, r) {
    r = Math.min(r, w / 2, h / 2);
    c.beginPath();
    c.moveTo(x + r, y);
    c.arcTo(x + w, y, x + w, y + h, r);
    c.arcTo(x + w, y + h, x, y + h, r);
    c.arcTo(x, y + h, x, y, r);
    c.arcTo(x, y, x + w, y, r);
    c.closePath();
  }

  // ---- navigator (kept V1 strip, §9.2) ----
  function drawNav() {
    const r = nav.getBoundingClientRect();
    nav.width = r.width * dpr(); nav.height = r.height * dpr();
    const w = nav.width, h = nav.height;
    nctx.clearRect(0, 0, w, h);
    const a = app.analysis;
    if (!a) return;
    const D = dur();
    for (const s of a.sections) {
      nctx.fillStyle = SECTION_COLORS[s.type] || "#444";
      nctx.globalAlpha = 0.55;
      nctx.fillRect(s.start / D * w, 0, (s.end - s.start) / D * w, h);
    }
    nctx.globalAlpha = 1;
    nctx.strokeStyle = "rgba(255,255,255,.85)";
    nctx.lineWidth = dpr();
    nctx.beginPath();
    const E = a.curves.energy;
    for (let x = 0; x < w; x += 2) {
      const y = h - E[Math.min(E.length - 1, Math.floor(x / w * E.length))] * (h - 4) - 2;
      x === 0 ? nctx.moveTo(x, y) : nctx.lineTo(x, y);
    }
    nctx.stroke();
    // viewport rect
    const vw = tl.getBoundingClientRect().width;
    const visS = (vw * dpr() - GUTTER) / (view.pxPerSec * dpr());
    nctx.strokeStyle = "#c8ff00";
    nctx.lineWidth = dpr();
    nctx.strokeRect(view.t0 / D * w, 1, visS / D * w, h - 2);
    // playhead
    nctx.fillStyle = "#fff";
    nctx.fillRect(app.playback.t / D * w, 0, dpr(), h);
  }

  nav.addEventListener("pointerdown", e => {
    const r = nav.getBoundingClientRect();
    const t = (e.clientX - r.left) / r.width * dur();
    app.playback.seek(t);
    centerOn(t);
  });

  function centerOn(t) {
    const vw = (tl.getBoundingClientRect().width * dpr() - GUTTER) / (view.pxPerSec * dpr());
    view.t0 = Math.max(0, t - vw / 2);
  }

  // ---------------- snapping (spec §9.5) ----------------
  function snapPoints(excludeClipId) {
    const pts = [];
    const a = app.analysis;
    if (a) {
      for (const s of a.sections) { pts.push(s.start); pts.push(s.end); }
      for (const b of a.bars) pts.push(b);
      const pxPerBeat = (60 / a.bpm) * view.pxPerSec;
      if (pxPerBeat > 6) for (const b of a.beats) pts.push(b);
    }
    for (const tr of app.project.tracks)
      for (const c of tr.clips) {
        if (c.id === excludeClipId) continue;
        pts.push(c.start); pts.push(c.end);
      }
    return pts;
  }
  function snap(t, pts) {
    if (!$("ed-snap").checked) return t;
    const thresh = 8 / view.pxPerSec;
    let best = t, bd = thresh;
    for (const p of pts) { const d = Math.abs(p - t); if (d < bd) { bd = d; best = p; } }
    return best;
  }

  // ---------------- hit testing ----------------
  function hit(e) {
    const r = tl.getBoundingClientRect();
    const x = (e.clientX - r.left) * dpr(), y = (e.clientY - r.top) * dpr();
    if (y < RULER_H * dpr()) return { zone: "ruler", t: T(x) };
    const ti = Math.floor((y / dpr() - RULER_H) / TRACK_H);
    const track = app.project.tracks[ti];
    if (!track) return { zone: "empty", t: T(x) };
    if (x < GUTTER) {
      const ly = y / dpr() - RULER_H - ti * TRACK_H;
      if (ly > 18) return { zone: e.clientX - r.left < 18 ? "mute" : "solo", track };
      return { zone: "gutter", track };
    }
    const t = T(x);
    for (const c of [...track.clips].reverse()) {
      if (t >= c.start && t <= c.end) {
        const x0 = X(c.start), x1 = X(c.end);
        const edge = 6 * dpr();
        if (x - x0 < edge) return { zone: "trimL", track, clip: c, t };
        if (x1 - x < edge) return { zone: "trimR", track, clip: c, t };
        return { zone: "clip", track, clip: c, t };
      }
    }
    return { zone: "lane", track, trackIndex: ti, t };
  }

  // ---------------- pointer interactions ----------------
  tl.addEventListener("pointerdown", e => {
    if (!app.project) return;
    const h = hit(e);
    tl.setPointerCapture(e.pointerId);
    if (h.zone === "ruler" || h.zone === "empty" || h.zone === "lane") {
      app.playback.seek(Math.max(0, h.t));
      if (h.zone !== "ruler") { app.selection = null; app.emit("selectionChanged"); }
      drag = { kind: "scrub" };
    } else if (h.zone === "mute") { h.track.mute = !h.track.mute; app.commit(); }
    else if (h.zone === "solo") { h.track.solo = !h.track.solo; app.commit(); }
    else if (h.zone === "clip") {
      app.selection = h.clip.id;
      app.emit("selectionChanged");
      drag = { kind: "move", clip: h.clip, track: h.track, grabDt: h.t - h.clip.start, len: h.clip.end - h.clip.start, pts: snapPoints(h.clip.id), moved: false };
    } else if (h.zone === "trimL" || h.zone === "trimR") {
      app.selection = h.clip.id;
      app.emit("selectionChanged");
      drag = { kind: h.zone, clip: h.clip, pts: snapPoints(h.clip.id) };
    }
    draw();
  });

  tl.addEventListener("pointermove", e => {
    if (!app.project) return;
    if (!drag) {
      const h = hit(e);
      tl.style.cursor = h.zone === "trimL" || h.zone === "trimR" ? "col-resize"
        : h.zone === "clip" ? "grab" : h.zone.startsWith("gut") || h.zone === "mute" || h.zone === "solo" ? "pointer" : "default";
      return;
    }
    const h = hit(e);
    if (drag.kind === "scrub") { app.playback.seek(Math.max(0, h.t ?? 0)); return; }
    const c = drag.clip;
    if (drag.kind === "move") {
      let ns = snap(h.t - drag.grabDt, drag.pts);
      // also snap the clip END edge
      const endSnap = snap(ns + drag.len, drag.pts);
      if (Math.abs(endSnap - (ns + drag.len)) < 1e-9) { /* no end snap */ } else ns = endSnap - drag.len;
      ns = Math.max(0, ns);
      if (Math.abs(ns - c.start) > 1e-6) { drag.moved = true; }
      c.start = ns; c.end = ns + drag.len;
      // vertical move between same-type tracks
      if (h.track && h.track !== drag.track && h.track.type === drag.track.type && !h.track.lock) {
        removeClip(app.project, c.id);
        h.track.clips.push(c);
        sortTrack(h.track);
        drag.track = h.track;
      }
    } else if (drag.kind === "trimL") {
      c.start = Math.min(snap(h.t, drag.pts), c.end - 0.1);
      c.start = Math.max(0, c.start);
      drag.moved = true;
    } else if (drag.kind === "trimR") {
      c.end = Math.max(snap(h.t, drag.pts), c.start + 0.1);
      drag.moved = true;
    }
    draw();
  });

  tl.addEventListener("pointerup", () => {
    if (drag && drag.clip && drag.moved) {
      drag.clip.auto = false;         // user edit clears the Director badge (§9.8)
      sortTrack(drag.track || findClip(app.project, drag.clip.id).track);
      app.commit();
    }
    drag = null;
  });

  // wheel: scroll / zoom (ctrl or pinch)
  $("ed-timeline").addEventListener("wheel", e => {
    e.preventDefault();
    if (e.ctrlKey || e.metaKey) {
      const r = tl.getBoundingClientRect();
      const pivot = T((e.clientX - r.left) * dpr());
      const f = Math.exp(-e.deltaY * 0.002);
      view.pxPerSec = Math.max(1, Math.min(200, view.pxPerSec * f));
      view.t0 = Math.max(0, pivot - ((e.clientX - r.left) * dpr() - GUTTER) / (view.pxPerSec * dpr()));
    } else {
      view.t0 = Math.max(0, view.t0 + (e.deltaY + e.deltaX) / view.pxPerSec * 0.5);
    }
    draw();
  }, { passive: false });

  // ---- drag & drop from asset browser (spec §15.3) ----
  tl.addEventListener("dragover", e => {
    if (e.dataTransfer.types.includes("text/poopdog-gen")) e.preventDefault();
  });
  tl.addEventListener("drop", e => {
    const genId = e.dataTransfer.getData("text/poopdog-gen");
    const gen = REGISTRY.get(genId);
    if (!gen) return;
    e.preventDefault();
    const h = hit(e);
    let track = h.track;
    if (!track || track.type !== gen.track) {
      track = app.project.tracks.find(tr => tr.type === gen.track && !tr.lock);
    }
    if (!track) return;
    const t = snap(Math.max(0, h.t ?? app.playback.t), snapPoints());
    const a = app.analysis;
    const sec = a ? a.sections.find(s => t >= s.start && t < s.end) : null;
    const end = sec ? sec.end : Math.min(dur(), t + 8);
    const clip = makeClip(genId, t, end, {}, { seed: Math.floor(Math.random() * 9999) });
    track.clips.push(clip);
    sortTrack(track);
    app.selection = clip.id;
    app.commit();
    app.emit("selectionChanged");
  });

  // ---- clip-level keys ----
  app.on("key", e => {
    const sel = app.selection && findClip(app.project, app.selection);
    const mod = e.ctrlKey || e.metaKey;
    if (!sel) return;
    if (e.key === "Delete" || e.key === "Backspace") {
      removeClip(app.project, sel.clip.id);
      app.selection = null;
      app.commit();
      app.emit("selectionChanged");
    } else if (mod && e.key.toLowerCase() === "d") {
      e.preventDefault();
      const c = sel.clip, len = c.end - c.start;
      const copy = makeClip(c.gen, c.end, c.end + len, { ...c.params },
        { mods: JSON.parse(JSON.stringify(c.mods)), mode: c.mode, seed: c.seed + 1, fadeIn: c.fadeIn, fadeOut: c.fadeOut });
      sel.track.clips.push(copy);
      sortTrack(sel.track);
      app.selection = copy.id;
      app.commit();
      app.emit("selectionChanged");
    } else if (e.key.toLowerCase() === "b") {
      // blade at playhead (spec §9.6)
      const t = app.playback.t, c = sel.clip;
      if (t > c.start + 0.1 && t < c.end - 0.1) {
        const right = makeClip(c.gen, t, c.end, { ...c.params },
          { mods: JSON.parse(JSON.stringify(c.mods)), mode: c.mode, seed: c.seed, fadeOut: c.fadeOut });
        c.end = t; c.fadeOut = 0; c.auto = false;
        sel.track.clips.push(right);
        sortTrack(sel.track);
        app.commit();
      }
    }
  });

  // redraw on state changes + every frame (playhead)
  app.on("projectChanged", draw);
  app.on("selectionChanged", draw);
  app.on("frame", () => { if (!$("scr-editor").hidden) draw(); });
  app.on("openEditor", () => { view = { t0: 0, pxPerSec: Math.max(2, (tl.getBoundingClientRect().width - 130) / dur()) }; draw(); });
  window.addEventListener("resize", draw);
}
