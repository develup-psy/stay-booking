package psy.staybooking.payment.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(PaymentCreateDto request) {
        ExternalPaymentMethod externalPaymentMethod = request.getExternalPaymentMethod();

        if (request.getPaymentDetail() == null && externalPaymentMethod != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "외부 결제 상세 정보가 필요합니다.");
        }
        if (request.getPaymentDetail() != null) {
            if (externalPaymentMethod != null && externalPaymentMethod != request.getPaymentDetail().getExternalPaymentMethod()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "외부 결제 수단과 결제 상세 정보가 일치하지 않습니다.");
            }
            externalPaymentMethod = request.getPaymentDetail().getExternalPaymentMethod();
        }
        if (request.getPointAmount() < request.getTotalAmount() && externalPaymentMethod == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "외부 결제 수단이 필요합니다.");
        }
        if (request.getPointAmount() == request.getTotalAmount() && request.getPaymentDetail() != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "외부 결제 수단과 결제 상세 정보가 일치하지 않습니다.");
        }

        Payment payment = paymentRepository.save(
            Payment.create(
                request.getBookingId(),
                request.getTotalAmount(),
                request.getPointAmount(),
                externalPaymentMethod
            )
        );

        if (!payment.isPointOnly()) {
            payment.startProcessing();
        }
        return payment;
    }

    @Transactional
    public Payment completePointOnlyPayment(Long paymentId) {
        Payment payment = getPayment(paymentId);
        payment.markSucceeded();
        return payment;
    }

    @Transactional
    public Payment succeedPayment(Long paymentId, PaymentResponseDto paymentResponse) {
        Payment payment = getPayment(paymentId);
        payment.recordApprovalSuccess(paymentResponse.getProviderTransactionId());
        payment.approve(paymentResponse.getProviderTransactionId(), paymentResponse.getApprovedAt());
        return payment;
    }

    @Transactional
    public Payment failPayment(Long paymentId, PaymentResponseDto paymentResponse) {
        Payment payment = getPayment(paymentId);
        payment.recordApprovalFailure(paymentResponse.getErrorMessage());
        payment.fail(paymentResponse.getErrorCode(), paymentResponse.getErrorMessage());
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }
}
