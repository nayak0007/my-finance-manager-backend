package com.myfinancemanager.service;

import com.myfinancemanager.domain.ExpenseRecord;
import com.myfinancemanager.domain.IncomeRecord;
import com.myfinancemanager.domain.InvestmentRecord;
import com.myfinancemanager.dto.dashboard.CategoryBreakdownResponse;
import com.myfinancemanager.dto.dashboard.DashboardSummaryResponse;
import com.myfinancemanager.dto.dashboard.MonthlyTrendPoint;
import com.myfinancemanager.dto.dashboard.RecentTransactionResponse;
import com.myfinancemanager.repository.ExpenseRepository;
import com.myfinancemanager.repository.IncomeRepository;
import com.myfinancemanager.repository.InvestmentRepository;
import com.myfinancemanager.repository.projection.CategoryTotal;
import com.myfinancemanager.repository.projection.MonthlyTotal;
import com.myfinancemanager.service.util.PeriodResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final InvestmentRepository investmentRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(UUID userId, String period, LocalDate from, LocalDate to) {
        PeriodResolver.PeriodRange range = PeriodResolver.resolve(period, from, to);
        BigDecimal totalIncome = incomeRepository.sumAmount(userId, range.from(), range.to());
        BigDecimal totalExpenses = expenseRepository.sumAmount(userId, range.from(), range.to());
        BigDecimal totalInvestments = investmentRepository.sumInvestedBetween(userId, range.from(), range.to());

        return new DashboardSummaryResponse(
                range.from(),
                range.to(),
                totalIncome,
                totalExpenses,
                totalInvestments,
                totalIncome.subtract(totalExpenses),
                incomeRepository.countByUserIdAndTransactionDateBetween(userId, range.from(), range.to()),
                expenseRepository.countByUserIdAndTransactionDateBetween(userId, range.from(), range.to()),
                investmentRepository.countByUserIdAndTransactionDateBetween(userId, range.from(), range.to()));
    }

    @Transactional(readOnly = true)
    public List<CategoryBreakdownResponse> expensesByCategory(UUID userId, String period, LocalDate from, LocalDate to) {
        PeriodResolver.PeriodRange range = PeriodResolver.resolve(period, from, to);
        List<CategoryTotal> totals = expenseRepository.sumByCategory(userId, range.from(), range.to());
        BigDecimal grandTotal = totals.stream()
                .map(CategoryTotal::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totals.stream()
                .map(total -> new CategoryBreakdownResponse(
                        total.getCategory() == null ? "uncategorized" : total.getCategory(),
                        total.getTotal(),
                        grandTotal.signum() == 0
                                ? BigDecimal.ZERO
                                : total.getTotal().multiply(BigDecimal.valueOf(100))
                                        .divide(grandTotal, 2, RoundingMode.HALF_UP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryBreakdownResponse> incomeByCategory(UUID userId, String period, LocalDate from, LocalDate to) {
        PeriodResolver.PeriodRange range = PeriodResolver.resolve(period, from, to);
        List<CategoryTotal> totals = incomeRepository.sumByCategory(userId, range.from(), range.to());
        BigDecimal grandTotal = totals.stream()
                .map(CategoryTotal::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totals.stream()
                .map(total -> new CategoryBreakdownResponse(
                        total.getCategory() == null ? "uncategorized" : total.getCategory(),
                        total.getTotal(),
                        grandTotal.signum() == 0
                                ? BigDecimal.ZERO
                                : total.getTotal().multiply(BigDecimal.valueOf(100))
                                        .divide(grandTotal, 2, RoundingMode.HALF_UP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyTrendPoint> monthlyTrend(UUID userId, int months) {
        int safeMonths = Math.max(1, Math.min(months, 60));
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(safeMonths - 1L).withDayOfMonth(1);

        Map<YearMonth, BigDecimal> incomeByMonth = new LinkedHashMap<>();
        for (MonthlyTotal total : incomeRepository.sumByMonth(userId, start, end)) {
            incomeByMonth.put(YearMonth.of(total.getYearValue(), total.getMonthValue()), total.getTotal());
        }
        Map<YearMonth, BigDecimal> expenseByMonth = new LinkedHashMap<>();
        for (MonthlyTotal total : expenseRepository.sumByMonth(userId, start, end)) {
            expenseByMonth.put(YearMonth.of(total.getYearValue(), total.getMonthValue()), total.getTotal());
        }

        List<MonthlyTrendPoint> points = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            BigDecimal income = incomeByMonth.getOrDefault(cursor, BigDecimal.ZERO);
            BigDecimal expense = expenseByMonth.getOrDefault(cursor, BigDecimal.ZERO);
            points.add(new MonthlyTrendPoint(
                    cursor.getYear(),
                    cursor.getMonthValue(),
                    cursor.toString(),
                    income,
                    expense,
                    income.subtract(expense)));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<RecentTransactionResponse> recentTransactions(UUID userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        PageRequest pageRequest = PageRequest.of(0, safeLimit,
                Sort.by(Sort.Direction.DESC, "transactionDate", "createdAt"));

        List<RecentTransactionResponse> merged = new ArrayList<>();
        for (IncomeRecord income : incomeRepository.findByUserId(userId, pageRequest)) {
            merged.add(new RecentTransactionResponse(
                    income.getId(),
                    "INCOME",
                    income.getSource() != null ? income.getSource() : income.getCategory(),
                    income.getCategory(),
                    income.getAmount(),
                    income.getTransactionDate(),
                    income.getOrigin()));
        }
        for (ExpenseRecord expense : expenseRepository.findByUserId(userId, pageRequest)) {
            merged.add(new RecentTransactionResponse(
                    expense.getId(),
                    "EXPENSE",
                    expense.getMerchant() != null ? expense.getMerchant() : expense.getCategory(),
                    expense.getCategory(),
                    expense.getAmount(),
                    expense.getTransactionDate(),
                    expense.getOrigin()));
        }
        for (InvestmentRecord investment : investmentRepository.findByUserId(userId, pageRequest)) {
            merged.add(new RecentTransactionResponse(
                    investment.getId(),
                    "INVESTMENT",
                    investment.getInstrumentName(),
                    investment.getInvestmentType() != null ? investment.getInvestmentType().name() : null,
                    investment.getAmountInvested(),
                    investment.getTransactionDate(),
                    investment.getOrigin()));
        }

        merged.sort(java.util.Comparator
                .comparing(RecentTransactionResponse::transactionDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(RecentTransactionResponse::id));
        return merged.stream().limit(safeLimit).toList();
    }

}
