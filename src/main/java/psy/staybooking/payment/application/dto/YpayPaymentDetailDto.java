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
public class YpayPaymentDetailDto implements PaymentDetailDto {

    private String authorizationToken;

    @Override
    public ExternalPaymentMethod getExternalPaymentMethod() {
        return ExternalPaymentMethod.YPAY;
    }

    @Override
    public void validate() {
        if (this.authorizationToken == null || this.authorizationToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Y페이 인증 토큰은 필수입니다.");
        }
    }
}
