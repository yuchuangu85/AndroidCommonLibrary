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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Denotes that the request body will use form URL encoding. Fields should be declared as
 * parameters and annotated with {@link Field @Field}.
 * <p>
 * Requests made with this annotation will have {@code application/x-www-form-urlencoded} MIME
 * type. Field names and values will be UTF-8 encoded before being URI-encoded in accordance to
 * <a href="http://tools.ietf.org/html/rfc3986">RFC-3986</a>.
 *
 * 用于修饰Field注解和FieldMap注解
 * 使用该注解,表示请求正文将使用表单网址编码。字段应该声明为参数，并用@Field注释或FieldMap注释。
 * 使用FormUrlEncoded注解的请求将具”application / x-www-form-urlencoded” MIME类型。字段
 * 名称和值将先进行UTF-8进行编码,再根据RFC-3986进行URI编码.
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface FormUrlEncoded {
}
