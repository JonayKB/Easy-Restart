package es.jonaykb.easy_restart;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

public class ModConfig {

        public static final ForgeConfigSpec SERVER_SPEC;
        public static final ServerConfig SERVER;

        static {
                ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
                SERVER = new ServerConfig(builder);
                SERVER_SPEC = builder.build();
        }

        public static class ServerConfig {

                public final ForgeConfigSpec.BooleanValue enableSounds;
                public final ForgeConfigSpec.BooleanValue enableTitles;
                public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> preWarningMinutes;
                public final ForgeConfigSpec.ConfigValue<List<? extends String>> cronRules;
                public final ForgeConfigSpec.ConfigValue<List<? extends String>> stopRules;
                public final ForgeConfigSpec.ConfigValue<List<? extends String>> warnOnlyRules;
                public final ForgeConfigSpec.ConfigValue<String> msgPreWarning;
                public final ForgeConfigSpec.ConfigValue<String> msgFinalCountdown;
                public final ForgeConfigSpec.ConfigValue<String> msgRestartNow;
                public final ForgeConfigSpec.ConfigValue<String> msgScheduledStart;
                public final ForgeConfigSpec.ConfigValue<String> msgCancelled;
                public final ForgeConfigSpec.ConfigValue<String> msgStopWarning;
                public final ForgeConfigSpec.ConfigValue<String> msgStopCountdown;
                public final ForgeConfigSpec.ConfigValue<String> msgStopNow;

                public final ForgeConfigSpec.ConfigValue<String> restartScriptPath;
                public final ForgeConfigSpec.IntValue restartExitCode;

                ServerConfig(ForgeConfigSpec.Builder builder) {
                        builder.comment("Config of Auto-Restart Mod").push("general");

                        enableSounds = builder
                                        .comment("Enable or disable warning sounds.")
                                        .define("enableSounds", true);

                        enableTitles = builder
                                        .comment("Enable or disable titles/overlays on screen.")
                                        .define("enableTitles", true);

                        preWarningMinutes = builder
                                        .comment("Minutes before a restart where warnings will be sent.")
                                        .defineList("preWarningMinutes",
                                                        Arrays.asList(10, 5, 1),
                                                        obj -> obj instanceof Integer);

                        restartScriptPath = builder
                                        .comment(
                                                        "Path to the script used to restart the server after shutdown.",
                                                        "Accepts absolute paths (e.g. /home/mc/start.sh) or paths relative to the server root.",
                                                        "Leave empty (\"\") to disable auto-restart — the server will only stop.",
                                                        "On Windows use double backslashes or forward slashes (e.g. scripts/start.bat).",
                                                        "The script is launched detached from the JVM so it survives the server process ending.")
                                        .define("restartScriptPath", "");

                        restartExitCode = builder
                                        .comment(
                                                        "Exit code the server process ends with after a restart rule or /easyrestart now.",
                                                        "For panels that relaunch the server themselves (Crafty, Pterodactyl, AMP...):",
                                                        "leave restartScriptPath empty, set this to e.g. 1 and turn on the panel's crash",
                                                        "detection / auto-restart. Stop rules always end with 0, so if 0 is an ignored",
                                                        "exit code in the panel, stops stay stopped while restarts come back up.",
                                                        "0 = normal exit (default).")
                                        .defineInRange("restartExitCode", 0, 0, 255);

                        builder.pop();

                        builder.comment("Cron-style rules for scheduling restarts.").push("cron");

                        cronRules = builder
                                        .comment("List of cron rules. Example: \"0 3 * * *\" for daily restart at 3:00.")
                                        .defineList("rules",
                                                        Arrays.asList("0 3 * * *"),
                                                        obj -> obj instanceof String);

                        stopRules = builder
                                        .comment("Cron rules that warn players and stop the server WITHOUT relaunching it,",
                                                        "even if restartScriptPath is set. E.g. stop cleanly at 2:58 before the",
                                                        "machine reboots at 3:00 -> \"58 2 * * *\".")
                                        .defineList("stopRules",
                                                        Arrays.asList(),
                                                        obj -> obj instanceof String);

                        warnOnlyRules = builder
                                        .comment("Cron rules that only warn players, with the same warnings and countdown,",
                                                        "but never restart the server. Use them when something else restarts it,",
                                                        "e.g. the machine reboots itself at 3:00 -> \"0 3 * * *\".",
                                                        "The world is saved when the final countdown starts.",
                                                        "If rules of different types match the same minute: stop > restart > warn-only.")
                                        .defineList("warnOnlyRules",
                                                        Arrays.asList(),
                                                        obj -> obj instanceof String);

                        builder.pop();

                        builder.comment("Configurable messages").push("messages");

                        msgPreWarning = builder.define(
                                        "preWarning",
                                        "§eEl servidor se reiniciará en §c{minutes} §eminutos.");

                        msgFinalCountdown = builder.define(
                                        "finalCountdown",
                                        "§6Reinicio en §c{seconds} §6segundos...");

                        msgRestartNow = builder.define(
                                        "restartNow",
                                        "§cEl servidor se reinicia ahora.");

                        msgScheduledStart = builder.define(
                                        "scheduledStart",
                                        "§aReinicio programado para dentro de §e1 minuto.");

                        msgCancelled = builder
                                        .comment("Broadcast when a pending restart is cancelled with /easyrestart cancel,",
                                                        "only if players had already been warned about it.")
                                        .define("cancelled", "§aSe ha cancelado el reinicio del servidor.");

                        msgStopWarning = builder
                                        .comment("Like preWarning, for stop rules. Placeholder: {minutes}")
                                        .define("stopWarning", "§eEl servidor se apagará en §c{minutes} §eminutos.");

                        msgStopCountdown = builder
                                        .comment("Like finalCountdown, for stop rules. Placeholder: {seconds}")
                                        .define("stopCountdown", "§6Apagado en §c{seconds} §6segundos...");

                        msgStopNow = builder
                                        .comment("Broadcast when a stop rule shuts the server down.")
                                        .define("stopNow", "§cEl servidor se apaga ahora.");

                        builder.pop();
                }
        }
}