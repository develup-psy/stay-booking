package psy.staybooking.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import psy.staybooking.common.api.ApiResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.failure(errorCode, exception.getMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        return ResponseEntity
            .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
            .body(ApiResponse.failure(ErrorCode.INVALID_INPUT_VALUE, extractValidationMessage(exception)));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity
            .status(ErrorCode.INVALID_PARAMETER.getStatus())
            .body(ApiResponse.failure(ErrorCode.INVALID_PARAMETER));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception exception) {
        log.error("예상하지 못한 예외가 발생했습니다.", exception);
        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private String extractValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return extractFieldMessage(methodArgumentNotValidException.getBindingResult().getFieldError());
        }

        if (exception instanceof BindException bindException) {
            return extractFieldMessage(bindException.getBindingResult().getFieldError());
        }

        return ErrorCode.INVALID_INPUT_VALUE.getMessage();
    }

    private String extractFieldMessage(FieldError fieldError) {
        if (fieldError == null || fieldError.getDefaultMessage() == null) {
            return ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        return fieldError.getDefaultMessage();
    }
}
