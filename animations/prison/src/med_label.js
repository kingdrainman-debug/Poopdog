// med_label.js · the opening medicine prompt (option 3, "medication label"), animated.
// A pharmacy / gas-canister label with an "LTX©" mark over a "d0ll-g4s" sub-label, small print and a barcode,
// inside the rounded hazard border whose stripes run clockwise, unbroken round the corners (see med_common.js).
// Keyable: exact #00FF00 background, every pixel opaque. Loops seamlessly every 5 s.
(() => {
  const { C, PX, AB, RX } = MED;
  function drawStill() {                                        // everything except the moving border
    MED.panel();
    MED.brand();
    UI.paint(UI.text('PREPARE FOR', `19px ${AB}`, RX, 112), C.black);
    UI.paint(UI.text('YOUR MEDICINE', `19px ${AB}`, RX, 136), C.black);
    UI.paint(UI.mask(c => c.fillRect(RX, 166, 498 - RX, 1)), C.black);
    UI.paint(UI.text('INHALATION AEROSOL', `8px ${PX}`, RX, 174), C.black);
    UI.paint(UI.text('1 DOSE - AS DIRECTED', `8px ${PX}`, RX, 186), C.grey);
    UI.paint(UI.rrect(RX, 204, 126, 14, 7), C.cyan, C.black, 1);
    UI.paint(UI.text('BEGINS SHORTLY', `8px ${PX}`, RX + 63, 207, 'center'), C.black);
    UI.paint(UI.mask(c => { for (let i = 0, x = 398; x < 497; i++) { const w = 1 + ((i * 7919) % 3); if (i % 2 === 0) c.fillRect(x, 202, w, 30); x += w + (((i * 104729) >> 2) % 2) + 1; } }), C.black);
    UI.paint(UI.text('U-R-RBBR-D011', `8px ${PX}`, 447, 236, 'center'), C.black);
  }
  SCENES['med_label'] = {
    draw(c, lt) {
      MED.bg();
      drawStill();
      MED.border(lt);
      HD.present(c);
    },
  };
})();
