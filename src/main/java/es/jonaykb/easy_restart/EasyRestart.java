package es.jonaykb.easy_restart;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.jonaykb.easy_restart.commands.EasyRestartCommand;

@Mod(EasyRestart.MOD_ID)
public class EasyRestart {

    public static final String MOD_ID = "easy_restart";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static CronScheduler CRON_SCHEDULER;
    public static RestartManager RESTART_MANAGER;

    @SuppressWarnings("removal")
    public EasyRestart() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SERVER_SPEC);
        // Server-only mod: clients without it must not see the server as incompatible.
        context.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        LOGGER.info("EasyRestart initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RESTART_MANAGER = new RestartManager();
        CRON_SCHEDULER = new CronScheduler(RESTART_MANAGER, event.getServer());
        LOGGER.info("EasyRestart: Scheduler and RestartManager initialized");
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (RESTART_MANAGER != null) {
            RESTART_MANAGER.onServerStopped();
        }
        RESTART_MANAGER = null;
        CRON_SCHEDULER = null;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EasyRestartCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (RESTART_MANAGER != null) {
            RESTART_MANAGER.tick(event.getServer());
        }
    }
}
