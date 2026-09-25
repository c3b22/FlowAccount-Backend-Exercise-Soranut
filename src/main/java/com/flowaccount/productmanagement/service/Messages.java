package com.flowaccount.productmanagement.service;

import com.flowaccount.productmanagement.model.Categories;

/** ข้อความ error ภาษาไทยทั้งหมดของ API */
public final class Messages {

    public static final String NAME_REQUIRED = "ชื่อสินค้าต้องไม่ว่าง";
    public static final String SKU_REQUIRED = "รหัสสินค้าต้องไม่ว่าง";
    public static final String SKU_TOO_SHORT = "รหัสสินค้าต้องมีอย่างน้อย 3 ตัวอักษร";
    public static final String SKU_DUPLICATE = "รหัสสินค้านี้มีอยู่ในระบบแล้ว";
    public static final String PRICE_REQUIRED = "ต้องระบุราคา";
    public static final String PRICE_MUST_BE_POSITIVE = "ราคาต้องมากกว่า 0";
    public static final String STOCK_REQUIRED = "ต้องระบุจำนวนคงเหลือ";
    public static final String STOCK_MUST_NOT_BE_NEGATIVE = "จำนวนคงเหลือต้องไม่ติดลบ";
    public static final String CATEGORY_INVALID =
            "หมวดหมู่ต้องเป็นหนึ่งใน: " + String.join(", ", Categories.ALL);

    public static final String PRODUCT_ID_REQUIRED = "ต้องระบุรหัสสินค้า (productId)";
    public static final String QUANTITY_MUST_BE_POSITIVE = "จำนวนที่ขายต้องมากกว่า 0";
    public static final String PRODUCT_NOT_FOUND = "ไม่พบสินค้า";

    public static final String KEYWORD_REQUIRED = "ต้องระบุคำค้นหา (keyword)";
    public static final String BULK_ITEMS_REQUIRED = "ต้องระบุรายการสินค้าที่ต้องการอัพเดทราคาอย่างน้อย 1 รายการ";
    public static final String BULK_ITEM_INVALID = "รูปแบบรายการไม่ถูกต้อง";
    public static final String NEW_PRICE_REQUIRED = "ต้องระบุราคาใหม่ (newPrice)";

    public static final String INVALID_REQUEST_BODY = "รูปแบบข้อมูลที่ส่งมาไม่ถูกต้อง";
    public static final String ENDPOINT_NOT_FOUND = "ไม่พบ endpoint ที่ร้องขอ";
    public static final String METHOD_NOT_ALLOWED = "ไม่รองรับ HTTP method นี้";
    public static final String UNSUPPORTED_MEDIA_TYPE = "ไม่รองรับชนิดข้อมูลนี้ กรุณาส่งเป็น application/json";
    public static final String INTERNAL_ERROR = "เกิดข้อผิดพลาดภายในระบบ";

    private Messages() {
    }

    public static String insufficientStock(int stock, int quantity) {
        return "สินค้าคงเหลือไม่เพียงพอ (คงเหลือ %d, ต้องการ %d)".formatted(stock, quantity);
    }
}
