/*
 * Copyright (C) 2013 Square, Inc.
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

import retrofit2.Retrofit;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Named pair for a form-encoded request.
 * <p>
 * Values are converted to strings using {@link Retrofit#stringConverter(Type, Annotation[])}
 * (or {@link Object#toString()}, if no matching string converter is installed)
 * and then form URL encoded.
 * {@code null} values are ignored. Passing a {@link java.util.List List} or array will result in a
 * field pair for each non-{@code null} item.
 * <p>
 * Simple Example:
 * <pre><code>
 * &#64;FormUrlEncoded
 * &#64;POST("/")
 * Call&lt;ResponseBody&gt; example(
 *     &#64;Field("name") String name,
 *     &#64;Field("occupation") String occupation);
 * </code></pre>
 * Calling with {@code foo.example("Bob Smith", "President")} yields a request body of
 * {@code name=Bob+Smith&occupation=President}.
 * <p>
 * Array/Varargs Example:
 * <pre><code>
 * &#64;FormUrlEncoded
 * &#64;POST("/list")
 * Call&lt;ResponseBody&gt; example(@Field("name") String... names);
 * </code></pre>
 * Calling with {@code foo.example("Bob Smith", "Jane Doe")} yields a request body of
 * {@code name=Bob+Smith&name=Jane+Doe}.
 *
 * 作用于方法的参数（放到请求体中）
 * 用于发送一个表单请求
 * 用String.valueOf()把参数值转换为String,然后进行URL编码,当参数值为null值时,会自动忽略,如果传入的是一个List或array,
 * 则为每一个非空的item拼接一个键值对,每一个键值对中的键是相同的,值就是非空item的值,如: name=张三&name=李四&name=王五,
 * 另外,如果item的值有空格,在拼接时会自动忽略,例如某个item的值为:张 三,则拼接后为name=张三.
 *
 * @see FormUrlEncoded
 * @see FieldMap
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Field {
    String value();

    /**
     * Specifies whether the {@linkplain #value() name} and value are already URL encoded.
     */
    boolean encoded() default false;
}
