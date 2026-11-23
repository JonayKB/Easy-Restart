package es.jonaykb.easy_restart.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import es.jonaykb.easy_restart.CronRule;
import es.jonaykb.easy_restart.CronScheduler;
import es.jonaykb.easy_restart.EasyRestart;
import es.jonaykb.easy_restart.RestartManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EasyRestartCommand {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("easyrestart")
                        .requires(cs -> cs.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("cron", StringArgumentType.greedyString())
                                        .executes(EasyRestartCommand::add)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                        .executes(EasyRestartCommand::remove)))
                        .then(Commands.literal("list")
                                .executes(EasyRestartCommand::list))
                        .then(Commands.literal("reload")
                                .executes(EasyRestartCommand::reload))
                        .then(Commands.literal("now")
                                .executes(EasyRestartCommand::now))
                        .then(Commands.literal("cancel")
                                .executes(EasyRestartCommand::cancel))
                        .then(Commands.literal("help")
                                .executes(EasyRestartCommand::help)));
    }

    private static int add(CommandContext<CommandSourceStack> ctx) {
        CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
        if (scheduler == null) {
            return notReady(ctx);
        }
        String cron = StringArgumentType.getString(ctx, "cron");
        try {
            CronRule rule = scheduler.addRule(cron);
            ctx.getSource().sendSuccess(() -> Component.literal("§aAdded rule: §f" + rule.getRaw()), true);
            sendNext(ctx);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid cron rule '" + cron + "': " + e.getMessage()));
            return 0;
        }
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
        if (scheduler == null) {
            return notReady(ctx);
        }
        int id = IntegerArgumentType.getInteger(ctx, "id");
        CronRule removed = scheduler.removeRule(id);
        if (removed == null) {
            ctx.getSource().sendFailure(Component.literal("No rule with ID " + id + ". Use /easyrestart list."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aRemoved rule: §f" + removed.getRaw()), true);
        sendNext(ctx);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
        if (scheduler == null) {
            return notReady(ctx);
        }
        List<CronRule> rules = scheduler.getRules();
        if (rules.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No cron rules."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("Cron rules:"), false);
            for (int i = 0; i < rules.size(); i++) {
                String line = i + ": " + rules.get(i).getRaw();
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        sendNext(ctx);
        return rules.size();
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
        if (scheduler == null) {
            return notReady(ctx);
        }
        scheduler.loadRules();
        int count = scheduler.getRules().size();
        ctx.getSource().sendSuccess(() -> Component.literal("§aReloaded " + count + " rule(s). Invalid rules are listed in the server log."), true);
        sendNext(ctx);
        return 1;
    }

    private static int now(CommandContext<CommandSourceStack> ctx) {
        RestartManager manager = EasyRestart.RESTART_MANAGER;
        if (manager == null) {
            return notReady(ctx);
        }
        manager.beginManualRestart(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§aRestarting in "
                + RestartManager.COUNTDOWN_SECONDS + " seconds."), true);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) {
        CronScheduler scheduler = EasyRestart.CRON_SCHEDULER;
        if (scheduler == null) {
            return notReady(ctx);
        }
        LocalDateTime cancelled = scheduler.cancelNextRestart();
        if (cancelled == null) {
            ctx.getSource().sendFailure(Component.literal("There is no pending restart to cancel."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aCancelled the restart at " + cancelled.format(TIME_FORMAT) + "."), true);
        sendNext(ctx);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "EasyRestart Commands:\n" +
                        "/easyrestart add <cron> - Add a cron rule (no quotes needed)\n" +
                        "/easyrestart remove <id> - Remove a cron rule by ID\n" +
                        "/easyrestart list - List all cron rules and the next restart\n" +
                        "/easyrestart reload - Reload cron rules from config\n" +
                        "/easyrestart now - Restart after a " + RestartManager.COUNTDOWN_SECONDS + "-second countdown\n" +
                        "/easyrestart cancel - Cancel the pending restart (cron rules keep running)\n" +
                        "/easyrestart help - Show this help message"),
                false);
        return 1;
    }

    private static void sendNext(CommandContext<CommandSourceStack> ctx) {
        RestartManager manager = EasyRestart.RESTART_MANAGER;
        LocalDateTime next = manager == null ? null : manager.getRestartTime();
        String text = next == null ? "Next restart: none" : "Next restart: " + next.format(TIME_FORMAT);
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static int notReady(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("EasyRestart is not running yet."));
        return 0;
    }
}
