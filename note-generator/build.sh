#!/bin/sh
# Builds the installable mod folder out/note-generator and its ZIP.
# Usage: sh build.sh <unpacked Patchbay folder, e.g. ~/Downloads/BML-0.36.0-dev.17-macos>
# The mod has no hooks and names no Bitwig classes, so javac alone builds it: nothing from Patchbay is run.
set -eu

BML="${1:?Pass the unpacked Patchbay folder (the one holding modkit/ and mods/)}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HERE/build/classes"
OUT="$HERE/out/note-generator"

CP="$BML/modkit/bml-api.jar"
for framework in piano-roll-framework inspector-composition-framework notifications-framework; do
    jar="$BML/mods/$framework/helper.jar"
    [ -f "$jar" ] || { echo "Missing $jar" >&2; exit 1; }
    CP="$CP:$jar"
done
[ -f "$BML/modkit/bml-api.jar" ] || { echo "Missing $BML/modkit/bml-api.jar" >&2; exit 1; }

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"

find "$HERE/src" -name '*.java' > "$HERE/build/sources.txt"
javac --release 21 -proc:none -encoding UTF-8 -implicit:none -cp "$CP" -d "$WORK" @"$HERE/build/sources.txt"
jar --create --file "$OUT/helper.jar" -C "$WORK" .

# The finished manifest: the source one plus the empty hook lists a mod without hooks gets.
python3 - "$HERE/mod.json" "$OUT/mod.json" <<'EOF'
import json, sys
manifest = json.load(open(sys.argv[1], encoding="utf-8"))
manifest.setdefault("targets", {})
manifest.setdefault("hooks", [])
json.dump(manifest, open(sys.argv[2], "w", encoding="utf-8"), indent=2)
EOF

cp "$HERE/README.md" "$OUT/"
cp "$HERE/package/"* "$OUT/"
cp -R "$HERE/src" "$OUT/src"
cp "$HERE/build.json" "$OUT/"

VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["mod"]["version"])' "$HERE/mod.json")"
rm -f "$HERE/out/note-generator-$VERSION.zip"
(cd "$HERE/out" && zip -qr "note-generator-$VERSION.zip" note-generator -x '*.DS_Store')
echo "BUILD_OK $HERE/out/note-generator-$VERSION.zip"
