package ch.coldforge.notegen;

import bml.api.Bml;
import bml.inspector.BlockContext;
import bml.inspector.Blocks;
import bml.inspector.Context;
import bml.inspector.InspectorProvider;
import bml.inspector.Rows;
import bml.notifications.Notifications;
import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.KeySignature;
import bml.pianoroll.Listening;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Preview;
import bml.pianoroll.Refused;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Note Generator block in the Note Inspector, its preview and Apply.
 *
 * With notes selected, they mark where to write: the clip, the channel and the time from the first note's start to
 * the last note's end. With none, it writes over the whole clip open in the Detail Editor, from the block in the
 * clip's Inspector. Mode chooses chords or a melody, in Bitwig's key at that place, laid out in a form. Nothing shows until the user
 * changes a row or clicks Dice; then the outcome is drawn as Bitwig's preview notes and plays in place of the
 * clip's notes. Selecting other notes, editing notes, Undo and Redo take the preview away. Apply writes it as one
 * Undo step.
 */
public final class NoteGeneratorUi implements InspectorProvider {
    private static final Bml.Mod MOD = Bml.mod("note-generator");
    private static final String LABEL = "Note Generator";

    private static final int CHORDS = 0;
    private static final int MELODY = 1;
    private static final String[] MODES = {"Chords", "Melody"};
    private static final String[] CHORD_LENGTHS = {"1/2 bar", "1 bar", "2 bars"};
    private static final double[] CHORD_BEATS = {2, 4, 8};
    private static final String[] CHORD_TYPES = {"Triads", "Sevenths"};
    private static final String[] OFF_ON = {"Off", "On"};
    private static final String[] RATES = {"1/4", "1/8", "1/16"};
    private static final double[] RATE_BEATS = {1, 0.5, 0.25};
    private static final String[] REGISTERS = {"Low", "Mid", "High"};
    private static final int[] CENTRES = {48, 60, 72};
    private static final String[] EXISTING = {"Replace", "Keep"};
    private static final String[] FORM_LABELS = new String[GenLogic.FORMS.length];
    private static final String[] SECTIONS = {"Auto", "1 bar", "2 bars", "4 bars"};
    private static final double[] SECTION_BEATS = {0, 4, 8, 16};
    private static final String[] RHYTHMS = {"Hold", "Pulse", "Offbeat", "3-3-2"};

    static {
        for (int i = 0; i < FORM_LABELS.length; i++) {
            FORM_LABELS[i] = String.join(" ", GenLogic.FORMS[i].split(""));
        }
    }

    // The values, kept until Bitwig closes.
    private static int mode = CHORDS;
    private static int seed = 1;
    private static int chordLength = 1;
    private static int chordType = 0;
    private static int bass = 1;
    private static int rhythm = GenLogic.HOLD;
    private static int form = 1; // A A A B
    private static int section = 0;
    private static int rate = 2;
    private static int density = 50;
    private static final int[] register = {1, 2}; // per mode: chords in the middle, a melody above them
    private static final int[] existing = {0, 1}; // per mode: chords replace the notes there, a melody keeps them

    private static final List<WeakReference<BlockContext>> built = new ArrayList<>();

    // While a preview shows: the notes it was drawn for, the plan, and what ends it.
    private static Set<Object> shownOn = Set.of();
    private static Preview preview;
    private static Edit previewed;
    private static Listening selectionWatch;
    private static Listening changeWatch;
    private static boolean previewPosted;
    private static String said; // the refusal last told while values change: said once, not per update

