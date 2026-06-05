package es.jonaykb.easy_restart;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static es.jonaykb.easy_restart.EasyRestart.LOGGER;

public class RestartManager {

    private boolean restartScheduled = false;
    private LocalDateTime restartTime = null;
    private final Set<Integer> warned = new HashSet<>();
    private int tickCount = 0;
    private Thread forcedShutdownWatchdog = null;
    private volatile boolean normalShutdownCompleted = false;

    public void beginScheduledRestart(MinecraftServer server, LocalDateTime scheduledTime) {
        if (restartScheduled && restartTime != null && restartTime.equals(scheduledTime)) {
            LOGGER.info("EasyRestart: Same restart already scheduled at " + scheduledTime);
            return;
        }

        restartScheduled = true;
        restartTime = scheduledTime;
        warned.clear();

        LOGGER.info("EasyRestart: New restart scheduled at " + scheduledTime);
    }

    public void tick(MinecraftServer server) {
        update(server);
    }

    private void update(MinecraftServer server) {
        if (!restartScheduled || restartTime == null)
            return;

        long secondsLeft = java.time.Duration.between(LocalDateTime.now(), restartTime).getSeconds();

        if (secondsLeft < 0) {
            executeRestart(server);
            return;
        }

        // Warnings
        for (int min : ModConfig.SERVER.preWarningMinutes.get()) {
            if (secondsLeft <= min * 60 && !warned.contains(min)) {
                warned.add(min);
                broadcastPreWarning(server, min);
                playSound(server);
                LOGGER.info("Prewarning: Server restart in " + min + " minutes.");
            }
        }

        // Countdown
        if (secondsLeft <= 10 && secondsLeft > 0) {
            if (tickCount % 20 == 0) {
                LOGGER.info("EasyRestart: Server restart in " + secondsLeft + " seconds.");
                broadcastFinalCountdown(server, secondsLeft);
                playSound(server);
            }
            tickCount++;
        }

        // Restart
        if (secondsLeft == 0) {
            executeRestart(server);
        }
    }

    private void executeRestart(MinecraftServer server) {
        LOGGER.info("EasyRestart: Executing scheduled restart");

        // Lanzar el script de arranque ANTES de que el JVM muera
        launchRestartScript();

        // Inicia el watchdog de apagado forzado
        startForcedShutdownWatchdog(server, 120);

        // Guardar mundos
        server.saveAllChunks(true, true, true);

        normalShutdownCompleted = false;

        // Ordenar al servidor que se apague
        server.halt(false);

        // Limpiar estado
        restartScheduled = false;
        restartTime = null;
        warned.clear();
    }

    /**
     * Lanza el script de reinicio del servidor en un proceso independiente.
     *
     * <p>
     * El proceso se lanza desacoplado del JVM actual mediante
     * {@code ProcessBuilder}
     * con {@code inheritIO()} desactivado e I/O redirigido a DISCARD, de modo que
     * sobrevive al apagado del servidor sin quedar colgado esperando streams.
     * </p>
     *
     * <p>
     * Se acepta tanto ruta absoluta como relativa al directorio de trabajo del
     * servidor.
     * En Linux/macOS se llama directamente al script; en Windows se invoca a través
     * de
     * {@code cmd /c} para que los .bat y .cmd funcionen correctamente.
     * </p>
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
            LOGGER.error("EasyRestart: Restart script not found: " + scriptFile.getAbsolutePath());
            return;
        }

        // Asegurar permisos de ejecución en Unix
        if (!scriptFile.canExecute()) {
            boolean marked = scriptFile.setExecutable(true);
            if (!marked) {
                LOGGER.warn("EasyRestart: Could not set executable permission on: " + scriptFile.getAbsolutePath()
                        + " — the script may fail to start.");
            }
        }

        try {
            List<String> command = new ArrayList<>();

            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (isWindows) {
                command.add("cmd");
                command.add("/c");
            }
            command.add(scriptFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(scriptFile.getParentFile()); 
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = pb.start();
            LOGGER.info("EasyRestart: Restart script launched (PID " + process.pid() + "): "
                    + scriptFile.getAbsolutePath());

        } catch (IOException e) {
            LOGGER.error("EasyRestart: Failed to launch restart script: " + scriptFile.getAbsolutePath(), e);
        }
    }

    private void startForcedShutdownWatchdog(MinecraftServer server, int seconds) {
        forcedShutdownWatchdog = new Thread(() -> {
            int remaining = seconds;

            try {
                while (remaining > 0) {
                    if (normalShutdownCompleted) {
                        LOGGER.info("EasyRestart: Normal shutdown completed. Watchdog cancelled.");
                        return;
                    }

                    String msg = "El servidor se está apagando... apagado forzado en "
                            + remaining + " segundos.";
                    broadcast(server, msg);

                    LOGGER.info("[EasyRestart] Forced shutdown in " + remaining + "s");
                    Thread.sleep(1000);

                    remaining--;
                }

                LOGGER.error("EasyRestart: Timeout reached. Forcing immediate shutdown.");
                Runtime.getRuntime().halt(0);

            } catch (InterruptedException ignored) {
                LOGGER.info("EasyRestart: Forced shutdown watchdog interrupted.");
            }
        });

        forcedShutdownWatchdog.setDaemon(true);
        forcedShutdownWatchdog.setName("EasyRestart-ForcedShutdownWatchdog");
        forcedShutdownWatchdog.start();
    }

    private void broadcast(MinecraftServer server, String msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(msg));
        }
    }

    private void broadcastPreWarning(MinecraftServer server, int minutes) {
        String msg = ModConfig.SERVER.msgPreWarning.get()
                .replace("{minutes}", String.valueOf(minutes));
        broadcast(server, msg);
    }

    private void broadcastFinalCountdown(MinecraftServer server, long seconds) {
        String msg = ModConfig.SERVER.msgFinalCountdown.get()
                .replace("{seconds}", String.valueOf(seconds));
        broadcast(server, msg);
        showTitle(server, "§c" + seconds);
    }

    private void playSound(MinecraftServer server) {
        if (!ModConfig.SERVER.enableSounds.get())
            return;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), p.getSoundSource(), 1f, 1f);
        }
    }

    private void showTitle(MinecraftServer server, String msg) {
        if (!ModConfig.SERVER.enableTitles.get())
            return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Component titleComponent = Component.literal(msg);
            ClientboundSetTitleTextPacket packet = new ClientboundSetTitleTextPacket(titleComponent);
            player.connection.send(packet);
        }
    }

    public boolean isRestartScheduled() {
        return restartScheduled;
    }

    public LocalDateTime getRestartTime() {
        return restartTime;
    }
}