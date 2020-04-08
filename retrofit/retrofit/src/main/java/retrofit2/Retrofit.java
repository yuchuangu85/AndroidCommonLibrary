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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.Url;

import static java.util.Collections.unmodifiableList;

/**
 * 1,注解真正的实现在ParameterHandler类中,,每个注解的真正实现都是ParameterHandler类中的一个final类型的内部类,
 * 每个内部类都对各个注解的使用要求做了限制,比如参数是否可空,键和值是否可空等.
 * <p>
 * 2,FormUrlEncoded注解和Multipart注解不能同时使用,否则会抛出methodError(“Only one encoding annotation
 * is allowed.”);可在ServiceMethod类中parseMethodAnnotation()方法中找到不能同时使用的具体原因.
 * <p>
 * 3,Path注解与Url注解不能同时使用,否则会抛出parameterError(p, “@Path parameters may not be used with @Url.”),
 * 可在ServiceMethod类中parseParameterAnnotation()方法中找到不能同时使用的具体代码.其实原因也很好理解:
 * Path注解用于替换url路径中的参数,这就要求在使用path注解时,必须已经存在请求路径,不然没法替换路径中指定的参数啊,
 * 而Url注解是在参数中指定的请求路径的,这个时候指定请求路径已经晚了,path注解找不到请求路径,更别提更换请求路径中的参数了.
 * <p>
 * 4,对于FiledMap,HeaderMap,PartMap,QueryMap这四种作用于方法的注解,其参数类型必须为Map的实例,且key的类型必须
 * 为String类型,否则抛出异常(以PartMap注解为例):parameterError(p, “@PartMap keys must be of type String: ” + keyType);
 * <p>
 * 5,使用Body注解的参数不能使用form 或multi-part编码,即如果为方法使用了FormUrlEncoded或Multipart注解,则方法的
 * 参数中不能使用Body注解,否则抛出异常parameterError(p, “@Body parameters cannot be used with form or multi-part encoding.”);
 * <p>
 * Retrofit adapts a Java interface to HTTP calls by using annotations on the declared methods to
 * define how requests are made. Create instances using {@linkplain Builder
 * the builder} and pass your interface to {@link #create} to generate an implementation.
 * <p>
 * For example,
 * <pre><code>
 *
 * 第一步：
 * Retrofit retrofit = new Retrofit.Builder()
 *     .baseUrl("https://api.example.com/")
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .build();
 *
 * 第二步：
 * MyApi api = retrofit.create(MyApi.class);
 *
 * 第三步：
 * Response&lt;User&gt; user = api.getUser().execute();
 * 原始代码：
 * "Response<User> user = api.getUser().execute();"
 * </code></pre>
 *
 * @author Bob Lee (bob@squareup.com)
 * @author Jake Wharton (jw@squareup.com)
 * <p>
 * 参考：
 * https://blog.csdn.net/shusheng0007/article/details/81335264
 */
public final class Retrofit {

    // 缓存解析出来的方法
    private final Map<Method, ServiceMethod<?>> serviceMethodCache = new ConcurrentHashMap<>();

