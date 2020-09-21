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
package com.squareup.leakcanary;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static com.squareup.leakcanary.Preconditions.checkNotNull;

/**
 * 这里有个小知识点，弱引用和引用队列 ReferenceQueue 联合使用时，如果弱引用持有的对象被垃圾回收，
 * Java 虚拟机就会把这个弱引用加入到与之关联的引用队列中。即 KeyedWeakReference 持有的 Activity
 * 对象如果被垃圾回收，该对象 (KeyedWeakReference) 就会加入到引用队列 queue 中。
 *
 * @see {@link HeapDump#referenceKey}.
 */
final class KeyedWeakReference extends WeakReference<Object> {
    public final String key;
    public final String name;

    KeyedWeakReference(Object referent, String key, String name,
                       ReferenceQueue<Object> referenceQueue) {
        super(checkNotNull(referent, "referent"), checkNotNull(referenceQueue, "referenceQueue"));
        this.key = checkNotNull(key, "key");
        this.name = checkNotNull(name, "name");
    }
}