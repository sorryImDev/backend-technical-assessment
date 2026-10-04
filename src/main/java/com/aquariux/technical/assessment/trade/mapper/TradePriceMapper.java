package com.aquariux.technical.assessment.trade.mapper;

import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TradePriceMapper {

    @Select("""
            SELECT id, crypto_pair_id, bid_price, ask_price, bid_source, ask_source, created_at
            FROM crypto_prices
            WHERE crypto_pair_id = #{cryptoPairId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    CryptoPrice findLatestByCryptoPairId(Long cryptoPairId);
}
