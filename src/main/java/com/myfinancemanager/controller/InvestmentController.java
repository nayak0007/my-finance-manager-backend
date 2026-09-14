package com.myfinancemanager.controller;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.domain.InvestmentType;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.investment.InvestmentRequest;
import com.myfinancemanager.dto.investment.InvestmentResponse;
import com.myfinancemanager.dto.investment.PortfolioSummaryResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.InvestmentService;
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

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Investments")
@RestController
@RequestMapping("/api/v1/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    @Operation(summary = "List investment records with optional filters")
    @GetMapping
    public ResponseEntity<PageResponse<InvestmentResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) InvestmentType type,
            @RequestParam(required = false) String broker,
            @RequestParam(required = false) TransactionOrigin origin,
            @RequestParam(required = false, name = "q") String search,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        InvestmentService.InvestmentFilter filter =
                new InvestmentService.InvestmentFilter(from, to, type, broker, origin, search);
        return ResponseEntity.ok(PageResponse.from(
                investmentService.list(SecurityUtils.currentUserId(), filter, pageable), r -> r));
    }

    @Operation(summary = "Portfolio summary: totals, gain/loss and allocation")
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryResponse> summary() {
        return ResponseEntity.ok(investmentService.portfolioSummary(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Create an investment record")
    @PostMapping
    public ResponseEntity<InvestmentResponse> create(@Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentService.create(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Get an investment record")
    @GetMapping("/{id}")
    public ResponseEntity<InvestmentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(investmentService.get(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Update an investment record")
    @PutMapping("/{id}")
    public ResponseEntity<InvestmentResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.ok(investmentService.update(SecurityUtils.currentUserId(), id, request));
    }

    @Operation(summary = "Delete an investment record")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        investmentService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
