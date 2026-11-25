package ch.coldforge.notegen;

import bml.api.Bml;
import bml.notifications.Notifications;
import bml.pianoroll.Clip;
import bml.pianoroll.Edit;
import bml.pianoroll.KeySignature;
import bml.pianoroll.Note;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The generator's settings and what Generate does with them.
 *
 * It writes into the clip open in the Detail Editor: over the time selection when there is one, else over the
 * whole clip. Mode chooses chords or a melody, in Bitwig's key at that place, laid out in a form. Each Generate is
 * one Undo step. The settings are kept until Bitwig closes; {@link GeneratorDialog} shows them.
 */
final class Generator {
    static final Bml.Mod MOD = Bml.mod("note-generator");
    static final String LABEL = "Note Generator";

    static final int CHORDS = 0;
    static final int MELODY = 1;
    static final String[] MODES = {"Chords", "Melody"};
    static final String[] FORM_LABELS = new String[GenLogic.FORMS.length];
    static final String[] SECTIONS = {"Auto", "1 bar", "2 bars", "4 bars"};
    private static final double[] SECTION_BEATS = {0, 4, 8, 16};
    static final String[] CHORD_LENGTHS = {"1/2 bar", "1 bar", "2 bars"};
    private static final double[] CHORD_BEATS = {2, 4, 8};
    static final String[] CHORD_TYPES = {"Triads", "Sevenths"};
    static final String[] RHYTHMS = {"Hold", "Pulse", "Offbeat", "3-3-2"};
    static final String[] OFF_ON = {"Off", "On"};
    static final String[] RATES = {"1/4", "1/8", "1/16"};
    private static final double[] RATE_BEATS = {1, 0.5, 0.25};
    static final String[] DENSITIES = {"Sparse", "Medium", "Busy"};
    private static final int[] DENSITY_PERCENT = {22, 50, 82};
    static final String[] REGISTERS = {"Low", "Mid", "High"};
    private static final int[] CENTRES = {48, 60, 72};
    static final String[] EXISTING = {"Replace mine", "Replace all", "Keep"};
    private static final int REPLACE_MINE = 0;
    private static final int REPLACE_ALL = 1;
    static final String[] RANGES = {"Time selection", "Whole clip"};
    static final String[] GLIDES = {"Off", "Some", "Many"};
    private static final int[] GLIDE_PERCENT = {0, 30, 65};

    static {
        for (int i = 0; i < FORM_LABELS.length; i++) {
            FORM_LABELS[i] = String.join(" ", GenLogic.FORMS[i].split(""));
        }
    }

    // The settings.
    static int mode = CHORDS;
    static int form = 1; // A A A B
    static int section = 0;
    static int chordLength = 1;
    static int chordType = 0;
    static int rhythm = GenLogic.HOLD;
    static int bass = 1;
    static int rate = 2;
    static int density = 1;
    static int glide = 0;
    static final int[] register = {1, 2}; // per mode: chords in the middle, a melody above them
    static int existing = REPLACE_MINE;
    static int range = 0;
    static int seed = 1;

    /** The notes each mode last wrote (Bitwig's note objects), so "Replace mine" can take them out again. */
    private static final List<List<WeakReference<Object>>> mine = List.of(new ArrayList<>(), new ArrayList<>());
    private static String logged = "";

    private Generator() {
    }

    /** Where Generate would write now, for the dialog. */
    static String rangeText() {
        DetailEditor.Open open = DetailEditor.open();
        if (open == null) {
            return "No note clip open";
        }
        double[] span = span(open);
        return (span[0] > open.start() + 1e-6 || span[1] < open.end() - 1e-6 ? "Time selection, " : "Whole clip, ") + bars(span[1] - span[0]);
    }

    /** Whether the open clip has a time selection to write over. */
    static boolean hasSelection() {
        DetailEditor.Open open = DetailEditor.open();
        return open != null && selection(open) != null;
    }

    private static String bars(double beats) {
        double bars = beats / 4;
        String number = Math.abs(bars - Math.rint(bars)) < 1e-6 ? String.valueOf((long) Math.rint(bars)) : String.format("%.2f", bars);
        return number + (number.equals("1") ? " bar" : " bars");
    }

