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

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import static retrofit2.Utils.methodError;

/**
 * Adapts an invocation of an interface method into an HTTP call.
 * 把对接口方法的调用转为一次 HTTP 调用。
 * 一个 ServiceMethod 对象对应于一个 API interface 的一个方法
 *
 * @param <T>
 */
abstract class ServiceMethod<T> {

    // 解析接口注解
    static <T> ServiceMethod<T> parseAnnotations(Retrofit retrofit, Method method) {
        // 解析方法注解和所有参数注解，并返回请求工厂
        RequestFactory requestFactory = RequestFactory.parseAnnotations(retrofit, method);

        /**
         * 获取请求方法的返回类型，例如下面正常返回{Call<CardConfigInfo>}
         *
         * @FormUrlEncoded
         * @Headers("Content-Type:application/x-www-form-urlencoded")
         * @POST("category/{cat}/")
         * Call<List<Item>> categoryList(@Path("cat") String a, @Query("page") int b);
         *
         * 这里returnType是：Call<List<Item>>
         */
        Type returnType = method.getGenericReturnType();
        if (Utils.hasUnresolvableType(returnType)) {
            throw methodError(method,
                    "Method return type must not include a type variable or wildcard: %s", returnType);
        }
        if (returnType == void.class) {// 接口网络请求方法返回类型不能为空
            throw methodError(method, "Service methods cannot return void.");
        }

        // 返回的是CallAdapted（HttpServiceMethod）
        return HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory);
    }

    abstract @Nullable
    T invoke(Object[] args);
}
