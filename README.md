# EasyRestart

**Automated server restarts with cron scheduling, countdown warnings, and optional auto-relaunch.**

EasyRestart is a server-side Forge mod that lets you schedule Minecraft server restarts using standard cron expressions. Players receive configurable warnings before each restart, a final countdown with on-screen titles, and the server can automatically relaunch itself via a startup script you define.

---

## Features

- **Cron-based scheduling** — define any number of restart times using standard 5-field cron syntax (`minute hour day month weekday`)
- **Pre-warning broadcasts** — configurable list of minutes before restart at which players are notified (default: 10, 5, 1 minute)
- **Final countdown** — 10-second countdown with chat messages and configurable on-screen titles
- **Sound alerts** — plays a pling sound on each warning and countdown tick (can be disabled)
- **Auto-relaunch** — optionally point the mod to a startup script; it will be launched as a detached process before the JVM shuts down, so the server comes back up automatically
- **Forced shutdown watchdog** — if the server hangs during shutdown, a background watchdog forcefully terminates it after 120 seconds
- **Rule validation** — invalid or impossible cron rules (e.g. `0 25 * * *`) are rejected with a clear message and never crash the server
- **In-game commands** — add, remove, list, and reload cron rules without restarting; trigger an immediate restart with `/easyrestart now` or skip the next one with `/easyrestart cancel`
- **Fully configurable messages** — all broadcast strings support placeholders and Minecraft color codes

---

## Installation

1. Download the mod `.jar` and drop it into your server's `mods/` folder.
2. Start the server once to generate the config file at `config/easy_restart-common.toml`.
3. Edit the config to set your desired cron rules, messages, and (optionally) the restart script path.
4. Restart the server — the scheduler will activate immediately.

> **Server-side only.** Players do not need to install this mod.

---

## Configuration

Config file: `config/easy_restart-common.toml`

### General

| Key | Default | Description |
|-----|---------|-------------|
| `enableSounds` | `true` | Play sound alerts on warnings and countdown ticks |
| `enableTitles` | `true` | Show on-screen title during the final 10-second countdown |
| `preWarningMinutes` | `[10, 5, 1]` | Minutes before restart at which broadcast warnings are sent |
| `restartScriptPath` | `""` | Path to the script that starts the server. Accepts absolute or relative paths. Leave empty to disable auto-relaunch |

### Cron Rules

| Key | Default | Description |
|-----|---------|-------------|
| `rules` | `["0 3 * * *"]` | List of cron expressions. Multiple rules are supported |

### Messages

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `preWarning` | `{minutes}` | Broadcast when a pre-warning threshold is reached |
| `finalCountdown` | `{seconds}` | Broadcast each second during the final 10-second countdown |
| `restartNow` | — | Broadcast when `/easyrestart now` is used, before its 10-second countdown |
| `scheduledStart` | — | Currently unused |
| `cancelled` | — | Broadcast when `/easyrestart cancel` stops a restart players had already been warned about |

All messages support Minecraft color codes (`§a`, `§c`, etc.).

---

## Cron Syntax

EasyRestart uses standard 5-field POSIX cron expressions:

```
┌─ minute   (0–59)
│ ┌─ hour   (0–23)
│ │ ┌─ day of month (1–31)
│ │ │ ┌─ month (1–12)
│ │ │ │ ┌─ day of week (0=Sun … 6=Sat)
│ │ │ │ │
* * * * *
```

Supported syntax: `*`, lists (`1,4`), ranges (`1-5`) and steps (`*/6`, `5/15`). Day of week accepts `0`–`7` (both `0` and `7` are Sunday). When both day of month and day of week are restricted, a day matches if **either** one matches, as in standard cron. Times use the server's local time zone.

**Examples:**

| Expression | Meaning |
|-----------|---------|
| `0 3 * * *` | Every day at 03:00 |
| `0 */6 * * *` | Every 6 hours (00:00, 06:00, 12:00, 18:00) |
| `30 8 * * 1` | Every Monday at 08:30 |
| `0 4 * * 1,4` | Every Monday and Thursday at 04:00 |
| `0 2,14 * * *` | Every day at 02:00 and 14:00 |

---

## Auto-Relaunch Setup

To make the server restart automatically after shutdown:

### Linux / macOS

1. Create a startup script (e.g. `start.sh`) in your server root:

```bash
#!/bin/bash
cd "$(dirname "$0")"
java -Xmx4G -Xms1G -jar server.jar nogui
```

2. Make it executable:

```bash
chmod +x start.sh
```

3. Set the config option:

```toml
restartScriptPath = "start.sh"
# or absolute: restartScriptPath = "/home/mc/server/start.sh"
```

### Windows

1. Create a `start.bat` in your server root:

```bat
@echo off
cd /d "%~dp0"
java -Xmx4G -Xms1G -jar server.jar nogui
```

2. Set the config option:

```toml
restartScriptPath = "start.bat"
# or absolute: restartScriptPath = "C:/servers/minecraft/start.bat"
```

> **Note:** The script is launched as a detached process once the server has fully stopped (world saved, port and world lock released), so the new instance can start cleanly. Its output is discarded, so the new server has no attached console; if you need one, have the script start the server inside a new `screen`/`tmux` session.
>
> If the server runs as a **systemd** service, leave `restartScriptPath` empty and use `Restart=always` in the unit instead: systemd kills child processes when the service's main process exits.

---

## Commands

All commands require operator permission level 2.

| Command | Description |
|---------|-------------|
| `/easyrestart add <cron>` | Add a new cron rule (no quotes needed, e.g. `/easyrestart add 0 */6 * * *`) |
| `/easyrestart remove <id>` | Remove a cron rule by its list index |
| `/easyrestart list` | List all active cron rules with their IDs and the next restart |
| `/easyrestart reload` | Reload rules from the config file |
| `/easyrestart now` | Restart after a 10-second countdown |
| `/easyrestart cancel` | Cancel the pending restart. A cron restart is skipped and the rule's following run is scheduled; a `now` restart is dropped. Players get the `cancelled` message if they had already been warned |
| `/easyrestart help` | Show command usage |

---

## Compatibility

- **Minecraft:** 1.20.1
- **Mod loader:** Forge
- **Side:** Server only — clients without the mod can join, and the server list doesn't show it as incompatible

---

## Development

```bash
./gradlew test    # unit tests for the cron parser and the restart timing
./gradlew build   # builds build/libs/easy_restart-<version>.jar (runs the tests too)
```

---

## License

MIT License — free to use, modify, and distribute.