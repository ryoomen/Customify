package com.ryoomen.customify;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;

import org.bukkit.OfflinePlayer;

public final class CustomifyExpansion extends PlaceholderExpansion implements Taskable {

    private static final String VERSION = "0.1.0";

    private volatile Customify customify;
    private CommandRegistrar registrar;

    @Override
    public String getIdentifier() {
        return "customify";
    }

    @Override
    public String getAuthor() {
        return "Ryoomen";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Customify current = customify;

        return current == null ? null : current.resolve(player, params, PlaceholderAPI::setPlaceholders);
    }

    @Override
    public List<String> getPlaceholders() {
        List<String> placeholders = new ArrayList<>();
        Customify current = customify;

        if (current != null) {
            for (String id : current.getEntries().keySet()) {
                placeholders.add("%customify_" + id + "%");
            }
        }

        return placeholders;
    }

    @Override
    public void start() {
        Logger logger = getPlaceholderAPI().getLogger();
        Customify created = new Customify(getPlaceholderAPI().getDataFolder().toPath().resolve("Customify"), logger);

        created.reload();
        logger.info("[Customify] Loaded " + created.getEntries().size() + " placeholders");

        customify = created;
        registrar = new CommandRegistrar(getPlaceholderAPI(), new CustomifyCommand(created, VERSION));
        registrar.register();
    }

    @Override
    public void stop() {
        if (registrar != null) {
            registrar.unregister();
            registrar = null;
        }

        customify = null;
    }
}
