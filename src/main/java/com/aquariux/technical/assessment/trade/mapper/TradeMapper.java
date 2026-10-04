package com.aquariux.technical.assessment.trade.mapper;

import com.aquariux.technical.assessment.trade.entity.Trade;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TradeMapper {

    @Insert("""
            INSERT INTO trades (user_id, crypto_pair_id, trade_type, quantity, price, total_amount, trade_time, client_order_id)
            VALUES (#{trade.userId}, #{trade.cryptoPairId}, #{trade.tradeType}, #{trade.quantity}, #{trade.price},
                    #{trade.totalAmount}, #{trade.tradeTime}, #{clientOrderId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "trade.id")
    void insertTrade(@Param("trade") Trade trade, @Param("clientOrderId") String clientOrderId);

    @Select("""
            SELECT id, user_id, crypto_pair_id, trade_type, quantity, price, total_amount, trade_time
            FROM trades
            WHERE user_id = #{userId} AND client_order_id = #{clientOrderId}
            """)
    Trade findByUserIdAndClientOrderId(@Param("userId") Long userId, @Param("clientOrderId") String clientOrderId);
}