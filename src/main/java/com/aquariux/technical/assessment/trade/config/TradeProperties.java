package com.aquariux.technical.assessment.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param maxPriceAge trades are rejected if the latest price is older than this (trade.max-price-age, default 30s;
 *                    the scheduler refreshes prices every 10s)
 */
@ConfigurationProperties(prefix = "trade")
public record TradeProperties(@DefaultValue("30s") Duration maxPriceAge) {
}
