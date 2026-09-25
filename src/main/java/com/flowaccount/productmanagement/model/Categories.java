package com.flowaccount.productmanagement.model;

import java.util.List;

public final class Categories {

    public static final List<String> ALL = List.of("อาหาร", "เครื่องดื่ม", "ของใช้", "เสื้อผ้า");

    private Categories() {
    }

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }
}
