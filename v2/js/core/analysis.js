// Offline analysis service (spec §5). Pure: AudioBuffer -> Analysis.
// Everything downstream (director, modulation, render) reads only this,
// which is what makes preview and export bit-identical (§3 determinism).
//
// Analysis = {
//   sampleRate, duration, frameDur,          // frameDur = hop/sr (~11.6ms)
//   bpm, beats: Float64Array, bars: Float64Array,
//   curves: { bass, mid, high, energy, loudness },  // Float32Array per frame, 0..1
//   events: { kick, snare, hat, drop },      // [{t, strength}] sorted by t
//   sections: [{ start, end, type }],
//   waveform: Float32Array                   // ~2000 peak values for drawing
// }
"use strict";

const FFT_SIZE = 1024, HOP = 512;

// ---- radix-2 FFT (real input, magnitudes out) ----
function makeFFT(n) {
  const rev = new Uint32Array(n);
  for (let i = 0, j = 0; i < n; i++) {
    rev[i] = j;
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j |= bit;
  }
  const cos = new Float32Array(n / 2), sin = new Float32Array(n / 2);
  for (let i = 0; i < n / 2; i++) { cos[i] = Math.cos(-2 * Math.PI * i / n); sin[i] = Math.sin(-2 * Math.PI * i / n); }
  const re = new Float32Array(n), im = new Float32Array(n);
  return function fft(input, mags) {
    for (let i = 0; i < n; i++) { re[i] = input[rev[i]]; im[i] = 0; }
    for (let len = 2; len <= n; len <<= 1) {
      const half = len >> 1, step = n / len;
      for (let i = 0; i < n; i += len) {
        for (let j = 0; j < half; j++) {
          const k = j * step;
          const tr = re[i + j + half] * cos[k] - im[i + j + half] * sin[k];
          const ti = re[i + j + half] * sin[k] + im[i + j + half] * cos[k];
          re[i + j + half] = re[i + j] - tr; im[i + j + half] = im[i + j] - ti;
          re[i + j] += tr; im[i + j] += ti;
        }
      }
    }
    for (let i = 0; i < n / 2; i++) mags[i] = Math.hypot(re[i], im[i]) / n;
  };
}

