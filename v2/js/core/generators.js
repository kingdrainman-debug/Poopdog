// Generator registry (spec §6). Every generator is:
//   { id, name, category, track ("visual"|"adjust"|"transition"|"camera"),
//     params: [{key,label,min,max,step,def} | {key,label,select,def}],
//     defaultMods: [slot...],       — factory audio bindings (§8.4)
//     draw(g) }                     — pure function of g (no retained state)
//
// g = { ctx, w, h, t (clip-local, speed-applied), T (global), p (params),
//       a (audio source values), c(i,alpha,boost) palette colour,
//       glowPx, seed, h01(i), clip, events, beats, bpm, progress }
"use strict";

import { hash01 } from "./prng.js";
import { makeSlot } from "./modulation.js";

const TAU = Math.PI * 2;
export const REGISTRY = new Map();
const def = g => REGISTRY.set(g.id, g);
const P = (key, label, min, max, def_, step = 0.01) => ({ key, label, min, max, def: def_, step });

function beams(g, count, fn) {
  const { ctx } = g;
  ctx.save();
  ctx.lineCap = "round";
  for (let i = 0; i < count; i++) {
    ctx.strokeStyle = g.c(i, 0.4 + g.a.loudness * 0.55, 10);
    ctx.lineWidth = 1.5 + g.a.bass * 8 * g.p.intensity;
    ctx.shadowBlur = g.glowPx;
    ctx.shadowColor = g.c(i, 0.8);
    ctx.beginPath();
    fn(i, ctx);
    ctx.stroke();
  }
  ctx.shadowBlur = 0;
  ctx.restore();
}

// ============================== LASERS ==============================

def({
  id: "laser.fan", name: "Fan Lasers", category: "Lasers", track: "visual",
  params: [P("beams", "Beams", 2, 14, 6, 1), P("spread", "Spread", 0.1, 1.5, 0.9), P("sway", "Sway rate", 0, 3, 1)],
  defaultMods: [makeSlot("intensity", "kick", 0.35, 0, 140)],
  draw(g) {
    const { w, h } = g, rot = g.t * 0.4;
    beams(g, g.p.beams * 2, (i, ctx) => {
      const side = i % 2, k = (i / 2) | 0, ox = side ? w : 0, dir = side ? -1 : 1;
      const a = -Math.PI / 2 + dir * (Math.sin(rot * g.p.sway + k * 0.35) * g.p.spread * 0.5 + 0.35);
      ctx.moveTo(ox, h);
      ctx.lineTo(ox + Math.cos(a) * h * 1.9, h + Math.sin(a) * h * 1.9);
    });
  },
});

def({
  id: "laser.cross", name: "Cross Lasers", category: "Lasers", track: "visual",
  params: [P("beams", "Beams", 2, 12, 6, 1), P("sway", "Sway", 0, 80, 30, 1)],
  defaultMods: [makeSlot("intensity", "kick", 0.3, 0, 140)],
  draw(g) {
    const { w, h } = g;
    beams(g, g.p.beams * 2, (i, ctx) => {
      const horiz = i % 2 === 0, k = (i / 2) | 0, p = (k + 1) / (g.p.beams + 1);
      const sw = Math.sin(g.t * 1.5 + k * 1.7) * g.p.sway;
      if (horiz) { ctx.moveTo(0, p * h + sw); ctx.lineTo(w, p * h - sw); }
      else { ctx.moveTo(p * w + sw, 0); ctx.lineTo(p * w - sw, h); }
    });
  },
});

def({
  id: "laser.burst", name: "Laser Burst", category: "Lasers", track: "visual",
  params: [P("rays", "Rays", 4, 24, 10, 1), P("spin", "Spin", 0, 4, 1.2)],
  defaultMods: [makeSlot("opacity", "kick", 0.6, 0, 180), makeSlot("intensity", "kick", 0.5, 0, 160)],
  draw(g) {
    const { w, h } = g, n = Math.round(g.p.rays * (0.5 + g.a.kick * 0.8));
    beams(g, n, (i, ctx) => {
      const a = (i / n) * TAU + g.t * g.p.spin + g.h01(i) * 0.5;
      ctx.moveTo(w / 2, h / 2);
      ctx.lineTo(w / 2 + Math.cos(a) * w, h / 2 + Math.sin(a) * w);
    });
  },
});

def({
  id: "laser.circle", name: "Circle Lasers", category: "Lasers", track: "visual",
  params: [P("rings", "Rings", 2, 10, 6, 1), P("arc", "Arc", 0.2, 1, 0.7)],
  defaultMods: [makeSlot("scale", "kick", 0.18, 0, 150)],
  draw(g) {
    const { w, h } = g;
    beams(g, g.p.rings, (i, ctx) => {
      const r = (i + 1) / g.p.rings * Math.min(w, h) * 0.45;
      ctx.arc(w / 2, h / 2, r, g.t * 0.5 + i, g.t * 0.5 + i + TAU * g.p.arc);
    });
  },
});

