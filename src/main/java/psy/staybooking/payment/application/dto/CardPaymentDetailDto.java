package psy.staybooking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.domain.ExternalPaymentMethod;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentDetailDto implements PaymentDetailDto {

    private String paymentToken;
    private Integer installmentMonths;

    @Override
    public ExternalPaymentMethod getExternalPaymentMethod() {
        return ExternalPaymentMethod.CARD;
    }

    @Override
    public void validate() {
        if (this.paymentToken == null || this.paymentToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "카드 결제 토큰은 필수입니다.");
        }

        int installmentMonths = this.installmentMonths == null ? 0 : this.installmentMonths;
        if (installmentMonths < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "할부 개월 수는 0 이상이어야 합니다.");
        }
    }
}
