/*
 * Copyright (C) 2016 Square, Inc.
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
package retrofit2.http;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Map;

import retrofit2.Retrofit;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Adds headers specified in the {@link Map} or {@link okhttp3.Headers}.
 * <p>
 * Values in the map are converted to strings using
 * {@link Retrofit#stringConverter(Type, Annotation[])} (or {@link Object#toString()}, if no
 * matching string converter is installed).
 * <p>
 * Simple Example:
 * <pre>
 * &#64;GET("/search")
 * void list(@HeaderMap Map&lt;String, String&gt; headers);
 *
 * ...
 *
 * // The following call yields /search with headers
 * // Accept: text/plain and Accept-Charset: utf-8
 * foo.list(ImmutableMap.of("Accept", "text/plain", "Accept-Charset", "utf-8"));
 * </pre>
 *
 * 作用于方法的参数,用于添加请求头
 * 以map的方式添加多个请求头,map中的key为请求头的名称,value为请求头的值,且value使用String.valueOf()
 * 统一转换为String类型,map中每一项的键和值都不能为空,否则抛出IllegalArgumentException异常
 *
 * @see Header
 * @see Headers
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface HeaderMap {

}
