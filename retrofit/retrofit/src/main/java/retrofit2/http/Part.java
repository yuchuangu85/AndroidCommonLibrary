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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import retrofit2.Converter;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Denotes a single part of a multi-part request.
 * <p>
 * The parameter type on which this annotation exists will be processed in one of three ways:
 * <ul>
 * <li>If the type is {@link okhttp3.MultipartBody.Part} the contents will be used directly. Omit
 * the name from the annotation (i.e., {@code @Part MultipartBody.Part part}).</li>
 * <li>If the type is {@link okhttp3.RequestBody RequestBody} the value will be used
 * directly with its content type. Supply the part name in the annotation (e.g.,
 * {@code @Part("foo") RequestBody foo}).</li>
 * <li>Other object types will be converted to an appropriate representation by using
 * {@linkplain Converter a converter}. Supply the part name in the annotation (e.g.,
 * {@code @Part("foo") Image photo}).</li>
 * </ul>
 * <p>
 * Values may be {@code null} which will omit them from the request body.
 * <p>
 * <pre><code>
 * &#64;Multipart
 * &#64;POST("/")
 * Call&lt;ResponseBody&gt; example(
 *     &#64;Part("description") String description,
 *     &#64;Part(value = "image", encoding = "8-bit") RequestBody image);
 * </code></pre>
 * <p>
 * Part parameters may not be {@code null}.
 *
 * 作用于方法的参数,用于定义Multipart请求的每个part
 * 使用该注解定义的参数,参数值可以为空,为空时,则忽略
 * 使用该注解定义的参数类型有以下3种方式可选:
 * 1, 如果类型是okhttp3.MultipartBody.Part，内容将被直接使用。 省略part中的名称,即 @Part MultipartBody.Part part
 * 2, 如果类型是RequestBody，那么该值将直接与其内容类型一起使用。 在注释中提供part名称（例如，@Part（“foo”）RequestBody foo）。
 * 3, 其他对象类型将通过使用转换器转换为适当的格式。 在注释中提供part名称（例如，@Part（“foo”）Image photo）。
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Part {
    /**
     * The name of the part. Required for all parameter types except
     * {@link okhttp3.MultipartBody.Part}.
     */
    String value() default "";

    /**
     * The {@code Content-Transfer-Encoding} of this part.
     */
    String encoding() default "binary";
}
