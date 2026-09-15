package com.myfinancemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "investment_records", indexes = {
        @Index(name = "idx_investment_user_date", columnList = "user_id, transaction_date"),
        @Index(name = "idx_investment_fingerprint", columnList = "fingerprint")
})
public class InvestmentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "instrument_name", nullable = false, length = 200)
    private String instrumentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_type", nullable = false, length = 30)
    private InvestmentType investmentType = InvestmentType.OTHER;

    @Column(name = "amount_invested", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountInvested;

    @Column(name = "current_value", precision = 19, scale = 4)
    private BigDecimal currentValue;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "broker", length = 200)
    private String broker;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private TransactionOrigin origin = TransactionOrigin.MANUAL;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;
}
