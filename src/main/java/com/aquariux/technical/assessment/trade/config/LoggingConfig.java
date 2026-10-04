package com.aquariux.technical.assessment.trade.config;

import com.aquariux.technical.assessment.trade.logging.HttpCallLoggingInterceptor;
import com.aquariux.technical.assessment.trade.logging.ServiceLoggingInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;

@Configuration
public class LoggingConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor serviceLoggingAdvisor() {
        StaticMethodMatcherPointcut serviceMethods = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return AnnotatedElementUtils.hasAnnotation(targetClass, Service.class)
                        && method.getDeclaringClass() != Object.class;
            }
        };

        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(serviceMethods, new ServiceLoggingInterceptor());
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }

    @Bean
    public static BeanPostProcessor restTemplateLoggingPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RestTemplate restTemplate) {
                    restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(restTemplate.getRequestFactory()));
                    restTemplate.getInterceptors().add(new HttpCallLoggingInterceptor());
                }
                return bean;
            }
        };
    }
}
