package com.futurice.cascade.reactive;

import android.support.annotation.NonNull;

import com.futurice.cascade.i.IThreadType;
import com.futurice.cascade.i.action.IOnErrorAction;

import static com.futurice.cascade.Async.*;

/**
 * An {@link Integer} which can be updated in an atomic, thread-safe manner.
 * <p>
 * This is similar to an {@link java.util.concurrent.atomic.AtomicInteger} with reactive bindings to
 * get and set the value in reactive chains (function sequences that can fire multiple times).
 * <p>
 * Created by phou on 30-04-2015.
 */
public class ReactiveInteger extends ReactiveValue<Integer> {
    /**
     * Create a new atomic integer
     *
     * @param threadType
     * @param name
     * @param initialValue
     */
    public ReactiveInteger(
            @NonNull final IThreadType threadType,
            @NonNull final String name,
            final int initialValue) {
        super(threadType, name, initialValue);
    }

    /**
     * Create a new atomic integer
     *
     * @param threadType
     * @param name
     * @param initialValue
     * @param onError
     */
    public ReactiveInteger(
            @NonNull final IThreadType threadType,
            @NonNull final String name,
            final int initialValue,
            @NonNull final IOnErrorAction onError) {
        super(threadType, name, initialValue, onError);
    }

    /**
     * Create a new atomic integer
     *
     * @param threadType
     * @param name
     */
    public ReactiveInteger(
            @NonNull final IThreadType threadType,
            @NonNull final String name) {
        super(threadType, name);
    }

    /**
     * Create a new atomic integer
     *
     * @param threadType
     * @param name
     * @param onError
     */
    public ReactiveInteger(
            @NonNull final IThreadType threadType,
            @NonNull final String name,
            @NonNull final IOnErrorAction onError) {
        super(threadType, name, onError);
    }

    /**
     * Add two integers in a thread-safe manner
     *
     * @param i
     * @return
     */
    public int addAndGet(final int i) {
        int currentValue;

        for (;;) {
            currentValue = get();
            if (compareAndSet(currentValue, currentValue + i)) {
                return currentValue;
            }
            ii(this, origin, "Collision concurrent add, will try again: " + currentValue);
        }
    }

    /**
     * Multiply two integers in a thread-safe manner
     *
     * @param i
     * @return
     */
    public int multiplyAndGet(final int i) {
        int currentValue;

        for (;;) {
            currentValue = get();
            if (compareAndSet(currentValue, currentValue * i)) {
                return currentValue;
            }
            ii(this, origin, "Collision concurrent add, will try again: " + currentValue);
        }
    }

    /**
     * Increment the integer in a thread-safe manner
     *
     * @return
     */
    public int incrementAndGet() {
        return addAndGet(1);
    }


    /**
     * Decrement the integer in a thread-safe manner
     *
     * @return
     */
    public int decrementAndGet() {
        return addAndGet(-1);
    }
}
