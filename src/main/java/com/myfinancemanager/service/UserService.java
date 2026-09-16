package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.domain.User;
import com.myfinancemanager.dto.expense.ExpenseResponse;
import com.myfinancemanager.dto.income.IncomeResponse;
import com.myfinancemanager.dto.investment.InvestmentResponse;
import com.myfinancemanager.dto.user.UpdateProfileRequest;
import com.myfinancemanager.dto.user.UserResponse;
import com.myfinancemanager.repository.AIInsightRepository;
import com.myfinancemanager.repository.AutoCaptureQueueRepository;
import com.myfinancemanager.repository.BudgetRepository;
import com.myfinancemanager.repository.AutoCaptureSettingsRepository;
import com.myfinancemanager.repository.ExpenseRepository;
import com.myfinancemanager.repository.ImportBatchRepository;
import com.myfinancemanager.repository.IncomeRepository;
import com.myfinancemanager.repository.InvestmentRepository;
import com.myfinancemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final InvestmentRepository investmentRepository;
    private final ImportBatchRepository importBatchRepository;
    private final AutoCaptureQueueRepository autoCaptureQueueRepository;
    private final AutoCaptureSettingsRepository autoCaptureSettingsRepository;
    private final AIInsightRepository aiInsightRepository;
    private final BudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public User getEntity(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(getEntity(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getEntity(userId);
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.currency() != null) {
            user.setCurrency(request.currency().toUpperCase());
        }
        if (request.preferences() != null) {
            user.setPreferences(new LinkedHashMap<>(request.preferences()));
        }
        if (request.notificationsEnabled() != null) {
            user.setNotificationsEnabled(request.notificationsEnabled());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportData(UUID userId) {
        User user = getEntity(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now());
        export.put("user", UserResponse.from(user));
        List<IncomeResponse> incomes = incomeRepository.findAll(ownedBy(userId)).stream()
                .map(IncomeResponse::from).toList();
        List<ExpenseResponse> expenses = expenseRepository.findAll(ownedBy(userId)).stream()
                .map(ExpenseResponse::from).toList();
        List<InvestmentResponse> investments = investmentRepository.findAll(ownedBy(userId)).stream()
                .map(InvestmentResponse::from).toList();
        export.put("incomeRecords", incomes);
        export.put("expenseRecords", expenses);
        export.put("investmentRecords", investments);
        return export;
    }

    /**
     * Removes the local profile and every record owned by it.
     *
     * <p>No password is required: credentials live in Neon Auth, ownership is already proven by
     * the caller's token, and the app deletes the Neon Auth user itself.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = getEntity(userId);
        autoCaptureSettingsRepository.findByUserId(userId).ifPresent(autoCaptureSettingsRepository::delete);
        autoCaptureQueueRepository.deleteAll(autoCaptureQueueRepository.findByUserId(userId));
        aiInsightRepository.deleteAll(aiInsightRepository.findByUserId(userId));
        budgetRepository.deleteAll(budgetRepository.findByUserIdOrderByCategoryAsc(userId));
        importBatchRepository.deleteAll(importBatchRepository.findByUserId(userId));
        incomeRepository.deleteAll(incomeRepository.findAll(ownedBy(userId)));
        expenseRepository.deleteAll(expenseRepository.findAll(ownedBy(userId)));
        investmentRepository.deleteAll(investmentRepository.findAll(ownedBy(userId)));
        userRepository.delete(user);
    }

    private <T> Specification<T> ownedBy(UUID userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("id"), userId);
    }
}
