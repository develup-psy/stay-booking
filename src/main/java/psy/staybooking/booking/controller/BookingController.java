package psy.staybooking.booking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psy.staybooking.booking.application.dto.BookingCreateRequest;
import psy.staybooking.booking.application.dto.BookingResponse;
import psy.staybooking.booking.application.dto.CheckoutResponse;
import psy.staybooking.booking.application.service.BookingService;
import psy.staybooking.common.api.ApiResponse;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/checkout/{productId}")
    public ResponseEntity<ApiResponse<CheckoutResponse>> getCheckout(
        @PathVariable Long productId,
        @RequestHeader("X-USER-ID") Long userId
    ) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 식별자는 필수입니다.");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }

        CheckoutResponse response = bookingService.getCheckout(productId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
        @RequestBody BookingCreateRequest request,
        @RequestHeader("X-USER-ID") Long userId
    ) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "예약 요청은 필수입니다.");
        }
        if (request.getProductId() == null || request.getProductId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 식별자는 필수입니다.");
        }

        BookingResponse response = bookingService.createBooking(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