    /** The generated notes, as a plan: over the selected notes' range, or with none selected over the whole clip. */
    private static Edit plan(BlockContext context) {
        List<Note> marks = marks(context);
        Clip clip;
        double start;
        double end;
        List<Note> there; // the notes Replace removes
        if (!marks.isEmpty()) {
            clip = marks.get(0).clip();
            if (clip == null) {
                throw new Refused("the notes' clip can't be read");
            }
            start = Double.MAX_VALUE;
            end = -Double.MAX_VALUE;
            for (Note mark : marks) {
                if (!clip.equals(mark.clip())) {
                    throw new Refused("select notes of one clip");
                }
                start = Math.min(start, mark.start());
                end = Math.max(end, mark.end());
            }
            there = marks;
        } else {
            DetailEditor.Open open = DetailEditor.open();
            if (open == null) {
                throw new Refused("open a note clip in the Detail Editor, or select notes");
            }
            clip = open.clip();
            start = open.start();
            end = open.end();
            there = clip.notesBetween(start, end);
        }
        GenLogic.Scale scale = scaleAt(clip, start);
        boolean replace = existing[mode] == 0;
        int centre = CENTRES[register[mode]];
        String letters = GenLogic.FORMS[form];
        double sectionBeats = SECTION_BEATS[section];

        List<GenLogic.Out> notes;
        if (mode == CHORDS) {
            notes = GenLogic.chords(scale, start, end, CHORD_BEATS[chordLength], chordType == 1, bass == 1, centre, rhythm,
                    letters, sectionBeats, seed);
        } else {
            Set<Note> leaving = replace ? new HashSet<>(there) : Set.of();
            List<GenLogic.Sounding> harmony = new ArrayList<>();
            for (Note other : clip.notesBetween(start, end)) {
                if (!leaving.contains(other)) {
                    harmony.add(new GenLogic.Sounding(other.key(), other.start(), other.end()));
                }
            }
            notes = GenLogic.melody(scale, start, end, RATE_BEATS[rate], density, centre, harmony, letters, sectionBeats, seed);
        }
        if (notes.isEmpty()) {
            throw new Refused("the range is too short");
        }

        Edit edit = PianoRoll.edit(mode == CHORDS ? "Generate Chords" : "Generate Melody");
        if (replace) {
            for (Note note : there) {
                edit.remove(note);
            }
        }
        int channel = there.isEmpty() ? 0 : there.get(0).channel();
        for (GenLogic.Out out : notes) {
            edit.add(clip, channel, out.key(), out.start(), out.duration(), out.velocity(), null);
        }
        return edit;
    }

    /** The selected notes, each once. None in a clip's Inspector, or when nothing is selected. */
    private static List<Note> marks(BlockContext context) {
        if (context.context() != Context.NOTE) {
            return List.of();
        }
        try {
            return new ArrayList<>(new LinkedHashSet<>(PianoRoll.selection(context.panel()).notes()));
        } catch (Refused e) {
            return List.of();
        }
    }

    /** Bitwig's key at this beat of the clip; C major when it can't be read. */
    private static GenLogic.Scale scaleAt(Clip clip, double beat) {
        try {
            KeySignature key = clip.keySignatureAt(beat);
            boolean[] in = new boolean[12];
            for (int i = 0; i < 12; i++) {
                in[i] = key.inScale(i);
            }
            return new GenLogic.Scale(key.root(), in);
        } catch (RuntimeException e) { // Refused, or a Bitwig without a key to read
            return GenLogic.Scale.major(0);
        }
    }

    // The block.

    /** The user changed a row, during Bitwig's handling of that change. */
    private static void userChanged(BlockContext context) {
        if (selectionWatch == null) {
            shownOn = selectedNotes(context.panel());
            selectionWatch = PianoRoll.onSelectionChange(selected -> {
                if (!notesOf(selected.notes()).equals(shownOn)) {
                    endAll(); // another selection, or none
                }
            });
            changeWatch = PianoRoll.onChange(NoteGeneratorUi::endAll); // an edit, Undo or Redo
        }
        if (!previewPosted) { // values changing quickly while dragging: the latest wins
            previewPosted = true;
            PianoRoll.later(() -> {
                previewPosted = false;
                showPreview(context);
            });
        }
    }

    private static void showPreview(BlockContext context) {
        endPreview();
        try {
            Edit edit = plan(context);
            Preview shown = edit.preview(true, true, true); // drawn as Apply would leave it, and heard when the clip plays
            preview = shown;
            previewed = edit;
            shown.onEnded(() -> endedElsewhere(shown));
            said = null;
        } catch (Refused e) {
            if (!e.getMessage().equals(said)) {
                said = e.getMessage();
                Notifications.toast(LABEL + ": " + e.getMessage());
            }
        } catch (RuntimeException e) {
            MOD.log(LABEL + ": the preview failed", e);
        }
    }

