package com.myfinancemanager.repository;

import com.myfinancemanager.domain.InvestmentRecord;
import com.myfinancemanager.repository.projection.TypeAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvestmentRepository extends JpaRepository<InvestmentRecord, UUID>, JpaSpecificationExecutor<InvestmentRecord> {

    @Query("select coalesce(sum(i.amountInvested), 0) from InvestmentRecord i where i.user.id = :userId")
    BigDecimal sumInvested(@Param("userId") UUID userId);

    @Query("select coalesce(sum(i.amountInvested), 0) from InvestmentRecord i " +
            "where i.user.id = :userId and i.transactionDate between :from and :to")
    BigDecimal sumInvestedBetween(@Param("userId") UUID userId,
                                  @Param("from") java.time.LocalDate from,
                                  @Param("to") java.time.LocalDate to);

    long countByUserIdAndTransactionDateBetween(UUID userId, java.time.LocalDate from, java.time.LocalDate to);

    @Query("select coalesce(sum(coalesce(i.currentValue, i.amountInvested)), 0) from InvestmentRecord i " +
            "where i.user.id = :userId")
    BigDecimal sumCurrentValue(@Param("userId") UUID userId);

    @Query("select i.investmentType as type, sum(i.amountInvested) as invested, " +
            "sum(coalesce(i.currentValue, i.amountInvested)) as currentValue " +
            "from InvestmentRecord i where i.user.id = :userId group by i.investmentType")
    List<TypeAllocation> allocationByType(@Param("userId") UUID userId);

    org.springframework.data.domain.Page<InvestmentRecord> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);

    boolean existsByUserIdAndFingerprint(UUID userId, String fingerprint);

    java.util.Optional<InvestmentRecord> findFirstByUserIdAndFingerprint(UUID userId, String fingerprint);
}
