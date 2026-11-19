#!/usr/bin/env bash
#
# Regenerates every app icon from the two SVGs next to this script:
#
#   app-icon.svg        -> Android launcher mipmaps, square and round (cake kept inside 80% of
#                          the radius, because the launcher crops round icons tightly)
#   app-icon-large.svg  -> the iOS 1024 icon and the web icons (same cake, drawn bigger)
#
# Run from anywhere on this Mac:
#
#   design/make-icons.sh
#
# Needs only what the Mac already has: Google Chrome (renders the SVG), sips (resizes) and swift
# (iosApp/tools/flatten-icon.swift strips the alpha channel Apple rejects with ITMS-90717).
# Afterwards check the result:
#
#   sips -g pixelWidth -g hasAlpha androidApp/src/androidMain/res/mipmap-*/ic_launcher*.png \
#     iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png \
#     webApp/src/jsMain/resources/images/*.png
#
# Expect hasAlpha "yes" only for ic_launcher_round.png.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SVG="$ROOT/design/app-icon.svg"
SVG_LARGE="$ROOT/design/app-icon-large.svg"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

shot() {  # shot <url> <out.png> [extra chrome flags]
  local url="$1" out="$2"; shift 2
  "$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=1 \
    --window-size=1024,1024 "$@" --screenshot="$out" "$url" >/dev/null 2>&1
  [ -s "$out" ] || { echo "Chrome did not write $out" >&2; exit 1; }
}
flatten() { swift "$ROOT/iosApp/tools/flatten-icon.swift" "$1" "$2" >/dev/null; }
resize() { sips -z "$1" "$1" "$2" --out "$3" >/dev/null; }

# Square, full-bleed, opaque. The OS rounds the corners itself.
shot "file://$SVG" "$WORK/square-raw.png";       flatten "$WORK/square-raw.png" "$WORK/square.png"
shot "file://$SVG_LARGE" "$WORK/large-raw.png";  flatten "$WORK/large-raw.png"  "$WORK/large.png"

# Round, transparent outside the circle (Android's roundIcon).
cat > "$WORK/round.html" <<HTML
<html style="background:transparent"><body style="margin:0;background:transparent">
<img src="file://$SVG" style="display:block;width:1024px;height:1024px;clip-path:circle(50%)">
</body></html>
HTML
shot "file://$WORK/round.html" "$WORK/round.png" --default-background-color=00000000

RES="$ROOT/androidApp/src/androidMain/res"
for pair in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  d="${pair%%:*}"; px="${pair##*:}"
  resize "$px" "$WORK/square.png" "$RES/mipmap-$d/ic_launcher.png"
  resize "$px" "$WORK/round.png"  "$RES/mipmap-$d/ic_launcher_round.png"
done

cp "$WORK/large.png" "$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"

WEB="$ROOT/webApp/src/jsMain/resources/images"
for px in 32 180 192 512; do resize "$px" "$WORK/large.png" "$WEB/icon-$px.png"; done
resize 512 "$WORK/large.png" "$WEB/logo.png"

echo "icons regenerated from $SVG and $SVG_LARGE"
