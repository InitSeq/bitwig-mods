# Piano Roll: Note Generator

Generates **chords** or a **melody** in the project's key, laid out in a form such as A A A B, from a block in the Inspector. You see and hear the result before you apply it.

- Mod ID: `note-generator`
- Needs Framework: Piano Roll, Framework: Inspector and Framework: Notifications (all ship with Patchbay).
- Built against Bitwig Studio 6.1.7 on macOS: the hook that finds the clip in the Detail Editor uses that build's names.
- Status: prototype.

## Use it

1. Open a note clip in the Detail Editor (double-click it). The clip's Inspector shows the **Note Generator** block. It writes over the **whole clip**, so an empty clip is fine.
2. Or select notes in the piano roll: the block in the Note Inspector then writes only from the first selected note's start to the last one's end.
3. Pick a **Mode** and a **Form**, change any row or click **Dice**. The result appears as preview notes and plays in place of the clip's notes.
4. Click **Dice** until you like it, then **Apply**. It is one Undo step.

| Row | What it does |
|---|---|
| Mode | **Chords** or **Melody** |
| Form | The order of the sections: A A A A, A A A B, A A B A, A A B B, A B A B, A B A C, A B C B or A B C D. The same letter is the same chords or the same motif again |
| Section | How long one letter is. **Auto**: a quarter of the range, so the form fits once (16 bars: 4 bars each). Or 1, 2 or 4 bars; a longer range starts the form again |
| Length (Chords) | One chord every half bar, bar or two bars |
| Chords (Chords) | Triads or seventh chords |
| Rhythm (Chords) | **Hold**, **Pulse** (every beat), **Offbeat** (bass on the beat, chord between) or **3-3-2** |
| Bass (Chords) | Adds the chord's root below |
| Rate (Melody) | The grid the melody moves on: 1/4, 1/8 or 1/16 |
| Density (Melody) | Few notes, some, or many |
| Register | Low, Mid or High (around C2, C3, C4) |
| Existing | **Replace** removes the notes there (the selected ones, or all of the clip's), **Keep** leaves them. Chords replace by default, a melody keeps |
| Seed | The number behind the result: the same seed and settings give the same notes |
| Dice | A new seed |
| Apply | Writes the notes |

## What it generates

- **Key:** Bitwig's global key and scale at the start of the range. C major when Bitwig has none to read.
- **Chords:** each letter draws on a well-known four-chord loop for the key (major or minor); A starts on the key's own chord. Chords are voiced close to each other, and a letter sounds the same each time it returns.
- **Melody:** each letter is a motif: a rhythm built from short cells that repeat inside it, and a line with one clear shape (an arch, rising, falling, a wave). When a letter returns, its rhythm returns exactly and its notes on the beat move to the harmony at that place. Every section ends on a tone of its chord; the last one comes to rest.
- **Chords and melody together:** a melody follows the notes sounding in the clip. With none there, it follows the chords the same seed and form would generate, so generating chords and then a melody with the same seed fits.

Bars are counted as 4 beats.

## Build

`sh build.sh <unpacked Patchbay folder>` writes `out/note-generator/` and its ZIP with Patchbay's mod kit; it needs JDK 21 or newer, Python 3 and Bitwig Studio 6.1.7 in `/Applications`. `test/` holds an offline check of the generator (`GENLOGIC_OK`).
