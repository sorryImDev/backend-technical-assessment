package com.aquariux.technical.assessment.trade.logging;

import com.aquariux.technical.assessment.trade.enums.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class HttpCallLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String call = "method=" + request.getMethod() + " url=" + request.getURI();

        log.info(LogFormatter.format(LogEvent.REQ_PREP,
                call + " payload=" + LogFormatter.payload(new String(body, StandardCharsets.UTF_8))));

        ClientHttpResponse response = execution.execute(request, body);

        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        log.info(LogFormatter.format(LogEvent.RESP_RECV, call
                + " status=" + LogFormatter.status(response.getStatusCode()) + " payload=" + LogFormatter.payload(responseBody)));
        return response;
    }
}
