package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.domain.IncomeRecord;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.domain.User;
import com.myfinancemanager.dto.income.IncomeRequest;
import com.myfinancemanager.dto.income.IncomeResponse;
import com.myfinancemanager.repository.IncomeRepository;
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
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final UserRepository userRepository;

    @Transactional
    public IncomeResponse create(UUID userId, IncomeRequest request) {
        IncomeRecord record = new IncomeRecord();
        record.setUser(userRepository.getReferenceById(userId));
        apply(record, request);
        return IncomeResponse.from(incomeRepository.save(record));
    }

    @Transactional(readOnly = true)
    public IncomeResponse get(UUID userId, UUID id) {
        return IncomeResponse.from(load(userId, id));
    }

    @Transactional(readOnly = true)
    public Page<IncomeResponse> list(UUID userId, IncomeFilter filter, Pageable pageable) {
        return incomeRepository.findAll(specification(userId, filter), pageable).map(IncomeResponse::from);
    }

    @Transactional
    public IncomeResponse update(UUID userId, UUID id, IncomeRequest request) {
        IncomeRecord record = load(userId, id);
        apply(record, request);
        return IncomeResponse.from(incomeRepository.save(record));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        incomeRepository.delete(load(userId, id));
    }

    private IncomeRecord load(UUID userId, UUID id) {
        IncomeRecord record = incomeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Income record not found"));
        if (!record.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Income record not found");
        }
        return record;
    }

    private void apply(IncomeRecord record, IncomeRequest request) {
        record.setAmount(request.amount());
        record.setSource(request.source());
        record.setCategory(request.category() == null ? "other" : request.category());
        record.setTransactionDate(request.transactionDate());
        record.setOrigin(request.origin() == null ? TransactionOrigin.MANUAL : request.origin());
        record.setRecurring(request.recurring());
        record.setNotes(request.notes());
        record.setFingerprint(TransactionFingerprint.of(
                StagedTransactionType.INCOME,
                request.amount(),
                request.transactionDate(),
                request.source() != null ? request.source() : request.category()));
    }

    private Specification<IncomeRecord> specification(UUID userId, IncomeFilter filter) {
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
                if (filter.source() != null && !filter.source().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("source")), "%" + filter.source().toLowerCase() + "%"));
                }
                if (filter.category() != null && !filter.category().isBlank()) {
                    predicates.add(cb.equal(cb.lower(root.get("category")), filter.category().toLowerCase()));
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
                            cb.like(cb.lower(root.get("source")), like)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public record IncomeFilter(
            LocalDate from,
            LocalDate to,
            String source,
            String category,
            TransactionOrigin origin,
            Boolean recurring,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search
    ) {
    }
}
