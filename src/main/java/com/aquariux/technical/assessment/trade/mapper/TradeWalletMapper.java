package com.aquariux.technical.assessment.trade.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface TradeWalletMapper {

    @Update("""
            UPDATE user_wallets
            SET balance = balance - #{amount}, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND symbol_id = #{symbolId} AND balance >= #{amount}
            """)
    int debit(@Param("userId") Long userId, @Param("symbolId") Long symbolId, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE user_wallets
            SET balance = balance + #{amount}, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND symbol_id = #{symbolId}
            """)
    int credit(@Param("userId") Long userId, @Param("symbolId") Long symbolId, @Param("amount") BigDecimal amount);

    @Insert("""
            INSERT INTO user_wallets (user_id, symbol_id, balance)
            VALUES (#{userId}, #{symbolId}, #{balance})
            """)
    void insertWallet(@Param("userId") Long userId, @Param("symbolId") Long symbolId, @Param("balance") BigDecimal balance);
}
