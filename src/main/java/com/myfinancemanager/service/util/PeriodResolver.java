package com.myfinancemanager.service.util;

import com.myfinancemanager.common.exception.BadRequestException;

import java.time.LocalDate;

public final class PeriodResolver {

    private PeriodResolver() {
    }

    public static PeriodRange resolve(String period, LocalDate from, LocalDate to) {
        if (period != null && !period.isBlank()) {
            LocalDate today = LocalDate.now();
            return switch (period.toLowerCase()) {
                case "this_month", "current_month" -> new PeriodRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
                case "last_month" -> {
                    LocalDate firstOfLast = today.withDayOfMonth(1).minusMonths(1);
                    yield new PeriodRange(firstOfLast, firstOfLast.withDayOfMonth(firstOfLast.lengthOfMonth()));
                }
                case "this_quarter" -> {
                    int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                    LocalDate start = LocalDate.of(today.getYear(), firstMonth, 1);
                    yield new PeriodRange(start, start.plusMonths(3).minusDays(1));
                }
                case "this_year", "current_year" -> new PeriodRange(
                        LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
                case "last_year" -> new PeriodRange(
                        LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31));
                case "last_30_days" -> new PeriodRange(today.minusDays(29), today);
                case "last_90_days" -> new PeriodRange(today.minusDays(89), today);
                case "current_week" -> new PeriodRange(today.minusDays(today.getDayOfWeek().getValue() - 1L), today);
                case "all" -> new PeriodRange(LocalDate.of(1970, 1, 1), today);
                case "custom" -> custom(from, to);
                default -> throw new BadRequestException("Unsupported period: " + period);
            };
        }
        if (from != null || to != null) {
            return custom(from, to);
        }
        LocalDate today = LocalDate.now();
        return new PeriodRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
    }

    private static PeriodRange custom(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Both 'from' and 'to' are required when period=custom");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' date must be on or before 'to' date");
        }
        return new PeriodRange(from, to);
    }

    public record PeriodRange(LocalDate from, LocalDate to) {
    }
}
