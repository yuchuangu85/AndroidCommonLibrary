/*
 * Copyright (C) 2012 Square, Inc.
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

import javax.annotation.Nullable;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * Convert objects to and from their representation in HTTP. Instances are created by {@linkplain
 * Factory a factory} which is {@linkplain Retrofit.Builder#addConverterFactory(Factory) installed}
 * into the {@link Retrofit} instance.
 * <p>
 * 转化器接口(将okhttp返回的原始结果转换成需要的结果，例如：就返回的String字符串通过Gson转换器转换成需要的对象)
 * F：来源类型(From)
 * T：目标类型(To)
 */
public interface Converter<F, T> {
    @Nullable
    T convert(F value) throws IOException;

    /**
     * Creates {@link Converter} instances based on a type and target usage.
     * <p>
     * 转换器工厂接口，通过工厂模式创建转换器
     */
    abstract class Factory {
        /**
         * Returns a {@link Converter} for converting an HTTP response body to {@code type}, or null if
         * {@code type} cannot be handled by this factory. This is used to create converters for
         * response types such as {@code SimpleResponse} from a {@code Call<SimpleResponse>}
         * declaration.
         * <p>
         * 返回结果转换器（就是将返回的数据转换成需要的结果对象的转换器）
         * <p>
         *
         * @param type 返回数据的类型
         */
        public @Nullable
        Converter<ResponseBody, ?> responseBodyConverter(Type type,
                                                         Annotation[] annotations, Retrofit retrofit) {
            return null;
        }

        /**
         * Returns a {@link Converter} for converting {@code type} to an HTTP request body, or null if
         * {@code type} cannot be handled by this factory. This is used to create converters for types
         * specified by {@link Body @Body}, {@link Part @Part}, and {@link PartMap @PartMap}
         * values.
         * <p>
         * 请求体转换器（就是将请求数据转转换成请求对象Request的转换器）
         */
        public @Nullable
        Converter<?, RequestBody> requestBodyConverter(Type type,
                                                       Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
            return null;
        }

        /**
         * Returns a {@link Converter} for converting {@code type} to a {@link String}, or null if
         * {@code type} cannot be handled by this factory. This is used to create converters for types
         * specified by {@link Field @Field}, {@link FieldMap @FieldMap} values,
         * {@link Header @Header}, {@link HeaderMap @HeaderMap}, {@link Path @Path},
         * {@link Query @Query}, and {@link QueryMap @QueryMap} values.
         * <p>
         * 将请求参数转换为String的转换器
         */
        public @Nullable
        Converter<?, String> stringConverter(Type type, Annotation[] annotations,
                                             Retrofit retrofit) {
            return null;
        }

        /**
         * Extract the upper bound of the generic parameter at {@code index} from {@code type}. For
         * example, index 1 of {@code Map<String, ? extends Runnable>} returns {@code Runnable}.
         */
        protected static Type getParameterUpperBound(int index, ParameterizedType type) {
            return Utils.getParameterUpperBound(index, type);
        }

        /**
         * Extract the raw class type from {@code type}. For example, the type representing
         * {@code List<? extends Runnable>} returns {@code List.class}.
         */
        protected static Class<?> getRawType(Type type) {
            return Utils.getRawType(type);
        }
    }
}
