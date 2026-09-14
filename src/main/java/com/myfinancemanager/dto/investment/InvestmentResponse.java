package com.myfinancemanager.dto.investment;

import com.myfinancemanager.domain.InvestmentRecord;
import com.myfinancemanager.domain.InvestmentType;
import com.myfinancemanager.domain.TransactionOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvestmentResponse(
        UUID id,
        String instrumentName,
        InvestmentType investmentType,
        BigDecimal amountInvested,
        BigDecimal currentValue,
        BigDecimal gainLoss,
        BigDecimal gainLossPercent,
        LocalDate transactionDate,
        String broker,
        TransactionOrigin origin,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvestmentResponse from(InvestmentRecord record) {
        BigDecimal invested = record.getAmountInvested();
        BigDecimal current = record.getCurrentValue() != null ? record.getCurrentValue() : invested;
        BigDecimal gainLoss = current != null && invested != null ? current.subtract(invested) : null;
        BigDecimal gainLossPercent = null;
        if (gainLoss != null && invested != null && invested.signum() != 0) {
            gainLossPercent = gainLoss
                    .multiply(BigDecimal.valueOf(100))
                    .divide(invested, 4, java.math.RoundingMode.HALF_UP);
        }
        return new InvestmentResponse(
                record.getId(),
                record.getInstrumentName(),
                record.getInvestmentType(),
                invested,
                record.getCurrentValue(),
                gainLoss,
                gainLossPercent,
                record.getTransactionDate(),
                record.getBroker(),
                record.getOrigin(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
