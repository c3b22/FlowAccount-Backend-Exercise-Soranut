# Product Management API (Spring Boot)

โจทย์ FlowAccount Engineering Workshop — Track A (Backend) ระบบจัดการสินค้าสำหรับร้านค้า SME
พัฒนาด้วย **Java 21 + Spring Boot 4.1** เก็บข้อมูลใน memory

## วิธีรัน

ต้องมี JDK 21 (ไม่ต้องติดตั้ง Maven — โปรเจกต์มี Maven Wrapper ให้)

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
./mvnw test                   # รันเทสต์ทั้งหมด
```

API อยู่ที่ <http://localhost:8080>

- Swagger UI (เอกสาร API + ปุ่มลองยิง request): <http://localhost:8080/swagger-ui.html>
- ตัวอย่าง request ทั้งหมดอยู่ใน [`requests.http`](requests.http)

> ข้อมูลอยู่ใน memory จึงหายเมื่อปิดโปรแกรม

## Endpoints

| Challenge | Method & Path | สำเร็จ | ผิดพลาด |
|---|---|---|---|
| 1 | `POST /api/products` | `201` + สินค้าที่สร้าง | `400` `{ "errors": [...] }` |
| 2 | `GET /api/products[?category=อาหาร]` | `200` + list | — |
| 3 | `POST /api/products/sell` | `200` + สรุปการขาย | `400` จำนวน/สต็อกไม่พอ, `404` ไม่พบสินค้า |
| 4 | `GET /api/products/search?keyword=ข้าว` | `200` + list | `400` ถ้าไม่ระบุ keyword |
| 5 | `PUT /api/products/bulk-price-update` | `200` + summary | `400` ถ้า array ว่าง |

ทุก error (รวมถึง error ของ Spring เอง เช่น JSON พัง, path ไม่มี, method ผิด) ใช้ format เดียวกัน:
`{ "errors": ["ข้อความภาษาไทย", ...] }`

### ตัวอย่าง

```jsonc
// POST /api/products
{ "name": "ข้าวผัด", "sku": "FOOD001", "price": 45.00, "stock": 20, "category": "อาหาร" }
// 201 Created
{ "id": 1, "name": "ข้าวผัด", "sku": "FOOD001", "price": 45.00, "stock": 20,
  "category": "อาหาร", "createdAt": "2026-09-25T03:20:50.186762500Z" }

// POST /api/products/sell
{ "productId": 1, "quantity": 5 }
// 200 OK
{ "productId": 1, "name": "ข้าวผัด", "sku": "FOOD001", "quantitySold": 5,
  "unitPrice": 45.00, "totalPrice": 225.00, "remainingStock": 15 }

// PUT /api/products/bulk-price-update
[ { "productId": 1, "newPrice": 50 }, { "productId": 9, "newPrice": 1 } ]
// 200 OK
{ "totalRequested": 2, "updatedCount": 1, "failedCount": 1,
  "failures": [ { "productId": 9, "reason": "ไม่พบสินค้า" } ] }
```

## การตัดสินใจในการออกแบบ (ส่วนที่โจทย์ไม่ได้ระบุ)

- **SKU ซ้ำ** เทียบแบบไม่สนตัวพิมพ์เล็ก-ใหญ่ และตัดช่องว่างหัวท้าย (`FOOD001` = `food001`) ตอบ `400` รวมกับ error อื่นในรอบเดียว
- **Validation แบบเขียนเอง** (`ProductValidator`) แทน Bean Validation annotation เพราะต้องการให้ error
  เรียงตามลำดับ field คงที่ ได้ข้อความไทยตามโจทย์ และรวม error "SKU ซ้ำ" (ซึ่งต้องเช็กกับข้อมูลใน store) ไว้ใน list เดียวกัน
- **`/sell`** ตรวจตามลำดับของโจทย์: `quantity > 0` (400) → ไม่พบสินค้า (404) → สต็อกไม่พอ (400)
- **`GET ?category=`** ถ้าหมวดหมู่ไม่ตรงกับที่กำหนด คืน list ว่าง (`200 []`) ไม่ใช่ error
- **Bulk update** เป็นแบบ partial success: รายการที่ถูกต้องอัพเดทจริง รายการที่ผิด (ไม่พบสินค้า / ราคา ≤ 0 / ข้อมูลไม่ครบ) ถูกข้ามและรายงานเหตุผลใน `failures`
- **ราคา** ใช้ `BigDecimal` ไม่ใช้ `double` เพื่อไม่ให้เกิดความคลาดเคลื่อนของทศนิยมกับเงิน
- **ปิดการปัดทศนิยมเป็นจำนวนเต็ม** (`accept-float-as-int=false`): `"stock": 1.5` ต้องได้ 400 ไม่ใช่ถูกแปลงเป็น 1 เงียบๆ

## โครงสร้าง

```
src/main/java/com/flowaccount/productmanagement
├── controller/   ProductController          — routing เท่านั้น
├── service/      ProductService             — business logic + lock
│                 ProductValidator, Messages — กฎ validation และข้อความไทย
├── repository/   ProductRepository (interface), InMemoryProductRepository
├── exception/    GlobalExceptionHandler     — แปลงทุก error เป็น {"errors": [...]}
├── model/        Product (immutable record), Categories
├── dto/          request / response
└── config/       AppConfig (Clock bean)
src/test/java/...
├── service/ProductServiceTest               — unit test ของ logic + concurrency (ไม่เปิด Spring)
└── controller/ProductApiTest                — ทดสอบ HTTP contract ผ่าน MockMvc
```

### Concurrency & ประสิทธิภาพ

- `Product` เป็น immutable record → การอ่าน (GET/search) **ไม่ต้อง lock** และไม่ block กัน
- การเขียนทั้งหมด (สร้าง / ขาย / อัพเดทราคา) ผ่าน `ReentrantLock` ตัวเดียว ทำให้งาน check-then-act เป็น atomic
  ไม่ขายเกินสต็อก และไม่มี SKU ซ้ำแม้ยิงพร้อมกันหลาย request (มีเทสต์ยืนยันทั้งสองกรณี)
- ใช้ `ReentrantLock` แทน `synchronized` และเปิด `spring.threads.virtual.enabled=true`
  เพื่อให้ทำงานกับ virtual threads ของ Java 21 ได้ดี ไม่เกิด thread pinning
- เช็ก SKU ซ้ำด้วย index ใน O(1)

### ถ้าจะทำเป็นระบบจริง

เปลี่ยน `InMemoryProductRepository` เป็น Spring Data JPA + database: ใช้ unique index บน `sku`
และตัดสต็อกด้วย `UPDATE product SET stock = stock - :q WHERE id = :id AND stock >= :q` (atomic ระดับ DB)
เพื่อให้ทำงานถูกต้องเมื่อรันหลาย instance
