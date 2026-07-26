// Preset colour palettes for the rave engine.
// Each palette: ordered hues used for lasers/tunnels, plus a strobe colour
// and a background tint. Colours are [h, s, l] (deg, %, %).
"use strict";

const PALETTES = {
  cyberpunk: {
    label: "Cyberpunk",
    colors: [[315, 100, 60], [185, 100, 55], [265, 100, 65], [55, 100, 60]],
    strobe: [185, 100, 85],
    bg: [255, 60, 4],
  },
  synthwave: {
    label: "Synthwave",
    colors: [[300, 100, 62], [200, 100, 60], [260, 90, 62], [330, 100, 65]],
    strobe: [300, 100, 88],
    bg: [270, 55, 5],
  },
  hardstyle: {
    label: "Hardstyle",
    colors: [[0, 100, 55], [45, 100, 55], [0, 0, 95], [15, 100, 60]],
    strobe: [0, 0, 100],
    bg: [0, 45, 4],
  },
  techno: {
    label: "Techno",
    colors: [[0, 0, 92], [210, 15, 70], [0, 0, 60], [200, 30, 80]],
    strobe: [0, 0, 100],
    bg: [220, 15, 3],
  },
  psytrance: {
    label: "Psytrance",
    colors: [[120, 100, 55], [280, 100, 60], [30, 100, 55], [180, 100, 55]],
    strobe: [120, 100, 85],
    bg: [280, 50, 4],
  },
  trance: {
    label: "Trance",
    colors: [[210, 100, 60], [180, 100, 60], [240, 90, 65], [195, 100, 70]],
    strobe: [200, 100, 90],
    bg: [220, 60, 5],
  },
  dnb: {
    label: "Drum & Bass",
    colors: [[25, 100, 55], [55, 100, 55], [0, 90, 55], [40, 100, 65]],
    strobe: [45, 100, 85],
    bg: [20, 40, 4],
  },
  acid: {
    label: "Acid",
    colors: [[75, 100, 55], [60, 100, 50], [90, 100, 60], [50, 100, 55]],
    strobe: [70, 100, 85],
    bg: [90, 50, 3],
  },
  neon: {
    label: "Neon",
    colors: [[320, 100, 60], [160, 100, 55], [45, 100, 58], [210, 100, 60]],
    strobe: [0, 0, 100],
    bg: [260, 40, 4],
  },
  blacklight: {
    label: "Blacklight",
    colors: [[265, 100, 62], [285, 100, 58], [245, 100, 65], [300, 90, 60]],
    strobe: [270, 100, 88],
    bg: [265, 70, 4],
  },
};

function hsl(c, alpha = 1, lightBoost = 0) {
  const l = Math.min(100, c[2] + lightBoost);
  return `hsla(${c[0]}, ${c[1]}%, ${l}%, ${alpha})`;
}
