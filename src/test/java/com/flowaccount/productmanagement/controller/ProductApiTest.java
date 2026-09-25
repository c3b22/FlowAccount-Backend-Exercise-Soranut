package com.flowaccount.productmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.flowaccount.productmanagement.repository.InMemoryProductRepository;
import com.flowaccount.productmanagement.service.Messages;

/** ทดสอบ HTTP contract จริงผ่าน Spring MVC: status code, รูปแบบ JSON, error format */
@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTest {

    private static final String FRIED_RICE = """
            {"name":"ข้าวผัด","sku":"FOOD001","price":45.00,"stock":20,"category":"อาหาร"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InMemoryProductRepository repository;

    @BeforeEach
    void resetStore() {
        repository.clear();
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions createProduct(String name, String sku, String price, int stock, String category) throws Exception {
        return postJson("/api/products", """
                {"name":"%s","sku":"%s","price":%s,"stock":%d,"category":"%s"}""".formatted(name, sku, price, stock, category));
    }

    // ---------- Challenge 1: POST /api/products ----------

    @Test
    void create_returns201WithProduct() throws Exception {
        postJson("/api/products", FRIED_RICE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("ข้าวผัด"))
                .andExpect(jsonPath("$.sku").value("FOOD001"))
                .andExpect(jsonPath("$.price").value(45.00))
                .andExpect(jsonPath("$.stock").value(20))
                .andExpect(jsonPath("$.category").value("อาหาร"))
                .andExpect(jsonPath("$.createdAt").value(endsWith("Z")));
    }

    @Test
    void create_responseContainsReadableThaiNotUnicodeEscapes() throws Exception {
        String raw = postJson("/api/products", FRIED_RICE).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(raw).contains("ข้าวผัด").doesNotContain("\\u0E");
    }

    @Test
    void create_invalidFieldsReturn400WithAllErrorsInOneResponse() throws Exception {
        postJson("/api/products", """
                {"name":"","sku":"AB","price":0,"stock":-1,"category":"ยา"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.contains(
                        Messages.NAME_REQUIRED,
                        Messages.SKU_TOO_SHORT,
                        Messages.PRICE_MUST_BE_POSITIVE,
                        Messages.STOCK_MUST_NOT_BE_NEGATIVE,
                        Messages.CATEGORY_INVALID)));
    }

    @Test
    void create_emptyObjectReportsEveryRequiredField() throws Exception {
        postJson("/api/products", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(5)));
    }

    @Test
    void create_duplicateSkuReturns400() throws Exception {
        createProduct("ข้าวผัด", "FOOD001", "45", 1, "อาหาร").andExpect(status().isCreated());

        createProduct("ข้าวมันไก่", "food001", "50", 1, "อาหาร")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.SKU_DUPLICATE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"name":"ข้าวผัด","sku":"FOOD001","price":"abc","stock":1,"category":"อาหาร"}""", // ชนิดข้อมูลผิด
            """
            {"name":"ข้าวผัด","sku":"FOOD001","price":45,"stock":1.5,"category":"อาหาร"}""", // stock ต้องเป็นจำนวนเต็ม
            """
            {"name": """, // JSON พัง
            "null",
            "[]"
    })
    void create_malformedBodyReturns400InStandardErrorFormat(String json) throws Exception {
        postJson("/api/products", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.INVALID_REQUEST_BODY));
    }

    // ---------- Challenge 2: GET /api/products ----------

    @Test
    void list_onEmptyStoreReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_returnsAllAndFiltersByCategory() throws Exception {
        createProduct("ข้าวผัด", "FOOD001", "45", 1, "อาหาร");
        createProduct("ชาเขียว", "DRINK001", "25", 1, "เครื่องดื่ม");
        createProduct("ข้าวมันไก่", "FOOD002", "50", 1, "อาหาร");

        mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(get("/api/products").param("category", "อาหาร"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sku").value("FOOD001"))
                .andExpect(jsonPath("$[1].sku").value("FOOD002"));

        mvc.perform(get("/api/products").param("category", "ยา"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- Challenge 3: POST /api/products/sell ----------

    @Test
    void sell_reducesStockAndReturns200() throws Exception {
        createProduct("ข้าวผัด", "FOOD001", "45.00", 20, "อาหาร");

        postJson("/api/products/sell", """
                {"productId":1,"quantity":5}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantitySold").value(5))
                .andExpect(jsonPath("$.remainingStock").value(15))
                .andExpect(jsonPath("$.totalPrice").value(225.00));

        mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$[0].stock").value(15));
    }

    @Test
    void sell_nonPositiveQuantityReturns400EvenIfProductDoesNotExist() throws Exception {
        postJson("/api/products/sell", """
                {"productId":999,"quantity":0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.QUANTITY_MUST_BE_POSITIVE));
    }

    @Test
    void sell_unknownProductReturns404() throws Exception {
        postJson("/api/products/sell", """
                {"productId":999,"quantity":1}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value(Messages.PRODUCT_NOT_FOUND));
    }

    @Test
    void sell_insufficientStockReturns400() throws Exception {
        createProduct("ข้าวผัด", "FOOD001", "45", 2, "อาหาร");

        postJson("/api/products/sell", """
                {"productId":1,"quantity":3}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.insufficientStock(2, 3)));
    }

    // ---------- Challenge 4: GET /api/products/search ----------

    @Test
    void search_matchesNameOrSkuCaseInsensitively() throws Exception {
        createProduct("ข้าวผัด", "FOOD001", "45", 1, "อาหาร");
        createProduct("ชาเขียว", "DRINK001", "25", 1, "เครื่องดื่ม");

        mvc.perform(get("/api/products/search").param("keyword", "ข้าว"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku").value("FOOD001"));

        mvc.perform(get("/api/products/search").param("keyword", "drink"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku").value("DRINK001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void search_withoutKeywordReturns400(String keyword) throws Exception {
        mvc.perform(get("/api/products/search").param("keyword", keyword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.KEYWORD_REQUIRED));

        mvc.perform(get("/api/products/search"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Challenge 5: PUT /api/products/bulk-price-update ----------

    @Test
    void bulkPriceUpdate_returnsSummaryWithPartialSuccess() throws Exception {
        createProduct("ข้าวผัด", "FOOD001", "45", 1, "อาหาร");

        mvc.perform(put("/api/products/bulk-price-update").contentType(MediaType.APPLICATION_JSON).content("""
                [{"productId":1,"newPrice":50},{"productId":9,"newPrice":1}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.failures[0].productId").value(9))
                .andExpect(jsonPath("$.failures[0].reason").value(Messages.PRODUCT_NOT_FOUND));

        mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$[0].price").value(50));
    }

    @Test
    void bulkPriceUpdate_emptyArrayReturns400() throws Exception {
        mvc.perform(put("/api/products/bulk-price-update").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.BULK_ITEMS_REQUIRED));
    }

    @Test
    void bulkPriceUpdate_bodyThatIsNotAnArrayReturns400() throws Exception {
        mvc.perform(put("/api/products/bulk-price-update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"newPrice\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(Messages.INVALID_REQUEST_BODY));
    }

    @Test
    void bulkPriceUpdate_nullItemIsReportedInsteadOfCrashing() throws Exception {
        mvc.perform(put("/api/products/bulk-price-update").contentType(MediaType.APPLICATION_JSON).content("[null]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failures[0].reason").value(Messages.BULK_ITEM_INVALID));
    }

    // ---------- error ของ Spring MVC เองก็ต้องอยู่ใน format เดียวกัน ----------

    @Test
    void unknownPathReturns404InStandardErrorFormat() throws Exception {
        mvc.perform(get("/api/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value(Messages.ENDPOINT_NOT_FOUND));
    }

    @Test
    void wrongHttpMethodReturns405InStandardErrorFormat() throws Exception {
        mvc.perform(put("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errors[0]").value(Messages.METHOD_NOT_ALLOWED));
    }

    @Test
    void nonJsonContentTypeReturns415InStandardErrorFormat() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errors[0]").value(Messages.UNSUPPORTED_MEDIA_TYPE));
    }
}
