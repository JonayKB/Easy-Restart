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
                public final ForgeConfigSpec.ConfigValue<String> msgPreWarning;
                public final ForgeConfigSpec.ConfigValue<String> msgFinalCountdown;
                public final ForgeConfigSpec.ConfigValue<String> msgRestartNow;
                public final ForgeConfigSpec.ConfigValue<String> msgScheduledStart;

                public final ForgeConfigSpec.ConfigValue<String> restartScriptPath;

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

                        builder.pop();

                        builder.comment("Cron-style rules for scheduling restarts.").push("cron");

                        cronRules = builder
                                        .comment("List of cron rules. Example: \"0 3 * * *\" for daily restart at 3:00.")
                                        .defineList("rules",
                                                        Arrays.asList("0 3 * * *"),
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

                        builder.pop();
                }
        }
}