package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.domain.ExpenseRecord;
import com.myfinancemanager.domain.IncomeRecord;
import com.myfinancemanager.domain.InvestmentRecord;
import com.myfinancemanager.domain.InvestmentType;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.repository.ExpenseRepository;
import com.myfinancemanager.repository.IncomeRepository;
import com.myfinancemanager.repository.InvestmentRepository;
import com.myfinancemanager.repository.UserRepository;
import com.myfinancemanager.service.util.TransactionFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Central place that turns an incoming (parsed or manually reviewed) transaction into the
 * appropriate income/expense/investment record. Shared by statement import and auto-capture.
 */
@Service
@RequiredArgsConstructor
public class TransactionWriter {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final InvestmentRepository investmentRepository;
    private final UserRepository userRepository;

    public UUID write(UUID userId,
                      StagedTransactionType type,
                      BigDecimal amount,
                      LocalDate date,
                      String description,
                      String merchant,
                      String category,
                      PaymentMode paymentMode,
                      String source,
                      TransactionOrigin origin,
                      String notes) {
        if (type == null) {
            throw new BadRequestException("Transaction type is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("A positive transaction amount is required");
        }
        if (date == null) {
            throw new BadRequestException("Transaction date is required");
        }
        return switch (type) {
            case INCOME -> writeIncome(userId, amount, date, source, description, category, origin, notes);
            case EXPENSE -> writeExpense(userId, amount, date, merchant, description, category, paymentMode, origin, notes);
            case INVESTMENT -> writeInvestment(userId, amount, date, description, merchant, category, origin, notes);
        };
    }

    private UUID writeIncome(UUID userId, BigDecimal amount, LocalDate date, String source,
                             String description, String category, TransactionOrigin origin, String notes) {
        IncomeRecord record = new IncomeRecord();
        record.setUser(userRepository.getReferenceById(userId));
        record.setAmount(amount);
        String resolvedSource = firstNonBlank(source, description);
        record.setSource(resolvedSource);
        record.setCategory(category != null && !category.isBlank() ? category : "other");
        record.setTransactionDate(date);
        record.setOrigin(origin);
        record.setNotes(notes);
        record.setFingerprint(TransactionFingerprint.of(StagedTransactionType.INCOME, amount, date, resolvedSource));
        return incomeRepository.save(record).getId();
    }

    private UUID writeExpense(UUID userId, BigDecimal amount, LocalDate date, String merchant,
                              String description, String category, PaymentMode paymentMode,
                              TransactionOrigin origin, String notes) {
        ExpenseRecord record = new ExpenseRecord();
        record.setUser(userRepository.getReferenceById(userId));
        record.setAmount(amount);
        String resolvedMerchant = firstNonBlank(merchant, description);
        record.setMerchant(resolvedMerchant);
        record.setCategory(category != null && !category.isBlank() ? category : "other");
        record.setPaymentMode(paymentMode != null ? paymentMode : PaymentMode.OTHER);
        record.setTransactionDate(date);
        record.setOrigin(origin);
        record.setNotes(notes);
        record.setFingerprint(TransactionFingerprint.of(StagedTransactionType.EXPENSE, amount, date, resolvedMerchant));
        return expenseRepository.save(record).getId();
    }

    private UUID writeInvestment(UUID userId, BigDecimal amount, LocalDate date, String description,
                                 String merchant, String category, TransactionOrigin origin, String notes) {
        InvestmentRecord record = new InvestmentRecord();
        record.setUser(userRepository.getReferenceById(userId));
        String instrument = firstNonBlank(description, merchant, category);
        record.setInstrumentName(instrument != null ? instrument : "Imported investment");
        record.setInvestmentType(InvestmentType.OTHER);
        record.setAmountInvested(amount);
        record.setTransactionDate(date);
        record.setOrigin(origin);
        record.setNotes(notes);
        record.setFingerprint(TransactionFingerprint.of(StagedTransactionType.INVESTMENT, amount, date, instrument));
        return investmentRepository.save(record).getId();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
