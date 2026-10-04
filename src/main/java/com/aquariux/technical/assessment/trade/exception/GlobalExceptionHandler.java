package com.aquariux.technical.assessment.trade.exception;

import com.aquariux.technical.assessment.trade.dto.response.ErrorResponse;
import com.aquariux.technical.assessment.trade.enums.ErrorCode;
import com.aquariux.technical.assessment.trade.enums.LogEvent;
import com.aquariux.technical.assessment.trade.logging.LogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TradeException.class)
    public ResponseEntity<Object> handleTradeException(TradeException e, WebRequest request) {
        return buildResponse(e, e.getErrorCode().getStatus(), e.getErrorCode().name(), e.getMessage(), request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrencyFailure(ConcurrencyFailureException e, WebRequest request) {
        ErrorCode errorCode = ErrorCode.TRADE_CONFLICT;
        return buildResponse(e, errorCode.getStatus(), errorCode.name(),
                "Another trade is in progress for this user, please retry", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e, WebRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return buildResponse(e, errorCode.getStatus(), errorCode.name(), "Unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        String code = status.is4xxClientError() ? ErrorCode.INVALID_REQUEST.name() : ErrorCode.INTERNAL_ERROR.name();
        String message = status.is4xxClientError() ? "Malformed or unsupported request" : "Unexpected error occurred";
        ResponseEntity<Object> response = buildResponse(e, status, code, message, request);
        return ResponseEntity.status(status).headers(headers).body(response.getBody());
    }

    private ResponseEntity<Object> buildResponse(Exception e, HttpStatusCode status, String code, String message,
                                                 WebRequest request) {
        String path = request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : null;

        String logLine = LogFormatter.format(LogEvent.ERR_RESP, "endpoint=" + path + " status=" + LogFormatter.status(status)
                + " error=" + code + " exception=" + e.getClass().getSimpleName() + " detail=" + e.getMessage());
        if (ErrorCode.INTERNAL_ERROR.name().equals(code)) {
            log.error(logLine, e);
        } else {
            log.warn(logLine);
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(LocalDateTime.now(), status.value(), code, message, path));
    }
}
