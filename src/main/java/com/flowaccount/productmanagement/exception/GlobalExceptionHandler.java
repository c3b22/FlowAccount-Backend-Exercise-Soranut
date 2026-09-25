package com.flowaccount.productmanagement.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.flowaccount.productmanagement.dto.ErrorResponse;
import com.flowaccount.productmanagement.service.Messages;

/**
 * แปลงทุก error ให้เป็น format เดียวกันตามโจทย์: {"errors": ["..."]}
 *
 * สืบทอด ResponseEntityExceptionHandler เพื่อให้ error ของ Spring MVC เอง
 * (body พัง, ชนิดข้อมูลผิด, method/media type ไม่รองรับ, ไม่พบ path) เก็บ status code เดิมไว้
 * แต่ถูกเปลี่ยน body ให้เป็น format ของเรา
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ApiValidationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getErrors()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(Messages.INTERNAL_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(messageFor(statusCode)));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> Messages.INVALID_REQUEST_BODY;
            case 404 -> Messages.ENDPOINT_NOT_FOUND;
            case 405 -> Messages.METHOD_NOT_ALLOWED;
            case 415 -> Messages.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? Messages.INTERNAL_ERROR : Messages.INVALID_REQUEST_BODY;
        };
    }
}
