package warroom.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import warroom.Demo;
import warroom.data.WarData;

import java.util.Locale;

/**
 * Console command (with LazyWizard's Console Commands): shows War Room with a made-up history, or brings
 * the real records back. Only loaded when Console Commands is active.
 */
public class WarRoomDemo implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String a = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
        try {
            if (a.equals("off")) {
                Console.showMessage(Demo.stop()
                        ? "War Room demo off: your own records are back, and tracking goes on."
                        : "The War Room demo is not on.");
                return CommandResult.SUCCESS;
            }
            long seed;
            boolean big = a.equals("big");
            if (a.isEmpty() || a.equals("on") || a.equals("new") || big) {
                seed = System.nanoTime();
            } else {
                try {
                    seed = Long.parseLong(a);
                } catch (NumberFormatException ex) {
                    return CommandResult.BAD_SYNTAX;
                }
            }
            boolean again = Demo.isOn();
            WarData d = Demo.start(seed, big);
            Console.showMessage((again ? "War Room demo: a new made-up history" : "War Room demo on")
                    + (big ? " (long and busy, " + d.retired.size() + " factions with older wars beyond the Archive). " : ". ")
                    + d.active.size() + " wars going on, " + d.archive.size() + " ended, built from the sector's real "
                    + "factions. Open the Intel screen and pick War Room. Your own records are set aside and tracking "
                    + "pauses; nothing of the demo is saved. 'warroom_demo off' (or loading a save) brings them back.");
            return CommandResult.SUCCESS;
        } catch (Exception ex) {
            Console.showException("War Room demo failed", ex);
            return CommandResult.ERROR;
        }
    }
}
