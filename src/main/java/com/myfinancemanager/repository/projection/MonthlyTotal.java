package com.myfinancemanager.repository.projection;

import java.math.BigDecimal;

public interface MonthlyTotal {

    Integer getYearValue();

    Integer getMonthValue();

    BigDecimal getTotal();
}
