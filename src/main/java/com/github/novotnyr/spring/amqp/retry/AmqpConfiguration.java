package com.github.novotnyr.spring.amqp.retry;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.io.IOException;
import java.sql.SQLException;

@Configuration
@EnableRabbit
public class AmqpConfiguration {

    @Bean
    public org.springframework.amqp.support.converter.MessageConverter amqpMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            AmqpTemplate replyAmqpTemplate,
            MessageConverter messageConverter
    )
    {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        configureRetry(factory, replyAmqpTemplate, messageConverter);
        return factory;
    }

    private void configureRetry(SimpleRabbitListenerContainerFactory factory, AmqpTemplate amqpTemplate, MessageConverter messageConverter) {
        RetryOperationsInterceptor retry = RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy())
                .build();
        retry.setRecoverer(methodInvocationRecoverer(amqpTemplate, messageConverter));
        factory.setAdviceChain(retry);
    }

    private RpcReplySupportingMethodInvocationRecoverer methodInvocationRecoverer(AmqpTemplate amqpTemplate, MessageConverter messageConverter) {
        return new RpcReplySupportingMethodInvocationRecoverer(messageRecoverer(amqpTemplate), messageConverter);
    }

    private RetryPolicy retryPolicy() {
        return new SimpleRetryPolicy(3, retryableExceptionClassifier().asMap(),
                true, false);
    }

    private MessageRecoverer messageRecoverer(AmqpTemplate amqpTemplate) {
        String errorExchange = "";
        String errorRoutingKey = "error";
        String errorRoutingKeyPrefix = "";
        return new RepublishMessageRecoverer(amqpTemplate, errorExchange, errorRoutingKey)
                .errorRoutingKeyPrefix(errorRoutingKeyPrefix);
    }

    private ExceptionClassifier retryableExceptionClassifier() {
        return new ExceptionClassifier()
                .accept(SQLException.class)
                .accept(IOException.class);
    }
}
