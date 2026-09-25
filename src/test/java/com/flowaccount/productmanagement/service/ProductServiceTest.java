package com.flowaccount.productmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.flowaccount.productmanagement.dto.BulkPriceUpdateFailure;
import com.flowaccount.productmanagement.dto.BulkPriceUpdateItem;
import com.flowaccount.productmanagement.dto.BulkPriceUpdateResponse;
import com.flowaccount.productmanagement.dto.CreateProductRequest;
import com.flowaccount.productmanagement.dto.SellProductRequest;
import com.flowaccount.productmanagement.dto.SellProductResponse;
import com.flowaccount.productmanagement.exception.ApiValidationException;
import com.flowaccount.productmanagement.exception.ProductNotFoundException;
import com.flowaccount.productmanagement.model.Product;
import com.flowaccount.productmanagement.repository.InMemoryProductRepository;

/** Unit test ของ business logic ล้วนๆ ไม่ต้องเปิด Spring context */
class ProductServiceTest {

    private static final Instant NOW = Instant.parse("2025-11-17T10:30:00Z");

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(new InMemoryProductRepository(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Product createProduct(String name, String sku, String price, int stock, String category) {
        return service.create(new CreateProductRequest(name, sku, new BigDecimal(price), stock, category));
    }

    private Product createProduct(String sku, int stock) {
        return createProduct("ข้าวผัด", sku, "45.00", stock, "อาหาร");
    }

    private List<String> errorsOf(Runnable action) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(
                ApiValidationException.class, action::run).getErrors();
    }

    // ---------- create ----------

    @Test
    void create_assignsSequentialIdsAndCreatedAtFromClock() {
        Product first = createProduct("AAA001", 1);
        Product second = createProduct("AAA002", 1);

        assertThat(first.id()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(2);
        assertThat(first.createdAt()).isEqualTo(NOW);
    }

    @Test
    void create_trimsTextFields() {
        Product product = service.create(new CreateProductRequest("  ข้าวผัด ", " FOOD001  ", BigDecimal.TEN, 1, " อาหาร "));

        assertThat(product.name()).isEqualTo("ข้าวผัด");
        assertThat(product.sku()).isEqualTo("FOOD001");
        assertThat(product.category()).isEqualTo("อาหาร");
    }

    @Test
    void create_reportsAllInvalidFieldsInOrder() {
        List<String> errors = errorsOf(() ->
                service.create(new CreateProductRequest("", "AB", BigDecimal.ZERO, -1, "ยา")));

        assertThat(errors).containsExactly(
                Messages.NAME_REQUIRED,
                Messages.SKU_TOO_SHORT,
                Messages.PRICE_MUST_BE_POSITIVE,
                Messages.STOCK_MUST_NOT_BE_NEGATIVE,
                Messages.CATEGORY_INVALID);
    }

    @Test
    void create_reportsEveryMissingField() {
        List<String> errors = errorsOf(() ->
                service.create(new CreateProductRequest(null, null, null, null, null)));

        assertThat(errors).containsExactly(
                Messages.NAME_REQUIRED,
                Messages.SKU_REQUIRED,
                Messages.PRICE_REQUIRED,
                Messages.STOCK_REQUIRED,
                Messages.CATEGORY_INVALID);
    }

    @Test
    void create_blankSkuIsRequiredErrorNotTooShort() {
        List<String> errors = errorsOf(() ->
                service.create(new CreateProductRequest("ข้าวผัด", "   ", BigDecimal.TEN, 1, "อาหาร")));

        assertThat(errors).containsExactly(Messages.SKU_REQUIRED);
    }

    @Test
    void create_allowsZeroStock() {
        assertThat(createProduct("AAA001", 0).stock()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"อาหาร", "เครื่องดื่ม", "ของใช้", "เสื้อผ้า"})
    void create_acceptsAllFourCategories(String category) {
        assertThat(createProduct("ชื่อ", "AAA001", "10", 1, category).category()).isEqualTo(category);
    }

    @Test
    void create_rejectsDuplicateSkuCaseInsensitively() {
        createProduct("FOOD001", 1);

        assertThat(errorsOf(() -> createProduct("food001", 1))).containsExactly(Messages.SKU_DUPLICATE);
    }

    @Test
    void create_combinesDuplicateSkuWithOtherErrors() {
        createProduct("FOOD001", 1);

        List<String> errors = errorsOf(() ->
                service.create(new CreateProductRequest("", "FOOD001", BigDecimal.TEN, 1, "อาหาร")));

        assertThat(errors).containsExactly(Messages.NAME_REQUIRED, Messages.SKU_DUPLICATE);
    }

    @Test
    void create_concurrentRequestsWithSameSkuLetOnlyOneThrough() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> {
                try {
                    createProduct("FOOD001", 1);
                    return true;
                } catch (ApiValidationException e) {
                    return false;
                }
            });
        }

