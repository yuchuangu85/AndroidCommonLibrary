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

/**
 * Each subscriber method has a thread mode, which determines in which thread the method is to be called by EventBus.
 * EventBus takes care of threading independently from the posting thread.
 *
 * @author Markus
 * @see EventBus#register(Object)
 */
public enum ThreadMode {
    /**
     * 事件接收和发送在相同的线程，不需要切换线程，开销最小。如果在主线程，避免事件处理阻塞线程
     * <p>
     * Subscriber will be called directly in the same thread, which is posting the event. This is the default. Event delivery
     * implies the least(最少) overhead(开销) because it avoids thread switching completely. Thus this is the recommended mode for
     * simple tasks that are known to complete in a very short time without requiring the main thread. Event handlers
     * using this mode must return quickly to avoid blocking the posting thread, which may be the main thread.
     */
    POSTING,

    /**
     * 在Android中主线程中处理事件，如果是主线程中调用，则直接调用事件接收方法，如果在子线程发送消息，则通过消息队列（非阻塞）
     * <p>
     * On Android, subscriber will be called in Android's main thread (UI thread). If the posting thread is
     * the main thread, subscriber methods will be called directly, blocking the posting thread. Otherwise the event
     * is queued for delivery (non-blocking). Subscribers using this mode must return quickly to avoid blocking the main thread.
     * If not on Android, behaves the same as {@link #POSTING}.
     */
    MAIN,

    /**
     * 在Android上，订阅者在主线程调用。事件排队分发，不会造成消息阻塞
     * <p>
     * On Android, subscriber will be called in Android's main thread (UI thread). Different from {@link #MAIN},
     * the event will always be queued for delivery. This ensures that the post call is non-blocking.
     */
    MAIN_ORDERED,

    /**
     * 订阅者在后台线程中被调用，如果发送事件在后台线程，订阅者直接调用。如果发送事件在主线程，EventBus会通过一个后台单一线程，通过顺序方式发送事件。
     * 订阅者用这个模式要快速返回，避免消息阻塞。
     * <p>
     * On Android, subscriber will be called in a background thread. If posting thread is not the main thread, subscriber methods
     * will be called directly in the posting thread. If the posting thread is the main thread, EventBus uses a single
     * background thread, that will deliver all its events sequentially. Subscribers using this mode should try to
     * return quickly to avoid blocking the background thread. If not on Android, always uses a background thread.
     */
    BACKGROUND,

    /**
     * 订阅者会在一个独立线程被调用。订阅者线程会一直独立与发送事件线程和主线程。发送事件线程不会等待订阅者方法。如果订阅者执行需要耗时则最好
     * 使用这种模式。为了避免大量线程，最好使用线程池，以高效复用线程。
     * <p>
     * Subscriber will be called in a separate thread. This is always independent from the posting thread and the
     * main thread. Posting events never wait for subscriber methods using this mode. Subscriber methods should
     * use this mode if their execution might take some time, e.g. for network access. Avoid triggering a large number
     * of long running asynchronous subscriber methods at the same time to limit the number of concurrent threads. EventBus
     * uses a thread pool to efficiently reuse threads from completed asynchronous subscriber notifications.
     */
    ASYNC
}