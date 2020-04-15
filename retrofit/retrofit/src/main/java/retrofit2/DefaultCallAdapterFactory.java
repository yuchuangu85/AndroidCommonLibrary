/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import okhttp3.Request;

/**
 * Andrioid平台默认请求适配器工厂
 */
final class DefaultCallAdapterFactory extends CallAdapter.Factory {
    private final @Nullable
    Executor callbackExecutor;

    DefaultCallAdapterFactory(@Nullable Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    @Override
    public @Nullable
    CallAdapter<?, ?> get(
            Type returnType, Annotation[] annotations, Retrofit retrofit) {
        // 返回的类型必须是Call 类型
        if (getRawType(returnType) != Call.class) {
            return null;
        }
        if (!(returnType instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Call return type must be parameterized as Call<Foo> or Call<? extends Foo>");
        }
        // 例如returnType为：List<User> 那么responseType 就是User
        final Type responseType = Utils.getParameterUpperBound(0, (ParameterizedType) returnType);

        // 注解中是否包含SkipCallbackExecutor注解
        // callbackExecutor：Android平台为MainThreadExecutor，Java平台为null
        final Executor executor = Utils.isAnnotationPresent(annotations, SkipCallbackExecutor.class)
                ? null
                : callbackExecutor;

        return new CallAdapter<Object, Call<?>>() {
            @Override
            public Type responseType() {
                return responseType;
            }

            @Override
            public Call<Object> adapt(Call<Object> call) {
                return executor == null
                        ? call
                        : new ExecutorCallbackCall<>(executor, call);
            }
        };
    }

    // 这里使用了典型的代理模式，不过这次是静态代理。ExecutorCallbackCall 代理了 OkHttpCall，
    // 他们都实现了接口Call。这样代理就有能力在执行被代理对象的动作是附加一些动作了，
    // 例如这里的线程切换。妙哉否？妙哉！
    static final class ExecutorCallbackCall<T> implements Call<T> {
        final Executor callbackExecutor;
        // 代理了 OkHttpCall
        final Call<T> delegate;

        ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
            this.callbackExecutor = callbackExecutor;
            this.delegate = delegate;
        }

        @Override
        public void enqueue(final Callback<T> callback) {
            Objects.requireNonNull(callback, "callback == null");

            // 这里最终调用到OkHttpCall.enqueue方法
            delegate.enqueue(new Callback<T>() {
                @Override
                public void onResponse(Call<T> call, final Response<T> response) {
                    callbackExecutor.execute(() -> {
                        if (delegate.isCanceled()) {
                            // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                            callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                        } else {
                            callback.onResponse(ExecutorCallbackCall.this, response);
                        }
                    });
                }

                @Override
                public void onFailure(Call<T> call, final Throwable t) {
                    callbackExecutor.execute(() -> callback.onFailure(ExecutorCallbackCall.this, t));
                }
            });
        }

        @Override
        public boolean isExecuted() {
            return delegate.isExecuted();
        }

        @Override
        public Response<T> execute() throws IOException {
            return delegate.execute();
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCanceled() {
            return delegate.isCanceled();
        }

        @SuppressWarnings("CloneDoesntCallSuperClone") // Performing deep clone.
        @Override
        public Call<T> clone() {
            return new ExecutorCallbackCall<>(callbackExecutor, delegate.clone());
        }

        @Override
        public Request request() {
            return delegate.request();
        }
    }
}
