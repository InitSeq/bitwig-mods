package ch.coldforge.notegen;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

/**
 * Generate Notes as Bitwig actions: in the right-click menu of a time selection or of notes in a clip's note
 * editor, in the Commander, and with a key from Settings > Shortcuts.
 */
public final class GeneratorActions implements ActionProvider {
    /** Bitwig's names for a time selection and for notes in a clip's note editor. */
    private static final String[] KINDS = {
            "arranger_clip_note_time_selection", "launcher_clip_note_time_selection",
            "arranger_clip_note_lane_time_selection", "launcher_clip_note_lane_time_selection",
            "arranger_clip_instrument_note", "launcher_clip_instrument_note",
    };

    @Override
    public void declare(Actions actions) {
        Menu[] menus = new Menu[KINDS.length];
        for (int i = 0; i < KINDS.length; i++) {
            menus[i] = Menu.selection(KINDS[i]);
        }
        actions.category("note_generator", "Note Generator");
        // Stored in users' shortcut settings: never change the IDs.
        actions.action("note_generator_generate", "Generate Notes")
               .menuLabel("Generate Notes…")
               .category("note_generator")
               .in(menus)
               .onRun(context -> {
                   DetailEditor.menuOpenedOn(context.selection());
                   GeneratorDialog.show(context.widget());
               });
        actions.action("note_generator_again", "Generate Notes Again")
               .category("note_generator")
               .in(menus)
               .onRun(context -> {
                   DetailEditor.menuOpenedOn(context.selection());
                   Generator.generate();
               });
    }
}
