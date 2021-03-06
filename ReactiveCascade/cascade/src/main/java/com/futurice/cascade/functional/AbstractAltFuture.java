/*
This file is part of Reactive Cascade which is released under The MIT License.
See license.txt or http://reactivecascade.com for details.
This is open source for the common good. Please contribute improvements by pull request or contact paul.houghton@futurice.com
*/
package com.futurice.cascade.functional;

import android.support.annotation.CallSuper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.futurice.cascade.Async;
import com.futurice.cascade.BuildConfig;
import com.futurice.cascade.i.CallOrigin;
import com.futurice.cascade.i.IAction;
import com.futurice.cascade.i.IActionOne;
import com.futurice.cascade.i.IActionOneR;
import com.futurice.cascade.i.IActionR;
import com.futurice.cascade.i.IAltFuture;
import com.futurice.cascade.i.IReactiveTarget;
import com.futurice.cascade.i.ISettableAltFuture;
import com.futurice.cascade.i.IThreadType;
import com.futurice.cascade.i.NotCallOrigin;
import com.futurice.cascade.util.AssertUtil;
import com.futurice.cascade.util.Origin;
import com.futurice.cascade.util.RCLog;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The common base class for default implementations such as {@link SettableAltFuture} and {@link RunnableAltFuture}.
 * Most developers will not need to concern themselves with this abstract class.
 * <p>
 * {@link RunnableAltFuture} overrides this class.
 * TODO You may also use a {@link SettableAltFuture} to inject data where the from is determined from entirely outside of the current chain hierarchy.
 * This is currently an experimental feature so be warned, your results and chain behaviour may vary. Additional
 * testing is on the long list.
 * <p>
 * You may prefer to use {@link ImmutableValue} that a similar need in some cases. That is a
 * slightly faster, simpler implementation than {@link SettableAltFuture}.
 * <p>
 * TODO Would it be helpful for debugging to store and pass forward a reference to the object which originally detected the problem? It might help with filtering what mOnFireAction you want to do mOnError
 */
@NotCallOrigin
public abstract class AbstractAltFuture<IN, OUT> extends Origin implements IAltFuture<IN, OUT> {
    /**
     * A null object meaning "no mind", "unasserted" or "state not yet set".
     * <p>
     * Many use <code>null</code> instead of a <a href=https://en.wikipedia.org/wiki/Null_Object_pattern">null object</a>
     * with a specific meaning. Functional and reactive values in Cascade do not allow null as it both pollutes and obfuscates code.
     * <code>ZEN</code> is true emptiness to future state and explicit choice for a mature object design.
     * The difference matters. Read about Tony Hoare, the inventor of <code>null</code>, referring to it as "my billion dollar mistake".
     * <p>
     * The contract: once the ZEN has been lost, it can not be regained. It may transition to {@link #FORKED},
     * or a final immutable state.
     * <p>
     * <em>A Cup of Tea</em>
     * <p>
     * Nan-in, a Japanese master during the Meiji era (1868-1912), received a university
     * professor who came to inquire about Zen.
     * <p>
     * Nan-in served tea. He poured his visitor's cup full, and subscribe kept on pouring.
     * The professor watched the overflow until he no longer could restrain himself.
     * "It is overfull. No more will go in! "Like this cup," Nan-in said, "you are full
     * of your own opinions and speculations. How can I show you Zen unless you first empty your cup?"
     * <p>
     * {@link "http://www.lotustemple.us/resources/koansandmondo.html"}
     * <p>
     * TODO Document ZEN and apply to use to allow collections and arguments that currently might not accept null to accept null as a first class from. Not yet used in many places.
     */
    public static final State ZEN = new AbstractState() {
        @Override
        public String toString() {
            return "ZEN";
        }
    };

    /**
     * The state returned by a function which has no value, but is finished running.
     * <p>
     * In some functional styles this is (somewhat confusingly) a "Null" object passed along the chain.
     * We prefer to name each state explicity for debuggability and disambiguation.
     */
    public static final State COMPLETE = new AbstractState() {
        @Override
        public String toString() {
            return "COMPLETE";
        }
    };

