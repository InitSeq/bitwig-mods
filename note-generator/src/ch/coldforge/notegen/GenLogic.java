package ch.coldforge.notegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * What the generator writes: chords or a melody over a time range, in a scale, laid out in a form such as
 * A A A B. Plain Java with no Bitwig names, so it can be tested without Bitwig. Times are in beats (quarter
 * notes), bars are 4 beats.
 *
 * The range is cut into sections. Each section plays the material of its letter: the same letter is the same
 * chords or the same motif again, fitted to the harmony at its new place.
 */
final class GenLogic {
    private GenLogic() {
    }

    /** The forms on offer. Each letter is one section; a range with more sections starts the form again. */
    static final String[] FORMS = {"AAAA", "AAAB", "AABA", "AABB", "ABAB", "ABAC", "ABCB", "ABCD"};

    /** How chords are played. */
    static final int HOLD = 0;
    static final int PULSE = 1;
    static final int OFFBEAT = 2;
    static final int THREE_THREE_TWO = 3;

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

        /** A major or minor triad stands on this degree. */
        boolean consonant(int degree) {
            int lower = interval(degree, degree + 2);
            int upper = interval(degree + 2, degree + 4);
            return (lower == 4 && upper == 3) || (lower == 3 && upper == 4);
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

    // Sections.

    /** The range cut into sections, each with the letter of the form it plays (0 for A). */
    record Layout(double start, double end, double sectionBeats, int[] letters) {
        int sections() {
            return letters.length;
        }

        double from(int section) {
            return start + section * sectionBeats;
        }

        double to(int section) {
            return Math.min(end, from(section) + sectionBeats);
        }

        /** The section holding this time. */
        int at(double time) {
            return Math.max(0, Math.min(letters.length - 1, (int) Math.floor((time - start) / sectionBeats + 1e-9)));
        }
    }

    /**
     * sectionBeats 0 chooses the length: a quarter of the range in whole bars, so the form fits once; one bar for
     * a range under four bars.
     */
    static Layout layout(double start, double end, String form, double sectionBeats) {
        double total = end - start;
        double section = sectionBeats;
        if (!(section > 0)) {
            int bars = (int) Math.floor(total / 4 + 1e-9);
            section = bars >= 4 ? (bars / 4) * 4.0 : Math.min(4.0, total);
        }
        section = Math.min(section, total);
        int count = Math.max(1, (int) Math.ceil(total / section - 1e-9));
        int[] letters = new int[count];
        for (int i = 0; i < count; i++) {
            letters[i] = Math.max(0, Math.min(3, form.charAt(i % form.length()) - 'A'));
        }
        return new Layout(start, end, section, letters);
    }

    // Chords.

    /** Four-chord loops in a major key, as degrees from 0. */
    private static final int[][] MAJOR_LOOPS = {
            {0, 4, 5, 3}, {0, 5, 3, 4}, {0, 3, 5, 4}, {0, 3, 0, 4}, {0, 2, 3, 4}, {0, 5, 1, 4},
            {5, 3, 0, 4}, {3, 0, 4, 5}, {1, 4, 0, 5}, {3, 4, 5, 0}, {5, 4, 3, 4},
    };

    /** Four-chord loops in a minor key, as degrees from 0. */
    private static final int[][] MINOR_LOOPS = {
            {0, 5, 2, 6}, {0, 6, 5, 6}, {0, 3, 5, 4}, {0, 5, 3, 4}, {0, 2, 6, 5}, {0, 4, 5, 3}, {0, 6, 5, 4},
            {5, 6, 0, 0}, {3, 0, 6, 5}, {5, 2, 6, 0}, {3, 5, 6, 0}, {2, 6, 0, 5},
    };

    /** The four loops the letters draw on: the first starts on the key's own chord. */
    private static int[][] loops(Scale scale, long seed) {
        Random random = new Random(seed * 7919 + 13);
        if (scale.size() != 7) { // no seven degrees to name: a walk over the scale's own chords
            int[][] walks = new int[4][4];
            for (int[] walk : walks) {
                for (int i = 0; i < 4; i++) {
                    walk[i] = random.nextInt(scale.size());
                }
            }
            walks[0][0] = 0;
            return walks;
        }
        List<int[]> pool = new ArrayList<>(List.of(scale.interval(0, 2) == 3 ? MINOR_LOOPS : MAJOR_LOOPS));
        Collections.shuffle(pool, random);
        List<int[]> chosen = new ArrayList<>();
        for (int[] loop : pool) {
            if (loop[0] == 0) {
                chosen.add(loop);
                break;
            }
        }
        for (int[] loop : pool) {
            if (chosen.size() < 4 && !chosen.contains(loop)) {
                chosen.add(loop);
            }
        }
        int[][] loops = new int[4][];
        for (int i = 0; i < 4; i++) {
            loops[i] = chosen.get(i).clone();
            for (int j = 0; j < 4; j++) {
                loops[i][j] = singable(scale, loops[i][j]);
            }
        }
        return loops;
    }

    /** This degree, or a near one when its chord is diminished or augmented in this scale. */
    private static int singable(Scale scale, int degree) {
        for (int other : new int[] {degree, degree + 2, degree + 5, 0}) {
            if (scale.consonant(other)) {
                return Math.floorMod(other, scale.size());
            }
        }
        return degree;
    }

    /** The degrees of one section's chords. A section of one or two chords shares a loop with its neighbours. */
    private static int[] sectionDegrees(int[][] loops, int letter, int chords) {
        int[] degrees = new int[chords];
        for (int i = 0; i < chords; i++) {
            if (chords == 1) {
                degrees[i] = loops[0][letter];
            } else if (chords == 2) {
                degrees[i] = loops[letter / 2][(letter % 2) * 2 + i];
            } else {
                degrees[i] = loops[letter][i % 4];
            }
        }
        return degrees;
    }

    /** A chord in time: its degree, and when it sounds. */
    private record Slot(int degree, double from, double to) {
    }

    private static List<Slot> slots(Scale scale, Layout layout, double chordLength, long seed) {
        int[][] loops = loops(scale, seed);
        List<Slot> slots = new ArrayList<>();
        for (int section = 0; section < layout.sections(); section++) {
            double from = layout.from(section);
            double to = layout.to(section);
            double length = Math.min(chordLength, layout.sectionBeats());
            int chords = Math.max(1, (int) Math.ceil(layout.sectionBeats() / length - 1e-9));
            int[] degrees = sectionDegrees(loops, layout.letters()[section], chords);
            for (int i = 0; i < chords; i++) {
                double at = from + i * length;
                if (at < to - 1e-6) {
                    slots.add(new Slot(degrees[i], at, Math.min(to, at + length)));
                }
            }
        }
        return slots;
    }

    /**
     * A chord progression over the range, laid out in the form. Each chord is voiced close to the one before,
     * around centre, and played in the rhythm given.
     */
    static List<Out> chords(Scale scale, double start, double end, double chordLength, boolean sevenths, boolean bass,
                            int centre, int rhythm, String form, double sectionBeats, long seed) {
        List<Out> out = new ArrayList<>();
        if (!(end - start > 1e-6) || !(chordLength > 0)) {
            return out;
        }
        Random random = new Random(seed);
        Layout layout = layout(start, end, form, sectionBeats);
        double target = centre;
        int section = -1;
        for (Slot slot : slots(scale, layout, chordLength, seed)) {
            if (layout.at(slot.from()) != section) { // each section starts from the centre, so a letter sounds the same each time
                section = layout.at(slot.from());
                target = centre;
            }
            int tones = Math.min(scale.size(), sevenths ? 4 : 3);
            TreeSet<Integer> keys = new TreeSet<>();
            for (int tone = 0; tone < tones; tone++) {
                keys.add(nearest(scale.pitchClass(slot.degree() + 2 * tone), target));
            }
            double sum = 0;
            for (int key : keys) {
                sum += key;
            }
            // The next chord stays near this one, but never drifts far from the centre.
            target = Math.max(centre - 5, Math.min(centre + 5, sum / keys.size()));
            int low = centre - 24;
            int root = low + Math.floorMod(scale.pitchClass(slot.degree()) - low, 12);

            for (double[] hit : hits(rhythm, slot.from(), slot.to(), false)) {
                for (int key : keys) {
                    add(out, key, hit, 0.72, random);
                }
            }
            if (bass) {
                for (double[] hit : hits(rhythm, slot.from(), slot.to(), true)) {
                    add(out, root, hit, 0.78, random);
                }
            }
        }
        return out;
    }

    private static void add(List<Out> out, int key, double[] hit, double velocity, Random random) {
        if (key >= 0 && key <= 127 && hit[1] > 1e-6) {
            out.add(new Out(key, hit[0], hit[1], clamp(velocity + (random.nextDouble() - 0.5) * 0.08)));
        }
    }

    /** Where a chord (or its bass) is struck between from and to: pairs of start and length. */
    private static List<double[]> hits(int rhythm, double from, double to, boolean bass) {
        List<double[]> hits = new ArrayList<>();
        if (rhythm == HOLD) {
            hits.add(new double[] {from, to - from});
        } else if (rhythm == PULSE) {
            for (double at = from; at < to - 1e-6; at += 1) {
                hits.add(new double[] {at, Math.min(0.75, to - at)});
            }
        } else if (rhythm == OFFBEAT) { // the bass on the beat, the chord between the beats
            for (double at = from + (bass ? 0 : 0.5); at < to - 1e-6; at += 1) {
                hits.add(new double[] {at, Math.min(0.5, to - at)});
            }
        } else {
            double[] lengths = {1.5, 1.5, 1};
            int i = 0;
            for (double at = from; at < to - 1e-6; at += lengths[i++ % 3]) {
                hits.add(new double[] {at, Math.min(lengths[i % 3] - 0.25, to - at)});
            }
        }
        return hits;
    }

    /** The key with this pitch class nearest to target. */
    private static int nearest(int pitchClass, double target) {
        int below = (int) Math.floor(target);
        below -= Math.floorMod(below - pitchClass, 12);
        return target - below <= below + 12 - target ? below : below + 12;
    }

    // Melody.

    /** Rhythms of eight steps: x starts a note. Three of eight steps, five, seven or eight. */
    private static final String[][] CELLS = {
            {"x..x..x.", "x.x...x.", "x...x.x.", "x..x.x..", "x.x..x..", "x..x...x"},
            {"x.xx.xx.", "xx.x.xx.", "x.x.xx.x", "xxx.x.x.", "x.xxx.x.", "x.xx.x.x", "xx.xx.x."},
            {"xxxxxxx.", "xxx.xxxx", "xxxxx.xx", "xxxxxxxx", "xx.xxxxx", "xxxx.xxx"},
    };

    /** A note of a motif: where it starts in the section, how long it is held, how high above the anchor. */
    private record Tone(int step, int held, int height) {
    }

    /** One letter's motif over a section of this many steps. Its notes are one step long, some tied over a gap. */
    private static List<Tone> motif(int letter, int steps, double density, long seed) {
        Random random = new Random(seed * 1000003 + letter * 7717 + 5);
        // Three rhythm cells, laid out a b a c: the motif repeats itself before it moves on.
        String[] tier = CELLS[density < 0.34 ? 0 : density < 0.67 ? 1 : 2];
        String[] cells = new String[3];
        for (int i = 0; i < 3; i++) {
            cells[i] = tier[random.nextInt(tier.length)];
        }
        List<Integer> onsets = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            String cell = cells[new int[] {0, 1, 0, 2}[(step / 8) % 4]];
            if (step == 0 || cell.charAt(step % 8) == 'x') {
                onsets.add(step);
            }
        }
        // The line: one clear shape per motif, a different one for each letter.
        int shape = Math.floorMod((int) (seed % 5) + letter * 2 + (letter > 0 ? random.nextInt(2) : 0), 5);
        int lift = new int[] {0, 2, -2, 3}[letter];
        List<Tone> tones = new ArrayList<>();
        int before = 0;
        for (int i = 0; i < onsets.size(); i++) {
            int step = onsets.get(i);
            double place = steps <= 1 ? 0 : (double) step / steps;
            double line = switch (shape) {
                case 0 -> 4 * Math.sin(Math.PI * place); // an arch
                case 1 -> 4 - 5 * place; // falling
                case 2 -> -1 + 5 * place; // rising
                case 3 -> 2.5 * Math.sin(2 * Math.PI * place); // a wave
                default -> 1 - 3 * Math.sin(Math.PI * place); // a valley
            };
            int height = (int) Math.round(line) + lift + new int[] {-1, 0, 0, 0, 1}[random.nextInt(5)];
            if (i > 0) {
                height = Math.max(before - 3, Math.min(before + 3, height)); // no wide leaps
            }
            before = height;
            boolean lastTone = i == onsets.size() - 1;
            int gap = (lastTone ? steps : onsets.get(i + 1)) - step;
            int held = lastTone ? Math.min(gap, 4) : gap > 1 && random.nextDouble() < 0.6 ? 2 : 1;
            tones.add(new Tone(step, held, height));
        }
        return tones;
    }

