package com.myfinancemanager.controller;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.income.IncomeRequest;
import com.myfinancemanager.dto.income.IncomeResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.IncomeService;
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

@Tag(name = "Income")
@RestController
@RequestMapping("/api/v1/incomes")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @Operation(summary = "List income records with optional filters")
    @GetMapping
    public ResponseEntity<PageResponse<IncomeResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TransactionOrigin origin,
            @RequestParam(required = false) Boolean recurring,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false, name = "q") String search,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        IncomeService.IncomeFilter filter = new IncomeService.IncomeFilter(
                from, to, source, category, origin, recurring, minAmount, maxAmount, search);
        return ResponseEntity.ok(PageResponse.from(
                incomeService.list(SecurityUtils.currentUserId(), filter, pageable), r -> r));
    }

    @Operation(summary = "Create an income record")
    @PostMapping
    public ResponseEntity<IncomeResponse> create(@Valid @RequestBody IncomeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incomeService.create(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Get an income record")
    @GetMapping("/{id}")
    public ResponseEntity<IncomeResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(incomeService.get(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Update an income record")
    @PutMapping("/{id}")
    public ResponseEntity<IncomeResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody IncomeRequest request) {
        return ResponseEntity.ok(incomeService.update(SecurityUtils.currentUserId(), id, request));
    }

    @Operation(summary = "Delete an income record")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        incomeService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
