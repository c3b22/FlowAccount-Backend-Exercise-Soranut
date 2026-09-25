package com.flowaccount.productmanagement.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.flowaccount.productmanagement.dto.BulkPriceUpdateFailure;
import com.flowaccount.productmanagement.dto.BulkPriceUpdateItem;
import com.flowaccount.productmanagement.dto.BulkPriceUpdateResponse;
import com.flowaccount.productmanagement.dto.CreateProductRequest;
import com.flowaccount.productmanagement.dto.SellProductRequest;
import com.flowaccount.productmanagement.dto.SellProductResponse;
import com.flowaccount.productmanagement.exception.ApiValidationException;
import com.flowaccount.productmanagement.exception.ProductNotFoundException;
import com.flowaccount.productmanagement.model.Product;
import com.flowaccount.productmanagement.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final Clock clock;

    // การเขียนทั้งหมดผ่าน lock เดียว เพื่อให้งาน check-then-act เป็น atomic
    // (SKU ซ้ำ / stock ไม่ติดลบ แม้มีหลาย request เข้ามาพร้อมกัน)
    // ส่วนการอ่านไม่ต้องใช้ lock เพราะ Product เป็น immutable record
    // ใช้ ReentrantLock แทน synchronized เพื่อเป็นมิตรกับ virtual threads
    private final ReentrantLock writeLock = new ReentrantLock();

    public ProductService(ProductRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Product create(CreateProductRequest rawRequest) {
        CreateProductRequest request = rawRequest.normalized();
        List<String> errors = new ArrayList<>(ProductValidator.validate(request));

        return withWriteLock(() -> {
            String sku = request.sku();
            if (sku != null && sku.length() >= ProductValidator.MIN_SKU_LENGTH && repository.existsBySku(sku)) {
                errors.add(Messages.SKU_DUPLICATE);
            }
            if (!errors.isEmpty()) {
                throw new ApiValidationException(errors);
            }

            return repository.add(
                    request.name(),
                    sku,
                    request.price(),
                    request.stock(),
                    request.category(),
                    clock.instant());
        });
    }

    public List<Product> list(String category) {
        List<Product> products = repository.findAll();
        if (category == null || category.isBlank()) {
            return products;
        }
        String wanted = category.trim();
        return products.stream().filter(p -> p.category().equals(wanted)).toList();
    }

    public List<Product> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ApiValidationException(Messages.KEYWORD_REQUIRED);
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(kw)
                        || p.sku().toLowerCase(Locale.ROOT).contains(kw))
                .toList();
    }

    public SellProductResponse sell(SellProductRequest request) {
        // ตรวจตามลำดับที่โจทย์กำหนด: quantity > 0 -> มีสินค้า -> stock พอ
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new ApiValidationException(Messages.QUANTITY_MUST_BE_POSITIVE);
        }
        if (request.productId() == null) {
            throw new ApiValidationException(Messages.PRODUCT_ID_REQUIRED);
        }
        int quantity = request.quantity();
        long productId = request.productId();

        return withWriteLock(() -> {
            Product product = repository.findById(productId).orElseThrow(ProductNotFoundException::new);

            if (product.stock() < quantity) {
                throw new ApiValidationException(Messages.insufficientStock(product.stock(), quantity));
            }

            Product updated = product.withStock(product.stock() - quantity);
            repository.update(updated);

            return new SellProductResponse(
                    updated.id(),
                    updated.name(),
                    updated.sku(),
                    quantity,
                    updated.price(),
                    updated.price().multiply(BigDecimal.valueOf(quantity)),
                    updated.stock());
        });
    }

    public BulkPriceUpdateResponse bulkUpdatePrices(List<BulkPriceUpdateItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiValidationException(Messages.BULK_ITEMS_REQUIRED);
        }

        List<BulkPriceUpdateFailure> failures = new ArrayList<>();
        int[] updated = {0};

        // ทำทั้งชุดภายใน lock เดียว เพื่อให้ผู้อ่านคนอื่นเห็นผลของ batch ที่สอดคล้องกัน
        // รายการที่ผิดจะถูกข้าม (partial success) และรายงานเหตุผลกลับไปใน failures
        withWriteLock(() -> {
            for (BulkPriceUpdateItem item : items) {
                if (item == null) {
                    failures.add(new BulkPriceUpdateFailure(null, Messages.BULK_ITEM_INVALID));
                } else if (item.productId() == null) {
                    failures.add(new BulkPriceUpdateFailure(null, Messages.PRODUCT_ID_REQUIRED));
                } else if (item.newPrice() == null) {
                    failures.add(new BulkPriceUpdateFailure(item.productId(), Messages.NEW_PRICE_REQUIRED));
                } else if (item.newPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    failures.add(new BulkPriceUpdateFailure(item.productId(), Messages.PRICE_MUST_BE_POSITIVE));
                } else {
                    repository.findById(item.productId()).ifPresentOrElse(
                            product -> {
                                repository.update(product.withPrice(item.newPrice()));
                                updated[0]++;
                            },
                            () -> failures.add(new BulkPriceUpdateFailure(item.productId(), Messages.PRODUCT_NOT_FOUND)));
                }
            }
            return null;
        });

        return new BulkPriceUpdateResponse(items.size(), updated[0], failures.size(), failures);
    }

    private <T> T withWriteLock(Supplier<T> action) {
        writeLock.lock();
        try {
            return action.get();
        } finally {
            writeLock.unlock();
        }
    }
}
