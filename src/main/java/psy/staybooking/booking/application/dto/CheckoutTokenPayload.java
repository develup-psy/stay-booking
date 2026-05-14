package psy.staybooking.booking.application.dto;

import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    public static CheckoutTokenPayload from(Claims claims, ZoneId zoneId) {
        return CheckoutTokenPayload.builder()
            .checkoutTokenId(claims.get("checkoutTokenId", String.class))
            .userId(claims.get("userId", Long.class))
            .productId(claims.get("productId", Long.class))
            .bookedPriceAmount(claims.get("bookedPriceAmount", Long.class))
            .expiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), zoneId))
            .build();
    }
}
