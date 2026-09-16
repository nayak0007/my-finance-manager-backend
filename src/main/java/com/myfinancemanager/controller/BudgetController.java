package com.myfinancemanager.controller;

import com.myfinancemanager.dto.budget.BudgetResponse;
import com.myfinancemanager.dto.budget.BudgetUpsertRequest;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Budgets")
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @Operation(summary = "List the user's category budgets")
    @GetMapping
    public ResponseEntity<List<BudgetResponse>> list() {
        return ResponseEntity.ok(budgetService.list(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Create or update the monthly budget for a category")
    @PutMapping("/{category}")
    public ResponseEntity<BudgetResponse> upsert(@PathVariable String category,
                                                 @Valid @RequestBody BudgetUpsertRequest request) {
        return ResponseEntity.ok(budgetService.upsert(SecurityUtils.currentUserId(), category, request));
    }

    @Operation(summary = "Remove the monthly budget for a category")
    @DeleteMapping("/{category}")
    public ResponseEntity<Void> delete(@PathVariable String category) {
        budgetService.delete(SecurityUtils.currentUserId(), category);
        return ResponseEntity.noContent().build();
    }
}
