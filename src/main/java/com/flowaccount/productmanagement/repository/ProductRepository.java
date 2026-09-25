package com.flowaccount.productmanagement.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.flowaccount.productmanagement.model.Product;

/**
 * แต่ละ method ปลอดภัยต่อการเรียกจากหลาย thread เมื่อเรียกเดี่ยวๆ แต่งานแบบ check-then-act
 * (เช่น เช็ก SKU ซ้ำแล้วค่อยเพิ่ม, เช็ก stock แล้วค่อยตัด) ผู้เรียกต้องทำภายใน lock เอง
 * ดู {@link com.flowaccount.productmanagement.service.ProductService}
 */
public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(long id);

    boolean existsBySku(String sku);

    Product add(String name, String sku, BigDecimal price, int stock, String category, Instant createdAt);

    void update(Product product);
}
