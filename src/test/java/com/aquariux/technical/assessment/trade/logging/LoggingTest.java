package com.aquariux.technical.assessment.trade.logging;

import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:logging-test", "trade.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoggingTest {

    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})? ";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CryptoPriceMapper cryptoPriceMapper;

    @BeforeEach
    void setUp() {
        CryptoPrice price = new CryptoPrice();
        price.setCryptoPairId(1L);
        price.setBidPrice(new BigDecimal("50000.00"));
        price.setAskPrice(new BigDecimal("50010.00"));
        price.setBidSource("BINANCE");
        price.setAskSource("HUOBI");
        cryptoPriceMapper.insertPrice(price);
    }

    @Test
    @Transactional
    void executeTrade_Success_ShouldLogRequestAndOperationWith200(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/trades/execute").contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 0.01}
                        """))
                .andExpect(status().isOk());

        assertThat(output).containsPattern(TIMESTAMP + "\\[REQ_RECV] method=POST endpoint=/api/trades/execute");
        assertThat(output).containsPattern(TIMESTAMP + "\\[START_OP] operation=TradeServiceImpl.executeTrade");
        assertThat(output).containsPattern(TIMESTAMP + "\\[END_OP] operation=TradeServiceImpl.executeTrade status=200 OK");
        assertThat(output).containsPattern(TIMESTAMP + "\\[TRADE_EXEC] tradeId=\\d+ userId=1 pair=BTCUSDT type=BUY quantity=0.01 "
                + "price=50010.00000000 total=500.10000000");
    }

    @Test
    void executeTrade_Rejected_ShouldLogEndOpWithErrorStatus(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/trades/execute").contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId": 1, "pairName": "BTCUSDT", "tradeType": "BUY", "quantity": 100}
                        """))
                .andExpect(status().isUnprocessableEntity());

        assertThat(output).containsPattern(TIMESTAMP
                + "\\[END_OP] operation=TradeServiceImpl.executeTrade status=422 Unprocessable Entity error=INSUFFICIENT_BALANCE");
        assertThat(output).containsPattern(TIMESTAMP + "\\[ERR_RESP] endpoint=/api/trades/execute "
                + "status=422 Unprocessable Entity error=INSUFFICIENT_BALANCE exception=TradeException detail=Insufficient USDT balance");
    }

    @Test
    void malformedRequest_ShouldLogErrorResponseInStandardFormat(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/trades/execute").contentType(MediaType.APPLICATION_JSON).content("{\"userId\": "))
                .andExpect(status().isBadRequest());

        assertThat(output).containsPattern(TIMESTAMP + "\\[ERR_RESP] endpoint=/api/trades/execute "
                + "status=400 Bad Request error=INVALID_REQUEST exception=HttpMessageNotReadableException");
    }

    @Test
    void baselineServices_ShouldAlsoLogStartAndEnd(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/wallets/user/1")).andExpect(status().isOk());

        assertThat(output).containsPattern(TIMESTAMP + "\\[REQ_RECV] method=GET endpoint=/api/wallets/user/1");
        assertThat(output).containsPattern(TIMESTAMP + "\\[START_OP] operation=WalletServiceImpl.getUserWalletBalances");
        assertThat(output).containsPattern(TIMESTAMP + "\\[END_OP] operation=WalletServiceImpl.getUserWalletBalances status=200 OK");
    }

    @Test
    void outgoingHttpCall_ShouldLogPayloadSentAndResponseReceived(CapturedOutput output) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).bufferContent().build();
        server.expect(requestTo("https://exchange.test/ticker"))
                .andRespond(withSuccess("{\"bid\":1}", MediaType.APPLICATION_JSON));

        String response = restTemplate.getForObject("https://exchange.test/ticker", String.class);

        assertThat(response).isEqualTo("{\"bid\":1}");
        assertThat(output).containsPattern(TIMESTAMP + "\\[REQ_PREP] method=GET url=https://exchange.test/ticker payload=<none>");
        assertThat(output).containsPattern(TIMESTAMP
                + "\\[RESP_RECV] method=GET url=https://exchange.test/ticker status=200 OK payload=\\{\"bid\":1}");
    }
}
