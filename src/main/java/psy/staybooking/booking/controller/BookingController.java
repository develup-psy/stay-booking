package psy.staybooking.booking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psy.staybooking.booking.application.dto.CheckoutResponse;
import psy.staybooking.booking.application.service.BookingService;
import psy.staybooking.common.api.ApiResponse;

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
        CheckoutResponse response = bookingService.getCheckout(productId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
