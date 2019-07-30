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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Replaces the header with the value of its target.
 * <pre><code>
 * &#64;GET("/")
 * Call&lt;ResponseBody&gt; foo(@Header("Accept-Language") String lang);
 * </code></pre>
 * Header parameters may be {@code null} which will omit them from the request. Passing a
 * {@link java.util.List List} or array will result in a header for each non-{@code null} item.
 * <p>
 * <strong>Note:</strong> Headers do not overwrite each other. All headers with the same name will
 * be included in the request.
 *
 * 作用于方法的参数,用于添加请求头
 * 使用该注解定义的请求头可以为空,当为空时,会自动忽略,当传入一个List或array时,为拼接每个非空的item的值到请求头中.
 * 具有相同名称的请求头不会相互覆盖,而是会照样添加到请求头中
 *
 * @see Headers
 * @see HeaderMap
 */
@Documented
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface Header {
    String value();
}