    /*
     * TODO It should be possible to refactor and eliminate the FORKED state in production builds for performance, using only ZEN plus a single state change
     * This would however result in more debugging difficulty and the loss of certain broken logic tests so
     * it should be configurable for production builds. Library users debugging their logic would not know at they time
     * of .fork() if the operation has already been forked due to an error in their code. They
     * would only find out much later. Perhaps this is acceptable in production since the final state
     * change can occur only once, but impure functions would exert their side effect multiple times if
     * forked multiple times. If the debug pattern remains clear and side effects are idempotent this
     * might be worth the performance gained.
     */
    protected static final State FORKED = new AbstractState() {
        @Override
        public String toString() {
            return "FORKED";
        }
    };
    @NonNull
    protected final AtomicReference<Object> mStateAR = new AtomicReference<>(ZEN);
    @NonNull
    protected final IThreadType mThreadType;
    @NonNull
    protected final CopyOnWriteArrayList<IAltFuture<OUT, ?>> mDownchainAltFutureList = new CopyOnWriteArrayList<>(); // Callable split IThreadType actions to start after this mOnFireAction completes
    @NonNull
    private final AtomicReference<IAltFuture<?, ? extends IN>> mPreviousAltFutureAR = new AtomicReference<>();

    /**
     * Create, from is not yet determined
     *
     * @param threadType on which this alt future will evaluate and fire downchain events
     */
    public AbstractAltFuture(@NonNull final IThreadType threadType) {
        this.mThreadType = threadType;
    }

    @Override // IAltFuture
    @CallOrigin
    @CallSuper
    public boolean cancel(@NonNull final String reason) {
        final AltFutureStateCancelled state = new AltFutureStateCancelled(reason);

        if (mStateAR.compareAndSet(ZEN, state) || mStateAR.compareAndSet(FORKED, state)) {
            RCLog.d(this, "Cancelled: reason=" + reason);
            return true;
        }

        final Object s = mStateAR.get();

        if (s instanceof StateCancelled) {
            RCLog.d(this, "Ignoring duplicate cancel(\"" + reason + "\"). state=" + s);
        } else {
            RCLog.d(this, "Ignoring cancel(\"" + reason + "\"). state=" + s);
        }

        return false;
    }

    @Override
    public boolean cancel(@NonNull final StateError stateError) {
        final Object state = this.mStateAR.get();
        final StateCancelled stateCancelled = new StateCancelled() {
            private final ImmutableValue<String> mOrigin = RCLog.originAsync();

            @NonNull
            @Override
            public ImmutableValue<String> getOrigin() {
                return mOrigin;
            }

            @NonNull
            @Override
            public String getReason() {
                return "Cancelled by upchain error=" + getStateError();
            }

            @Nullable
            @Override
            public StateError getStateError() {
                return stateError;
            }
        };

        if (mStateAR.compareAndSet(ZEN, stateCancelled) || mStateAR.compareAndSet(FORKED, stateCancelled)) {
            RCLog.d(this, "Cancelled from state " + state);
            final Exception e = forEachThen(ignore ->
                    doOnCancelled(stateCancelled));
            if (e != null) {
                RCLog.throwRuntimeException(this, "Problem executing onCancelled() downchain actions", e);
            }

            return true;
        }

        RCLog.d(this, "Ignoring cancel(" + stateError + "). state=" + mStateAR.get());

        return false;
    }

    @Override // IAltFuture
    public boolean isCancelled() {
        return isCancelled(mStateAR.get());
    }

    private boolean isCancelled(@NonNull final Object objectThatMayBeAState) {
        return objectThatMayBeAState instanceof StateCancelled;
    }

    @Override // IAltFuture
    public final boolean isDone() {
        return isDone(mStateAR.get());
    }

    protected boolean isDone(@NonNull final Object state) {
        return state != ZEN && state != FORKED;
    }

    @Override // IAltFuture
    public final boolean isForked() {
        return isForked(mStateAR.get());
    }

    protected boolean isForked(@NonNull final Object state) {
        return state != ZEN; // && !(state instanceof AltFutureStateSetButNotYetForked);
    }

