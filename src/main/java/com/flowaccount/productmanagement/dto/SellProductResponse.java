package com.flowaccount.productmanagement.dto;

import java.math.BigDecimal;

public record SellProductResponse(
        long productId,
        String name,
        String sku,
        int quantitySold,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        int remainingStock) {
}
