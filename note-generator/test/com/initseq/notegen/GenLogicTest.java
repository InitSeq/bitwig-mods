package com.initseq.notegen;

import com.initseq.notegen.GenLogic.Layout;
import com.initseq.notegen.GenLogic.Out;
import com.initseq.notegen.GenLogic.Scale;
import com.initseq.notegen.GenLogic.Sounding;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline checks of the generator, without Bitwig. Run with: java -cp <classes> com.initseq.notegen.GenLogicTest
 * With the argument "show" it prints one example of each mode instead.
 */
public final class GenLogicTest {
    private static int failures;
    private static int glidedCases;
    private static int glidedWithOverlap;
    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public static void main(String[] args) {
        boolean[] minor = new boolean[12];
        for (int step : new int[] {0, 2, 3, 5, 7, 8, 10}) {
            minor[step] = true;
        }
        boolean[] pentatonic = new boolean[12];
        for (int step : new int[] {0, 3, 5, 7, 10}) {
            pentatonic[step] = true;
        }
        boolean[] dorian = new boolean[12];
        for (int step : new int[] {0, 2, 3, 5, 7, 9, 10}) {
            dorian[step] = true;
        }
        Scale[] scales = {Scale.major(0), new Scale(9, minor), new Scale(4, pentatonic), new Scale(6, minor), new Scale(2, dorian)};

        if (args.length > 0 && args[0].equals("show")) {
            show(new Scale(6, minor));
            return;
        }

        for (Scale scale : scales) {
            for (long seed = 1; seed <= 120; seed++) {
                for (String form : GenLogic.FORMS) {
                    int rhythm = (int) (seed % 4);
                    // Chords: 16 bars from beat 8, one per bar, with bass.
                    List<Out> chords = GenLogic.chords(scale, 8, 72, 4, seed % 2 == 0, true, 60, rhythm, form, 0, seed);
                    check(!chords.isEmpty(), "chords: empty");
                    inRangeAndScale(chords, scale, 8, 72, "chords");
                    // The same letter is the same chords: compare each section with the first of its letter.
                    sameWhereLettersMatch(chords, GenLogic.layout(8, 72, form, 0), false, "chords " + form);
                    // The first chord holds the key's root in the bass.
                    int lowest = chords.stream().filter(n -> n.start() == 8).mapToInt(Out::key).min().orElse(-1);
                    check(lowest % 12 == scale.root, "chords: first bass is not the root");

                    // Melody on every rate and several densities, over the chords the same seed would give.
                    for (double step : new double[] {1, 0.5, 0.25}) {
                        for (int density : new int[] {5, 50, 100}) {
                            List<Out> melody = GenLogic.melody(scale, 8, 72, step, density, 72, List.of(), form, 0, 0, seed);
                            check(!melody.isEmpty(), "melody: empty");
                            inRangeAndScale(melody, scale, 8, 72, "melody");
                            oneVoice(melody, 0);
                            sameWhereLettersMatch(melody, GenLogic.layout(8, 72, form, 0), false, "melody " + form);
                            // The rate is the note value: most notes are one or two steps long.
                            long shortNotes = melody.stream().filter(n -> n.duration() <= 2 * step + 1e-9).count();
                            check(shortNotes * 10 >= melody.size() * 8, "melody: notes longer than the rate");
                            // Glides overlap the next note by half a step, and repeat with their letter too.
                            List<Out> glided = GenLogic.melody(scale, 8, 72, step, density, 72, List.of(), form, 0, 60, seed);
                            oneVoice(glided, step / 2);
                            sameWhereLettersMatch(glided, GenLogic.layout(8, 72, form, 0), false, "glide " + form);
                            glidedCases++;
                            glidedWithOverlap += overlaps(glided) > 0 ? 1 : 0;
                            for (Out note : melody) {
                                check(Math.abs(note.key() - 72) <= 16, "melody: far from the centre: " + note.key());
                            }
                        }
                    }

                    // Over chords in the clip, the melody rests on a tone of the chord sounding at its end.
                    List<Out> held = GenLogic.chords(scale, 8, 72, 4, false, true, 60, GenLogic.HOLD, form, 0, seed);
                    List<Sounding> harmony = new ArrayList<>();
                    for (Out chord : held) {
                        harmony.add(new Sounding(chord.key(), chord.start(), chord.start() + chord.duration()));
                    }
                    List<Out> over = GenLogic.melody(scale, 8, 72, 0.25, 50, 72, harmony, form, 0, 0, seed);
                    sameWhereLettersMatch(over, GenLogic.layout(8, 72, form, 0), false, "melody over chords " + form);

                    // The same seed gives the same notes.
                    check(chords.equals(GenLogic.chords(scale, 8, 72, 4, seed % 2 == 0, true, 60, rhythm, form, 0, seed)), "chords: not repeatable");
                    check(over.equals(GenLogic.melody(scale, 8, 72, 0.25, 50, 72, harmony, form, 0, 0, seed)), "melody: not repeatable");
                }
            }
        }

        // Layouts.
        check(GenLogic.layout(0, 64, "AAAB", 0).sectionBeats() == 16 && GenLogic.layout(0, 64, "AAAB", 0).sections() == 4, "16 bars: four 4-bar sections");
        check(GenLogic.layout(0, 32, "AAAB", 0).sectionBeats() == 8, "8 bars: 2-bar sections");
        check(GenLogic.layout(0, 16, "AAAB", 0).sectionBeats() == 4, "4 bars: 1-bar sections");
        check(GenLogic.layout(0, 8, "ABCD", 0).sections() == 4 && GenLogic.layout(0, 8, "ABCD", 0).sectionBeats() == 2, "2 bars: four half-bar sections");
        check(GenLogic.layout(0, 4, "AAAB", 0).sectionBeats() == 1, "1 bar: one beat each");
        check(GenLogic.layout(0, 48, "AAAB", 0).sectionBeats() == 12, "12 bars: 3 bars each");
        check(java.util.Arrays.equals(GenLogic.layout(0, 20, "AAAB", 0).letters(), new int[] {0, 0, 0, 1, 0}), "5 bars: one bar each, the form starts again");
        check(GenLogic.layout(0, 24, "AAAB", 0).sectionBeats() == 4, "6 bars: one bar each");
        check(GenLogic.layout(0, 64, "ABAC", 8).sections() == 8, "fixed 2-bar sections");
        check(java.util.Arrays.equals(GenLogic.layout(0, 32, "ABAC", 4).letters(), new int[] {0, 1, 0, 2, 0, 1, 0, 2}), "the form starts again");

        // Odd ranges: shorter than a chord, not a whole number of chords, shorter than a step.
        Scale c = scales[0];
        check(GenLogic.chords(c, 0, 1.5, 4, false, false, 60, GenLogic.HOLD, "AAAA", 0, 1).stream().allMatch(n -> n.duration() == 1.5), "short chord");
        List<Out> uneven = GenLogic.chords(c, 0, 6, 4, false, false, 60, GenLogic.HOLD, "AAAB", 0, 1);
        check(uneven.stream().mapToDouble(n -> n.start() + n.duration()).max().orElse(0) == 6, "uneven chords end at the range");
        check(GenLogic.melody(c, 0, 0.1, 0.25, 50, 72, List.of(), "AAAA", 0, 0, 1).size() == 1, "tiny melody");
        check(GenLogic.chords(c, 4, 4, 4, false, false, 60, GenLogic.HOLD, "AAAA", 0, 1).isEmpty(), "empty range");
        inRangeAndScale(GenLogic.melody(c, 0, 13.3, 0.25, 70, 72, List.of(), "ABAC", 0, 30, 3), c, 0, 13.3, "ragged melody");
        inRangeAndScale(GenLogic.chords(c, 0, 13.3, 2, true, true, 60, GenLogic.THREE_THREE_TWO, "ABAC", 0, 3), c, 0, 13.3, "ragged chords");

        check(glidedWithOverlap * 10 >= glidedCases * 9, "glide: too few melodies have a note running into the next");

        // A four-bar loop: A B C D is four different bars, A A A B three equal bars and another.
        for (long seed = 1; seed <= 200; seed++) {
            for (double step : new double[] {0.5, 0.25}) {
                List<String> bars = barsOf(GenLogic.melody(c, 0, 16, step, 50, 72, List.of(), "ABCD", 0, 0, seed));
                check(new java.util.HashSet<>(bars).size() == 4, "ABCD: two bars are the same, seed " + seed);
                bars = barsOf(GenLogic.melody(c, 0, 16, step, 50, 72, List.of(), "AAAB", 0, 0, seed));
                check(bars.get(0).equals(bars.get(1)) && bars.get(1).equals(bars.get(2)) && !bars.get(2).equals(bars.get(3)),
                        "AAAB: not three equal bars and another, seed " + seed);
            }
            List<String> chordBars = barsOf(GenLogic.chords(c, 0, 16, 4, false, true, 60, GenLogic.HOLD, "ABCD", 0, seed));
            check(new java.util.HashSet<>(chordBars).size() >= 3, "ABCD chords: too many equal bars, seed " + seed);
            chordBars = barsOf(GenLogic.chords(c, 0, 16, 4, false, true, 60, GenLogic.HOLD, "AAAB", 0, seed));
            check(chordBars.get(0).equals(chordBars.get(1)) && chordBars.get(1).equals(chordBars.get(2))
                    && !chordBars.get(2).equals(chordBars.get(3)), "AAAB chords, seed " + seed);
        }

        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("GENLOGIC_OK");
    }

