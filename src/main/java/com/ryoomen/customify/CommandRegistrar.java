package com.ryoomen.customify;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

final class CommandRegistrar {

    private final Plugin plugin;
    private final Logger logger;
    private final CustomifyCommand command;

    private CommandMap commandMap;
    private Listener fallback;

    CommandRegistrar(Plugin plugin, CustomifyCommand command) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.command = command;
    }

    void register() {
        try {
            commandMap = findCommandMap();
            removePrevious();

            if (!commandMap.register(CustomifyCommand.NAME, command)) {
                logger.warning("[Customify] Another command already uses /customify, use /customify:customify instead");
            }

            if (commandMap.getCommand(CustomifyCommand.NAME + ":" + CustomifyCommand.NAME) != command) {
                throw new IllegalStateException("the command did not appear in the command map");
            }

            syncCommands();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            commandMap = null;
            logger.warning("[Customify] Could not register the command (" + e + "), "
                    + "chat, console and rcon commands are handled through events instead");

            try {
                fallback = new EventFallback();
                Bukkit.getPluginManager().registerEvents(fallback, plugin);
            } catch (RuntimeException | LinkageError failure) {
                fallback = null;
                logger.severe("[Customify] Could not register the command events either (" + failure + "), "
                        + "the placeholders still work but there is no command");
            }
        }
    }

    void unregister() {
        if (fallback != null) {
            HandlerList.unregisterAll(fallback);
            fallback = null;
        }

        if (commandMap == null) {
            return;
        }

        try {
            command.unregister(commandMap);
            knownCommands().values().removeIf(known -> known == command);
            syncCommands();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warning("[Customify] Could not unregister the command (" + e + ", command map "
                    + commandMap.getClass().getName() + "), it is replaced on the next load or stays until the server restarts");
        }

        commandMap = null;
    }

    boolean handle(CommandSender sender, String line) {
        String[] parts = line.trim().split("\\s+");
        String label = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];
        String name = label.toLowerCase(Locale.ROOT);

        if (!name.equals(CustomifyCommand.NAME) && !name.equals(CustomifyCommand.NAME + ":" + CustomifyCommand.NAME)) {
            return false;
        }

        command.run(sender, label, Arrays.copyOfRange(parts, 1, parts.length));

        return true;
    }

    private void removePrevious() {
        Command existing = commandMap.getCommand(CustomifyCommand.NAME);

        if (existing == null || existing == command || !existing.getClass().getName().equals(command.getClass().getName())) {
            return;
        }

        try {
            existing.unregister(commandMap);
            knownCommands().values().removeIf(known -> known == existing);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warning("[Customify] Could not remove the command of the previous load (" + e + "), it may keep answering until the server restarts");
        }
    }

    private CommandMap findCommandMap() throws ReflectiveOperationException {
        Object server = Bukkit.getServer();
        CommandMap found = callGetter(Bukkit.class, null);

        if (found == null) {
            found = callGetter(server.getClass(), server);
        }

        if (found == null) {
            Object manager = Bukkit.getPluginManager();
            Field field = manager.getClass().getDeclaredField("commandMap");

            field.setAccessible(true);
            found = (CommandMap) field.get(manager);
        }

        if (found == null) {
            throw new IllegalStateException("no command map found");
        }

        return found;
    }

    private CommandMap callGetter(Class<?> type, Object target) {
        try {
            Object result = type.getMethod("getCommandMap").invoke(target);

            return result instanceof CommandMap ? (CommandMap) result : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Map<?, ?> knownCommands() throws ReflectiveOperationException {
        try {
            return (Map<?, ?>) commandMap.getClass().getMethod("getKnownCommands").invoke(commandMap);
        } catch (NoSuchMethodException ignored) {
        }

        for (Class<?> type = commandMap.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("knownCommands");

                field.setAccessible(true);

                return (Map<?, ?>) field.get(commandMap);
            } catch (NoSuchFieldException ignored) {
            }
        }

        throw new NoSuchFieldException("knownCommands");
    }

    private void syncCommands() {
        try {
            if (Bukkit.isPrimaryThread()) {
                sync();
            } else {
                Bukkit.getScheduler().runTask(plugin, this::sync);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void sync() {
        try {
            Object server = Bukkit.getServer();

            server.getClass().getMethod("syncCommands").invoke(server);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    final class EventFallback implements Listener {

        @EventHandler(ignoreCancelled = true)
        public void onPlayer(PlayerCommandPreprocessEvent event) {
            if (handle(event.getPlayer(), event.getMessage())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onConsole(ServerCommandEvent event) {
            if (handle(event.getSender(), event.getCommand())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onRcon(RemoteServerCommandEvent event) {
            if (handle(event.getSender(), event.getCommand())) {
                event.setCancelled(true);
            }
        }
    }
}
