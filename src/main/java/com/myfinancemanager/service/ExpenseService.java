package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.domain.ExpenseRecord;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.expense.ExpenseRequest;
import com.myfinancemanager.dto.expense.ExpenseResponse;
import com.myfinancemanager.repository.ExpenseRepository;
import com.myfinancemanager.repository.UserRepository;
import com.myfinancemanager.service.util.TransactionFingerprint;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseResponse create(UUID userId, ExpenseRequest request) {
        ExpenseRecord record = new ExpenseRecord();
        record.setUser(userRepository.getReferenceById(userId));
        apply(record, request);
        return ExpenseResponse.from(expenseRepository.save(record));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID userId, UUID id) {
        return ExpenseResponse.from(load(userId, id));
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> list(UUID userId, ExpenseFilter filter, Pageable pageable) {
        return expenseRepository.findAll(specification(userId, filter), pageable).map(ExpenseResponse::from);
    }

    @Transactional
    public ExpenseResponse update(UUID userId, UUID id, ExpenseRequest request) {
        ExpenseRecord record = load(userId, id);
        apply(record, request);
        return ExpenseResponse.from(expenseRepository.save(record));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        expenseRepository.delete(load(userId, id));
    }

    private ExpenseRecord load(UUID userId, UUID id) {
        ExpenseRecord record = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense record not found"));
        if (!record.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Expense record not found");
        }
        return record;
    }

    private void apply(ExpenseRecord record, ExpenseRequest request) {
        record.setAmount(request.amount());
        record.setMerchant(request.merchant());
        record.setCategory(request.category() == null ? "other" : request.category());
        record.setPaymentMode(request.paymentMode() == null ? PaymentMode.OTHER : request.paymentMode());
        record.setTransactionDate(request.transactionDate());
        record.setOrigin(request.origin() == null ? TransactionOrigin.MANUAL : request.origin());
        record.setRecurring(request.recurring());
        record.setNotes(request.notes());
        record.setFingerprint(TransactionFingerprint.of(
                StagedTransactionType.EXPENSE,
                request.amount(),
                request.transactionDate(),
                request.merchant() != null ? request.merchant() : request.category()));
    }

    private Specification<ExpenseRecord> specification(UUID userId, ExpenseFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (filter != null) {
                if (filter.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.from()));
                }
                if (filter.to() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.to()));
                }
                if (filter.merchant() != null && !filter.merchant().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("merchant")), "%" + filter.merchant().toLowerCase() + "%"));
                }
                if (filter.category() != null && !filter.category().isBlank()) {
                    predicates.add(cb.equal(cb.lower(root.get("category")), filter.category().toLowerCase()));
                }
                if (filter.paymentMode() != null) {
                    predicates.add(cb.equal(root.get("paymentMode"), filter.paymentMode()));
                }
                if (filter.origin() != null) {
                    predicates.add(cb.equal(root.get("origin"), filter.origin()));
                }
                if (filter.recurring() != null) {
                    predicates.add(cb.equal(root.get("recurring"), filter.recurring()));
                }
                if (filter.minAmount() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
                }
                if (filter.maxAmount() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
                }
                if (filter.search() != null && !filter.search().isBlank()) {
                    String like = "%" + filter.search().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("notes")), like),
                            cb.like(cb.lower(root.get("merchant")), like)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public record ExpenseFilter(
            LocalDate from,
            LocalDate to,
            String merchant,
            String category,
            PaymentMode paymentMode,
            TransactionOrigin origin,
            Boolean recurring,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search
    ) {
    }
}
