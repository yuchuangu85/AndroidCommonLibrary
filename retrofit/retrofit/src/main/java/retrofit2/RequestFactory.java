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
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import kotlin.coroutines.Continuation;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.Multipart;
import retrofit2.http.OPTIONS;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.QueryName;
import retrofit2.http.Tag;
import retrofit2.http.Url;

import static retrofit2.Utils.methodError;
import static retrofit2.Utils.parameterError;

/**
 * 请求工厂，通过该工厂模式创建请求对象Request（okhttp3.Request）
 */
final class RequestFactory {
    static RequestFactory parseAnnotations(Retrofit retrofit, Method method) {
        return new Builder(retrofit, method).build();
    }

    private final Method method;
    private final HttpUrl baseUrl;
    final String httpMethod;
    private final @Nullable
    String relativeUrl;
    private final @Nullable
    Headers headers;
    private final @Nullable
    MediaType contentType;
    private final boolean hasBody;
    private final boolean isFormEncoded;
    private final boolean isMultipart;
    private final ParameterHandler<?>[] parameterHandlers;
    final boolean isKotlinSuspendFunction;

    RequestFactory(Builder builder) {
        method = builder.method;
        baseUrl = builder.retrofit.baseUrl;
        httpMethod = builder.httpMethod;
        relativeUrl = builder.relativeUrl;
        headers = builder.headers;
        contentType = builder.contentType;
        hasBody = builder.hasBody;
        isFormEncoded = builder.isFormEncoded;
        isMultipart = builder.isMultipart;
        parameterHandlers = builder.parameterHandlers;
        isKotlinSuspendFunction = builder.isKotlinSuspendFunction;
    }

    /**
     * 创建了一个okhttp 的 Request，里面使用到了RequestBuilder 类，这个类负责使用RequestFactory类
     * 处理出来的信息构建http请求
     *
     * @param args 网络请求接口参数
     *
     * @return Request
     *
     * @throws IOException
     */
    okhttp3.Request create(Object[] args) throws IOException {
        // 下面的代码比较关键，通过参数信息来完善RequestBuilder ，而这些参数信息存放在parameterHandlers里面，
        // 它是通过parseParameter（）函数解析出来的
        @SuppressWarnings("unchecked") // It is an error to invoke a method with the wrong arg types.
                ParameterHandler<Object>[] handlers = (ParameterHandler<Object>[]) parameterHandlers;

        int argumentCount = args.length;
        if (argumentCount != handlers.length) {
            throw new IllegalArgumentException("Argument count (" + argumentCount
                    + ") doesn't match expected count (" + handlers.length + ")");
        }

        RequestBuilder requestBuilder = new RequestBuilder(httpMethod, baseUrl, relativeUrl,
                headers, contentType, hasBody, isFormEncoded, isMultipart);

        if (isKotlinSuspendFunction) {
            // The Continuation is the last parameter and the handlers array contains null at that index.
            argumentCount--;
        }

        // 关键代码 使用上面得到的ParameterHandler来配置 RequestBuilder
        List<Object> argumentList = new ArrayList<>(argumentCount);
        for (int p = 0; p < argumentCount; p++) {
            argumentList.add(args[p]);
            handlers[p].apply(requestBuilder, args[p]);
        }

        // 构建Request，并传入参数数组
        return requestBuilder.get()
                .tag(Invocation.class, new Invocation(method, argumentList))
                .build();
    }

    /**
     * Inspects the annotations on an interface method to construct a reusable service method. This
     * requires potentially-expensive reflection so it is best to build each service method only once
     * and reuse it. Builders cannot be reused.
     */
    static final class Builder {
        // Upper and lower characters, digits, underscores, and hyphens, starting with a character.
        private static final String PARAM = "[a-zA-Z][a-zA-Z0-9_-]*";
        private static final Pattern PARAM_URL_REGEX = Pattern.compile("\\{(" + PARAM + ")\\}");
        private static final Pattern PARAM_NAME_REGEX = Pattern.compile(PARAM);