    @Override // IAltFuture
    @NonNull
    public IAltFuture<IN, OUT> fork() {
        final IAltFuture<?, ? extends IN> previousAltFuture = getUpchain();

        if (previousAltFuture != null && !previousAltFuture.isDone()) {
            RCLog.v(this, "Previous IAltFuture not forked, searching upchain: " + previousAltFuture);
            previousAltFuture.fork();
            return this;
        }

        Object s = null;
        if (Async.USE_FORKED_STATE ? !mStateAR.compareAndSet(ZEN, FORKED) : (s = mStateAR.get()) != ZEN) {
            if (s == null) {
                s = mStateAR.get();
            }
            if (s instanceof StateCancelled || s instanceof StateError) {
                RCLog.v(getOrigin(), "Can not fork(), RunnableAltFuture was cancelled: " + s);
                return this;
            }
            RCLog.i(getOrigin(), "Possibly a legitimate race condition. Ignoring duplicate fork(), already fork()ed or set(): " + s);
            return this;
        }
        doFork();

        return this;
    }

    protected abstract void doFork();

    /**
     * Implementations of {@link #fork()} must call this when completed. It reduces the window of time
     * in which past intermediate calculation values in a active chain are held in memory. It is
     * the equivalent of the (illegal) statement:
     * <code>{@link #setUpchain(IAltFuture)}</code> to null.
     * <p>
     * This may not be done until {@link #isDone()} == true, such as when the {@link #fork()} has completed.
     */
    protected final void clearPreviousAltFuture() {
        AssertUtil.assertTrue(isDone());
        this.mPreviousAltFutureAR.lazySet(null);
    }

    @Override // IAltFuture
    @Nullable
    public final IAltFuture<?, ? extends IN> getUpchain() {
        return this.mPreviousAltFutureAR.get();
    }

    @Override // IAltFuture
    public void setUpchain(@NonNull final IAltFuture<?, ? extends IN> altFuture) {
        final boolean set = this.mPreviousAltFutureAR.compareAndSet(null, altFuture);

        if (!set) {
            RCLog.v(this, "Second setUpchain(), merging two chains. Neither can proceed past this point until both burn to this point.");
        }
    }

    @Override // IAltFuture
    @NonNull
    @SuppressWarnings("unchecked")
    public OUT get() {
        final Object state = mStateAR.get();

        if (!isDone(state)) {
            RCLog.throwIllegalStateException(this, getOrigin(), "Attempt to get() RunnableAltFuture that is not yet finished. state=" + state);
        }
        if (isCancelled(state)) {
            RCLog.throwIllegalStateException(this, getOrigin(), "Attempt to get() RunnableAltFuture that is cancelled: state=" + state);
        }

        return (OUT) state;
    }

    @Override // IAltFuture
    @NonNull
    @SuppressWarnings("unchecked")
    public OUT safeGet() {
        final Object state = mStateAR.get();

        if (!isDone(state) || isCancelled(state)) {
            return (OUT) VALUE_NOT_AVAILABLE;
        }

        return (OUT) state;

    }

    @Override // IAltFuture
    @NonNull
    public final IThreadType getThreadType() {
        return this.mThreadType;
    }

    /**
     * Perform some action on an instantaneous snapshot of the list of .subscribe() down-chain actions
     *
     * @param action
     * @throws Exception
     */
    protected Exception forEachThen(@NonNull final IActionOne<IAltFuture<OUT, ?>> action) {
        final Iterator<IAltFuture<OUT, ?>> iterator = mDownchainAltFutureList.iterator();
        Exception exception = null;

        while (iterator.hasNext()) {
            try {
                action.call(iterator.next());
            } catch (Exception e) {
                if (exception == null) {
                    exception = e;
                }
                RCLog.e(this, "Problem with forEachThen(): " + e);
            }
        }

        return exception;
    }

    @NotCallOrigin
    @NonNull
    @SuppressWarnings("unchecked")
    @Override // IAltFuture
    public ISettableAltFuture<OUT> onError(@NonNull IActionOne<Exception> onErrorAction) {
        return (ISettableAltFuture<OUT>) then(new OnErrorAltFuture<OUT>(mThreadType, onErrorAction));
    }

    @NotCallOrigin
    @NonNull
    @SuppressWarnings("unchecked")
    @Override // IAltFuture
    public ISettableAltFuture<OUT> onCancelled(@NonNull IActionOne<String> onCancelledAction) {
        return (ISettableAltFuture<OUT>) then(new OnCancelledAltFuture<OUT>(mThreadType, onCancelledAction));
    }

