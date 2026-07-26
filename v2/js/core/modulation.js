// Modulation system (spec §8). Pure functions of (analysis, t) — no state,
// so scrubbing, preview and offline render all agree.
"use strict";

export const EVENT_SOURCES = ["kick", "snare", "hat", "beat", "bar", "drop"];
export const CURVE_SOURCES = ["bass", "mid", "high", "energy", "loudness", "buildProgress"];
export const ALL_SOURCES = [...EVENT_SOURCES, ...CURVE_SOURCES];

// Envelope for event sources: attack/release in ms around each event.
// Scans events near t (they're sorted); returns 0..1.
function eventEnv(events, t, attackMs, releaseMs) {
  const atk = attackMs / 1000, rel = releaseMs / 1000;
  let v = 0;
  // walk backwards from the latest event <= t + atk
  let lo = 0, hi = events.length;
  while (lo < hi) { const m = (lo + hi) >> 1; (events[m].t <= t + atk) ? lo = m + 1 : hi = m; }
  for (let i = lo - 1; i >= 0; i--) {
    const e = events[i], dt = t - e.t;
    if (dt > rel + atk) break;
    let env;
    if (dt < 0) env = 0;
    else if (dt < atk) env = dt / Math.max(atk, 1e-4);
    else env = Math.max(0, 1 - (dt - atk) / Math.max(rel, 1e-4));
    v = Math.max(v, env * (e.strength ?? 1));
  }
  return v;
}

function beatEvents(beats, t, releaseS) {
  // beats is a Float64Array of times; envelope decays over releaseS
  let lo = 0, hi = beats.length;
  while (lo < hi) { const m = (lo + hi) >> 1; (beats[m] <= t) ? lo = m + 1 : hi = m; }
  if (lo === 0) return 0;
  const dt = t - beats[lo - 1];
  return Math.max(0, 1 - dt / releaseS);
}

function curveAt(curve, frameDur, t) {
  const f = t / frameDur;
  const i = Math.floor(f);
  if (i < 0) return curve[0] ?? 0;
  if (i >= curve.length - 1) return curve[curve.length - 1] ?? 0;
  const fr = f - i;
  return curve[i] * (1 - fr) + curve[i + 1] * fr;
}

export function sourceValue(analysis, source, t, attackMs = 0, releaseMs = 150) {
  if (!analysis) return 0;
  switch (source) {
    case "kick": return eventEnv(analysis.events.kick, t, attackMs, releaseMs);
    case "snare": return eventEnv(analysis.events.snare, t, attackMs, releaseMs);
    case "hat": return eventEnv(analysis.events.hat, t, attackMs, releaseMs);
    case "drop": return eventEnv(analysis.events.drop, t, attackMs, Math.max(releaseMs, 600));
    case "beat": return beatEvents(analysis.beats, t, Math.max(releaseMs, 60) / 1000);
    case "bar": return beatEvents(analysis.bars, t, Math.max(releaseMs, 120) / 1000);
    case "bass": return curveAt(analysis.curves.bass, analysis.frameDur, t);
    case "mid": return curveAt(analysis.curves.mid, analysis.frameDur, t);
    case "high": return curveAt(analysis.curves.high, analysis.frameDur, t);
    case "energy": return curveAt(analysis.curves.energy, analysis.frameDur, t);
    case "loudness": return curveAt(analysis.curves.loudness, analysis.frameDur, t);
    case "buildProgress": {
      const s = analysis.sections.find(x => t >= x.start && t < x.end);
      if (!s || s.type !== "build") return 0;
      return (t - s.start) / Math.max(0.001, s.end - s.start);
    }
    default: return 0;
  }
}

// slot: { param, source, amount (-1..1), attackMs, releaseMs }
// Base parameter values are normalised against the param's range by the
// caller; modulation adds amount*source of the range, clamped.
export function applyMods(clip, analysis, t, key, base, min, max) {
  if (clip.mode === "manual" || !clip.mods) return base;
  let v = base;
  const range = max - min;
  for (const m of clip.mods) {
    if (m.param !== key || m.bypass) continue;
    const s = sourceValue(analysis, m.source, t, m.attackMs ?? 0, m.releaseMs ?? 150);
    v += m.amount * s * range * (clip.params.pulse ?? 1);
  }
  return Math.max(min, Math.min(max, v));
}

export function makeSlot(param, source, amount = 0.5, attackMs = 0, releaseMs = 150) {
  return { param, source, amount, attackMs, releaseMs, bypass: false };
}