    /** Sections with the same letter hold the same notes; a melody's may differ in pitch, never in rhythm. */
    private static void sameWhereLettersMatch(List<Out> notes, Layout layout, boolean rhythmOnly, String what) {
        for (int a = 0; a < layout.sections(); a++) {
            for (int b = a + 1; b < layout.sections(); b++) {
                if (layout.letters()[a] != layout.letters()[b] || layout.to(b) - layout.from(b) != layout.to(a) - layout.from(a)) {
                    continue;
                }
                check(inSection(notes, layout, a, rhythmOnly).equals(inSection(notes, layout, b, rhythmOnly)),
                        what + ": sections " + a + " and " + b + " differ");
            }
        }
    }

    private static List<String> inSection(List<Out> notes, Layout layout, int section, boolean rhythmOnly) {
        List<String> found = new ArrayList<>();
        for (Out note : notes) {
            if (note.start() >= layout.from(section) - 1e-9 && note.start() < layout.to(section) - 1e-9) {
                found.add((note.start() - layout.from(section)) + ":" + note.duration() + (rhythmOnly ? "" : ":" + note.key()));
            }
        }
        return found;
    }

    private static void oneVoice(List<Out> melody, double overlap) {
        for (int i = 1; i < melody.size(); i++) {
            Out before = melody.get(i - 1);
            check(before.start() + before.duration() <= melody.get(i).start() + overlap + 1e-9, "melody: two notes at once");
        }
    }

