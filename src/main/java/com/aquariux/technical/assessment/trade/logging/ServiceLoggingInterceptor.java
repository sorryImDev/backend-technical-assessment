package com.aquariux.technical.assessment.trade.logging;

import com.aquariux.technical.assessment.trade.enums.LogEvent;
import com.aquariux.technical.assessment.trade.exception.TradeException;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.HttpStatus;

@Slf4j
public class ServiceLoggingInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String operation = "operation=" + AopUtils.getTargetClass(invocation.getThis()).getSimpleName()
                + "." + invocation.getMethod().getName();

        log.info(LogFormatter.format(LogEvent.START_OP, operation));
        try {
            Object result = invocation.proceed();
            log.info(LogFormatter.format(LogEvent.END_OP, operation + " status=" + LogFormatter.status(HttpStatus.OK)));
            return result;
        } catch (TradeException e) {
            String endOp = LogFormatter.format(LogEvent.END_OP, operation
                    + " status=" + LogFormatter.status(e.getErrorCode().getStatus()) + " error=" + e.getErrorCode());
            if (e.getErrorCode().getStatus().is5xxServerError()) {
                log.error(endOp);
            } else {
                log.info(endOp);
            }
            throw e;
        } catch (Throwable e) {
            log.error(LogFormatter.format(LogEvent.END_OP, operation
                    + " status=" + LogFormatter.status(HttpStatus.INTERNAL_SERVER_ERROR) + " error=" + e.getClass().getSimpleName()));
            throw e;
        }
    }
}
