package com.myfinancemanager.repository.projection;

import java.math.BigDecimal;

public interface CategoryTotal {

    String getCategory();

    BigDecimal getTotal();
}
