package psy.staybooking.payment.application.dto;

import psy.staybooking.payment.domain.ExternalPaymentMethod;

public interface PaymentDetailDto {

    ExternalPaymentMethod getExternalPaymentMethod();
}
