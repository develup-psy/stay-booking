package psy.staybooking.payment.application.service;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.dto.YpayPaymentDetailDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.domain.PaymentStatus;
import psy.staybooking.payment.repository.PaymentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentTransactionService paymentTransactionService;

    @Test
    void createPaymentMarksPointOnlyPaymentSucceeded() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentTransactionService.createPayment(
            PaymentCreateDto.builder()
                .bookingId(1L)
                .totalAmount(50_000L)
                .pointAmount(50_000L)
                .build()
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createPaymentStartsExternalPaymentProcessing() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentTransactionService.createPayment(
            PaymentCreateDto.builder()
                .bookingId(1L)
                .totalAmount(100_000L)
                .pointAmount(20_000L)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-token")
                    .installmentMonths(0)
                    .build())
                .build()
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getLogs()).hasSize(2);
    }

    @Test
    void createPaymentThrowsWhenMethodAndDetailDoNotMatch() {
        assertThatThrownBy(() -> paymentTransactionService.createPayment(
            PaymentCreateDto.builder()
                .bookingId(1L)
                .totalAmount(100_000L)
                .pointAmount(0L)
                .externalPaymentMethod(ExternalPaymentMethod.CARD)
                .paymentDetail(YpayPaymentDetailDto.builder()
                    .authorizationToken("ypay-token")
                    .build())
                .build()
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void succeedPaymentApprovesPayment() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        payment.startProcessing();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Payment result = paymentTransactionService.succeedPayment(
            1L,
            PaymentResponseDto.approved("mock-1", LocalDateTime.of(2026, 5, 14, 18, 0))
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.getLogs().get(result.getLogs().size() - 2).getEventType()).isEqualTo(psy.staybooking.payment.domain.PaymentEventType.APPROVAL_SUCCEEDED);
    }

    @Test
    void completePointOnlyPaymentMarksPaymentSucceeded() {
        Payment payment = Payment.create(1L, 50_000L, 50_000L, null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Payment result = paymentTransactionService.completePointOnlyPayment(1L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void failPaymentFailsPayment() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        payment.startProcessing();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Payment result = paymentTransactionService.failPayment(
            1L,
            PaymentResponseDto.failed("DECLINED", "card declined")
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getLogs().get(result.getLogs().size() - 2).getEventType()).isEqualTo(psy.staybooking.payment.domain.PaymentEventType.APPROVAL_FAILED);
    }
}
