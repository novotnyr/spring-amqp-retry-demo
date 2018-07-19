package com.github.novotnyr.spring.amqp.retry;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.AmqpIllegalStateException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.amqp.rabbit.listener.adapter.ReplyFailureException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.remoting.support.RemoteInvocationResult;

/**
 * Replies to a specified channel with a specified payload
 * using a AMQP RPC mechanism.
 * <p>
 *     <b>Design note:</b> this class inherits
 *     from {@link AbstractAdaptableMessageListener}. This is
 *     not a very nice design, but it is required to reuse
 *     code that handles RPC replies for MessageListeners.
 * </p>
 *
 */
public class AmqpReplyTemplate extends AbstractAdaptableMessageListener {
    /**
     * Create a reply template with a specific converter from exceptions to response representation.
     * @param messageConverter used to convert exceptions to message payloads.
     */
    public AmqpReplyTemplate(MessageConverter messageConverter) {
        setMessageConverter(messageConverter);
    }

    /**
     * Do not use this method. It is not part of public API.
     * <p>
     *     This is a nasty design workaround to reuse RPC reply mechanism
     *     available in the parent class.
     * </p>
     */
    @Override
    @Deprecated
    public void onMessage(Message message, Channel channel) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("This operation is not implemented. This class should be used" +
                " as a synchronous message sender.");
    }

    /**
     * Reply with an exception to a AMQP client via RPC/Reply mechanism.
     * <p>
     *     Exception is wrapped to a {@link RemoteInvocationResult} before being sent.
     * </p>
     *
     * @param channel the Rabbit channel to operate on
     * @param message originating request {@link Message} that should be replied
     * @param throwable exception that should be send in a response to the client
     * @return true when reply went well. <code>false</code> when the message does not indicate
     *         <em>Reply-To</em> mechanism.
     * @throws ReplyFailureException when reply failed
     */
    public boolean reply(Channel channel, Message message, Throwable throwable) throws ReplyFailureException {
        try {
            assertReplyNotYetFailed(throwable);
            if (!isRpcMessage(message)) {
                return false;
            }
            RemoteInvocationResult result = fromCause(throwable);
            doReply(channel, message, result);
        } catch (ReplyFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new ReplyFailureException("Reply failed", e);
        }
        return true;
    }

    /**
     * Extracts the cause of the provided {@link Throwable} and wraps it
     * into {@link RemoteInvocationResult}.
     */
    protected RemoteInvocationResult fromCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        RemoteInvocationResult result = new RemoteInvocationResult(cause);
        if (cause != null) {
            result.setValue(throwable.getCause().getClass().getName());
        }
        return result;
    }

    /**
     * Asserts that the exception is not ReplyFailure. Otherwise
     * this means that RPC reply has already failed and there is no point
     * to try replying again.
     */
    private void assertReplyNotYetFailed(Throwable t) {
        if (t.getCause() instanceof ReplyFailureException) {
            throw (ReplyFailureException) t.getCause();
        }
    }

    /**
     * Execute an RPC reply to a specified message.
     * @param channel the Rabbit channel to operate on
     * @param request originating request {@link Message}
     * @param resultArg reply payload. This will be converted with message converter
     * @throws Exception thrown by RabbitMQ API
     */
    private void doReply(Channel channel, Message request, Object resultArg) throws Exception {
        try {
            super.handleResult(resultArg, request, channel);
        } catch (ReplyFailureException rfe) {
            rfe.printStackTrace();
            /*
            TODO we should distinguish between synchronous and asynchronous failure
             */
        } catch (Exception e) {
            throw e;
        }
    }

    private boolean isRpcMessage(Message message) {
        return message.getMessageProperties().getReplyTo() != null;
    }

    public static class ChannelAndMessage {
        private final Channel channel;

        private final Message message;

        public ChannelAndMessage(Object[] args) {
            if (args.length < 2) {
                throw new AmqpIllegalStateException("Not enough arguments for reply were provided.");
            }
            if (args[0] == null || !(args[0] instanceof Channel)) {
                throw new AmqpIllegalStateException("Missing 'Channel' argument '0' for reply");
            }
            if (args[1] == null || !(args[1] instanceof Message)) {
                throw new AmqpIllegalStateException("Missing 'Message' argument '1' for reply");
            }
            this.channel = (Channel) args[0];
            this.message = (Message) args[1];
        }

        public Channel getChannel() {
            return channel;
        }

        public Message getMessage() {
            return message;
        }
    }
}
