package psy.staybooking.stability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import psy.staybooking.booking.application.dto.BookingCreateRequest;
import psy.staybooking.booking.application.dto.BookingCreateResult;
import psy.staybooking.booking.application.dto.BookingResponse;
import psy.staybooking.booking.application.dto.CheckoutTokenPayload;
import psy.staybooking.booking.application.service.BookingService;
import psy.staybooking.booking.application.service.BookingStockService;
import psy.staybooking.booking.application.service.BookingTransactionService;
import psy.staybooking.booking.application.service.CheckoutTokenProvider;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.domain.PaymentStatus;
import psy.staybooking.payment.repository.PaymentRepository;
import psy.staybooking.point.domain.PointHoldStatus;
import psy.staybooking.point.domain.PointWallet;
import psy.staybooking.point.repository.PointWalletRepository;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.system.application.service.ModeService;
import psy.staybooking.system.application.service.RecoveryWorker;
import psy.staybooking.system.domain.SystemModeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class StabilityValidationIntegrationTest {

    private static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.0"))
        .withDatabaseName("stay_booking")
        .withUsername("stay_booking")
        .withPassword("stay_booking");

    private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);

    private static final MockWebServer PAYMENT_API_SERVER = new MockWebServer();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingStockService bookingStockService;

    @Autowired
    private BookingTransactionService bookingTransactionService;

    @Autowired
    private CheckoutTokenProvider checkoutTokenProvider;

    @Autowired
    private RecoveryWorker recoveryWorker;

    @Autowired
    private ModeService modeService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startInfrastructure() throws IOException {
        Startables.deepStart(MYSQL_CONTAINER, REDIS_CONTAINER).join();
        PAYMENT_API_SERVER.start();
        PAYMENT_API_SERVER.setDispatcher(new PaymentApiDispatcher());
    }

    @AfterAll
    static void stopInfrastructure() throws IOException {
        PAYMENT_API_SERVER.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
        registry.add("payment.api.base-url", () -> PAYMENT_API_SERVER.url("/").toString().replaceAll("/$", ""));
        registry.add("mode.health-checker.delay-ms", () -> "600000");
        registry.add("mode.health-checker.initial-delay-ms", () -> "600000");
        registry.add("recovery.orphan-stock.delay-ms", () -> "600000");
        registry.add("recovery.orphan-stock.initial-delay-ms", () -> "600000");
        registry.add("recovery.pending-payment.delay-ms", () -> "600000");
        registry.add("recovery.pending-payment.initial-delay-ms", () -> "600000");
        registry.add("recovery.redis-resync.delay-ms", () -> "600000");
        registry.add("recovery.redis-resync.initial-delay-ms", () -> "600000");
        registry.add("recovery.orphan-stock.stale-seconds", () -> "60");
        registry.add("recovery.pending-payment.timeout-seconds", () -> "60");
    }

    @BeforeEach
    void setUp() {
        clearDatabase();
        clearRedis();
    }

    @Test
    void hundred0ConcurrentRequestsSellExactly10BookingsInDbFallback() throws Exception {
        modeService.switchToDbFallback("concurrency-test", "integration-test");

        Product product = saveOpenProduct("P-CONCURRENCY", 100_000L, 10);
        int totalRequests = 1000;
        int beforeRequestCount = PAYMENT_API_SERVER.getRequestCount();

        ExecutorService executorService = Executors.newFixedThreadPool(64);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < totalRequests; i++) {
            final int requestNo = i;
            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                startLatch.await(10, TimeUnit.SECONDS);

                try {
                    return bookingService.createBooking(
                        BookingCreateRequest.builder()
                            .productId(product.getProductId())
                            .checkoutToken(checkoutTokenProvider.createCheckoutToken(1L, product))
                            .pointAmount(0L)
                            .paymentDetail(CardPaymentDetailDto.builder()
                                .paymentToken("card-success-" + requestNo)
                                .installmentMonths(0)
                                .build())
                            .build(),
                        1L
                    );
                } catch (BusinessException exception) {
                    return exception;
                }
            }));
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);

        int successCount = 0;
        int soldOutCount = 0;
        List<BusinessException> unexpectedFailures = new ArrayList<>();

        for (Future<Object> future : futures) {
            Object result = future.get(10, TimeUnit.SECONDS);
            if (result instanceof BookingResponse response) {
                successCount++;
                assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
                assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
                continue;
            }

            BusinessException exception = (BusinessException) result;
            if (exception.getErrorCode() == ErrorCode.SOLD_OUT) {
                soldOutCount++;
                continue;
            }
            unexpectedFailures.add(exception);
        }

        assertThat(successCount).isEqualTo(10);
        assertThat(soldOutCount).isEqualTo(990);
        assertThat(unexpectedFailures).isEmpty();
        assertThat(bookingRepository.count()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from bookings where status = 'CONFIRMED'",
            Long.class
        )).isEqualTo(10L);
        assertThat(PAYMENT_API_SERVER.getRequestCount() - beforeRequestCount).isEqualTo(10);
    }

    @Test
    void sameCheckoutTokenIsProcessedOnlyOnce() throws Exception {
        modeService.switchToDbFallback("idempotency-test", "integration-test");

        Product product = saveOpenProduct("P-IDEMPOTENT", 90_000L, 10);
        String checkoutToken = checkoutTokenProvider.createCheckoutToken(1L, product);
        int beforeRequestCount = PAYMENT_API_SERVER.getRequestCount();

        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CountDownLatch readyLatch = new CountDownLatch(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<BookingResponse>> futures = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                startLatch.await(10, TimeUnit.SECONDS);
                return bookingService.createBooking(
                    BookingCreateRequest.builder()
                        .productId(product.getProductId())
                        .checkoutToken(checkoutToken)
                        .pointAmount(0L)
                        .paymentDetail(CardPaymentDetailDto.builder()
                            .paymentToken("card-idempotent")
                            .installmentMonths(0)
                            .build())
                        .build(),
                    1L
                );
            }));
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        List<BookingResponse> responses = new ArrayList<>();
        for (Future<BookingResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }

        assertThat(responses).hasSize(20);
        assertThat(responses).extracting(BookingResponse::getBookingId).containsOnly(1L);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(PAYMENT_API_SERVER.getRequestCount() - beforeRequestCount).isEqualTo(1);
    }

    @Test
    void redisStockUnavailableFallsBackToDatabasePath() {
        modeService.switchToRedisNormal("fallback-test", "integration-test");

        Product product = saveOpenProduct("P-FALLBACK", 80_000L, 1);

        BookingResponse response = bookingService.createBooking(
            BookingCreateRequest.builder()
                .productId(product.getProductId())
                .checkoutToken(checkoutTokenProvider.createCheckoutToken(1L, product))
                .pointAmount(0L)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-fallback")
                    .installmentMonths(0)
                    .build())
                .build(),
            1L
        );

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(modeService.getCurrentMode()).isEqualTo(SystemModeType.REDIS_NORMAL);
        assertThat(stringRedisTemplate.opsForValue().get("stock:product:" + product.getProductId() + ":remaining")).isNull();
    }

    @Test
    void paymentFailureRestoresRedisStockAndReleasesPointHold() {
        modeService.switchToRedisNormal("payment-failure-test", "integration-test");

        Product product = saveOpenProduct("P-PAYMENT-FAIL", 70_000L, 1);
        PointWallet pointWallet = pointWalletRepository.save(PointWallet.create(1L, 50_000L));
        bookingStockService.syncRedisStock(product.getProductId(), 1L, Map.of());
        String checkoutToken = checkoutTokenProvider.createCheckoutToken(1L, product);
        String checkoutTokenId = checkoutTokenProvider.parseCheckoutToken(checkoutToken).getCheckoutTokenId();

        assertThatThrownBy(() -> bookingService.createBooking(
            BookingCreateRequest.builder()
                .productId(product.getProductId())
                .checkoutToken(checkoutToken)
                .pointAmount(20_000L)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("fail-card-token")
                    .installmentMonths(0)
                    .build())
                .build(),
            1L
        )).isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.PAYMENT_APPROVAL_FAILED);

        Booking failedBooking = bookingRepository.findByCheckoutTokenId(checkoutTokenId).orElseThrow();
        Payment failedPayment = paymentRepository.findByBookingId(failedBooking.getBookingId()).orElseThrow();
        PointWallet updatedWallet = pointWalletRepository.findWalletWithHoldsByUserId(1L).orElseThrow();

        assertThat(failedBooking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedWallet.getTotalAmount()).isEqualTo(pointWallet.getTotalAmount());
        assertThat(updatedWallet.getAvailableAmount()).isEqualTo(pointWallet.getTotalAmount());
        assertThat(updatedWallet.getHolds()).singleElement().extracting("status").isEqualTo(PointHoldStatus.RELEASED);
        assertThat(bookingStockService.getAvailableRedisStock(product.getProductId())).isEqualTo(1L);
        assertThat(bookingStockService.hasRedisHolder(product.getProductId(), checkoutTokenId)).isFalse();
    }

    @Test
    void recoveryWorkerRestoresOrphanStockAndTimedOutPendingPayment() {
        modeService.switchToRedisNormal("recovery-test", "integration-test");

        Product product = saveOpenProduct("P-RECOVERY", 3_000L, 2);
        bookingStockService.syncRedisStock(
            product.getProductId(),
            0L,
            Map.of(
                "orphan-token", String.valueOf(System.currentTimeMillis() - 120_000),
                "pending-token", String.valueOf(System.currentTimeMillis() - 120_000)
            )
        );

        pointWalletRepository.save(PointWallet.create(9L, 10_000L));

        BookingCreateResult pendingBooking = bookingTransactionService.createBooking(
            BookingCreateRequest.builder()
                .productId(product.getProductId())
                .pointAmount(1_000L)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-pending")
                    .installmentMonths(0)
                    .build())
                .build(),
            CheckoutTokenPayload.builder()
                .checkoutTokenId("pending-token")
                .userId(9L)
                .productId(product.getProductId())
                .bookedPriceAmount(product.getPriceAmount())
                .build(),
            9L
        );

        jdbcTemplate.update(
            "update bookings set created_at = ? where booking_id = ?",
            LocalDateTime.now().minusSeconds(120),
            pendingBooking.getBooking().getBookingId()
        );

        recoveryWorker.recoverOrphanStockAllocations();
        recoveryWorker.recoverTimedOutPendingPayments();

        Booking recoveredBooking = bookingRepository.findById(pendingBooking.getBooking().getBookingId()).orElseThrow();
        Payment recoveredPayment = paymentRepository.findByBookingId(recoveredBooking.getBookingId()).orElseThrow();
        PointWallet recoveredWallet = pointWalletRepository.findWalletWithHoldsByUserId(9L).orElseThrow();

        assertThat(bookingStockService.getAvailableRedisStock(product.getProductId())).isEqualTo(2L);
        assertThat(bookingStockService.hasRedisHolder(product.getProductId(), "orphan-token")).isFalse();
        assertThat(bookingStockService.hasRedisHolder(product.getProductId(), "pending-token")).isFalse();
        assertThat(recoveredBooking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(recoveredPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(recoveredWallet.getAvailableAmount()).isEqualTo(10_000L);
        assertThat(recoveredWallet.getHolds()).singleElement().extracting("status").isEqualTo(PointHoldStatus.RELEASED);
    }

    @Test
    void recoveryWorkerResyncsRedisStockAndReturnsToRedisNormal() {
        Product product = saveOpenProduct("P-RESYNC", 5_000L, 3);

        Booking pendingBooking = bookingRepository.save(
            Booking.createPending("B-PENDING", 1L, product.getProductId(), "pending-token", product.getPriceAmount())
        );
        Booking confirmedBooking = bookingRepository.save(
            Booking.createPending("B-CONFIRMED", 2L, product.getProductId(), "confirmed-token", product.getPriceAmount())
        );
        confirmedBooking.confirm(LocalDateTime.now());

        bookingStockService.syncRedisStock(product.getProductId(), 99L, Map.of("stale-token", "1"));
        modeService.switchToRecovering("redis-resync-test", "integration-test");

        recoveryWorker.resyncRedisStock();

        Map<String, String> holders = bookingStockService.getRedisHolders(product.getProductId());

        assertThat(modeService.getCurrentMode()).isEqualTo(SystemModeType.REDIS_NORMAL);
        assertThat(bookingStockService.getAvailableRedisStock(product.getProductId())).isEqualTo(1L);
        assertThat(holders).containsKeys("pending-token", "confirmed-token");
        assertThat(holders).doesNotContainKey("stale-token");
    }

    private Product saveOpenProduct(String productCode, long priceAmount, int totalStock) {
        LocalDateTime now = LocalDateTime.now();
        return productRepository.save(
            Product.create(
                productCode,
                "stay-" + productCode,
                priceAmount,
                totalStock,
                now.minusDays(1),
                now.plusDays(1),
                now.plusDays(10),
                now.plusDays(11)
            )
        );
    }

    private void clearDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE payment_logs");
        jdbcTemplate.execute("TRUNCATE TABLE payments");
        jdbcTemplate.execute("TRUNCATE TABLE point_holds");
        jdbcTemplate.execute("TRUNCATE TABLE point_wallets");
        jdbcTemplate.execute("TRUNCATE TABLE bookings");
        jdbcTemplate.execute("TRUNCATE TABLE products");
        jdbcTemplate.execute("TRUNCATE TABLE system_modes");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void clearRedis() {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb(RedisServerCommands.FlushOption.SYNC);
            return null;
        });
    }

    private static class PaymentApiDispatcher extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            try {
                JsonNode requestBody = OBJECT_MAPPER.readTree(request.getBody().readUtf8());
                String path = request.getPath();

                if ("/v1/payments/card/confirm".equals(path)) {
                    return confirmCard(requestBody);
                }
                if ("/v1/payments/ypay/confirm".equals(path)) {
                    return confirmYpay(requestBody);
                }
                return new MockResponse().setResponseCode(404);
            } catch (IOException exception) {
                return new MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"code\":\"MOCK_500\",\"message\":\"mock payment server error\"}");
            }
        }

        private MockResponse confirmCard(JsonNode requestBody) {
            String paymentToken = requestBody.path("paymentToken").asText();
            String orderId = requestBody.path("orderId").asText();
            long amount = requestBody.path("amount").asLong();

            if (paymentToken.startsWith("fail")) {
                return new MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":\"CARD_DECLINED\",\"message\":\"카드 결제가 거절되었습니다.\"}");
            }

            if (paymentToken.startsWith("error")) {
                return new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":\"PG_UNAVAILABLE\",\"message\":\"외부 결제 연동에 실패했습니다.\"}");
            }

            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    "{\"providerTransactionId\":\"card-" + UUID.randomUUID()
                        + "\",\"orderId\":\"" + orderId
                        + "\",\"status\":\"SUCCEEDED\",\"approvedAt\":\"2026-05-14T18:00:00+09:00\",\"method\":\"CARD\",\"totalAmount\":"
                        + amount + "}"
                );
        }

        private MockResponse confirmYpay(JsonNode requestBody) {
            String authorizationToken = requestBody.path("authorizationToken").asText();
            String orderId = requestBody.path("orderId").asText();
            long amount = requestBody.path("amount").asLong();

            if (authorizationToken.startsWith("fail")) {
                return new MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":\"YPAY_DECLINED\",\"message\":\"Y페이 결제가 거절되었습니다.\"}");
            }

            if (authorizationToken.startsWith("error")) {
                return new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":\"PG_UNAVAILABLE\",\"message\":\"외부 결제 연동에 실패했습니다.\"}");
            }

            return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    "{\"providerTransactionId\":\"ypay-" + UUID.randomUUID()
                        + "\",\"orderId\":\"" + orderId
                        + "\",\"status\":\"SUCCEEDED\",\"approvedAt\":\"2026-05-14T18:00:00+09:00\",\"method\":\"YPAY\",\"totalAmount\":"
                        + amount + "}"
                );
        }
    }
}
