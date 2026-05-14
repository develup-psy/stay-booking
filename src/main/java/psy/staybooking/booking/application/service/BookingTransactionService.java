package psy.staybooking.booking.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psy.staybooking.booking.application.dto.BookingCreateRequest;
import psy.staybooking.booking.application.dto.BookingCreateResult;
import psy.staybooking.booking.application.dto.CheckoutTokenPayload;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.booking.domain.BookingStatus;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.service.PaymentService;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.point.application.service.PointService;

@Service
@RequiredArgsConstructor
public class BookingTransactionService {

    private final BookingRepository bookingRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final PointService pointService;
    private final Clock clock;

    @Transactional
    public BookingCreateResult createBooking(BookingCreateRequest request, CheckoutTokenPayload checkoutTokenPayload, Long userId) {
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품 정보를 찾을 수 없습니다."));

        product.validateSaleOpen(LocalDateTime.now(clock));

        Booking booking = bookingRepository.save(
            Booking.createPending(
                "B-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                userId,
                request.getProductId(),
                checkoutTokenPayload.getCheckoutTokenId(),
                checkoutTokenPayload.getBookedPriceAmount()
            )
        );

        if (request.getPointAmount() > 0) {
            pointService.holdPoint(userId, booking.getBookingId(), request.getPointAmount());
        }

        Payment payment = paymentService.createPayment(
            PaymentCreateDto.builder()
                .bookingId(booking.getBookingId())
                .totalAmount(checkoutTokenPayload.getBookedPriceAmount())
                .pointAmount(request.getPointAmount())
                .paymentDetail(request.getPaymentDetail())
                .build()
        );

        return BookingCreateResult.builder()
            .booking(booking)
            .payment(payment)
            .build();
    }

    @Transactional
    public BookingCreateResult createBookingByDatabase(BookingCreateRequest request, CheckoutTokenPayload checkoutTokenPayload, Long userId) {
        Product product = productRepository.findByProductIdForUpdate(request.getProductId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품 정보를 찾을 수 없습니다."));

        product.validateSaleOpen(LocalDateTime.now(clock));

        long activeBookingCount = bookingRepository.countByProductIdAndStatuses(
            request.getProductId(),
            List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED)
        );

        if (activeBookingCount >= product.getTotalStock()) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }

        Booking booking = bookingRepository.save(
            Booking.createPending(
                "B-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                userId,
                request.getProductId(),
                checkoutTokenPayload.getCheckoutTokenId(),
                checkoutTokenPayload.getBookedPriceAmount()
            )
        );

        if (request.getPointAmount() > 0) {
            pointService.holdPoint(userId, booking.getBookingId(), request.getPointAmount());
        }

        Payment payment = paymentService.createPayment(
            PaymentCreateDto.builder()
                .bookingId(booking.getBookingId())
                .totalAmount(checkoutTokenPayload.getBookedPriceAmount())
                .pointAmount(request.getPointAmount())
                .paymentDetail(request.getPaymentDetail())
                .build()
        );

        return BookingCreateResult.builder()
            .booking(booking)
            .payment(payment)
            .build();
    }

    @Transactional
    public BookingCreateResult confirmBooking(Long bookingId, Long paymentId, Long userId, PaymentResponseDto paymentResponse) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "예약 정보를 찾을 수 없습니다."));

        if (paymentResponse == null) {
            Payment payment = paymentService.getPayment(paymentId);
            if (payment.getPointAmount() > 0) {
                pointService.commitHold(userId, bookingId);
            }
            payment = paymentService.completePointOnlyPayment(paymentId);
            booking.confirm(LocalDateTime.now(clock));
            return BookingCreateResult.builder()
                .booking(booking)
                .payment(payment)
                .build();
        }

        Payment payment = paymentService.succeedPayment(paymentId, paymentResponse);

        if (payment.getPointAmount() > 0) {
            pointService.commitHold(userId, bookingId);
        }

        booking.confirm(LocalDateTime.now(clock));

        return BookingCreateResult.builder()
            .booking(booking)
            .payment(payment)
            .build();
    }

    @Transactional
    public BookingCreateResult failBooking(Long bookingId, Long paymentId, Long userId, PaymentResponseDto paymentResponse) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "예약 정보를 찾을 수 없습니다."));

        Payment payment = paymentService.failPayment(paymentId, paymentResponse);

        if (payment.getPointAmount() > 0) {
            pointService.releaseHold(userId, bookingId);
        }

        booking.fail(
            payment.getLastErrorCode() == null || payment.getLastErrorCode().isBlank() ? ErrorCode.PAYMENT_APPROVAL_FAILED.getCode() : payment.getLastErrorCode(),
            LocalDateTime.now(clock)
        );

        return BookingCreateResult.builder()
            .booking(booking)
            .payment(payment)
            .build();
    }
}
