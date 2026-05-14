package psy.staybooking.payment.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;

@Service
@RequiredArgsConstructor
public class MockCardPaymentStrategy implements PaymentStrategy {

    private final Clock clock;

    @Override
    public boolean supports(PaymentContext paymentContext) {
        return paymentContext.getExternalPaymentMethod() == ExternalPaymentMethod.CARD;
    }

    @Override
    public PaymentResponseDto confirmPayment(PaymentContext paymentContext) {
        if (!(paymentContext.getPaymentDetail() instanceof CardPaymentDetailDto cardPayment)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "카드 결제 상세 정보는 필수입니다.");
        }
        if (cardPayment.getPaymentToken() == null || cardPayment.getPaymentToken().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "카드 결제 토큰은 필수입니다.");
        }
        int installmentMonths = cardPayment.getInstallmentMonths() == null ? 0 : cardPayment.getInstallmentMonths();
        if (installmentMonths < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "할부 개월 수는 0 이상이어야 합니다.");
        }

        String paymentToken = cardPayment.getPaymentToken();
        if (paymentToken.startsWith("fail")) {
            return PaymentResponseDto.failed("MOCK_DECLINED", "모의 결제가 거절되었습니다.");
        }
        if (paymentToken.startsWith("error")) {
            return PaymentResponseDto.failed("MOCK_ERROR", "모의 결제 처리 중 오류가 발생했습니다.");
        }

        return PaymentResponseDto.approved(
            "mock-" + paymentContext.getPaymentId(),
            LocalDateTime.now(clock)
        );
    }
}
