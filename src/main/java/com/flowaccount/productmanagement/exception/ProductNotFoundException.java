package com.flowaccount.productmanagement.exception;

import com.flowaccount.productmanagement.service.Messages;

/** ไม่พบสินค้า -> HTTP 404 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super(Messages.PRODUCT_NOT_FOUND);
    }
}
