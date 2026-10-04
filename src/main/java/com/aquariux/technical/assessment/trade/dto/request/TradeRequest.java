package com.aquariux.technical.assessment.trade.dto.request;

import com.aquariux.technical.assessment.trade.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {
    private Long userId;
    private TradeType tradeType;
    

    @Schema(description = "Crypto pair to trade", example = "BTCUSDT")
    private String pairName;

    @Schema(description = "Amount of the base currency to buy or sell", example = "0.01")
    private BigDecimal quantity;

    @Schema(description = "Optional idempotency key, unique per user. Retrying with the same key returns the original trade")
    private String clientOrderId;
}