    /** The time selection inside the clip; null when there is none. */
    private static double[] selection(DetailEditor.Open open) {
        if (!open.selected()) {
            return null;
        }
        double from = Math.max(open.start(), open.selectionStart());
        double to = Math.min(open.end(), open.selectionEnd());
        return to - from > 1e-6 ? new double[] {from, to} : null;
    }

    private static double[] span(DetailEditor.Open open) {
        double[] selected = range == 0 ? selection(open) : null;
        return selected != null ? selected : new double[] {open.start(), open.end()};
    }

    /** Other notes with the current settings, written at once as one Undo step. */
    static void generate() {
        seed = 1 + new Random().nextInt(999);
        try {
            DetailEditor.Open open = DetailEditor.open();
            if (open == null) {
                throw new Refused("open a note clip in the Detail Editor");
            }
            write(open);
        } catch (Refused e) {
            Notifications.toast(LABEL + ": " + e.getMessage());
        } catch (RuntimeException e) {
            MOD.log(LABEL + " failed", e);
            Notifications.toast(LABEL + ": it failed, see Bitwig's log");
        }
    }

    private static void write(DetailEditor.Open open) {
        Clip clip = open.clip();
        double[] span = span(open);
        double start = span[0];
        double end = span[1];
        String ranges = "clip " + open.start() + " to " + open.end() + ", time selection "
                + (open.selected() ? open.selectionStart() + " to " + open.selectionEnd() : "none") + ", writing " + start + " to " + end;
        if (!ranges.equals(logged)) { // when they change: what the ranges look like, for finding a wrong place
            logged = ranges;
            MOD.log(ranges);
        }
        List<Note> there = clip.notesBetween(start, end);
        List<Note> leaving = new ArrayList<>();
        for (Note note : there) {
            if (existing == REPLACE_ALL || (existing == REPLACE_MINE && isMine(note))) {
                leaving.add(note);
            }
        }
        GenLogic.Scale scale = scaleAt(clip, start);
        int centre = CENTRES[register[mode]];
        String letters = GenLogic.FORMS[form];
        double sectionBeats = SECTION_BEATS[section];

        List<GenLogic.Out> notes;
        if (mode == CHORDS) {
            notes = GenLogic.chords(scale, start, end, CHORD_BEATS[chordLength], chordType == 1, bass == 1, centre, rhythm,
                    letters, sectionBeats, seed);
        } else {
            List<GenLogic.Sounding> harmony = new ArrayList<>();
            for (Note other : there) {
                if (!leaving.contains(other)) {
                    harmony.add(new GenLogic.Sounding(other.key(), other.start(), other.end()));
                }
            }
            notes = GenLogic.melody(scale, start, end, RATE_BEATS[rate], DENSITY_PERCENT[density], centre, harmony, letters,
                    sectionBeats, GLIDE_PERCENT[glide], seed);
        }
        if (notes.isEmpty()) {
            throw new Refused("the range is too short");
        }

        Edit edit = PianoRoll.edit(mode == CHORDS ? "Generate Chords" : "Generate Melody");
        for (Note note : leaving) {
            edit.remove(note);
        }
        int channel = there.isEmpty() ? 0 : there.get(0).channel();
        List<Note> added = new ArrayList<>();
        for (GenLogic.Out out : notes) {
            added.add(edit.add(clip, channel, out.key(), out.start(), out.duration(), out.velocity(), null));
        }
        edit.apply();

        // Remembered for Replace mine. Notes of this mode outside the range stay remembered too.
        List<WeakReference<Object>> kept = mine.get(mode);
        kept.removeIf(ref -> ref.get() == null || leaving.stream().anyMatch(note -> note.bitwigNote() == ref.get()));
        for (Note note : added) {
            if (note.bitwigNote() != null) {
                kept.add(new WeakReference<>(note.bitwigNote()));
            }
        }
    }

    private static boolean isMine(Note note) {
        for (WeakReference<Object> kept : mine.get(mode)) {
            if (kept.get() == note.bitwigNote()) {
                return true;
            }
        }
        return false;
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
}
