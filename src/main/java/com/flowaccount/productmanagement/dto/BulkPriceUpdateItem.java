package com.flowaccount.productmanagement.dto;

import java.math.BigDecimal;

public record BulkPriceUpdateItem(Long productId, BigDecimal newPrice) {
}
