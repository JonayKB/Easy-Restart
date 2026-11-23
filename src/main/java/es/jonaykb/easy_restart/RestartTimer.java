package es.jonaykb.easy_restart;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Restart timing without any Minecraft dependency: tracks the pending restart and
 * decides, on each tick, whether a warning, a countdown step or the restart is due.
 * The caller passes the current time, so it is fully testable.
 */
public class RestartTimer {

    /** Length of the final per-second countdown. */
    public static final int COUNTDOWN_SECONDS = 10;

    public enum Kind { NONE, WARNING, COUNTDOWN, EXECUTE }

    /** What the caller must do on this tick. {@code value} is minutes for WARNING, seconds for COUNTDOWN. */
    public record Action(Kind kind, long value) {
        static final Action NONE = new Action(Kind.NONE, 0);
        static final Action EXECUTE = new Action(Kind.EXECUTE, 0);
    }

    private LocalDateTime restartTime = null;
    /** True when the pending restart came from /easyrestart now, so cron changes don't replace it. */
    private boolean manual = false;
    private final Set<Integer> warned = new HashSet<>();
    private long lastCountdownSecond = -1;
    /** True once players have been told about the pending restart. */
    private boolean announced = false;

    /** Sets the next cron restart. Ignored while a manual restart is pending. Returns true if it changed. */
    public boolean scheduleCron(LocalDateTime time) {
        if (manual || time.equals(restartTime)) {
            return false;
        }
        restartTime = time;
        reset();
        return true;
    }

    /** Starts a manual restart: only the final countdown, no minute warnings. */
    public void startManual(LocalDateTime now, Collection<? extends Integer> warningMinutes) {
        restartTime = now.plusSeconds(COUNTDOWN_SECONDS);
        manual = true;
        reset();
        warned.addAll(warningMinutes);
        announced = true;
    }

    /** Drops a pending cron restart; a manual one is kept. */
    public void clearCron() {
        if (!manual) {
            restartTime = null;
            reset();
        }
    }

    /** Cancels whatever restart is pending (cron or manual). */
    public void cancel() {
        restartTime = null;
        manual = false;
        reset();
    }

    private void reset() {
        warned.clear();
        lastCountdownSecond = -1;
        announced = false;
    }

    public Action tick(LocalDateTime now, Collection<? extends Integer> warningMinutes) {
        if (restartTime == null) {
            return Action.NONE;
        }

        long millisLeft = Duration.between(now, restartTime).toMillis();
        if (millisLeft <= 0) {
            restartTime = null;
            manual = false;
            return Action.EXECUTE;
        }
        long secondsLeft = (millisLeft + 999) / 1000;

        if (secondsLeft <= COUNTDOWN_SECONDS) {
            if (secondsLeft == lastCountdownSecond) {
                return Action.NONE;
            }
            lastCountdownSecond = secondsLeft;
            announced = true;
            return new Action(Kind.COUNTDOWN, secondsLeft);
        }

        // If several thresholds are crossed at once (the restart was scheduled late),
        // send one warning with the real time left.
        boolean crossed = false;
        for (int min : warningMinutes) {
            if (secondsLeft <= min * 60L && warned.add(min)) {
                crossed = true;
            }
        }
        if (!crossed) {
            return Action.NONE;
        }
        announced = true;
        return new Action(Kind.WARNING, Math.max(1, (secondsLeft + 59) / 60));
    }

    public LocalDateTime getRestartTime() {
        return restartTime;
    }

    public boolean isManual() {
        return manual;
    }

    public boolean isAnnounced() {
        return announced;
    }
}