    private static int overlaps(List<Out> melody) {
        int count = 0;
        for (int i = 1; i < melody.size(); i++) {
            if (melody.get(i - 1).start() + melody.get(i - 1).duration() > melody.get(i).start() + 1e-9) {
                count++;
            }
        }
        return count;
    }

    /** Each of the four bars from beat 0 as text: its notes' places, lengths and keys. */
    private static List<String> barsOf(List<Out> notes) {
        List<String> bars = new ArrayList<>();
        for (int bar = 0; bar < 4; bar++) {
            StringBuilder text = new StringBuilder();
            for (Out note : notes) {
                if (note.start() >= bar * 4 - 1e-9 && note.start() < bar * 4 + 4 - 1e-9) {
                    text.append(note.start() - bar * 4).append(':').append(note.duration()).append(':').append(note.key()).append(' ');
                }
            }
            bars.add(text.toString());
        }
        return bars;
    }

    private static void inRangeAndScale(List<Out> notes, Scale scale, double start, double end, String what) {
        for (Out note : notes) {
            check(note.key() >= 0 && note.key() <= 127, what + ": key " + note.key());
            check(note.start() >= start - 1e-9 && note.start() + note.duration() <= end + 1e-9, what + ": outside the range");
            check(note.duration() > 0, what + ": no length");
            check(note.velocity() > 0 && note.velocity() <= 1, what + ": velocity");
            boolean in = false;
            for (int step : scale.steps) {
                in |= Math.floorMod(note.key() - scale.root, 12) == step;
            }
            check(in, what + ": out of scale: " + note.key());
        }
    }

    private static void show(Scale scale) {
        for (String form : new String[] {"AAAB", "ABAC"}) {
            System.out.println("== F# minor, 4 bars, form " + form);
            List<Out> chords = GenLogic.chords(scale, 0, 16, 4, false, true, 60, GenLogic.HOLD, form, 0, 7);
            for (double bar = 0; bar < 16; bar += 4) {
                double at = bar;
                StringBuilder line = new StringBuilder("chord bar " + (int) (bar / 4 + 1) + ":");
                chords.stream().filter(n -> n.start() == at).forEach(n -> line.append(' ').append(name(n.key())));
                System.out.println(line);
            }
            List<Out> melody = GenLogic.melody(scale, 0, 16, 0.25, 50, 72, List.of(), form, 0, 0, 7);
            for (double bar = 0; bar < 16; bar += 4) {
                char[] grid = "................".toCharArray();
                StringBuilder line = new StringBuilder();
                for (Out n : melody) {
                    if (n.start() >= bar && n.start() < bar + 4) {
                        grid[(int) Math.round((n.start() - bar) * 4)] = 'x';
                        line.append(' ').append(name(n.key()));
                    }
                }
                System.out.println("melody bar " + (int) (bar / 4 + 1) + ": " + new String(grid) + line);
            }
        }
    }

    private static String name(int key) {
        return NAMES[key % 12] + (key / 12 - 2);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            if (failures++ < 15) {
                System.out.println("FAIL " + message);
            }
        }
    }
}
