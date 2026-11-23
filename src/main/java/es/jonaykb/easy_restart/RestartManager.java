package es.jonaykb.easy_restart;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static es.jonaykb.easy_restart.EasyRestart.LOGGER;

public class RestartManager {

    /** Length of the final per-second countdown. */
    public static final int COUNTDOWN_SECONDS = 10;
    /** Seconds the server gets to shut down before the JVM is killed. */
    private static final int FORCED_SHUTDOWN_SECONDS = 120;

    private LocalDateTime restartTime = null;
    /** True when the pending restart came from /easyrestart now, so cron changes don't replace it. */
    private boolean manual = false;
    private final Set<Integer> warned = new HashSet<>();
    private long lastCountdownSecond = -1;
    private boolean stopping = false;

    private final AtomicBoolean scriptPending = new AtomicBoolean(false);

    /** Schedules a cron restart. Ignored while a manual restart is pending. */
    public void beginScheduledRestart(MinecraftServer server, LocalDateTime scheduledTime) {
        if (manual || stopping) {
            return;
        }
        if (scheduledTime.equals(restartTime)) {
            return;
        }
        restartTime = scheduledTime;
        resetWarnings();
        LOGGER.info("EasyRestart: Next restart scheduled at {}", scheduledTime);
    }

    /** Starts the final countdown right away. */
    public void beginManualRestart(MinecraftServer server) {
        if (stopping) {
            return;
        }
        restartTime = LocalDateTime.now().plusSeconds(COUNTDOWN_SECONDS);
        manual = true;
        resetWarnings();
        // Skip the minute warnings: only the countdown is shown.
        warned.addAll(ModConfig.SERVER.preWarningMinutes.get());
        broadcast(server, ModConfig.SERVER.msgRestartNow.get());
        playSound(server);
        LOGGER.info("EasyRestart: Manual restart in {} seconds", COUNTDOWN_SECONDS);
    }

    /** Drops a pending cron restart (a manual one is kept). */
    public void cancelScheduledRestart() {
        if (manual) {
            return;
        }
        restartTime = null;
        resetWarnings();
    }

    private void resetWarnings() {
        warned.clear();
        lastCountdownSecond = -1;
    }

    public void tick(MinecraftServer server) {
        if (stopping || restartTime == null) {
            return;
        }

        long millisLeft = Duration.between(LocalDateTime.now(), restartTime).toMillis();
        if (millisLeft <= 0) {
            executeRestart(server);
            return;
        }
        long secondsLeft = (millisLeft + 999) / 1000;

        // Minute warnings. If several thresholds are crossed at once (the restart was
        // scheduled late), send one message with the real time left.
        if (secondsLeft > COUNTDOWN_SECONDS) {
            boolean crossed = false;
            for (int min : ModConfig.SERVER.preWarningMinutes.get()) {
                if (secondsLeft <= min * 60L && warned.add(min)) {
                    crossed = true;
                }
            }
            if (crossed) {
                long minutesLeft = Math.max(1, (secondsLeft + 59) / 60);
                broadcastPreWarning(server, minutesLeft);
                playSound(server);
                LOGGER.info("EasyRestart: Server restart in {} minute(s).", minutesLeft);
            }
        }

        // Final countdown, once per second.
        if (secondsLeft <= COUNTDOWN_SECONDS && secondsLeft != lastCountdownSecond) {
            lastCountdownSecond = secondsLeft;
            broadcastFinalCountdown(server, secondsLeft);
            playSound(server);
            LOGGER.info("EasyRestart: Server restart in {} seconds.", secondsLeft);
        }
    }

    private void executeRestart(MinecraftServer server) {
        LOGGER.info("EasyRestart: Executing restart");
        stopping = true;
        restartTime = null;

        // The script is launched once the server has fully stopped (see onServerStopped),
        // so the new instance doesn't find the port and the world lock still taken.
        scriptPending.set(true);
        startForcedShutdownWatchdog();

        server.halt(false);
    }

    /** Called from ServerStoppedEvent: port and world are free, start the new server. */
    public void onServerStopped() {
        launchRestartScriptIfPending();
    }

    private void launchRestartScriptIfPending() {
        if (scriptPending.compareAndSet(true, false)) {
            launchRestartScript();
        }
    }

    /**
     * Launches the restart script as a separate process with its output discarded,
     * so it keeps running after this JVM exits. Relative paths are resolved from the
     * server directory. On Windows it goes through {@code cmd /c} so .bat/.cmd work.
     */
    private void launchRestartScript() {
        String scriptPath = ModConfig.SERVER.restartScriptPath.get();

        if (scriptPath == null || scriptPath.isBlank()) {
            LOGGER.info("EasyRestart: No restart script configured — server will stop without restarting.");
            return;
        }

        File scriptFile = new File(scriptPath);
        if (!scriptFile.isAbsolute()) {
            scriptFile = new File(System.getProperty("user.dir"), scriptPath);
        }

        if (!scriptFile.exists()) {
            LOGGER.error("EasyRestart: Restart script not found: {}", scriptFile.getAbsolutePath());
            return;
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows && !scriptFile.canExecute() && !scriptFile.setExecutable(true)) {
            LOGGER.warn("EasyRestart: Could not set executable permission on {} — the script may fail to start.",
                    scriptFile.getAbsolutePath());
        }

        try {
            List<String> command = new ArrayList<>();
            if (isWindows) {
                command.add("cmd");
                command.add("/c");
            }
            command.add(scriptFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(scriptFile.getAbsoluteFile().getParentFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = pb.start();
            LOGGER.info("EasyRestart: Restart script launched (PID {}): {}", process.pid(),
                    scriptFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("EasyRestart: Failed to launch restart script: " + scriptFile.getAbsolutePath(), e);
        }
    }

    /**
     * Kills the JVM if it is still alive after {@link #FORCED_SHUTDOWN_SECONDS}
     * (hung shutdown or a mod thread keeping it open). Daemon thread, so a normal
     * exit just ends it.
     */
    private void startForcedShutdownWatchdog() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(FORCED_SHUTDOWN_SECONDS * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            LOGGER.error("EasyRestart: Server still running {}s after the restart began. Forcing exit.",
                    FORCED_SHUTDOWN_SECONDS);
            launchRestartScriptIfPending();
            Runtime.getRuntime().halt(0);
        }, "EasyRestart-ForcedShutdownWatchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void broadcast(MinecraftServer server, String msg) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    private void broadcastPreWarning(MinecraftServer server, long minutes) {
        broadcast(server, ModConfig.SERVER.msgPreWarning.get().replace("{minutes}", String.valueOf(minutes)));
    }

    private void broadcastFinalCountdown(MinecraftServer server, long seconds) {
        broadcast(server, ModConfig.SERVER.msgFinalCountdown.get().replace("{seconds}", String.valueOf(seconds)));
        showTitle(server, "§c" + seconds);
    }

    private void playSound(MinecraftServer server) {
        if (!ModConfig.SERVER.enableSounds.get()) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), p.getSoundSource(), 1f, 1f);
        }
    }

    private void showTitle(MinecraftServer server, String msg) {
        if (!ModConfig.SERVER.enableTitles.get()) {
            return;
        }
        ClientboundSetTitleTextPacket packet = new ClientboundSetTitleTextPacket(Component.literal(msg));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    public boolean isRestartScheduled() {
        return restartTime != null;
    }

    public boolean isManualRestart() {
        return manual;
    }

    public LocalDateTime getRestartTime() {
        return restartTime;
    }
}
