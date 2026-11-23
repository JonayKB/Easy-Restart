package es.jonaykb.easy_restart;

/** What a cron rule does when its time comes. Declaration order = order in /easyrestart list. */
public enum RuleType {
    /** Warn, stop the server and relaunch it with the restart script (if set). */
    RESTART("restart", "§c[restart]", 1),
    /** Warn and stop the server, without relaunching it. */
    STOP("stop", "§6[stop]", 2),
    /** Warn and save the world, but keep the server running (something else restarts it). */
    WARN_ONLY("warning-only", "§e[warning only]", 0);

    private final String label;
    private final String tag;
    /** When several rules fall on the same minute, the highest priority wins. */
    private final int priority;

    RuleType(String label, String tag, int priority) {
        this.label = label;
        this.tag = tag;
        this.priority = priority;
    }

    public String label() {
        return label;
    }

    /** Colored tag for chat. */
    public String tag() {
        return tag;
    }

    public boolean outranks(RuleType other) {
        return priority > other.priority;
    }
}
