package psy.staybooking.payment.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;

@Service
@RequiredArgsConstructor
public class CardPaymentStrategy implements PaymentStrategy {

    private final PaymentApiClient paymentApiClient;

    @Override
    public boolean supports(PaymentContext paymentContext) {
        return paymentContext.getExternalPaymentMethod() == ExternalPaymentMethod.CARD;
    }

    @Override
    public PaymentResponseDto confirmPayment(PaymentContext paymentContext) {
        if (!(paymentContext.getPaymentDetail() instanceof CardPaymentDetailDto cardPayment)) {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_FAILED, "카드 결제 상세 정보가 올바르지 않습니다.");
        }
        return paymentApiClient.confirmCard(paymentContext, cardPayment);
    }
}
