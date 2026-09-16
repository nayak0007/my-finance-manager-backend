package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.domain.Budget;
import com.myfinancemanager.dto.budget.BudgetResponse;
import com.myfinancemanager.dto.budget.BudgetUpsertRequest;
import com.myfinancemanager.repository.BudgetRepository;
import com.myfinancemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(UUID userId) {
        return budgetRepository.findByUserIdOrderByCategoryAsc(userId).stream()
                .map(BudgetResponse::from)
                .toList();
    }

    /**
     * Creates or updates the budget for a category. Keyed on the category so that replaying the
     * same client-side change (which happens whenever a sync retries) edits the existing budget
     * instead of adding a duplicate.
     */
    @Transactional
    public BudgetResponse upsert(UUID userId, String category, BudgetUpsertRequest request) {
        String key = normalizeCategory(category);
        Budget budget = budgetRepository.findByUserIdAndCategory(userId, key).orElseGet(() -> {
            Budget created = new Budget();
            created.setUser(userRepository.getReferenceById(userId));
            created.setCategory(key);
            return created;
        });
        budget.setMonthlyLimit(request.monthlyLimit());
        return BudgetResponse.from(budgetRepository.save(budget));
    }

    /**
     * Removes a category's budget. A category that has no budget is not an error: clients replay
     * deletes, and answering 404 would only make them retry a change that is already applied.
     */
    @Transactional
    public void delete(UUID userId, String category) {
        budgetRepository.findByUserIdAndCategory(userId, normalizeCategory(category))
                .ifPresent(budgetRepository::delete);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BadRequestException("A budget category is required");
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }
}
