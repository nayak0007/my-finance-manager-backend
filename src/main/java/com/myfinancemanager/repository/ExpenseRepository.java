package com.myfinancemanager.repository;

import com.myfinancemanager.domain.ExpenseRecord;
import com.myfinancemanager.repository.projection.CategoryTotal;
import com.myfinancemanager.repository.projection.MonthlyTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<ExpenseRecord, UUID>, JpaSpecificationExecutor<ExpenseRecord> {

    @Query("select coalesce(sum(e.amount), 0) from ExpenseRecord e " +
            "where e.user.id = :userId and e.transactionDate between :from and :to")
    BigDecimal sumAmount(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e.category as category, sum(e.amount) as total from ExpenseRecord e " +
            "where e.user.id = :userId and e.transactionDate between :from and :to " +
            "group by e.category order by sum(e.amount) desc")
    List<CategoryTotal> sumByCategory(@Param("userId") UUID userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    @Query("select year(e.transactionDate) as yearValue, month(e.transactionDate) as monthValue, " +
            "sum(e.amount) as total from ExpenseRecord e " +
            "where e.user.id = :userId and e.transactionDate between :from and :to " +
            "group by year(e.transactionDate), month(e.transactionDate) " +
            "order by year(e.transactionDate), month(e.transactionDate)")
    List<MonthlyTotal> sumByMonth(@Param("userId") UUID userId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    long countByUserIdAndTransactionDateBetween(UUID userId, LocalDate from, LocalDate to);

    org.springframework.data.domain.Page<ExpenseRecord> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);

    boolean existsByUserIdAndFingerprint(UUID userId, String fingerprint);

    java.util.Optional<ExpenseRecord> findFirstByUserIdAndFingerprint(UUID userId, String fingerprint);
}