        final Retrofit retrofit;
        final Method method;// 请求数据接口方法
        final Annotation[] methodAnnotations;// 方法上的所有注解数组
        final Annotation[][] parameterAnnotationsArray;// 接口方法中的参数注解数组(每个参数可能对应多个注解)
        final Type[] parameterTypes;// 参数类型数组

        boolean gotField;
        boolean gotPart;
        boolean gotBody;
        boolean gotPath;
        boolean gotQuery;
        boolean gotQueryName;
        boolean gotQueryMap;
        boolean gotUrl;
        @Nullable
        String httpMethod;
        boolean hasBody;
        boolean isFormEncoded;
        boolean isMultipart;
        @Nullable
        String relativeUrl;
        @Nullable
        Headers headers;
        @Nullable
        MediaType contentType;
        @Nullable
        Set<String> relativeUrlParamNames;
        @Nullable
        ParameterHandler<?>[] parameterHandlers;
        boolean isKotlinSuspendFunction;

        Builder(Retrofit retrofit, Method method) {
            this.retrofit = retrofit;
            this.method = method;
            // 返回请求方法的注解数组
            this.methodAnnotations = method.getAnnotations();
            // 返回的是请求方法中形参的类型数组
            this.parameterTypes = method.getGenericParameterTypes();
            // 返回的是方法参数的注解数组，如果一个注解对应多个参数，则会返回一个嵌套数组
            this.parameterAnnotationsArray = method.getParameterAnnotations();
        }

        RequestFactory build() {
            // 解析方法上的注解，例如@GET,@POST等
            for (Annotation annotation : methodAnnotations) {
                // 单独解析每一个方法注解内容
                parseMethodAnnotation(annotation);
            }

            if (httpMethod == null) {
                throw methodError(method, "HTTP method annotation is required (e.g., @GET, @POST, etc.).");
            }

            if (!hasBody) {
                if (isMultipart) {
                    throw methodError(method,
                            "Multipart can only be specified on HTTP methods with request body (e.g., @POST).");
                }
                if (isFormEncoded) {
                    throw methodError(method, "FormUrlEncoded can only be specified on HTTP methods with "
                            + "request body (e.g., @POST).");
                }
            }

            int parameterCount = parameterAnnotationsArray.length;
            parameterHandlers = new ParameterHandler<?>[parameterCount];
            // 解析参数注解，如@Path,@Query等
            // 将处理方法参数注解的ParameterHandler放在一个数组中parameterHandlers
            for (int p = 0, lastParameter = parameterCount - 1; p < parameterCount; p++) {
                // 每一个参数初始化一个ParameterHandler
                // parameterTypes[p]：参数的类型是 @QueryMap Map<String,String> map，那么这个值就是Map<String,String>
                parameterHandlers[p] =
                        parseParameter(p, parameterTypes[p], parameterAnnotationsArray[p], p == lastParameter);
            }

            if (relativeUrl == null && !gotUrl) {
                throw methodError(method, "Missing either @%s URL or @Url parameter.", httpMethod);
            }
            if (!isFormEncoded && !isMultipart && !hasBody && gotBody) {
                throw methodError(method, "Non-body HTTP method cannot contain @Body.");
            }
            if (isFormEncoded && !gotField) {
                throw methodError(method, "Form-encoded method must contain at least one @Field.");
            }
            if (isMultipart && !gotPart) {
                throw methodError(method, "Multipart method must contain at least one @Part.");
            }

            return new RequestFactory(this);
        }

