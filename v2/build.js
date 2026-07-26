#!/usr/bin/env node
// Bundles the V2 ES modules into single-file HTMLs that work when opened
// directly from disk (file://), where browsers block module imports.
//   node v2/build.js   ->  v2/dist/poopdog-v2.html, v2/dist/poopdog-v2-mobile.html
//
// The modules are written in a restricted style this bundler relies on:
// single-line imports at the top of each file, `export` only as a
// declaration prefix (no default exports, no `export {...}` lists).
"use strict";
const fs = require("fs");
const path = require("path");
const V2 = __dirname;

// dependency order (imports only ever point earlier in this list)
const CORE = [
  "js/core/prng.js", "js/core/palettes.js", "js/core/modulation.js",
  "js/core/analysis.js", "js/core/project.js", "js/core/generators.js",
  "js/core/engine.js", "js/core/director.js", "js/core/playback.js",
  "js/export/mp4muxer.js", "js/export/exporter.js",
];
const UI = [
  "js/ui/home.js", "js/ui/analysisScreen.js", "js/ui/editor.js",
  "js/ui/timeline.js", "js/ui/inspector.js", "js/ui/exportPage.js",
  "js/ui/app.js",          // last: its tail boots the app
];

function stripModule(src, name) {
  const out = [];
  for (const line of src.split("\n")) {
    const im = line.match(/^import\s*{([^}]*)}\s*from\s*["'][^"']+["'];?\s*$/);
    if (im) {
      // preserve `orig as alias` bindings as plain consts
      for (const part of im[1].split(",")) {
        const m = part.trim().match(/^(\w+)\s+as\s+(\w+)$/);
        if (m) out.push(`const ${m[2]} = ${m[1]};`);
      }
      continue;
    }
    if (/^import\s/.test(line)) continue;                    // bare/default imports (none used)
    if (line === '"use strict";') continue;                  // one added at bundle top
    out.push(line.replace(/^export\s+(?=(const|let|function|async|class)\b)/, ""));
  }
  return `// ===== ${name} =====\n` + out.join("\n");
}

const bundle = files =>
  files.map(f => stripModule(fs.readFileSync(path.join(V2, f), "utf8"), f)).join("\n");

const wrap = code => `<script>\n(function () {\n"use strict";\n${code}\n})();\n</script>`;

fs.mkdirSync(path.join(V2, "dist"), { recursive: true });

// ---- desktop ----
{
  let html = fs.readFileSync(path.join(V2, "index.html"), "utf8");
  const css = fs.readFileSync(path.join(V2, "css/app.css"), "utf8");
  html = html.replace(/<link rel="stylesheet" href="css\/app.css">/, `<style>\n${css}\n</style>`);
  html = html.replace(/<script type="module" src="js\/ui\/app.js"><\/script>/,
    wrap(bundle([...CORE, ...UI])));
  html = html.replace(/<script>\s*\/\/ file:\/\/ guard[\s\S]*?<\/script>\n?/, "");  // dist needs no guard
  fs.writeFileSync(path.join(V2, "dist/poopdog-v2.html"), html);
  console.log("wrote dist/poopdog-v2.html", html.length, "bytes");
}

// ---- mobile ----
{
  let html = fs.readFileSync(path.join(V2, "mobile.html"), "utf8");
  const m = html.match(/<script type="module">([\s\S]*?)<\/script>/);
  if (!m) throw new Error("mobile inline module not found");
  const shell = stripModule(m[1], "mobile shell");
  html = html.replace(m[0], wrap(bundle(CORE) + "\n" + shell));
  html = html.replace(/<script>\s*\/\/ file:\/\/ guard[\s\S]*?<\/script>\n?/, "");
  fs.writeFileSync(path.join(V2, "dist/poopdog-v2-mobile.html"), html);
  console.log("wrote dist/poopdog-v2-mobile.html", html.length, "bytes");
}