def({
  id: "laser.spiral", name: "Spiral Lasers", category: "Lasers", track: "visual",
  params: [P("arms", "Arms", 1, 6, 3, 1), P("turns", "Turns", 1, 6, 2.5)],
  defaultMods: [makeSlot("intensity", "bass", 0.4)],
  draw(g) {
    const { w, h, ctx } = g;
    beams(g, g.p.arms, (arm, c) => {
      for (let i = 0; i <= 70; i++) {
        const p = i / 70, a = g.t + arm * (TAU / g.p.arms) + p * TAU * g.p.turns;
        const r = p * Math.min(w, h) * 0.55 * (1 + g.a.bass * 0.3);
        const x = w / 2 + Math.cos(a) * r, y = h / 2 + Math.sin(a) * r;
        i === 0 ? c.moveTo(x, y) : c.lineTo(x, y);
      }
    });
  },
});

def({
  id: "laser.stadium", name: "Stadium Skybeams", category: "Lasers", track: "visual",
  params: [P("beams", "Beams", 3, 16, 8, 1), P("wander", "Wander", 0, 1, 0.5)],
  defaultMods: [makeSlot("intensity", "kick", 0.4, 0, 200)],
  draw(g) {
    const { w, h } = g;
    beams(g, g.p.beams, (i, ctx) => {
      const x = (i + 0.5) / g.p.beams * w;
      const tilt = Math.sin(g.t * (0.4 + g.h01(i) * 0.5) + i * 2.1) * g.p.wander * 0.7;
      ctx.moveTo(x, h);
      ctx.lineTo(x + Math.sin(tilt) * h, h - Math.cos(tilt) * h * 1.3);
    });
    // horizon glow line
    const ctx = g.ctx;
    ctx.fillStyle = g.c(1, 0.25 + g.a.bass * 0.3, 15);
    ctx.fillRect(0, h - 3, w, 3);
  },
});

def({
  id: "laser.matrix", name: "Beam Matrix", category: "Lasers", track: "visual",
  params: [P("rows", "Rows", 2, 8, 4, 1), P("cols", "Cols", 2, 8, 4, 1)],
  defaultMods: [makeSlot("opacity", "beat", 0.35, 0, 120)],
  draw(g) {
    const { w, h } = g, n = g.p.rows + g.p.cols;
    beams(g, n, (i, ctx) => {
      const drift = Math.sin(g.t * 0.8 + i) * 30;
      if (i < g.p.rows) { const y = (i + 0.5) / g.p.rows * h + drift; ctx.moveTo(0, y); ctx.lineTo(w, y); }
      else { const x = (i - g.p.rows + 0.5) / g.p.cols * w + drift; ctx.moveTo(x, 0); ctx.lineTo(x, h); }
    });
  },
});

// ============================== TUNNELS ==============================

function tunnelRings(g, drawRing) {
  const { ctx, w, h } = g;
  const cx = w / 2 + Math.sin(g.t * 0.4) * w * 0.05, cy = h / 2 + Math.cos(g.t * 0.31) * h * 0.05;
  const maxR = Math.hypot(w, h) * 0.6, rings = 13;
  const travel = g.t * 0.22 * (g.bpm / 120) * (1 + g.a.kick * 1.2);
  ctx.save();
  for (let i = 0; i < rings; i++) {
    const depth = ((i / rings) + travel) % 1;
    const r = Math.pow(depth, 2.4) * maxR * (1 + g.a.kick * 0.15);
    if (r < 2) continue;
    ctx.strokeStyle = g.c(i, Math.min(0.85, depth * 1.1) * (0.45 + g.a.loudness * 0.55), depth * 15);
    ctx.lineWidth = 1 + depth * (3 + g.a.bass * 6);
    ctx.shadowBlur = g.glowPx * depth;
    ctx.shadowColor = g.c(i, 0.8);
    ctx.beginPath();
    drawRing(cx, cy, r, depth, i, ctx);
    ctx.stroke();
  }
  ctx.shadowBlur = 0;
  ctx.restore();
  return { cx, cy, maxR };
}

const poly = (ctx, cx, cy, r, sides, rot) => {
  for (let k = 0; k <= sides; k++) {
    const a = rot + (k / sides) * TAU;
    const x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
    k === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
  }
};

