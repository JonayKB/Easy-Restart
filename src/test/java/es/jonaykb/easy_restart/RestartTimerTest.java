package es.jonaykb.easy_restart;

import es.jonaykb.easy_restart.RestartTimer.Action;
import es.jonaykb.easy_restart.RestartTimer.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestartTimerTest {

    private static final List<Integer> WARNINGS = List.of(10, 5, 1);
    private static final LocalDateTime RESTART = LocalDateTime.of(2026, 11, 24, 3, 0);

    private RestartTimer timer;

    @BeforeEach
    void setUp() {
        timer = new RestartTimer();
    }

    /** Ticks every 50 ms (one server tick) from {@code from} to {@code to} and collects the non-NONE actions. */
    private List<Action> run(LocalDateTime from, LocalDateTime to) {
        List<Action> actions = new ArrayList<>();
        for (LocalDateTime t = from; !t.isAfter(to); t = t.plusNanos(50_000_000)) {
            Action a = timer.tick(t, WARNINGS);
            if (a.kind() != Kind.NONE) {
                actions.add(a);
            }
        }
        return actions;
    }

    private static Action warning(long minutes) {
        return new Action(Kind.WARNING, minutes);
    }

    private static Action countdown(long seconds) {
        return new Action(Kind.COUNTDOWN, seconds);
    }

    @Test
    void nothingPendingDoesNothing() {
        assertEquals(Kind.NONE, timer.tick(RESTART, WARNINGS).kind());
        assertNull(timer.getRestartTime());
    }

    @Test
    void fullCronSequence() {
        timer.scheduleCron(RESTART);
        List<Action> actions = run(RESTART.minusMinutes(15), RESTART.plusSeconds(1));

        List<Action> expected = new ArrayList<>(List.of(warning(10), warning(5), warning(1)));
        for (int s = 10; s >= 1; s--) {
            expected.add(countdown(s));
        }
        expected.add(Action.EXECUTE);
        assertEquals(expected, actions);
        assertNull(timer.getRestartTime(), "restart is cleared once executed");
    }

    @Test
    void lateScheduleSendsOneWarningWithRealTimeLeft() {
        timer.scheduleCron(RESTART);
        // Scheduled 3 minutes ahead: the 10 and 5 minute thresholds are crossed at once.
        LocalDateTime start = RESTART.minusMinutes(3);
        assertEquals(warning(3), timer.tick(start, WARNINGS));
        assertEquals(List.of(warning(1)), run(start.plusSeconds(1), RESTART.minusSeconds(11)));
    }

    @Test
    void manualRestartOnlyCountsDown() {
        LocalDateTime now = RESTART.minusHours(2);
        timer.startManual(now, WARNINGS);
        assertTrue(timer.isManual());
        assertEquals(now.plusSeconds(RestartTimer.COUNTDOWN_SECONDS), timer.getRestartTime());

        List<Action> actions = run(now, now.plusSeconds(11));
        assertEquals(countdown(10), actions.get(0));
        assertEquals(Action.EXECUTE, actions.get(actions.size() - 1));
        assertEquals(11, actions.size()); // 10..1 + EXECUTE, nothing else
        assertTrue(actions.stream().noneMatch(a -> a.kind() == Kind.WARNING));
    }

    @Test
    void cronChangesDontReplaceManualRestart() {
        LocalDateTime now = RESTART.minusHours(2);
        timer.startManual(now, WARNINGS);
        LocalDateTime manualTime = timer.getRestartTime();

        assertFalse(timer.scheduleCron(RESTART));
        timer.clearCron();
        assertEquals(manualTime, timer.getRestartTime());
    }

    @Test
    void clearCronDropsCronRestart() {
        timer.scheduleCron(RESTART);
        timer.clearCron();
        assertNull(timer.getRestartTime());
        assertEquals(Kind.NONE, timer.tick(RESTART.plusSeconds(5), WARNINGS).kind());
    }

    @Test
    void rescheduleSameTimeKeepsWarningState() {
        timer.scheduleCron(RESTART);
        assertEquals(warning(10), timer.tick(RESTART.minusMinutes(10), WARNINGS));
        assertFalse(timer.scheduleCron(RESTART));
        assertEquals(Kind.NONE, timer.tick(RESTART.minusMinutes(9), WARNINGS).kind());
    }

    @Test
    void cancelStopsCronRestart() {
        timer.scheduleCron(RESTART);
        run(RESTART.minusMinutes(2), RESTART.minusSeconds(5));
        assertTrue(timer.isAnnounced());

        timer.cancel();
        assertNull(timer.getRestartTime());
        assertFalse(timer.isAnnounced());
        assertEquals(List.of(), run(RESTART.minusSeconds(5), RESTART.plusSeconds(5)));
    }

    @Test
    void cancelStopsManualRestartAndAllowsCronAgain() {
        LocalDateTime now = RESTART.minusHours(1);
        timer.startManual(now, WARNINGS);
        timer.cancel();
        assertFalse(timer.isManual());
        assertEquals(List.of(), run(now, now.plusSeconds(15)));

        assertTrue(timer.scheduleCron(RESTART));
        assertEquals(RESTART, timer.getRestartTime());
    }

    @Test
    void announcedOnlyAfterPlayersWereWarned() {
        timer.scheduleCron(RESTART);
        timer.tick(RESTART.minusHours(1), WARNINGS);
        assertFalse(timer.isAnnounced());
        timer.tick(RESTART.minusMinutes(10), WARNINGS);
        assertTrue(timer.isAnnounced());
    }

    @Test
    void newScheduleResetsWarnings() {
        timer.scheduleCron(RESTART);
        assertEquals(warning(10), timer.tick(RESTART.minusMinutes(10), WARNINGS));

        LocalDateTime later = RESTART.plusHours(6);
        assertTrue(timer.scheduleCron(later));
        assertFalse(timer.isAnnounced());
        assertEquals(warning(10), timer.tick(later.minusMinutes(10), WARNINGS));
    }

    @Test
    void warnOnlyRunWarnsAndCountsDownButDoesNotRestart() {
        timer.scheduleCron(RESTART, RuleType.WARN_ONLY);
        assertTrue(timer.isWarnOnly());
        List<Action> actions = run(RESTART.minusMinutes(15), RESTART.plusSeconds(1));

        List<Action> expected = new ArrayList<>(List.of(warning(10), warning(5), warning(1)));
        for (int s = 10; s >= 1; s--) {
            expected.add(countdown(s));
        }
        expected.add(new Action(Kind.WARN_ONLY_DONE, 0));
        assertEquals(expected, actions);
        assertNull(timer.getRestartTime());
        assertFalse(timer.isWarnOnly());
    }

    @Test
    void sameMinuteCanSwitchFromWarnOnlyToRestart() {
        timer.scheduleCron(RESTART, RuleType.WARN_ONLY);
        assertEquals(warning(10), timer.tick(RESTART.minusMinutes(10), WARNINGS));
        assertTrue(timer.scheduleCron(RESTART, RuleType.RESTART));
        assertFalse(timer.isWarnOnly());
        assertTrue(timer.isAnnounced(), "warnings already sent are kept");
        assertEquals(Action.EXECUTE, timer.tick(RESTART, WARNINGS));
    }

    @Test
    void manualRestartReplacesWarnOnlyRun() {
        timer.scheduleCron(RESTART, RuleType.WARN_ONLY);
        LocalDateTime now = RESTART.minusMinutes(30);
        timer.startManual(now, WARNINGS);
        assertFalse(timer.isWarnOnly());
        assertFalse(timer.scheduleCron(RESTART, RuleType.WARN_ONLY));
        assertEquals(Action.EXECUTE, timer.tick(now.plusSeconds(11), WARNINGS));
    }

    @Test
    void cancelClearsWarnOnly() {
        timer.scheduleCron(RESTART, RuleType.WARN_ONLY);
        timer.cancel();
        assertFalse(timer.isWarnOnly());
        assertEquals(Kind.NONE, timer.tick(RESTART.plusSeconds(1), WARNINGS).kind());
    }

    @Test
    void stopRunWarnsAndCountsDownThenStops() {
        timer.scheduleCron(RESTART, RuleType.STOP);
        assertEquals(RuleType.STOP, timer.getType());
        List<Action> actions = run(RESTART.minusMinutes(15), RESTART.plusSeconds(1));

        List<Action> expected = new ArrayList<>(List.of(warning(10), warning(5), warning(1)));
        for (int s = 10; s >= 1; s--) {
            expected.add(countdown(s));
        }
        expected.add(new Action(Kind.STOP, 0));
        assertEquals(expected, actions);
        assertNull(timer.getRestartTime());
        assertEquals(RuleType.RESTART, timer.getType(), "type resets after the run");
    }

    @Test
    void sameMinuteCanSwitchToStop() {
        timer.scheduleCron(RESTART, RuleType.RESTART);
        assertTrue(timer.scheduleCron(RESTART, RuleType.STOP));
        assertFalse(timer.scheduleCron(RESTART, RuleType.STOP));
        assertEquals(new Action(Kind.STOP, 0), timer.tick(RESTART, WARNINGS));
    }

    @Test
    void manualRestartReplacesStopRun() {
        timer.scheduleCron(RESTART, RuleType.STOP);
        LocalDateTime now = RESTART.minusMinutes(30);
        timer.startManual(now, WARNINGS);
        assertEquals(RuleType.RESTART, timer.getType());
        assertEquals(Action.EXECUTE, timer.tick(now.plusSeconds(11), WARNINGS));
    }

    @Test
    void cancelClearsStop() {
        timer.scheduleCron(RESTART, RuleType.STOP);
        timer.cancel();
        assertEquals(RuleType.RESTART, timer.getType());
        assertEquals(Kind.NONE, timer.tick(RESTART.plusSeconds(1), WARNINGS).kind());
    }

    @Test
    void missedRestartExecutesImmediately() {
        timer.scheduleCron(RESTART);
        assertEquals(Action.EXECUTE, timer.tick(RESTART.plusMinutes(1), WARNINGS));
    }
}
