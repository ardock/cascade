package com.futurice.cascade.active;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.futurice.cascade.i.IAction;
import com.futurice.cascade.i.IActionOne;
import com.futurice.cascade.i.IActionOneR;
import com.futurice.cascade.i.IActionR;
import com.futurice.cascade.i.IAltFuture;
import com.futurice.cascade.i.IOnErrorAction;
import com.futurice.cascade.i.IReactiveTarget;
import com.futurice.cascade.i.IThreadType;
import com.futurice.cascade.util.AssertUtil;
import com.futurice.cascade.util.CLog;
import com.futurice.cascade.util.Origin;

import java.util.ArrayList;
import java.util.List;

/**
 * A chain of two or more {@link com.futurice.cascade.i.IAltFuture}s merged into a single logical entity.
 * <p>
 * This is useful for returning a single logical entity from an async method such that it can receive values
 * at the head and output values to next chain steps at the mTail.
 */
public class CompoundAltFuture<IN, HEAD_OUT, TAIL_IN, OUT> extends Origin implements IAltFuture<IN, OUT> {
    protected final List<IAltFuture<?, ?>> mSubchain = new ArrayList<>();
    protected final IAltFuture<IN, HEAD_OUT> mHead;
    protected final IAltFuture<TAIL_IN, OUT> mTail;

    public CompoundAltFuture(@NonNull final IAltFuture<IN, HEAD_OUT> head,
                             @NonNull final IAltFuture<TAIL_IN, OUT> tail) {
        AssertUtil.assertTrue("Head of CompoundAltFuture must not be downchain from an existing chain", head.getUpchain() == null);
        AssertUtil.assertNotEqual(head, tail);

        mHead = head;
        mTail = tail;

        boolean foundHeadUpchainFromTail;
        IAltFuture<?, ?> previous = tail;
        do {
            mSubchain.add(0, previous);
            foundHeadUpchainFromTail = head.equals(previous);
            previous = previous.getUpchain();
        } while (!foundHeadUpchainFromTail && previous != null);
        mSubchain.add(0, head);
        if (!foundHeadUpchainFromTail) {
            CLog.throwIllegalArgumentException(head, "Head of CompoundAltFuture must be upchain from tail");
        }
    }

    @NonNull
    @Override // IAltFuture
    public IThreadType getThreadType() {
        return mHead.getThreadType();
    }

    @Override // IAltFuture
    public boolean isDone() {
        return mTail.isDone();
    }

    @Override // IAltFuture
    public boolean isForked() {
        return mHead.isForked();
    }

    @Override // IAltFuture
    public boolean cancel(@NonNull String reason) {
        for (final IAltFuture<?, ?> altFuture : mSubchain) {
            if (altFuture.cancel(reason)) {
                return true;
            }
        }

        return false;
    }

    @Override // IAltFuture
    public boolean cancel(@NonNull StateError stateError) {
        for (final IAltFuture<?, ?> altFuture : mSubchain) {
            if (altFuture.cancel(stateError)) {
                CLog.d(this, "Cancelled task within CompountAltFuture");
                return true;
            }
        }

        return false;
    }

    @Override // IAltFuture
    public boolean isCancelled() {
        for (IAltFuture<?, ?> altFuture : mSubchain) {
            if (altFuture.isCancelled()) {
                CLog.d(this, "CompountAltFuture is cancelled");
                return true;
            }
        }

        return false;
    }

    @NonNull
    @Override // IAltFuture
    public IAltFuture<IN, OUT> fork() {
        mHead.fork();
        return this;
    }

    @Nullable
    @Override // IAltFuture
    public <UPCHAIN_IN> IAltFuture<UPCHAIN_IN, IN> getUpchain() {
        return mHead.getUpchain();
    }

    @NonNull
    @Override // IAltFuture
    public <UPCHAIN_IN> IAltFuture<IN, OUT> setUpchain(@NonNull IAltFuture<UPCHAIN_IN, IN> altFuture) {
        mHead.setUpchain(altFuture);
        return this;
    }

    @Override // IAltFuture
    public void doOnError(@NonNull StateError stateError) throws Exception {
        mHead.doOnError(stateError);
    }

    @Override // IAltFuture
    public void doOnCancelled(@NonNull StateCancelled stateCancelled) throws Exception {
        mHead.doOnCancelled(stateCancelled);
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, OUT> then(@NonNull IAction<OUT> action) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.then(action);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    @SuppressWarnings("unchecked")
    public IAltFuture<IN, OUT> then(@NonNull IAction<OUT>... actions) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.then(actions);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, OUT> then(@NonNull IActionOne<OUT> action) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.then(action);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    @SuppressWarnings("unchecked")
    public IAltFuture<IN, OUT> then(@NonNull IActionOne<OUT>... actions) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.then(actions);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull IActionR<OUT, DOWNCHAIN_OUT> action) {
        return mTail.then(action);
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> then(@NonNull IAltFuture<OUT, DOWNCHAIN_OUT> altFuture) {
        return mTail.then(altFuture);
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT> map(@NonNull IActionOneR<OUT, DOWNCHAIN_OUT> action) {
        return mTail.map(action);
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    @SuppressWarnings("unchecked")
    public <DOWNCHAIN_OUT> IAltFuture<OUT, DOWNCHAIN_OUT>[] map(@NonNull IActionOneR<OUT, DOWNCHAIN_OUT>... actions) {
        return mTail.map(actions);
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, OUT> await(@NonNull IAltFuture<?, ?> altFuture) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.await(altFuture);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    @SuppressWarnings("unchecked")
    public IAltFuture<IN, OUT> await(@NonNull IAltFuture<?, OUT>... altFuturesToJoin) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.await(altFuturesToJoin);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, OUT> on(@NonNull IThreadType theadType) {
        if (theadType == mTail.getThreadType()) {
            return this;
        }

        return then(() -> {
        });
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, IN> filter(@NonNull IActionOneR<IN, Boolean> action) {
        return mHead.filter(action);
    }

    @NonNull
    @Override // IAltFuture
    @CheckResult(suggest = IAltFuture.CHECK_RESULT_SUGGESTION)
    public IAltFuture<IN, OUT> set(@NonNull IReactiveTarget<OUT> reactiveTarget) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.set(reactiveTarget);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    public IAltFuture<IN, OUT> onError(@NonNull IOnErrorAction action) {
        final IAltFuture<TAIL_IN, OUT> ignore = mTail.onError(action);
        return this;
    }

    @NonNull
    @Override // IAltFuture
    public OUT safeGet() {
        return mTail.safeGet();
    }

    @NonNull
    @Override // IAltFuture
    public OUT get() {
        return mTail.get();
    }
}
