package psy.staybooking.booking.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.booking.application.dto.CheckoutResponse;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.point.application.service.PointService;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.domain.ProductSaleStatus;
import psy.staybooking.product.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ProductRepository productRepository;
    private final PointService pointService;
    private final CheckoutTokenProvider checkoutTokenProvider;
    private final Clock clock;

    public CheckoutResponse getCheckout(Long productId, Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "사용자 식별자는 필수입니다.");
        }

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품 정보를 찾을 수 없습니다."));

        product.validateSaleOpen(LocalDateTime.now(clock));

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

        String checkoutToken = checkoutTokenProvider.createCheckoutToken(userId, product);
        return CheckoutResponse.from(product, ProductSaleStatus.OPEN, availablePoint, checkoutToken);
    }
}
