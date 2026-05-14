package psy.staybooking.payment.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.PaymentContext;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final List<PaymentStrategy> paymentStrategies;

    public PaymentStrategy getStrategy(PaymentContext paymentContext) {
        return paymentStrategies.stream()
            .filter(paymentStrategy -> paymentStrategy.supports(paymentContext))
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PAYMENT_PROCESSING_FAILED,
                "결제 전략을 찾을 수 없습니다."
            ));
    }
}