    /** Dice: other notes with the same settings. */
    private static void dice(BlockContext context) {
        seed = 1 + new Random().nextInt(999);
        refreshBlocks();
        userChanged(context);
    }

    /** Apply: the plan on screen, unless a value changed and its preview hasn't been drawn yet. */
    private static void apply(BlockContext context) {
        Edit shown = preview != null && preview.isShown() && !previewPosted ? previewed : null;
        endAll();
        try {
            (shown != null ? shown : plan(context)).apply();
        } catch (Refused e) {
            Notifications.toast(LABEL + ": " + e.getMessage());
        } catch (RuntimeException e) {
            MOD.log(LABEL + " failed", e);
        }
        refreshBlocks();
    }

    /** Bitwig ended the preview: its own preview or edit, a change, Undo or Redo. This runs inside what ended it. */
    private static void endedElsewhere(Preview shown) {
        if (preview != shown) {
            return;
        }
        preview = null;
        PianoRoll.later(NoteGeneratorUi::endAll);
    }

    /** Nothing shown, nothing listening. */
    private static void endAll() {
        endPreview();
        if (selectionWatch != null) {
            selectionWatch.stop();
            changeWatch.stop();
            selectionWatch = null;
            changeWatch = null;
        }
        shownOn = Set.of();
        said = null;
    }

    private static void endPreview() {
        previewed = null;
        if (preview != null) {
            Preview ending = preview;
            preview = null;
            ending.end();
        }
    }

    private static void refreshBlocks() {
        built.removeIf(block -> block.get() == null);
        for (WeakReference<BlockContext> block : built) {
            BlockContext context = block.get();
            if (context != null) {
                Rows.refresh(context);
            }
        }
    }

    private static Set<Object> selectedNotes(Object bitwigSelection) {
        try {
            return notesOf(PianoRoll.selection(bitwigSelection).notes());
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private static Set<Object> notesOf(List<Note> notes) {
        Set<Object> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Note note : notes) {
            set.add(note.bitwigNote());
        }
        return set;
    }

    @Override
    public void declare(Blocks blocks) {
        blocks.block("note_generator", LABEL)
              .in(Context.NOTE, Context.ARRANGER_CLIP, Context.LAUNCHER_CLIP)
              .content(context -> {
                  built.removeIf(block -> block.get() == null);
                  built.add(new WeakReference<>(context));
                  Object rows = Rows.column(
                          Rows.choice(context, "Mode", MODES, () -> mode, value -> mode = value).row(),
                          Rows.choice(context, "Form", FORM_LABELS, () -> form, value -> form = value).row(),
                          Rows.steps(context, "Section", SECTIONS, 0, () -> section, value -> section = value),
                          Rows.when(context, () -> mode == CHORDS,
                                  Rows.steps(context, "Length", CHORD_LENGTHS, 1, () -> chordLength, value -> chordLength = value),
                                  Rows.choice(context, "Chords", CHORD_TYPES, () -> chordType, value -> chordType = value).row(),
                                  Rows.choice(context, "Rhythm", RHYTHMS, () -> rhythm, value -> rhythm = value).row(),
                                  Rows.choice(context, "Bass", OFF_ON, () -> bass, value -> bass = value).row()),
                          Rows.when(context, () -> mode == MELODY,
                                  Rows.steps(context, "Rate", RATES, 2, () -> rate, value -> rate = value),
                                  Rows.number(context, "Density", 1, 100, 50, " %", false, () -> density,
                                          value -> density = (int) value)),
                          Rows.choice(context, "Register", REGISTERS, () -> register[mode], value -> register[mode] = value).row(),
                          Rows.choice(context, "Existing", EXISTING, () -> existing[mode], value -> existing[mode] = value).row(),
                          Rows.number(context, "Seed", 1, 999, 1, "", false, () -> seed, value -> seed = (int) value),
                          Rows.button(context, "Dice", () -> dice(context)),
                          Rows.button(context, "Apply", () -> apply(context)));
                  Rows.onUserChange(context, () -> userChanged(context));
                  return rows;
              });
    }
}
