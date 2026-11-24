package ch.coldforge.notegen;

import ch.coldforge.notegen.GenLogic.Out;
import ch.coldforge.notegen.GenLogic.Scale;
import ch.coldforge.notegen.GenLogic.Sounding;
import java.util.ArrayList;
import java.util.List;

/** Offline checks of the generator, without Bitwig. Run with: java -cp <classes> ch.coldforge.notegen.GenLogicTest */
public final class GenLogicTest {
    private static int failures;

    public static void main(String[] args) {
        boolean[] minor = new boolean[12];
        for (int step : new int[] {0, 2, 3, 5, 7, 8, 10}) {
            minor[step] = true;
        }
        boolean[] pentatonic = new boolean[12];
        for (int step : new int[] {0, 3, 5, 7, 10}) {
            pentatonic[step] = true;
        }
        Scale[] scales = {Scale.major(0), new Scale(9, minor), new Scale(4, pentatonic), new Scale(6, minor)};

        for (Scale scale : scales) {
            for (long seed = 1; seed <= 300; seed++) {
                // Chords: 4 bars from beat 8, one per bar, with bass.
                List<Out> chords = GenLogic.chords(scale, 8, 24, 4, seed % 2 == 0, true, 60, seed);
                check(!chords.isEmpty(), "chords: empty");
                inRangeAndScale(chords, scale, 8, 24, "chords");
                check(chords.stream().mapToDouble(Out::start).distinct().count() == 4, "chords: not 4 chords");
                for (double from = 8; from < 24; from += 4) {
                    double at = from;
                    long tones = chords.stream().filter(n -> n.start() == at).count();
                    check(tones >= 3 && tones <= 5, "chords: " + tones + " tones in a chord");
                }
                // The first chord holds the key's root in the bass.
                int lowest = chords.stream().filter(n -> n.start() == 8).mapToInt(Out::key).min().orElse(-1);
                check(lowest % 12 == scale.root, "chords: first bass is not the root");

                // Melody on every rate and several densities.
                for (double step : new double[] {1, 0.5, 0.25}) {
                    for (int density : new int[] {5, 50, 100}) {
                        List<Out> melody = GenLogic.melody(scale, 8, 24, step, density, 72, List.of(), seed);
                        check(!melody.isEmpty(), "melody: empty");
                        inRangeAndScale(melody, scale, 8, 24, "melody");
                        for (int i = 1; i < melody.size(); i++) {
                            Out before = melody.get(i - 1);
                            check(before.start() + before.duration() <= melody.get(i).start() + 1e-9, "melody: two notes at once");
                        }
                        for (Out note : melody) {
                            check(Math.abs(note.key() - 72) <= 16, "melody: far from the centre: " + note.key());
                        }
                    }
                }

                // The melody rests on a tone of the chord sounding at its end.
                List<Sounding> harmony = new ArrayList<>();
                for (Out chord : chords) {
                    harmony.add(new Sounding(chord.key(), chord.start(), chord.start() + chord.duration()));
                }
                List<Out> over = GenLogic.melody(scale, 8, 24, 0.25, 50, 72, harmony, seed);
                Out end = over.get(over.size() - 1);
                check(GenLogic.chordAt(scale, harmony, end.start()).contains(end.key() % 12), "melody: ends off the chord");

                // The same seed gives the same notes.
                check(chords.equals(GenLogic.chords(scale, 8, 24, 4, seed % 2 == 0, true, 60, seed)), "chords: not repeatable");
                check(over.equals(GenLogic.melody(scale, 8, 24, 0.25, 50, 72, harmony, seed)), "melody: not repeatable");
            }
        }

        // Odd ranges: shorter than a chord, not a whole number of chords, shorter than a step.
        check(GenLogic.chords(scales[0], 0, 1.5, 4, false, false, 60, 1).stream().allMatch(n -> n.duration() == 1.5), "short chord");
        List<Out> uneven = GenLogic.chords(scales[0], 0, 6, 4, false, false, 60, 1);
        check(uneven.stream().mapToDouble(n -> n.start() + n.duration()).max().orElse(0) == 6, "uneven chords end at the range");
        check(GenLogic.melody(scales[0], 0, 0.1, 0.25, 50, 72, List.of(), 1).size() == 1, "tiny melody");
        check(GenLogic.chords(scales[0], 4, 4, 4, false, false, 60, 1).isEmpty(), "empty range");

        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("GENLOGIC_OK");
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

    private static void check(boolean ok, String message) {
        if (!ok) {
            if (failures++ < 15) {
                System.out.println("FAIL " + message);
            }
        }
    }
}
