package com.flowaccount.productmanagement.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.flowaccount.productmanagement.model.Product;

@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();

    // index ของ SKU (ไม่สนตัวพิมพ์เล็ก-ใหญ่) เพื่อให้เช็ก SKU ซ้ำได้ใน O(1)
    private final ConcurrentHashMap<String, Long> skuIndex = new ConcurrentHashMap<>();

    private final AtomicLong lastId = new AtomicLong();

    @Override
    public List<Product> findAll() {
        return products.values().stream().sorted(Comparator.comparingLong(Product::id)).toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public boolean existsBySku(String sku) {
        return skuIndex.containsKey(normalize(sku));
    }

    @Override
    public Product add(String name, String sku, BigDecimal price, int stock, String category, Instant createdAt) {
        Product product = new Product(lastId.incrementAndGet(), name, sku, price, stock, category, createdAt);
        products.put(product.id(), product);
        skuIndex.put(normalize(sku), product.id());
        return product;
    }

    @Override
    public void update(Product product) {
        products.put(product.id(), product);
    }

    /** ล้างข้อมูลทั้งหมด (ใช้ในเทสต์) */
    public void clear() {
        products.clear();
        skuIndex.clear();
        lastId.set(0);
    }

    private static String normalize(String sku) {
        return sku.toLowerCase(Locale.ROOT);
    }
}