export async function analyze(buffer, onProgress) {
  const say = m => { if (onProgress) onProgress(m); };
  const sr = buffer.sampleRate, n = buffer.length;
  const mono = new Float32Array(n);
  for (let c = 0; c < buffer.numberOfChannels; c++) {
    const d = buffer.getChannelData(c);
    for (let i = 0; i < n; i++) mono[i] += d[i] / buffer.numberOfChannels;
  }

  say("Computing spectrum…");
  const frames = Math.max(1, Math.floor((n - FFT_SIZE) / HOP));
  const frameDur = HOP / sr;
  const fft = makeFFT(FFT_SIZE);
  const window = new Float32Array(FFT_SIZE);
  for (let i = 0; i < FFT_SIZE; i++) window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT_SIZE);
  const binHz = sr / FFT_SIZE;
  const bin = hz => Math.max(0, Math.min(FFT_SIZE / 2 - 1, Math.round(hz / binHz)));

  const BANDS = {                       // V1 bands kept (§5.1)
    sub: [bin(30), bin(120)], lowmid: [bin(150), bin(400)],
    bass: [bin(30), bin(150)], mid: [bin(400), bin(2500)],
    high: [bin(4000), bin(12000)], veryhigh: [bin(6000), bin(14000)],
    full: [bin(30), bin(14000)],
  };
  const bandE = {};
  for (const k in BANDS) bandE[k] = new Float32Array(frames);

  const frame = new Float32Array(FFT_SIZE), mags = new Float32Array(FFT_SIZE / 2);
  for (let f = 0; f < frames; f++) {
    const off = f * HOP;
    for (let i = 0; i < FFT_SIZE; i++) frame[i] = mono[off + i] * window[i];
    fft(frame, mags);
    for (const k in BANDS) {
      const [lo, hi] = BANDS[k];
      let s = 0;
      for (let b = lo; b <= hi; b++) s += mags[b];
      bandE[k][f] = s / (hi - lo + 1);
    }
    if ((f & 2047) === 0) await tick();      // keep UI alive on long mixes
  }

  say("Detecting hits…");
  const flux = {};
  for (const k of ["sub", "lowmid", "veryhigh", "full"]) {
    flux[k] = new Float32Array(frames);
    for (let f = 1; f < frames; f++) flux[k][f] = Math.max(0, bandE[k][f] - bandE[k][f - 1]);
  }
  const kick = pickEvents(flux.sub, frameDur, 1.9, 0.16);
  const snare = pickEvents(flux.lowmid, frameDur, 2.2, 0.20);
  const hat = pickEvents(flux.veryhigh, frameDur, 2.0, 0.10);

  say("Detecting BPM…");
  const { bpm, beats, bars } = tempo(flux.full, frameDur, buffer.duration);

  say("Finding structure…");
  const energy = smoothNorm(bandE.full, Math.round(1 / frameDur));
  const sections = detectSections(energy, frameDur, buffer.duration);
  const drop = sections.filter(s => s.type === "drop")
    .map(s => ({ t: s.start, strength: 1 }));

  // normalised curves for modulation (0..1 against 95th percentile)
  const curves = {
    bass: normCurve(bandE.bass), mid: normCurve(bandE.mid),
    high: normCurve(bandE.high), energy, loudness: normCurve(bandE.full),
  };

  // waveform peaks for drawing
  const W = 2000, waveform = new Float32Array(W);
  const per = Math.max(1, Math.floor(n / W));
  for (let i = 0; i < W; i++) {
    let m = 0;
    for (let j = i * per; j < Math.min(n, (i + 1) * per); j += 16) m = Math.max(m, Math.abs(mono[j]));
    waveform[i] = m;
  }

  return {
    sampleRate: sr, duration: buffer.duration, frameDur,
    bpm, beats, bars, curves,
    events: { kick, snare, hat, drop },
    sections, waveform,
  };
}

const tick = () => new Promise(r => setTimeout(r, 0));

// adaptive-threshold peak picking over a flux curve (V1 rolling-average
// idea, offline: local mean over ~1s window, refractory period)
function pickEvents(flux, frameDur, sens, refractory) {
  const win = Math.round(1 / frameDur);
  const out = [];
  let sum = 0;
  const q = [];
  let peak = 0;
  for (let f = 0; f < flux.length; f++) peak = Math.max(peak, flux[f]);
  if (peak <= 0) return out;
  let lastT = -1;
  for (let f = 0; f < flux.length; f++) {
    sum += flux[f]; q.push(flux[f]);
    if (q.length > win) sum -= q.shift();
    const mean = sum / q.length;
    const t = f * frameDur;
    const isPeak = f > 0 && f < flux.length - 1 && flux[f] >= flux[f - 1] && flux[f] >= flux[f + 1];
    if (isPeak && flux[f] > mean * sens && flux[f] > peak * 0.05 && t - lastT > refractory) {
      out.push({ t, strength: Math.min(1, flux[f] / (peak * 0.6)) });
      lastT = t;
    }
  }
  return out;
}

