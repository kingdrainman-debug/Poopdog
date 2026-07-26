// Visual engine — renders audio-reactive scenes on a 2D canvas using
// additive blending for the neon/laser look.
//
// Every renderer receives (ctx, w, h, t, f, s):
//   t — seconds since visuals started
//   f — realtime audio features from AudioEngine.update()
//   s — settings { palette, laserMode, tunnelType, glow, speed, strobeAmt, shakeAmt, intensity }
//
// Audio-reactive bindings (per the product spec):
//   laser width  → bass        tunnel speed → BPM & kick
//   glow         → highs       rotation     → kick envelope
//   hue cycling  → snare hits  strobe       → drops / kicks
"use strict";

class VisualEngine {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.t = 0;
    this.hueShift = 0;         // advanced by snare hits + slow drift
    this.tunnelZ = 0;          // accumulated tunnel travel
    this.rotation = 0;
    this.flash = 0;            // white flash envelope (transitions / strobe)
    this.particles = [];
    this.lastFrame = performance.now();
  }

  // pick a palette colour, cycled by index + snare-driven hue shift
  color(s, i, alpha = 1, boost = 0) {
    const pal = PALETTES[s.palette];
    const c = pal.colors[i % pal.colors.length];
    return `hsla(${(c[0] + this.hueShift) % 360}, ${c[1]}%, ${Math.min(100, c[2] + boost)}%, ${alpha})`;
  }

  render(scene, f, s, bpm) {
    const now = performance.now();
    const dt = Math.min(0.05, (now - this.lastFrame) / 1000);
    this.lastFrame = now;
    this.t += dt * s.speed;

    const ctx = this.ctx, w = this.canvas.width, h = this.canvas.height;

    // colour engine: snare bumps the hue, plus slow constant drift
    if (f.snare) this.hueShift = (this.hueShift + 25) % 360;
    this.hueShift = (this.hueShift + dt * 4) % 360;

    // tunnel speed → BPM, kicked forward on each kick
    const bpmRate = (bpm || 120) / 120;
    this.tunnelZ += dt * s.speed * bpmRate * (1 + f.kickEnv * 2);

    // rotation → kick
    this.rotation += dt * s.speed * 0.3 * (0.3 + f.kickEnv * 2);

    // strobe/flash: kicks trigger flash scaled by strobe control & intensity
    if (f.kick && s.strobeAmt > 0.01) this.flash = Math.max(this.flash, s.strobeAmt * s.intensity);
    this.flash *= 0.72;

    // trail fade — lower glow = harder fade
    const pal = PALETTES[s.palette];
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = `hsla(${pal.bg[0]}, ${pal.bg[1]}%, ${pal.bg[2]}%, ${1 - s.glow * 0.55})`;
    ctx.fillRect(0, 0, w, h);

    // camera shake → bass & kicks
    ctx.save();
    const shake = s.shakeAmt * (f.kickEnv * 14 + f.bass * 4);
    if (shake > 0.3) {
      ctx.translate((Math.random() - 0.5) * shake, (Math.random() - 0.5) * shake);
    }
    // bass zoom
    const zoom = 1 + f.bass * 0.05 * s.shakeAmt;
    ctx.translate(w / 2, h / 2); ctx.scale(zoom, zoom); ctx.translate(-w / 2, -h / 2);

    ctx.globalCompositeOperation = "lighter";
    switch (scene) {
      case "lasers": this.drawLasers(ctx, w, h, f, s); break;
      case "tunnel": this.drawTunnel(ctx, w, h, f, s); break;
      case "particles": this.drawParticles(ctx, w, h, f, s, dt); break;
      case "strobe": this.drawStrobe(ctx, w, h, f, s); break;
    }
    ctx.restore();

    // white flash overlay
    if (this.flash > 0.01) {
      ctx.globalCompositeOperation = "source-over";
      const sc = pal.strobe;
      ctx.fillStyle = `hsla(${sc[0]}, ${sc[1]}%, ${sc[2]}%, ${this.flash * 0.4})`;
      ctx.fillRect(0, 0, w, h);
    }
    ctx.globalCompositeOperation = "source-over";
  }

  // ---------------- lasers ----------------

  laserStroke(ctx, s, i, width, alpha) {
    ctx.strokeStyle = this.color(s, i, alpha, 10);
    ctx.lineWidth = width;
    ctx.shadowBlur = 12 + s.glow * 25;
    ctx.shadowColor = this.color(s, i, 0.9);
  }

  drawLasers(ctx, w, h, f, s) {
    const mode = s.laserMode;
    const width = 1.5 + f.bass * 9 * s.intensity;   // laser width → bass
    const alpha = 0.35 + f.loudness * 0.6;
    const n = 7;

    ctx.save();
    ctx.lineCap = "round";

    if (mode === "horizontal" || mode === "vertical" || mode === "cross") {
      const drawH = mode !== "vertical", drawV = mode !== "horizontal";
      for (let i = 0; i < n; i++) {
        const p = (i + 1) / (n + 1);
        const sway = Math.sin(this.t * 1.5 + i * 1.7) * 40 * s.intensity;
        this.laserStroke(ctx, s, i, width, alpha);
        if (drawH) {
          ctx.beginPath(); ctx.moveTo(0, p * h + sway); ctx.lineTo(w, p * h - sway); ctx.stroke();
        }
        if (drawV) {
          ctx.beginPath(); ctx.moveTo(p * w + sway, 0); ctx.lineTo(p * w - sway, h); ctx.stroke();
        }
      }
    } else if (mode === "fan") {
      // two fans from bottom corners, sweeping with rotation
      for (const [ox, dir] of [[0, 1], [w, -1]]) {
        for (let i = 0; i < n; i++) {
          const spread = 0.9 + f.kickEnv * 0.5;
          const a = -Math.PI / 2 + dir * (Math.sin(this.rotation + i * 0.35) * spread * 0.5 + 0.35);
          this.laserStroke(ctx, s, i, width, alpha);
          ctx.beginPath();
          ctx.moveTo(ox, h);
          ctx.lineTo(ox + Math.cos(a) * w * 1.6, h + Math.sin(a) * w * 1.6);
          ctx.stroke();
        }
      }
    } else if (mode === "circle") {
      const cx = w / 2, cy = h / 2;
      for (let i = 0; i < n; i++) {
        const r = (i + 1) / n * Math.min(w, h) * 0.45 * (1 + f.kickEnv * 0.25);
        this.laserStroke(ctx, s, i, width, alpha);
        ctx.beginPath();
        ctx.arc(cx, cy, r, this.rotation + i, this.rotation + i + Math.PI * 1.4);
        ctx.stroke();
      }
    } else if (mode === "spiral") {
      const cx = w / 2, cy = h / 2;
      for (let arm = 0; arm < 3; arm++) {
        this.laserStroke(ctx, s, arm, width, alpha);
        ctx.beginPath();
        for (let i = 0; i <= 80; i++) {
          const p = i / 80;
          const a = this.rotation * 2 + arm * (Math.PI * 2 / 3) + p * Math.PI * 5;
          const r = p * Math.min(w, h) * 0.55 * (1 + f.bass * 0.3);
          const x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
          i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
        }
        ctx.stroke();
      }
    } else if (mode === "burst") {
      // random laser bursts on kicks, radiating from centre
      const cx = w / 2, cy = h / 2;
      const count = Math.round(4 + f.kickEnv * 14);
      for (let i = 0; i < count; i++) {
        const a = (i / count) * Math.PI * 2 + this.rotation * 3 + Math.sin(i * 13.7) * 0.6;
        this.laserStroke(ctx, s, i, width * (0.5 + Math.random() * 0.8), alpha * (0.5 + f.kickEnv));
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.lineTo(cx + Math.cos(a) * w, cy + Math.sin(a) * w);
        ctx.stroke();
      }
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  }

  // ---------------- tunnels ----------------

  drawTunnel(ctx, w, h, f, s) {
    const type = s.tunnelType;
    const cx = w / 2 + Math.sin(this.t * 0.4) * w * 0.05;
    const cy = h / 2 + Math.cos(this.t * 0.31) * h * 0.05;
    const maxR = Math.hypot(w, h) * 0.6;
    const rings = 14;

    ctx.save();
    ctx.lineCap = "round";
    for (let i = 0; i < rings; i++) {
      // ring depth cycles from far (0) to near (1) as tunnelZ advances
      const depth = ((i / rings) + this.tunnelZ * 0.22) % 1;
      const r = Math.pow(depth, 2.4) * maxR * (1 + f.kickEnv * 0.15);
      if (r < 2) continue;
      const alpha = Math.min(0.85, depth * 1.1) * (0.45 + f.loudness * 0.55);
      const lw = 1 + depth * (3 + f.bass * 6);
      ctx.strokeStyle = this.color(s, i, alpha, depth * 15);
      ctx.lineWidth = lw;
      ctx.shadowBlur = s.glow * 20 * depth;
      ctx.shadowColor = this.color(s, i, 0.8);
      const twist = this.rotation + depth * 1.5;

      ctx.beginPath();
      if (type === "neon") {
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
      } else if (type === "hex") {
        this.polygonPath(ctx, cx, cy, r, 6, twist);
      } else if (type === "wireframe") {
        this.polygonPath(ctx, cx, cy, r, 4, twist);
      } else if (type === "grid") {
        this.polygonPath(ctx, cx, cy, r, 4, Math.PI / 4);
      } else if (type === "particle") {
        // ring of dots instead of a stroke
        const dots = 14;
        for (let d = 0; d < dots; d++) {
          const a = (d / dots) * Math.PI * 2 + twist;
          const x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
          ctx.moveTo(x, y);
          ctx.arc(x, y, lw * 0.9, 0, Math.PI * 2);
        }
      }
      ctx.stroke();
    }

    // wireframe/grid: radial connector lines for depth
    if (type === "wireframe" || type === "grid") {
      ctx.strokeStyle = this.color(s, 1, 0.25 + f.bass * 0.3);
      ctx.lineWidth = 1.5;
      ctx.shadowBlur = 0;
      const spokes = type === "grid" ? 8 : 4;
      for (let d = 0; d < spokes; d++) {
        const a = (d / spokes) * Math.PI * 2 + (type === "grid" ? Math.PI / 4 : this.rotation);
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.lineTo(cx + Math.cos(a) * maxR, cy + Math.sin(a) * maxR);
        ctx.stroke();
      }
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  }

  polygonPath(ctx, cx, cy, r, sides, rot) {
    for (let i = 0; i <= sides; i++) {
      const a = rot + (i / sides) * Math.PI * 2;
      const x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
  }

  // ---------------- particles ----------------

  drawParticles(ctx, w, h, f, s, dt) {
    // spawn: base rate scaled by loudness, burst on kicks
    const spawn = Math.round(2 + f.loudness * 6 + (f.kick ? 30 : 0));
    for (let i = 0; i < spawn && this.particles.length < 900; i++) {
      const a = Math.random() * Math.PI * 2;
      const sp = 30 + Math.random() * 120 + f.kickEnv * 350;
      this.particles.push({
        x: w / 2 + (Math.random() - 0.5) * w * 0.2,
        y: h / 2 + (Math.random() - 0.5) * h * 0.2,
        vx: Math.cos(a) * sp, vy: Math.sin(a) * sp,
        life: 1, decay: 0.25 + Math.random() * 0.5,
        size: 1.5 + Math.random() * 3.5,
        ci: Math.floor(Math.random() * 4),
      });
    }

    ctx.save();
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i];
      p.life -= p.decay * dt;
      if (p.life <= 0) { this.particles.splice(i, 1); continue; }
      p.x += p.vx * dt * s.speed;
      p.y += p.vy * dt * s.speed;
      p.vx *= 0.995; p.vy *= 0.995;
      const size = p.size * (0.5 + f.bass) * p.life;
      ctx.fillStyle = this.color(s, p.ci, p.life * 0.9, f.highs * 30);
      ctx.shadowBlur = s.glow * 14;
      ctx.shadowColor = this.color(s, p.ci, 0.8);
      ctx.beginPath();
      ctx.arc(p.x, p.y, Math.max(0.4, size), 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.shadowBlur = 0;
    ctx.restore();
  }

  // ---------------- strobe ----------------

  drawStrobe(ctx, w, h, f, s) {
    // hard beat flash + scan bars
    if (f.kick) this.flash = Math.max(this.flash, 0.9 * s.intensity);
    const bars = 6;
    for (let i = 0; i < bars; i++) {
      const y = ((i / bars) + this.t * 0.25) % 1 * h;
      ctx.fillStyle = this.color(s, i, 0.10 + f.loudness * 0.25);
      ctx.fillRect(0, y, w, h / bars * 0.45);
    }
    // centre pulse ring on snare
    if (f.snareEnv > 0.05) {
      ctx.strokeStyle = this.color(s, 2, f.snareEnv * 0.9, 20);
      ctx.lineWidth = 3 + f.snareEnv * 10;
      ctx.beginPath();
      ctx.arc(w / 2, h / 2, (1 - f.snareEnv) * Math.min(w, h) * 0.5, 0, Math.PI * 2);
      ctx.stroke();
    }
  }
}
