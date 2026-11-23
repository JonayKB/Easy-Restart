package es.jonaykb.easy_restart;

import net.minecraft.server.MinecraftServer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static es.jonaykb.easy_restart.EasyRestart.LOGGER;

public class CronScheduler {

    private final List<CronRule> rules = new ArrayList<>();
    private final RestartManager restartManager;
    private final MinecraftServer server;
    private LocalDateTime nextScheduledRestart = null;
    /** Cron runs cancelled with /easyrestart cancel; only these exact times are skipped. */
    private final Set<LocalDateTime> skippedRestarts = new HashSet<>();

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
        LocalDateTime now = LocalDateTime.now();
        skippedRestarts.removeIf(t -> !t.isAfter(now));
        LocalDateTime earliest = CronRule.earliestNext(rules, now, skippedRestarts);

        nextScheduledRestart = earliest;
        if (earliest == null) {
            restartManager.cancelScheduledRestart();
            LOGGER.info("EasyRestart: No restart scheduled.");
        } else {
            restartManager.beginScheduledRestart(server, earliest);
        }
    }

    /**
     * Cancels the pending restart. A manual one is simply dropped; a cron one is
     * skipped and the following run is scheduled instead.
     *
     * @return the cancelled restart time, or null if nothing was pending or it was too late.
     */
    public LocalDateTime cancelNextRestart() {
        LocalDateTime pending = restartManager.getRestartTime();
        if (pending == null) {
            return null;
        }
        boolean wasManual = restartManager.isManualRestart();
        if (!restartManager.cancelRestart(server)) {
            return null;
        }
        if (!wasManual) {
            skippedRestarts.add(pending);
        }
        scheduleNextRestart();
        return pending;
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
