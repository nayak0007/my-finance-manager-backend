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
@Table(name = "expense_records", indexes = {
        @Index(name = "idx_expense_user_date", columnList = "user_id, transaction_date"),
        @Index(name = "idx_expense_fingerprint", columnList = "fingerprint")
})
public class ExpenseRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "merchant", length = 200)
    private String merchant;

    @Column(name = "category", length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 30)
    private PaymentMode paymentMode = PaymentMode.OTHER;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private TransactionOrigin origin = TransactionOrigin.MANUAL;

    @Column(name = "recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;
}
