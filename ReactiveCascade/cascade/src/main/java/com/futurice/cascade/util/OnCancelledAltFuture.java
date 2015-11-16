package com.futurice.cascade.util;

import android.support.annotation.NonNull;

import com.futurice.cascade.active.RunnableAltFuture;
import com.futurice.cascade.i.IActionOne;
import com.futurice.cascade.i.IThreadType;

/**
 * Created by phou on 14-Nov-15.
 */
public class OnCancelledAltFuture<IN, OUT> extends RunnableAltFuture<IN, OUT> {
    /**
     * Constructor
     *
     * @param threadType the thread pool to run this command on
     * @param action     a function that receives one input and no return from
     */
    @SuppressWarnings("unchecked")
    public OnCancelledAltFuture(@NonNull IThreadType threadType,
                                @NonNull IActionOne<String> action) {
        super(threadType, (IActionOne<IN>) action);
    }

    @Override // IAltFuture
    public void doOnCancelled(@NonNull final StateCancelled stateCancelled) throws Exception {
        CLog.v(this, "Handling doOnCancelled for reason=" + stateCancelled);
        this.mStateAR.set(stateCancelled);

        //FIXME Continue here
//        if (oe != null) {
//            oe.call(cancellationException);
//        }
//        forEachThen(altFuture -> {
//            altFuture.doOnCancelled(cancellationException);
//        });
    }
}
