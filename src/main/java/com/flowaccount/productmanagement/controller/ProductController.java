package com.flowaccount.productmanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowaccount.productmanagement.dto.BulkPriceUpdateItem;
import com.flowaccount.productmanagement.dto.BulkPriceUpdateResponse;
import com.flowaccount.productmanagement.dto.CreateProductRequest;
import com.flowaccount.productmanagement.dto.SellProductRequest;
import com.flowaccount.productmanagement.dto.SellProductResponse;
import com.flowaccount.productmanagement.model.Product;
import com.flowaccount.productmanagement.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(summary = "เพิ่มสินค้าใหม่")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody CreateProductRequest request) {
        return service.create(request);
    }

    @Operation(summary = "ดึงรายการสินค้าทั้งหมด หรือกรองตามหมวดหมู่ด้วย ?category=")
    @GetMapping
    public List<Product> list(@RequestParam(required = false) String category) {
        return service.list(category);
    }

    @Operation(summary = "ค้นหาสินค้าจาก name หรือ sku (ไม่สนตัวพิมพ์เล็ก-ใหญ่)")
    @GetMapping("/search")
    public List<Product> search(@RequestParam(required = false) String keyword) {
        return service.search(keyword);
    }

    @Operation(summary = "ขายสินค้า (ตัดสต็อก)")
    @PostMapping("/sell")
    public SellProductResponse sell(@RequestBody SellProductRequest request) {
        return service.sell(request);
    }

    @Operation(summary = "อัพเดทราคาสินค้าเป็นชุด")
    @PutMapping("/bulk-price-update")
    public BulkPriceUpdateResponse bulkPriceUpdate(@RequestBody List<BulkPriceUpdateItem> items) {
        return service.bulkUpdatePrices(items);
    }
}
