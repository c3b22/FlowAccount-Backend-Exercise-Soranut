package com.flowaccount.productmanagement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.flowaccount.productmanagement.dto.CreateProductRequest;
import com.flowaccount.productmanagement.model.Categories;

public final class ProductValidator {

    public static final int MIN_SKU_LENGTH = 3;

    private ProductValidator() {
    }

    /**
     * ตรวจสอบทุก field แล้วรวม error ทั้งหมดกลับมาในรอบเดียว (ตามตัวอย่าง response ของโจทย์)
     * คาดว่า request ผ่าน {@link CreateProductRequest#normalized()} มาแล้ว
     */
    public static List<String> validate(CreateProductRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.name() == null || request.name().isBlank()) {
            errors.add(Messages.NAME_REQUIRED);
        }

        if (request.sku() == null || request.sku().isEmpty()) {
            errors.add(Messages.SKU_REQUIRED);
        } else if (request.sku().length() < MIN_SKU_LENGTH) {
            errors.add(Messages.SKU_TOO_SHORT);
        }

        if (request.price() == null) {
            errors.add(Messages.PRICE_REQUIRED);
        } else if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(Messages.PRICE_MUST_BE_POSITIVE);
        }

        if (request.stock() == null) {
            errors.add(Messages.STOCK_REQUIRED);
        } else if (request.stock() < 0) {
            errors.add(Messages.STOCK_MUST_NOT_BE_NEGATIVE);
        }

        if (!Categories.isValid(request.category())) {
            errors.add(Messages.CATEGORY_INVALID);
        }

        return errors;
    }
}
