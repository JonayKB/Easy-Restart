# EasyRestart

**Automated server restarts with cron scheduling, countdown warnings, and optional auto-relaunch.**

EasyRestart is a server-side Forge mod that lets you schedule Minecraft server restarts using standard cron expressions. Players receive configurable warnings before each restart, a final countdown with on-screen titles, and the server can automatically relaunch itself via a startup script you define.

---

## Features

- **Cron-based scheduling** — define any number of restart times using standard 5-field cron syntax (`minute hour day month weekday`)
- **Stop rules** — warn players and stop the server without relaunching it, even if a restart script is set
- **Warning-only rules** — for times when something else restarts the server (e.g. the machine reboots itself at 03:00): players get the same warnings and countdown, the world is saved, but the server is not restarted. Can be mixed with normal restart rules
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
| `restartExitCode` | `0` | Exit code the process ends with after a restart rule or `/easyrestart now`. For panels (Crafty, Pterodactyl, AMP…), see [Panels](#panels-crafty-pterodactyl-amp). Stop rules and manual `/stop` always exit with `0` |
| `restartScriptPath` | `""` | Path to the script that starts the server. Accepts absolute or relative paths. Leave empty to disable auto-relaunch |

### Cron Rules

| Key | Default | Description |
|-----|---------|-------------|
| `rules` | `["0 3 * * *"]` | Restart rules: warn players, then stop the server (and relaunch it if a script is set) |
| `stopRules` | `[]` | Stop rules: same warnings (with "shutting down" messages), then stop the server **without** relaunching it, even if `restartScriptPath` is set |
| `warnOnlyRules` | `[]` | Warning-only rules: same warnings and countdown, the world is saved when the countdown starts, but the server keeps running |

All three lists can be used at once. If rules of different types fall on the same minute: **stop** beats **restart**, and both beat **warning-only**.

**Example:** the machine reboots on its own every day at 03:00, and you also want a real server restart at 15:00:

```toml
[cron]
    rules = ["0 15 * * *"]
    warnOnlyRules = ["0 3 * * *"]
```

Safer variant: stop the server cleanly two minutes before the machine reboots, so nothing can be lost:

```toml
[cron]
    rules = ["0 15 * * *"]
    stopRules = ["58 2 * * *"]
```

> If the server runs under systemd with `Restart=always` (or a loop in your start script), a stop rule will look like a crash and the server will come back up. Use `Restart=on-failure` so a clean stop stays stopped.

### Messages

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `preWarning` | `{minutes}` | Broadcast when a pre-warning threshold is reached |
| `finalCountdown` | `{seconds}` | Broadcast each second during the final 10-second countdown |
| `restartNow` | — | Broadcast when `/easyrestart now` is used (before its 10-second countdown) and when a warning-only rule reaches its time |
| `scheduledStart` | — | Currently unused |
| `stopWarning` | `{minutes}` | Like `preWarning`, for stop rules |
| `stopCountdown` | `{seconds}` | Like `finalCountdown`, for stop rules |
| `stopNow` | — | Broadcast when a stop rule shuts the server down |
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

## Panels (Crafty, Pterodactyl, AMP…)

Panels only manage the server process they started themselves: a server launched by `restartScriptPath` runs outside the panel, with no console in it, and the panel thinks the server is off. With a panel, let the panel relaunch the server instead:

1. Leave `restartScriptPath = ""`.
2. Set `restartExitCode` to a non-zero code, e.g. `1`.
3. Turn on the panel's crash detection / auto-restart, making sure that code is **not** an ignored exit code. In Crafty: the server's *Crash detection* setting and its *ignored exit codes* list.

After a restart rule or `/easyrestart now`, the server stops cleanly, the world is saved, and the process exits with that code, so the panel sees a "crash" and starts it again with its own console. Stop rules and manual `/stop` exit with `0`: if `0` is an ignored exit code in the panel, they stay stopped.

If another mod also sets the exit code (e.g. to report crashes), Easy-Restart only sets it when it stopped the server itself; real crashes are left to that mod. Use a code for `restartExitCode` that the panel restarts on.

---

## Commands

All commands require operator permission level 2.

| Command | Description |
|---------|-------------|
| `/easyrestart add <cron>` | Add a restart rule (no quotes needed, e.g. `/easyrestart add 0 */6 * * *`) |
| `/easyrestart addstop <cron>` | Add a stop rule (warns, then stops without relaunching) |
| `/easyrestart addwarn <cron>` | Add a warning-only rule (warns and saves, never restarts) |
| `/easyrestart remove <id>` | Remove a cron rule by its list index |
| `/easyrestart list` | List all rules with their IDs and type (`[restart]` / `[stop]` / `[warning only]`) and the next restart |
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