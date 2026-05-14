package psy.staybooking.booking.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.product.domain.Product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutTokenProviderTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T09:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final String secret = "stay-booking-checkout-token-secret-key-2026-seoul";

    @Test
    void createCheckoutTokenCreatesSignedToken() {
        CheckoutTokenProvider checkoutTokenProvider = new CheckoutTokenProvider(clock, secret, 180L);
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

        String checkoutToken = checkoutTokenProvider.createCheckoutToken(1L, product);
        var payload = checkoutTokenProvider.parseCheckoutToken(checkoutToken);

        assertThat(payload.getUserId()).isEqualTo(1L);
        assertThat(payload.getProductId()).isEqualTo(1L);
        assertThat(payload.getBookedPriceAmount()).isEqualTo(100_000L);
    }

    @Test
    void parseCheckoutTokenThrowsWhenTokenIsExpired() {
        CheckoutTokenProvider checkoutTokenProvider = new CheckoutTokenProvider(clock, secret, -1L);
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

        String checkoutToken = checkoutTokenProvider.createCheckoutToken(1L, product);

        assertThatThrownBy(() -> checkoutTokenProvider.parseCheckoutToken(checkoutToken))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void parseCheckoutTokenThrowsWhenTokenIsInvalid() {
        CheckoutTokenProvider checkoutTokenProvider = new CheckoutTokenProvider(clock, secret, 180L);

        assertThatThrownBy(() -> checkoutTokenProvider.parseCheckoutToken("invalid-token"))
            .isInstanceOf(BusinessException.class);
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
}
