package ch.coldforge.notegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * What the generator writes: chords or a melody over a time range, in a scale. Plain Java with no Bitwig
 * names, so it can be tested without Bitwig. Times are in beats (quarter notes), bars are 4 beats.
 */
final class GenLogic {
    private GenLogic() {
    }

    /** A note to write. Velocity is 0 to 1. */
    record Out(int key, double start, double duration, double velocity) {
    }

    /** A note already in the clip that the melody should fit to. */
    record Sounding(int key, double start, double end) {
    }

    /** A key: its root as a pitch class and which semitones above the root belong to it. */
    static final class Scale {
        final int root;
        final int[] steps; // semitones above the root, ascending

        Scale(int root, boolean[] inScale) {
            this.root = Math.floorMod(root, 12);
            List<Integer> found = new ArrayList<>();
            for (int i = 0; i < 12 && i < inScale.length; i++) {
                if (inScale[i]) {
                    found.add(i);
                }
            }
            if (found.isEmpty()) {
                found = List.of(0, 2, 4, 5, 7, 9, 11);
            }
            steps = found.stream().mapToInt(Integer::intValue).toArray();
        }

        static Scale major(int root) {
            boolean[] in = new boolean[12];
            for (int step : new int[] {0, 2, 4, 5, 7, 9, 11}) {
                in[step] = true;
            }
            return new Scale(root, in);
        }

        int size() {
            return steps.length;
        }

        /** The pitch class of a degree; degrees wrap around the scale. */
        int pitchClass(int degree) {
            return (root + steps[Math.floorMod(degree, steps.length)]) % 12;
        }

        /** Semitones from one degree up to another, 1 to 12. */
        int interval(int from, int to) {
            int semitones = Math.floorMod(pitchClass(to) - pitchClass(from), 12);
            return semitones == 0 ? 12 : semitones;
        }

