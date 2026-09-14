package com.myfinancemanager.repository.projection;

import com.myfinancemanager.domain.InvestmentType;

import java.math.BigDecimal;

public interface TypeAllocation {

    InvestmentType getType();

    BigDecimal getInvested();

    BigDecimal getCurrentValue();
}
