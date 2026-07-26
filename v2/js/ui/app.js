// App shell: shared state, screen router, playback clock, boot.
"use strict";

import { makeEngine } from "../core/engine.js";
import { makeHistory, autosave, commit as commitHistory } from "../core/project.js";
import { initHome } from "./home.js";
import { initAnalysisScreen } from "./analysisScreen.js";
import { initEditor } from "./editor.js";
import { initExport } from "./exportPage.js";

export const $ = id => document.getElementById(id);

// ---- playback: plays the source AudioBuffer; the render clock ----
class Playback {
  constructor() {
    this.ctx = new (window.AudioContext || window.webkitAudioContext)();
    this.gain = this.ctx.createGain();
    this.gain.connect(this.ctx.destination);
    this.streamDest = this.ctx.createMediaStreamDestination();
    this.gain.connect(this.streamDest);
    this.buffer = null; this.source = null;
    this.playing = false; this.startedAt = 0; this.offset = 0;
    this.onEnded = null;
  }
  get duration() { return this.buffer ? this.buffer.duration : 0; }
  get t() {
    if (!this.buffer) return 0;
    return this.playing ? Math.min(this.offset + this.ctx.currentTime - this.startedAt, this.duration) : this.offset;
  }
  play() {
    if (!this.buffer || this.playing) return;
    this.ctx.resume();
    this.source = this.ctx.createBufferSource();
    this.source.buffer = this.buffer;
    this.source.connect(this.gain);
    this.source.onended = () => {
      if (this.playing && this.t >= this.duration - 0.05) {
        this.playing = false; this.offset = 0;
        if (this.onEnded) this.onEnded();
      }
    };
    this.source.start(0, this.offset);
    this.startedAt = this.ctx.currentTime;
    this.playing = true;
  }
  pause() { if (!this.playing) return; this.offset = this.t; this._kill(); this.playing = false; }
  seek(t) {
    const was = this.playing;
    if (was) this._kill();
    this.playing = false;
    this.offset = Math.max(0, Math.min(t, this.duration));
    if (was) this.play();
  }
  _kill() {
    if (this.source) {
      this.source.onended = null;
      try { this.source.stop(); } catch (e) { /* noop */ }
      this.source.disconnect(); this.source = null;
    }
  }
}

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
