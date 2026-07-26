// App shell: shared state, screen router, playback clock, boot.
"use strict";

import { makeEngine } from "../core/engine.js";
import { Playback } from "../core/playback.js";
import { makeHistory, autosave, commit as commitHistory } from "../core/project.js";
import { initHome } from "./home.js";
import { initAnalysisScreen } from "./analysisScreen.js";
import { initEditor } from "./editor.js";
import { initExport } from "./exportPage.js";

export const $ = id => document.getElementById(id);

export const app = {
  project: null,
  analysis: null,
  audioBuffer: null,
  history: null,
  playback: new Playback(),
  engine: makeEngine(),
  selection: null,          // selected clip id
  listeners: {},

  on(ev, fn) { (this.listeners[ev] ||= []).push(fn); },
  emit(ev, ...args) { for (const fn of this.listeners[ev] || []) fn(...args); },

  show(screen) {
    for (const s of ["home", "analysis", "editor"]) $("scr-" + s).hidden = s !== screen;
    this.emit("screen", screen);
  },

  toast(msg, ms = 3200) {
    const el = $("toast");
    el.textContent = msg;
    el.hidden = false;
    clearTimeout(this._toastT);
    this._toastT = setTimeout(() => { el.hidden = true; }, ms);
  },

  setHistory() { this.history = makeHistory(this.project); },
  commit() {
    if (this.history) commitHistory(this.history, this.project);
    this.emit("projectChanged");
  },
};

// autosave loop (spec §15.6): every 2 minutes when a project is open
setInterval(() => { if (app.project) autosave(app.project); }, 120000);
window.addEventListener("beforeunload", () => { if (app.project) autosave(app.project); });

initHome(app);
initAnalysisScreen(app);
initEditor(app);
initExport(app);
app.show("home");

window.__poopdogBooted = true;
