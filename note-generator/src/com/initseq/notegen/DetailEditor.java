package ch.coldforge.notegen;

import bitwig.obf.CNg; // a boolean value that can be set
import bitwig.obf.DrA; // a note clip
import bitwig.obf.NdS; // the Drum/Hybrid editor mode
import bitwig.obf.aJI; // the Detail Editor's mode button (Drum/Hybrid), in the strip beside the piano keys
import bitwig.obf.aNi; // the Detail Editor panel
import bitwig.obf.QCB; // a time selection's range, as Bitwig keeps it
import bitwig.obf.bjt; // a time selection in an editor (what its right-click menu is opened on)
import bitwig.obf.tJQ; // a time selection's range: its two ends
import bitwig.obf.bke; // the Detail Editor's controller
import bitwig.obf.bpF; // a clip's note editor layer
import bitwig.obf.cjx; // a fixed widget size
import bitwig.obf.dBC; // an icon toggle button
import bitwig.obf.dXL; // a row or column of widgets
import bitwig.obf.dwF; // a widget
import bitwig.obf.zfc; // icon sizing
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.WRA; // builds the Detail Editor's mode strip
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
    // The time selection can be found in three places; the first that holds one inside the clip is used.
    private static volatile WeakReference<tJQ> clicked = new WeakReference<>(null); // what the right-click menu was opened on
    private static volatile WeakReference<tJQ> made = new WeakReference<>(null); // the one whose ends were last set
    private static volatile String clickedOn = "nothing";

    private DetailEditor() {
    }

    /**
     * A note clip and its time range, in the clip's beats; and the editor's time selection, when it has one
     * (selected).
     */
    record Open(Clip clip, double start, double end, boolean selected, double selectionStart, double selectionEnd,
                String found) {
    }

    /** Bitwig sets up a Detail Editor panel: remembered, the newest one wins. */
    @Hook(target = aNi.class, method = "Gef(LHSH;Lbke;LbaN;)V", at = At.ENTRY, id = "detail-editor-set-up")
    public static void setUp(@This aNi built) {
        panel = new WeakReference<>(built);
    }

    /** A time selection's start is set: it is the one last made. */
    @Hook(target = QCB.class, method = "unz(D)V", at = At.ENTRY, id = "time-selection-start")
    public static void timeStart(@This QCB range) {
        made = new WeakReference<>(range);
    }

    /** A time selection's end is set. */
    @Hook(target = QCB.class, method = "Anf(D)V", at = At.ENTRY, id = "time-selection-end")
    public static void timeEnd(@This QCB range) {
        made = new WeakReference<>(range);
    }

    /** The selection a right-click menu was opened on: when it is a time selection, it is the one to use. */
    static void menuOpenedOn(Object bitwigSelection) {
        try {
            Object model = bitwigSelection instanceof bjt selection ? selection.BBg() : null; // BBg: what it selects
            clicked = new WeakReference<>(model instanceof tJQ range ? range : null);
            clickedOn = bitwigSelection == null ? "nothing"
                    : bitwigSelection.getClass().getName() + "/" + (model != null ? model.getClass().getName() : "none");
        } catch (RuntimeException | LinkageError e) {
            MOD.log("the time selection can't be read from the menu", e);
        }
    }

    /** A time selection's two ends, in order; null when it is empty or can't be read. */
    private static double[] ends(tJQ range) {
        try {
            if (range == null) {
                return null;
            }
            double from = Math.min(range.scr(), range.ZRB());
            double to = Math.max(range.scr(), range.ZRB());
            return Double.isFinite(from) && Double.isFinite(to) && to - from > 1e-6 ? new double[] {from, to} : null;
        } catch (RuntimeException | LinkageError | AssertionError e) {
            return null;
        }
    }

    private static String text(double[] ends) {
        return ends == null ? "none" : ends[0] + ".." + ends[1];
    }

    /**
     * Bitwig built a button of the mode strip: beside the Drum/Hybrid button goes the mod's own, which opens the
     * Generate Notes dialog. Other mods add theirs the same way (Scale Fold, Step Sequencer), so it goes into the column
     * they made when there is one.
     */
    @Hook(target = WRA.class, method = "beI(Lbke;LJtD;)LdwF;", at = At.WRAP_RETURN, id = "mode-strip-button")
    public static dwF addButton(@Return dwF widget) {
        try {
            // The mode button itself, or the column another mod put it in.
            dXL outer = widget instanceof dXL column && column.KkD() >= 1 && column.n_(0) instanceof aJI ? column : null;
            if (!((outer != null ? outer.n_(0) : widget) instanceof aJI modeButton)
                    || !(modeButton.UIe().tWW() instanceof NdS) || modeButton.KkD() != 1) { // tWW: the mode it stands for
                return widget;
            }
            if (modeButton.Anf != null) { // its Detail Editor panel
                panel = new WeakReference<>(modeButton.Anf);
            }
            dwF inner = modeButton.n_(0);
            CNg pressed = new CNg(false);
            dBC button = dBC.E2Q();
            dBC.beI(button.vce(), pressed); // its on/off value: always off again, it is a push button
            button.beI(modeButton.jl_().iOJ().beI("Borderless")); // the strip's button style from the theme
            button.Bqk(0.5f);
            button.byf("8th note.svg"); // one of Bitwig's icons
            button.zzi("Generate Notes…");
            button.sCA(zfc.beI());
            button.sCA(() -> PianoRoll.later(() -> { // once Bitwig has finished handling the click
                pressed.setBoolValue(false);
                GeneratorDialog.show(button);
            }));
            button.Gef(new cjx(22L));

            if (inner instanceof dXL column) { // another mod's column inside the mode button
                column.IvP(button);
                return widget;
            }
            if (outer != null) { // another mod's column around the mode button
                outer.IvP(button);
                return widget;
            }
            if (!(inner instanceof dBC)) {
                return widget;
            }
            dXL column = dXL.unz(1); // a new column: the mode button, then ours
            column.ymo(0.0f);
            inner.Gef(new cjx(22L));
            column.IvP(button);
            column.BBg(modeButton, 0);
            return column;
        } catch (RuntimeException | LinkageError e) {
            MOD.log("the Generate Notes button could not be added", e);
            return widget;
        }
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
            Clip clipOf = PianoRoll.clip(clip);
            if (clipOf == null || !Double.isFinite(start) || !Double.isFinite(end) || !(end > start)) {
                return null;
            }
            double[] fromMenu = ends(clicked.get());
            double[] lastMade = ends(made.get());
            double[] ofEditor = null;
            try {
                bjt own = editor.ckt(); // the editor's own time selection
                ofEditor = own != null && own.BBg() instanceof tJQ range ? ends(range) : null;
            } catch (RuntimeException | LinkageError e) {
                // an editor without one
            }
            double[] chosen = null;
            for (double[] candidate : new double[][] {fromMenu, lastMade, ofEditor}) {
                if (candidate != null && Math.min(end, candidate[1]) - Math.max(start, candidate[0]) > 1e-6) {
                    chosen = candidate; // the first that lies in the clip
                    break;
                }
            }
            String found = "menu " + text(fromMenu) + " (" + clickedOn + "), last made " + text(lastMade) + ", editor "
                    + text(ofEditor) + ", time-selection mode " + editor.unz();
            return new Open(clipOf, start, end, chosen != null, chosen != null ? chosen[0] : 0,
                    chosen != null ? chosen[1] : 0, found);
        } catch (RuntimeException | LinkageError e) {
            MOD.log("the Detail Editor's clip can't be read", e);
            return null;
        }
    }
}
