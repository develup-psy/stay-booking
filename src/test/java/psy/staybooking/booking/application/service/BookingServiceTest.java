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
import psy.staybooking.booking.application.dto.CheckoutResponse;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.point.application.service.PointService;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.domain.ProductSaleStatus;
import psy.staybooking.product.repository.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PointService pointService;

    @Mock
    private CheckoutTokenProvider checkoutTokenProvider;

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
        when(pointService.getAvailablePoint(1L)).thenReturn(30_000L);
        when(checkoutTokenProvider.createCheckoutToken(1L, product)).thenReturn("checkout-token");

        CheckoutResponse response = bookingService.getCheckout(1L, 1L);

        assertThat(response.getSaleStatus()).isEqualTo(ProductSaleStatus.OPEN);
        assertThat(response.getAvailablePoint()).isEqualTo(30_000L);
        assertThat(response.getCheckoutToken()).isEqualTo("checkout-token");
    }

    @Test
    void getCheckoutThrowsWhenSaleIsBeforeOpen() {
        Product product = Product.create(
            "P-001",
            "seoul-stay",
            100_000L,
            10,
            LocalDateTime.of(2026, 5, 14, 19, 0),
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            LocalDateTime.of(2026, 6, 2, 11, 0)
        );
        setProductId(product, 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> bookingService.getCheckout(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_SALE_NOT_OPEN);
    }

    @Test
    void getCheckoutThrowsWhenSaleIsClosed() {
        Product product = Product.create(
            "P-001",
            "seoul-stay",
            100_000L,
            10,
            LocalDateTime.of(2026, 5, 14, 16, 0),
            LocalDateTime.of(2026, 5, 14, 17, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            LocalDateTime.of(2026, 6, 2, 11, 0)
        );
        setProductId(product, 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> bookingService.getCheckout(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_SALE_CLOSED);
    }

    @Test
    void getCheckoutReturnsZeroWhenPointWalletDoesNotExist() {
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
        when(pointService.getAvailablePoint(1L)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        when(checkoutTokenProvider.createCheckoutToken(1L, product)).thenReturn("checkout-token");

        CheckoutResponse response = bookingService.getCheckout(1L, 1L);

        assertThat(response.getAvailablePoint()).isEqualTo(0L);
    }

    @Test
    void getCheckoutThrowsWhenProductDoesNotExist() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getCheckout(1L, 1L))
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
