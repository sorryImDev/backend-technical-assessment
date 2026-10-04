package com.aquariux.technical.assessment.trade.logging;

import com.aquariux.technical.assessment.trade.enums.LogEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class LogFormatter {

    private static final int MAX_PAYLOAD_LENGTH = 500;

    private LogFormatter() {
    }

    public static String format(LogEvent event, String fields) {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS) + " [" + event + "] " + fields;
    }

    public static String status(HttpStatusCode status) {
        return status.value() + " " + HttpStatus.valueOf(status.value()).getReasonPhrase();
    }

    public static String payload(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "<none>";
        }
        return payload.length() > MAX_PAYLOAD_LENGTH ? payload.substring(0, MAX_PAYLOAD_LENGTH) + "...(truncated)" : payload;
    }
}
