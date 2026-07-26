// Home dashboard (spec §4.2): new-from-audio, templates, recent projects.
"use strict";

import { $ } from "./app.js";
import { PALETTES } from "../core/palettes.js";
import { makeProject, defaultTracks, deserialize, recentProjects } from "../core/project.js";

const TEMPLATES = [
  { name: "Techno Warehouse", palette: "techno", seed: 3, desc: "Stark strobes, grids, monochrome" },
  { name: "Festival Mainstage", palette: "neon", seed: 7, desc: "Big lasers, confetti, colour" },
  { name: "Psy Forest", palette: "psytrance", seed: 11, desc: "Mandalas, spirals, organic motion" },
  { name: "Synthwave Drive", palette: "synthwave", seed: 5, desc: "Sun, terrain, chrome grids" },
];

export function initHome(app) {
  let pendingTemplate = null;
  let pendingRecent = null;

  const openPicker = () => $("home-file").click();

  $("home-drop").addEventListener("click", openPicker);
  $("home-drop").addEventListener("dragover", e => { e.preventDefault(); $("home-drop").classList.add("over"); });
  $("home-drop").addEventListener("dragleave", () => $("home-drop").classList.remove("over"));
  $("home-drop").addEventListener("drop", e => {
    e.preventDefault();
    $("home-drop").classList.remove("over");
    const f = e.dataTransfer.files[0];
    if (f && f.type.startsWith("audio")) startProject(f);
  });
  $("home-file").addEventListener("change", e => {
    if (e.target.files[0]) startProject(e.target.files[0]);
    e.target.value = "";
  });

  async function startProject(file) {
    let project;
    if (pendingRecent) {
      project = pendingRecent;
      pendingRecent = null;
      app.toast(`Relinked audio for “${project.name}”`);
    } else {
      project = makeProject(file.name.replace(/\.[^.]+$/, ""));
      project.tracks = defaultTracks();
      if (pendingTemplate) {
        project.palette = pendingTemplate.palette;
        project.seed = pendingTemplate.seed;
        pendingTemplate = null;
      }
    }
    project.audioName = file.name;
    app.project = project;
    app.setHistory();
    app.emit("audioFile", file);          // analysis screen takes over
  }

  // templates
  const tpl = $("home-templates");
  for (const t of TEMPLATES) {
    const el = document.createElement("div");
    el.className = "card";
    const sw = PALETTES[t.palette].colors
      .map(c => `<i style="background:hsl(${c[0]},${c[1]}%,${c[2]}%)"></i>`).join("");
    el.innerHTML = `<div class="t">${t.name}</div><div class="d">${t.desc}</div><div class="swatches">${sw}</div>`;
    el.addEventListener("click", () => {
      pendingTemplate = t;
      app.toast(`Template “${t.name}” — now pick an audio file`);
      openPicker();
    });
    tpl.appendChild(el);
  }

  // recent projects
  function renderRecent() {
    const wrap = $("home-recent");
    wrap.innerHTML = "";
    const list = recentProjects();
    if (!list.length) {
      wrap.innerHTML = `<div class="dim">Nothing yet — projects autosave here.</div>`;
      return;
    }
    for (const e of list) {
      const el = document.createElement("div");
      el.className = "card";
      const when = new Date(e.savedAt).toLocaleString();
      el.innerHTML = `<div class="t">${e.name}</div><div class="d">${e.audioName || ""}<br>${when}</div>`;
      el.addEventListener("click", () => {
        pendingRecent = deserialize(e.project);
        app.toast("Pick the project's audio file to relink it");
        openPicker();
      });
      wrap.appendChild(el);
    }
  }
  renderRecent();
  app.on("screen", s => { if (s === "home") renderRecent(); });
}
