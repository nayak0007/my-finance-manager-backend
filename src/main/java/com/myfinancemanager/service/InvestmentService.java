package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.domain.InvestmentRecord;
import com.myfinancemanager.domain.InvestmentType;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.investment.InvestmentRequest;
import com.myfinancemanager.dto.investment.InvestmentResponse;
import com.myfinancemanager.dto.investment.PortfolioSummaryResponse;
import com.myfinancemanager.repository.InvestmentRepository;
import com.myfinancemanager.repository.UserRepository;
import com.myfinancemanager.repository.projection.TypeAllocation;
import com.myfinancemanager.service.util.TransactionFingerprint;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public InvestmentResponse create(UUID userId, InvestmentRequest request) {
        InvestmentRecord record = new InvestmentRecord();
        record.setUser(userRepository.getReferenceById(userId));
        apply(record, request);
        return InvestmentResponse.from(investmentRepository.save(record));
    }

    @Transactional(readOnly = true)
    public InvestmentResponse get(UUID userId, UUID id) {
        return InvestmentResponse.from(load(userId, id));
    }

    @Transactional(readOnly = true)
    public Page<InvestmentResponse> list(UUID userId, InvestmentFilter filter, Pageable pageable) {
        return investmentRepository.findAll(specification(userId, filter), pageable).map(InvestmentResponse::from);
    }

    @Transactional
    public InvestmentResponse update(UUID userId, UUID id, InvestmentRequest request) {
        InvestmentRecord record = load(userId, id);
        apply(record, request);
        return InvestmentResponse.from(investmentRepository.save(record));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        investmentRepository.delete(load(userId, id));
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse portfolioSummary(UUID userId) {
        BigDecimal totalInvested = investmentRepository.sumInvested(userId);
        BigDecimal currentValue = investmentRepository.sumCurrentValue(userId);
        List<TypeAllocation> allocations = investmentRepository.allocationByType(userId);

        BigDecimal gainLoss = currentValue.subtract(totalInvested);
        BigDecimal gainLossPercent = totalInvested.signum() == 0
                ? BigDecimal.ZERO
                : gainLoss.multiply(BigDecimal.valueOf(100)).divide(totalInvested, 4, RoundingMode.HALF_UP);

        List<PortfolioSummaryResponse.AllocationItem> items = allocations.stream()
                .map(allocation -> {
                    BigDecimal invested = allocation.getInvested() == null ? BigDecimal.ZERO : allocation.getInvested();
                    BigDecimal value = allocation.getCurrentValue() == null ? invested : allocation.getCurrentValue();
                    BigDecimal percentage = totalInvested.signum() == 0
                            ? BigDecimal.ZERO
                            : invested.multiply(BigDecimal.valueOf(100))
                                    .divide(totalInvested, 4, RoundingMode.HALF_UP);
                    return new PortfolioSummaryResponse.AllocationItem(
                            allocation.getType(), invested, value, percentage);
                })
                .toList();

        return new PortfolioSummaryResponse(
                totalInvested,
                currentValue,
                gainLoss,
                gainLossPercent,
                items,
                investmentRepository.count(specification(userId, null)));
    }

    private InvestmentRecord load(UUID userId, UUID id) {
        InvestmentRecord record = investmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investment record not found"));
        if (!record.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Investment record not found");
        }
        return record;
    }

    private void apply(InvestmentRecord record, InvestmentRequest request) {
        record.setInstrumentName(request.instrumentName());
        record.setInvestmentType(request.investmentType() == null ? InvestmentType.OTHER : request.investmentType());
        record.setAmountInvested(request.amountInvested());
        record.setCurrentValue(request.currentValue());
        record.setTransactionDate(request.transactionDate());
        record.setBroker(request.broker());
        record.setOrigin(request.origin() == null ? TransactionOrigin.MANUAL : request.origin());
        record.setNotes(request.notes());
        record.setFingerprint(TransactionFingerprint.of(
                StagedTransactionType.INVESTMENT,
                request.amountInvested(),
                request.transactionDate(),
                request.instrumentName()));
    }

    private Specification<InvestmentRecord> specification(UUID userId, InvestmentFilter filter) {
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
                if (filter.type() != null) {
                    predicates.add(cb.equal(root.get("investmentType"), filter.type()));
                }
                if (filter.broker() != null && !filter.broker().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("broker")), "%" + filter.broker().toLowerCase() + "%"));
                }
                if (filter.origin() != null) {
                    predicates.add(cb.equal(root.get("origin"), filter.origin()));
                }
                if (filter.search() != null && !filter.search().isBlank()) {
                    String like = "%" + filter.search().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("notes")), like),
                            cb.like(cb.lower(root.get("instrumentName")), like)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public record InvestmentFilter(
            LocalDate from,
            LocalDate to,
            InvestmentType type,
            String broker,
            TransactionOrigin origin,
            String search
    ) {
    }
}
