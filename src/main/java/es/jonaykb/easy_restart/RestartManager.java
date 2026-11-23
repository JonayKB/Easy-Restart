package es.jonaykb.easy_restart;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static es.jonaykb.easy_restart.EasyRestart.LOGGER;

/** Turns {@link RestartTimer} decisions into messages, sounds and the actual shutdown. */
public class RestartManager {

    public static final int COUNTDOWN_SECONDS = RestartTimer.COUNTDOWN_SECONDS;
    /** Seconds the server gets to shut down before the JVM is killed. */
    private static final int FORCED_SHUTDOWN_SECONDS = 120;

    private final RestartTimer timer = new RestartTimer();
    private boolean stopping = false;
    private final AtomicBoolean scriptPending = new AtomicBoolean(false);

    /** Schedules a cron restart. Ignored while a manual restart is pending. */
    public void beginScheduledRestart(MinecraftServer server, LocalDateTime scheduledTime) {
        if (!stopping && timer.scheduleCron(scheduledTime)) {
            LOGGER.info("EasyRestart: Next restart scheduled at {}", scheduledTime);
        }
    }

    /** Starts the final countdown right away. */
    public void beginManualRestart(MinecraftServer server) {
        if (stopping) {
            return;
        }
        timer.startManual(LocalDateTime.now(), ModConfig.SERVER.preWarningMinutes.get());
        broadcast(server, ModConfig.SERVER.msgRestartNow.get());
        playSound(server);
        LOGGER.info("EasyRestart: Manual restart in {} seconds", COUNTDOWN_SECONDS);
    }

    /** Drops a pending cron restart (a manual one is kept). */
    public void cancelScheduledRestart() {
        timer.clearCron();
    }

    /**
     * Cancels the pending restart, cron or manual, and tells players if they had
     * already been warned. Returns false if it's too late (the server is stopping).
     */
    public boolean cancelRestart(MinecraftServer server) {
        if (stopping) {
            return false;
        }
        boolean announced = timer.isAnnounced();
        timer.cancel();
        if (announced) {
            broadcast(server, ModConfig.SERVER.msgCancelled.get());
            clearTitle(server);
        }
        LOGGER.info("EasyRestart: Pending restart cancelled");
        return true;
    }

    public void tick(MinecraftServer server) {
        if (stopping) {
            return;
        }
        RestartTimer.Action action = timer.tick(LocalDateTime.now(), ModConfig.SERVER.preWarningMinutes.get());
        switch (action.kind()) {
            case WARNING -> {
                broadcast(server, ModConfig.SERVER.msgPreWarning.get()
                        .replace("{minutes}", String.valueOf(action.value())));
                playSound(server);
                LOGGER.info("EasyRestart: Server restart in {} minute(s).", action.value());
            }
            case COUNTDOWN -> {
                broadcast(server, ModConfig.SERVER.msgFinalCountdown.get()
                        .replace("{seconds}", String.valueOf(action.value())));
                showTitle(server, "§c" + action.value());
                playSound(server);
                LOGGER.info("EasyRestart: Server restart in {} seconds.", action.value());
            }
            case EXECUTE -> executeRestart(server);
            case NONE -> {
            }
        }
    }

    private void executeRestart(MinecraftServer server) {
        LOGGER.info("EasyRestart: Executing restart");
        stopping = true;

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

    private void clearTitle(MinecraftServer server) {
        ClientboundClearTitlesPacket packet = new ClientboundClearTitlesPacket(true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    public boolean isRestartScheduled() {
        return timer.getRestartTime() != null;
    }

    public boolean isManualRestart() {
        return timer.isManual();
    }

    public LocalDateTime getRestartTime() {
        return timer.getRestartTime();
    }
}