        // 解析每一个方法注解(各个注解的含义查看各个注解文件)
        private void parseMethodAnnotation(Annotation annotation) {
            if (annotation instanceof DELETE) {
                parseHttpMethodAndPath("DELETE", ((DELETE) annotation).value(), false);
            } else if (annotation instanceof GET) {
                parseHttpMethodAndPath("GET", ((GET) annotation).value(), false);
            } else if (annotation instanceof HEAD) {
                parseHttpMethodAndPath("HEAD", ((HEAD) annotation).value(), false);
            } else if (annotation instanceof PATCH) {
                parseHttpMethodAndPath("PATCH", ((PATCH) annotation).value(), true);
            } else if (annotation instanceof POST) {
                parseHttpMethodAndPath("POST", ((POST) annotation).value(), true);
            } else if (annotation instanceof PUT) {
                parseHttpMethodAndPath("PUT", ((PUT) annotation).value(), true);
            } else if (annotation instanceof OPTIONS) {
                parseHttpMethodAndPath("OPTIONS", ((OPTIONS) annotation).value(), false);
            } else if (annotation instanceof HTTP) {
                HTTP http = (HTTP) annotation;
                parseHttpMethodAndPath(http.method(), http.path(), http.hasBody());
            } else if (annotation instanceof retrofit2.http.Headers) {
                String[] headersToParse = ((retrofit2.http.Headers) annotation).value();
                if (headersToParse.length == 0) {
                    throw methodError(method, "@Headers annotation is empty.");
                }
                headers = parseHeaders(headersToParse);
            } else if (annotation instanceof Multipart) {// 多个参数注解
                if (isFormEncoded) {
                    throw methodError(method, "Only one encoding annotation is allowed.");
                }
                isMultipart = true;
            } else if (annotation instanceof FormUrlEncoded) {
                if (isMultipart) {
                    throw methodError(method, "Only one encoding annotation is allowed.");
                }
                isFormEncoded = true;
            }
        }

        /**
         * 解析http请求
         *
         * @param httpMethod http请求方法名：get.post等
         * @param value      注解中的值(相对路径url)
         * @param hasBody    是否有body
         */
        private void parseHttpMethodAndPath(String httpMethod, String value, boolean hasBody) {
            if (this.httpMethod != null) {
                throw methodError(method, "Only one HTTP method is allowed. Found: %s and %s.",
                        this.httpMethod, httpMethod);
            }
            this.httpMethod = httpMethod;
            this.hasBody = hasBody;

            // 没有设置相对路径就解析完毕了
            if (value.isEmpty()) {
                return;
            }

            /*
             * 例如：
             * @POST("/negscr/home/cardConfig?")
             * Call<CardConfigInfo> getCardConfigList(@Field("model") String model, @Field("version") String version);
             */
            // Get the relative URL path and existing query string, if present.
            // 解析相对路径与查询字符串
            int question = value.indexOf('?');// 解析是否有问号（?），问号前面是相对路径
            if (question != -1 && question < value.length() - 1) {// 有
                // Ensure the query string does not have any named parameters.
                String queryParams = value.substring(question + 1);
                Matcher queryParamMatcher = PARAM_URL_REGEX.matcher(queryParams);
                if (queryParamMatcher.find()) {
                    throw methodError(method, "URL query string \"%s\" must not have replace block. "
                            + "For dynamic query parameters use @Query.", queryParams);
                }
            }

            this.relativeUrl = value;
            this.relativeUrlParamNames = parsePathParameters(value);
        }

        private Headers parseHeaders(String[] headers) {
            Headers.Builder builder = new Headers.Builder();
            for (String header : headers) {
                int colon = header.indexOf(':');
                if (colon == -1 || colon == 0 || colon == header.length() - 1) {
                    throw methodError(method,
                            "@Headers value must be in the form \"Name: Value\". Found: \"%s\"", header);
                }
                String headerName = header.substring(0, colon);
                String headerValue = header.substring(colon + 1).trim();
                if ("Content-Type".equalsIgnoreCase(headerName)) {
                    try {
                        contentType = MediaType.get(headerValue);
                    } catch (IllegalArgumentException e) {
                        throw methodError(method, e, "Malformed content type: %s", headerValue);
                    }
                } else {
                    builder.add(headerName, headerValue);
                }
            }
            return builder.build();
        }

