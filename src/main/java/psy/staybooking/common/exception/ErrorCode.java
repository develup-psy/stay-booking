package psy.staybooking.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_401", "잘못된 요청 파라미터입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_402", "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 자원을 찾을 수 없습니다."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "COMMON_409", "상태 전이가 올바르지 않습니다."),
    INVALID_CHECKOUT_TOKEN(HttpStatus.BAD_REQUEST, "CHECKOUT_400", "체크아웃 토큰이 올바르지 않습니다."),
    CHECKOUT_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "CHECKOUT_401", "체크아웃 토큰이 만료되었습니다."),
    PRODUCT_SALE_NOT_OPEN(HttpStatus.BAD_REQUEST, "BOOKING_400", "아직 판매 시작 전인 상품입니다."),
    PRODUCT_SALE_CLOSED(HttpStatus.BAD_REQUEST, "BOOKING_401", "판매가 종료된 상품입니다."),
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "POINT_400", "보유 포인트가 부족합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
