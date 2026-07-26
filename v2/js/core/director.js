// Show Director (spec §9.8). Writes ordinary, editable clips into the
// project's tracks from the analysis. Clips are tagged auto:true; the
// contract: regenerate replaces ONLY still-tagged clips, never user edits.
"use strict";

import { prng } from "./prng.js";
import { makeClip, sortTrack } from "./project.js";
import { AUTO_PALETTES } from "./palettes.js";

// pools: section type -> weighted choices per role
const POOLS = {
  background: {
    intro: ["bg.nebula", "bg.gradient", "part.starfield"],
    build: ["bg.grid", "bg.gradient", "bg.matrix"],
    drop: ["bg.grid", "bg.gradient", "bg.sun"],
    breakdown: ["bg.nebula", "part.starfield", "bg.gradient"],
    groove: ["bg.gradient", "bg.grid", "bg.nebula", "bg.sun"],
    chorus: ["bg.sun", "bg.gradient"],
    outro: ["bg.nebula", "part.starfield"],
  },
  main: {
    intro: ["tunnel.rings", "geo.mandala", "spec.circle"],
    build: ["tunnel.hex", "laser.circle", "tunnel.warp", "geo.poly"],
    drop: ["laser.fan", "laser.burst", "tunnel.grid", "laser.cross", "tunnel.star", "laser.stadium"],
    breakdown: ["geo.mandala", "part.embers", "spec.circle", "tunnel.warp"],
    groove: ["tunnel.hex", "laser.circle", "geo.terrain", "tunnel.rings", "laser.matrix"],
    chorus: ["laser.stadium", "geo.terrain", "laser.fan"],
    outro: ["part.starfield", "tunnel.rings"],
  },
  accents: {
    drop: ["part.shockwave", "part.sparks", "spec.bars"],
    chorus: ["part.confetti", "part.sparks"],
    build: ["part.sparks"],
  },
  camera: {
    intro: "cam.drift", build: "cam.push", drop: "cam.shake",
    breakdown: "cam.orbit", groove: "cam.bassZoom", chorus: "cam.bassZoom", outro: "cam.drift",
  },
  intensity: { intro: 0.4, build: 0.7, drop: 1.0, breakdown: 0.45, groove: 0.7, chorus: 0.85, outro: 0.35 },
};

export function generateShow(project, analysis) {
  const tracks = Object.fromEntries(project.tracks.map(tr => [tr.name, tr]));
  const bg = tracks["Background"], main = tracks["Main"], acc = tracks["Accents"];
  const fx = tracks["FX / Colour"], cam = tracks["Camera"], trans = tracks["Transitions"];

  // clear only director-owned clips (the §9.8 contract)
  for (const tr of project.tracks) tr.clips = tr.clips.filter(c => !c.auto);

  const pick = (pool, rnd) => pool[Math.floor(rnd() * pool.length) % pool.length];

  analysis.sections.forEach((sec, i) => {
    const rnd = prng(project.seed * 7919 + i * 131);
    const type = POOLS.main[sec.type] ? sec.type : "groove";
    const intensity = POOLS.intensity[type];
    const opts = { auto: true, seed: project.seed * 100 + i };

    if (bg) bg.clips.push(makeClip(pick(POOLS.background[type], rnd), sec.start, sec.end,
      { intensity: intensity * 0.8 }, { ...opts, fadeIn: 0.4, fadeOut: 0.4 }));

    if (main) main.clips.push(makeClip(pick(POOLS.main[type], rnd), sec.start, sec.end,
      { intensity }, { ...opts, fadeIn: 0.25, fadeOut: 0.25 }));

    if (acc && POOLS.accents[type]) {
      acc.clips.push(makeClip(pick(POOLS.accents[type], rnd), sec.start, sec.end,
        { intensity: intensity * 0.9 }, opts));
    }

    if (cam) cam.clips.push(makeClip(POOLS.camera[type], sec.start, sec.end, {}, opts));

    if (fx) {
      // palette per section (kept from V1 Auto Show)
      const pals = AUTO_PALETTES[type] || AUTO_PALETTES.groove;
      const palette = pals[i % pals.length];
      fx.clips.push(makeClip("fx.palette", sec.start, sec.end, { palette }, opts));
      if (type === "drop") {
        fx.clips.push(makeClip("fx.strobe", sec.start, sec.end, { amount: 0.65 }, opts));
        fx.clips.push(makeClip("fx.rgbsplit", sec.start, sec.end, { shift: 5, intensity: 0.6 }, opts));
      }
      if (type === "build") {
        const buildFx = makeClip("fx.rgbsplit", sec.start, sec.end, { shift: 2 }, opts);
        buildFx.mods = [{ param: "shift", source: "buildProgress", amount: 0.5, attackMs: 0, releaseMs: 100, bypass: false }];
        fx.clips.push(buildFx);
      }
    }

    // transition flash at every section start except t=0 (kept from V1)
    if (trans && sec.start > 0.01) {
      trans.clips.push(makeClip(sec.type === "drop" ? "trans.flash" : "trans.flash",
        sec.start - 0.15, sec.start + 0.45, { peak: sec.type === "drop" ? 1 : 0.7 }, opts));
    }
  });

  for (const tr of project.tracks) sortTrack(tr);
}
