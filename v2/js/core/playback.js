// Shared playback clock (desktop + mobile shells). Plays the source
// AudioBuffer; its `t` is the time every render reads.
"use strict";

export class Playback {
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