    // 负责创建 HTTP 请求，HTTP 请求被抽象为了 okhttp3.Call 类，它表示一个已经准备好，
    // 可以随时执行的 HTTP 请求；默认为OkHttpClient
    final okhttp3.Call.Factory callFactory;
    final HttpUrl baseUrl;// 基础链接
    /**
     * 数据转换器：在retrofit-converters库中
     * GsonConverterFactory：用来创建GsonRequestBodyConverter和GsonResponseBodyConverter
     * GuavaOptionalConverterFactory：用来创建OptionalConverter
     * JacksonConverterFactory：用来创建JacksonRequestBodyConverter和JacksonResponseBodyConverter
     * Java8OptionalConverterFactory：用来创建OptionalConverter（弃用）
     * JaxbConverterFactory：用来创建JaxbRequestConverter和JaxbResponseConverter
     * MoshiConverterFactory：用来创建MoshiRequestBodyConverter和MoshiResponseBodyConverter
     * ProtoConverterFactory：用来创建ProtoRequestBodyConverter和ProtoResponseBodyConverter
     * ScalarsConverterFactory：用来创建ScalarRequestBodyConverter和ScalarResponseBodyConverters
     * SimpleXmlConverterFactory：用来创建SimpleXmlRequestBodyConverter和SimpleXmlResponseBodyConverter（弃用）
     * WireConverterFactory：用来创建WireRequestBodyConverter和WireResponseBodyConverter
     * 内置：
     * BuiltInConverters
     * 平台默认：
     * Android平台：sdk>=24 添加OptionalConverterFactory，返回OptionalConverter
     * Android平台：sdk<24 不添加
     * java平台：OptionalConverterFactory返回OptionalConverter
     */
    final List<Converter.Factory> converterFactories;
    /**
     * 添加默认CallAdapter.Factory
     * 1.Android平台 sdk>=24 添加 CompletableFutureCallAdapterFactory 和 DefaultCallAdapterFactory
     * 2.Android平台 sdk<24 只添加DefaultCallAdapterFactory
     * 3.java平台添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
     * <p>
     * DefaultCallAdapterFactory：返回CallAdapter
     * CompletableFutureCallAdapterFactory：返回ResponseCallAdapter
     * sdk<24返回第一个，否则返回两个第二个在前，第一个在后
     * <p>
     * 手动配置：
     * GuavaCallAdapterFactory：get返回-ResponseCallAdapter
     * Java8CallAdapterFactory：get返回-ResponseCallAdapter
     * RxJavaCallAdapterFactory：get返回-RxJavaCallAdapter
     * RxJava2CallAdapterFactory：get返回-RxJava2CallAdapter
     * ScalaCallAdapterFactory：get返回-ResponseCallAdapter
     */
    final List<CallAdapter.Factory> callAdapterFactories;

    // 用于执行回调 Android中默认是 MainThreadExecutor
    final @Nullable
    Executor callbackExecutor;
    // 是否需要立即解析接口中的方法
    final boolean validateEagerly;

    Retrofit(okhttp3.Call.Factory callFactory, HttpUrl baseUrl,
             List<Converter.Factory> converterFactories, List<CallAdapter.Factory> callAdapterFactories,
             @Nullable Executor callbackExecutor, boolean validateEagerly) {
        this.callFactory = callFactory;
        this.baseUrl = baseUrl;
        this.converterFactories = converterFactories; // Copy+unmodifiable at call site.
        this.callAdapterFactories = callAdapterFactories; // Copy+unmodifiable at call site.
        this.callbackExecutor = callbackExecutor;
        this.validateEagerly = validateEagerly;
    }

