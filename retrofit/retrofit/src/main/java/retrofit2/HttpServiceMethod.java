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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import kotlin.coroutines.Continuation;
import okhttp3.ResponseBody;

import static retrofit2.Utils.getRawType;
import static retrofit2.Utils.methodError;

/**
 * Adapts an invocation of an interface method into an HTTP call.
 *
 * 适配一个接口方法的一次调用为一个http call（ServiceMethod）
 */
abstract class HttpServiceMethod<ResponseT, ReturnT> extends ServiceMethod<ReturnT> {
    /**
     * Inspects the annotations on an interface method to construct a reusable service method that
     * speaks HTTP. This requires potentially-expensive reflection so it is best to build each service
     * method only once and reuse it.
     */
    static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(
            Retrofit retrofit, Method method, RequestFactory requestFactory) {
        boolean isKotlinSuspendFunction = requestFactory.isKotlinSuspendFunction;
        boolean continuationWantsResponse = false;
        boolean continuationBodyNullable = false;

        // 获取该方法的注解数组
        Annotation[] annotations = method.getAnnotations();
        Type adapterType;
        if (isKotlinSuspendFunction) {
            Type[] parameterTypes = method.getGenericParameterTypes();
            Type responseType = Utils.getParameterLowerBound(0,
                    (ParameterizedType) parameterTypes[parameterTypes.length - 1]);
            if (getRawType(responseType) == Response.class && responseType instanceof ParameterizedType) {
                // Unwrap the actual body type from Response<T>.
                responseType = Utils.getParameterUpperBound(0, (ParameterizedType) responseType);
                continuationWantsResponse = true;
            } else {
                // TODO figure out if type is nullable or not
                // Metadata metadata = method.getDeclaringClass().getAnnotation(Metadata.class)
                // Find the entry for method
                // Determine if return type is nullable or not
            }

            adapterType = new Utils.ParameterizedTypeImpl(null, Call.class, responseType);
            annotations = SkipCallbackExecutorImpl.ensurePresent(annotations);
        } else {
            // 接口方法返回值类型
            adapterType = method.getGenericReturnType();
        }

        /**
         * 添加默认CallAdapter.Factory
         * 1.Android平台 sdk>=24 添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
         * 2.Android平台 sdk<24 只添加DefaultCallAdapterFactory
         * 3.java平台添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
         *
         * sdk>=24：callAdapter = ResponseCallAdapter -> (CompletableFutureCallAdapterFactory)
         * sdk<24：callAdapter = CallAdapter -> (DefaultCallAdapterFactory)
         *
         * 这里会根据数据请求接口返回类型来获取callAdapter，我们Android开发通常为Call<Object>类型，所以返回
         * DefaultCallAdapterFactory.CallAdapter
         */
        CallAdapter<ResponseT, ReturnT> callAdapter =
                createCallAdapter(retrofit, method, adapterType, annotations);
        // 接口声明的返回类型例如Call<User>,那么这个就是User
        Type responseType = callAdapter.responseType();
        if (responseType == okhttp3.Response.class) {
            throw methodError(method, "'"
                    + getRawType(responseType).getName()
                    + "' is not a valid response body type. Did you mean ResponseBody?");
        }
        if (responseType == Response.class) {
            throw methodError(method, "Response must include generic type (e.g., Response<String>)");
        }
        // TODO support Unit for Kotlin?
        if (requestFactory.httpMethod.equals("HEAD") && !Void.class.equals(responseType)) {
            throw methodError(method, "HEAD method must use Void as response type.");
        }

        // 请求结果转换器(通常为GsonConverterFactory)
        Converter<ResponseBody, ResponseT> responseConverter =
                createResponseConverter(retrofit, method, responseType);

        okhttp3.Call.Factory callFactory = retrofit.callFactory;
        if (!isKotlinSuspendFunction) {
            return new CallAdapted<>(requestFactory, callFactory, responseConverter, callAdapter);
        } else if (continuationWantsResponse) {
            //noinspection unchecked Kotlin compiler guarantees ReturnT to be Object.
            return (HttpServiceMethod<ResponseT, ReturnT>) new SuspendForResponse<>(requestFactory,
                    callFactory, responseConverter, (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter);
        } else {
            //noinspection unchecked Kotlin compiler guarantees ReturnT to be Object.
            return (HttpServiceMethod<ResponseT, ReturnT>) new SuspendForBody<>(requestFactory,
                    callFactory, responseConverter, (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter,
                    continuationBodyNullable);
        }
    }

    private static <ResponseT, ReturnT> CallAdapter<ResponseT, ReturnT> createCallAdapter(
            Retrofit retrofit, Method method, Type returnType, Annotation[] annotations) {
        try {
            //noinspection unchecked
            return (CallAdapter<ResponseT, ReturnT>) retrofit.callAdapter(returnType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create call adapter for %s", returnType);
        }
    }

    private static <ResponseT> Converter<ResponseBody, ResponseT> createResponseConverter(
            Retrofit retrofit, Method method, Type responseType) {
        Annotation[] annotations = method.getAnnotations();
        try {
            return retrofit.responseBodyConverter(responseType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create converter for %s", responseType);
        }
    }

    private final RequestFactory requestFactory;
    private final okhttp3.Call.Factory callFactory;// OkHttpClient
    private final Converter<ResponseBody, ResponseT> responseConverter;// 请求相应转换器

    HttpServiceMethod(RequestFactory requestFactory, okhttp3.Call.Factory callFactory,
                      Converter<ResponseBody, ResponseT> responseConverter) {
        this.requestFactory = requestFactory;
        this.callFactory = callFactory;
        this.responseConverter = responseConverter;
    }

    // 提交一个http请求，返回我们设定的类型的结果，例如Call<User>
    @Override
    final @Nullable
    ReturnT invoke(Object[] args) {
        // 创建OkHttpCall对象(封装了OkHttp的请求)，用来发起网络请求
        Call<ResponseT> call = new OkHttpCall<>(requestFactory, args, callFactory, responseConverter);
        return adapt(call, args);
    }

    // 这里call是OkHttpCall
    protected abstract @Nullable
    ReturnT adapt(Call<ResponseT> call, Object[] args);

    static final class CallAdapted<ResponseT, ReturnT> extends HttpServiceMethod<ResponseT, ReturnT> {

        /**
         * 添加默认CallAdapter.Factory
         * 1.Android平台 sdk>=24 添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
         * 2.Android平台 sdk<24 只添加DefaultCallAdapterFactory
         * 3.java平台添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
         *
         * 根据定义的请求接口类型Call<Object>返回DefaultCallAdapterFactory.CallAdapter
         * （如果定义的请求接口返回类型为CompletableFuture<Object>，那么
         * 返回CompletableFutureCallAdapterFactory.CallAdapter）
         */
        private final CallAdapter<ResponseT, ReturnT> callAdapter;

        CallAdapted(RequestFactory requestFactory, okhttp3.Call.Factory callFactory,
                    Converter<ResponseBody, ResponseT> responseConverter,
                    CallAdapter<ResponseT, ReturnT> callAdapter) {
            super(requestFactory, callFactory, responseConverter);
            this.callAdapter = callAdapter;
        }

        // 返回类型（ReturnT）为Call<Object>
        @Override
        protected ReturnT adapt(Call<ResponseT> call, Object[] args) {
            // DefaultCallAdapterFactory返回ExecutorCallbackCall
            return callAdapter.adapt(call);
        }
    }

    static final class SuspendForResponse<ResponseT> extends HttpServiceMethod<ResponseT, Object> {
        private final CallAdapter<ResponseT, Call<ResponseT>> callAdapter;

        SuspendForResponse(RequestFactory requestFactory, okhttp3.Call.Factory callFactory,
                           Converter<ResponseBody, ResponseT> responseConverter,
                           CallAdapter<ResponseT, Call<ResponseT>> callAdapter) {
            super(requestFactory, callFactory, responseConverter);
            this.callAdapter = callAdapter;
        }

        @Override
        protected Object adapt(Call<ResponseT> call, Object[] args) {
            call = callAdapter.adapt(call);

            //noinspection unchecked Checked by reflection inside RequestFactory.
            Continuation<Response<ResponseT>> continuation =
                    (Continuation<Response<ResponseT>>) args[args.length - 1];
            return KotlinExtensions.awaitResponse(call, continuation);
        }
    }

    static final class SuspendForBody<ResponseT> extends HttpServiceMethod<ResponseT, Object> {
        private final CallAdapter<ResponseT, Call<ResponseT>> callAdapter;
        private final boolean isNullable;

        SuspendForBody(RequestFactory requestFactory, okhttp3.Call.Factory callFactory,
                       Converter<ResponseBody, ResponseT> responseConverter,
                       CallAdapter<ResponseT, Call<ResponseT>> callAdapter, boolean isNullable) {
            super(requestFactory, callFactory, responseConverter);
            this.callAdapter = callAdapter;
            this.isNullable = isNullable;
        }

        @Override
        protected Object adapt(Call<ResponseT> call, Object[] args) {
            call = callAdapter.adapt(call);

            //noinspection unchecked Checked by reflection inside RequestFactory.
            Continuation<ResponseT> continuation = (Continuation<ResponseT>) args[args.length - 1];
            return isNullable
                    ? KotlinExtensions.awaitNullable(call, continuation)
                    : KotlinExtensions.await(call, continuation);
        }
    }
}
