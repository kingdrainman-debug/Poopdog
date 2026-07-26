// Project model (spec §9, §16). Tracks own clips; clips reference a
// generator id, carry params, modulation slots, a seed, and a mode.
// Undo is snapshot-based over the serializable part.
"use strict";

let nextId = 1;
export const uid = () => nextId++;

export const TRACK_TYPES = ["camera", "adjust", "visual", "transition"];

export function makeProject(name = "Untitled") {
  return {
    name,
    audioName: null,
    palette: "cyberpunk",
    seed: 1,
    tracks: [],            // top of list renders last (on top)
    analysisOverrides: null,
  };
}

export function makeTrack(type, name) {
  return { id: uid(), type, name, mute: false, solo: false, lock: false, clips: [] };
}

export function makeClip(genId, start, end, params = {}, opts = {}) {
  return {
    id: uid(), gen: genId, start, end,
    params,                // key -> value (universal + specific), sparse over defaults
    mods: opts.mods || [], // modulation slots (§8)
    mode: opts.mode || "auto",   // auto | manual
    seed: opts.seed ?? 1,
    auto: opts.auto || false,    // Director badge: regenerate may replace while true
    fadeIn: opts.fadeIn ?? 0, fadeOut: opts.fadeOut ?? 0,
  };
}

// Standard track set the Director targets (spec §9.8)
export function defaultTracks() {
  return [
    makeTrack("transition", "Transitions"),
    makeTrack("adjust", "FX / Colour"),
    makeTrack("camera", "Camera"),
    makeTrack("visual", "Accents"),
    makeTrack("visual", "Main"),
    makeTrack("visual", "Background"),
  ];
}

export function clipsAt(project, t) {
  // returns active clips bottom-to-top: visual tracks (list bottom = first),
  // then adjust, transition; camera handled separately
  const layers = { visual: [], adjust: [], transition: [], camera: [] };
  const anySolo = project.tracks.some(tr => tr.solo);
  for (let i = project.tracks.length - 1; i >= 0; i--) {   // bottom-up
    const tr = project.tracks[i];
    if (tr.mute || (anySolo && !tr.solo)) continue;
    for (const c of tr.clips) {
      if (t >= c.start && t < c.end) layers[tr.type].push(c);
    }
  }
  return layers;
}

export function findClip(project, id) {
  for (const tr of project.tracks)
    for (const c of tr.clips)
      if (c.id === id) return { track: tr, clip: c };
  return null;
}

export function removeClip(project, id) {
  for (const tr of project.tracks) {
    const i = tr.clips.findIndex(c => c.id === id);
    if (i >= 0) { tr.clips.splice(i, 1); return true; }
  }
  return false;
}

export function sortTrack(tr) { tr.clips.sort((a, b) => a.start - b.start); }

// ---------- serialization ----------
export function serialize(project) {
  return JSON.stringify(project);
}
export function deserialize(json) {
  const p = JSON.parse(json);
  // bump uid counter past everything loaded
  let max = 0;
  for (const tr of p.tracks) {
    max = Math.max(max, tr.id);
    for (const c of tr.clips) max = Math.max(max, c.id);
  }
  nextId = max + 1;
  return p;
}

// ---------- undo (snapshots, spec §15.5 scaled down) ----------
const UNDO_CAP = 100;
export function makeHistory(project) {
  return { undo: [], redo: [], last: serialize(project) };
}
export function commit(history, project) {
  const now = serialize(project);
  if (now === history.last) return;
  history.undo.push(history.last);
  if (history.undo.length > UNDO_CAP) history.undo.shift();
  history.redo.length = 0;
  history.last = now;
}
export function undo(history, project) {
  if (!history.undo.length) return null;
  history.redo.push(history.last);
  history.last = history.undo.pop();
  return deserialize(history.last);
}
export function redo(history) {
  if (!history.redo.length) return null;
  history.undo.push(history.last);
  history.last = history.redo.pop();
  return deserialize(history.last);
}

// ---------- autosave (spec §15.6, localStorage-backed in this build) ----------
const RECENT_KEY = "poopdog.v2.recent";
export function autosave(project) {
  try {
    const list = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]")
      .filter(e => e.name !== project.name);
    list.unshift({
      name: project.name, audioName: project.audioName,
      savedAt: Date.now(), project: serialize(project),
    });
    localStorage.setItem(RECENT_KEY, JSON.stringify(list.slice(0, 8)));
  } catch (e) { /* storage full: autosave is best-effort */ }
}
export function recentProjects() {
  try { return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]"); }
  catch (e) { return []; }
}