    /**
     * Create an implementation of the API endpoints defined by the {@code service} interface.
     * <p>
     * The relative path for a given method is obtained from an annotation on the method describing
     * the request type. The built-in methods are {@link retrofit2.http.GET GET},
     * {@link retrofit2.http.PUT PUT}, {@link retrofit2.http.POST POST}, {@link retrofit2.http.PATCH
     * PATCH}, {@link retrofit2.http.HEAD HEAD}, {@link retrofit2.http.DELETE DELETE} and
     * {@link retrofit2.http.OPTIONS OPTIONS}. You can use a custom HTTP method with
     * {@link HTTP @HTTP}. For a dynamic(动态) URL, omit(省略) the path on the annotation and annotate the first
     * parameter with {@link Url @Url}.
     * <p>
     * Method parameters can be used to replace parts of the URL by annotating them with
     * {@link retrofit2.http.Path @Path}. Replacement sections are denoted(表示) by an identifier
     * surrounded by curly braces (e.g., "{foo}"). To add items to the query string of a URL use
     * {@link retrofit2.http.Query @Query}.
     * <p>
     * The body of a request is denoted by the {@link retrofit2.http.Body @Body} annotation. The
     * object will be converted to request representation by one of the {@link Converter.Factory}
     * instances. A {@link RequestBody} can also be used for a raw representation.
     * <p>
     * Alternative request body formats are supported by method annotations and corresponding
     * parameter annotations:
     * <ul>
     * <li>{@link retrofit2.http.FormUrlEncoded @FormUrlEncoded} - Form-encoded data with key-value
     * pairs specified by the {@link retrofit2.http.Field @Field} parameter annotation.
     * <li>{@link retrofit2.http.Multipart @Multipart} - RFC 2388-compliant multipart data with
     * parts specified by the {@link retrofit2.http.Part @Part} parameter annotation.
     * </ul>
     * <p>
     * Additional static headers can be added for an endpoint using the
     * {@link retrofit2.http.Headers @Headers} method annotation. For per-request control over a
     * header annotate a parameter with {@link Header @Header}.
     * <p>
     * By default, methods return a {@link Call} which represents the HTTP request. The generic
     * parameter of the call is the response body type and will be converted by one of the
     * {@link Converter.Factory} instances. {@link ResponseBody} can also be used for a raw
     * representation. {@link Void} can be used if you do not care about the body contents.
     * <p>
     * <p>
     * For example:
     * <pre>
     * public interface CategoryService {
     *   &#64;POST("category/{cat}/")
     *   Call&lt;List&lt;Item&gt;&gt; categoryList(@Path("cat") String a, @Query("page") int b);
     * }
     * </pre>
     * 代理模式：https://a.codekk.com/detail/Android/Caij/%E5%85%AC%E5%85%B1%E6%8A%80%E6%9C%AF%E7%82%B9%E4%B9%8B%20Java%20%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86
     * <p>
     * 这里泛型T就是代理类service的Class类型（上面实例中的CategoryService）
     */
    @SuppressWarnings("unchecked") // Single-interface proxy creation guarded by parameter safety.
    public <T> T create(final Class<T> service) {
        // 验证接口的合法性以及是否要将所有请求方法缓存起来
        validateServiceInterface(service);
        /**
         * 代理模式（参考上面链接或下面内容）
         * <p>
         * 所谓动态代理：就是你定义了一个接口，里面声明了很多方法，那你正常情况下是不是应该有一个这个接口的实现类，
         * 然后才能使用里面的方法。所谓动态代理就代理的这个实体类，我们这里的create()返回值就是此动态代理实例，
         * 只要使用这个实例调用方法，那么都会进入invoke()方法里面。
         * <p>
         * 通过Proxy.newProxyInstance(…)函数新建了一个代理对象，实际代理类就是在这时候动态生成的。
         * 第一个参数：表示类加载器
         * 第二个参数：表示委托类的接口，生成代理类时需要实现这些接口
         * 第三个参数：实现类对象，负责连接代理类和委托类的中间类
         */
        return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service},
                new InvocationHandler() {
                    private final Platform platform = Platform.get();
                    private final Object[] emptyArgs = new Object[0];

                    /**
                     * 调用代理对象的每个函数(CategoryService.categoryList())实际最终都是
                     * 调用了InvocationHandler的invoke函数。在这个方法中可以执行一些操作
                     * (这里是解析方法的注解参数等)，通过这个方法真正的执行我们编写的接口中的网络请求。
                     *
                     * 我们使用Proxy类的newProxyInstance()方法生成的代理对象proxy去调用了
                     * ((CategoryService)proxy).categoryList();操作，那么系统就会将此方法分发给invoke().
                     * 其中proxy对象的类是系统帮我们动态生产的，其实现了我们的业务接口CategoryService。
                     *
                     * 实例
                     × <pre>
                     * public interface CategoryService {
                     *   @POST("category/{cat}/")
                     *   Call<List<Item>> categoryList(@Path("cat") String a, @Query("page") int b);
                     * }
                     * </pre>
                     * @param proxy  表示通过 Proxy.newProxyInstance() 生成的代理类对象。--CategoryService
                     * @param method 表示代理对象被调用的函数。--categoryList方法(有注解)
                     * @param args   表示代理对象被调用的函数的参数。--a,b(有注解)
                     *
                     * @return 提交一个http请求，返回我们设定的类型的结果，例如Call<User>
                     * @throws Throwable
                     */
                    @Override
                    public @Nullable
                    Object invoke(Object proxy, Method method,
                                  @Nullable Object[] args) throws Throwable {
                        // If the method is a method from Object then defer to normal invocation.
                        // 得到目标方法method所在类对应的Class对象
                        if (method.getDeclaringClass() == Object.class) {
                            // invoke（调用）就是调用Method类代表的方法
                            return method.invoke(this, args);
                        }
                        // 为了兼容 Java8 平台，Android 中不会执行
                        if (platform.isDefaultMethod(method)) {
                            return platform.invokeDefaultMethod(method, service, proxy, args);
                        }
                        /**
                         * loadServiceMethod返回的是CallAdapted（HttpServiceMethod）
                         * 这里调用invoke方法实际上调用的是CallAdapted的invoke方法也就是父类
                         * （HttpServiceMethod）的invoke方法，然后返回CallAdapter的工厂：
                         * Android平台 sdk>=24 添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
                         * Android平台 sdk<24 只添加DefaultCallAdapterFactory
                         * <p>
                         * 提交一个http请求，返回我们设定的类型的结果，例如Call<User>
                         * <p>
                         * 这里loadServiceMethod方法返回的是HttpServiceMethod
                         */
                        return loadServiceMethod(method).invoke(args != null ? args : emptyArgs);
                    }
                });
    }

    private void validateServiceInterface(Class<?> service) {
        if (!service.isInterface()) {// 验证是否是接口类
            throw new IllegalArgumentException("API declarations must be interfaces.");
        }

        Deque<Class<?>> check = new ArrayDeque<>(1);
        check.add(service);
        while (!check.isEmpty()) {
            Class<?> candidate = check.removeFirst();
            if (candidate.getTypeParameters().length != 0) {
                StringBuilder message = new StringBuilder("Type parameters are unsupported on ")
                        .append(candidate.getName());
                if (candidate != service) {
                    message.append(" which is an interface of ")
                            .append(service.getName());
                }
                throw new IllegalArgumentException(message.toString());
            }
            Collections.addAll(check, candidate.getInterfaces());
        }

        // 提前解析方法
        if (validateEagerly) {
            Platform platform = Platform.get();
            for (Method method : service.getDeclaredMethods()) {
                if (!platform.isDefaultMethod(method) && !Modifier.isStatic(method.getModifiers())) {
                    loadServiceMethod(method);
                }
            }
        }
    }

    // 返回的是CallAdapted（HttpServiceMethod）
    ServiceMethod<?> loadServiceMethod(Method method) {
        // 缓存逻辑，同一个 API 的同一个方法，只会创建一次。这里由于我们每次获取 API 实例都是传入的 class 对象，
        // 而 class 对象是进程内单例的，所以获取到它的同一个方法 Method 实例也是单例的，所以这里的缓存是有效的。
        ServiceMethod<?> result = serviceMethodCache.get(method);
        if (result != null) return result;

        synchronized (serviceMethodCache) {
            result = serviceMethodCache.get(method);
            if (result == null) {
                result = ServiceMethod.parseAnnotations(this, method);
                serviceMethodCache.put(method, result);
            }
        }
        // HttpServiceMethod.CallAdapted
        return result;
    }

    /**
     * The factory used to create {@linkplain okhttp3.Call OkHttp calls} for sending a HTTP requests.
     * Typically an instance of {@link OkHttpClient}.
     */
    public okhttp3.Call.Factory callFactory() {
        return callFactory;
    }

    /**
     * The API base URL.
     */
    public HttpUrl baseUrl() {
        return baseUrl;
    }

    /**
     * Returns a list of the factories tried when creating a
     * {@linkplain #callAdapter(Type, Annotation[])} call adapter}.
     */
    public List<CallAdapter.Factory> callAdapterFactories() {
        return callAdapterFactories;
    }

    /**
     * Returns the {@link CallAdapter} for {@code returnType} from the available {@linkplain
     * #callAdapterFactories() factories}.
     *
     * @throws IllegalArgumentException if no call adapter available for {@code type}.
     */
    public CallAdapter<?, ?> callAdapter(Type returnType, Annotation[] annotations) {
        return nextCallAdapter(null, returnType, annotations);
    }

    /**
     * Returns the {@link CallAdapter} for {@code returnType} from the available {@linkplain
     * #callAdapterFactories() factories} except {@code skipPast}.
     *
     * @param returnType 返回数据类型：Call<User>
     *
     * @throws IllegalArgumentException if no call adapter available for {@code type}.
     */
    public CallAdapter<?, ?> nextCallAdapter(@Nullable CallAdapter.Factory skipPast, Type returnType,
                                             Annotation[] annotations) {
        Objects.requireNonNull(returnType, "returnType == null");
        Objects.requireNonNull(annotations, "annotations == null");

        // 是否要跳过列表中某个callAdapter
        int start = callAdapterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
            /*
             * CompletableFutureCallAdapterFactory：返回ResponseCallAdapter
             * DefaultCallAdapterFactory：返回CallAdapter
             * 当为CompletableFutureCallAdapterFactory时，通过校验返回类型最终返回的CallAdapter为null
             * 这里最终返回的是DefaultCallAdapterFactory.CallAdapter
             */
            CallAdapter<?, ?> adapter = callAdapterFactories.get(i).get(returnType, annotations, this);
            if (adapter != null) {
                return adapter;
            }
        }

        StringBuilder builder = new StringBuilder("Could not locate call adapter for ")
                .append(returnType)
                .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    /**
     * Returns an unmodifiable list of the factories tried when creating a
     * {@linkplain #requestBodyConverter(Type, Annotation[], Annotation[]) request body converter}, a
     * {@linkplain #responseBodyConverter(Type, Annotation[]) response body converter}, or a
     * {@linkplain #stringConverter(Type, Annotation[]) string converter}.
     */
    public List<Converter.Factory> converterFactories() {
        return converterFactories;
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link RequestBody} from the available
     * {@linkplain #converterFactories() factories}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<T, RequestBody> requestBodyConverter(Type type,
                                                              Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        return nextRequestBodyConverter(null, type, parameterAnnotations, methodAnnotations);
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link RequestBody} from the available
     * {@linkplain #converterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<T, RequestBody> nextRequestBodyConverter(
            @Nullable Converter.Factory skipPast, Type type, Annotation[] parameterAnnotations,
            Annotation[] methodAnnotations) {
        Objects.requireNonNull(type, "type == null");
        Objects.requireNonNull(parameterAnnotations, "parameterAnnotations == null");
        Objects.requireNonNull(methodAnnotations, "methodAnnotations == null");

        int start = converterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            Converter.Factory factory = converterFactories.get(i);
            Converter<?, RequestBody> converter =
                    factory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<T, RequestBody>) converter;
            }
        }

        StringBuilder builder = new StringBuilder("Could not locate RequestBody converter for ")
                .append(type)
                .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    /**
     * Returns a {@link Converter} for {@link ResponseBody} to {@code type} from the available
     * {@linkplain #converterFactories() factories}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
        return nextResponseBodyConverter(null, type, annotations);
    }

    /**
     * Returns a {@link Converter} for {@link ResponseBody} to {@code type} from the available
     * {@linkplain #converterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<ResponseBody, T> nextResponseBodyConverter(
            @Nullable Converter.Factory skipPast, Type type, Annotation[] annotations) {
        Objects.requireNonNull(type, "type == null");
        Objects.requireNonNull(annotations, "annotations == null");

        int start = converterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            Converter<ResponseBody, ?> converter =
                    converterFactories.get(i).responseBodyConverter(type, annotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<ResponseBody, T>) converter;
            }
        }

        StringBuilder builder = new StringBuilder("Could not locate ResponseBody converter for ")
                .append(type)
                .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link String} from the available
     * {@linkplain #converterFactories() factories}.
     * <p>
     * 返回一个将变量转换为String的转换器
     */
    public <T> Converter<T, String> stringConverter(Type type, Annotation[] annotations) {
        Objects.requireNonNull(type, "type == null");
        Objects.requireNonNull(annotations, "annotations == null");

        for (int i = 0, count = converterFactories.size(); i < count; i++) {
            Converter<?, String> converter =
                    converterFactories.get(i).stringConverter(type, annotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<T, String>) converter;
            }
        }

        // Nothing matched. Resort to default converter which just calls toString().
        //noinspection unchecked
        return (Converter<T, String>) BuiltInConverters.ToStringConverter.INSTANCE;
    }

    /**
     * The executor used for {@link Callback} methods on a {@link Call}. This may be {@code null},
     * in which case callbacks should be made synchronously on the background thread.
     */
    public @Nullable
    Executor callbackExecutor() {
        return callbackExecutor;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    /**
     * Build a new {@link Retrofit}.
     * <p>
     * Calling {@link #baseUrl} is required before calling {@link #build()}. All other methods
     * are optional.
     */
    public static final class Builder {
        private final Platform platform;
        private @Nullable
        okhttp3.Call.Factory callFactory;
        private @Nullable
        HttpUrl baseUrl;
        private final List<Converter.Factory> converterFactories = new ArrayList<>();
        private final List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>();
        // 线程池：Android平台为MainThreadExecutor，Java平台为null
        private @Nullable
        Executor callbackExecutor;
        private boolean validateEagerly;

        Builder(Platform platform) {
            this.platform = platform;
        }

        public Builder() {
            this(Platform.get());
        }

        Builder(Retrofit retrofit) {
            platform = Platform.get();
            callFactory = retrofit.callFactory;
            baseUrl = retrofit.baseUrl;

            // Do not add the default BuiltIntConverters and platform-aware converters added by build().
            for (int i = 1,
                 size = retrofit.converterFactories.size() - platform.defaultConverterFactoriesSize();
                 i < size; i++) {
                converterFactories.add(retrofit.converterFactories.get(i));
            }

            // Do not add the default, platform-aware call adapters added by build().
            for (int i = 0,
                 size = retrofit.callAdapterFactories.size() - platform.defaultCallAdapterFactoriesSize();
                 i < size; i++) {
                callAdapterFactories.add(retrofit.callAdapterFactories.get(i));
            }

            callbackExecutor = retrofit.callbackExecutor;
            validateEagerly = retrofit.validateEagerly;
        }

        /**
         * The HTTP client used for requests.
         * <p>
         * This is a convenience method for calling {@link #callFactory}.
         */
        public Builder client(OkHttpClient client) {
            return callFactory(Objects.requireNonNull(client, "client == null"));
        }

        /**
         * Specify a custom call factory for creating {@link Call} instances.
         * <p>
         * Note: Calling {@link #client} automatically sets this value.
         */
        public Builder callFactory(okhttp3.Call.Factory factory) {
            this.callFactory = Objects.requireNonNull(factory, "factory == null");
            return this;
        }

        /**
         * Set the API base URL.
         *
         * @see #baseUrl(HttpUrl)
         */
        public Builder baseUrl(URL baseUrl) {
            Objects.requireNonNull(baseUrl, "baseUrl == null");
            return baseUrl(HttpUrl.get(baseUrl.toString()));
        }

        /**
         * Set the API base URL.
         *
         * @see #baseUrl(HttpUrl)
         */
        public Builder baseUrl(String baseUrl) {
            Objects.requireNonNull(baseUrl, "baseUrl == null");
            return baseUrl(HttpUrl.get(baseUrl));
        }

        /**
         * Set the API base URL.
         * <p>
         * The specified endpoint values (such as with {@link GET @GET}) are resolved against this
         * value using {@link HttpUrl#resolve(String)}. The behavior of this matches that of an
         * {@code <a href="">} link on a website resolving on the current URL.
         * <p>
         * <b>Base URLs should always end in {@code /}.</b>
         * <p>
         * A trailing {@code /} ensures that endpoints values which are relative paths will correctly
         * append themselves to a base which has path components.
         * <p>
         * <b>Correct:</b><br>
         * Base URL: http://example.com/api/<br>
         * Endpoint: foo/bar/<br>
         * Result: http://example.com/api/foo/bar/
         * <p>
         * <b>Incorrect:</b><br>
         * Base URL: http://example.com/api<br>
         * Endpoint: foo/bar/<br>
         * Result: http://example.com/foo/bar/
         * <p>
         * This method enforces that {@code baseUrl} has a trailing {@code /}.
         * <p>
         * <b>Endpoint values which contain a leading {@code /} are absolute.</b>
         * <p>
         * Absolute values retain only the host from {@code baseUrl} and ignore any specified path
         * components.
         * <p>
         * Base URL: http://example.com/api/<br>
         * Endpoint: /foo/bar/<br>
         * Result: http://example.com/foo/bar/
         * <p>
         * Base URL: http://example.com/<br>
         * Endpoint: /foo/bar/<br>
         * Result: http://example.com/foo/bar/
         * <p>
         * <b>Endpoint values may be a full URL.</b>
         * <p>
         * Values which have a host replace the host of {@code baseUrl} and values also with a scheme
         * replace the scheme of {@code baseUrl}.
         * <p>
         * Base URL: http://example.com/<br>
         * Endpoint: https://github.com/square/retrofit/<br>
         * Result: https://github.com/square/retrofit/
         * <p>
         * Base URL: http://example.com<br>
         * Endpoint: //github.com/square/retrofit/<br>
         * Result: http://github.com/square/retrofit/ (note the scheme stays 'http')
         */
        public Builder baseUrl(HttpUrl baseUrl) {
            Objects.requireNonNull(baseUrl, "baseUrl == null");
            List<String> pathSegments = baseUrl.pathSegments();
            if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
                throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
            }
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Add converter factory for serialization and deserialization of objects.
         * <p>
         * 添加返回结果转换器
         */
        public Builder addConverterFactory(Converter.Factory factory) {
            converterFactories.add(Objects.requireNonNull(factory, "factory == null"));
            return this;
        }

        /**
         * Add a call adapter factory for supporting service method return types other than {@link
         * Call}.
         * <p>
         * 添加数据请求适配器
         */
        public Builder addCallAdapterFactory(CallAdapter.Factory factory) {
            callAdapterFactories.add(Objects.requireNonNull(factory, "factory == null"));
            return this;
        }

        /**
         * The executor on which {@link Callback} methods are invoked when returning {@link Call} from
         * your service method.
         * <p>
         * Note: {@code executor} is not used for {@linkplain #addCallAdapterFactory custom method
         * return types}.
         */
        public Builder callbackExecutor(Executor executor) {
            this.callbackExecutor = Objects.requireNonNull(executor, "executor == null");
            return this;
        }

        /**
         * Returns a modifiable list of call adapter factories.
         */
        public List<CallAdapter.Factory> callAdapterFactories() {
            return this.callAdapterFactories;
        }

        /**
         * Returns a modifiable list of converter factories.
         */
        public List<Converter.Factory> converterFactories() {
            return this.converterFactories;
        }

        /**
         * When calling {@link #create} on the resulting {@link Retrofit} instance, eagerly validate
         * the configuration of all methods in the supplied interface.
         */
        public Builder validateEagerly(boolean validateEagerly) {
            this.validateEagerly = validateEagerly;
            return this;
        }

        /**
         * Create the {@link Retrofit} instance using the configured values.
         * <p>
         * Note: If neither {@link #client} nor {@link #callFactory} is called a default {@link
         * OkHttpClient} will be created and used.
         */
        public Retrofit build() {
            if (baseUrl == null) {
                throw new IllegalStateException("Base URL required.");
            }

            // 如果没有手动创建，则new一个OkHttpClient(我们通常为默认的)
            okhttp3.Call.Factory callFactory = this.callFactory;
            if (callFactory == null) {
                callFactory = new OkHttpClient();
            }

            // 可以设置Executor，一般我们都不设置，直接使用系统默认的
            // Android 中返回的是 MainThreadExecutor(就是一个使用Handler切换到主线程的executor)
            Executor callbackExecutor = this.callbackExecutor;
            if (callbackExecutor == null) {// 没有手动设置线程池
                // 获取默认线程池：Android平台为MainThreadExecutor，Java平台为null
                callbackExecutor = platform.defaultCallbackExecutor();
            }

            // Make a defensive copy of the adapters and add the default Call adapter.
            // 添加手动设置的请求适配器工厂（CallAdapter.Factory），先添加用户的，然后添加默认的
            List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>(this.callAdapterFactories);
            /**
             * 添加默认CallAdapter.Factory(网络请求适配器工厂)
             * 1.Android平台 sdk>=24 添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
             * 2.Android平台 sdk<24 只添加DefaultCallAdapterFactory
             * 3.java平台添加CompletableFutureCallAdapterFactory和DefaultCallAdapterFactory
             */
            callAdapterFactories.addAll(platform.defaultCallAdapterFactories(callbackExecutor));

            // Make a defensive copy of the converters.
            List<Converter.Factory> converterFactories = new ArrayList<>(
                    1 + this.converterFactories.size() + platform.defaultConverterFactoriesSize());

            // Add the built-in converter factory first. This prevents overriding its behavior but also
            // ensures correct behavior when using converters that consume all types.
            // 先添加默认的配置，再添加用户设置的
            converterFactories.add(new BuiltInConverters());// 内置Converter.Factory
            converterFactories.addAll(this.converterFactories);// 手动添加的Converter.Factory
            converterFactories.addAll(platform.defaultConverterFactories());// 平台默认Converter.Factory

            return new Retrofit(callFactory, baseUrl, unmodifiableList(converterFactories),
                    unmodifiableList(callAdapterFactories), callbackExecutor, validateEagerly);
        }
    }
}
