# Piano Roll: Note Generator

Generates **chords** or a **melody** in the project's key, from a block in the Note Inspector. You see and hear the result before you apply it.

- Mod ID: `note-generator`
- Needs Framework: Piano Roll, Framework: Inspector and Framework: Notifications (all ship with Patchbay).
- Status: prototype 0.1.0-dev.1. Built without Bitwig names or hooks of its own; not yet tried in Bitwig.

## Use it

1. In the piano roll, select one or more notes. They mark **where** to write: the time from the first note's start to the last note's end. In an empty clip, draw one long note over the bars you want filled.
2. In the Note Inspector, find the **Note Generator** block and pick a **Mode**.
3. Change any row or click **Dice**. The result appears as preview notes and plays in place of the clip's notes.
4. Click **Dice** until you like it, then **Apply**. It is one Undo step.

| Row | What it does |
|---|---|
| Mode | **Chords** or **Melody** |
| Length (Chords) | One chord every half bar, bar or two bars |
| Chords (Chords) | Triads or seventh chords |
| Bass (Chords) | Adds the chord's root below |
| Rate (Melody) | The grid the melody moves on: 1/4, 1/8 or 1/16 |
| Density (Melody) | How many of the grid's steps get a note |
| Register | Low, Mid or High (around C2, C3, C4) |
| Selection | **Replace** removes the selected notes, **Keep** leaves them. Chords replace by default, a melody keeps |
| Seed | The number behind the result: the same seed and settings give the same notes |
| Dice | A new seed |
| Apply | Writes the notes |

## What it generates

- **Key:** Bitwig's global key and scale at the start of the range. C major when Bitwig has none to read.
- **Chords:** a progression that starts on the key's own chord, avoids repeating a chord and leads back home at the end, so it loops. Each chord is voiced close to the one before.
- **Melody:** one voice with a two-bar rhythm that repeats; each repeat starts with the first one's shape and then varies. Notes on the beat lean on the notes sounding in the clip at that moment, so a melody generated over chords follows them (select the chords, Mode: Melody, Selection: Keep). The last note comes to rest on the chord.

Bars are counted as 4 beats.

## Build

`sh build.sh <unpacked Patchbay folder>` (needs JDK 21 and Python 3) writes `out/note-generator/` and its ZIP. `test/` holds an offline check of the generator (`GENLOGIC_OK`).