        /** Every key of the scale, 0 to 127, ascending. */
        int[] ladder() {
            List<Integer> keys = new ArrayList<>();
            for (int key = 0; key <= 127; key++) {
                int above = Math.floorMod(key - root, 12);
                for (int step : steps) {
                    if (step == above) {
                        keys.add(key);
                    }
                }
            }
            return keys.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    // Chords.

    /** How likely each degree follows another in a seven-note scale: rows are from, columns are to. */
    private static final double[][] FOLLOWS = {
            {0, 2, 1, 3, 3, 3, 0.3}, // I
            {0.5, 0, 1, 1, 4, 0.5, 1}, // ii
            {0.5, 1, 0, 3, 0.5, 3, 0}, // iii
            {3, 2, 0.3, 0, 3, 1, 0.2}, // IV
            {4, 0.3, 0.5, 2, 0, 3, 0}, // V
            {0.5, 2, 1, 3, 2, 0, 0.2}, // vi
            {3, 0.2, 1, 0.2, 0.5, 0.5, 0}, // vii
    };

    /**
     * A chord progression from start to end, one chord every chordLength beats, each held until the next.
     * The first chord is the key's own. Each chord is voiced close to the one before, around centre.
     */
    static List<Out> chords(Scale scale, double start, double end, double chordLength, boolean sevenths,
                            boolean bass, int centre, long seed) {
        List<Out> out = new ArrayList<>();
        if (!(end > start) || !(chordLength > 0)) {
            return out;
        }
        Random random = new Random(seed);
        int count = Math.max(1, (int) Math.ceil((end - start) / chordLength - 1e-9));
        int[] degrees = progression(scale, count, random);
        double target = centre;
        for (int i = 0; i < count; i++) {
            double from = start + i * chordLength;
            double length = Math.min(chordLength, end - from);
            if (!(length > 1e-6)) {
                break;
            }
            int tones = Math.min(scale.size(), sevenths ? 4 : 3);
            TreeSet<Integer> keys = new TreeSet<>();
            for (int tone = 0; tone < tones; tone++) {
                keys.add(nearest(scale.pitchClass(degrees[i] + 2 * tone), target));
            }
            double sum = 0;
            for (int key : keys) {
                sum += key;
            }
            // The next chord stays near this one, but never drifts far from the centre.
            target = Math.max(centre - 5, Math.min(centre + 5, sum / keys.size()));
            if (bass) {
                int low = centre - 24;
                keys.add(low + Math.floorMod(scale.pitchClass(degrees[i]) - low, 12));
            }
            for (int key : keys) {
                if (key >= 0 && key <= 127) {
                    out.add(new Out(key, from, length, clamp(0.72 + (random.nextDouble() - 0.5) * 0.08)));
                }
            }
        }
        return out;
    }

    /** The degrees of the chords: starts on the key's own chord, never repeats a chord, ends leading back to it. */
    static int[] progression(Scale scale, int count, Random random) {
        int size = scale.size();
        int[] degrees = new int[count];
        for (int i = 1; i < count; i++) {
            double[] weights = new double[size];
            for (int to = 0; to < size; to++) {
                if (to == degrees[i - 1]) {
                    continue;
                }
                double weight = size == 7 ? FOLLOWS[degrees[i - 1]][to] : 1;
                if (size >= 5 && !consonant(scale, to)) {
                    weight *= 0.15; // diminished and augmented chords are rare
                }
                if (size == 7 && i == count - 1 && count >= 4) {
                    weight *= to == 4 ? 3 : to == 3 ? 2 : to == 0 ? 0.2 : 1; // the loop comes home through V or IV
                }
                weights[to] = weight;
            }
            degrees[i] = pick(weights, random);
        }
        return degrees;
    }

    /** A major or minor triad on this degree. */
    private static boolean consonant(Scale scale, int degree) {
        int lower = scale.interval(degree, degree + 2);
        int upper = scale.interval(degree + 2, degree + 4);
        return (lower == 4 && upper == 3) || (lower == 3 && upper == 4);
    }

    /** The key with this pitch class nearest to target. */
    private static int nearest(int pitchClass, double target) {
        int below = (int) Math.floor(target);
        below -= Math.floorMod(below - pitchClass, 12);
        return target - below <= below + 12 - target ? below : below + 12;
    }

    // Melody.

    private static final int[] MOVES = {-4, -3, -2, -1, 0, 1, 2, 3, 4};
    private static final double[] MOVE_WEIGHTS = {0.5, 1, 3, 5, 1, 5, 3, 1, 0.5};

    /**
     * A one-voice melody from start to end on a grid of step beats. A two-bar rhythm repeats, and each repeat
     * starts with the first one's shape before it goes its own way. Notes on the beat lean on the harmony
     * sounding there (the key's own chord when nothing sounds), and the last note comes to rest.
     */
    static List<Out> melody(Scale scale, double start, double end, double step, int densityPercent, int centre,
                            List<Sounding> harmony, long seed) {
        List<Out> out = new ArrayList<>();
        if (!(end > start) || !(step > 0)) {
            return out;
        }
        Random random = new Random(seed);
        int total = Math.max(1, (int) Math.floor((end - start) / step + 1e-6));
        int beat = Math.max(1, (int) Math.round(1 / step));
        int bar = beat * 4;
        int unit = Math.min(total, bar * 2);
        double density = Math.max(1, Math.min(100, densityPercent)) / 100.0;

        // The rhythm: where notes start within the unit, and how long each is held.
        boolean[] onset = new boolean[unit];
        for (int i = 0; i < unit; i++) {
            int place = i % bar;
            double weight = place == 0 ? 1 : place % beat == 0 ? 0.85 : beat >= 2 && place % (beat / 2) == 0 ? 0.55 : 0.3;
            onset[i] = i == 0 || random.nextDouble() < weight * (0.25 + 1.5 * density);
        }
        int[] held = new int[unit];
        for (int i = 0; i < unit; i++) {
            if (onset[i]) {
                int gap = 1;
                while (i + gap < unit && !onset[i + gap]) {
                    gap++;
                }
                held[i] = gap > 1 && random.nextDouble() < 0.3 ? Math.max(1, gap / 2) : Math.min(gap, bar);
            }
        }

        int[] ladder = scale.ladder();
        int lowest = indexNear(ladder, centre - 14);
        int highest = indexNear(ladder, centre + 14);
        int[] shape = new int[unit]; // the first unit's moves, for the repeats
        int at = -1;
        int last = lastOnset(onset, unit, total);
        for (int i = 0; i < total; i++) {
            int place = i % unit;
            if (!onset[place]) {
                continue;
            }
            double time = start + i * step;
            List<Integer> chord = chordAt(scale, harmony, time);
            boolean onBeat = place % beat == 0;
            if (at < 0) {
                at = snap(ladder, indexNear(ladder, centre), chord, 7, lowest, highest);
            } else {
                int move;
                if (i >= unit && place < unit / 2 && random.nextDouble() < 0.8) {
                    move = shape[place];
                } else {
                    double[] weights = MOVE_WEIGHTS.clone();
                    for (int m = 0; m < MOVES.length; m++) {
                        if ((ladder[at] > centre + 7 && MOVES[m] > 0) || (ladder[at] < centre - 7 && MOVES[m] < 0)) {
                            weights[m] *= 0.3; // turn back towards the centre
                        }
                    }
                    move = MOVES[pick(weights, random)];
                }
                if (i < unit) {
                    shape[place] = move;
                }
                at = Math.max(lowest, Math.min(highest, at + move));
                if (i == last) {
                    List<Integer> rest = chord.contains(scale.root) ? List.of(scale.root) : chord;
                    at = snap(ladder, at, rest, 7, lowest, highest);
                } else if (onBeat && random.nextDouble() < 0.75) {
                    at = snap(ladder, at, chord, 3, lowest, highest);
                }
            }
            double length = Math.min(held[place] * step, end - time);
            if (length > 1e-6) {
                double accent = place % bar == 0 ? 0.8 : onBeat ? 0.72 : 0.6;
                out.add(new Out(ladder[at], time, length, clamp(accent + (random.nextDouble() - 0.5) * 0.1)));
            }
        }
        return out;
    }

    private static int lastOnset(boolean[] onset, int unit, int total) {
        for (int i = total - 1; i > 0; i--) {
            if (onset[i % unit]) {
                return i;
            }
        }
        return 0;
    }

    /** The pitch classes sounding at this time; the key's own chord when nothing sounds. */
    static List<Integer> chordAt(Scale scale, List<Sounding> harmony, double time) {
        List<Integer> classes = new ArrayList<>();
        for (Sounding note : harmony) {
            int pitchClass = Math.floorMod(note.key(), 12);
            if (note.start() <= time + 1e-6 && time + 1e-6 < note.end() && !classes.contains(pitchClass)) {
                classes.add(pitchClass);
            }
        }
        if (classes.isEmpty()) {
            for (int tone = 0; tone < Math.min(3, scale.size()); tone++) {
                classes.add(scale.pitchClass(scale.size() >= 5 ? 2 * tone : tone));
            }
        }
        return classes;
    }

    /** The place on the ladder from lowest to highest nearest to index, at most reach places away, whose pitch class is one of these. */
    private static int snap(int[] ladder, int index, List<Integer> pitchClasses, int reach, int lowest, int highest) {
        for (int away = 0; away <= reach; away++) {
            for (int side : new int[] {index - away, index + away}) {
                if (side >= lowest && side <= highest && pitchClasses.contains(ladder[side] % 12)) {
                    return side;
                }
            }
        }
        return index;
    }

    private static int indexNear(int[] ladder, int key) {
        int best = 0;
        for (int i = 1; i < ladder.length; i++) {
            if (Math.abs(ladder[i] - key) < Math.abs(ladder[best] - key)) {
                best = i;
            }
        }
        return best;
    }

    private static int pick(double[] weights, Random random) {
        double sum = 0;
        for (double weight : weights) {
            sum += weight;
        }
        if (!(sum > 0)) {
            return random.nextInt(weights.length);
        }
        double point = random.nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            point -= weights[i];
            if (point < 0 && weights[i] > 0) {
                return i;
            }
        }
        for (int i = weights.length - 1; i >= 0; i--) {
            if (weights[i] > 0) {
                return i;
            }
        }
        return 0;
    }

    private static double clamp(double velocity) {
        return Math.max(0.05, Math.min(1, velocity));
    }
}
