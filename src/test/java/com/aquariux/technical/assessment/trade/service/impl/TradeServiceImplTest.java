package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.config.TradeProperties;
import com.aquariux.technical.assessment.trade.dto.internal.CryptoPairDto;
import com.aquariux.technical.assessment.trade.dto.internal.UserWalletDto;
import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.enums.ErrorCode;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.exception.TradeException;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.TradePairMapper;
import com.aquariux.technical.assessment.trade.mapper.TradePriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeWalletMapper;
import com.aquariux.technical.assessment.trade.mapper.UserMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long BTC_ID = 1L;
    private static final Long USDT_ID = 3L;

    @Mock
    private TradeMapper tradeMapper;
    @Mock
    private UserWalletMapper userWalletMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TradePairMapper tradePairMapper;
    @Mock
    private TradePriceMapper tradePriceMapper;
    @Mock
    private TradeWalletMapper tradeWalletMapper;

    private TradeServiceImpl tradeService;

    private CryptoPairDto btcPair;
    private CryptoPrice btcPrice;
    private TradeRequest request;

    @BeforeEach
    void setUp() {
        tradeService = new TradeServiceImpl(tradeMapper, userWalletMapper, userMapper, tradePairMapper, tradePriceMapper,
                tradeWalletMapper, new TradeProperties(Duration.ofSeconds(30)));

        btcPair = new CryptoPairDto();
        btcPair.setId(1L);
        btcPair.setPairName("BTCUSDT");
        btcPair.setActive(true);
        btcPair.setBaseSymbolId(BTC_ID);
        btcPair.setBaseSymbol("BTC");
        btcPair.setQuoteSymbolId(USDT_ID);
        btcPair.setQuoteSymbol("USDT");

        btcPrice = new CryptoPrice();
        btcPrice.setCryptoPairId(1L);
        btcPrice.setBidPrice(new BigDecimal("50000.00"));
        btcPrice.setAskPrice(new BigDecimal("50100.00"));
        btcPrice.setCreatedAt(LocalDateTime.now());

        request = new TradeRequest();
        request.setUserId(USER_ID);
        request.setPairName("BTCUSDT");
        request.setTradeType(TradeType.BUY);
        request.setQuantity(new BigDecimal("0.1"));
    }

    @Test
    void executeTrade_Buy_ShouldUseAskPriceAndUpdateWallets() {

        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, USDT_ID, new BigDecimal("5010.00000000"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, BTC_ID, new BigDecimal("0.1"))).thenReturn(1);
        givenWalletBalances("0.1", "4990.00");

        TradeResponse result = tradeService.executeTrade(request);

        assertThat(result.getPairName()).isEqualTo("BTCUSDT");
        assertThat(result.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(result.getPrice()).isEqualByComparingTo("50100.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("5010.00");
        assertThat(result.getWallets()).hasSize(2);
        assertThat(result.getWallets().get(0).getSymbol()).isEqualTo("BTC");
        assertThat(result.getWallets().get(1).getBalance()).isEqualByComparingTo("4990.00");

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insertTrade(tradeCaptor.capture(), isNull());
        Trade trade = tradeCaptor.getValue();
        assertThat(trade.getUserId()).isEqualTo(USER_ID);
        assertThat(trade.getCryptoPairId()).isEqualTo(1L);
        assertThat(trade.getTradeType()).isEqualTo("BUY");
        assertThat(trade.getQuantity()).isEqualByComparingTo("0.1");
        assertThat(trade.getPrice()).isEqualByComparingTo("50100.00");
        assertThat(trade.getTotalAmount()).isEqualByComparingTo("5010.00");
        assertThat(trade.getTradeTime()).isNotNull();
        verify(tradeWalletMapper, never()).insertWallet(anyLong(), anyLong(), any());
    }

    @Test
    void executeTrade_Sell_ShouldUseBidPriceAndUpdateWallets() {

        request.setTradeType(TradeType.SELL);
        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, BTC_ID, new BigDecimal("0.1"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, USDT_ID, new BigDecimal("5000.00000000"))).thenReturn(1);
        givenWalletBalances("0.4", "15000.00");

        TradeResponse result = tradeService.executeTrade(request);

        assertThat(result.getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(result.getPrice()).isEqualByComparingTo("50000.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("5000.00");
        verify(tradeMapper).insertTrade(any(Trade.class), isNull());
    }

    @Test
    void executeTrade_BuyFirstTime_ShouldCreateWallet() {

        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, USDT_ID, new BigDecimal("5010.00000000"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, BTC_ID, new BigDecimal("0.1"))).thenReturn(0);
        givenWalletBalances("0.1", "4990.00");


        tradeService.executeTrade(request);


        verify(tradeWalletMapper).insertWallet(USER_ID, BTC_ID, new BigDecimal("0.1"));
    }

    @Test
    void executeTrade_ShouldRoundTotalAmountAgainstUser() {

        btcPrice.setBidPrice(new BigDecimal("50000.33333333"));
        btcPrice.setAskPrice(new BigDecimal("50000.33333333"));
        request.setQuantity(new BigDecimal("0.00000001"));
        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, USDT_ID, new BigDecimal("0.00050001"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, BTC_ID, new BigDecimal("0.00000001"))).thenReturn(1);
        givenWalletBalances("0.00000001", "1.00");

        TradeResponse buy = tradeService.executeTrade(request);

        request.setTradeType(TradeType.SELL);
        when(tradeWalletMapper.debit(USER_ID, BTC_ID, new BigDecimal("0.00000001"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, USDT_ID, new BigDecimal("0.00050000"))).thenReturn(1);
        TradeResponse sell = tradeService.executeTrade(request);

        assertThat(buy.getTotalAmount()).isEqualByComparingTo("0.00050001");
        assertThat(sell.getTotalAmount()).isEqualByComparingTo("0.00050000");
    }

    @Test
    void executeTrade_WhenPriceIsCrossed_ShouldNotAllowRiskFreeProfit() {
        // Given - best bid (one exchange) is above best ask (another exchange)
        btcPrice.setBidPrice(new BigDecimal("50100.00"));
        btcPrice.setAskPrice(new BigDecimal("50000.00"));
        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, USDT_ID, new BigDecimal("5010.00000000"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, BTC_ID, new BigDecimal("0.1"))).thenReturn(1);
        when(tradeWalletMapper.debit(USER_ID, BTC_ID, new BigDecimal("0.1"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, USDT_ID, new BigDecimal("5000.00000000"))).thenReturn(1);
        givenWalletBalances("0.1", "4990.00");

        // When
        TradeResponse buy = tradeService.executeTrade(request);
        request.setTradeType(TradeType.SELL);
        TradeResponse sell = tradeService.executeTrade(request);

        // Then - buys at the higher price, sells at the lower one
        assertThat(buy.getPrice()).isEqualByComparingTo("50100.00");
        assertThat(sell.getPrice()).isEqualByComparingTo("50000.00");
    }

    @Test
    void executeTrade_WhenInsufficientBalance_ShouldThrowAndNotRecordTrade() {

        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, USDT_ID, new BigDecimal("5010.00000000"))).thenReturn(0);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
        verify(tradeWalletMapper, never()).credit(anyLong(), anyLong(), any());
        verify(tradeMapper, never()).insertTrade(any(), any());
    }

    @Test
    void executeTrade_WhenUserNotFound_ShouldThrow() {

        when(userMapper.lockById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(tradeMapper, never()).insertTrade(any(), any());
    }

    @Test
    void executeTrade_WhenPairNotFound_ShouldThrow() {

        when(userMapper.lockById(USER_ID)).thenReturn(USER_ID);
        when(tradePairMapper.findByPairName("BTCUSDT")).thenReturn(null);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CRYPTO_PAIR_NOT_FOUND));
    }

    @Test
    void executeTrade_WhenPairInactive_ShouldThrow() {

        btcPair.setActive(false);
        when(userMapper.lockById(USER_ID)).thenReturn(USER_ID);
        when(tradePairMapper.findByPairName("BTCUSDT")).thenReturn(btcPair);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CRYPTO_PAIR_INACTIVE));
    }

    @Test
    void executeTrade_WhenNoPrice_ShouldThrow() {

        when(userMapper.lockById(USER_ID)).thenReturn(USER_ID);
        when(tradePairMapper.findByPairName("BTCUSDT")).thenReturn(btcPair);
        when(tradePriceMapper.findLatestByCryptoPairId(1L)).thenReturn(null);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_UNAVAILABLE));
    }

    @Test
    void executeTrade_WhenPriceIsStale_ShouldThrowAndNotTouchWallets() {

        btcPrice.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        givenTradableMarket();

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_UNAVAILABLE));
        verify(tradeWalletMapper, never()).debit(anyLong(), anyLong(), any());
    }

    @Test
    void executeTrade_WhenPriceIsOldButNoMaxAgeConfigured_ShouldExecute() {

        tradeService = new TradeServiceImpl(tradeMapper, userWalletMapper, userMapper, tradePairMapper, tradePriceMapper,
                tradeWalletMapper, new TradeProperties(null));
        btcPrice.setCreatedAt(LocalDateTime.now().minusYears(1));
        givenTradableMarket();
        when(tradeWalletMapper.debit(USER_ID, USDT_ID, new BigDecimal("5010.00000000"))).thenReturn(1);
        when(tradeWalletMapper.credit(USER_ID, BTC_ID, new BigDecimal("0.1"))).thenReturn(1);
        givenWalletBalances("0.1", "4990.00");

        TradeResponse result = tradeService.executeTrade(request);

        assertThat(result.getPrice()).isEqualByComparingTo("50100.00");
    }

    @Test
    void executeTrade_WithInvalidRequest_ShouldThrowBeforeTouchingData() {
        assertInvalid(() -> request.setUserId(null));
        assertInvalid(() -> request.setPairName(" "));
        assertInvalid(() -> request.setTradeType(null));
        assertInvalid(() -> request.setQuantity(null));
        assertInvalid(() -> request.setQuantity(BigDecimal.ZERO));
        assertInvalid(() -> request.setQuantity(new BigDecimal("-1")));
        assertInvalid(() -> request.setQuantity(new BigDecimal("0.000000001")));
        assertInvalid(() -> request.setQuantity(new BigDecimal("1000000000000")));
        assertInvalid(() -> request.setClientOrderId("x".repeat(65)));

        verifyNoInteractions(tradeMapper, userWalletMapper, userMapper, tradePairMapper, tradePriceMapper, tradeWalletMapper);
    }

    @Test
    void executeTrade_WhenUnexpectedErrorOccurs_ShouldThrowInternalErrorWithCause() {
        // Given
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(userMapper.lockById(USER_ID)).thenThrow(failure);

        // When / Then
        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR))
                .hasMessage("Unexpected error occurred")
                .hasCause(failure);
        verify(tradeMapper, never()).insertTrade(any(), any());
    }

    @Test
    void executeTrade_WhenUserLockTimesOut_ShouldThrowTradeConflict() {
        // Given
        when(userMapper.lockById(USER_ID)).thenThrow(new CannotAcquireLockException("lock timeout"));

        // When / Then
        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRADE_CONFLICT));
    }

    @Test
    void executeTrade_WithUsedClientOrderId_ShouldReturnExistingTradeWithoutExecuting() {

        request.setClientOrderId("order-1");
        when(userMapper.lockById(USER_ID)).thenReturn(USER_ID);
        when(tradePairMapper.findByPairName("BTCUSDT")).thenReturn(btcPair);
        when(tradeMapper.findByUserIdAndClientOrderId(USER_ID, "order-1")).thenReturn(existingTrade());
        givenWalletBalances("0.1", "4990.00");

        TradeResponse result = tradeService.executeTrade(request);

        assertThat(result.getTradeId()).isEqualTo(99L);
        assertThat(result.getPrice()).isEqualByComparingTo("49000.00");
        verify(tradeWalletMapper, never()).debit(anyLong(), anyLong(), any());
        verify(tradeMapper, never()).insertTrade(any(), any());
    }

    @Test
    void executeTrade_WithClientOrderIdUsedForDifferentTrade_ShouldThrow() {

        request.setClientOrderId("order-1");
        request.setQuantity(new BigDecimal("0.2"));
        when(userMapper.lockById(USER_ID)).thenReturn(USER_ID);
        when(tradePairMapper.findByPairName("BTCUSDT")).thenReturn(btcPair);
        when(tradeMapper.findByUserIdAndClientOrderId(USER_ID, "order-1")).thenReturn(existingTrade());

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_CLIENT_ORDER_ID));
    }

    private void assertInvalid(Runnable invalidate) {
        setUp();
        invalidate.run();
        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private void givenTradableMarket() {
        when(userMapper.lockById(USER_ID)).thenReturn(USER_ID);
        when(tradePairMapper.findByPairName("BTCUSDT")).thenReturn(btcPair);
        when(tradePriceMapper.findLatestByCryptoPairId(1L)).thenReturn(btcPrice);
    }

    private void givenWalletBalances(String btcBalance, String usdtBalance) {
        when(userWalletMapper.findByUserId(USER_ID)).thenReturn(Arrays.asList(
                wallet("BTC", "Bitcoin", btcBalance), wallet("USDT", "Tether", usdtBalance)));
    }

    private UserWalletDto wallet(String symbol, String name, String balance) {
        UserWalletDto wallet = new UserWalletDto();
        wallet.setSymbol(symbol);
        wallet.setName(name);
        wallet.setBalance(new BigDecimal(balance));
        return wallet;
    }

    private Trade existingTrade() {
        Trade trade = new Trade();
        trade.setId(99L);
        trade.setUserId(USER_ID);
        trade.setCryptoPairId(1L);
        trade.setTradeType("BUY");
        trade.setQuantity(new BigDecimal("0.10000000"));
        trade.setPrice(new BigDecimal("49000.00"));
        trade.setTotalAmount(new BigDecimal("4900.00"));
        trade.setTradeTime(LocalDateTime.now().minusMinutes(1));
        return trade;
    }
}
