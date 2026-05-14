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
import psy.staybooking.booking.application.dto.CheckoutTokenPayload;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.service.PaymentService;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.domain.PaymentStatus;
import psy.staybooking.point.application.service.PointService;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.booking.repository.BookingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingTransactionServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PointService pointService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-14T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private BookingTransactionService bookingTransactionService;

    @Test
    void createBookingByDatabaseCreatesPendingBookingWhenStockIsAvailable() {
        Product product = createOpenProduct();
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        payment.startProcessing();
        when(productRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(bookingRepository.countByProductIdAndStatuses(any(), any())).thenReturn(2L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            setBookingId(booking, 1L);
            return booking;
        });
        when(paymentService.createPayment(any(PaymentCreateDto.class))).thenReturn(payment);

        BookingCreateResult result = bookingTransactionService.createBookingByDatabase(
            BookingCreateRequest.builder()
                .productId(10L)
                .pointAmount(20_000L)
                .build(),
            CheckoutTokenPayload.builder()
                .checkoutTokenId("token-id")
                .bookedPriceAmount(100_000L)
                .build(),
            1L
        );

        assertThat(result.getBooking().getBookingId()).isEqualTo(1L);
        assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(pointService).holdPoint(1L, 1L, 20_000L);
    }

    @Test
    void createBookingByDatabaseThrowsWhenStockIsSoldOut() {
        Product product = createOpenProduct();
        when(productRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(bookingRepository.countByProductIdAndStatuses(any(), any())).thenReturn(10L);

        assertThatThrownBy(() -> bookingTransactionService.createBookingByDatabase(
            BookingCreateRequest.builder()
                .productId(10L)
                .pointAmount(0L)
                .build(),
            CheckoutTokenPayload.builder()
                .checkoutTokenId("token-id")
                .bookedPriceAmount(100_000L)
                .build(),
            1L
        )).isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOLD_OUT);
    }

    @Test
    void confirmBookingCommitsPointHoldAndConfirmsBooking() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        payment.startProcessing();
        payment.approve("mock-1", LocalDateTime.of(2026, 5, 14, 18, 0));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentService.succeedPayment(eq(1L), any(PaymentResponseDto.class)))
            .thenReturn(payment);

        BookingCreateResult result = bookingTransactionService.confirmBooking(
            1L,
            1L,
            1L,
            PaymentResponseDto.approved("mock-1", LocalDateTime.of(2026, 5, 14, 18, 0))
        );

        assertThat(result.getBooking().getStatus()).isEqualTo(psy.staybooking.booking.domain.BookingStatus.CONFIRMED);
        verify(pointService).commitHold(1L, 1L);
    }

    @Test
    void failBookingReleasesPointHoldAndFailsBooking() {
        Booking booking = Booking.createPending("B-001", 1L, 10L, "token-id", 100_000L);
        setBookingId(booking, 1L);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        payment.startProcessing();
        payment.fail("MOCK_DECLINED", "모의 결제가 거절되었습니다.");
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentService.failPayment(eq(1L), any(PaymentResponseDto.class)))
            .thenReturn(payment);

        BookingCreateResult result = bookingTransactionService.failBooking(
            1L,
            1L,
            1L,
            PaymentResponseDto.failed("MOCK_DECLINED", "모의 결제가 거절되었습니다.")
        );

        assertThat(result.getBooking().getStatus()).isEqualTo(psy.staybooking.booking.domain.BookingStatus.FAILED);
        verify(pointService).releaseHold(1L, 1L);
    }

    private Product createOpenProduct() {
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
        setProductId(product, 10L);
        return product;
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
}
