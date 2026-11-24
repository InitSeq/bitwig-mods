package ch.coldforge.notegen;

import bitwig.obf.DrA; // a note clip
import bitwig.obf.aNi; // the Detail Editor panel
import bitwig.obf.bke; // the Detail Editor's controller
import bitwig.obf.bpF; // a clip's note editor layer
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.This;
import bml.pianoroll.Clip;
import bml.pianoroll.PianoRoll;
import java.lang.ref.WeakReference;

/**
 * Finds the note clip open in the Detail Editor, so the generator can write into a clip with no note selected.
 * Names are Bitwig 6.1.7's on macOS.
 */
public final class DetailEditor {
    private static final Bml.Mod MOD = Bml.mod("note-generator");
    private static volatile WeakReference<aNi> panel = new WeakReference<>(null);

    private DetailEditor() {
    }

    /** A note clip and its time range, in the clip's beats. */
    record Open(Clip clip, double start, double end) {
    }

    /** Bitwig sets up a Detail Editor panel: remembered, the newest one wins. */
    @Hook(target = aNi.class, method = "Gef(LHSH;Lbke;LbaN;)V", at = At.ENTRY, id = "detail-editor-set-up")
    public static void setUp(@This aNi built) {
        panel = new WeakReference<>(built);
    }

    /** The note clip the Detail Editor shows. Null when it shows none, several, or one that can't be edited. */
    static Open open() {
        try {
            aNi shown = panel.get();
            bke editor = shown != null ? shown.yX3() : null; // the panel's controller
            if (editor == null || editor.Xit() == null) {
                return null;
            }
            bpF lane = editor.GwM() instanceof bpF focused ? focused : null; // the focused layer
            if (lane == null) {
                for (Object layer : editor.jTT()) { // every layer: the one clip they all show
                    if (layer instanceof bpF other) {
                        if (lane != null && lane.mV_() != other.mV_()) {
                            return null;
                        }
                        lane = other;
                    }
                }
            }
            if (lane == null || !(lane.mV_() instanceof DrA clip) || !clip.sSO()) { // sSO: it can be edited
                return null;
            }
            double start = clip.dC_();
            double end = clip.GwM();
            Clip found = PianoRoll.clip(clip);
            if (found == null || !Double.isFinite(start) || !Double.isFinite(end) || !(end > start)) {
                return null;
            }
            return new Open(found, start, end);
        } catch (RuntimeException | LinkageError e) {
            MOD.log("the Detail Editor's clip can't be read", e);
            return null;
        }
    }
}