        /**
         * 解析参数注解封装到ParameterHandler中
         *
         * @param p                 参数的index
         * @param parameterType     参数类型
         * @param annotations       参数对应的注解数组（每个参数可能对应多个注解）
         * @param allowContinuation 是否是最后一个参数
         *
         * @return
         */
        private @Nullable
        ParameterHandler<?> parseParameter(
                int p, Type parameterType, @Nullable Annotation[] annotations, boolean allowContinuation) {
            ParameterHandler<?> result = null;
            if (annotations != null) {
                // 遍历某一个参数上的注解，因为一个参数除了使用Retrofit的注解标注以外，也有可能使用其他注解标注，
                // 这里只处理retrofit自己的注解，如果一个参数上存在两个以上的retrofit注解，则会报错
                for (Annotation annotation : annotations) {
                    ParameterHandler<?> annotationAction =
                            parseParameterAnnotation(p, parameterType, annotations, annotation);

                    // 发现是非Retrofit注解，直接跳过
                    if (annotationAction == null) {
                        continue;
                    }

                    if (result != null) {// Retrofit同一个参数注解只允许有一个
                        throw parameterError(method, p,
                                "Multiple Retrofit annotations found, only one allowed.");
                    }

                    result = annotationAction;
                }
            }

            if (result == null) {// 参数没有注解
                if (allowContinuation) {// 最后一个参数
                    try {
                        if (Utils.getRawType(parameterType) == Continuation.class) {
                            isKotlinSuspendFunction = true;
                            return null;
                        }
                    } catch (NoClassDefFoundError ignored) {
                    }
                }
                throw parameterError(method, p, "No Retrofit annotation found.");
            }

            return result;
        }

