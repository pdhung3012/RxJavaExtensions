/*
 * Copyright 2016-2019 David Karnok
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

package hu.akarnokd.rxjava3.basetypes;

import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import io.reactivex.internal.subscriptions.DeferredScalarSubscription;

/**
 * Cache the success value or error from upstream and relay/replay
 * them to subscribers.
 *
 * @param <T> the value type
 *
 * @since 0.14.1
 */
final class PerhapsCache<T> extends Perhaps<T> implements Subscriber<T> {

    @SuppressWarnings("rawtypes")
    static final CacheSubscription[] EMPTY = new CacheSubscription[0];

    @SuppressWarnings("rawtypes")
    static final CacheSubscription[] TERMINATED = new CacheSubscription[0];

    final Perhaps<T> source;

    final AtomicBoolean once;

    final AtomicReference<CacheSubscription<T>[]> subscribers;

    T value;
    Throwable error;

    @SuppressWarnings("unchecked")
    PerhapsCache(Perhaps<T> source) {
        this.source = source;
        this.once = new AtomicBoolean();
        this.subscribers = new AtomicReference<CacheSubscription<T>[]>(EMPTY);
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        CacheSubscription<T> inner = new CacheSubscription<T>(s, this);
        s.onSubscribe(inner);

        if (add(inner)) {
            if (inner.isCancelled()) {
                remove(inner);
            }
            if (once.compareAndSet(false, true)) {
                source.subscribe(this);
            }
        } else {
            if (!inner.isCancelled()) {
                Throwable ex = error;
                if (ex != null) {
                    inner.error(ex);
                } else {
                    T v = value;
                    if (v != null) {
                        inner.complete(v);
                    } else {
                        inner.complete();
                    }
                }
            }
        }
    }

    boolean add(CacheSubscription<T> inner) {
        for (;;) {
            CacheSubscription<T>[] a = subscribers.get();
            if (a == TERMINATED) {
                return false;
            }
            int n = a.length;

            @SuppressWarnings("unchecked")
            CacheSubscription<T>[] b = new CacheSubscription[n + 1];
            System.arraycopy(a, 0, b, 0, n);
            b[n] = inner;
            if (subscribers.compareAndSet(a, b)) {
                return true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    void remove(CacheSubscription<T> inner) {
        for (;;) {
            CacheSubscription<T>[] a = subscribers.get();
            int n = a.length;
            if (n == 0) {
                break;
            }

            int j = -1;

            for (int i = 0; i < n; i++) {
                if (a[i] == inner) {
                    j = i;
                    break;
                }
            }

            if (j < 0) {
                break;
            }

            CacheSubscription<T>[] b;
            if (n == 1) {
                b = EMPTY;
            } else {
                b = new CacheSubscription[n - 1];
                System.arraycopy(a, 0, b, 0, j);
                System.arraycopy(a, j + 1, b, j, n - j - 1);
            }
            if (subscribers.compareAndSet(a, b)) {
                break;
            }
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {
        value = t;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onError(Throwable t) {
        error = t;
        for (CacheSubscription<T> inner : subscribers.getAndSet(TERMINATED)) {
            inner.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onComplete() {
        T t = value;
        CacheSubscription<T>[] inners = subscribers.getAndSet(TERMINATED);
        if (t != null) {
            for (CacheSubscription<T> inner : inners) {
                inner.complete(t);
            }
        } else {
            for (CacheSubscription<T> inner : inners) {
                inner.complete();
            }
        }
    }

    static final class CacheSubscription<T> extends DeferredScalarSubscription<T> {

        private static final long serialVersionUID = -44000898247441619L;

        final AtomicReference<Object> parent;

        CacheSubscription(Subscriber<? super T> downstream, PerhapsCache<T> parent) {
            super(downstream);
            this.parent = new AtomicReference<Object>(parent);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void cancel() {
            super.cancel();
            Object o = parent.get();
            if (o != null && parent.compareAndSet(o, null)) {
                ((PerhapsCache<T>)o).remove(this);
            }
        }

        void error(Throwable ex) {
            downstream.onError(ex);
        }

        void complete() {
            downstream.onComplete();
        }
    }
}
