// Render engine (spec §3, §14). render(project, analysis, t, ctx, w, h)
// is a pure function — the SAME code path draws preview frames and export
// frames; only resolution differs. No retained canvas state between frames.
"use strict";

import { REGISTRY } from "./generators.js";
import { PALETTES, hsl } from "./palettes.js";
import { hash01 } from "./prng.js";
import { applyMods, sourceValue } from "./modulation.js";
import { clipsAt } from "./project.js";

export const UNIVERSAL = [
  { key: "intensity", label: "Intensity", min: 0, max: 1, def: 0.8, step: 0.01 },
  { key: "opacity", label: "Opacity", min: 0, max: 1, def: 1, step: 0.01 },
  { key: "speed", label: "Speed", min: 0.1, max: 3, def: 1, step: 0.05 },
  { key: "scale", label: "Scale", min: 0.25, max: 4, def: 1, step: 0.01 },
  { key: "hueShift", label: "Hue shift", min: -180, max: 180, def: 0, step: 1 },
  { key: "glow", label: "Glow", min: 0, max: 1, def: 0.6, step: 0.01 },
  { key: "pulse", label: "Pulse str.", min: 0, max: 2, def: 1, step: 0.01 },
];
export const BLENDS = ["add", "normal", "screen", "multiply"];
const BLEND_OP = { add: "lighter", normal: "source-over", screen: "screen", multiply: "multiply" };

export function paramDef(gen, key) {
  return UNIVERSAL.find(p => p.key === key) || gen.params.find(p => p.key === key) || null;
}
export function numericParams(gen) {
  return [...UNIVERSAL, ...gen.params.filter(p => p.select === undefined)];
}

// Resolve one parameter value with modulation applied.
export function resolve(clip, gen, analysis, t, key) {
  const d = paramDef(gen, key);
  if (!d) return clip.params[key];
  if (d.select !== undefined) return clip.params[key] ?? d.def;
  const base = clip.params[key] ?? d.def;
  return applyMods(clip, analysis, t, key, base, d.min, d.max);
}

