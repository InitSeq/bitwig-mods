package ch.coldforge.notegen;

import bitwig.obf.dvv; // Bitwig's modal dialog
import bitwig.obf.dwF; // a widget
import bml.inspector.BlockContext;
import bml.inspector.Context;
import bml.inspector.Rows;

/**
 * The Generate Notes dialog: every setting as a dropdown, and Generate, which writes other notes each time it is
 * clicked. The dialog stays open, so a playing clip can be auditioned variation by variation; Close ends it.
 *
 * Its rows are Framework: Inspector's own row builders, which also work outside the Inspector as long as they need
 * no panel (dropdowns, text and buttons do not; number fields do).
 */
final class GeneratorDialog extends dvv {
    private static GeneratorDialog shown;

    private final BlockContext rows = new BlockContext(Context.NOTE, null);

    private GeneratorDialog() {
        XhI(); // set the dialog up
        neH("Generate Notes"); // its title
        Ml2(true); // show Cancel, which closes it
        Object body = Rows.column(
                Rows.text(rows, "Where", Generator::rangeText),
                Rows.choice(rows, "Range", Generator.RANGES, () -> Generator.range, value -> Generator.range = value,
                        Generator::hasSelection).row(),
                Rows.choice(rows, "Mode", Generator.MODES, () -> Generator.mode, value -> Generator.mode = value).row(),
                Rows.choice(rows, "Form", Generator.FORM_LABELS, () -> Generator.form, value -> Generator.form = value).row(),
                Rows.text(rows, "Parts", Generator::partsText),
                Rows.choice(rows, "Register", Generator.REGISTERS, () -> Generator.register[Generator.mode],
                        value -> Generator.register[Generator.mode] = value).row(),
                // Chords.
                Rows.choice(rows, "Chord every", Generator.CHORD_LENGTHS, () -> Generator.chordLength,
                        value -> Generator.chordLength = value, GeneratorDialog::chords).row(),
                Rows.choice(rows, "Chords", Generator.CHORD_TYPES, () -> Generator.chordType,
                        value -> Generator.chordType = value, GeneratorDialog::chords).row(),
                Rows.choice(rows, "Rhythm", Generator.RHYTHMS, () -> Generator.rhythm, value -> Generator.rhythm = value,
                        GeneratorDialog::chords).row(),
                Rows.choice(rows, "Bass", Generator.OFF_ON, () -> Generator.bass, value -> Generator.bass = value,
                        GeneratorDialog::chords).row(),
                // Melody.
                Rows.choice(rows, "Rate", Generator.RATES, () -> Generator.rate, value -> Generator.rate = value,
                        GeneratorDialog::melody).row(),
                Rows.choice(rows, "Density", Generator.DENSITIES, () -> Generator.density, value -> Generator.density = value,
                        GeneratorDialog::melody).row(),
                Rows.choice(rows, "Glide", Generator.GLIDES, () -> Generator.glide, value -> Generator.glide = value,
                        GeneratorDialog::melody).row(),
                Rows.choice(rows, "Existing", Generator.EXISTING, () -> Generator.existing, value -> Generator.existing = value).row(),
                Rows.button(rows, "Generate", this::generate));
        BBg((dwF) body); // the dialog's content
        H_(); // finish it before it is shown
    }

    private static boolean chords() {
        return Generator.mode == Generator.CHORDS;
    }

    private static boolean melody() {
        return Generator.mode == Generator.MELODY;
    }

    private void generate() {
        Generator.generate();
        Rows.refresh(rows);
    }

    /** Opens the dialog over this widget's window. One at a time. */
    static void show(Object parent) {
        try {
            if (!(parent instanceof dwF widget)) {
                Generator.MOD.log("no window to open the dialog in");
                return;
            }
            if (shown != null) {
                try {
                    shown.ke_(); // close the one before
                } catch (RuntimeException ignored) {
                    // it was closed already
                }
            }
            shown = new GeneratorDialog();
            shown.MgQ(widget); // show it over the widget's window
        } catch (RuntimeException | LinkageError e) {
            Generator.MOD.log("the Generate Notes dialog could not be opened", e);
            bml.notifications.Notifications.toast(Generator.LABEL + ": the dialog could not be opened, see Bitwig's log");
        }
    }
}
