package psy.staybooking.payment.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.dto.YpayPaymentDetailDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;

@Service
@RequiredArgsConstructor
public class MockYpayPaymentStrategy implements PaymentStrategy {

    private final Clock clock;

    @Override
    public boolean supports(PaymentContext paymentContext) {
        return paymentContext.getExternalPaymentMethod() == ExternalPaymentMethod.YPAY;
    }

    @Override
    public PaymentResponseDto confirmPayment(PaymentContext paymentContext) {
        if (!(paymentContext.getPaymentDetail() instanceof YpayPaymentDetailDto ypayPayment)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Y페이 결제 상세 정보는 필수입니다.");
        }
        if (ypayPayment.getAuthorizationToken() == null || ypayPayment.getAuthorizationToken().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Y페이 인증 토큰은 필수입니다.");
        }

        String authorizationToken = ypayPayment.getAuthorizationToken();
        if (authorizationToken.startsWith("fail")) {
            return PaymentResponseDto.failed("MOCK_DECLINED", "모의 결제가 거절되었습니다.");
        }
        if (authorizationToken.startsWith("error")) {
            return PaymentResponseDto.failed("MOCK_ERROR", "모의 결제 처리 중 오류가 발생했습니다.");
        }

        return PaymentResponseDto.approved(
            "mock-" + paymentContext.getPaymentId(),
            LocalDateTime.now(clock)
        );
    }
}
