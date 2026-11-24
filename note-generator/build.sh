#!/bin/sh
# Builds the installable mod folder out/note-generator and its ZIP, with Patchbay's mod kit.
# Usage: sh build.sh <unpacked Patchbay folder> [<Bitwig's Java folder>]
#   e.g. sh build.sh ~/Downloads/BML-0.36.0-dev.17-macos
# Needs JDK 25 or newer (javac, jar: the compile jar holds Java 25 class files) and Python 3. JDK names its bin
# folder; by default Homebrew's openjdk, else whatever is on PATH. The mod kit runs on the Java inside
# Bitwig Studio.app (set KIT_JAVA to use another). The Bitwig jar must be the build named in mod.json's built_against
# (Bitwig Studio 6.1.7 on macOS): the mod names that build's classes.
set -eu

BML="${1:?Pass the unpacked Patchbay folder (the one holding modkit/ and mods/)}"
BITWIG="${2:-/Applications/Bitwig Studio.app/Contents/Java}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HERE/build/classes"
OUT="$HERE/out/note-generator"
KIT="$BML/modkit"
COMPILE="$HERE/build/bitwig-compile.jar"
KIT_JAVA="${KIT_JAVA:-$BITWIG/../PlugIns/JavaVM-$(uname -m | sed 's/x86_64/x64/').bundle/Contents/Home/bin/java}"

JDK="${JDK:-/opt/homebrew/opt/openjdk/bin}"
[ -x "$JDK/javac" ] || JDK="$(dirname "$(command -v javac)")"

DEPS=""
for framework in piano-roll-framework inspector-composition-framework notifications-framework; do
    jar="$BML/mods/$framework/helper.jar"
    [ -f "$jar" ] || { echo "Missing $jar" >&2; exit 1; }
    DEPS="$DEPS:$jar"
done
DEPS="${DEPS#:}"

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"

# Bitwig's classes under the package bitwig.obf, signatures only. Never ship or share this jar.
[ -f "$COMPILE" ] || "$KIT_JAVA" -jar "$KIT/bml-modkit.jar" compile-jar "$BITWIG/bitwig.jar" "$COMPILE"

find "$HERE/src" -name '*.java' > "$HERE/build/sources.txt"
"$JDK/javac" --release 21 -proc:none -encoding UTF-8 -implicit:none \
    -cp "$COMPILE:$BITWIG/libs.jar:$KIT/bml-api.jar:$DEPS" -d "$WORK" @"$HERE/build/sources.txt"

# Checks the hooks against Bitwig's jar, writes them to hooks.json and renames bitwig.obf.X back to X.
OLDIFS="$IFS"; IFS=":"; set -- $DEPS; IFS="$OLDIFS"
"$KIT_JAVA" -jar "$KIT/bml-modkit.jar" finish "$WORK" "$HERE/build/hooks.json" "$BITWIG/bitwig.jar" "$BITWIG/libs.jar" "$@"

"$JDK/jar" --create --file "$OUT/helper.jar" -C "$WORK" .

# The finished manifest: the source one plus the hooks, as BML/docs/sdk/building.md describes.
python3 "$HERE/finish_manifest.py" "$HERE/mod.json" "$HERE/build/hooks.json" "$OUT/mod.json"

cp "$HERE/README.md" "$OUT/"
cp "$HERE/package/"* "$OUT/"
cp -R "$HERE/src" "$OUT/src"
cp "$HERE/build.json" "$OUT/"

VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["mod"]["version"])' "$HERE/mod.json")"
rm -f "$HERE/out/note-generator-$VERSION.zip"
(cd "$HERE/out" && zip -qr "note-generator-$VERSION.zip" note-generator -x '*.DS_Store')
echo "BUILD_OK $HERE/out/note-generator-$VERSION.zip"
