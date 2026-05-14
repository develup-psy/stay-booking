package psy.staybooking.booking.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.booking.application.dto.BookingCreateRequest;
import psy.staybooking.booking.application.dto.BookingCreateResult;
import psy.staybooking.booking.application.dto.BookingResponse;
import psy.staybooking.booking.application.dto.CheckoutResponse;
import psy.staybooking.booking.application.dto.CheckoutTokenPayload;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.service.PaymentService;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.repository.PaymentRepository;
import psy.staybooking.point.application.service.PointService;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.domain.ProductSaleStatus;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.system.application.service.ModeService;
import psy.staybooking.system.domain.SystemModeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PointService pointService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private BookingStockService bookingStockService;

    @Mock
    private BookingTransactionService bookingTransactionService;

    @Mock
    private CheckoutTokenProvider checkoutTokenProvider;

    @Mock
    private ModeService modeService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-14T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private BookingService bookingService;

    @Test
    void getCheckoutReturnsCheckoutTokenWhenSaleIsOpen() {
        Product product = Product.create(
            "P-001",
            "seoul-stay",
            100_000L,
            10,
            LocalDateTime.of(2026, 5, 14, 17, 0),
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            LocalDateTime.of(2026, 6, 2, 11, 0)
        );
        setProductId(product, 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.REDIS_NORMAL);
        when(bookingStockService.getAvailableRedisStock(1L)).thenReturn(3L);
        when(pointService.getAvailablePoint(1L)).thenReturn(30_000L);
        when(checkoutTokenProvider.createCheckoutToken(1L, product)).thenReturn("checkout-token");

        CheckoutResponse response = bookingService.getCheckout(1L, 1L);

        assertThat(response.getSaleStatus()).isEqualTo(ProductSaleStatus.OPEN);
        assertThat(response.getAvailablePoint()).isEqualTo(30_000L);
        assertThat(response.getCheckoutToken()).isEqualTo("checkout-token");
    }

    @Test
    void getCheckoutSwitchesToDbFallbackWhenRedisAccessFails() {
        Product product = Product.create(
            "P-001",
            "seoul-stay",
            100_000L,
            10,
            LocalDateTime.of(2026, 5, 14, 17, 0),
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            LocalDateTime.of(2026, 6, 2, 11, 0)
        );
        setProductId(product, 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.REDIS_NORMAL);
        when(bookingStockService.getAvailableRedisStock(1L)).thenThrow(new RuntimeException("redis down"));
        when(bookingStockService.getAvailableDatabaseStock(1L, 10)).thenReturn(5L);
        when(pointService.getAvailablePoint(1L)).thenReturn(30_000L);
        when(checkoutTokenProvider.createCheckoutToken(1L, product)).thenReturn("checkout-token");

        CheckoutResponse response = bookingService.getCheckout(1L, 1L);

        assertThat(response.getSaleStatus()).isEqualTo(ProductSaleStatus.OPEN);
        verify(modeService).switchToDbFallback("redis checkout stock access failed", "booking-service");
    }

    @Test
    void createBookingReturnsExistingBookingWhenCheckoutTokenAlreadyUsed() {
        CheckoutTokenPayload checkoutTokenPayload = CheckoutTokenPayload.builder()
            .checkoutTokenId("token-id")
            .userId(1L)
            .productId(10L)
            .bookedPriceAmount(100_000L)
            .build();
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, psy.staybooking.payment.domain.ExternalPaymentMethod.CARD);
        payment.startProcessing();
        setPaymentId(payment, 1L);

        when(checkoutTokenProvider.parseCheckoutToken("checkout-token")).thenReturn(checkoutTokenPayload);
        when(bookingRepository.findByCheckoutTokenId("token-id")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(1L)).thenReturn(Optional.of(payment));

        BookingResponse response = bookingService.createBooking(
            BookingCreateRequest.builder()
                .productId(10L)
                .checkoutToken("checkout-token")
                .pointAmount(20_000L)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-token")
                    .installmentMonths(0)
                    .build())
                .build(),
            1L
        );

        assertThat(response.getBookingId()).isEqualTo(1L);
        assertThat(response.getPaymentStatus()).isEqualTo(payment.getStatus());
        verify(bookingStockService, never()).reserveRedisStock(any(), any());
    }

    @Test
    void createBookingConfirmsPointOnlyBooking() {
        CheckoutTokenPayload checkoutTokenPayload = CheckoutTokenPayload.builder()
            .checkoutTokenId("token-id")
            .userId(1L)
            .productId(10L)
            .bookedPriceAmount(100_000L)
            .build();
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 100_000L, null);
        setPaymentId(payment, 1L);
        Payment succeededPayment = Payment.create(1L, 100_000L, 100_000L, null);
        succeededPayment.markSucceeded();
        setPaymentId(succeededPayment, 1L);
        Booking confirmedBooking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(confirmedBooking, 1L);
        confirmedBooking.confirm(LocalDateTime.of(2026, 5, 14, 18, 0));

        when(checkoutTokenProvider.parseCheckoutToken("checkout-token")).thenReturn(checkoutTokenPayload);
        when(bookingRepository.findByCheckoutTokenId("token-id")).thenReturn(Optional.empty());
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.DB_FALLBACK);
        when(bookingTransactionService.createBookingByDatabase(any(), eq(checkoutTokenPayload), eq(1L))).thenReturn(
            BookingCreateResult.builder()
                .booking(booking)
                .payment(payment)
                .build()
        );
        when(bookingTransactionService.confirmBooking(1L, 1L, 1L, null)).thenReturn(
            BookingCreateResult.builder()
                .booking(confirmedBooking)
                .payment(succeededPayment)
                .build()
        );

        BookingResponse response = bookingService.createBooking(
            BookingCreateRequest.builder()
                .productId(10L)
                .checkoutToken("checkout-token")
                .pointAmount(100_000L)
                .build(),
            1L
        );

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getPaymentStatus()).isEqualTo(succeededPayment.getStatus());
    }

    @Test
    void createBookingFailsWhenExternalPaymentIsDeclined() {
        CheckoutTokenPayload checkoutTokenPayload = CheckoutTokenPayload.builder()
            .checkoutTokenId("token-id")
            .userId(1L)
            .productId(10L)
            .bookedPriceAmount(100_000L)
            .build();
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, psy.staybooking.payment.domain.ExternalPaymentMethod.CARD);
        payment.startProcessing();
        setPaymentId(payment, 1L);
        Payment failedPayment = Payment.create(1L, 100_000L, 20_000L, psy.staybooking.payment.domain.ExternalPaymentMethod.CARD);
        failedPayment.startProcessing();
        failedPayment.fail(ErrorCode.PAYMENT_APPROVAL_FAILED.getCode(), "모의 결제가 거절되었습니다.");
        setPaymentId(failedPayment, 1L);
        Booking failedBooking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(failedBooking, 1L);
        failedBooking.fail(ErrorCode.PAYMENT_APPROVAL_FAILED.getCode(), LocalDateTime.of(2026, 5, 14, 18, 0));

        when(checkoutTokenProvider.parseCheckoutToken("checkout-token")).thenReturn(checkoutTokenPayload);
        when(bookingRepository.findByCheckoutTokenId("token-id")).thenReturn(Optional.empty());
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.REDIS_NORMAL);
        when(bookingStockService.reserveRedisStock(10L, "token-id")).thenReturn(true);
        when(bookingTransactionService.createBooking(any(), eq(checkoutTokenPayload), eq(1L))).thenReturn(
            BookingCreateResult.builder()
                .booking(booking)
                .payment(payment)
                .build()
        );
        when(paymentService.processExternalPayment(eq(payment), any(PaymentCreateDto.class))).thenReturn(
            PaymentResponseDto.approvalFailed("MOCK_DECLINED", "모의 결제가 거절되었습니다.")
        );
        when(bookingTransactionService.failBooking(eq(1L), eq(1L), eq(1L), any(PaymentResponseDto.class)))
            .thenReturn(BookingCreateResult.builder().booking(failedBooking).payment(failedPayment).build());

        assertThatThrownBy(() -> bookingService.createBooking(
            BookingCreateRequest.builder()
                .productId(10L)
                .checkoutToken("checkout-token")
                .pointAmount(20_000L)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("fail-card-token")
                    .installmentMonths(0)
                    .build())
                .build(),
            1L
        )).isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_APPROVAL_FAILED);

        verify(bookingStockService).releaseRedisStock(10L, "token-id");
    }

    @Test
    void createBookingFallsBackToDatabaseWhenRedisReservationFails() {
        CheckoutTokenPayload checkoutTokenPayload = CheckoutTokenPayload.builder()
            .checkoutTokenId("token-id")
            .userId(1L)
            .productId(10L)
            .bookedPriceAmount(100_000L)
            .build();
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 100_000L, null);
        setPaymentId(payment, 1L);
        Payment succeededPayment = Payment.create(1L, 100_000L, 100_000L, null);
        succeededPayment.markSucceeded();
        setPaymentId(succeededPayment, 1L);
        Booking confirmedBooking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(confirmedBooking, 1L);
        confirmedBooking.confirm(LocalDateTime.of(2026, 5, 14, 18, 0));

        when(checkoutTokenProvider.parseCheckoutToken("checkout-token")).thenReturn(checkoutTokenPayload);
        when(bookingRepository.findByCheckoutTokenId("token-id")).thenReturn(Optional.empty());
        when(modeService.getCurrentMode()).thenReturn(SystemModeType.REDIS_NORMAL);
        when(bookingStockService.reserveRedisStock(10L, "token-id")).thenThrow(new RuntimeException("redis down"));
        when(bookingTransactionService.createBookingByDatabase(any(), eq(checkoutTokenPayload), eq(1L))).thenReturn(
            BookingCreateResult.builder()
                .booking(booking)
                .payment(payment)
                .build()
        );
        when(bookingTransactionService.confirmBooking(1L, 1L, 1L, null)).thenReturn(
            BookingCreateResult.builder()
                .booking(confirmedBooking)
                .payment(succeededPayment)
                .build()
        );

        BookingResponse response = bookingService.createBooking(
            BookingCreateRequest.builder()
                .productId(10L)
                .checkoutToken("checkout-token")
                .pointAmount(100_000L)
                .build(),
            1L
        );

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(modeService).switchToDbFallback("redis stock reservation failed", "booking-service");
    }

    private void setProductId(Product product, Long productId) {
        try {
            var field = Product.class.getDeclaredField("productId");
            field.setAccessible(true);
            field.set(product, productId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setBookingId(Booking booking, Long bookingId) {
        try {
            var field = Booking.class.getDeclaredField("bookingId");
            field.setAccessible(true);
            field.set(booking, bookingId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setPaymentId(Payment payment, Long paymentId) {
        try {
            var field = Payment.class.getDeclaredField("paymentId");
            field.setAccessible(true);
            field.set(payment, paymentId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
