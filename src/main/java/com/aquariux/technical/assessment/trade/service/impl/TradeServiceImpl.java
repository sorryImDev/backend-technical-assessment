package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.internal.UserWalletDto;
import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.aquariux.technical.assessment.trade.config.TradeProperties;
import com.aquariux.technical.assessment.trade.dto.internal.CryptoPairDto;
import com.aquariux.technical.assessment.trade.dto.response.WalletBalanceResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.enums.ErrorCode;
import com.aquariux.technical.assessment.trade.exception.TradeException;
import com.aquariux.technical.assessment.trade.enums.LogEvent;
import com.aquariux.technical.assessment.trade.logging.LogFormatter;
import com.aquariux.technical.assessment.trade.mapper.TradePairMapper;
import com.aquariux.technical.assessment.trade.mapper.TradePriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeWalletMapper;
import com.aquariux.technical.assessment.trade.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeServiceImpl implements TradeServiceInterface {

    private final TradeMapper tradeMapper;
    private final UserWalletMapper userWalletMapper;
    private final UserMapper userMapper;
    private final TradePairMapper tradePairMapper;
    private final TradePriceMapper tradePriceMapper;
    private final TradeWalletMapper tradeWalletMapper;
    private final TradeProperties tradeProperties;

    // Matches DECIMAL(20,8) used for all amounts
    private static final int AMOUNT_SCALE = 8;
    private static final int MAX_INTEGER_DIGITS = 12;
    private static final int MAX_CLIENT_ORDER_ID_LENGTH = 64;

    @Override
    @Transactional
    public TradeResponse executeTrade(TradeRequest tradeRequest) {
        try {
            validateRequest(tradeRequest);

            Long userId = tradeRequest.getUserId();
            TradeType tradeType = tradeRequest.getTradeType();
            BigDecimal quantity = tradeRequest.getQuantity();

            // Row lock user so concurrent trades of the same user run one at a time
            if (userMapper.lockById(userId) == null) {
                throw new TradeException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId);
            }

            CryptoPairDto pair = tradePairMapper.findByPairName(tradeRequest.getPairName().trim().toUpperCase());
            if (pair == null) {
                throw new TradeException(ErrorCode.CRYPTO_PAIR_NOT_FOUND, "Crypto pair not supported: " + tradeRequest.getPairName());
            }

            if (tradeRequest.getClientOrderId() != null) {
                Trade existingTrade = tradeMapper.findByUserIdAndClientOrderId(userId, tradeRequest.getClientOrderId());
                if (existingTrade != null) {
                    return replayTrade(existingTrade, tradeRequest, pair);
                }
            }

            if (!Boolean.TRUE.equals(pair.getActive())) {
                throw new TradeException(ErrorCode.CRYPTO_PAIR_INACTIVE, "Crypto pair is not tradable: " + pair.getPairName());
            }

            BigDecimal price = getCurrentPrice(pair, tradeType);

            BigDecimal totalAmount = quantity.multiply(price)
                    .setScale(AMOUNT_SCALE, tradeType == TradeType.BUY ? RoundingMode.UP : RoundingMode.DOWN);

            if (totalAmount.signum() <= 0) {
                throw new TradeException(ErrorCode.TRADE_AMOUNT_TOO_SMALL, "Trade amount is too small");
            }

            if (tradeType == TradeType.BUY) {
                debitWallet(userId, pair.getQuoteSymbolId(), pair.getQuoteSymbol(), totalAmount);
                creditWallet(userId, pair.getBaseSymbolId(), quantity);
            } else {
                debitWallet(userId, pair.getBaseSymbolId(), pair.getBaseSymbol(), quantity);
                creditWallet(userId, pair.getQuoteSymbolId(), totalAmount);
            }

            Trade trade = new Trade();
            trade.setUserId(userId);
            trade.setCryptoPairId(pair.getId());
            trade.setTradeType(tradeType.name());
            trade.setQuantity(quantity);
            trade.setPrice(price);
            trade.setTotalAmount(totalAmount);
            trade.setTradeTime(LocalDateTime.now());
            tradeMapper.insertTrade(trade, tradeRequest.getClientOrderId());

            log.info(LogFormatter.format(LogEvent.TRADE_EXEC, "tradeId=" + trade.getId() + " userId=" + userId
                    + " pair=" + pair.getPairName() + " type=" + tradeType + " quantity=" + quantity.toPlainString()
                    + " price=" + price.toPlainString() + " total=" + totalAmount.toPlainString()));

            return mapToResponse(trade, pair, tradeRequest.getClientOrderId());
        } catch (TradeException e) {
            throw e;
        } catch (ConcurrencyFailureException e) {
            throw new TradeException(ErrorCode.TRADE_CONFLICT, "Another trade is in progress for this user, please retry", e);
        } catch (Exception e) {
            throw new TradeException(ErrorCode.INTERNAL_ERROR, "Unexpected error occurred", e);
        }
    }

    private void validateRequest(TradeRequest tradeRequest) {
        if (tradeRequest == null) {
            throw new TradeException(ErrorCode.INVALID_REQUEST, "Request body is required");
        }
        if (tradeRequest.getUserId() == null) {
            throw new TradeException(ErrorCode.INVALID_REQUEST, "userId is required");
        }
        if (tradeRequest.getPairName() == null || tradeRequest.getPairName().isBlank()) {
            throw new TradeException(ErrorCode.INVALID_REQUEST, "pairName is required");
        }
        if (tradeRequest.getTradeType() == null) {
            throw new TradeException(ErrorCode.INVALID_REQUEST, "tradeType is required");
        }

        BigDecimal quantity = tradeRequest.getQuantity();
        if (quantity == null) {
            throw new TradeException(ErrorCode.INVALID_REQUEST, "quantity is required");
        }
        if (quantity.signum() <= 0) {
            throw new TradeException(ErrorCode.INVALID_REQUEST, "quantity must be greater than 0");
        }
        if (quantity.stripTrailingZeros().scale() > AMOUNT_SCALE
                || quantity.precision() - quantity.scale() > MAX_INTEGER_DIGITS) {
            throw new TradeException(ErrorCode.INVALID_REQUEST,
                    "quantity supports at most " + MAX_INTEGER_DIGITS + " integer digits and " + AMOUNT_SCALE + " decimal places");
        }

        String clientOrderId = tradeRequest.getClientOrderId();
        if (clientOrderId != null && (clientOrderId.isBlank() || clientOrderId.length() > MAX_CLIENT_ORDER_ID_LENGTH)) {
            throw new TradeException(ErrorCode.INVALID_REQUEST,
                    "clientOrderId must be 1 to " + MAX_CLIENT_ORDER_ID_LENGTH + " characters");
        }
    }

    private TradeResponse replayTrade(Trade existingTrade, TradeRequest tradeRequest, CryptoPairDto pair) {
        boolean sameTrade = existingTrade.getCryptoPairId().equals(pair.getId())
                && existingTrade.getTradeType().equals(tradeRequest.getTradeType().name())
                && existingTrade.getQuantity().compareTo(tradeRequest.getQuantity()) == 0;
        if (!sameTrade) {
            throw new TradeException(ErrorCode.DUPLICATE_CLIENT_ORDER_ID,
                    "clientOrderId already used for a different trade: " + tradeRequest.getClientOrderId());
        }

        log.info(LogFormatter.format(LogEvent.TRADE_REPLAY, "tradeId=" + existingTrade.getId()
                + " userId=" + existingTrade.getUserId() + " clientOrderId=" + tradeRequest.getClientOrderId()));
        return mapToResponse(existingTrade, pair, tradeRequest.getClientOrderId());
    }

    private BigDecimal getCurrentPrice(CryptoPairDto pair, TradeType tradeType) {
        CryptoPrice latestPrice = tradePriceMapper.findLatestByCryptoPairId(pair.getId());
        if (latestPrice == null) {
            throw new TradeException(ErrorCode.PRICE_UNAVAILABLE, "No price available for " + pair.getPairName());
        }
        if (tradeProperties.maxPriceAge() != null
                && latestPrice.getCreatedAt().isBefore(LocalDateTime.now().minus(tradeProperties.maxPriceAge()))) {
            throw new TradeException(ErrorCode.PRICE_UNAVAILABLE, "No current market price available for " + pair.getPairName());
        }

        BigDecimal bidPrice = latestPrice.getBidPrice();
        BigDecimal askPrice = latestPrice.getAskPrice();
        if (bidPrice == null || askPrice == null || bidPrice.signum() <= 0 || askPrice.signum() <= 0) {
            throw new TradeException(ErrorCode.PRICE_UNAVAILABLE, "No current market price available for " + pair.getPairName());
        }

        return tradeType == TradeType.BUY ? askPrice.max(bidPrice) : bidPrice.min(askPrice);
    }

    private void debitWallet(Long userId, Long symbolId, String symbol, BigDecimal amount) {
        if (tradeWalletMapper.debit(userId, symbolId, amount) == 0) {
            throw new TradeException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient " + symbol + " balance, required: " + amount.toPlainString());
        }
    }

    private void creditWallet(Long userId, Long symbolId, BigDecimal amount) {
        // Wallet records are created only when the user first acquires the currency
        if (tradeWalletMapper.credit(userId, symbolId, amount) == 0) {
            tradeWalletMapper.insertWallet(userId, symbolId, amount);
        }
    }

    private TradeResponse mapToResponse(Trade trade, CryptoPairDto pair, String clientOrderId) {
        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getId());
        response.setUserId(trade.getUserId());
        response.setPairName(pair.getPairName());
        response.setTradeType(TradeType.valueOf(trade.getTradeType()));
        response.setQuantity(trade.getQuantity());
        response.setPrice(trade.getPrice());
        response.setTotalAmount(trade.getTotalAmount());
        response.setTradeTime(trade.getTradeTime());
        response.setClientOrderId(clientOrderId);

        Map<String, UserWalletDto> wallets = userWalletMapper.findByUserId(trade.getUserId()).stream()
                .collect(Collectors.toMap(UserWalletDto::getSymbol, Function.identity()));
        response.setWallets(List.of(
                mapToWalletResponse(wallets.get(pair.getBaseSymbol())),
                mapToWalletResponse(wallets.get(pair.getQuoteSymbol()))));
        return response;
    }

    private WalletBalanceResponse mapToWalletResponse(UserWalletDto wallet) {
        WalletBalanceResponse response = new WalletBalanceResponse();
        response.setSymbol(wallet.getSymbol());
        response.setName(wallet.getName());
        response.setBalance(wallet.getBalance());
        return response;
    }
}