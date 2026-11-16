#!/usr/bin/env bash
# Renders the branding SVGs and the dashboard gallery to PNG with headless Edge/Chrome (Windows Git Bash).
#   bash branding/render.sh
# Gallery shots use the demo data from branding/demo/data.js (fictional player, never real data).
set -e
cd "$(dirname "$0")"
BROWSER="${BROWSER:-/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe}"
TMP="$TEMP/playchrono-render"; rm -rf "$TMP"; mkdir -p "$TMP/out" "$TMP/demo"
PROF="$(cygpath -w "$TMP/profile")"

shot() { # url width height out [scale]
  timeout 90 "$BROWSER" --headless --disable-gpu --no-first-run --user-data-dir="$PROF" --hide-scrollbars \
    --default-background-color=00000000 --force-device-scale-factor="${5:-1}" --window-size="$2,$3" \
    --virtual-time-budget=6000 --screenshot="$(cygpath -w "$TMP/out")\\$(basename "$4")" "$1" >/dev/null 2>&1
  cp "$TMP/out/$(basename "$4")" "./$4" && echo "  $4"
}

# dashboard with demo data (copied to a temp folder so the paths have no special characters)
cp ../web/*.* "$TMP/demo/" && cp demo/data.js "$TMP/demo/data.js" && cp src/*.html "$TMP/demo/"
src="file:///$(cygpath -m "$TMP/demo")"
shot "$src/icon.html" 512 512 icon.png
shot "$src/banner.html" 1600 500 banner.png
d="file:///$(cygpath -m "$TMP/demo")/index.html"
mkdir -p gallery
shot "$d?anim=0&lang=en&period=90#overview" 1440 1180 gallery/01-overview-light.png
shot "$d?anim=0&lang=en&period=90&theme=dark#overview" 1440 1180 gallery/02-overview-dark.png
shot "$d?anim=0&lang=en&period=90&theme=dark#schedule" 1440 1250 gallery/03-schedule.png
shot "$d?anim=0&lang=en&period=90#places" 1440 840 gallery/04-servers.png
shot "$d?anim=0&lang=en&period=90&theme=dark#activity" 1440 1000 gallery/05-activity.png
shot "$d?anim=0&lang=en&period=90#world" 1440 1150 gallery/06-exploration.png
shot "$d?anim=0&lang=en&period=90&theme=dark#inputs" 1440 900 gallery/07-inputs.png
shot "$d?anim=0&lang=en&period=90#leaderboard" 1440 1250 gallery/08-leaderboard.png
shot "$d?anim=0&lang=fr&period=90&theme=dark#overview" 1440 1180 gallery/09-francais.png
shot "$d?anim=0&lang=en&period=90&theme=dark#places" 1440 2300 gallery/10-server-details.png
