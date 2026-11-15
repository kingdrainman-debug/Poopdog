// Breathing stages (INHALE 4 · HOLD 3 · EXHALE) in three candidate layouts A/B/C, on chroma green.
window.SCENE_LIBS = ['hd_lib.js', 'prison_palette.js', 'ui_lib.js', 'med_common.js', 'med_breath.js']; window.PUNCH = 0;
window.ERAS = ['A', 'B', 'C'].flatMap(s => ['in', 'hold', 'out'].map(k => ({ id: `br${s}_${k}`, dur: 5 })));
