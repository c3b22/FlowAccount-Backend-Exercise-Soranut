package com.flowaccount.productmanagement.exception;

import java.util.List;

/** ข้อมูลที่ส่งมาไม่ถูกต้อง -> HTTP 400 พร้อมรายการ error */
public class ApiValidationException extends RuntimeException {

    private final List<String> errors;

    public ApiValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public ApiValidationException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
