/*
 * Copyright (C) 2011 Square, Inc.
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
import retrofit2.Retrofit;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Use this annotation on a service method param when you want to directly control the request body
 * of a POST/PUT request (instead of sending in as request parameters or form-style request
 * body). The object will be serialized using the {@link Retrofit Retrofit} instance
 * {@link Converter Converter} and the result will be set directly as the
 * request body.
 * <p>
 * Body parameters may not be {@code null}.
 *
 * 作用于方法的参数
 * 使用该注解定义的参数不可为null
 * 当你发送一个post或put请求,但是又不想作为请求参数或表单的方式发送请求时,使用该注解定义的参数可以
 * 直接传入一个实体类,retrofit会通过convert把该实体序列化并将序列化后的结果直接作为请求体发送出去.
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Body {
}
