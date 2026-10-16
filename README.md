# Customify

A [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) expansion that turns a single YAML file into your own custom placeholders - no code required.

```yaml
placeholders:
  donate: "www.site.com"
```

`%customify_donate%` is replaced with `www.site.com`, anywhere PlaceholderAPI placeholders are accepted.

## What is Customify?

Most servers end up needing a handful of placeholders that have nothing to do with any plugin: a donate link, a Discord invite, a store URL, a rules list. Writing a tiny expansion for each one, or stuffing them into an unrelated config, is overkill.

Customify is that missing expansion. Define an id and a value in `placeholders.yml`, reload, and `%customify_<id>%` is live. Values can reference other placeholders, including PlaceholderAPI's own, so a placeholder can be built out of others.

## Why use it?

- **No code needed.** Add a line to a YAML file instead of writing and maintaining a Java expansion.
- **Nesting.** A value can contain other placeholders, Customify's own or any other plugin's, resolved for the player who asked.
- **Edits don't touch the rest of the file.** `/customify set` and `remove` change only the lines they need; comments, layout and every other entry stay exactly as they were.
- **Works everywhere.** Builds against the Spigot 1.12.2 API and runs unmodified up to the newest Paper, with no bundled libraries.
- **Fails safe.** A broken file keeps the previous placeholders active instead of breaking all of them; a single broken entry is skipped and logged, the rest still loads.

## Features

- Text and multi-line (list) values, with `&` color codes
- Nested placeholders, with a depth limit so a reference loop cannot hang the server
- `/customify` command to reload, list, create, and remove placeholders, with tab completion
- Universal command registration across server forks, with an automatic fallback to chat, console and rcon command events if the server does not expose a command map
- In-place editing: `set`/`remove` patch only the affected lines of `placeholders.yml`, keeping comments and formatting, and write a `.bak` copy before saving
- No bundled dependencies: uses the SnakeYAML that ships with the server

## How it works

1. Drop the jar into `plugins/PlaceholderAPI/expansions/` and reload PlaceholderAPI.
2. Customify creates `plugins/PlaceholderAPI/Customify/placeholders.yml` with a few examples.
3. Add an id and a value, reload, and use it anywhere.

```yaml
placeholders:
  donate: "www.site.com"
  discord: "&bdiscord.gg/example"
  welcome: "&fWelcome to the server, &b%player_name%&f!"
  store: "&b%customify_donate%/store"
  rules:
    - "&bRule 1&f: Be kind to others"
    - "&bRule 2&f: No cheating"
    - "&bRule 3&f: Have fun"
```

```
%customify_donate%     -> www.site.com
%customify_welcome%    -> Welcome to the server, Steve!
%customify_store%      -> www.site.com/store
```

- **Text:** `id: "text"`.
- **Lines:** a list of texts, joined with a line break.
- **Ids** may contain letters, digits, `_`, `-` and `.`, and are not case-sensitive.
- Quote values that could be read as something other than text (`yes`, `20`, `1.5`).

## Commands

| Command | Description |
|---|---|
| `/customify help` | Show the commands |
| `/customify reload` | Reload `placeholders.yml` |
| `/customify list` | List all placeholders |
| `/customify set <id> <value>` | Create or update a text placeholder |
| `/customify remove <id>` | Remove a placeholder |

All commands require `customify.admin` (default: op). `set`/`remove` only create or remove single texts; a list is created and edited in the file, and `set` refuses to change an entry that holds one (`remove` still works on it).

## Requirements

- Spigot, Paper, or a fork of either (built against the 1.12.2 API, newer versions work as well)
- PlaceholderAPI 2.11.x
- Java 8 or newer

## Building

```
mvn clean package
```

No libraries are bundled: Customify reads and writes `placeholders.yml` with the YAML library that already ships with the server.

## Links

- [PlaceholderAPI eCloud](https://api.extendedclip.com/)
- [PlaceholderAPI on SpigotMC](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [PlaceholderAPI Wiki](https://wiki.placeholderapi.com/)

## License

MIT