        assertThat(runConcurrently(tasks).stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(service.list(null)).hasSize(1);
    }

    // ---------- list / search ----------

    @Test
    void list_filtersByCategoryAndKeepsIdOrder() {
        createProduct("ข้าวผัด", "FOOD001", "45", 1, "อาหาร");
        createProduct("ชาเขียว", "DRINK001", "25", 1, "เครื่องดื่ม");
        createProduct("ข้าวมันไก่", "FOOD002", "50", 1, "อาหาร");

        assertThat(service.list(null)).extracting(Product::id).containsExactly(1L, 2L, 3L);
        assertThat(service.list("")).hasSize(3);
        assertThat(service.list("อาหาร")).extracting(Product::sku).containsExactly("FOOD001", "FOOD002");
        assertThat(service.list("ยา")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "ข้าว, 'FOOD001,FOOD002'",
            "drink, DRINK001",
            "SHIRT-BLUE, SHIRT-Blue",
            "basic, SHIRT-Blue",
            "001, 'FOOD001,DRINK001'",
            "ไม่มีของนี้, ''"
    })
    void search_matchesNameOrSkuCaseInsensitively(String keyword, String expectedSkus) {
        createProduct("ข้าวผัด", "FOOD001", "45", 1, "อาหาร");
        createProduct("ข้าวมันไก่", "FOOD002", "50", 1, "อาหาร");
        createProduct("ชาเขียว", "DRINK001", "25", 1, "เครื่องดื่ม");
        createProduct("เสื้อยืด Basic", "SHIRT-Blue", "199", 1, "เสื้อผ้า");

        List<String> expected = expectedSkus.isEmpty() ? List.of() : List.of(expectedSkus.split(","));

        assertThat(service.search(keyword)).extracting(Product::sku).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void search_requiresKeyword() {
        assertThat(errorsOf(() -> service.search(null))).containsExactly(Messages.KEYWORD_REQUIRED);
        assertThat(errorsOf(() -> service.search("  "))).containsExactly(Messages.KEYWORD_REQUIRED);
    }

    // ---------- sell ----------

    @Test
    void sell_reducesStockAndReturnsSummary() {
        Product product = createProduct("FOOD001", 20);

        SellProductResponse response = service.sell(new SellProductRequest(product.id(), 5));

        assertThat(response.quantitySold()).isEqualTo(5);
        assertThat(response.remainingStock()).isEqualTo(15);
        assertThat(response.unitPrice()).isEqualByComparingTo("45");
        assertThat(response.totalPrice()).isEqualByComparingTo("225");
        assertThat(service.list(null).getFirst().stock()).isEqualTo(15);
    }

    @Test
    void sell_allowsSellingExactlyTheRemainingStock() {
        Product product = createProduct("FOOD001", 3);

        service.sell(new SellProductRequest(product.id(), 3));

        assertThat(service.list(null).getFirst().stock()).isZero();
    }

    @Test
    void sell_rejectsNonPositiveQuantityBeforeLookingUpProduct() {
        // ลำดับตามโจทย์: quantity มาก่อน -> ต้องเป็น 400 (ValidationException) ไม่ใช่ 404
        assertThat(errorsOf(() -> service.sell(new SellProductRequest(999L, 0))))
                .containsExactly(Messages.QUANTITY_MUST_BE_POSITIVE);
        assertThat(errorsOf(() -> service.sell(new SellProductRequest(999L, -1))))
                .containsExactly(Messages.QUANTITY_MUST_BE_POSITIVE);
        assertThat(errorsOf(() -> service.sell(new SellProductRequest(999L, null))))
                .containsExactly(Messages.QUANTITY_MUST_BE_POSITIVE);
    }

    @Test
    void sell_requiresProductId() {
        assertThat(errorsOf(() -> service.sell(new SellProductRequest(null, 1))))
                .containsExactly(Messages.PRODUCT_ID_REQUIRED);
    }

    @Test
    void sell_unknownProductThrowsNotFound() {
        assertThatThrownBy(() -> service.sell(new SellProductRequest(999L, 1)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void sell_insufficientStockLeavesStockUntouched() {
        Product product = createProduct("FOOD001", 2);

        assertThat(errorsOf(() -> service.sell(new SellProductRequest(product.id(), 3))))
                .containsExactly(Messages.insufficientStock(2, 3));
        assertThat(service.list(null).getFirst().stock()).isEqualTo(2);
    }

    @Test
    void sell_concurrentSalesNeverOversell() throws Exception {
        Product product = createProduct("FOOD001", 50);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tasks.add(() -> {
                try {
                    service.sell(new SellProductRequest(product.id(), 1));
                    return true;
                } catch (ApiValidationException e) {
                    return false;
                }
            });
        }

        assertThat(runConcurrently(tasks).stream().filter(Boolean::booleanValue).count()).isEqualTo(50);
        assertThat(service.list(null).getFirst().stock()).isZero();
    }

    // ---------- bulk price update ----------

    @Test
    void bulkUpdate_appliesValidItemsAndReportsFailures() {
        Product a = createProduct("AAA001", 1);
        Product b = createProduct("AAA002", 1);

        BulkPriceUpdateResponse response = service.bulkUpdatePrices(java.util.Arrays.asList(
                new BulkPriceUpdateItem(a.id(), new BigDecimal("30")),
                new BulkPriceUpdateItem(999L, BigDecimal.TEN),          // ไม่พบสินค้า
                new BulkPriceUpdateItem(b.id(), BigDecimal.ZERO),       // ราคาไม่ถูกต้อง
                new BulkPriceUpdateItem(b.id(), null),                  // ไม่ระบุราคา
                new BulkPriceUpdateItem(null, BigDecimal.ONE),          // ไม่ระบุสินค้า
                null));                                                 // item เป็น null

        assertThat(response.totalRequested()).isEqualTo(6);
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(5);
        assertThat(response.failures()).containsExactly(
                new BulkPriceUpdateFailure(999L, Messages.PRODUCT_NOT_FOUND),
                new BulkPriceUpdateFailure(b.id(), Messages.PRICE_MUST_BE_POSITIVE),
                new BulkPriceUpdateFailure(b.id(), Messages.NEW_PRICE_REQUIRED),
                new BulkPriceUpdateFailure(null, Messages.PRODUCT_ID_REQUIRED),
                new BulkPriceUpdateFailure(null, Messages.BULK_ITEM_INVALID));
        assertThat(service.list(null)).extracting(Product::price)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("30"), new BigDecimal("45.00"));
    }

    @Test
    void bulkUpdate_requiresAtLeastOneItem() {
        assertThat(errorsOf(() -> service.bulkUpdatePrices(null))).containsExactly(Messages.BULK_ITEMS_REQUIRED);
        assertThat(errorsOf(() -> service.bulkUpdatePrices(List.of()))).containsExactly(Messages.BULK_ITEMS_REQUIRED);
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<T> results = new ArrayList<>();
            for (Future<T> future : pool.invokeAll(tasks)) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
