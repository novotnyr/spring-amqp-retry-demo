package com.github.novotnyr.spring.amqp.retry;

import org.springframework.classify.Classifier;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Represents a classifier that maps Throwable classes to boolean values.
 * <p>
 *     This is to represent lists of acceptable exceptions. Examples include:
 *     <ul>
 *         <li>classifier of retryable exceptions</li>
 *         <li>classifier of rethrowable exceptions</li>
 *     </ul>
 * </p>
 */
public class ExceptionClassifier implements Classifier<Throwable, Boolean> {
    private Map<Class<? extends Throwable>, Boolean> throwables = new WeakHashMap<>();

    /**
     * Classify the corresponding throwable as accepted or rejected.
     */
    @Override
    public Boolean classify(Throwable throwable) {
        if (throwable == null) {
            return Boolean.FALSE;
        }
        return throwables.getOrDefault(throwable.getClass(), Boolean.FALSE);
    }

    /**
     * When configuring this classifier, classify instances of the
     * class as positive. This means that the specified throwable
     * needs to be accepted.
     * @param throwableClass class of {@link Throwable} that should be acceoted
     * @return instance of this classifier for further configuration or fluent API use.
     */
    public ExceptionClassifier accept(Class<? extends Throwable> throwableClass) {
        this.throwables.put(throwableClass, Boolean.TRUE);
        return this;
    }

    /**
     * Return this classifier as a map between {@link Throwable} classes
     * and Boolean true values. This indicates {@link Throwable}s that should
     * be accepted.
     */
    public Map<Class<? extends Throwable>, Boolean> asMap() {
        return new WeakHashMap<>(this.throwables);
    }

}
