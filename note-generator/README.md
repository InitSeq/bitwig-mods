# Piano Roll: Note Generator

Generates **chords** or a **melody** in the project's key, laid out in a form such as A A A B. You say where with a time selection and how in a dialog.

- Mod ID: `note-generator`
- Needs Framework: Piano Roll, Framework: Actions and Menus, Framework: Inspector and Framework: Notifications (all ship with Patchbay).
- Built against Bitwig Studio 6.1.7 on macOS: the hooks that find the clip in the Detail Editor and add the button use that build's names.
- Status: prototype.

## Use it

1. Open a note clip in the Detail Editor. Drag a **time selection** over the bars to fill; with none, the whole clip is filled.
2. Open **Generate Notes…**: right-click the time selection (or a note), or click the note icon in the strip beside the piano keys, below the editor mode buttons. It is also in the Commander and can have a key (Settings > Shortcuts, category Note Generator).
3. Pick the options in the dialog and click **Generate**. The notes are written at once, as one Undo step.
4. Click **Generate** again for other notes: by default it replaces only what it wrote before. The dialog stays open, so with the clip playing you hear each variation. **Cancel** closes it.

**Generate Notes Again** (right-click menu, Commander, shortcut) writes other notes with the last settings, without the dialog.

| Option | What it does |
|---|---|
| Where | Shows what will be filled: the time selection or the whole clip, and how long it is |
| Range | **Time selection** (when there is one) or **Whole clip** |
| Mode | **Chords** or **Melody** |
| Form | The order of the parts: A A A A, A A A B, A A B A, A A B B, A B A B, A B A C, A B C B or A B C D. The same letter is the same notes again, exactly. The form divides the range: over 4 bars each letter is one bar, so A A A B is three equal bars and a different fourth, and A B C D is four different bars |
| Parts | Shows how the form falls on the range now, for example "A A A B · 1 bar each" |
| Register | Low, Mid or High (around C2, C3, C4) |
| Chord every (Chords) | Half a bar, a bar or two bars |
| Chords (Chords) | Triads or seventh chords |
| Rhythm (Chords) | **Hold**, **Pulse** (every beat), **Offbeat** (bass on the beat, chord between) or **3-3-2** |
| Bass (Chords) | Adds the chord's root below |
| Rate (Melody) | The note value: quarter, eighth or sixteenth notes |
| Density (Melody) | How many steps of the rate get a note: Sparse, Medium or Busy |
| Glide (Melody) | **Some** or **Many** notes run on into the next one, by half a step. A synth set to legato or glide then slides between them |
| Existing | **Replace mine**: takes out what this mode generated in the range before (until Bitwig is closed) and leaves everything else. **Replace all**: takes out every note in the range. **Keep**: adds to what is there |

How long a part is follows from the range: a quarter of it when that is whole bars (4 bars: 1 bar, 8 bars: 2 bars, 16 bars: 4 bars), or whole beats in a range of up to two bars. Any other range (5 or 6 bars, say) gets one-bar parts, and the form starts again after its last letter.

## What it generates

- **Key:** Bitwig's global key and scale at the start of the range. C major when Bitwig has none to read.
- **Chords:** each letter draws on a well-known four-chord loop for the key (major or minor); A starts on the key's own chord. Chords are voiced close to each other, and a letter sounds the same each time it returns.
- **Melody:** each letter is a motif: a rhythm built from short cells that repeat inside it, and a line with one clear shape (an arch, rising, falling, a wave). It is fitted to the harmony where the letter first stands; every later section of that letter repeats it note for note. The letter of the last section comes to rest on its chord.
- **Chords and melody together:** a melody follows the notes sounding in the clip. With none there, it follows the chords the same seed and form would generate, so generating chords and then a melody with the same seed fits.

Bars are counted as 4 beats.

## Build

`sh build.sh <unpacked Patchbay folder>` writes `out/note-generator/` and its ZIP with Patchbay's mod kit; it needs JDK 25 or newer, Python 3 and Bitwig Studio 6.1.7 in `/Applications`. `test/` holds an offline check of the generator (`GENLOGIC_OK`).
