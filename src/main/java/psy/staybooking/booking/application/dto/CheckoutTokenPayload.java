package psy.staybooking.booking.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutTokenPayload {

    private String checkoutTokenId;
    private Long userId;
    private Long productId;
    private long bookedPriceAmount;
    private LocalDateTime expiresAt;
}
