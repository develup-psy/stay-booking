package psy.staybooking.payment.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.dto.YpayPaymentDetailDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProcessorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T09:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final MockCardPaymentStrategy cardPaymentStrategy = new MockCardPaymentStrategy(clock);
    private final MockYpayPaymentStrategy ypayPaymentStrategy = new MockYpayPaymentStrategy(clock);

    @Test
    void processPaymentDelegatesToCardStrategy() {
        PaymentStrategyFactory paymentStrategyFactory = new PaymentStrategyFactory(List.of(cardPaymentStrategy, ypayPaymentStrategy));
        PaymentProcessor paymentProcessor = new PaymentProcessor(paymentStrategyFactory);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);

        PaymentResponseDto result = paymentProcessor.processPayment(
            payment,
            PaymentCreateDto.builder()
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-token")
                    .installmentMonths(0)
                    .build())
                .build()
        );

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getProviderTransactionId()).isEqualTo("mock-" + payment.getPaymentId());
    }

    @Test
    void processPaymentThrowsWhenStrategyDoesNotExist() {
        PaymentStrategyFactory paymentStrategyFactory = new PaymentStrategyFactory(List.of(cardPaymentStrategy));
        PaymentProcessor paymentProcessor = new PaymentProcessor(paymentStrategyFactory);
        Payment payment = Payment.create(1L, 100_000L, 0L, ExternalPaymentMethod.YPAY);

        assertThatThrownBy(() -> paymentProcessor.processPayment(
            payment,
            PaymentCreateDto.builder()
                .paymentDetail(YpayPaymentDetailDto.builder()
                    .authorizationToken("ypay-token")
                    .build())
                .build()
        ))
            .isInstanceOf(BusinessException.class);
    }
}
