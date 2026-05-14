package psy.staybooking.booking.application.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import psy.staybooking.booking.application.dto.CheckoutTokenPayload;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.product.domain.Product;

@Service
public class CheckoutTokenProvider {

    private final Clock clock;
    private final String secret;
    private final long ttlSeconds;

    public CheckoutTokenProvider(
        Clock clock,
        @Value("${checkout.token.secret}") String secret,
        @Value("${checkout.token.ttl-seconds:180}") long ttlSeconds
    ) {
        this.clock = clock;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public String createCheckoutToken(Long userId, Product product) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }
        if (product == null || product.getProductId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 정보는 필수입니다.");
        }

        LocalDateTime issuedAt = LocalDateTime.now(clock);
        LocalDateTime expiresAt = issuedAt.plusSeconds(ttlSeconds);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("checkoutTokenId", UUID.randomUUID().toString())
            .claim("userId", userId)
            .claim("productId", product.getProductId())
            .claim("bookedPriceAmount", product.getPriceAmount())
            .issuedAt(Date.from(issuedAt.atZone(clock.getZone()).toInstant()))
            .expiration(Date.from(expiresAt.atZone(clock.getZone()).toInstant()))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    public CheckoutTokenPayload parseCheckoutToken(String checkoutToken) {
        if (checkoutToken == null || checkoutToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CHECKOUT_TOKEN);
        }

        try {
            var claims = Jwts.parser()
                .clock(() -> Date.from(LocalDateTime.now(clock).atZone(clock.getZone()).toInstant()))
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(checkoutToken)
                .getPayload();

            return CheckoutTokenPayload.builder()
                .checkoutTokenId(claims.get("checkoutTokenId", String.class))
                .userId(claims.get("userId", Long.class))
                .productId(claims.get("productId", Long.class))
                .bookedPriceAmount(claims.get("bookedPriceAmount", Long.class))
                .expiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), clock.getZone()))
                .build();
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.CHECKOUT_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_CHECKOUT_TOKEN);
        }
    }
}
