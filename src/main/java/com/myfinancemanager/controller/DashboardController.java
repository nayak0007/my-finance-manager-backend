package com.myfinancemanager.controller;

import com.myfinancemanager.dto.dashboard.CategoryBreakdownResponse;
import com.myfinancemanager.dto.dashboard.DashboardSummaryResponse;
import com.myfinancemanager.dto.dashboard.MonthlyTrendPoint;
import com.myfinancemanager.dto.dashboard.RecentTransactionResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Summary cards for the selected period")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboardService.summary(SecurityUtils.currentUserId(), period, from, to));
    }

    @Operation(summary = "Expense totals grouped by category")
    @GetMapping("/expenses-by-category")
    public ResponseEntity<List<CategoryBreakdownResponse>> expensesByCategory(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboardService.expensesByCategory(SecurityUtils.currentUserId(), period, from, to));
    }

    @Operation(summary = "Income totals grouped by category")
    @GetMapping("/income-by-category")
    public ResponseEntity<List<CategoryBreakdownResponse>> incomeByCategory(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboardService.incomeByCategory(SecurityUtils.currentUserId(), period, from, to));
    }

    @Operation(summary = "Monthly income vs expense trend")
    @GetMapping("/trend")
    public ResponseEntity<List<MonthlyTrendPoint>> trend(
            @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(dashboardService.monthlyTrend(SecurityUtils.currentUserId(), months));
    }

    @Operation(summary = "Recent transactions across all categories")
    @GetMapping("/recent-transactions")
    public ResponseEntity<List<RecentTransactionResponse>> recentTransactions(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.recentTransactions(SecurityUtils.currentUserId(), limit));
    }
}
