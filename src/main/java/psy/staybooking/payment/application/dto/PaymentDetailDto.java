package psy.staybooking.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import psy.staybooking.payment.domain.ExternalPaymentMethod;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "externalPaymentMethod")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CardPaymentDetailDto.class, name = "CARD"),
    @JsonSubTypes.Type(value = YpayPaymentDetailDto.class, name = "YPAY")
})
public interface PaymentDetailDto {

    ExternalPaymentMethod getExternalPaymentMethod();

    void validate();
}
