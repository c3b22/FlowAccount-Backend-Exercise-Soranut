package com.flowaccount.productmanagement.model;

import java.math.BigDecimal;
import java.time.Instant;

/** สินค้าในระบบ (immutable — การแก้ไขทำโดยสร้าง record ใหม่ด้วย withXxx) */
public record Product(
        long id,
        String name,
        String sku,
        BigDecimal price,
        int stock,
        String category,
        Instant createdAt) {

    public Product withStock(int newStock) {
        return new Product(id, name, sku, price, newStock, category, createdAt);
    }

    public Product withPrice(BigDecimal newPrice) {
        return new Product(id, name, sku, newPrice, stock, category, createdAt);
    }
}
