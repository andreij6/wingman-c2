#!/bin/zsh
# Generates map basemaps with Nano Banana through the Antigravity CLI (agy).
# Usage: zsh design/tools/images.sh [name ...]   (no names = all)
cd "$(dirname "$0")/../.."
OUT=$(pwd)/design/images
mkdir -p $OUT
STYLE="Strictly top-down orthographic view. Dark tactical basemap styling: deep blue-charcoal and slate tones, subtle hillshade relief, faint thin topographic contour lines, very low contrast and slightly desaturated so bright map symbols read clearly on top. No text, no labels, no numbers, no grid, no compass, no UI, no logos, no watermark."
typeset -A P
P[terrain]="Square 1:1 terrain basemap of rugged rural hill country: one long forested ridge running diagonally from lower-left to upper-right through the center, a winding river along the top third, two thin dirt roads, scattered tree cover, a few small fields. $STYLE"
names=("$@"); [[ ${#names} -eq 0 ]] && names=(${(k)P})
for n in $names; do
  agy -p "Use your image generation tool (Nano Banana) to generate this image and save it as a PNG at $OUT/$n.png: ${P[$n]} Reply with the saved path only." \
      --dangerously-skip-permissions --print-timeout 300s > $OUT/$n.log 2>&1
  echo "$n: $(ls -la $OUT/$n.png 2>/dev/null || echo FAILED)"
done