export function makeEngine() {
  let snapCanvas = null, snapCtx = null;

  function snapshotOf(ctx, w, h) {
    if (!snapCanvas || snapCanvas.width !== w || snapCanvas.height !== h) {
      snapCanvas = (typeof OffscreenCanvas !== "undefined") ? new OffscreenCanvas(w, h)
        : Object.assign(document.createElement("canvas"), { width: w, height: h });
      snapCtx = snapCanvas.getContext("2d");
    }
    snapCtx.clearRect(0, 0, w, h);
    snapCtx.drawImage(ctx.canvas, 0, 0, w, h);
    return snapCanvas;
  }

  function makeG(clip, gen, analysis, t, ctx, w, h, palette, audio) {
    const speed = resolve(clip, gen, analysis, t, "speed");
    const local = (t - clip.start) * speed;
    const pal = PALETTES[palette] || PALETTES.cyberpunk;
    const hueShift = resolve(clip, gen, analysis, t, "hueShift") + (clip.params.palIdx ?? 0) * 0;
    const p = { palIdx: clip.params.palIdx ?? 0 };
    for (const d of [...UNIVERSAL, ...gen.params]) {
      p[d.key] = d.select !== undefined ? (clip.params[d.key] ?? d.def) : resolve(clip, gen, analysis, t, d.key);
    }
    const drift = t * 4;   // slow global hue drift (kept from V1's colour engine)
    return {
      ctx, w, h, t: local, T: t, p,
      a: audio, seed: clip.seed, clip,
      bpm: analysis ? analysis.bpm : 120,
      events: analysis ? analysis.events : { kick: [], snare: [], hat: [], drop: [] },
      beats: analysis ? analysis.beats : [],
      progress: (t - clip.start) / Math.max(0.001, clip.end - clip.start),
      glowPx: 10 + p.glow * 22,
      h01: i => hash01(clip.seed, i),
      c: (i, alpha = 1, boost = 0) =>
        hsl(pal.colors[(i + (clip.params.palIdx ?? 0)) % pal.colors.length], alpha, boost, hueShift + drift),
      cStrobe: alpha => hsl(pal.strobe, alpha, 0, 0),
      snapshot: () => snapshotOf(ctx, w, h),
    };
  }

  function clipAlpha(clip, gen, analysis, t) {
    let a = resolve(clip, gen, analysis, t, "opacity");
    if (clip.fadeIn > 0) a *= Math.min(1, (t - clip.start) / clip.fadeIn);
    if (clip.fadeOut > 0) a *= Math.min(1, (clip.end - t) / clip.fadeOut);
    return Math.max(0, Math.min(1, a));
  }

  return {
    render(project, analysis, t, ctx, w, h) {
      const layers = clipsAt(project, t);

      // active palette: project default, overridden by an fx.palette clip
      let palette = project.palette;
      for (const c of layers.adjust) {
        if (c.gen === "fx.palette") palette = c.params.palette || palette;
      }
      const pal = PALETTES[palette] || PALETTES.cyberpunk;

      // shared audio source values for this frame
      const audio = analysis ? {
        kick: sourceValue(analysis, "kick", t, 0, 140),
        snare: sourceValue(analysis, "snare", t, 0, 160),
        hat: sourceValue(analysis, "hat", t, 0, 80),
        bass: sourceValue(analysis, "bass", t),
        mid: sourceValue(analysis, "mid", t),
        high: sourceValue(analysis, "high", t),
        energy: sourceValue(analysis, "energy", t),
        loudness: sourceValue(analysis, "loudness", t),
      } : { kick: 0, snare: 0, hat: 0, bass: .4, mid: .3, high: .3, energy: .5, loudness: .4 };

      // background
      ctx.globalCompositeOperation = "source-over";
      ctx.globalAlpha = 1;
      ctx.fillStyle = hsl(pal.bg, 1);
      ctx.fillRect(0, 0, w, h);

      // camera transform (topmost active camera clip wins; §9.1)
      ctx.save();
      const camClip = layers.camera[layers.camera.length - 1];
      if (camClip) {
        const gen = REGISTRY.get(camClip.gen);
        if (gen && gen.cam) {
          const g = makeG(camClip, gen, analysis, t, ctx, w, h, palette, audio);
          const cam = gen.cam(g);
          const a = clipAlpha(camClip, gen, analysis, t);   // fade camera influence
          const zoom = 1 + (cam.zoom - 1) * a;
          ctx.translate(w / 2 + cam.dx * a, h / 2 + cam.dy * a);
          ctx.rotate(cam.rot * a);
          ctx.scale(zoom, zoom);
          ctx.translate(-w / 2, -h / 2);
        }
      }

      // visual clips bottom-to-top
      for (const clip of layers.visual) {
        const gen = REGISTRY.get(clip.gen);
        if (!gen || !gen.draw) continue;
        const alpha = clipAlpha(clip, gen, analysis, t);
        if (alpha <= 0.001) continue;
        const g = makeG(clip, gen, analysis, t, ctx, w, h, palette, audio);
        ctx.save();
        ctx.globalAlpha = alpha;
        ctx.globalCompositeOperation = BLEND_OP[clip.params.blend || "add"] || "lighter";
        const sc = g.p.scale;
        if (sc !== 1) { ctx.translate(w / 2, h / 2); ctx.scale(sc, sc); ctx.translate(-w / 2, -h / 2); }
        gen.draw(g);
        ctx.restore();
      }

      ctx.restore();   // end camera

      // adjustment clips (over everything below camera transform)
      for (const clip of layers.adjust) {
        const gen = REGISTRY.get(clip.gen);
        if (!gen || !gen.draw || clip.gen === "fx.palette") continue;
        const alpha = clipAlpha(clip, gen, analysis, t);
        if (alpha <= 0.001) continue;
        const g = makeG(clip, gen, analysis, t, ctx, w, h, palette, audio);
        ctx.save();
        ctx.globalAlpha = alpha;
        gen.draw(g);
        ctx.restore();
      }

      // transitions last
      for (const clip of layers.transition) {
        const gen = REGISTRY.get(clip.gen);
        if (!gen || !gen.draw) continue;
        const g = makeG(clip, gen, analysis, t, ctx, w, h, palette, audio);
        ctx.save();
        gen.draw(g);
        ctx.restore();
      }

      ctx.globalCompositeOperation = "source-over";
      ctx.globalAlpha = 1;
      return { palette, audio };
    },
  };
}
