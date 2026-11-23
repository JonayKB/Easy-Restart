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
        restartManager.setOnWarnOnlyDone(this::scheduleNextRestart);
        loadRules();
    }

    /** Reloads the rules from the config. Invalid rules are skipped and logged. */
    public void loadRules() {
        rules.clear();
        loadRules(ModConfig.SERVER.cronRules.get(), false);
        loadRules(ModConfig.SERVER.warnOnlyRules.get(), true);
        scheduleNextRestart();
    }

    private void loadRules(List<? extends String> raws, boolean warnOnly) {
        String type = warnOnly ? "warn-only" : "restart";
        for (String raw : raws) {
            try {
                rules.add(new CronRule(raw, warnOnly));
                LOGGER.info("EasyRestart: Cron rule loaded ({}): {}", type, raw);
            } catch (IllegalArgumentException e) {
                LOGGER.error("EasyRestart: Skipping invalid {} cron rule '{}': {}", type, raw, e.getMessage());
            }
        }
    }

    /** Schedules the earliest upcoming run among all rules, or cancels it if there is none. */
    public void scheduleNextRestart() {
        LocalDateTime now = LocalDateTime.now();
        skippedRestarts.removeIf(t -> !t.isAfter(now));
        CronRule.Run next = CronRule.nextRun(rules, now, skippedRestarts);

        nextScheduledRestart = next == null ? null : next.time();
        if (next == null) {
            restartManager.cancelScheduledRestart();
            LOGGER.info("EasyRestart: No restart scheduled.");
        } else {
            restartManager.beginScheduledRestart(server, next.time(), next.warnOnly());
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
        List<String> restartRules = new ArrayList<>();
        List<String> warnRules = new ArrayList<>();
        for (CronRule rule : rules) {
            (rule.isWarnOnly() ? warnRules : restartRules).add(rule.getRaw());
        }
        ModConfig.SERVER.cronRules.set(restartRules);
        ModConfig.SERVER.warnOnlyRules.set(warnRules);
        ModConfig.SERVER_SPEC.save();
    }

    /**
     * Adds and saves a rule.
     *
     * @throws IllegalArgumentException if the rule is invalid; nothing is saved.
     */
    public CronRule addRule(String raw) {
        return addRule(raw, false);
    }

    /**
     * Adds and saves a rule, restart or warn-only.
     *
     * @throws IllegalArgumentException if the rule is invalid; nothing is saved.
     */
    public CronRule addRule(String raw, boolean warnOnly) {
        CronRule rule = new CronRule(raw, warnOnly);
        // Keep restart rules first so IDs match the order of the config file.
        if (warnOnly) {
            rules.add(rule);
        } else {
            int firstWarn = 0;
            while (firstWarn < rules.size() && !rules.get(firstWarn).isWarnOnly()) {
                firstWarn++;
            }
            rules.add(firstWarn, rule);
        }
        LOGGER.info("EasyRestart: Cron rule added ({}): {}", warnOnly ? "warn-only" : "restart", rule.getRaw());
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
