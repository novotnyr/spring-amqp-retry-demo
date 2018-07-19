package com.github.novotnyr.spring.amqp.retry;

import org.springframework.amqp.AmqpException;

/**
 * Exception indicating that the <i>Content Type</i>
 * message property is not supported or valid.
 */
public class UnsupportedAmqpContentTypeException extends AmqpException {
    public UnsupportedAmqpContentTypeException(String message) {
        super(message);
    }
}