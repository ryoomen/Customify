package com.ryoomen.customify;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

final class Customify {

    private static final String FILE_NAME = "placeholders.yml";
    private static final String SECTION = "placeholders";
    private static final int MAX_DEPTH = 5;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]+");

    private final Path directory;
    private final Path file;
    private final Logger logger;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[1]);

    private volatile Map<String, String> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    Customify(Path directory, Logger logger) {
        this.directory = directory;
        this.file = directory.resolve(FILE_NAME);
        this.logger = logger;
    }

    Map<String, String> getEntries() {
        return entries;
    }

    String resolve(OfflinePlayer player, String id, BiFunction<OfflinePlayer, String, String> parser) {
        String text = entries.get(id);

        if (text == null || !text.contains("%")) {
            return text;
        }

        int[] level = depth.get();

        if (level[0] >= MAX_DEPTH) {
            if (reported.add(id)) {
                logger.warning("[Customify] Placeholder '" + id + "' is nested too deeply, check it for a loop");
            }

            return null;
        }

        level[0]++;

        try {
            return parser.apply(player, text);
        } finally {
            level[0]--;
        }
    }

    synchronized boolean reload() {
        List<String> problems = new ArrayList<>();
        Map<String, String> loaded = null;

        try {
            createFile();
            loaded = parse(read(), problems);
        } catch (IOException e) {
            problems.add("Could not read " + FILE_NAME + ": " + e.getMessage());
        }

        for (String problem : problems) {
            logger.warning("[Customify] " + problem);
        }

        if (loaded == null) {
            logger.severe("[Customify] Nothing was changed because " + FILE_NAME + " has errors");
            return false;
        }

        entries = loaded;
        reported.clear();

        return true;
    }

    String set(String id, String value) {
        if (!ID.matcher(id).matches()) {
            return "Invalid id '" + id + "', use only letters, digits, '_', '-' and '.'";
        }

        if (value.trim().isEmpty()) {
            return "The value must not be empty";
        }

        return edit(id, value);
    }

    String remove(String id) {
        return edit(id, null);
    }

    private synchronized String edit(String id, String value) {
        try {
            createFile();

            String text = read();
            String eol = text.contains("\r\n") ? "\r\n" : "\n";
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
            int index = find(lines, id);

            if (value == null) {
                if (index < 0) {
                    return "Placeholder '" + id + "' does not exist";
                }

                lines.subList(index, blockEnd(lines, index)).clear();
            } else if (index >= 0) {
                if (blockEnd(lines, index) > index + 1) {
                    return "Placeholder '" + id + "' is a list, edit " + FILE_NAME + " directly";
                }

                String line = lines.get(index);

                lines.set(index, line.substring(0, line.indexOf(':') + 1) + " " + quote(value));
            } else {
                int last = lines.size();

                while (last > 0 && lines.get(last - 1).trim().isEmpty()) {
                    last--;
                }

                if (!text.contains("\n" + SECTION + ":") && !text.startsWith(SECTION + ":")) {
                    lines.add(last++, SECTION + ":");
                }

                lines.add(last, "  " + id + ": " + quote(value));
            }

            String result = String.join(eol, lines);
            List<String> problems = new ArrayList<>();
            Map<String, String> parsed = parse(result, problems);

            if (parsed == null || parsed.containsKey(id) == (value == null)) {
                String detail = (value != null && parsed == null)
                        ? "Placeholder '" + id + "' needs a text or a list of texts as its value"
                        : (problems.isEmpty() ? "" : problems.get(0));
                return "The change would break " + FILE_NAME + " and was not saved"
                        + (detail.isEmpty() ? "" : ": " + detail);
            }

            Files.copy(file, directory.resolve(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.write(file, result.getBytes(StandardCharsets.UTF_8));

            reload();

            return null;
        } catch (IOException e) {
            return "Could not edit " + FILE_NAME + ": " + e.getMessage();
        }
    }

    private int find(List<String> lines, String id) {
        String start = id.toLowerCase(Locale.ROOT) + ":";

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(" ") && lines.get(i).trim().toLowerCase(Locale.ROOT).startsWith(start)) {
                return i;
            }
        }

        return -1;
    }

    private int blockEnd(List<String> lines, int index) {
        int indent = indentOf(lines.get(index));
        int end = index + 1;

        while (end < lines.size() && !lines.get(end).trim().isEmpty() && indentOf(lines.get(end)) > indent) {
            end++;
        }

        return end;
    }

    private int indentOf(String line) {
        int indent = 0;

        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }

        return indent;
    }

    private String quote(String value) {
        return value.charAt(0) == 33 ? value : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Map<String, String> parse(String text, List<String> problems) {
        Object data;

        try {
            Yaml yaml = createYaml();
            Reader in = new StringReader(text);

            data = yaml.load(in);
        } catch (RuntimeException e) {
            problems.add("Invalid YAML: " + e.getMessage());
            return null;
        }

        if (!(data instanceof Map) || !((Map<?, ?>) data).containsKey(SECTION)) {
            problems.add(FILE_NAME + " needs a '" + SECTION + "' section with your placeholders");
            return null;
        }

        Object section = ((Map<?, ?>) data).get(SECTION);
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        if (section == null) {
            return result;
        }

        if (!(section instanceof Map)) {
            problems.add("The '" + SECTION + "' section must contain ids and their values");
            return null;
        }

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) section).entrySet()) {
            String id = String.valueOf(entry.getKey());
            String value = textOf(entry.getValue());

            if (!ID.matcher(id).matches()) {
                problems.add("Invalid id '" + id + "', use only letters, digits, '_', '-' and '.'");
            } else if (result.containsKey(id)) {
                problems.add("Id '" + id + "' is used twice, ids are not case-sensitive");
            } else if (value == null) {
                problems.add("Placeholder '" + id + "' needs a text or a list of texts as its value");
            } else {
                result.put(id, ChatColor.translateAlternateColorCodes('&', value));
            }
        }

        return result;
    }

    private String textOf(Object value) {
        if (value instanceof List) {
            List<String> lines = new ArrayList<>();

            for (Object line : (List<?>) value) {
                lines.add(String.valueOf(line));
            }

            return lines.isEmpty() ? null : String.join("\n", lines);
        }

        return value == null || value instanceof Map ? null : String.valueOf(value);
    }

    private Yaml createYaml() {
        LoaderOptions opts = new LoaderOptions();
        try {
            opts.setAllowDuplicateKeys(false);
            opts.setMaxAliasesForCollections(50);
            opts.setNestingDepthLimit(50);
            opts.setCodePointLimit(3 * 1024 * 1024);
            opts.setTagInspector(tag -> opts != null);
            opts.setEnumCaseSensitive(false);
        } catch (LinkageError e) {
        }
        return new Yaml(opts);
    }

    private void createFile() throws IOException {
        Files.createDirectories(directory);

        if (Files.notExists(file)) {
            copyDefaultFile();
        }
    }

    private void copyDefaultFile() throws IOException {
        String name = FILE_NAME;
        InputStream in = Customify.class.getResourceAsStream("/" + name);

        if (in != null) {
            try {
                Files.copy(in, file);
            } finally {
                in.close();
            }

            return;
        }

        try (JarFile jar = new JarFile(new File(Customify.class.getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            JarEntry entry = jar.getJarEntry(name);

            if (entry == null) {
                throw new IOException("The bundled " + FILE_NAME + " is missing from the jar");
            }

            try (InputStream stream = jar.getInputStream(entry)) {
                Files.copy(stream, file);
            }
        } catch (URISyntaxException | RuntimeException e) {
            throw new IOException("Could not find the jar of the expansion: " + e);
        }
    }

    private String read() throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