def({
  id: "tunnel.rings", name: "Neon Ring Tunnel", category: "Tunnels", track: "visual",
  params: [P("twist", "Twist", 0, 3, 1)],
  defaultMods: [makeSlot("intensity", "bass", 0.3)],
  draw(g) { tunnelRings(g, (cx, cy, r, d, i, ctx) => ctx.arc(cx, cy, r, 0, TAU)); },
});
def({
  id: "tunnel.hex", name: "Hex Tunnel", category: "Tunnels", track: "visual",
  params: [P("twist", "Twist", 0, 3, 1.5)],
  defaultMods: [makeSlot("intensity", "bass", 0.3)],
  draw(g) { tunnelRings(g, (cx, cy, r, d, i, ctx) => poly(ctx, cx, cy, r, 6, g.t * 0.3 + d * g.p.twist)); },
});
def({
  id: "tunnel.grid", name: "Grid Tunnel", category: "Tunnels", track: "visual",
  params: [P("spokes", "Spokes", 4, 12, 8, 1)],
  defaultMods: [makeSlot("intensity", "kick", 0.35, 0, 150)],
  draw(g) {
    const { cx, cy, maxR } = tunnelRings(g, (x, y, r, d, i, ctx) => poly(ctx, x, y, r, 4, Math.PI / 4));
    const ctx = g.ctx;
    ctx.strokeStyle = g.c(1, 0.25 + g.a.bass * 0.3);
    ctx.lineWidth = 1.5;
    for (let d = 0; d < g.p.spokes; d++) {
      const a = d / g.p.spokes * TAU + Math.PI / 4;
      ctx.beginPath(); ctx.moveTo(cx, cy);
      ctx.lineTo(cx + Math.cos(a) * maxR, cy + Math.sin(a) * maxR);
      ctx.stroke();
    }
  },
});
def({
  id: "tunnel.star", name: "Star Tunnel", category: "Tunnels", track: "visual",
  params: [P("stars", "Streaks", 40, 220, 120, 1), P("rush", "Rush", 0.2, 4, 1.4)],
  defaultMods: [makeSlot("rush", "energy", 0.5)],
  draw(g) {
    const { ctx, w, h } = g, cx = w / 2, cy = h / 2, n = g.p.stars | 0;
    ctx.save();
    ctx.lineCap = "round";
    for (let i = 0; i < n; i++) {
      const a = g.h01(i) * TAU;
      const cycle = 1.6 / g.p.rush;
      const z = 1 - ((g.t / cycle + g.h01(i + 9000)) % 1);        // 1 far -> 0 near
      const r0 = Math.pow(1 - z, 2.2) * Math.hypot(w, h) * 0.7;
      const r1 = Math.pow(Math.min(1, 1 - z + 0.05 + g.a.kick * 0.06), 2.2) * Math.hypot(w, h) * 0.7;
      ctx.strokeStyle = g.c(i % 4, (1 - z) * 0.9, (1 - z) * 25);
      ctx.lineWidth = (1 - z) * 3 + 0.4;
      ctx.beginPath();
      ctx.moveTo(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0);
      ctx.lineTo(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1);
      ctx.stroke();
    }
    ctx.restore();
  },
});
def({
  id: "tunnel.warp", name: "Warp Gate", category: "Tunnels", track: "visual",
  params: [P("rings", "Rings", 4, 20, 10, 1)],
  defaultMods: [makeSlot("scale", "kick", 0.15, 0, 160)],
  draw(g) {
    const { ctx, w, h } = g, cx = w / 2, cy = h / 2;
    ctx.save();
    for (let i = 0; i < g.p.rings; i++) {
      const ph = (g.t * 0.5 + i / g.p.rings) % 1;
      const r = ph * Math.min(w, h) * 0.55;
      ctx.strokeStyle = g.c(i, (1 - ph) * 0.8, 10 + g.a.high * 20);
      ctx.lineWidth = 2 + (1 - ph) * 6;
      ctx.shadowBlur = g.glowPx * (1 - ph);
      ctx.shadowColor = g.c(i, 0.8);
      ctx.beginPath();
      ctx.ellipse(cx, cy, r, r * 0.6, Math.sin(g.t * 0.3) * 0.4, 0, TAU);
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

// ============================== GEOMETRY ==============================

def({
  id: "geo.rings", name: "Pulse Rings", category: "Geometry", track: "visual",
  params: [P("rings", "Rings", 1, 8, 4, 1)],
  defaultMods: [makeSlot("scale", "kick", 0.25, 0, 200)],
  draw(g) {
    const { ctx, w, h } = g;
    ctx.save();
    for (let i = 0; i < g.p.rings; i++) {
      const r = (i + 1) / g.p.rings * Math.min(w, h) * 0.35 * (1 + g.a.kick * 0.2);
      ctx.strokeStyle = g.c(i, 0.7, g.a.high * 25);
      ctx.lineWidth = 2.5 + g.a.bass * 5;
      ctx.shadowBlur = g.glowPx;
      ctx.shadowColor = g.c(i, 0.8);
      ctx.beginPath(); ctx.arc(w / 2, h / 2, r, 0, TAU); ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "geo.terrain", name: "Wireframe Terrain", category: "Geometry", track: "visual",
  params: [P("rows", "Rows", 6, 24, 14, 1), P("amp", "Height", 0, 1, 0.5)],
  defaultMods: [makeSlot("amp", "bass", 0.5)],
  draw(g) {
    const { ctx, w, h } = g, rows = g.p.rows | 0, cols = 24;
    ctx.save();
    ctx.lineWidth = 1.4;
    for (let r = 0; r < rows; r++) {
      const z = (r + (g.t * 2) % 1) / rows;                 // 0 near horizon
      const y = h * 0.45 + Math.pow(z, 1.6) * h * 0.55;
      const persp = 0.15 + z * 0.85;
      ctx.strokeStyle = g.c(r % 4, 0.25 + z * 0.6, z * 10);
      ctx.shadowBlur = g.glowPx * z * 0.5;
      ctx.shadowColor = g.c(r % 4, 0.6);
      ctx.beginPath();
      for (let cIdx = 0; cIdx <= cols; cIdx++) {
        const u = cIdx / cols;
        const x = w / 2 + (u - 0.5) * w * 1.4 * persp;
        const noise = Math.sin(u * 9 + g.t * 1.3 + r) * Math.cos(u * 5 - g.t) * g.p.amp;
        const yy = y - Math.abs(noise) * h * 0.18 * z * (0.4 + g.a.bass);
        cIdx === 0 ? ctx.moveTo(x, yy) : ctx.lineTo(x, yy);
      }
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "geo.poly", name: "Wire Polyhedron", category: "Geometry", track: "visual",
  params: [P("sides", "Sides", 3, 9, 6, 1), P("layers", "Layers", 1, 5, 3, 1)],
  defaultMods: [makeSlot("intensity", "kick", 0.3, 0, 150)],
  draw(g) {
    const { ctx, w, h } = g, R = Math.min(w, h) * 0.3;
    ctx.save();
    for (let l = 0; l < g.p.layers; l++) {
      const r = R * (1 - l * 0.25) * (1 + g.a.kick * 0.15);
      ctx.strokeStyle = g.c(l, 0.7, 10);
      ctx.lineWidth = 2;
      ctx.shadowBlur = g.glowPx;
      ctx.shadowColor = g.c(l, 0.8);
      ctx.beginPath();
      poly(ctx, w / 2, h / 2, r, g.p.sides | 0, g.t * (0.4 + l * 0.25) * (l % 2 ? -1 : 1));
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "geo.mandala", name: "Mandala", category: "Geometry", track: "visual",
  params: [P("petals", "Petals", 4, 16, 8, 1), P("depth", "Depth", 1, 4, 2, 1)],
  defaultMods: [makeSlot("hueShift", "snare", 0.3, 0, 300)],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.petals | 0, R = Math.min(w, h) * 0.38;
    ctx.save();
    ctx.translate(w / 2, h / 2);
    ctx.rotate(g.t * 0.15);
    for (let d = 0; d < g.p.depth; d++) {
      const r = R * (1 - d * 0.3);
      ctx.strokeStyle = g.c(d, 0.65, g.a.high * 20);
      ctx.lineWidth = 1.8;
      ctx.shadowBlur = g.glowPx * 0.7;
      ctx.shadowColor = g.c(d, 0.7);
      for (let i = 0; i < n; i++) {
        const a = i / n * TAU;
        ctx.beginPath();
        ctx.ellipse(Math.cos(a) * r * 0.5, Math.sin(a) * r * 0.5, r * 0.5, r * 0.22 * (1 + g.a.bass * 0.5), a, 0, TAU);
        ctx.stroke();
      }
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

// ============================== PARTICLES ==============================
// Deterministic: particle i state at time t is a pure function of
// (seed, i, t) — scrubbing and export sample identically (spec §3).

def({
  id: "part.sparks", name: "Kick Sparks", category: "Particles", track: "visual",
  params: [P("perBurst", "Per burst", 6, 60, 26, 1), P("life", "Life (s)", 0.3, 2.5, 1.1), P("vel", "Velocity", 50, 600, 260, 1)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g;
    ctx.save();
    const life = g.p.life, K = g.p.perBurst | 0;
    for (const e of g.events.kick) {
      const age = g.T - e.t;
      if (age < 0 || age > life) continue;
      const bSeed = Math.round(e.t * 997);
      for (let i = 0; i < K; i++) {
        const a = hash01(g.seed + bSeed, i) * TAU;
        const v = g.p.vel * (0.4 + hash01(g.seed + bSeed, i + 500) * 0.8) * (0.5 + e.strength);
        const x = w / 2 + Math.cos(a) * v * age, y = h / 2 + Math.sin(a) * v * age + 40 * age * age;
        const lf = 1 - age / life;
        ctx.fillStyle = g.c(i % 4, lf * 0.9, g.a.high * 25);
        ctx.shadowBlur = g.glowPx * 0.5;
        ctx.shadowColor = g.c(i % 4, 0.8);
        ctx.beginPath();
        ctx.arc(x, y, Math.max(0.4, (1 + hash01(g.seed + bSeed, i + 900) * 2.5) * lf), 0, TAU);
        ctx.fill();
      }
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "part.starfield", name: "Starfield", category: "Particles", track: "visual",
  params: [P("count", "Stars", 40, 300, 140, 1)],
  defaultMods: [makeSlot("intensity", "high", 0.3)],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.count | 0;
    ctx.save();
    for (let i = 0; i < n; i++) {
      const x = g.h01(i) * w, y = g.h01(i + 5000) * h;
      const tw = 0.4 + 0.6 * Math.sin(g.t * (1 + g.h01(i + 999) * 3) + g.h01(i) * TAU) ** 2;
      ctx.fillStyle = g.c(i % 4, tw * (0.5 + g.a.high * 0.5), 20);
      ctx.beginPath();
      ctx.arc(x, y, 0.6 + g.h01(i + 70) * 1.8 * tw, 0, TAU);
      ctx.fill();
    }
    ctx.restore();
  },
});

def({
  id: "part.confetti", name: "Confetti", category: "Particles", track: "visual",
  params: [P("count", "Pieces", 30, 200, 90, 1), P("fall", "Fall speed", 20, 200, 80, 1)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.count | 0;
    ctx.save();
    for (let i = 0; i < n; i++) {
      const speed = g.p.fall * (0.5 + g.h01(i));
      const y = ((g.h01(i + 300) * h + g.t * speed) % (h + 30)) - 15;
      const x = g.h01(i) * w + Math.sin(g.t * 2 + i) * 22;
      ctx.fillStyle = g.c(i % 4, 0.85, 5);
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(g.t * (1 + g.h01(i + 60) * 3) + i);
      ctx.fillRect(-4, -2, 8, 4);
      ctx.restore();
    }
    ctx.restore();
  },
});

def({
  id: "part.embers", name: "Rising Embers", category: "Particles", track: "visual",
  params: [P("count", "Embers", 20, 160, 70, 1)],
  defaultMods: [makeSlot("intensity", "bass", 0.3)],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.count | 0;
    ctx.save();
    for (let i = 0; i < n; i++) {
      const speed = 30 + g.h01(i) * 70;
      const y = h + 10 - ((g.h01(i + 44) * h + g.t * speed) % (h + 30));
      const x = g.h01(i) * w + Math.sin(g.t * 1.4 + i * 2.6) * 30;
      const a = 0.3 + 0.7 * ((y / h));
      ctx.fillStyle = g.c(i % 4, a * (0.5 + g.a.bass * 0.5), 15);
      ctx.shadowBlur = g.glowPx * 0.4;
      ctx.shadowColor = g.c(i % 4, 0.7);
      ctx.beginPath();
      ctx.arc(x, y, 1 + g.h01(i + 8) * 2.2, 0, TAU);
      ctx.fill();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "part.shockwave", name: "Kick Shockwaves", category: "Particles", track: "visual",
  params: [P("speed", "Expand", 100, 1200, 500, 1), P("width", "Width", 1, 16, 6)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g;
    ctx.save();
    for (const e of g.events.kick) {
      const age = g.T - e.t;
      if (age < 0 || age > 1.2) continue;
      const r = age * g.p.speed, a = Math.max(0, 1 - age / 1.2) * e.strength;
      ctx.strokeStyle = g.c(2, a * 0.85, 20);
      ctx.lineWidth = g.p.width * (1 - age * 0.6);
      ctx.shadowBlur = g.glowPx;
      ctx.shadowColor = g.c(2, 0.8);
      ctx.beginPath(); ctx.arc(w / 2, h / 2, r, 0, TAU); ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

// ============================== BACKGROUNDS ==============================

def({
  id: "bg.gradient", name: "Gradient Field", category: "Backgrounds", track: "visual",
  params: [P("drift", "Drift", 0, 2, 0.6)],
  defaultMods: [makeSlot("intensity", "energy", 0.4)],
  draw(g) {
    const { ctx, w, h } = g;
    const cx = w / 2 + Math.sin(g.t * g.p.drift) * w * 0.2;
    const cy = h / 2 + Math.cos(g.t * g.p.drift * 0.7) * h * 0.2;
    const grad = ctx.createRadialGradient(cx, cy, 0, w / 2, h / 2, Math.hypot(w, h) * 0.6);
    grad.addColorStop(0, g.c(0, 0.35 * g.p.intensity, 0));
    grad.addColorStop(0.6, g.c(1, 0.18 * g.p.intensity, -10));
    grad.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);
  },
});

def({
  id: "bg.grid", name: "Perspective Grid", category: "Backgrounds", track: "visual",
  params: [P("lines", "Lines", 6, 24, 12, 1)],
  defaultMods: [makeSlot("intensity", "bass", 0.35)],
  draw(g) {
    const { ctx, w, h } = g, horizon = h * 0.45;
    ctx.save();
    ctx.lineWidth = 1.4;
    ctx.strokeStyle = g.c(1, 0.5, 5);
    ctx.shadowBlur = g.glowPx * 0.4;
    ctx.shadowColor = g.c(1, 0.6);
    for (let i = 0; i < g.p.lines; i++) {           // scrolling horizontals
      const z = ((i / g.p.lines) + g.t * 0.4) % 1;
      const y = horizon + Math.pow(z, 2) * (h - horizon);
      ctx.globalAlpha = z;
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
    }
    ctx.globalAlpha = 0.6;
    for (let i = -8; i <= 8; i++) {                 // verticals converging
      ctx.beginPath(); ctx.moveTo(w / 2 + i * 30, horizon); ctx.lineTo(w / 2 + i * w * 0.12, h);
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "bg.nebula", name: "Nebula", category: "Backgrounds", track: "visual",
  params: [P("blobs", "Density", 3, 12, 6, 1)],
  defaultMods: [makeSlot("intensity", "energy", 0.4)],
  draw(g) {
    const { ctx, w, h } = g;
    for (let i = 0; i < g.p.blobs; i++) {
      const x = (g.h01(i) + Math.sin(g.t * 0.13 + i * 2.4) * 0.12) * w;
      const y = (g.h01(i + 31) + Math.cos(g.t * 0.11 + i * 1.7) * 0.12) * h;
      const r = (0.18 + g.h01(i + 77) * 0.25) * Math.min(w, h) * (1 + g.a.bass * 0.15);
      const grad = ctx.createRadialGradient(x, y, 0, x, y, r);
      grad.addColorStop(0, g.c(i % 4, 0.16 * g.p.intensity, 5));
      grad.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = grad;
      ctx.fillRect(x - r, y - r, r * 2, r * 2);
    }
  },
});

def({
  id: "bg.matrix", name: "Matrix Rain", category: "Backgrounds", track: "visual",
  params: [P("cols", "Columns", 12, 60, 32, 1)],
  defaultMods: [makeSlot("intensity", "high", 0.3)],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.cols | 0, cw = w / n;
    ctx.save();
    for (let i = 0; i < n; i++) {
      const speed = 120 + g.h01(i) * 260;
      const head = (g.h01(i + 12) * h + g.t * speed) % (h + 160);
      for (let k = 0; k < 9; k++) {
        const y = head - k * 16;
        if (y < -16 || y > h) continue;
        ctx.fillStyle = g.c(i % 4, (1 - k / 9) * 0.8, k === 0 ? 35 : 0);
        ctx.fillRect(i * cw + cw * 0.25, y, cw * 0.5, 9);
      }
    }
    ctx.restore();
  },
});

def({
  id: "bg.sun", name: "Synthwave Sun", category: "Backgrounds", track: "visual",
  params: [P("size", "Size", 0.1, 0.5, 0.26), P("slats", "Slats", 0, 10, 6, 1)],
  defaultMods: [makeSlot("size", "bass", 0.15)],
  draw(g) {
    const { ctx, w, h } = g, r = g.p.size * Math.min(w, h), cx = w / 2, cy = h * 0.42;
    ctx.save();
    const grad = ctx.createLinearGradient(0, cy - r, 0, cy + r);
    grad.addColorStop(0, g.c(0, 0.95, 15));
    grad.addColorStop(1, g.c(3, 0.9, 0));
    ctx.fillStyle = grad;
    ctx.shadowBlur = g.glowPx * 1.5;
    ctx.shadowColor = g.c(0, 0.9);
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.fill();
    ctx.shadowBlur = 0;
    ctx.globalCompositeOperation = "destination-out";     // slats cut out
    for (let i = 0; i < g.p.slats; i++) {
      const y = cy + r * 0.15 + i * (r * 0.85 / Math.max(1, g.p.slats));
      ctx.fillRect(cx - r, y, r * 2, 2 + i * 1.5);
    }
    ctx.restore();
  },
});

// ============================== SPECTRUM ==============================

def({
  id: "spec.bars", name: "Bar Equalizer", category: "Spectrum", track: "visual",
  params: [P("bars", "Bars", 8, 48, 24, 1)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.bars | 0, bw = w / n;
    ctx.save();
    for (let i = 0; i < n; i++) {
      const u = i / n;
      // pseudo-spectrum from the three band curves, shaped per bar
      const v = u < 0.33 ? g.a.bass * (1 - u) : u < 0.66 ? g.a.mid : g.a.high * (0.5 + g.h01(i) * 0.8);
      const bh = Math.pow(v, 1.2) * h * 0.7 * g.p.intensity;
      ctx.fillStyle = g.c(i % 4, 0.8, v * 25);
      ctx.shadowBlur = g.glowPx * 0.5;
      ctx.shadowColor = g.c(i % 4, 0.7);
      ctx.fillRect(i * bw + bw * 0.15, h - bh, bw * 0.7, bh);
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

def({
  id: "spec.circle", name: "Circular Spectrum", category: "Spectrum", track: "visual",
  params: [P("spokes", "Spokes", 16, 96, 48, 1)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g, n = g.p.spokes | 0, R = Math.min(w, h) * 0.22;
    ctx.save();
    ctx.lineCap = "round";
    for (let i = 0; i < n; i++) {
      const u = i / n, a = u * TAU + g.t * 0.3;
      const v = u < 0.33 ? g.a.bass : u < 0.66 ? g.a.mid : g.a.high;
      const len = R * 0.3 + Math.pow(v, 1.3) * R * (0.8 + g.h01(i) * 0.5);
      ctx.strokeStyle = g.c(i % 4, 0.85, v * 25);
      ctx.lineWidth = 3;
      ctx.shadowBlur = g.glowPx * 0.5;
      ctx.shadowColor = g.c(i % 4, 0.7);
      ctx.beginPath();
      ctx.moveTo(w / 2 + Math.cos(a) * R, h / 2 + Math.sin(a) * R);
      ctx.lineTo(w / 2 + Math.cos(a) * (R + len), h / 2 + Math.sin(a) * (R + len));
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  },
});

// ============================== ADJUSTMENT (FX / COLOUR) ==============================

def({
  id: "fx.palette", name: "Palette", category: "Colour", track: "adjust",
  params: [{ key: "palette", label: "Palette", select: ["cyberpunk","synthwave","hardstyle","techno","psytrance","trance","dnb","acid","neon","blacklight"], def: "cyberpunk" }],
  defaultMods: [],
  draw() { /* state-only: engine reads params.palette */ },
});

def({
  id: "fx.strobe", name: "Strobe", category: "FX", track: "adjust",
  params: [P("amount", "Amount", 0, 1, 0.7)],
  defaultMods: [],
  draw(g) {
    const env = g.a.kick;
    if (env < 0.03) return;
    const { ctx, w, h } = g;
    ctx.fillStyle = g.cStrobe(env * g.p.amount * 0.5 * g.p.intensity);
    ctx.fillRect(0, 0, w, h);
  },
});

def({
  id: "fx.scanlines", name: "Scanlines", category: "FX", track: "adjust",
  params: [P("gap", "Gap", 2, 12, 4, 1)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g, gap = g.p.gap | 0;
    ctx.fillStyle = `rgba(0,0,0,${0.28 * g.p.intensity})`;
    for (let y = ((g.t * 30) | 0) % gap; y < h; y += gap) ctx.fillRect(0, y, w, 1);
  },
});

def({
  id: "fx.vignette", name: "Vignette + Grain", category: "FX", track: "adjust",
  params: [P("dark", "Darkness", 0, 1, 0.55)],
  defaultMods: [],
  draw(g) {
    const { ctx, w, h } = g;
    const grad = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.35, w / 2, h / 2, Math.hypot(w, h) * 0.6);
    grad.addColorStop(0, "rgba(0,0,0,0)");
    grad.addColorStop(1, `rgba(0,0,0,${g.p.dark * g.p.intensity})`);
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);
    // sparse deterministic grain
    const fseed = (g.t * 24) | 0;
    ctx.fillStyle = "rgba(255,255,255,0.06)";
    for (let i = 0; i < 120; i++) {
      ctx.fillRect(hash01(fseed, i) * w, hash01(fseed, i + 999) * h, 1.5, 1.5);
    }
  },
});

def({
  id: "fx.rgbsplit", name: "RGB Split", category: "FX", track: "adjust",
  params: [P("shift", "Shift px", 0, 30, 8, 1)],
  defaultMods: [makeSlot("shift", "kick", 0.6, 0, 120)],
  draw(g) {
    const off = g.p.shift * g.p.intensity;
    if (off < 0.5 || !g.snapshot) return;
    const { ctx, w, h } = g, snap = g.snapshot();
    ctx.save();
    ctx.globalCompositeOperation = "lighter";
    ctx.globalAlpha = 0.5;
    ctx.filter = "hue-rotate(120deg)";
    ctx.drawImage(snap, off, 0, w, h);
    ctx.filter = "hue-rotate(-120deg)";
    ctx.drawImage(snap, -off, 0, w, h);
    ctx.filter = "none";
    ctx.restore();
  },
});

def({
  id: "fx.invert", name: "Invert Flash", category: "FX", track: "adjust",
  params: [P("amount", "Amount", 0, 1, 0.8)],
  defaultMods: [],
  draw(g) {
    const env = g.a.snare * g.p.amount * g.p.intensity;
    if (env < 0.1) return;
    const { ctx, w, h } = g;
    ctx.save();
    ctx.globalCompositeOperation = "difference";
    ctx.fillStyle = `rgba(${255 * env},${255 * env},${255 * env},1)`;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  },
});

// ============================== TRANSITIONS ==============================

def({
  id: "trans.flash", name: "White Flash", category: "Transitions", track: "transition",
  params: [P("peak", "Peak", 0.2, 1, 0.85)],
  defaultMods: [],
  draw(g) {
    const a = Math.sin(Math.min(1, Math.max(0, g.progress)) * Math.PI) * g.p.peak;
    if (a < 0.01) return;
    const { ctx, w, h } = g;
    ctx.fillStyle = g.cStrobe(a);
    ctx.fillRect(0, 0, w, h);
  },
});

def({
  id: "trans.glitch", name: "Glitch Cut", category: "Transitions", track: "transition",
  params: [P("slices", "Slices", 3, 20, 9, 1)],
  defaultMods: [],
  draw(g) {
    if (!g.snapshot) return;
    const { ctx, w, h } = g, snap = g.snapshot();
    const step = ((g.T * 24) | 0), n = g.p.slices | 0;
    const amt = Math.sin(Math.min(1, Math.max(0, g.progress)) * Math.PI);
    ctx.save();
    for (let i = 0; i < n; i++) {
      const y = (i / n) * h, sh = h / n;
      const dx = (hash01(step, i) - 0.5) * w * 0.25 * amt;
      ctx.drawImage(snap, 0, y, w, sh, dx, y, w, sh);
    }
    ctx.restore();
  },
});

// ============================== CAMERA ==============================
// Camera generators return a transform; engine applies it under everything.

def({
  id: "cam.bassZoom", name: "Bass Zoom", category: "Camera", track: "camera",
  params: [P("amount", "Amount", 0, 1, 0.5)],
  defaultMods: [],
  cam(g) { return { zoom: 1 + g.a.bass * 0.12 * g.p.amount * 2, rot: 0, dx: 0, dy: 0 }; },
});
def({
  id: "cam.shake", name: "Kick Shake", category: "Camera", track: "camera",
  params: [P("amount", "Amount", 0, 1, 0.6)],
  defaultMods: [],
  cam(g) {
    const e = g.a.kick * g.p.amount, f = (g.T * 60) | 0;
    return { zoom: 1, rot: 0, dx: (hash01(f, 1) - 0.5) * 26 * e, dy: (hash01(f, 2) - 0.5) * 26 * e };
  },
});
def({
  id: "cam.orbit", name: "Slow Orbit", category: "Camera", track: "camera",
  params: [P("deg", "Degrees", 0, 20, 6, 0.5)],
  defaultMods: [],
  cam(g) { return { zoom: 1.06, rot: Math.sin(g.t * 0.25) * g.p.deg * Math.PI / 180, dx: 0, dy: 0 }; },
});
def({
  id: "cam.push", name: "Push In", category: "Camera", track: "camera",
  params: [P("amount", "Amount", 0, 1, 0.4)],
  defaultMods: [],
  cam(g) { return { zoom: 1 + g.progress * g.p.amount * 0.5, rot: 0, dx: 0, dy: 0 }; },
});
def({
  id: "cam.drift", name: "Handheld Drift", category: "Camera", track: "camera",
  params: [P("amount", "Amount", 0, 1, 0.5)],
  defaultMods: [],
  cam(g) {
    const a = g.p.amount;
    return {
      zoom: 1.03, rot: Math.sin(g.t * 0.4) * 0.01 * a,
      dx: Math.sin(g.t * 0.31) * 14 * a, dy: Math.cos(g.t * 0.23) * 10 * a,
    };
  },
});

export const CATEGORIES = [...new Set([...REGISTRY.values()].map(g => g.category))];
export const byCategory = cat => [...REGISTRY.values()].filter(g => g.category === cat);
