package com.myfinancemanager.service.util;

import com.myfinancemanager.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodResolverTest {

    @Test
    void resolvesThisMonth() {
        PeriodResolver.PeriodRange range = PeriodResolver.resolve("this_month", null, null);
        LocalDate today = LocalDate.now();

        assertThat(range.from()).isEqualTo(today.withDayOfMonth(1));
        assertThat(range.to()).isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
    }

    @Test
    void respectsCustomRange() {
        PeriodResolver.PeriodRange range = PeriodResolver.resolve(
                "custom", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> PeriodResolver.resolve(
                "custom", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownPeriod() {
        assertThatThrownBy(() -> PeriodResolver.resolve("whenever", null, null))
                .isInstanceOf(BadRequestException.class);
    }
}