        /**
         * 解析每一个参数注解
         *
         * @param p           参数位置
         * @param type        参数类型
         * @param annotations 参数注解
         * @param annotation  参数中的单个注解
         *
         * @return
         */
        @Nullable
        private ParameterHandler<?> parseParameterAnnotation(
                int p, Type type, Annotation[] annotations, Annotation annotation) {
            if (annotation instanceof Url) {
                validateResolvableType(p, type);
                if (gotUrl) {// 不能有重复的Url注解
                    throw parameterError(method, p, "Multiple @Url method annotations found.");
                }
                if (gotPath) {// 不能有重复的Path注解
                    throw parameterError(method, p, "@Path parameters may not be used with @Url.");
                }
                if (gotQuery) {// 不能有重复的Query注解
                    throw parameterError(method, p, "A @Url parameter must not come after a @Query.");
                }
                if (gotQueryName) {// 不能有重复的QueryName注解
                    throw parameterError(method, p, "A @Url parameter must not come after a @QueryName.");
                }
                if (gotQueryMap) {// 不能有重复的QueryMap注解
                    throw parameterError(method, p, "A @Url parameter must not come after a @QueryMap.");
                }
                if (relativeUrl != null) {
                    throw parameterError(method, p, "@Url cannot be used with @%s URL", httpMethod);
                }

                gotUrl = true;

                // 相对路径
                if (type == HttpUrl.class
                        || type == String.class
                        || type == URI.class
                        || (type instanceof Class && "android.net.Uri".equals(((Class<?>) type).getName()))) {
                    return new ParameterHandler.RelativeUrl(method, p);// 关键代码
                } else {
                    throw parameterError(method, p,
                            "@Url must be okhttp3.HttpUrl, String, java.net.URI, or android.net.Uri type.");
                }

            } else if (annotation instanceof Path) {// Path注解
                validateResolvableType(p, type);
                if (gotQuery) {
                    throw parameterError(method, p, "A @Path parameter must not come after a @Query.");
                }
                if (gotQueryName) {
                    throw parameterError(method, p, "A @Path parameter must not come after a @QueryName.");
                }
                if (gotQueryMap) {
                    throw parameterError(method, p, "A @Path parameter must not come after a @QueryMap.");
                }
                if (gotUrl) {
                    throw parameterError(method, p, "@Path parameters may not be used with @Url.");
                }
                if (relativeUrl == null) {
                    throw parameterError(method, p, "@Path can only be used with relative url on @%s",
                            httpMethod);
                }
                gotPath = true;

                Path path = (Path) annotation;
                String name = path.value();
                validatePathName(p, name);

                // 注意这里就开始使用converter了，在解析@Path注解的参数时，其类型是要被转换成String的
                Converter<?, String> converter = retrofit.stringConverter(type, annotations);
                return new ParameterHandler.Path<>(method, p, name, converter, path.encoded());

            } else if (annotation instanceof Query) {
                validateResolvableType(p, type);
                Query query = (Query) annotation;
                String name = query.value();
                boolean encoded = query.encoded();

                Class<?> rawParameterType = Utils.getRawType(type);
                gotQuery = true;
                // 针对参数类型为Iterable（例如集合），数组以及其他三种情况作了处理,这段代码基本上把
                // ParameterHandler的功能都涉及到了。
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    // 必须是泛型
                    if (!(type instanceof ParameterizedType)) {
                        throw parameterError(method, p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.Query<>(name, converter, encoded).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.Query<>(name, converter, encoded).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(type, annotations);
                    return new ParameterHandler.Query<>(name, converter, encoded);
                }

            } else if (annotation instanceof QueryName) {
                validateResolvableType(p, type);
                QueryName query = (QueryName) annotation;
                boolean encoded = query.encoded();

                Class<?> rawParameterType = Utils.getRawType(type);
                gotQueryName = true;
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(type instanceof ParameterizedType)) {
                        throw parameterError(method, p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.QueryName<>(converter, encoded).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.QueryName<>(converter, encoded).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(type, annotations);
                    return new ParameterHandler.QueryName<>(converter, encoded);
                }

            } else if (annotation instanceof QueryMap) {
                validateResolvableType(p, type);
                Class<?> rawParameterType = Utils.getRawType(type);
                gotQueryMap = true;
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(method, p, "@QueryMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(method, p,
                            "Map must include generic types (e.g., Map<String, String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;
                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(method, p, "@QueryMap keys must be of type String: " + keyType);
                }
                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                Converter<?, String> valueConverter =
                        retrofit.stringConverter(valueType, annotations);

                return new ParameterHandler.QueryMap<>(method, p,
                        valueConverter, ((QueryMap) annotation).encoded());

            } else if (annotation instanceof Header) {
                validateResolvableType(p, type);
                Header header = (Header) annotation;
                String name = header.value();

                Class<?> rawParameterType = Utils.getRawType(type);
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(type instanceof ParameterizedType)) {
                        throw parameterError(method, p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.Header<>(name, converter).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.Header<>(name, converter).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(type, annotations);
                    return new ParameterHandler.Header<>(name, converter);
                }

            } else if (annotation instanceof HeaderMap) {
                if (type == Headers.class) {
                    return new ParameterHandler.Headers(method, p);
                }

                validateResolvableType(p, type);
                Class<?> rawParameterType = Utils.getRawType(type);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(method, p, "@HeaderMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(method, p,
                            "Map must include generic types (e.g., Map<String, String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;
                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(method, p, "@HeaderMap keys must be of type String: " + keyType);
                }
                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                Converter<?, String> valueConverter =
                        retrofit.stringConverter(valueType, annotations);

                return new ParameterHandler.HeaderMap<>(method, p, valueConverter);

            } else if (annotation instanceof Field) {
                validateResolvableType(p, type);
                if (!isFormEncoded) {
                    throw parameterError(method, p, "@Field parameters can only be used with form encoding.");
                }
                Field field = (Field) annotation;
                String name = field.value();
                boolean encoded = field.encoded();

                gotField = true;

                Class<?> rawParameterType = Utils.getRawType(type);
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(type instanceof ParameterizedType)) {
                        throw parameterError(method, p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.Field<>(name, converter, encoded).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.Field<>(name, converter, encoded).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(type, annotations);
                    return new ParameterHandler.Field<>(name, converter, encoded);
                }

            } else if (annotation instanceof FieldMap) {
                validateResolvableType(p, type);
                if (!isFormEncoded) {
                    throw parameterError(method, p,
                            "@FieldMap parameters can only be used with form encoding.");
                }
                Class<?> rawParameterType = Utils.getRawType(type);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(method, p, "@FieldMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(method, p,
                            "Map must include generic types (e.g., Map<String, String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;
                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(method, p, "@FieldMap keys must be of type String: " + keyType);
                }
                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                Converter<?, String> valueConverter =
                        retrofit.stringConverter(valueType, annotations);

                gotField = true;
                return new ParameterHandler.FieldMap<>(method, p,
                        valueConverter, ((FieldMap) annotation).encoded());

            } else if (annotation instanceof Part) {
                validateResolvableType(p, type);
                if (!isMultipart) {
                    throw parameterError(method, p,
                            "@Part parameters can only be used with multipart encoding.");
                }
                Part part = (Part) annotation;
                gotPart = true;

                String partName = part.value();
                Class<?> rawParameterType = Utils.getRawType(type);
                if (partName.isEmpty()) {
                    if (Iterable.class.isAssignableFrom(rawParameterType)) {
                        if (!(type instanceof ParameterizedType)) {
                            throw parameterError(method, p, rawParameterType.getSimpleName()
                                    + " must include generic type (e.g., "
                                    + rawParameterType.getSimpleName()
                                    + "<String>)");
                        }
                        ParameterizedType parameterizedType = (ParameterizedType) type;
                        Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                        if (!MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(iterableType))) {
                            throw parameterError(method, p,
                                    "@Part annotation must supply a name or use MultipartBody.Part parameter type.");
                        }
                        return ParameterHandler.RawPart.INSTANCE.iterable();
                    } else if (rawParameterType.isArray()) {
                        Class<?> arrayComponentType = rawParameterType.getComponentType();
                        if (!MultipartBody.Part.class.isAssignableFrom(arrayComponentType)) {
                            throw parameterError(method, p,
                                    "@Part annotation must supply a name or use MultipartBody.Part parameter type.");
                        }
                        return ParameterHandler.RawPart.INSTANCE.array();
                    } else if (MultipartBody.Part.class.isAssignableFrom(rawParameterType)) {
                        return ParameterHandler.RawPart.INSTANCE;
                    } else {
                        throw parameterError(method, p,
                                "@Part annotation must supply a name or use MultipartBody.Part parameter type.");
                    }
                } else {
                    Headers headers =
                            Headers.of("Content-Disposition", "form-data; name=\"" + partName + "\"",
                                    "Content-Transfer-Encoding", part.encoding());

                    if (Iterable.class.isAssignableFrom(rawParameterType)) {
                        if (!(type instanceof ParameterizedType)) {
                            throw parameterError(method, p, rawParameterType.getSimpleName()
                                    + " must include generic type (e.g., "
                                    + rawParameterType.getSimpleName()
                                    + "<String>)");
                        }
                        ParameterizedType parameterizedType = (ParameterizedType) type;
                        Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                        if (MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(iterableType))) {
                            throw parameterError(method, p,
                                    "@Part parameters using the MultipartBody.Part must not "
                                            + "include a part name in the annotation.");
                        }
                        Converter<?, RequestBody> converter =
                                retrofit.requestBodyConverter(iterableType, annotations, methodAnnotations);
                        return new ParameterHandler.Part<>(method, p, headers, converter).iterable();
                    } else if (rawParameterType.isArray()) {
                        Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
                        if (MultipartBody.Part.class.isAssignableFrom(arrayComponentType)) {
                            throw parameterError(method, p,
                                    "@Part parameters using the MultipartBody.Part must not "
                                            + "include a part name in the annotation.");
                        }
                        Converter<?, RequestBody> converter =
                                retrofit.requestBodyConverter(arrayComponentType, annotations, methodAnnotations);
                        return new ParameterHandler.Part<>(method, p, headers, converter).array();
                    } else if (MultipartBody.Part.class.isAssignableFrom(rawParameterType)) {
                        throw parameterError(method, p,
                                "@Part parameters using the MultipartBody.Part must not "
                                        + "include a part name in the annotation.");
                    } else {
                        Converter<?, RequestBody> converter =
                                retrofit.requestBodyConverter(type, annotations, methodAnnotations);
                        return new ParameterHandler.Part<>(method, p, headers, converter);
                    }
                }

            } else if (annotation instanceof PartMap) {
                validateResolvableType(p, type);
                if (!isMultipart) {
                    throw parameterError(method, p,
                            "@PartMap parameters can only be used with multipart encoding.");
                }
                gotPart = true;
                Class<?> rawParameterType = Utils.getRawType(type);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(method, p, "@PartMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(method, p,
                            "Map must include generic types (e.g., Map<String, String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;

                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(method, p, "@PartMap keys must be of type String: " + keyType);
                }

                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                if (MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(valueType))) {
                    throw parameterError(method, p, "@PartMap values cannot be MultipartBody.Part. "
                            + "Use @Part List<Part> or a different value type instead.");
                }

