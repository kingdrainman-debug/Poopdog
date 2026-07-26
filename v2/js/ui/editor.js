// Editor screen (spec §4.5): preview loop, transport, asset browser,
// generate button, keyboard shortcuts. Timeline + inspector live in their
// own modules.
"use strict";

import { $ } from "./app.js";
import { REGISTRY, CATEGORIES, byCategory } from "../core/generators.js";
import { sectionAt } from "../core/analysis.js";
import { SECTION_LABELS } from "../core/palettes.js";
import { makeClip, sortTrack, undo as undoOp, redo as redoOp } from "../core/project.js";
import { generateShow } from "../core/director.js";
import { initTimeline } from "./timeline.js";
import { initInspector } from "./inspector.js";

const CAT_COLORS = {
  Lasers: "#ff2d95", Tunnels: "#00d0ff", Geometry: "#c8ff00", Particles: "#ffb400",
  Backgrounds: "#7a4fd0", Spectrum: "#00ffa8", Colour: "#ff7ad0", FX: "#ff5540",
  Transitions: "#ffffff", Camera: "#9aa7ff",
};

export function initEditor(app) {
  const preview = $("ed-preview");
  const pvCtx = preview.getContext("2d");
  let quality = 1;             // 1 full, 0.5 half, 0.25 quarter
  let autoQuality = true;
  let fpsSamples = [];

  initTimeline(app);
  initInspector(app);

  // ---- asset browser ----
  function renderAssets(filter = "") {
    const list = $("as-list");
    list.innerHTML = "";
    const f = filter.trim().toLowerCase();
    for (const cat of CATEGORIES) {
      const items = byCategory(cat).filter(g => !f || g.name.toLowerCase().includes(f) || cat.toLowerCase().includes(f));
      if (!items.length) continue;
      const head = document.createElement("div");
      head.className = "as-cat";
      head.textContent = cat;
      list.appendChild(head);
      for (const gen of items) {
        const el = document.createElement("div");
        el.className = "as-item";
        el.draggable = true;
        el.innerHTML = `<span class="sw" style="background:${CAT_COLORS[cat] || "#666"}"></span>${gen.name}`;
        el.title = "Drag to timeline, or double-click to add at playhead";
        el.addEventListener("dragstart", e => e.dataTransfer.setData("text/poopdog-gen", gen.id));
        el.addEventListener("dblclick", () => addAtPlayhead(gen.id));
        list.appendChild(el);
      }
    }
  }
  $("as-search").addEventListener("input", e => renderAssets(e.target.value));
  renderAssets();

  // add a clip filling the current section on the right track (spec §15.3)
  function addAtPlayhead(genId) {
    const gen = REGISTRY.get(genId);
    if (!gen || !app.project) return;
    const t = app.playback.t;
    const sec = app.analysis ? sectionAt(app.analysis.sections, t) : null;
    const start = sec ? Math.max(sec.start, t) : t;
    const end = sec ? sec.end : Math.min(app.playback.duration || start + 8, start + 8);
    const track = app.project.tracks.find(tr => tr.type === gen.track && !tr.lock)
      || app.project.tracks.find(tr => tr.type === "visual");
    if (!track) return;
    const clip = makeClip(genId, start, end, {}, { seed: Math.floor(Math.random() * 9999) });
    track.clips.push(clip);
    sortTrack(track);
    app.selection = clip.id;
    app.commit();
    app.toast(`${gen.name} → ${track.name}`);
  }
  app.addAtPlayhead = addAtPlayhead;

  // ---- transport ----
  const fmt = t => `${Math.floor(t / 60)}:${(t % 60).toFixed(2).padStart(5, "0")}`;
  function setPlayIcon() { $("tr-play").textContent = app.playback.playing ? "⏸" : "▶"; }
  $("tr-play").addEventListener("click", () => {
    app.playback.playing ? app.playback.pause() : app.playback.play();
    setPlayIcon();
  });
  $("tr-start").addEventListener("click", () => app.playback.seek(0));
  app.playback.onEnded = setPlayIcon;

  $("ed-home").addEventListener("click", () => { app.playback.pause(); setPlayIcon(); app.show("home"); });

  // ---- generate / seed ----
  $("ed-regen").addEventListener("click", () => {
    if (!app.analysis) return app.toast("No analysis — reimport audio");
    app.project.seed = parseInt($("ed-seed").value, 10) || 1;
    generateShow(app.project, app.analysis);
    app.commit();
    app.toast("Show regenerated (user-edited clips kept)");
  });
  $("ed-seed").addEventListener("change", () => {
    app.project.seed = parseInt($("ed-seed").value, 10) || 1;
  });

  $("ed-export").addEventListener("click", () => app.emit("openExport"));

  // ---- preview quality ----
  $("pv-quality").addEventListener("click", () => {
    autoQuality = false;
    quality = quality === 1 ? 0.5 : quality === 0.5 ? 0.25 : 1;
    $("pv-quality").textContent = quality === 1 ? "Full" : quality === 0.5 ? "Half" : "Quarter";
  });
  $("pv-full").addEventListener("click", () => {
    (document.fullscreenElement ? document.exitFullscreen() : $("ed-preview-wrap").requestFullscreen()).catch(() => {});
  });

  app.on("openEditor", () => {
    $("tr-bpm").textContent = app.analysis ? `${app.analysis.bpm} BPM` : "";
    $("ed-seed").value = app.project.seed;
    app.playback.seek(0);
    app.playback.play();
    setPlayIcon();
  });

  // ---- render loop ----
  let lastFrame = performance.now();
  function frame() {
    requestAnimationFrame(frame);
    if ($("scr-editor").hidden || !app.project) return;

    const now = performance.now();
    const dt = now - lastFrame;
    lastFrame = now;
    fpsSamples.push(1000 / Math.max(1, dt));
    if (fpsSamples.length > 30) fpsSamples.shift();
    const fps = fpsSamples.reduce((a, b) => a + b, 0) / fpsSamples.length;

    // adaptive preview resolution (spec §14.3)
    if (autoQuality) {
      if (fps < 38 && quality > 0.25) { quality = quality === 1 ? 0.5 : 0.25; fpsSamples = []; }
      else if (fps > 57 && quality < 1) { quality = quality === 0.25 ? 0.5 : 1; fpsSamples = []; }
      $("pv-quality").textContent = quality === 1 ? "Full" : quality === 0.5 ? "Half" : "Quarter";
    }
    const W = Math.round(1280 * quality), H = Math.round(720 * quality);
    if (preview.width !== W) { preview.width = W; preview.height = H; }

    const t = app.playback.t;
    app.engine.render(app.project, app.analysis, t, pvCtx, W, H);

    // HUD
    const fpsEl = $("pv-fps");
    fpsEl.textContent = fps.toFixed(0);
    fpsEl.style.color = fps >= 55 ? "#7dff8a" : fps >= 40 ? "#ffcf40" : "#ff6060";
    $("tr-time").textContent = fmt(t);
    if (app.analysis) {
      const sec = sectionAt(app.analysis.sections, t);
      $("pv-section").textContent = sec ? (SECTION_LABELS[sec.type] || sec.type) : "—";
      // beat dot
      const beats = app.analysis.beats;
      let lo = 0, hi = beats.length;
      while (lo < hi) { const m = (lo + hi) >> 1; (beats[m] <= t) ? lo = m + 1 : hi = m; }
      const since = lo > 0 ? t - beats[lo - 1] : 9;
      $("pv-beat").classList.toggle("on", since < 0.09);
    }
    app.emit("frame", t);
  }
  frame();

  // ---- keyboard shortcuts (spec §15.1 subset) ----
  document.addEventListener("keydown", e => {
    if ($("scr-editor").hidden) return;
    const tag = e.target.tagName;
    if (tag === "INPUT" || tag === "SELECT" || tag === "TEXTAREA") return;
    const mod = e.ctrlKey || e.metaKey;

    if (e.code === "Space") { e.preventDefault(); $("tr-play").click(); }
    else if (e.key === "Home") app.playback.seek(0);
    else if (e.key === "ArrowLeft") app.playback.seek(app.playback.t - (e.shiftKey ? 60 / (app.analysis?.bpm || 120) : 1 / 30));
    else if (e.key === "ArrowRight") app.playback.seek(app.playback.t + (e.shiftKey ? 60 / (app.analysis?.bpm || 120) : 1 / 30));
    else if (e.key === "ArrowUp" || e.key === "ArrowDown") {
      if (!app.analysis) return;
      e.preventDefault();
      const t = app.playback.t, secs = app.analysis.sections;
      if (e.key === "ArrowDown") {
        const next = secs.find(s => s.start > t + 0.05);
        if (next) app.playback.seek(next.start);
      } else {
        const cur = sectionAt(secs, t);
        if (cur && t - cur.start > 0.5) app.playback.seek(cur.start);
        else {
          const prev = [...secs].reverse().find(s => s.start < t - 0.05 && (!cur || s.start < cur.start));
          app.playback.seek(prev ? prev.start : 0);
        }
      }
    }
    else if (e.key.toLowerCase() === "n") { const s = $("ed-snap"); s.checked = !s.checked; }
    else if (e.key.toLowerCase() === "f") $("pv-full").click();
    else if (mod && e.key.toLowerCase() === "z") {
      e.preventDefault();
      const p = e.shiftKey ? redoOp(app.history) : undoOp(app.history, app.project);
      if (p) { app.project = p; app.selection = null; app.emit("projectChanged"); }
    }
    else if (mod && e.key.toLowerCase() === "e") { e.preventDefault(); app.emit("openExport"); }
    else if (mod && e.key.toLowerCase() === "g" && e.shiftKey) { e.preventDefault(); $("ed-regen").click(); }
    else app.emit("key", e);   // timeline handles clip-level keys
  });
}
