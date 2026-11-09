#!/usr/bin/env bash
# Copy the skill's render engine into a build dir (once) and overlay this project's sources.
# Usage: ./sync.sh <build-dir>
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
build="${1:?build dir}"
skill="$here/../../.agents/skills/huashu-art-motion/scripts/engine"
[ -d "$build" ] || cp -R "$skill" "$build"
cp "$here"/src/*.js "$build"/
cp "$here"/src/scenes/*.js "$build"/scenes/
