package com.aquariux.technical.assessment.trade.exception;

import com.aquariux.technical.assessment.trade.enums.ErrorCode;
import lombok.Getter;

@Getter
public class TradeException extends RuntimeException {

    private final ErrorCode errorCode;

    public TradeException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TradeException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
