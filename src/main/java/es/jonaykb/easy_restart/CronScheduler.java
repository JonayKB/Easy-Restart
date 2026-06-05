package es.jonaykb.easy_restart;

import net.minecraft.server.MinecraftServer;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public void loadRules() {
        rules.clear();
        for (String raw : ModConfig.SERVER.cronRules.get()) {
            try {
                CronRule rule = new CronRule(raw);
                rules.add(rule);
                LOGGER.info("EasyRestart: Cron rule loaded: " + raw);
            } catch (Exception e) {
                LOGGER.error("EasyRestart: Invalid cron rule: " + raw, e);
            }
        }

        // Encontrar el próximo reinicio de todas las reglas
        scheduleNextRestart();
    }

    private void scheduleNextRestart() {
        if (rules.isEmpty()) {
            nextScheduledRestart = null;
            return;
        }

        // Encontrar la próxima ejecución más temprana de todas las reglas
        LocalDateTime earliest = null;
        for (CronRule rule : rules) {
            LocalDateTime next = rule.getNextExecution();
            if (earliest == null || next.isBefore(earliest)) {
                earliest = next;
            }
        }

        nextScheduledRestart = earliest;
        if (nextScheduledRestart != null) {
            restartManager.beginScheduledRestart(server, nextScheduledRestart);
            LOGGER.info("EasyRestart: Next restart scheduled at: " + nextScheduledRestart);
        }
    }

    public void saveRules() {
        List<String> rawRules = new ArrayList<>();
        for (CronRule rule : rules) {
            rawRules.add(rule.getRaw());
        }
        ModConfig.SERVER.cronRules.set(rawRules);
        ModConfig.SERVER.cronRules.save();
    }

    public void addRule(String raw) {
        try {
            CronRule rule = new CronRule(raw);
            rules.add(rule);
            LOGGER.info("Cron rule added: " + raw);
            saveRules();
            scheduleNextRestart(); 
        } catch (Exception e) {
            LOGGER.error("Invalid cron rule: " + raw, e);
        }
    }

    public boolean removeRule(int index) {
        if (index < 0 || index >= rules.size())
            return false;
        CronRule removed = rules.remove(index);
        LOGGER.info("Cron rule removed: " + removed.getRaw());
        saveRules();
        scheduleNextRestart(); // Recalcular próximo reinicio
        return true;
    }

    public List<CronRule> getRules() {
        return rules;
    }

    public LocalDateTime getNextScheduledRestart() {
        return nextScheduledRestart;
    }
}