// AudioEngine — playback + realtime feature extraction.
//
// Exposes per-frame features that visuals bind to:
//   bass, mids, highs, loudness  — smoothed band energies (0..1)
//   kick, snare                  — true on the frame a hit is detected
//   kickEnv, snareEnv            — decaying envelopes (0..1) for smooth reaction
"use strict";

class AudioEngine {
  constructor() {
    this.ctx = new (window.AudioContext || window.webkitAudioContext)();
    this.analyser = this.ctx.createAnalyser();
    this.analyser.fftSize = 2048;
    this.analyser.smoothingTimeConstant = 0.5;

    // Recording tap: everything routed through masterGain so export can
    // capture the audio via a MediaStreamDestination.
    this.masterGain = this.ctx.createGain();
    this.masterGain.connect(this.analyser);
    this.analyser.connect(this.ctx.destination);
    this.streamDest = this.ctx.createMediaStreamDestination();
    this.masterGain.connect(this.streamDest);

    this.freqData = new Uint8Array(this.analyser.frequencyBinCount);

    this.buffer = null;
    this.source = null;
    this.playing = false;
    this.startedAt = 0;   // ctx.currentTime when playback started
    this.offset = 0;      // seek offset in seconds

    this.beatSensitivity = 1.3;

    // feature state
    this.features = {
      bass: 0, mids: 0, highs: 0, loudness: 0,
      kick: false, snare: false, kickEnv: 0, snareEnv: 0,
    };
    this._bassHistory = new Float32Array(43);   // ~0.7s at 60fps
    this._midHistory = new Float32Array(43);
    this._histIdx = 0;
    this._lastKickTime = 0;
    this._lastSnareTime = 0;
  }

  async load(arrayBuffer) {
    this.stop();
    this.buffer = await this.ctx.decodeAudioData(arrayBuffer);
    this.offset = 0;
    return this.buffer;
  }

  get duration() { return this.buffer ? this.buffer.duration : 0; }

  get currentTime() {
    if (!this.buffer) return 0;
    if (!this.playing) return this.offset;
    return Math.min(this.offset + this.ctx.currentTime - this.startedAt, this.duration);
  }

  play() {
    if (!this.buffer || this.playing) return;
    this.ctx.resume();
    this.source = this.ctx.createBufferSource();
    this.source.buffer = this.buffer;
    this.source.connect(this.masterGain);
    this.source.onended = () => {
      if (this.playing && this.currentTime >= this.duration - 0.05) {
        this.playing = false;
        this.offset = 0;
        if (this.onEnded) this.onEnded();
      }
    };
    this.source.start(0, this.offset);
    this.startedAt = this.ctx.currentTime;
    this.playing = true;
  }

  pause() {
    if (!this.playing) return;
    this.offset = this.currentTime;
    this._stopSource();
    this.playing = false;
  }

  stop() {
    this._stopSource();
    this.playing = false;
    this.offset = 0;
  }

  seek(t) {
    const wasPlaying = this.playing;
    if (wasPlaying) this._stopSource();
    this.playing = false;
    this.offset = Math.max(0, Math.min(t, this.duration));
    if (wasPlaying) this.play();
  }

  _stopSource() {
    if (this.source) {
      this.source.onended = null;
      try { this.source.stop(); } catch (e) { /* already stopped */ }
      this.source.disconnect();
      this.source = null;
    }
  }

  // Call once per animation frame.
  update() {
    const f = this.features;
    f.kick = false;
    f.snare = false;

    this.analyser.getByteFrequencyData(this.freqData);
    const bins = this.freqData;
    const binHz = this.ctx.sampleRate / this.analyser.fftSize;

    const avg = (loHz, hiHz) => {
      const lo = Math.max(0, Math.floor(loHz / binHz));
      const hi = Math.min(bins.length - 1, Math.ceil(hiHz / binHz));
      let sum = 0;
      for (let i = lo; i <= hi; i++) sum += bins[i];
      return sum / ((hi - lo + 1) * 255);
    };

    const bass = avg(30, 150);
    const mids = avg(400, 2500);
    const highs = avg(4000, 12000);
    const loud = avg(30, 14000);

    // smooth (fast attack, slower release)
    const smooth = (prev, next) => next > prev ? prev + (next - prev) * 0.55 : prev + (next - prev) * 0.12;
    f.bass = smooth(f.bass, bass);
    f.mids = smooth(f.mids, mids);
    f.highs = smooth(f.highs, highs);
    f.loudness = smooth(f.loudness, loud);

    // --- beat detection: energy vs rolling average, per band ---
    const histLen = this._bassHistory.length;
    let bassAvg = 0, midAvg = 0;
    for (let i = 0; i < histLen; i++) { bassAvg += this._bassHistory[i]; midAvg += this._midHistory[i]; }
    bassAvg /= histLen; midAvg /= histLen;

    const now = this.ctx.currentTime;
    const sens = this.beatSensitivity;
    if (this.playing) {
      if (bass > bassAvg * sens && bass > 0.15 && now - this._lastKickTime > 0.18) {
        f.kick = true;
        f.kickEnv = 1;
        this._lastKickTime = now;
      }
      if (mids > midAvg * (sens + 0.15) && mids > 0.12 && now - this._lastSnareTime > 0.22) {
        f.snare = true;
        f.snareEnv = 1;
        this._lastSnareTime = now;
      }
    }

    this._bassHistory[this._histIdx] = bass;
    this._midHistory[this._histIdx] = mids;
    this._histIdx = (this._histIdx + 1) % histLen;

    // decay envelopes
    f.kickEnv *= 0.90;
    f.snareEnv *= 0.88;
    if (f.kickEnv < 0.001) f.kickEnv = 0;
    if (f.snareEnv < 0.001) f.snareEnv = 0;

    return f;
  }
}
