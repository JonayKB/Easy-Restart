package es.jonaykb.easy_restart;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Collection;

/**
 * Standard 5-field cron expression: minute hour day-of-month month day-of-week.
 * Supports '*', '?', lists (a,b), ranges (a-b) and steps (x/n). Day-of-week
 * accepts 0-7 (0 and 7 are Sunday). As in POSIX cron, when both day-of-month and
 * day-of-week are restricted, a day matches if either one matches.
 */
public class CronRule {

    /** How far ahead to look for the next run. 8 years covers any leap day. */
    private static final int SEARCH_YEARS = 8;

    private final String raw;
    /** What happens when the rule fires: restart, stop, or only warn. */
    private final RuleType type;
    private final BitSet minutes;
    private final BitSet hours;
    private final BitSet daysOfMonth;
    private final BitSet months;
    private final BitSet daysOfWeek;
    private final boolean dayOfMonthAny;
    private final boolean dayOfWeekAny;

    /**
     * @throws IllegalArgumentException if the expression is malformed, out of
     *                                  range, or can never run.
     */
    public CronRule(String rule) {
        this(rule, RuleType.RESTART);
    }

    /**
     * @param type what happens when the rule fires.
     * @throws IllegalArgumentException if the expression is malformed, out of
     *                                  range, or can never run.
     */
    public CronRule(String rule, RuleType type) {
        this.type = type;
        this.raw = rule.trim();
        String[] parts = raw.split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("expected 5 fields, got " + parts.length);
        }

        minutes = parseField(parts[0], 0, 59, "minute");
        hours = parseField(parts[1], 0, 23, "hour");
        daysOfMonth = parseField(parts[2], 1, 31, "day of month");
        months = parseField(parts[3], 1, 12, "month");
        daysOfWeek = parseField(parts[4], 0, 7, "day of week");
        if (daysOfWeek.get(7)) {
            daysOfWeek.set(0);
            daysOfWeek.clear(7);
        }
        dayOfMonthAny = isWildcard(parts[2]);
        dayOfWeekAny = isWildcard(parts[4]);

        if (getNextExecutionFrom(LocalDateTime.now()) == null) {
            throw new IllegalArgumentException("this rule never matches a real date");
        }
    }

    public String getRaw() {
        return raw;
    }

    public RuleType getType() {
        return type;
    }

    public boolean isWarnOnly() {
        return type == RuleType.WARN_ONLY;
    }

    /** A scheduled run: when, and what it does. */
    public record Run(LocalDateTime time, RuleType type) {
    }

    public boolean matches(LocalDateTime time) {
        return minutes.get(time.getMinute())
                && hours.get(time.getHour())
                && matchesDate(time.toLocalDate());
    }

    private boolean matchesDate(LocalDate date) {
        if (!months.get(date.getMonthValue())) {
            return false;
        }
        boolean dom = daysOfMonth.get(date.getDayOfMonth());
        boolean dow = daysOfWeek.get(date.getDayOfWeek().getValue() % 7);
        if (dayOfMonthAny || dayOfWeekAny) {
            return dom && dow;
        }
        return dom || dow;
    }

    /** Next run strictly after now, or null if none in the search window. */
    public LocalDateTime getNextExecution() {
        return getNextExecutionFrom(LocalDateTime.now());
    }

    /** Next run strictly after {@code fromTime}, or null if none in the search window. */
    public LocalDateTime getNextExecutionFrom(LocalDateTime fromTime) {
        LocalDateTime current = fromTime.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        LocalDateTime limit = current.plusYears(SEARCH_YEARS);

        while (current.isBefore(limit)) {
            if (!matchesDate(current.toLocalDate())) {
                current = current.toLocalDate().plusDays(1).atStartOfDay();
            } else if (!hours.get(current.getHour())) {
                current = current.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            } else if (!minutes.get(current.getMinute())) {
                current = current.plusMinutes(1);
            } else {
                return current;
            }
        }
        return null;
    }

    /** Earliest next run among {@code rules} strictly after {@code from}, or null if none. */
    public static LocalDateTime earliestNext(Collection<CronRule> rules, LocalDateTime from) {
        LocalDateTime earliest = null;
        for (CronRule rule : rules) {
            LocalDateTime next = rule.getNextExecutionFrom(from);
            if (next != null && (earliest == null || next.isBefore(earliest))) {
                earliest = next;
            }
        }
        return earliest;
    }

    /**
     * Like {@link #earliestNext(Collection, LocalDateTime)} but skipping the given
     * run times (restarts cancelled with /easyrestart cancel).
     */
    public static LocalDateTime earliestNext(Collection<CronRule> rules, LocalDateTime from,
            Collection<LocalDateTime> skipped) {
        LocalDateTime next = earliestNext(rules, from);
        while (next != null && skipped.contains(next)) {
            next = earliestNext(rules, next);
        }
        return next;
    }

    /**
     * Next run among {@code rules} after {@code from}, skipping {@code skipped} times.
     * If rules of different types fall on the same minute: stop beats restart, and
     * both beat warn-only.
     */
    public static Run nextRun(Collection<CronRule> rules, LocalDateTime from, Collection<LocalDateTime> skipped) {
        LocalDateTime time = earliestNext(rules, from, skipped);
        if (time == null) {
            return null;
        }
        RuleType winner = null;
        for (CronRule rule : rules) {
            if (rule.matches(time) && (winner == null || rule.type.outranks(winner))) {
                winner = rule.type;
            }
        }
        return new Run(time, winner);
    }

    private static boolean isWildcard(String field) {
        return field.equals("*") || field.equals("?");
    }

    private static BitSet parseField(String field, int min, int max, String name) {
        BitSet values = new BitSet(max + 1);
        for (String part : field.split(",", -1)) {
            try {
                parsePart(part, min, max, values);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid " + name + " value '" + part + "'");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid " + name + " '" + part + "': " + e.getMessage());
            }
        }
        return values;
    }

    private static void parsePart(String part, int min, int max, BitSet values) {
        if (part.isEmpty()) {
            throw new IllegalArgumentException("empty value");
        }

        String range = part;
        int step = 1;
        int slash = part.indexOf('/');
        if (slash >= 0) {
            range = part.substring(0, slash);
            step = Integer.parseInt(part.substring(slash + 1));
            if (step <= 0) {
                throw new IllegalArgumentException("step must be greater than 0");
            }
        }

        int start;
        int end;
        if (isWildcard(range)) {
            start = min;
            end = max;
        } else if (range.contains("-")) {
            String[] bounds = range.split("-", -1);
            if (bounds.length != 2) {
                throw new IllegalArgumentException("bad range");
            }
            start = Integer.parseInt(bounds[0]);
            end = Integer.parseInt(bounds[1]);
        } else {
            start = Integer.parseInt(range);
            // "5/15" means "from 5 to the end, every 15"
            end = slash >= 0 ? max : start;
        }

        if (start < min || end > max) {
            throw new IllegalArgumentException("must be between " + min + " and " + max);
        }
        if (start > end) {
            throw new IllegalArgumentException("range start is after its end");
        }
        for (int i = start; i <= end; i += step) {
            values.set(i);
        }
    }

    @Override
    public String toString() {
        return "CronRule{" + raw + ", " + type.label() + '}';
    }
}
