/*
 * Copyright 2016 David Karnok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.akarnokd.rxjava2.basetypes;

import org.reactivestreams.*;

import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.functions.ObjectHelper;

/**
 * A 0-1-error base reactive type, similar to Maybe, implementing the Reactive-Streams Publisher
 * interface.
 *
 * @param <T> the value type
 * 
 * @since 0.14.0
 */
public abstract class Perhaps<T> implements Publisher<T> {

    @Override
    public final void subscribe(Subscriber<? super T> s) {
        ObjectHelper.requireNonNull(s, "s is null");
        try {
            subscribeActual(s);
        } catch (NullPointerException ex) {
            throw ex;
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            NullPointerException npe = new NullPointerException();
            npe.initCause(ex);
            throw npe;
        }
    }

    /**
     * Implement this method to react to a Subscriber subscribing to this Solo.
     * @param s the downstream Subscriber, never null
     */
    protected abstract void subscribeActual(Subscriber<? super T> s);

}
