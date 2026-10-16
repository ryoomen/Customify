package com.ryoomen.customify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

final class CustomifyCommand extends Command {

    static final String NAME = "customify";

    private static final String PERMISSION = "customify.admin";
    private static final String PREFIX = "&d&lCustomify &8\u00BB &f";
    private static final List<String> SUBCOMMANDS = Arrays.asList("help", "reload", "list", "set", "remove");

    private final Customify customify;
    private final String version;

    CustomifyCommand(Customify customify, String version) {
        super(NAME, "Manage Customify placeholders", "/" + NAME + " help", new ArrayList<String>());
        this.customify = customify;
        this.version = version;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        run(sender, label, args);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> matches = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission(PERMISSION)) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    matches.add(sub);
                }
            }
        }

        return matches;
    }

    void run(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "&cYou do not have permission to use this command.");
            return;
        }

        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) {
            sender.sendMessage(color("&d&lCustomify &7v" + version));
            sender.sendMessage(color("&d/" + label + " reload &8- &7Reload the placeholder list"));
            sender.sendMessage(color("&d/" + label + " list &8- &7List all placeholders"));
            sender.sendMessage(color("&d/" + label + " set <id> <value> &8- &7Create or update a placeholder"));
            sender.sendMessage(color("&d/" + label + " remove <id> &8- &7Remove a placeholder"));
        } else if (sub.equals("reload")) {
            if (customify.reload()) {
                send(sender, "Reloaded &d" + customify.getEntries().size() + " &fplaceholders.");
            } else {
                send(sender, "&cReload failed, the previous placeholders are still active. See the console.");
            }
        } else if (sub.equals("list")) {
            Map<String, String> entries = customify.getEntries();

            if (entries.isEmpty()) {
                send(sender, "There are no placeholders yet. Use &d/" + label + " set <id> <value>&f.");
            } else {
                send(sender, "Placeholders (" + entries.size() + "): &d" + String.join("&f, &d", entries.keySet()));
            }
        } else if (sub.equals("set")) {
            if (args.length < 3) {
                send(sender, "Usage: &d/" + label + " set <id> <value>");
                return;
            }

            String error = customify.set(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            send(sender, error == null ? "Saved placeholder &d" + args[1] + "&f." : "&cCould not save: &7" + error);
        } else if (sub.equals("remove")) {
            if (args.length != 2) {
                send(sender, "Usage: &d/" + label + " remove <id>");
                return;
            }

            String error = customify.remove(args[1]);

            send(sender, error == null ? "Removed placeholder &d" + args[1] + "&f." : "&cCould not remove: &7" + error);
        } else {
            send(sender, "Unknown subcommand &d" + args[0] + "&f. Use &d/" + label + " help&f.");
        }
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(color(PREFIX + message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
