package com.aquariux.technical.assessment.trade.controller;

import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:trade-controller-test", "trade.scheduling.enabled=false"})
@AutoConfigureMockMvc
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CryptoPriceMapper cryptoPriceMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM crypto_prices WHERE created_at > '2026-01-01 00:00:00'");
        insertPrice(1L, "50000.00", "50010.00");
    }

    @Test
    @Transactional
    void executeTrade_Buy_ShouldExecuteAtAskAndCreateWallet() throws Exception {
        // user 1 holds 10000 USDT only
        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 0.1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").isNumber())
                .andExpect(jsonPath("$.price").value(50010.00))
                .andExpect(jsonPath("$.totalAmount").value(5001.00))
                .andExpect(jsonPath("$.wallets[0].symbol").value("BTC"))
                .andExpect(jsonPath("$.wallets[0].balance").value(0.1))
                .andExpect(jsonPath("$.wallets[1].symbol").value("USDT"))
                .andExpect(jsonPath("$.wallets[1].balance").value(4999.00));

        assertThat(balance(1L, 1L)).isEqualByComparingTo("0.1");
        assertThat(balance(1L, 3L)).isEqualByComparingTo("4999.00");
    }

    @Test
    @Transactional
    void executeTrade_Sell_ShouldExecuteAtBid() throws Exception {
        // user 8 holds 45000 USDT and 1 BTC
        executeTrade("""
                {"userId": 8, "pairName": "BTCUSDT", "tradeType": "SELL", "quantity": 0.5}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(50000.00))
                .andExpect(jsonPath("$.totalAmount").value(25000.00));

        assertThat(balance(8L, 1L)).isEqualByComparingTo("0.5");
        assertThat(balance(8L, 3L)).isEqualByComparingTo("70000.00");
    }

    @Test
    @Transactional
    void executeTrade_WhenInsufficientBalance_ShouldRejectAndLeaveDataUntouched() throws Exception {
        int tradesBefore = tradeCount(1L);

        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 1}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        // user 1 has never held BTC
        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "SELL", "quantity": 0.1}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(balance(1L, 3L)).isEqualByComparingTo("10000.00");
        assertThat(tradeCount(1L)).isEqualTo(tradesBefore);
    }

    @Test
    @Transactional
    void executeTrade_WithSameClientOrderId_ShouldExecuteOnlyOnce() throws Exception {
        String body = """
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 0.01, "clientOrderId": "abc-1"}
                """;
        int tradesBefore = tradeCount(1L);

        executeTrade(body).andExpect(status().isOk());
        executeTrade(body).andExpect(status().isOk());

        assertThat(tradeCount(1L)).isEqualTo(tradesBefore + 1);
        assertThat(balance(1L, 3L)).isEqualByComparingTo("9499.90");

        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "SELL", "quantity": 0.01, "clientOrderId": "abc-1"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CLIENT_ORDER_ID"));
    }

    @Test
    @Transactional
    void executeTrade_WhenOnlySeedPricesExist_ShouldRejectAsPriceUnavailable() throws Exception {
        // ETHUSDT only has historical seed prices, which are not a current market price
        executeTrade("""
                {"userId": 1, "pairName": "ETHUSDT", "tradeType": "BUY", "quantity": 0.1}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRICE_UNAVAILABLE"));

        assertThat(balance(1L, 3L)).isEqualByComparingTo("10000.00");
    }

    @Test
    @Transactional
    void executeTrade_BuyThenSellOnCrossedPrice_ShouldNotProfit() throws Exception {
        // user 8 holds 45000 USDT and 1 BTC; bid is above ask
        insertPrice(1L, "50020.00", "50010.00");

        executeTrade("""
                {"userId": 8, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 0.1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(50020.00));
        executeTrade("""
                {"userId": 8, "pairName": "BTCUSDT", "tradeType": "SELL", "quantity": 0.1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(50010.00));

        assertThat(balance(8L, 3L)).isEqualByComparingTo("44999.00");
    }

    @Test
    void executeTrade_WithInvalidRequests_ShouldReturnErrorResponse() throws Exception {
        executeTrade("""
                {"userId": 1, "tradeType": "BUY"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("pairName is required"));

        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": -1}
                """)
                .andExpect(status().isBadRequest());

        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 0.000000001}
                """)
                .andExpect(status().isBadRequest());

        executeTrade("""
                {"userId": 1, "pairName": "BTCUSDT", "tradeType": "HOLD", "quantity": 1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        executeTrade("""
                {"userId": 999, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 1}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        executeTrade("""
                {"userId": 1, "pairName": "DOGEUSDT", "tradeType": "BUY", "quantity": 1}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CRYPTO_PAIR_NOT_FOUND"));
    }

    @Test
    void executeTrade_ConcurrentTradesForSameUser_ShouldNeverOverspend() throws Exception {
        // user 2 holds 25000 USDT: each buy costs 5001 USDT so only 4 of the 12 can succeed
        Callable<Integer> buy = () -> executeTrade("""
                {"userId": 2, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 0.1}
                """).andReturn().getResponse().getStatus();

        List<Integer> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
            List<Future<Integer>> futures = executor.invokeAll(IntStream.range(0, 12).mapToObj(i -> buy).toList());
            statuses = futures.stream().map(TradeControllerTest::get).toList();
        }

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(4);
        assertThat(statuses).filteredOn(status -> status == 422).hasSize(8);
        assertThat(balance(2L, 1L)).isEqualByComparingTo("0.4");
        assertThat(balance(2L, 3L)).isEqualByComparingTo("4996.00");
    }

    private ResultActions executeTrade(String body) throws Exception {
        return mockMvc.perform(post("/api/trades/execute").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void insertPrice(Long cryptoPairId, String bidPrice, String askPrice) {
        CryptoPrice price = new CryptoPrice();
        price.setCryptoPairId(cryptoPairId);
        price.setBidPrice(new BigDecimal(bidPrice));
        price.setAskPrice(new BigDecimal(askPrice));
        price.setBidSource("BINANCE");
        price.setAskSource("HUOBI");
        cryptoPriceMapper.insertPrice(price);
    }

    private BigDecimal balance(Long userId, Long symbolId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM user_wallets WHERE user_id = ? AND symbol_id = ?", BigDecimal.class, userId, symbolId);
    }

    private int tradeCount(Long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trades WHERE user_id = ?", Integer.class, userId);
    }

    private static Integer get(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
