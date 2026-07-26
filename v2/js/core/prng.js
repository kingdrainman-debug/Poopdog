// Seeded deterministic PRNG (mulberry32). The determinism law (spec §3):
// every random visual decision flows from a seed stored in the project.
"use strict";

export function prng(seed) {
  let a = (seed >>> 0) || 1;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Stable per-(seed, index) value without consuming a stream — lets a
// generator ask "random value #k for particle #i" at any time t.
export function hash01(seed, i) {
  let h = (seed ^ (i * 0x9E3779B9)) >>> 0;
  h = Math.imul(h ^ (h >>> 16), 0x21f0aaad) >>> 0;
  h = Math.imul(h ^ (h >>> 15), 0x735a2d97) >>> 0;
  return ((h ^ (h >>> 15)) >>> 0) / 4294967296;
}
