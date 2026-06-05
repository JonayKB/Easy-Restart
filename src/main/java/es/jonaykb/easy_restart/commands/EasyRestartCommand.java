package es.jonaykb.easy_restart.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import es.jonaykb.easy_restart.CronRule;
import es.jonaykb.easy_restart.CronScheduler;
import es.jonaykb.easy_restart.EasyRestart;
import es.jonaykb.easy_restart.RestartManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class EasyRestartCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("easyrestart")
                        .requires(cs -> cs.hasPermission(2)) // Admin only
                        .then(Commands.literal("add")
                                .then(Commands.argument("cron", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String cron = StringArgumentType.getString(ctx, "cron");
                                            CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
                                            scheduler.addRule(cron);
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> {
                                            int id = Integer.parseInt(StringArgumentType.getString(ctx, "id"));
                                            CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
                                            boolean removed = scheduler.removeRule(id);
                                            if (!removed) {
                                                ctx.getSource().sendFailure(Component.literal("Invalid ID: " + id));
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cron rules:"), false);
                                    int _idx = 0;
                                    for (CronRule rule : scheduler.getRules()) {
                                        int index = _idx++;
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(index + ": " + rule.getRaw()), false);
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
                                    scheduler.loadRules();
                                    return 1;
                                }))
                        .then(Commands.literal("now")
                                .executes(ctx -> {
                                    RestartManager manager = EasyRestart.RESTART_MANAGER;
                                    manager.beginScheduledRestart(ctx.getSource().getServer(), null);
                                    EasyRestart.LOGGER.info("Immediate restart executed");
                                    return 1;
                                }))
                        .then(Commands.literal("help")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "EasyRestart Commands:\n" +
                                                    "/easyrestart add <cron> - Add a cron rule\n" +
                                                    "/easyrestart remove <id> - Remove a cron rule by ID\n" +
                                                    "/easyrestart list - List all cron rules\n" +
                                                    "/easyrestart reload - Reload cron rules from config\n" +
                                                    "/easyrestart now - Initiate an immediate restart\n" +
                                                    "/easyrestart help - Show this help message"),
                                            false);
                                    return 1;
                                })));

    }
}