                Converter<?, RequestBody> valueConverter =
                        retrofit.requestBodyConverter(valueType, annotations, methodAnnotations);

                PartMap partMap = (PartMap) annotation;
                return new ParameterHandler.PartMap<>(method, p, valueConverter, partMap.encoding());

            } else if (annotation instanceof Body) {
                validateResolvableType(p, type);
                if (isFormEncoded || isMultipart) {
                    throw parameterError(method, p,
                            "@Body parameters cannot be used with form or multi-part encoding.");
                }
                if (gotBody) {
                    throw parameterError(method, p, "Multiple @Body method annotations found.");
                }

                Converter<?, RequestBody> converter;
                try {
                    converter = retrofit.requestBodyConverter(type, annotations, methodAnnotations);
                } catch (RuntimeException e) {
                    // Wide exception range because factories are user code.
                    throw parameterError(method, e, p, "Unable to create @Body converter for %s", type);
                }
                gotBody = true;
                return new ParameterHandler.Body<>(method, p, converter);

            } else if (annotation instanceof Tag) {
                validateResolvableType(p, type);

                Class<?> tagType = Utils.getRawType(type);
                for (int i = p - 1; i >= 0; i--) {
                    ParameterHandler<?> otherHandler = parameterHandlers[i];
                    if (otherHandler instanceof ParameterHandler.Tag
                            && ((ParameterHandler.Tag) otherHandler).cls.equals(tagType)) {
                        throw parameterError(method, p, "@Tag type "
                                + tagType.getName()
                                + " is duplicate of parameter #"
                                + (i + 1)
                                + " and would always overwrite its value.");
                    }
                }

                return new ParameterHandler.Tag<>(tagType);
            }

