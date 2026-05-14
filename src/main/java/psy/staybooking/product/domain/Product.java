package psy.staybooking.product.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.common.persistence.BaseTimeEntity;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    private String productCode;

    private String name;

    private long priceAmount;

    private int totalStock;

    private LocalDateTime saleOpenAt;

    private LocalDateTime saleCloseAt;

    private LocalDateTime checkInAt;

    private LocalDateTime checkOutAt;

    @Builder(access = AccessLevel.PACKAGE)
    Product(
        Long productId,
        String productCode,
        String name,
        long priceAmount,
        int totalStock,
        LocalDateTime saleOpenAt,
        LocalDateTime saleCloseAt,
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt
    ) {
        this.productId = productId;
        this.productCode = productCode;
        this.name = name;
        this.priceAmount = priceAmount;
        this.totalStock = totalStock;
        this.saleOpenAt = saleOpenAt;
        this.saleCloseAt = saleCloseAt;
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
    }

    public static Product create(
        String productCode,
        String name,
        long priceAmount,
        int totalStock,
        LocalDateTime saleOpenAt,
        LocalDateTime saleCloseAt,
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt
    ) {
        if (productCode == null || productCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 코드는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 이름은 필수입니다.");
        }
        if (priceAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "상품 가격은 0보다 커야 합니다.");
        }
        if (totalStock <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "총 재고는 1 이상이어야 합니다.");
        }
        if (saleOpenAt == null || checkInAt == null || checkOutAt == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "상품 판매/숙박 시간 정보는 필수입니다.");
        }

        return Product.builder()
            .productCode(productCode)
            .name(name)
            .priceAmount(priceAmount)
            .totalStock(totalStock)
            .saleOpenAt(saleOpenAt)
            .saleCloseAt(saleCloseAt)
            .checkInAt(checkInAt)
            .checkOutAt(checkOutAt)
            .build();
    }
}
