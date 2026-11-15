// prison_palette.js · the shared colour ramp for every layer of the prison film: near-black through rust and
// orange to a hot amber. Every prison layer picks its tones from this one ramp so they composite as one world.
window.PRISON = (() => {
  const R = HD.ramp(['#050302', '#1a0b03', '#3d1704', '#6e2a05', '#a8420a', '#e0600f', '#ff8418', '#ffad3d', '#ffd98a']);
  return { R };
})();
