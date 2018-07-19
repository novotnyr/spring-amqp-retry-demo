Spring AMQP Retry Demo
======================

This is a Spring Boot demo that shows stateless retry of failed
Rabbit listeners with support for AMQP RPC synchronous replies.

The goals of this demo:

*   show retryable exception handling
*   propagate exceptions to RPC client, if such scenario is used
*   propagate exceptions to a dedicated error queue, when asynchronous scenario
is used

Retry Strategies
================
Wheneve a Rabbit listener throws an exception, we might want to
retry an attempt. Maybe the database is temporarily offline,
maybe there is a connectivity error or another issue that might be attempted
again.

With **Spring Retry**, we can configure Spring AMQP to retry failed
operations.

There are two fundamental retry strategies for Spring AMQP:

* *stateless retry*: message is processed on a single node, even in a single thread,
and operation is retried until either success is achieved (i. e. no exception is thrown)
or allowed attempts are exhausted. In both cases, the AMQP message
is never requeued, since this might lead to infinite loops.

* *stateful retry*: message is processed and some exceptions might lead
to requeue or redelivery. In this case, we have to remember some *state*
across subsequent handlings of the same message on the same node. At least,
we need to remember the number of retry attempts left.

However, in this demo, we will demonstrate just the simple, stateless case.

Implementation
==============
There are multiple goals that are addressed by this code:

* under no circumstances should be an exception thrown in a way that
causes infinite redelivery looop. An exception will either drop the message,
or redeliver it to a Dead Letter Queue.
* we need to cover two types of exceptions:

    * exceptions thrown from the Rabbit listener
    * exceptions thrown from the message preprocessors and converters.

* in an RPC case, we have to always return a value to the requesting client:
either a return value or an exception.

Retry and Recovery
------------------
In the *stateless retry*, there are two types of exceptions: those
that are retryable and those that are not.

Essentially, a reply mechanism - as provided by Spring Retry - is a simple
loop over retry attempts that terminates successfully or fails due to
exhausted attempts.

If the Rabbit Listener throws a retryable exception,
another retry attempt is made, possibly after some backoff period,
unless all retry attempts are exhausted.

On the other hand, non-retryable exception immediately exhausts all remaining
retry attempts. This allows us to provide the same retry resolution logic
for both retryable and non-retryable exceptions.

This is implemented via custom `RetryPolicy` that holds a list of
retryable exceptions.

When all attempts are exhausted, Spring Retry proceeds to a **recovery**
stage, when an attempt to recover from retry failure is made.

### Recovery
Retry recovery is implemented in the `RpcReplySupportingMethodInvocationRecoverer`
class. Two cases are covered:

*   for nonRPC (asynchronous) calls, a recovery plan will send the failure message
to a dead letter queue. This message contains information about the reason
of the failure.
*   for RPC calls, the exception is wrapped into a result object which is
sent back to the client.

Plugging It In
--------------
The retry configuration is implemented as an AOP Advice to the
`SimpleRabbitListenerContainerFactory`. The whole retry logic is represented
by `RetryOperationsInterceptor` builder, that configures stateless
mode of operation, associates corresponding Retry Policy and attaches
method recoverer that will handle both exceptions and retry exhaustions.

Class Overview
--------------

* `SimpleRabbitListenerContainerFactory` (Spring AMQP) class has an
attached retry AOP advice represented by `RetryOperationsInterceptor`
* `RetryOperationsInterceptor` expects:
    *   a *retry policy* indicating which exceptions are treated as retryable
    *   message recoverer that is called on exceptions and retry attempt exhaustions
* `ExceptionClassifier`: represents a classifier that is attached to
a custom retry policy. It is a convenience class to easily specify retryable
exception calsses
* `AmqpReplyTemplate` a simple high-level class that handles RPC synchronous
replies to back to the client. Exceptions are transformed to the
Spring `RemoteInvocationResult` which are supported as convenient request-reply objects.
* `RpcReplySupportingMethodInvocationRecoverer` represents a high-level
retry recovery mechanism.
    * for nonRPC calls, failures are delivered via delegate Spring Retry
    `MessageRecoverer`, usually by sending a failure message to a dedicated
    queue. One useful built-in implementation is Spring Retry
    `RepublishMessageRecoverer` implementation.
    * for RPC calls, failures are replie via `AmqpReplyTemplate`
    back to the client