    /**
     * A one-voice melody over the range on a grid of step beats, laid out in the form. Each letter is a motif: a
     * rhythm of notes one step long, and a line. A letter is written once, fitted to the harmony at its first
     * place, and every later section of that letter is the same notes again, exactly. With no harmony given, the
     * melody follows the chords the same seed and form would generate.
     *
     * glidePercent: how many notes run on into the next one (by half a step), for a synth that glides between
     * overlapping notes. 0 keeps every note clear of the next.
     */
    static List<Out> melody(Scale scale, double start, double end, double step, int densityPercent, int centre,
                            List<Sounding> harmony, String form, double sectionBeats, int glidePercent, long seed) {
        List<Out> out = new ArrayList<>();
        if (!(end - start > 1e-6) || !(step > 0)) {
            return out;
        }
        Layout layout = layout(start, end, form, sectionBeats);
        if (harmony.isEmpty()) {
            harmony = new ArrayList<>();
            for (Slot slot : slots(scale, layout, 4, seed)) {
                for (int tone = 0; tone < Math.min(3, scale.size()); tone++) {
                    harmony.add(new Sounding(scale.pitchClass(slot.degree() + 2 * tone), slot.from(), slot.to()));
                }
            }
        }
        double density = Math.max(1, Math.min(100, densityPercent)) / 100.0;
        int sectionSteps = Math.max(1, (int) Math.round(layout.sectionBeats() / step));
        int[] ladder = scale.ladder();
        int lowest = indexNear(ladder, centre - 14);
        int highest = indexNear(ladder, centre + 14);

        // Each letter's notes, relative to its section's start: written where the letter first stands.
        List<List<Out>> written = new ArrayList<>(List.of(List.of(), List.of(), List.of(), List.of()));
        boolean[] done = new boolean[4];
        for (int section = 0; section < layout.sections(); section++) {
            int letter = layout.letters()[section];
            double from = layout.from(section);
            if (!done[letter]) {
                done[letter] = true;
                Random random = new Random(seed * 31 + letter * 131 + 7);
                List<Tone> tones = motif(letter, sectionSteps, density, seed);
                int anchor = snap(ladder, indexNear(ladder, centre), chordAt(scale, harmony, from), 4, lowest, highest);
                boolean closes = section == layout.sections() - 1; // the letter of the last section comes to rest
                List<Out> notes = new ArrayList<>();
                for (int i = 0; i < tones.size(); i++) {
                    Tone tone = tones.get(i);
                    double time = from + tone.step() * step;
                    List<Integer> chord = chordAt(scale, harmony, time);
                    int at = Math.max(lowest, Math.min(highest, anchor + tone.height()));
                    if (i == tones.size() - 1) {
                        at = closes ? snap(ladder, at, chord.contains(scale.root) ? List.of(scale.root) : chord, 7, lowest, highest)
                                : snap(ladder, at, chord, 4, lowest, highest);
                    } else if (tone.step() % 4 == 0) {
                        at = snap(ladder, at, chord, 2, lowest, highest);
                    }
                    double accent = tone.step() % 8 == 0 ? 0.82 : tone.step() % 4 == 0 ? 0.72 : 0.62;
                    notes.add(new Out(ladder[at], tone.step() * step, tone.held() * step,
                            clamp(accent + (random.nextDouble() - 0.5) * 0.08)));
                }
                // Glides: a note runs on into the next, when that one is another pitch.
                for (int i = 0; i + 1 < notes.size(); i++) {
                    Out note = notes.get(i);
                    Out next = notes.get(i + 1);
                    if (next.key() != note.key() && random.nextDouble() * 100 < glidePercent) {
                        notes.set(i, new Out(note.key(), note.start(), next.start() - note.start() + step / 2, note.velocity()));
                    }
                }
                written.set(letter, notes);
            }
            double length = layout.to(section) - from;
            for (Out note : written.get(letter)) {
                if (note.start() < length - 1e-6) {
                    out.add(new Out(note.key(), from + note.start(), Math.min(note.duration(), length - note.start()), note.velocity()));
                }
            }
        }
        return out;
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
        return Math.max(lowest, Math.min(highest, index));
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
            sum += Math.max(0, weight);
        }
        double point = random.nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            point -= Math.max(0, weights[i]);
            if (point < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static double clamp(double velocity) {
        return Math.max(0.05, Math.min(1, velocity));
    }
}
