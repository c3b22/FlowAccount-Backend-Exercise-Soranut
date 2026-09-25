package com.flowaccount.productmanagement.dto;

import java.math.BigDecimal;

/**
 * ทุก field เป็น nullable (wrapper type) โดยตั้งใจ: ถ้า field หายไปจาก JSON จะได้ null
 * แล้วให้ ProductValidator สร้างข้อความ error ภาษาไทยตามโจทย์ แทนที่จะให้ framework ตอบ error format ของตัวเอง
 */
public record CreateProductRequest(
        String name,
        String sku,
        BigDecimal price,
        Integer stock,
        String category) {

    /** ตัดช่องว่างหัวท้ายของ field ที่เป็นข้อความ */
    public CreateProductRequest normalized() {
        return new CreateProductRequest(trim(name), trim(sku), price, stock, trim(category));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
