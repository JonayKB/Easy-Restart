package es.jonaykb.easy_restart;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.jonaykb.easy_restart.commands.EasyRestartCommand;

@Mod(EasyRestart.MOD_ID)
public class EasyRestart {

    public static final String MOD_ID = "easy_restart";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static CronScheduler CRON_SCHEDULER;
    public static RestartManager RESTART_MANAGER;

    public EasyRestart() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                ModConfig.SERVER_SPEC);
        LOGGER.info("EasyRestart initialized");

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RESTART_MANAGER = new RestartManager();
        CRON_SCHEDULER = new CronScheduler(RESTART_MANAGER, event.getServer());

        LOGGER.info("EasyRestart: Scheduler and RestartManager initialized");

    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EasyRestartCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (RESTART_MANAGER != null) {
            RESTART_MANAGER.tick(event.getServer());
        }
    }
}