    @NotCallOrigin
    @Override // IAltFuture
    public void doOnError(@NonNull final StateError stateError) throws Exception {
        RCLog.d(this, "Handling doOnError(): " + stateError);

        if (!this.mStateAR.compareAndSet(ZEN, stateError) || (Async.USE_FORKED_STATE && !this.mStateAR.compareAndSet(FORKED, stateError))) {
            RCLog.i(this, "Will not repeat doOnError() because IAltFuture state is already determined: " + mStateAR.get());
            return;
        }

        final Exception e = forEachThen(af -> {
            af.doOnError(stateError);
        });

        if (e != null) {
            throw e;
        }
    }

    @Override // IAltFuture
    public void doOnCancelled(@NonNull final StateCancelled stateCancelled) throws Exception {
        RCLog.v(this, "Handling doOnCancelled for reason=" + stateCancelled);
        if (!this.mStateAR.compareAndSet(ZEN, stateCancelled) && !this.mStateAR.compareAndSet(FORKED, stateCancelled)) {
            RCLog.i(this, "Can not doOnCancelled because IAltFuture state is already determined: " + mStateAR.get());
            return;
        }

        final Exception e = forEachThen(altFuture -> {
            altFuture.doOnCancelled(stateCancelled);
        });

        if (e != null) {
            throw e;
        }
    }

    //----------------------------------- .then() actions ---------------------------------------------
    protected void doThen() {
        AssertUtil.assertTrue("doThen(): state=" + mStateAR.get(), isDone());

        final Exception e = forEachThen(IAltFuture::fork);
        if (e != null) {
            throw new IllegalStateException("Problem completing downchain actions", e);
        }
    }

    /**
     * Continue downchain actions on the specified {@link IThreadType}
     *
     * @param theadType the thread execution group to change to for the next chain operation
     * @return the previous chain link masked to reflect the new {@link IThreadType}
     */
    @NonNull
    @Override
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<?, OUT> on(@NonNull final IThreadType theadType) {
        if (theadType == mThreadType) {
            return this;
        }

        return then(new SettableAltFuture<>(theadType));
    }

