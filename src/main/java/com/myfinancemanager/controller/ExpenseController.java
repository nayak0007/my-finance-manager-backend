package com.myfinancemanager.controller;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.expense.ExpenseRequest;
import com.myfinancemanager.dto.expense.ExpenseResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Expenses")
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(summary = "List expense records with optional filters")
    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) PaymentMode paymentMode,
            @RequestParam(required = false) TransactionOrigin origin,
            @RequestParam(required = false) Boolean recurring,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false, name = "q") String search,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        ExpenseService.ExpenseFilter filter = new ExpenseService.ExpenseFilter(
                from, to, merchant, category, paymentMode, origin, recurring, minAmount, maxAmount, search);
        return ResponseEntity.ok(PageResponse.from(
                expenseService.list(SecurityUtils.currentUserId(), filter, pageable), r -> r));
    }

    @Operation(summary = "Create an expense record")
    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.create(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Get an expense record")
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(expenseService.get(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Update an expense record")
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.update(SecurityUtils.currentUserId(), id, request));
    }

    @Operation(summary = "Delete an expense record")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        expenseService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
