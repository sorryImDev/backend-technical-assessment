package com.aquariux.technical.assessment.trade.dto.internal;

import lombok.Data;

@Data
public class CryptoPairDto {
    private Long id;
    private String pairName;
    private Boolean active;
    private Long baseSymbolId;
    private String baseSymbol;
    private Long quoteSymbolId;
    private String quoteSymbol;
}
