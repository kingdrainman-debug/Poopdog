// Offline track analysis — the "understand the music first" layer.
//
// analyzeTrack(audioBuffer) -> {
//   bpm,                 detected tempo
//   energy: Float32Array smoothed loudness curve (one value per frame)
//   frameDur,            seconds per energy frame
//   sections: [{ start, end, type }]   type: intro|build|drop|breakdown|outro|groove
//   beats: [t, ...]      estimated beat times (seconds)
// }
//
// Runs on a downmixed copy of the decoded buffer; pure JS, no dependencies.
"use strict";

function analyzeTrack(buffer, onProgress) {
  const sr = buffer.sampleRate;
  const n = buffer.length;

  // downmix to mono
  const mono = new Float32Array(n);
  for (let c = 0; c < buffer.numberOfChannels; c++) {
    const data = buffer.getChannelData(c);
    for (let i = 0; i < n; i++) mono[i] += data[i] / buffer.numberOfChannels;
  }
  if (onProgress) onProgress("Computing energy envelope…");

  // --- frame energy (RMS) ---
  const hop = 1024;                       // ~23ms at 44.1k
  const frames = Math.floor(n / hop);
  const frameDur = hop / sr;
  const rms = new Float32Array(frames);
  for (let f = 0; f < frames; f++) {
    let sum = 0;
    const off = f * hop;
    for (let i = 0; i < hop; i++) { const s = mono[off + i]; sum += s * s; }
    rms[f] = Math.sqrt(sum / hop);
  }

  // onset strength: positive energy difference (rectified flux)
  const onset = new Float32Array(frames);
  for (let f = 1; f < frames; f++) onset[f] = Math.max(0, rms[f] - rms[f - 1]);

  if (onProgress) onProgress("Detecting BPM…");
  const bpm = detectBPM(onset, frameDur);

  if (onProgress) onProgress("Finding drops & builds…");

  // --- smoothed energy curve for section detection (~1s window) ---
  const win = Math.max(1, Math.round(1 / frameDur));
  const energy = movingAverage(rms, win);
  const peak = Math.max(...energy) || 1;
  for (let i = 0; i < frames; i++) energy[i] /= peak;

  const sections = detectSections(energy, frameDur);
  const beats = beatGrid(onset, frameDur, bpm, buffer.duration);

  return { bpm, energy, frameDur, sections, beats };
}

function movingAverage(arr, win) {
  const out = new Float32Array(arr.length);
  let sum = 0;
  for (let i = 0; i < arr.length; i++) {
    sum += arr[i];
    if (i >= win) sum -= arr[i - win];
    out[i] = sum / Math.min(i + 1, win);
  }
  return out;
}

// Autocorrelation of the onset envelope over the 80–180 BPM lag range.
function detectBPM(onset, frameDur) {
  // use up to the first ~90 seconds — enough to lock tempo, keeps it fast
  const len = Math.min(onset.length, Math.round(90 / frameDur));
  const minBPM = 80, maxBPM = 180;
  const minLag = Math.round(60 / (maxBPM * frameDur));
  const maxLag = Math.round(60 / (minBPM * frameDur));

  let bestLag = minLag, bestScore = -1;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let score = 0;
    for (let i = 0; i < len - lag; i++) score += onset[i] * onset[i + lag];
    // slight bias toward faster tempos to break octave ties in dance music
    score *= 1 + (maxLag - lag) / maxLag * 0.08;
    if (score > bestScore) { bestScore = score; bestLag = lag; }
  }
  return Math.round((60 / (bestLag * frameDur)) * 10) / 10;
}

// Snap a regular beat grid to the strongest onsets near each expected beat.
function beatGrid(onset, frameDur, bpm, duration) {
  const period = 60 / bpm;
  // find phase: strongest onset in the first two beat periods
  const searchFrames = Math.min(onset.length, Math.round((period * 2) / frameDur));
  let phaseFrame = 0, best = -1;
  for (let i = 0; i < searchFrames; i++) {
    if (onset[i] > best) { best = onset[i]; phaseFrame = i; }
  }
  const beats = [];
  for (let t = phaseFrame * frameDur; t < duration; t += period) beats.push(t);
  return beats;
}

// Segment the track by energy dynamics.
//   drop:      sustained high energy
//   breakdown: sustained low energy after the track has been loud
//   build:     rising energy leading into a drop
//   intro/outro at the edges, groove for the rest.
function detectSections(energy, frameDur) {
  const frames = energy.length;
  const duration = frames * frameDur;

  // classify each ~1s block as low/mid/high energy
  const blockFrames = Math.round(1 / frameDur);
  const nBlocks = Math.ceil(frames / blockFrames);
  const blockE = new Float32Array(nBlocks);
  for (let b = 0; b < nBlocks; b++) {
    let sum = 0, cnt = 0;
    for (let f = b * blockFrames; f < Math.min(frames, (b + 1) * blockFrames); f++) { sum += energy[f]; cnt++; }
    blockE[b] = cnt ? sum / cnt : 0;
  }

  const sorted = Array.from(blockE).sort((a, b) => a - b);
  const q = p => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))];
  const hiThresh = q(0.65);
  const loThresh = q(0.30);

  // raw labels
  const labels = new Array(nBlocks);
  for (let b = 0; b < nBlocks; b++) {
    labels[b] = blockE[b] >= hiThresh ? "high" : blockE[b] <= loThresh ? "low" : "mid";
  }

  // merge into runs, absorbing runs shorter than 4s into their neighbour
  const minRun = 4;
  let runs = [];
  for (let b = 0; b < nBlocks; b++) {
    if (runs.length && runs[runs.length - 1].label === labels[b]) runs[runs.length - 1].len++;
    else runs.push({ label: labels[b], start: b, len: 1 });
  }
  runs = runs.filter(r => r.len >= minRun || runs.length === 1);
  if (!runs.length) runs.push({ label: "mid", start: 0, len: nBlocks });
  // re-stitch gaps left by the filter so sections cover the whole track
  runs[0].start = 0;
  for (let i = 0; i < runs.length; i++) {
    const end = i + 1 < runs.length ? runs[i + 1].start : nBlocks;
    runs[i].len = end - runs[i].start;
  }

  // map runs to musical section types
  const sections = [];
  let seenHigh = false;
  for (let i = 0; i < runs.length; i++) {
    const r = runs[i];
    const start = r.start * 1;           // seconds (1s blocks)
    const end = Math.min(duration, (r.start + r.len) * 1);
    let type;
    if (r.label === "high") { type = "drop"; seenHigh = true; }
    else if (r.label === "low") {
      if (i === 0) type = "intro";
      else if (i === runs.length - 1) type = "outro";
      else type = seenHigh ? "breakdown" : "intro";
    } else {
      // mid energy: a build if the next run is high, otherwise groove
      const next = runs[i + 1];
      type = next && next.label === "high" ? "build" : "groove";
    }
    sections.push({ start, end, type });
  }
  if (!sections.length) sections.push({ start: 0, end: duration, type: "groove" });
  return sections;
}

function sectionAt(sections, t) {
  for (const s of sections) if (t >= s.start && t < s.end) return s;
  if (sections.length && t < sections[0].start) return sections[0];
  return sections[sections.length - 1] || null;
}