    @NonNull
    @Override
    public IAltFuture<OUT, OUT> then(@NonNull IAction<OUT> action) {
        return then(new RunnableAltFuture<OUT, OUT>(mThreadType, action));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<OUT, OUT> then(@NonNull final IActionOne<OUT> action) {
        return then(new RunnableAltFuture<OUT, OUT>(mThreadType, action));
    }

//    @NonNull
//    @Override
//    @SafeVarargs
//    public final <OTHER_IN> IAltFuture<?, OUT> await(@NonNull IActionTwoR<IN, OTHER_IN, OUT> joinAction,
//                                          @NonNull IAltFuture<?, OTHER_IN>... altFuturesToJoin) {
//        AssertUtil.assertTrue("await(IAltFuture...) with empty list of upchain things to await makes no sense", altFuturesToJoin.length == 0);
//        AssertUtil.assertTrue("await(IAltFuture...) with single item in the list of upchain things to await is confusing. Use .then() instead", altFuturesToJoin.length == 1);
//
//        final SettableAltFuture<?, OUT> outAltFuture = new SettableAltFuture<>(mThreadType);
//        final AtomicInteger downCounter = new AtomicInteger(altFuturesToJoin.length);
//        final AtomicReference<OUT> incrementalOut = new AtomicReference<>(null);
//
//        for (final IAltFuture<?, IN> upchainAltFuture : altFuturesToJoin) {
//            upchainAltFuture
//                    .on(mThreadType)
//                    .then(in -> {
//                        while (true) {
//                            final OUT initialOut = outAltFuture.get();
//                            final OUT currentTryOut = joinAction.call(initialOut, upchainAltFuture.get());
//
//                            if (incrementalOut.compareAndSet(initialOut, currentTryOut)) {
//                                break;
//                            }
//                        }
//                        if (downCounter.decrementAndGet() == 0) {
//                            outAltFuture.set(incrementalOut.get());
//                        }
//                    });
//        }
//
//        return outAltFuture;
//    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public ISettableAltFuture<OUT> then(@NonNull IActionOne<OUT>... actions) {
        AssertUtil.assertTrue("then(IActionOne...) with empty list of upchain things to await makes no sense", actions.length > 0);
        AssertUtil.assertTrue("then(IActionOne...) with single item in the list of upchain things to await is confusing. Use .then() instead", actions.length != 1);

        final IAltFuture<OUT, OUT>[] altFutures = new RunnableAltFuture[actions.length];

        for (int i = 0; i < actions.length; i++) {
            final IActionOne<OUT> a = actions[i];

            altFutures[i] = then(new RunnableAltFuture<>(mThreadType,
                    a::call));
        }

        return await(altFutures);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IActionR<DOWNCHAIN_OUT> action) {
        return then(new RunnableAltFuture<>(mThreadType, action));
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull final IAltFuture<OUT, DOWNCHAIN_OUT> altFuture) {
        altFuture.setUpchain(this);

        this.mDownchainAltFutureList.add(altFuture);
        if (isDone()) {
//            altFuture.map((IActionOne) v -> {
//                visualize(mOrigin.getName(), v.toString(), "RunnableAltFuture");
//            });
            altFuture.fork();
        }

        return altFuture;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public ISettableAltFuture<OUT> then(@NonNull IAction<? extends OUT>... actions) {
        AssertUtil.assertTrue("then(IActionOne...) with empty list of upchain things to await makes no sense", actions.length == 0);
        AssertUtil.assertTrue("then(IActionOne...) with single item in the list of upchain things to await is confusing. Use .then() instead", actions.length == 1);

        final IAltFuture<?, ? extends OUT>[] altFutures = new RunnableAltFuture[actions.length];

        for (int i = 0; i < actions.length; i++) {
            final IAction<? extends OUT> a = actions[i];

            altFutures[i] = then(new RunnableAltFuture<>(mThreadType, a));
        }

        return await(altFutures);
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> map(@NonNull final IActionOneR<OUT, DOWNCHAIN_OUT> action) {
        return then(new RunnableAltFuture<>(mThreadType, action));
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT>[] map(@NonNull IActionOneR<OUT, DOWNCHAIN_OUT>... actions) {
        AssertUtil.assertTrue("map(IActionOneR...) with empty list of upchain things to await makes no sense", actions.length == 0);
        AssertUtil.assertTrue("map(IActionOneR...) with single item in the list of upchain things to await is confusing. Use .then() instead", actions.length == 1);

        final IAltFuture<OUT, DOWNCHAIN_OUT>[] altFutures = new IAltFuture[actions.length];

        for (int i = 0; i < actions.length; i++) {
            final IActionOneR<OUT, DOWNCHAIN_OUT> a = actions[i];

            altFutures[i] = new RunnableAltFuture<OUT, DOWNCHAIN_OUT>(mThreadType, a);
        }

        return altFutures;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public ISettableAltFuture<OUT> sleep(final long sleepTime,
                                         @NonNull final TimeUnit timeUnit) {
        final ISettableAltFuture<OUT> outAltFuture = new SettableAltFuture<>(mThreadType);

        outAltFuture.setUpchain(this);
        final IAltFuture<?, ?> ignore = this.then(() -> {
            Async.TIMER.schedule(() -> {
                outAltFuture.set(get());
            }, sleepTime, timeUnit);
        });

        return outAltFuture;
    }

    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    @Override // IAltFuture
    public ISettableAltFuture<OUT> await(@NonNull IAltFuture<?, ?> altFuture) {
        final ISettableAltFuture<OUT> outAltFuture = new SettableAltFuture<>(mThreadType);

        outAltFuture.setUpchain(this);
        final IAltFuture<?, ?> ignore = altFuture.then(() -> {
            outAltFuture.set(get());
        });

        return outAltFuture;
    }

    @NonNull
    @Override // IAltFuture
    public ISettableAltFuture<OUT> await(@NonNull IAltFuture<?, ?>... altFutures) {
        AssertUtil.assertTrue("await(IAltFuture...) with empty list of upchain things to await makes no sense", altFutures.length > 0);
        AssertUtil.assertTrue("await(IAltFuture...) with single item in the list of upchain things to await is confusing. Use .then() instead", altFutures.length != 1);

        final ISettableAltFuture<OUT> outAltFuture = new SettableAltFuture<>(mThreadType);
        final AtomicInteger downCounter = new AtomicInteger(altFutures.length);

        outAltFuture.setUpchain(this);
        for (final IAltFuture<?, ?> upchainAltFuture : altFutures) {
            final IAltFuture<?, ?> ignore = upchainAltFuture.then(() -> {
                if (downCounter.decrementAndGet() == 0) {
                    outAltFuture.set(get());
                }
            });
        }

        return outAltFuture;
    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, IN> filter(@NonNull final IActionOneR<IN, Boolean> action) {
        return new RunnableAltFuture<>(mThreadType, in -> {
            if (!action.call(in)) {
                cancel("Filtered: " + in);
            }
            return in;
        }
        );
    }

//    @Override // IAltFuture
//    @NonNull
//    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
//    public IAltFuture<List<IN>, List<OUT>> map(@NonNull final IActionOneR<IN, OUT> action) {
//        return new RunnableAltFuture<>(mThreadType,
//                (List<IN> listIN) -> {
//                    //TODO Mapping is single-threaded even for long lists or complex transforms
//                    //TODO Idea: create the list of things to call(), and offer that to other threads in the ThreadType if they have freetime to help out
//                    final List<OUT> outputList = new ArrayList<>(listIN.size());
//                    for (IN IN : listIN) {
//                        outputList.add(action.call(IN));
//                    }
//                    return outputList;
//                }
//        );
//    }

//    @Override // IAltFuture
//    @NonNull
//    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
//    public IAltFuture<List<IN>, List<IN>> filter(@NonNull final IActionOneR<IN, Boolean> action) {
//        return new RunnableAltFuture<>(mThreadType,
//                (List<IN> listIN) -> {
//                    final List<IN> outputList = new ArrayList<>(listIN.size());
//                    for (IN IN : listIN) {
//                        if (action.call(IN)) {
//                            outputList.add(IN);
//                        }
//                    }
//                    return outputList;
//                }
//        );
//    }

    @Override // IAltFuture
    @NonNull
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<OUT, OUT> set(@NonNull final IReactiveTarget<OUT> reactiveTarget) {
        return then(reactiveTarget::fire);
    }

    protected static abstract class AbstractState extends Origin implements IAltFuture.State {
    }

//=============================== End .then() actions ========================================

    @NotCallOrigin
    protected final class AltFutureStateCancelled extends Origin implements StateCancelled {
        final String reason;

        AltFutureStateCancelled(@NonNull final String reason) {
            if (BuildConfig.DEBUG && reason.length() == 0) {
                throw new IllegalArgumentException("You must specify the cancellation reason to keep debugging sane");
            }
            this.reason = reason;
            com.futurice.cascade.util.RCLog.d(this, "Moving to StateCancelled:\n" + this.reason);
        }

        /**
         * The reason this task was cancelled. This is for debug purposes.
         *
         * @return
         */
        @NonNull
        @Override // StateCancelled
        public String getReason() {
            return reason;
        }

        /**
         * If the cancellation is because of an error state change elsewhere, provide the details
         * of that original cause also.
         *
         * @return
         */
        @Nullable
        @Override
        public StateError getStateError() {
            return null;
        }

        @Override // Object
        @NonNull
        public String toString() {
            return "CANCELLED: reason=" + reason;
        }
    }

    /**
     * An atomic state change marking also the reason for entering the exception state
     */
    @NotCallOrigin
    protected final class AltFutureStateError extends Origin implements StateError {
        @NonNull
        final String reason;
        @NonNull
        final Exception e;

        public AltFutureStateError(@NonNull String reason,
                                   @NonNull Exception e) {
            this.reason = reason;
            this.e = e;
            RCLog.e(this, "Moving to StateError:\n" + this.reason, e);
        }

        @Override // State
        @NonNull
        public Exception getException() {
            return e;
        }

        @Override // Object
        @NonNull
        public String toString() {
            return "ERROR: reason=" + reason + " error=" + e;
        }
    }
}
