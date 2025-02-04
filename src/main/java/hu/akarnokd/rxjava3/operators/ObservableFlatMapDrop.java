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

package hu.akarnokd.rxjava3.operators;

import java.util.concurrent.atomic.*;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Function;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.util.AtomicThrowable;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * FlatMap only one {@link ObservableSource} at a time and ignore upstream values until it terminates.
 * @param <T> the upstream value type
 * @param <R> the output type
 * @since 0.19.0
 */
final class ObservableFlatMapDrop<T, R> extends Observable<R> implements ObservableTransformer<T, R> {

    final Observable<T> source;

    final Function<? super T, ? extends ObservableSource<? extends R>> mapper;

    ObservableFlatMapDrop(Observable<T> source,
            Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public ObservableSource<R> apply(Observable<T> upstream) {
        return new ObservableFlatMapDrop<T, R>(upstream, mapper);
    }

    @Override
    protected void subscribeActual(Observer<? super R> observer) {
        source.subscribe(new FlatMapDropObserver<T, R>(observer, mapper));

    }

    static final class FlatMapDropObserver<T, R>
    implements Observer<T>, Disposable {

        final Observer<? super R> downstream;

        final Function<? super T, ? extends ObservableSource<? extends R>> mapper;

        final FlatMapDropInnerObserver innerObserver;

        final AtomicThrowable errors;

        final AtomicInteger done;

        Disposable upstream;

        volatile boolean active;

        FlatMapDropObserver(Observer<? super R> downstream,
                Function<? super T, ? extends ObservableSource<? extends R>> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.innerObserver = new FlatMapDropInnerObserver();
            this.errors = new AtomicThrowable();
            this.done = new AtomicInteger(1);
        }

        @Override
        public void onSubscribe(Disposable d) {
            if (DisposableHelper.validate(upstream, d)) {
                this.upstream = d;
                downstream.onSubscribe(this);
            }
        }

        @Override
        public void onNext(T t) {
            if (!active) {
                ObservableSource<? extends R> o;

                try {
                    o = ObjectHelper.requireNonNull(mapper.apply(t), "The mapper returned a null ObservableSource");
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    upstream.dispose();
                    onError(ex);
                    return;
                }

                active = true;
                done.incrementAndGet();
                o.subscribe(innerObserver);
            }
        }

        @Override
        public void onError(Throwable e) {
            if (errors.addThrowable(e)) {
                onComplete();
            } else {
                RxJavaPlugins.onError(e);
            }
        }

        @Override
        public void onComplete() {
            if (done.decrementAndGet() == 0) {
                Throwable ex = errors.terminate();
                if (ex == null) {
                    downstream.onComplete();
                } else {
                    downstream.onError(ex);
                }
            }
        }

        @Override
        public void dispose() {
            upstream.dispose();
            DisposableHelper.dispose(innerObserver);
        }

        @Override
        public boolean isDisposed() {
            return upstream.isDisposed();
        }

        void innerNext(R item) {
            downstream.onNext(item);
        }

        void innerError(Throwable e) {
            if (errors.addThrowable(e)) {
                innerComplete();
            } else {
                RxJavaPlugins.onError(e);
            }
        }

        void innerComplete() {
            active = false;
            if (done.decrementAndGet() == 0) {
                Throwable ex = errors.terminate();
                if (ex == null) {
                    downstream.onComplete();
                } else {
                    downstream.onError(ex);
                }
            }
        }

        final class FlatMapDropInnerObserver
        extends AtomicReference<Disposable>
        implements Observer<R> {

            private static final long serialVersionUID = -3707363807296094399L;

            @Override
            public void onSubscribe(Disposable d) {
                DisposableHelper.replace(this, d);
            }

            @Override
            public void onNext(R t) {
                innerNext(t);
            }

            @Override
            public void onError(Throwable e) {
                innerError(e);
            }

            @Override
            public void onComplete() {
                innerComplete();
            }
        }
    }
}
