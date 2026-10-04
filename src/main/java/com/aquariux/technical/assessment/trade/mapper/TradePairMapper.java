package com.aquariux.technical.assessment.trade.mapper;

import com.aquariux.technical.assessment.trade.dto.internal.CryptoPairDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TradePairMapper {

    @Select("""
            SELECT pair.id, pair.pair_name, pair.active,
                   base.id as baseSymbolId, base.symbol as baseSymbol,
                   quote.id as quoteSymbolId, quote.symbol as quoteSymbol
            FROM crypto_pairs pair
            INNER JOIN symbols base ON pair.base_symbol_id = base.id
            INNER JOIN symbols quote ON pair.quote_symbol_id = quote.id
            WHERE pair.pair_name = #{pairName}
            """)
    CryptoPairDto findByPairName(String pairName);
}
