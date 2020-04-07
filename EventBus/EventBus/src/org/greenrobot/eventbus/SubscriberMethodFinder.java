/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
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
package org.greenrobot.eventbus;

import org.greenrobot.eventbus.meta.SubscriberInfo;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SubscriberMethodFinder {
    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    // 订阅者方法缓存
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    private List<SubscriberInfoIndex> subscriberInfoIndexes;
    private final boolean strictMethodVerification;
    private final boolean ignoreGeneratedIndex;

    private static final int POOL_SIZE = 4;
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification,
                           boolean ignoreGeneratedIndex) {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    // 根据订阅Class查找该类中的所有方法
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        // 先从缓存中获取该订阅这对应的订阅方法集合，缓存中有直接取出返回
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        if (ignoreGeneratedIndex) {// 默认false
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            // 获取订阅者方法集合
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {// 放入缓存
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

    /**
     * 获取订阅类中有用的信息
     *
     * @param subscriberClass 订阅者class对象
     *
     * @return 订阅方法集合
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // 准备查找状态对象
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {// 第一次findState.clazz就是subscriberClass
            findState.subscriberInfo = getSubscriberInfo(findState);// 第一次为null
            if (findState.subscriberInfo != null) {
                // 订阅者方法集合
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                // 通过反射查找需要的订阅信息并进行缓存
                findUsingReflectionInSingleClass(findState);
            }
            // 查找超类
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        // 只保留findState.subscriberMethods到新的列表。其他全部释放
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        findState.recycle();
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        return subscriberMethods;
    }

    // 准备查找状态对象
    private FindState prepareFindState() {
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }
        return new FindState();
    }

    private SubscriberInfo getSubscriberInfo(FindState findState) {
        // 第一次findState.subscriberInfo是null
        if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
            SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
            if (findState.clazz == superclassInfo.getSubscriberClass()) {
                return superclassInfo;
            }
        }
        if (subscriberInfoIndexes != null) {// 默认情况下为null
            for (SubscriberInfoIndex index : subscriberInfoIndexes) {
                SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            findUsingReflectionInSingleClass(findState);
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /**
     * getModifiers方法返回该方法的修饰符：
     * 对应表如下：
     * PUBLIC: 1
     * PRIVATE: 2
     * PROTECTED: 4
     * STATIC: 8
     * FINAL: 16
     * SYNCHRONIZED: 32
     * VOLATILE: 64
     * TRANSIENT: 128
     * NATIVE: 256
     * INTERFACE: 512
     * ABSTRACT: 1024
     * STRICT: 2048
     *
     * @param findState
     */
    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            // This is faster than getMethods, especially when subscribers are fat classes like Activities
            // 查找所有方法（getDeclaredMethods方法是获取本类中的所有方法，包括私有的(private、protected、默认以及public)的方法。）
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
            // getMethods方法是获取本类以及父类或者父接口中所有的公共方法(public修饰符修饰的)
            methods = findState.clazz.getMethods();
            findState.skipSuperClasses = true;// 如果获取所有失败的话设置未true
        }
        for (Method method : methods) {
            int modifiers = method.getModifiers();// 获取方法的修饰符（参考上面的注释）
            // 该方法修饰符是public但不包含abstract、static、bridge、synthetic修饰符
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                Class<?>[] parameterTypes = method.getParameterTypes();// 获取方法中的所有形参类型集合
                // 只有一个形参（这里显示订阅者接收方法只能有一种类型的参数，一般是一个参数（接收到的消息对象））
                if (parameterTypes.length == 1) {
                    // 获取@Subscribe注解
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null) {
                        // 获取形参类型
                        Class<?> eventType = parameterTypes[0];
                        if (findState.checkAdd(method, eventType)) {// 检测是否可以添加，如果没添加过则返回true，允许添加
                            // 获取注解中的线程类型
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            // 把订阅者接收方法，参数类型，线程模型，优先级，是否是粘性事件等参数封装成
                            // 一个SubscriberMethod对象放入FindState中的列表中
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                    // isAnnotationPresent方法返回true，如果指定类型的注释存在于此元素上,否则返回false。
                    // strictMethodVerification默认false
                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
        }
    }

    static void clearCaches() {
        METHOD_CACHE.clear();
    }

    // 查找状态类
    static class FindState {
        // 订阅者方法集合
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        // 订阅者方法中的形参类型和对应方法的Map集合
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        Class<?> subscriberClass;// 观察者类
        Class<?> clazz;
        boolean skipSuperClasses;// 是否跳过超类
        SubscriberInfo subscriberInfo;// 订阅者信息

        void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        void recycle() {
            subscriberMethods.clear();
            anyMethodByEventType.clear();
            subscriberClassByMethodKey.clear();
            methodKeyBuilder.setLength(0);
            subscriberClass = null;
            clazz = null;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        /**
         * 检测是否符合条件
         *
         * @param method    方法对象
         * @param eventType 方法中的第一个形参类型（只有一个形参类型）
         *
         * @return
         */
        boolean checkAdd(Method method, Class<?> eventType) {
            // 2 level check: 1st level with event type only (fast), 2nd level with complete signature when required.
            // Usually a subscriber doesn't have methods listening to the same event type.
            Object existing = anyMethodByEventType.put(eventType, method);// 先检测是否存在这个key
            if (existing == null) {// 不存在相同的形参对应的对象
                return true;
            } else {// 存在相同的形参对应的对象
                if (existing instanceof Method) {// 如果存在这个key并且对应的值是方法对象，再次进行检测
                    // 已经存在了一样的抛出异常
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        // Paranoia check
                        throw new IllegalStateException();
                    }
                    // 把参数类型和包含对应信息的FindState对象放入缓存anyMethodByEventType中
                    // Put any non-Method object to "consume" the existing Method
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            // 该序列被改变为其长度由参数指定一个新的字符序列。
            methodKeyBuilder.setLength(0);
            // 将方法名和方法类型名拼劲
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());

            // 将上面拼接的字符串作为key，方法对象标示的方法的Class对象缓存到subscriberClassByMethodKey中
            String methodKey = methodKeyBuilder.toString();
            Class<?> methodClass = method.getDeclaringClass();// 返回表示声明由此Method对象表示的方法的类的Class对象。
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);// 是否存在
            // isAssignableFrom方法确定此Class对象所表示的类或接口是不一样的，或者说是一个超类或超接口，
            // 由指定的Class参数所表示类或接口。
            // 不存在或者不一样
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                // Only add if not already found in a sub class
                return true;
            } else {// 存在且一样
                // Revert the put, old class is further down the class hierarchy
                subscriberClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        void moveToSuperclass() {
            if (skipSuperClasses) {// 跳过超类
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();// 获取超类
                String clazzName = clazz.getName();
                /** Skip system classes, this just degrades（降低） performance. */
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") || clazzName.startsWith("android.")) {
                    clazz = null;
                }
            }
        }
    }

}