            return null; // Not a Retrofit annotation.
        }

        private void validateResolvableType(int p, Type type) {
            if (Utils.hasUnresolvableType(type)) {
                throw parameterError(method, p,
                        "Parameter type must not include a type variable or wildcard: %s", type);
            }
        }

        private void validatePathName(int p, String name) {
            if (!PARAM_NAME_REGEX.matcher(name).matches()) {
                throw parameterError(method, p, "@Path parameter name must match %s. Found: %s",
                        PARAM_URL_REGEX.pattern(), name);
            }
            // Verify URL replacement name is actually present in the URL path.
            if (!relativeUrlParamNames.contains(name)) {
                throw parameterError(method, p, "URL \"%s\" does not contain \"{%s}\".", relativeUrl, name);
            }
        }

        /**
         * Gets the set of unique path parameters used in the given URI. If a parameter is used twice
         * in the URI, it will only show up once in the set.
         */
        static Set<String> parsePathParameters(String path) {
            Matcher m = PARAM_URL_REGEX.matcher(path);
            Set<String> patterns = new LinkedHashSet<>();
            while (m.find()) {
                patterns.add(m.group(1));
            }
            return patterns;
        }

        private static Class<?> boxIfPrimitive(Class<?> type) {
            if (boolean.class == type) return Boolean.class;
            if (byte.class == type) return Byte.class;
            if (char.class == type) return Character.class;
            if (double.class == type) return Double.class;
            if (float.class == type) return Float.class;
            if (int.class == type) return Integer.class;
            if (long.class == type) return Long.class;
            if (short.class == type) return Short.class;
            return type;
        }
    }
}
