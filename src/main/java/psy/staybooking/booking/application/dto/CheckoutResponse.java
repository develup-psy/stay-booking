package psy.staybooking.booking.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.product.domain.Product;
import psy.staybooking.product.domain.ProductSaleStatus;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

    private Long productId;
    private String productCode;
    private String productName;
    private long bookedPriceAmount;
    private int totalStock;
    private LocalDateTime saleOpenAt;
    private LocalDateTime saleCloseAt;
    private LocalDateTime checkInAt;
    private LocalDateTime checkOutAt;
    private ProductSaleStatus saleStatus;
    private long availablePoint;
    private String checkoutToken;

    public static CheckoutResponse from(Product product, ProductSaleStatus saleStatus, long availablePoint, String checkoutToken) {
        return CheckoutResponse.builder()
            .productId(product.getProductId())
            .productCode(product.getProductCode())
            .productName(product.getName())
            .bookedPriceAmount(product.getPriceAmount())
            .totalStock(product.getTotalStock())
            .saleOpenAt(product.getSaleOpenAt())
            .saleCloseAt(product.getSaleCloseAt())
            .checkInAt(product.getCheckInAt())
            .checkOutAt(product.getCheckOutAt())
            .saleStatus(saleStatus)
            .availablePoint(availablePoint)
            .checkoutToken(checkoutToken)
            .build();
    }
}
