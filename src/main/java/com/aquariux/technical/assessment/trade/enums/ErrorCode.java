package com.aquariux.technical.assessment.trade.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    CRYPTO_PAIR_NOT_FOUND(HttpStatus.NOT_FOUND),
    CRYPTO_PAIR_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    TRADE_AMOUNT_TOO_SMALL(HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_CLIENT_ORDER_ID(HttpStatus.CONFLICT),
    PRICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    TRADE_CONFLICT(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
}
