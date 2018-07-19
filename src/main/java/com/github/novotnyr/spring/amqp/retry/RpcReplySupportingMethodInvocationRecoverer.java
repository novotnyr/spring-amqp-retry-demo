package com.github.novotnyr.spring.amqp.retry;

import com.github.novotnyr.spring.amqp.retry.AmqpReplyTemplate.ChannelAndMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.springframework.amqp.AmqpIllegalStateException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.ReplyFailureException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Recoverer that replies with RPC message to the originating client or delegates recovery
 * to another {@link MessageRecoverer}.
 */
public class RpcReplySupportingMethodInvocationRecoverer implements MethodInvocationRecoverer<Void> {
    public static final Logger logger = getLogger(RpcReplySupportingMethodInvocationRecoverer.class);

    private static final Void VOID = null;

    private final MessageRecoverer messageRecoverer;

    private final AmqpReplyTemplate amqpReplyTemplate;

    /**
     * Create instance with specified delegate and message converter used to handle replies
     * @param messageRecoverer delegate {@link MessageRecoverer} that handles nonRPC recoveries
     * @param messageConverter used to convert outbound payloads in RPC replies
     */
    public RpcReplySupportingMethodInvocationRecoverer(MessageRecoverer messageRecoverer, MessageConverter messageConverter) {
        this.messageRecoverer = messageRecoverer;
        this.amqpReplyTemplate = new AmqpReplyTemplate(messageConverter);
    }

    /**
     * Recovers from exhausted retry attempts by either RPC replying to AMQP message
     * or by delegating recovery to the wrapped {@link MessageRecoverer}.
     * @param args recovery parameters. Usually, the first argument shall be {@link Channel},
     *             the second argument indicates a {@link Message}.
     * @param cause exception that lead to exhaustion attempts
     */
    @Override
    public Void recover(Object[] args, Throwable cause) {
        ChannelAndMessage channelAndMessage = null;
        try {
            channelAndMessage = new ChannelAndMessage(args);
            reply(channelAndMessage.getChannel(), channelAndMessage.getMessage(), cause);
        } catch (AmqpIllegalStateException e) {
            // apparently 'args' are not correct, there is not much we can do
            logger.warn("Recovery 'args' are not supported: found {}", args.length);
        }
        return VOID;
    }

    /**
     * Recover by replying synchronously to the Message or by reusing message recoverer, if available.
     * @param channel receiving channel of the request message
     * @param requestMessage inbound message that was sent by client
     * @param throwable exception which caused the recovery
     * @return true, if the reply succeeded
     * @throws ReplyFailureException when reply failed
     */
    protected boolean reply(Channel channel, Message requestMessage, Throwable throwable) throws ReplyFailureException {
        try {
            boolean wasReplied = this.amqpReplyTemplate.reply(channel, requestMessage, throwable);
            if (!wasReplied) {
                throw new ReplyFailureException("No AMQP RPC Reply was sent: message has no Reply-To property", null);
            }
            return true;
        } catch (ReplyFailureException e) {
            // we cannot reliably reply, maybe MessageRecoverer can help
            if (this.messageRecoverer == null) {
                logger.warn("Message dropped on recovery: " + requestMessage, throwable);
            } else {
                this.messageRecoverer.recover(requestMessage, throwable);
            }
            return true;
        }
    }
}
