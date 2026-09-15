package com.myfinancemanager.repository;

import com.myfinancemanager.domain.IncomeRecord;
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

public interface IncomeRepository extends JpaRepository<IncomeRecord, UUID>, JpaSpecificationExecutor<IncomeRecord> {

    @Query("select coalesce(sum(i.amount), 0) from IncomeRecord i " +
            "where i.user.id = :userId and i.transactionDate between :from and :to")
    BigDecimal sumAmount(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select i.category as category, sum(i.amount) as total from IncomeRecord i " +
            "where i.user.id = :userId and i.transactionDate between :from and :to " +
            "group by i.category order by sum(i.amount) desc")
    List<CategoryTotal> sumByCategory(@Param("userId") UUID userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    @Query("select year(i.transactionDate) as yearValue, month(i.transactionDate) as monthValue, " +
            "sum(i.amount) as total from IncomeRecord i " +
            "where i.user.id = :userId and i.transactionDate between :from and :to " +
            "group by year(i.transactionDate), month(i.transactionDate) " +
            "order by year(i.transactionDate), month(i.transactionDate)")
    List<MonthlyTotal> sumByMonth(@Param("userId") UUID userId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    long countByUserIdAndTransactionDateBetween(UUID userId, LocalDate from, LocalDate to);

    org.springframework.data.domain.Page<IncomeRecord> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);

    boolean existsByUserIdAndFingerprint(UUID userId, String fingerprint);

    java.util.Optional<IncomeRecord> findFirstByUserIdAndFingerprint(UUID userId, String fingerprint);
}