// BPM via onset autocorrelation (kept from V1) + grid phase search
function tempo(onset, frameDur, duration) {
  const len = Math.min(onset.length, Math.round(90 / frameDur));
  const minLag = Math.round(60 / (180 * frameDur)), maxLag = Math.round(60 / (80 * frameDur));
  let bestLag = minLag, bestScore = -1;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let s = 0;
    for (let i = 0; i < len - lag; i++) s += onset[i] * onset[i + lag];
    s *= 1 + (maxLag - lag) / maxLag * 0.08;
    if (s > bestScore) { bestScore = s; bestLag = lag; }
  }
  const bpm = Math.round(60 / (bestLag * frameDur) * 10) / 10;
  const period = 60 / bpm;

  // phase: maximise summed onset at grid positions over first 30s
  const phaseSteps = Math.max(1, Math.round(period / frameDur));
  let bestPhase = 0, bestP = -1;
  for (let p = 0; p < phaseSteps; p++) {
    let s = 0;
    for (let t = p * frameDur; t < Math.min(30, duration); t += period) {
      const f = Math.round(t / frameDur);
      if (f < onset.length) s += onset[f];
    }
    if (s > bestP) { bestP = s; bestPhase = p; }
  }
  const t0 = bestPhase * frameDur;
  const beats = [];
  for (let t = t0; t < duration; t += period) beats.push(t);
  const bars = beats.filter((_, i) => i % 4 === 0);
  return { bpm, beats: Float64Array.from(beats), bars: Float64Array.from(bars) };
}

function smoothNorm(arr, win) {
  const out = new Float32Array(arr.length);
  let sum = 0;
  for (let i = 0; i < arr.length; i++) {
    sum += arr[i];
    if (i >= win) sum -= arr[i - win];
    out[i] = sum / Math.min(i + 1, win);
  }
  const peak = Math.max(...out) || 1;
  for (let i = 0; i < out.length; i++) out[i] /= peak;
  return out;
}

function normCurve(arr) {
  const sorted = Float32Array.from(arr).sort();
  const ref = sorted[Math.floor(sorted.length * 0.95)] || 1;
  const out = new Float32Array(arr.length);
  for (let i = 0; i < arr.length; i++) out[i] = Math.min(1, arr[i] / ref);
  return out;
}

// V1 energy-quantile segmentation (§5.1), with build detection kept
export function detectSections(energy, frameDur, duration) {
  const bf = Math.round(1 / frameDur), nB = Math.ceil(energy.length / bf);
  const bE = new Float32Array(nB);
  for (let b = 0; b < nB; b++) {
    let s = 0, c = 0;
    for (let f = b * bf; f < Math.min(energy.length, (b + 1) * bf); f++) { s += energy[f]; c++; }
    bE[b] = c ? s / c : 0;
  }
  const sorted = Array.from(bE).sort((a, b) => a - b);
  const q = p => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))];
  const hi = q(0.65), lo = q(0.30);
  const labels = Array.from(bE, e => e >= hi ? "high" : e <= lo ? "low" : "mid");
  let runs = [];
  for (let b = 0; b < nB; b++) {
    if (runs.length && runs[runs.length - 1].label === labels[b]) runs[runs.length - 1].len++;
    else runs.push({ label: labels[b], start: b, len: 1 });
  }
  runs = runs.filter(r => r.len >= 4 || runs.length === 1);
  if (!runs.length) runs.push({ label: "mid", start: 0, len: nB });
  runs[0].start = 0;
  for (let i = 0; i < runs.length; i++) {
    const end = i + 1 < runs.length ? runs[i + 1].start : nB;
    runs[i].len = end - runs[i].start;
  }
  const sections = [];
  let seenHigh = false;
  for (let i = 0; i < runs.length; i++) {
    const r = runs[i], start = r.start, end = Math.min(duration, r.start + r.len);
    let type;
    if (r.label === "high") { type = "drop"; seenHigh = true; }
    else if (r.label === "low") type = i === 0 ? "intro" : i === runs.length - 1 ? "outro" : seenHigh ? "breakdown" : "intro";
    else type = runs[i + 1] && runs[i + 1].label === "high" ? "build" : "groove";
    sections.push({ start, end, type });
  }
  return sections.length ? sections : [{ start: 0, end: duration, type: "groove" }];
}

export const sectionAt = (sections, t) =>
  sections.find(s => t >= s.start && t < s.end) ||
  (sections.length && t < sections[0].start ? sections[0] : sections[sections.length - 1]) || null;
