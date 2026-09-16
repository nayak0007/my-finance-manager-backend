package com.myfinancemanager.repository;

import com.myfinancemanager.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdOrderByCategoryAsc(UUID userId);

    Optional<Budget> findByUserIdAndCategory(UUID userId, String category);
}
