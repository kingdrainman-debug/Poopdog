// ShowDirector — Auto Show Mode.
//
// Takes the offline analysis and generates a full visual show timeline:
// each detected section gets a scene, palette behaviour and intensity that
// match its musical role (drops get lasers+strobe, breakdowns calm down...).
// Variety comes from a seeded rotation through scene/mode combos so the
// same track always generates the same show.
"use strict";

class ShowDirector {
  constructor() {
    this.cues = [];        // [{ start, end, type, scene, laserMode, tunnelType, intensity, strobeAmt, speed }]
    this.enabled = false;
  }

  // section type -> pool of scene setups the director rotates through
  static POOLS = {
    intro: [
      { scene: "tunnel", tunnelType: "neon", intensity: 0.4, strobeAmt: 0.0, speed: 0.5 },
      { scene: "particles", intensity: 0.35, strobeAmt: 0.0, speed: 0.4 },
    ],
    build: [
      { scene: "tunnel", tunnelType: "wireframe", intensity: 0.7, strobeAmt: 0.25, speed: 1.1 },
      { scene: "lasers", laserMode: "circle", intensity: 0.65, strobeAmt: 0.2, speed: 1.0 },
      { scene: "tunnel", tunnelType: "hex", intensity: 0.7, strobeAmt: 0.3, speed: 1.2 },
    ],
    drop: [
      { scene: "lasers", laserMode: "fan", intensity: 1.0, strobeAmt: 0.8, speed: 1.6 },
      { scene: "lasers", laserMode: "burst", intensity: 1.0, strobeAmt: 0.9, speed: 1.7 },
      { scene: "tunnel", tunnelType: "grid", intensity: 1.0, strobeAmt: 0.7, speed: 2.0 },
      { scene: "lasers", laserMode: "cross", intensity: 0.95, strobeAmt: 0.8, speed: 1.5 },
      { scene: "strobe", intensity: 1.0, strobeAmt: 1.0, speed: 1.5 },
    ],
    breakdown: [
      { scene: "particles", intensity: 0.45, strobeAmt: 0.05, speed: 0.5 },
      { scene: "tunnel", tunnelType: "particle", intensity: 0.5, strobeAmt: 0.0, speed: 0.6 },
      { scene: "lasers", laserMode: "spiral", intensity: 0.5, strobeAmt: 0.1, speed: 0.6 },
    ],
    groove: [
      { scene: "tunnel", tunnelType: "hex", intensity: 0.7, strobeAmt: 0.35, speed: 1.0 },
      { scene: "lasers", laserMode: "horizontal", intensity: 0.7, strobeAmt: 0.4, speed: 1.0 },
      { scene: "lasers", laserMode: "circle", intensity: 0.7, strobeAmt: 0.35, speed: 1.0 },
      { scene: "tunnel", tunnelType: "neon", intensity: 0.75, strobeAmt: 0.4, speed: 1.1 },
    ],
    outro: [
      { scene: "particles", intensity: 0.35, strobeAmt: 0.0, speed: 0.4 },
      { scene: "tunnel", tunnelType: "neon", intensity: 0.4, strobeAmt: 0.0, speed: 0.5 },
    ],
  };

  generate(analysis) {
    const counters = {};
    this.cues = analysis.sections.map(sec => {
      const pool = ShowDirector.POOLS[sec.type] || ShowDirector.POOLS.groove;
      const idx = counters[sec.type] = (counters[sec.type] ?? -1) + 1;
      const setup = pool[idx % pool.length];
      return { start: sec.start, end: sec.end, type: sec.type, ...setup };
    });
    this.enabled = true;
    return this.cues;
  }

  cueAt(t) {
    for (const c of this.cues) if (t >= c.start && t < c.end) return c;
    return this.cues[this.cues.length - 1] || null;
  }
}
