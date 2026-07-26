// Inspector panel (spec §10): universal controls, effect-specific params,
// modulation slots, mode switch, randomise/reset/delete.
"use strict";

import { $ } from "./app.js";
import { REGISTRY } from "../core/generators.js";
import { UNIVERSAL, BLENDS, numericParams } from "../core/engine.js";
import { ALL_SOURCES } from "../core/modulation.js";
import { findClip, removeClip } from "../core/project.js";
import { hash01 } from "../core/prng.js";

export function initInspector(app) {
  const body = $("in-body"), empty = $("in-empty");
  let commitTimer = null;

  const touched = clip => { clip.auto = false; scheduleCommit(); };
  function scheduleCommit() {
    clearTimeout(commitTimer);
    commitTimer = setTimeout(() => app.commit(), 400);
  }

  function render() {
    const found = app.selection && app.project ? findClip(app.project, app.selection) : null;
    if (!found) { body.hidden = true; empty.hidden = false; return; }
    const { clip, track } = found;
    const gen = REGISTRY.get(clip.gen);
    if (!gen) { body.hidden = true; empty.hidden = false; return; }
    body.hidden = false; empty.hidden = true;
    body.innerHTML = "";

    // ---- header ----
    const head = el(`<div class="in-head">
      <span class="name">${gen.name}</span>
      <button class="mini" data-act="rand" title="Randomise">🎲</button>
      <button class="mini" data-act="reset" title="Reset">↺</button>
      <button class="mini" data-act="del" title="Delete">✕</button>
    </div>
    <div class="dim" style="font-size:11px">${gen.category} · ${track.name}${clip.auto ? " · ⚡ director" : ""}</div>`);
    body.appendChild(head);
    body.querySelector('[data-act="del"]').onclick = () => {
      removeClip(app.project, clip.id);
      app.selection = null;
      app.commit();
      app.emit("selectionChanged");
    };
    body.querySelector('[data-act="reset"]').onclick = () => {
      clip.params = {}; clip.mods = JSON.parse(JSON.stringify(gen.defaultMods || []));
      touched(clip); render();
    };
    body.querySelector('[data-act="rand"]').onclick = () => {
      clip.seed = Math.floor(Math.random() * 99999);
      for (const p of gen.params) {
        if (p.select !== undefined) continue;
        const base = clip.params[p.key] ?? p.def;
        const jit = (hash01(clip.seed, p.key.length) - 0.5) * 0.3 * (p.max - p.min);
        clip.params[p.key] = clamp(base + jit, p.min, p.max, p.step);
      }
      touched(clip); render();
    };

    // ---- mode switch (spec §8.3) ----
    const seg = el(`<div class="seg">
      <button data-m="auto">Auto reactive</button>
      <button data-m="manual">Manual</button>
    </div>`);
    body.appendChild(seg);
    for (const b of seg.querySelectorAll("button")) {
      b.classList.toggle("on", clip.mode === b.dataset.m);
      b.onclick = () => { clip.mode = b.dataset.m; touched(clip); render(); };
    }

    // ---- timing ----
    body.appendChild(el(`<h4>Timing</h4>`));
    numRow(body, "Fade in", clip.fadeIn, 0, 5, 0.05, v => { clip.fadeIn = v; touched(clip); });
    numRow(body, "Fade out", clip.fadeOut, 0, 5, 0.05, v => { clip.fadeOut = v; touched(clip); });

    // ---- universal ----
    body.appendChild(el(`<h4>Universal</h4>`));
    for (const d of UNIVERSAL) {
      numRow(body, d.label, clip.params[d.key] ?? d.def, d.min, d.max, d.step,
        v => { clip.params[d.key] = v; touched(clip); }, hasMod(clip, d.key));
    }
    if (gen.track === "visual") {
      selRow(body, "Blend", clip.params.blend || "add", BLENDS, v => { clip.params.blend = v; touched(clip); });
      numRow(body, "Palette slot", clip.params.palIdx ?? 0, 0, 3, 1, v => { clip.params.palIdx = v; touched(clip); });
    }

    // ---- effect-specific ----
    if (gen.params.length) {
      body.appendChild(el(`<h4>${gen.name}</h4>`));
      for (const d of gen.params) {
        if (d.select !== undefined) {
          selRow(body, d.label, clip.params[d.key] ?? d.def, d.select, v => { clip.params[d.key] = v; touched(clip); });
        } else {
          numRow(body, d.label, clip.params[d.key] ?? d.def, d.min, d.max, d.step,
            v => { clip.params[d.key] = v; touched(clip); }, hasMod(clip, d.key));
        }
      }
    }

    // ---- modulation (spec §8) ----
    body.appendChild(el(`<h4>Audio modulation</h4>`));
    clip.mods.forEach((m, i) => {
      const slot = el(`<div class="modslot">
        <button class="mini rm">✕</button>
        <div class="in-row"><label>Param</label><select data-k="param"></select></div>
        <div class="in-row"><label>Source</label><select data-k="source"></select></div>
      </div>`);
      const pSel = slot.querySelector('[data-k="param"]');
      for (const p of numericParams(gen)) pSel.add(new Option(p.label, p.key, false, m.param === p.key));
      pSel.onchange = () => { m.param = pSel.value; touched(clip); };
      const sSel = slot.querySelector('[data-k="source"]');
      for (const s of ALL_SOURCES) sSel.add(new Option(s, s, false, m.source === s));
      sSel.onchange = () => { m.source = sSel.value; touched(clip); };
      numRow(slot, "Amount", m.amount, -1, 1, 0.01, v => { m.amount = v; touched(clip); });
      numRow(slot, "Attack ms", m.attackMs ?? 0, 0, 500, 5, v => { m.attackMs = v; touched(clip); });
      numRow(slot, "Release ms", m.releaseMs ?? 150, 10, 1500, 5, v => { m.releaseMs = v; touched(clip); });
      slot.querySelector(".rm").onclick = () => { clip.mods.splice(i, 1); touched(clip); render(); };
      body.appendChild(slot);
    });
    const add = el(`<button class="mini" style="width:100%">+ Add modulation</button>`);
    add.onclick = () => {
      clip.mods.push({ param: "intensity", source: "kick", amount: 0.5, attackMs: 0, releaseMs: 150, bypass: false });
      touched(clip); render();
    };
    body.appendChild(add);
  }

  const hasMod = (clip, key) => clip.mods && clip.mods.some(m => m.param === key && !m.bypass);
  const clamp = (v, min, max, step) => {
    v = Math.max(min, Math.min(max, v));
    return step >= 1 ? Math.round(v) : Math.round(v / step) * step;
  };

  function el(html) {
    const d = document.createElement("div");
    d.innerHTML = html;
    return d.children.length === 1 ? d.firstElementChild : d;
  }

  function numRow(parent, label, value, min, max, step, onInput, modded = false) {
    const row = el(`<div class="in-row">
      <label>${modded ? "〜 " : ""}${label}</label>
      <input type="range" min="${min}" max="${max}" step="${step}" value="${value}">
      <span class="val">${fmtVal(value, step)}</span>
    </div>`);
    const input = row.querySelector("input"), val = row.querySelector(".val");
    input.addEventListener("input", () => {
      const v = parseFloat(input.value);
      val.textContent = fmtVal(v, step);
      onInput(v);
    });
    input.addEventListener("dblclick", () => {   // dbl-click = type a value
      const v = prompt(label, input.value);
      if (v !== null && !isNaN(parseFloat(v))) {
        input.value = parseFloat(v);
        input.dispatchEvent(new Event("input"));
      }
    });
    parent.appendChild(row);
  }
  const fmtVal = (v, step) => step >= 1 ? String(Math.round(v)) : (+v).toFixed(2);

  function selRow(parent, label, value, options, onChange) {
    const row = el(`<div class="in-row"><label>${label}</label><select></select></div>`);
    const sel = row.querySelector("select");
    for (const o of options) sel.add(new Option(o, o, false, o === value));
    sel.onchange = () => onChange(sel.value);
    parent.appendChild(row);
  }

  app.on("selectionChanged", render);
  app.on("projectChanged", () => { if (!body.hidden) render(); });
}
