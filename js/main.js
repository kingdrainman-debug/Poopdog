// App wiring: file load -> analysis -> auto show -> render loop.
"use strict";

(() => {
  const $ = id => document.getElementById(id);

  const stage = $("stage");
  const audio = new AudioEngine();
  const visuals = new VisualEngine(stage);
  const director = new ShowDirector();
  const exporter = new Exporter(stage, audio);

  let analysis = null;

  // ---- settings (manual controls; director overrides when in auto mode) ----
  const settings = {
    palette: "cyberpunk",
    laserMode: "fan",
    tunnelType: "neon",
    glow: 0.7,
    speed: 1,
    strobeAmt: 0.5,
    shakeAmt: 0.6,
    intensity: 0.8,
  };

  // populate palette select
  for (const [key, p] of Object.entries(PALETTES)) {
    const opt = document.createElement("option");
    opt.value = key;
    opt.textContent = p.label;
    $("sel-palette").appendChild(opt);
  }

  // section type -> palette rotation for auto mode
  const SECTION_LABELS = {
    intro: "Intro", build: "Build-up", drop: "DROP",
    breakdown: "Breakdown", groove: "Groove", outro: "Outro",
  };
  const TIMELINE_COLORS = {
    intro: "#3a3a55", build: "#c8a000", drop: "#ff2d95",
    breakdown: "#2a7a8c", groove: "#5a5a8c", outro: "#3a3a55",
  };

  // ---- file loading ----
  async function loadFile(file) {
    $("dropzone").hidden = true;
    $("analysis-overlay").hidden = false;
    try {
      const arrayBuf = await file.arrayBuffer();
      $("analysis-status").textContent = "Decoding audio…";
      const buffer = await audio.load(arrayBuf);

      // let the overlay paint before the heavy sync analysis
      await new Promise(r => setTimeout(r, 50));
      analysis = analyzeTrack(buffer, msg => { $("analysis-status").textContent = msg; });

      director.generate(analysis);
      $("hud-bpm").textContent = `${analysis.bpm} BPM`;

      $("analysis-overlay").hidden = true;
      $("hud").hidden = false;
      $("timeline-wrap").hidden = false;
      $("controls").hidden = false;
      $("btn-autoshow").disabled = false;
      $("btn-export").disabled = false;
      drawTimeline();
      audio.play();
      $("btn-play").textContent = "⏸";
    } catch (err) {
      $("analysis-overlay").hidden = true;
      $("dropzone").hidden = false;
      alert("Could not load that file: " + err.message);
    }
  }

  $("btn-open").addEventListener("click", () => $("file-input").click());
  $("file-input").addEventListener("change", e => {
    if (e.target.files[0]) loadFile(e.target.files[0]);
  });

  const dz = $("dropzone");
  document.addEventListener("dragover", e => { e.preventDefault(); dz.classList.add("dragover"); });
  document.addEventListener("dragleave", () => dz.classList.remove("dragover"));
  document.addEventListener("drop", e => {
    e.preventDefault();
    dz.classList.remove("dragover");
    const file = e.dataTransfer.files[0];
    if (file && file.type.startsWith("audio")) loadFile(file);
  });

  // ---- transport ----
  $("btn-play").addEventListener("click", () => {
    if (!audio.buffer) return;
    if (audio.playing) { audio.pause(); $("btn-play").textContent = "▶"; }
    else { audio.play(); $("btn-play").textContent = "⏸"; }
  });
  audio.onEnded = () => { $("btn-play").textContent = "▶"; };

  document.addEventListener("keydown", e => {
    if (e.code === "Space" && audio.buffer && e.target.tagName !== "INPUT" && e.target.tagName !== "SELECT") {
      e.preventDefault();
      $("btn-play").click();
    }
  });

  // ---- timeline ----
  const tl = $("timeline");
  function drawTimeline() {
    const rect = tl.getBoundingClientRect();
    tl.width = rect.width * (window.devicePixelRatio || 1);
    const ctx = tl.getContext("2d");
    const w = tl.width, h = tl.height;
    ctx.clearRect(0, 0, w, h);
    if (!analysis) return;
    const dur = audio.duration;

    // section blocks
    for (const sec of analysis.sections) {
      ctx.fillStyle = TIMELINE_COLORS[sec.type] || "#444";
      ctx.globalAlpha = 0.55;
      ctx.fillRect(sec.start / dur * w, 0, (sec.end - sec.start) / dur * w, h);
    }
    ctx.globalAlpha = 1;

    // energy curve
    ctx.strokeStyle = "rgba(255,255,255,.9)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    const step = Math.max(1, Math.floor(analysis.energy.length / w));
    for (let x = 0; x < w; x++) {
      const idx = Math.min(analysis.energy.length - 1, Math.floor(x / w * analysis.energy.length / step) * step);
      const y = h - analysis.energy[idx] * (h - 6) - 3;
      x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();

    // section labels
    ctx.font = "10px system-ui";
    ctx.fillStyle = "rgba(255,255,255,.85)";
    for (const sec of analysis.sections) {
      const bw = (sec.end - sec.start) / dur * w;
      if (bw > 45) ctx.fillText(SECTION_LABELS[sec.type] || sec.type, sec.start / dur * w + 4, 12);
    }
  }
  window.addEventListener("resize", drawTimeline);

  tl.addEventListener("click", e => {
    if (!audio.buffer) return;
    const rect = tl.getBoundingClientRect();
    audio.seek((e.clientX - rect.left) / rect.width * audio.duration);
    if (audio.playing) $("btn-play").textContent = "⏸";
  });

  // ---- controls ----
  const bind = (id, fn) => $(id).addEventListener("input", e => fn(e.target.value));
  bind("sel-scene", () => {});   // read directly in the loop
  bind("sel-laser", v => { settings.laserMode = v; });
  bind("sel-tunnel", v => { settings.tunnelType = v; });
  bind("sel-palette", v => { settings.palette = v; });
  bind("rng-sens", v => { audio.beatSensitivity = +v; });
  bind("rng-glow", v => { settings.glow = +v; });
  bind("rng-speed", v => { settings.speed = +v; });
  bind("rng-strobe", v => { settings.strobeAmt = +v; });
  bind("rng-shake", v => { settings.shakeAmt = +v; });

  $("btn-autoshow").addEventListener("click", () => {
    // one click: regenerate the show and jump into auto mode from the top
    director.generate(analysis);
    $("sel-scene").value = "auto";
    audio.seek(0);
    audio.play();
    $("btn-play").textContent = "⏸";
  });

  // ---- export ----
  $("btn-export").addEventListener("click", () => {
    const btn = $("btn-export");
    if (exporter.recording) { exporter.stop(); return; }
    audio.seek(0);
    audio.play();
    $("btn-play").textContent = "⏸";
    exporter.start(() => {
      btn.textContent = "⏺ Export Video";
      btn.classList.remove("recording");
    });
    btn.textContent = "⏹ Stop & Save";
    btn.classList.add("recording");
  });
  // auto-stop export when the track ends
  const prevEnded = audio.onEnded;
  audio.onEnded = () => { prevEnded(); if (exporter.recording) exporter.stop(); };

  // ---- palette rotation per section in auto mode ----
  const AUTO_PALETTES = {
    drop: ["hardstyle", "neon", "cyberpunk", "acid"],
    build: ["synthwave", "trance"],
    breakdown: ["blacklight", "trance"],
    intro: ["techno"], outro: ["techno"],
    groove: ["cyberpunk", "psytrance", "dnb", "synthwave"],
  };
  let lastCue = null;

  // ---- render loop ----
  const fmt = t => `${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, "0")}`;

  function frame() {
    requestAnimationFrame(frame);
    const f = audio.update();
    const t = audio.currentTime;

    let scene = $("sel-scene").value;
    const active = { ...settings };

    if (scene === "auto" && director.enabled) {
      const cue = director.cueAt(t);
      if (cue) {
        scene = cue.scene;
        if (cue.laserMode) active.laserMode = cue.laserMode;
        if (cue.tunnelType) active.tunnelType = cue.tunnelType;
        active.intensity = cue.intensity;
        active.strobeAmt = cue.strobeAmt * settings.strobeAmt * 2;
        active.speed = cue.speed * settings.speed;

        if (cue !== lastCue) {
          // section transition: white flash + palette change
          if (lastCue) visuals.flash = Math.max(visuals.flash, 0.8);
          const pals = AUTO_PALETTES[cue.type] || AUTO_PALETTES.groove;
          active.palette = settings.palette = pals[Math.floor(cue.start) % pals.length];
          $("sel-palette").value = settings.palette;
          lastCue = cue;
        } else {
          active.palette = settings.palette;
        }
        $("hud-section").textContent = SECTION_LABELS[cue.type] || cue.type;
      }
    } else if (audio.buffer) {
      const sec = analysis ? sectionAt(analysis.sections, t) : null;
      $("hud-section").textContent = sec ? (SECTION_LABELS[sec.type] || sec.type) : "—";
    }
    $("hud-scene").textContent = scene;

    visuals.render(scene, f, active, analysis ? analysis.bpm : 120);

    // playhead + clock
    if (audio.buffer) {
      $("time-display").textContent = `${fmt(t)} / ${fmt(audio.duration)}`;
      const rect = $("timeline-wrap").getBoundingClientRect();
      $("playhead").style.left = `${(t / audio.duration) * rect.width}px`;
    }
  }
  frame();

  // idle animation on the empty stage so the app never looks dead
})();
