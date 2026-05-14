package psy.staybooking.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.CardPaymentConfirmRequest;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentConfirmFailResponse;
import psy.staybooking.payment.application.dto.PaymentConfirmSuccessResponse;
import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.dto.YpayPaymentConfirmRequest;

@Service
@RequiredArgsConstructor
public class PaymentApiClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${payment.api.base-url:http://localhost:8082}")
    private String baseUrl;

    public PaymentResponseDto confirmCard(PaymentContext paymentContext, CardPaymentDetailDto cardPaymentDetail) {
        return request(
            "/v1/payments/card/confirm",
            CardPaymentConfirmRequest.builder()
                .paymentToken(cardPaymentDetail.getPaymentToken())
                .orderId(String.valueOf(paymentContext.getBookingId()))
                .amount(paymentContext.getAmount())
                .build()
        );
    }

    public PaymentResponseDto confirmYpay(PaymentContext paymentContext, psy.staybooking.payment.application.dto.YpayPaymentDetailDto ypayPaymentDetail) {
        return request(
            "/v1/payments/ypay/confirm",
            YpayPaymentConfirmRequest.builder()
                .authorizationToken(ypayPaymentDetail.getAuthorizationToken())
                .orderId(String.valueOf(paymentContext.getBookingId()))
                .amount(paymentContext.getAmount())
                .build()
        );
    }

    private PaymentResponseDto request(String uri, Object requestBody) {
        try {
            return restClientBuilder
                .baseUrl(baseUrl)
                .build()
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange((request, response) -> mapResponse(response));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "외부 결제 연동에 실패했습니다.");
        }
    }

    private PaymentResponseDto mapResponse(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            PaymentConfirmSuccessResponse successResponse = objectMapper.readValue(response.getBody(), PaymentConfirmSuccessResponse.class);
            OffsetDateTime approvedAt = successResponse.getApprovedAt();
            return PaymentResponseDto.approved(
                successResponse.getProviderTransactionId(),
                approvedAt == null ? null : approvedAt.toLocalDateTime()
            );
        }

        PaymentConfirmFailResponse failResponse = objectMapper.readValue(response.getBody(), PaymentConfirmFailResponse.class);
        if (response.getStatusCode().is4xxClientError()) {
            return PaymentResponseDto.approvalFailed(failResponse.getCode(), failResponse.getMessage());
        }
        return PaymentResponseDto.providerUnavailable(failResponse.getCode(), failResponse.getMessage());
    }
}
