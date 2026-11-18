#!/bin/zsh
# (icons.json if missing) → gen → open in FigCraft → render every frame + overview.
# Basemap: zsh design/tools/images.sh (Nano Banana via agy) — run once; images/terrain.png is committed.
# Usage: zsh design/tools/build.sh
set -e
S=$(cd "$(dirname "$0")" && pwd)
P=$(cd "$S/.." && pwd)
DOC=$P/wingman-c2.figcraft
TMP=${TMPDIR:-/tmp}/wingman-c2-build; mkdir -p $TMP $TMP/fcout
cd $S
fc() { python3 -I fc.py "$@"; }
launch() {
  pkill -x FigCraft || true; sleep 1.2
  open -a FigCraft --args --control 47901 --automation-read-root $P --automation-write-root $TMP/fcout $DOC
  for i in {1..30}; do sleep 1; fc window.list --max=0 >/dev/null 2>&1 && break; done
  sleep 1.5
  for i in {1..6}; do
    fc window.list --max=400 | grep -q '"active": "wingman-c2"' && return 0
    fc ui.key '{"key":"Tab","ctrl":true}' --max=0 >/dev/null; sleep 0.5
  done
  echo "could not activate the wingman-c2 tab"; fc window.list; return 1
}

if [ ! -f $P/icons/icons.json ]; then launch; python3 -I icons.py $TMP/fcout; fi
python3 -I gen.py $DOC
launch

mkdir -p $P/renders
find $P/renders -name "*.png" -delete
python3 -I - "$DOC" <<'EOF' > $TMP/frames.txt
import json, sys, zipfile
d = json.loads(zipfile.ZipFile(sys.argv[1]).read("document.json"))
for i in d["nodes"]["1"]["children"]:
    n = d["nodes"][str(i)]["name"].lower().replace(" · ", "-").replace(" ", "-")
    print(i, n)
EOF
while read id name; do
  fc ui.render "{\"ids\":[$id],\"scale\":2}" --out=$P/renders/$name.png --max=0 >/dev/null
done < $TMP/frames.txt
SCREEN_IDS=$(python3 -I -c 'import json,sys,zipfile; d=json.loads(zipfile.ZipFile(sys.argv[1]).read("document.json")); n=d["nodes"]; print(json.dumps([i for i in n["1"]["children"] if not n[str(i)]["name"].startswith("00")]))' $DOC)
fc ui.render "{\"ids\":$SCREEN_IDS,\"scale\":2}" --out=$P/renders/overview.png --max=0 >/dev/null
ls -la $P/renders
