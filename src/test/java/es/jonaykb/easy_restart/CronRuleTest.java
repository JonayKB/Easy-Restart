package es.jonaykb.easy_restart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CronRuleTest {

    /** Monday 2026-11-23 14:37:20. */
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 23, 14, 37, 20);

    private static LocalDateTime next(String rule, LocalDateTime from) {
        return new CronRule(rule).getNextExecutionFrom(from);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'0 3 * * *',      2026-11-24T03:00",
            "'0 */6 * * *',    2026-11-23T18:00",
            "'30 8 * * 1',     2026-11-30T08:30",
            "'0 4 * * 1,4',    2026-11-26T04:00",
            "'0 2,14 * * *',   2026-11-24T02:00",
            "'5/15 * * * *',   2026-11-23T14:50",
            "'0-10/5 15 * * *', 2026-11-23T15:00",
            "'* * * * *',      2026-11-23T14:38",
            "'? * * * ?',      2026-11-23T14:38",
            "'0 0 1 1 *',      2027-01-01T00:00",
            "'0 0 29 2 *',     2028-02-29T00:00",
            "'0 0 * * 0',      2026-11-29T00:00",
            "'0 0 * * 7',      2026-11-29T00:00",
            "'0 0 * 12 *',     2026-12-01T00:00",
    })
    void findsNextRun(String rule, LocalDateTime expected) {
        assertEquals(expected, next(rule, FROM));
    }

    @Test
    void nextRunIsStrictlyAfterFrom() {
        LocalDateTime exact = LocalDateTime.of(2026, 11, 24, 3, 0);
        assertEquals(exact.plusDays(1), next("0 3 * * *", exact));
        assertEquals(exact.plusDays(1), next("0 3 * * *", exact.plusSeconds(30)));
    }

    @Test
    void consecutiveRunsOfSteppedRule() {
        CronRule rule = new CronRule("0 */6 * * *");
        LocalDateTime t = FROM;
        int[] hours = {18, 0, 6, 12, 18};
        for (int hour : hours) {
            t = rule.getNextExecutionFrom(t);
            assertEquals(hour, t.getHour());
            assertEquals(0, t.getMinute());
        }
    }

    @Test
    void dayOfMonthAndDayOfWeekUseOrWhenBothSet() {
        CronRule rule = new CronRule("0 0 13 * 5"); // the 13th OR any Friday
        assertEquals(LocalDateTime.of(2026, 11, 27, 0, 0), rule.getNextExecutionFrom(FROM));
        assertTrue(rule.matches(LocalDateTime.of(2026, 12, 13, 0, 0))); // a Sunday, but the 13th
        assertFalse(rule.matches(LocalDateTime.of(2026, 12, 14, 0, 0))); // Monday the 14th
    }

    @Test
    void dayOfWeekOnlyWhenDayOfMonthIsWildcard() {
        CronRule rule = new CronRule("0 0 * * 5");
        assertFalse(rule.matches(LocalDateTime.of(2026, 12, 13, 0, 0)));
        assertTrue(rule.matches(LocalDateTime.of(2026, 12, 11, 0, 0)));
    }

    @Test
    void trimsAndKeepsRawRule() {
        assertEquals("0 3 * * *", new CronRule("  0  3 * *   * ").getRaw().replaceAll("\\s+", " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "* * * *",
            "* * * * * *",
            "60 * * * *",
            "0 24 * * *",
            "0 25 * * *",
            "0 0 0 * *",
            "0 0 32 * *",
            "0 0 * 13 *",
            "0 0 * 0 *",
            "0 0 * * 8",
            "abc * * * *",
            "1,,2 * * * *",
            "5-1 * * * *",
            "1-2-3 * * * *",
            "*/0 * * * *",
            "*/x * * * *",
            "-5 * * * *",
            "0 0 31 2 *",
            "0 0 30 2 *",
            "0 0 31 4,6,9,11 *",
    })
    void rejectsInvalidOrImpossibleRules(String rule) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new CronRule(rule));
        assertNotNull(e.getMessage());
    }

    @Test
    void errorMessageNamesTheField() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new CronRule("0 25 * * *"));
        assertTrue(e.getMessage().contains("hour"), e.getMessage());
        assertTrue(e.getMessage().contains("0 and 23"), e.getMessage());
    }

    @Test
    void rareRuleIsFoundQuickly() {
        long start = System.nanoTime();
        assertEquals(LocalDateTime.of(2032, 2, 29, 0, 0),
                next("0 0 29 2 *", LocalDateTime.of(2028, 2, 29, 0, 0)));
        assertTrue(System.nanoTime() - start < 500_000_000L, "search took too long");
    }

    @Test
    void earliestNextPicksSoonestRule() {
        List<CronRule> rules = List.of(new CronRule("0 3 * * *"), new CronRule("0 15 * * *"));
        assertEquals(LocalDateTime.of(2026, 11, 23, 15, 0), CronRule.earliestNext(rules, FROM));
    }

    @Test
    void earliestNextFromCancelledRunSkipsIt() {
        // /easyrestart cancel on the 15:00 run: 03:00 tomorrow is next.
        List<CronRule> rules = List.of(new CronRule("0 3 * * *"), new CronRule("0 15 * * *"));
        LocalDateTime cancelled = LocalDateTime.of(2026, 11, 23, 15, 0);
        assertEquals(LocalDateTime.of(2026, 11, 24, 3, 0), CronRule.earliestNext(rules, FROM, Set.of(cancelled)));
    }

    @Test
    void skippedRunsAreSkippedOneByOne() {
        List<CronRule> rules = List.of(new CronRule("0 3 * * *"));
        LocalDateTime tomorrow = LocalDateTime.of(2026, 11, 24, 3, 0);
        LocalDateTime dayAfter = tomorrow.plusDays(1);
        assertEquals(dayAfter, CronRule.earliestNext(rules, FROM, Set.of(tomorrow)));
        assertEquals(dayAfter.plusDays(1), CronRule.earliestNext(rules, FROM, Set.of(tomorrow, dayAfter)));
    }

    @Test
    void skippingOneRunDoesNotHideEarlierRunsOfOtherRules() {
        // Cancelled tomorrow's 03:00, then added a rule for today at 16:00: 16:00 must still run.
        List<CronRule> rules = List.of(new CronRule("0 3 * * *"), new CronRule("0 16 * * *"));
        LocalDateTime skipped = LocalDateTime.of(2026, 11, 24, 3, 0);
        assertEquals(LocalDateTime.of(2026, 11, 23, 16, 0), CronRule.earliestNext(rules, FROM, Set.of(skipped)));
    }

    @Test
    void skippedRunSharedByTwoRulesIsSkippedOnce() {
        List<CronRule> rules = List.of(new CronRule("0 3 * * *"), new CronRule("0 */3 * * *"));
        LocalDateTime skipped = LocalDateTime.of(2026, 11, 23, 15, 0);
        assertEquals(LocalDateTime.of(2026, 11, 23, 18, 0), CronRule.earliestNext(rules, FROM, Set.of(skipped)));
    }

    @Test
    void earliestNextWithNoRulesIsNull() {
        assertNull(CronRule.earliestNext(List.of(), FROM));
    }
}
