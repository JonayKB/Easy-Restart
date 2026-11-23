package es.jonaykb.easy_restart;

import net.minecraft.server.MinecraftServer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static es.jonaykb.easy_restart.EasyRestart.LOGGER;

public class CronScheduler {

    private final List<CronRule> rules = new ArrayList<>();
    private final RestartManager restartManager;
    private final MinecraftServer server;
    private LocalDateTime nextScheduledRestart = null;

    public CronScheduler(RestartManager restartManager, MinecraftServer server) {
        this.restartManager = restartManager;
        this.server = server;
        loadRules();
    }

    /** Reloads the rules from the config. Invalid rules are skipped and logged. */
    public void loadRules() {
        rules.clear();
        for (String raw : ModConfig.SERVER.cronRules.get()) {
            try {
                rules.add(new CronRule(raw));
                LOGGER.info("EasyRestart: Cron rule loaded: {}", raw);
            } catch (IllegalArgumentException e) {
                LOGGER.error("EasyRestart: Skipping invalid cron rule '{}': {}", raw, e.getMessage());
            }
        }
        scheduleNextRestart();
    }

    /** Schedules the earliest upcoming run among all rules, or cancels it if there is none. */
    public void scheduleNextRestart() {
        LocalDateTime earliest = null;
        for (CronRule rule : rules) {
            LocalDateTime next = rule.getNextExecution();
            if (next != null && (earliest == null || next.isBefore(earliest))) {
                earliest = next;
            }
        }

        nextScheduledRestart = earliest;
        if (earliest == null) {
            restartManager.cancelScheduledRestart();
            LOGGER.info("EasyRestart: No restart scheduled.");
        } else {
            restartManager.beginScheduledRestart(server, earliest);
        }
    }

    private void saveRules() {
        List<String> rawRules = new ArrayList<>();
        for (CronRule rule : rules) {
            rawRules.add(rule.getRaw());
        }
        ModConfig.SERVER.cronRules.set(rawRules);
        ModConfig.SERVER.cronRules.save();
    }

    /**
     * Adds and saves a rule.
     *
     * @throws IllegalArgumentException if the rule is invalid; nothing is saved.
     */
    public CronRule addRule(String raw) {
        CronRule rule = new CronRule(raw);
        rules.add(rule);
        LOGGER.info("EasyRestart: Cron rule added: {}", rule.getRaw());
        saveRules();
        scheduleNextRestart();
        return rule;
    }

    public CronRule removeRule(int index) {
        if (index < 0 || index >= rules.size()) {
            return null;
        }
        CronRule removed = rules.remove(index);
        LOGGER.info("EasyRestart: Cron rule removed: {}", removed.getRaw());
        saveRules();
        scheduleNextRestart();
        return removed;
    }

    public List<CronRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public LocalDateTime getNextScheduledRestart() {
        return nextScheduledRestart;
    }
}
