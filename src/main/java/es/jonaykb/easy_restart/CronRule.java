package es.jonaykb.easy_restart;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

public class CronRule {
    private final String raw;
    private final String minutePart;
    private final String hourPart;
    private final String dayOfMonthPart;
    private final String monthPart;
    private final String dayOfWeekPart;

    public CronRule(String rule) {
        this.raw = rule;
        String[] parts = rule.trim().split("\\s+");
        
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid cron rule. Expected 5 fields, got " + parts.length + ": " + rule);
        }
        
        this.minutePart = parts[0];
        this.hourPart = parts[1];
        this.dayOfMonthPart = parts[2];
        this.monthPart = parts[3];
        this.dayOfWeekPart = parts[4];
    }

    public String getRaw() {
        return raw;
    }

    public boolean matches(LocalDateTime time) {
        return matchesField(minutePart, time.getMinute(), 0, 59) &&
               matchesField(hourPart, time.getHour(), 0, 23) &&
               matchesField(dayOfMonthPart, time.getDayOfMonth(), 1, 31) &&
               matchesField(monthPart, time.getMonthValue(), 1, 12) &&
               matchesField(dayOfWeekPart, time.getDayOfWeek().getValue() % 7, 0, 6);
    }

    /**
     * Obtiene la próxima ejecución a partir del tiempo actual
     */
    public LocalDateTime getNextExecution() {
        return getNextExecutionFrom(LocalDateTime.now());
    }

    /**
     * Obtiene la próxima ejecución a partir de un tiempo específico
     */
    public LocalDateTime getNextExecutionFrom(LocalDateTime fromTime) {
        // Empezar desde el siguiente minuto
        LocalDateTime current = fromTime.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
        
        // Buscar hasta 100 años en el futuro (evitar bucle infinito)
        int maxYears = 10;
        LocalDateTime limit = current.plusYears(maxYears);
        
        while (current.isBefore(limit)) {
            if (matches(current)) {
                return current;
            }
            current = current.plusMinutes(1);
        }
        
        throw new RuntimeException("No next execution found within " + maxYears + " years for cron: " + raw);
    }

    // Métodos auxiliares para verificar coincidencias
    private boolean matchesField(String field, int value, int min, int max) {
        if (field.equals("*") || field.equals("?")) {
            return true;
        }
        
        // Dividir por comas
        String[] parts = field.split(",");
        for (String part : parts) {
            if (matchesPart(part, value, min, max)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPart(String part, int value, int min, int max) {
        // Manejar pasos (e.g., */5, 0-30/5)
        if (part.contains("/")) {
            String[] stepParts = part.split("/");
            String range = stepParts[0];
            int step = Integer.parseInt(stepParts[1]);
            
            if (step <= 0) return false;
            
            // Si es asterisco, usar el rango completo
            if (range.equals("*")) {
                range = min + "-" + max;
            }
            
            // Parsear rango
            String[] rangeParts = range.split("-");
            int start = min;
            int end = max;
            
            if (rangeParts.length == 2) {
                start = Integer.parseInt(rangeParts[0]);
                end = Integer.parseInt(rangeParts[1]);
            } else if (!range.equals(min + "-" + max)) {
                start = Integer.parseInt(range);
                end = max;
            }
            
            // Verificar si el valor está en el rango y cumple el paso
            if (value < start || value > end) {
                return false;
            }
            
            return (value - start) % step == 0;
        }
        
        // Manejar rangos (e.g., 1-5)
        if (part.contains("-")) {
            String[] rangeParts = part.split("-");
            int start = Integer.parseInt(rangeParts[0]);
            int end = Integer.parseInt(rangeParts[1]);
            return value >= start && value <= end;
        }
        
        // Valor individual
        try {
            return Integer.parseInt(part) == value;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Método para obtener todos los valores válidos de un campo (útil para debugging)
    public Set<Integer> getValidValues(String field, int min, int max) {
        Set<Integer> values = new HashSet<>();
        
        if (field.equals("*") || field.equals("?")) {
            for (int i = min; i <= max; i++) {
                values.add(i);
            }
            return values;
        }
        
        String[] parts = field.split(",");
        for (String part : parts) {
            if (part.contains("/")) {
                String[] stepParts = part.split("/");
                String range = stepParts[0];
                int step = Integer.parseInt(stepParts[1]);
                
                if (range.equals("*")) {
                    range = min + "-" + max;
                }
                
                String[] rangeParts = range.split("-");
                int start = min;
                int end = max;
                
                if (rangeParts.length == 2) {
                    start = Integer.parseInt(rangeParts[0]);
                    end = Integer.parseInt(rangeParts[1]);
                } else if (!range.equals(min + "-" + max)) {
                    start = Integer.parseInt(range);
                    end = max;
                }
                
                for (int i = start; i <= end; i += step) {
                    if (i >= min && i <= max) {
                        values.add(i);
                    }
                }
            } else if (part.contains("-")) {
                String[] rangeParts = part.split("-");
                int start = Integer.parseInt(rangeParts[0]);
                int end = Integer.parseInt(rangeParts[1]);
                for (int i = start; i <= end; i++) {
                    if (i >= min && i <= max) {
                        values.add(i);
                    }
                }
            } else {
                try {
                    int val = Integer.parseInt(part);
                    if (val >= min && val <= max) {
                        values.add(val);
                    }
                } catch (NumberFormatException e) {
                    // Ignorar valores inválidos
                }
            }
        }
        
        return values;
    }

    @Override
    public String toString() {
        return "CronRule{" +
               "raw='" + raw + '\'' +
               ", minutes=" + getValidValues(minutePart, 0, 59) +
               ", hours=" + getValidValues(hourPart, 0, 23) +
               ", daysOfMonth=" + getValidValues(dayOfMonthPart, 1, 31) +
               ", months=" + getValidValues(monthPart, 1, 12) +
               ", daysOfWeek=" + getValidValues(dayOfWeekPart, 0, 6) +
               '}';
    }
}