package psy.staybooking.booking.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import psy.staybooking.booking.application.dto.BookingCreateRequest;
import psy.staybooking.booking.application.dto.BookingCreateResult;
import psy.staybooking.booking.application.dto.BookingResponse;
import psy.staybooking.booking.application.dto.CheckoutResponse;
import psy.staybooking.booking.application.dto.CheckoutTokenPayload;
import psy.staybooking.booking.domain.Booking;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.service.PaymentService;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.repository.PaymentRepository;
import psy.staybooking.point.application.service.PointService;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.domain.ProductSaleStatus;
import psy.staybooking.product.repository.ProductRepository;
import psy.staybooking.booking.repository.BookingRepository;
import psy.staybooking.system.application.service.SystemModeService;
import psy.staybooking.system.domain.SystemModeType;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ProductRepository productRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PointService pointService;
    private final PaymentService paymentService;
    private final BookingStockService bookingStockService;
    private final BookingTransactionService bookingTransactionService;
    private final CheckoutTokenProvider checkoutTokenProvider;
    private final SystemModeService systemModeService;
    private final Clock clock;

    public CheckoutResponse getCheckout(Long productId, Long userId) {
        //1. 상품 조회 및 판매 가능 시간 검증
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품 정보를 찾을 수 없습니다."));

        ProductSaleStatus saleStatus = product.getSaleStatus(LocalDateTime.now(clock));
        if (saleStatus == ProductSaleStatus.BEFORE_OPEN) {
            throw new BusinessException(ErrorCode.PRODUCT_SALE_NOT_OPEN);
        }
        if (saleStatus == ProductSaleStatus.CLOSED) {
            throw new BusinessException(ErrorCode.PRODUCT_SALE_CLOSED);
        }

        //2. 운영 모드에 따라 현재 재고 확인
        SystemModeType currentMode = systemModeService.getCurrentMode();
        long availableStock;
        if (currentMode == SystemModeType.REDIS_NORMAL) {
            try {
                availableStock = bookingStockService.getAvailableRedisStock(productId);
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.BOOKING_STOCK_UNAVAILABLE) {
                    availableStock = bookingStockService.getAvailableDatabaseStock(productId, product.getTotalStock());
                } else {
                    throw exception;
                }
            } catch (RuntimeException exception) {
                systemModeService.switchToDbFallback("redis checkout stock access failed");
                availableStock = bookingStockService.getAvailableDatabaseStock(productId, product.getTotalStock());
            }
        } else if (currentMode == SystemModeType.DB_FALLBACK || currentMode == SystemModeType.RECOVERING) {
            availableStock = bookingStockService.getAvailableDatabaseStock(productId, product.getTotalStock());
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 운영 모드입니다.");
        }
        if (availableStock <= 0) {
            saleStatus = ProductSaleStatus.SOLD_OUT;
        } else {
            saleStatus = ProductSaleStatus.OPEN;
        }

        //3. 사용 가능 포인트 조회
        long availablePoint;
        try {
            availablePoint = pointService.getAvailablePoint(userId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                availablePoint = 0L;
            } else {
                throw exception;
            }
        }

        //4. 주문서 진입 토큰 발급
        String checkoutToken = checkoutTokenProvider.createCheckoutToken(userId, product);
        return CheckoutResponse.from(product, saleStatus, availablePoint, checkoutToken);
    }

    public BookingResponse createBooking(BookingCreateRequest request, Long userId) {
        //1. checkoutToken 검증
        CheckoutTokenPayload checkoutTokenPayload = checkoutTokenProvider.parseCheckoutToken(request.getCheckoutToken());

        if (!checkoutTokenPayload.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CHECKOUT_TOKEN_USER_MISMATCH);
        }
        if (!checkoutTokenPayload.getProductId().equals(request.getProductId())) {
            throw new BusinessException(ErrorCode.CHECKOUT_TOKEN_PRODUCT_MISMATCH);
        }

        //2. 동일 checkoutToken으로 이미 생성된 기존 예약 존재 여부 확인(DB 기준)
        Booking existingBooking = bookingRepository.findByCheckoutTokenId(checkoutTokenPayload.getCheckoutTokenId()).orElse(null);
        if (existingBooking != null) {
            //2-1. 있다면 예외가 아니라 기존 예약 결과 반환
            Payment existingPayment = paymentRepository.findByBookingId(existingBooking.getBookingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
            return BookingResponse.from(existingBooking, existingPayment);
        }

        //3. 운영 모드 확인 및 재고 확보 시도
        boolean reservedWithRedis = false;
        SystemModeType currentMode = systemModeService.getCurrentMode();
        BookingCreateResult bookingCreateResult = null;

        //4-A. REDIS_NORMAL이면 Redis 기준으로 재고와 중복 요청 흔적을 먼저 확인
        if (currentMode == SystemModeType.REDIS_NORMAL) {
            boolean reserved;
            try {
                reserved = bookingStockService.reserveRedisStock(request.getProductId(), checkoutTokenPayload.getCheckoutTokenId());
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.BOOKING_STOCK_UNAVAILABLE) {
                    currentMode = SystemModeType.DB_FALLBACK;
                    reserved = false;
                } else {
                    throw exception;
                }
            } catch (RuntimeException exception) {
                systemModeService.switchToDbFallback("redis stock reservation failed");
                currentMode = SystemModeType.DB_FALLBACK;
                reserved = false;
            }

            if (currentMode == SystemModeType.REDIS_NORMAL) {
                if (!reserved) {
                    //4-A-1. Redis holders에 흔적이 있으면 기존 결과를 재조회하고, 없으면 아직 처리 중인 요청으로 본다
                    Booking duplicatedBooking = bookingRepository.findByCheckoutTokenId(checkoutTokenPayload.getCheckoutTokenId()).orElse(null);
                    if (duplicatedBooking != null) {
                        Payment duplicatedPayment = paymentRepository.findByBookingId(duplicatedBooking.getBookingId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
                        return BookingResponse.from(duplicatedBooking, duplicatedPayment);
                    }

                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }

                    duplicatedBooking = bookingRepository.findByCheckoutTokenId(checkoutTokenPayload.getCheckoutTokenId()).orElse(null);
                    if (duplicatedBooking != null) {
                        Payment duplicatedPayment = paymentRepository.findByBookingId(duplicatedBooking.getBookingId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
                        return BookingResponse.from(duplicatedBooking, duplicatedPayment);
                    }

                    throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 처리 중인 예약 요청입니다.");
                }

                reservedWithRedis = true;

                //4-A-1. Redis 재고 확보 이후 TX-1로 booking, payment, point hold 생성
                try {
                    bookingCreateResult = bookingTransactionService.createBooking(request, checkoutTokenPayload, userId);
                } catch (DataIntegrityViolationException exception) {
                    Booking duplicatedBooking = bookingRepository.findByCheckoutTokenId(checkoutTokenPayload.getCheckoutTokenId())
                        .orElseThrow(() -> exception);
                    Payment duplicatedPayment = paymentRepository.findByBookingId(duplicatedBooking.getBookingId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
                    return BookingResponse.from(duplicatedBooking, duplicatedPayment);
                } catch (BusinessException exception) {
                    bookingStockService.releaseRedisStock(request.getProductId(), checkoutTokenPayload.getCheckoutTokenId());
                    throw exception;
                } catch (RuntimeException exception) {
                    bookingStockService.releaseRedisStock(request.getProductId(), checkoutTokenPayload.getCheckoutTokenId());
                    throw exception;
                }
            }
        }

        //4-B. DB_FALLBACK 또는 RECOVERING이면 DB 기준으로 TX-1 진행
        if (bookingCreateResult == null && (currentMode == SystemModeType.DB_FALLBACK || currentMode == SystemModeType.RECOVERING)) {
            try {
                bookingCreateResult = bookingTransactionService.createBookingByDatabase(request, checkoutTokenPayload, userId);
            } catch (DataIntegrityViolationException exception) {
                Booking duplicatedBooking = bookingRepository.findByCheckoutTokenId(checkoutTokenPayload.getCheckoutTokenId())
                    .orElseThrow(() -> exception);
                Payment duplicatedPayment = paymentRepository.findByBookingId(duplicatedBooking.getBookingId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
                return BookingResponse.from(duplicatedBooking, duplicatedPayment);
            }
        }

        if (bookingCreateResult == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 운영 모드입니다.");
        }

        //5. 포인트 전액 결제면 내부 상태만으로 확정
        if (bookingCreateResult.getPayment().isPointOnly()) {
            BookingCreateResult confirmedBooking = bookingTransactionService.confirmBooking(
                bookingCreateResult.getBooking().getBookingId(),
                bookingCreateResult.getPayment().getPaymentId(),
                userId,
                null
            );
            return BookingResponse.from(confirmedBooking.getBooking(), confirmedBooking.getPayment());
        }

        //6. 외부 결제 실행
        PaymentResponseDto paymentResponse = paymentService.processExternalPayment(
            bookingCreateResult.getPayment(),
            PaymentCreateDto.builder()
                .bookingId(bookingCreateResult.getBooking().getBookingId())
                .totalAmount(checkoutTokenPayload.getBookedPriceAmount())
                .pointAmount(request.getPointAmount())
                .paymentDetail(request.getPaymentDetail())
                .build()
        );

        //7. TX-2: 결제 성공 또는 실패 최종 확정
        if (paymentResponse.isApproved()) {
            BookingCreateResult confirmedBooking = bookingTransactionService.confirmBooking(
                bookingCreateResult.getBooking().getBookingId(),
                bookingCreateResult.getPayment().getPaymentId(),
                userId,
                paymentResponse
            );
            return BookingResponse.from(confirmedBooking.getBooking(), confirmedBooking.getPayment());
        }

        BookingCreateResult failedBooking = bookingTransactionService.failBooking(
            bookingCreateResult.getBooking().getBookingId(),
            bookingCreateResult.getPayment().getPaymentId(),
            userId,
            paymentResponse
        );
        if (reservedWithRedis) {
            bookingStockService.releaseRedisStock(request.getProductId(), checkoutTokenPayload.getCheckoutTokenId());
        }

        ErrorCode paymentErrorCode = ErrorCode.fromCode(failedBooking.getPayment().getLastErrorCode());
        if (paymentErrorCode == ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE || paymentErrorCode == ErrorCode.PAYMENT_PROCESSING_FAILED) {
            throw new BusinessException(paymentErrorCode, failedBooking.getPayment().getLastErrorMessage());
        }
        throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED, failedBooking.getPayment().getLastErrorMessage());
    }
}
