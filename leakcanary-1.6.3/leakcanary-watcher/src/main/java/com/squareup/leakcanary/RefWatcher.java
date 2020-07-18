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

import java.io.File;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.squareup.leakcanary.HeapDumper.RETRY_LATER;
import static com.squareup.leakcanary.Preconditions.checkNotNull;
import static com.squareup.leakcanary.Retryable.Result.DONE;
import static com.squareup.leakcanary.Retryable.Result.RETRY;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Watches references that should become weakly reachable. When the {@link RefWatcher} detects that
 * a reference might not be weakly reachable when it should, it triggers the {@link HeapDumper}.
 *
 * <p>This class is thread-safe: you can call {@link #watch(Object)} from any thread.
 */
public final class RefWatcher {

    public static final RefWatcher DISABLED = new RefWatcherBuilder<>().build();

    private final WatchExecutor watchExecutor; // 执行内存泄漏检测的 Executor
    private final DebuggerControl debuggerControl;// 用于查询是否在 debug 调试模式下，调试中不会执行内存泄漏检测。
    private final GcTrigger gcTrigger;// GC 开关，调用系统GC。
    private final HeapDumper heapDumper;// 用于产生内存泄漏分析用的 dump 文件。即 dump 内存 head。
    private final HeapDump.Listener heapdumpListener;// 用于分析 dump 文件，生成内存泄漏分析报告。
    private final HeapDump.Builder heapDumpBuilder;
    private final Set<String> retainedKeys;// 保存待检测和产生内存泄漏的引用的 key。
    private final ReferenceQueue<Object> queue; // 用于判断弱引用持有的对象是否被 GC。

    RefWatcher(WatchExecutor watchExecutor, DebuggerControl debuggerControl, GcTrigger gcTrigger,
               HeapDumper heapDumper, HeapDump.Listener heapdumpListener, HeapDump.Builder heapDumpBuilder) {
        this.watchExecutor = checkNotNull(watchExecutor, "watchExecutor");
        this.debuggerControl = checkNotNull(debuggerControl, "debuggerControl");
        this.gcTrigger = checkNotNull(gcTrigger, "gcTrigger");
        this.heapDumper = checkNotNull(heapDumper, "heapDumper");
        this.heapdumpListener = checkNotNull(heapdumpListener, "heapdumpListener");
        this.heapDumpBuilder = heapDumpBuilder;
        retainedKeys = new CopyOnWriteArraySet<>();
        queue = new ReferenceQueue<>();
    }

    /**
     * Identical to {@link #watch(Object, String)} with an empty string reference name.
     *
     * @see #watch(Object, String)
     */
    public void watch(Object watchedReference) {
        watch(watchedReference, "");
    }

    /**
     * Watches the provided references and checks if it can be GCed. This method is non blocking,
     * the check is done on the {@link WatchExecutor} this {@link RefWatcher} has been constructed
     * with.
     *
     * @param referenceName An logical identifier for the watched object.
     */
    public void watch(Object watchedReference, String referenceName) {
        if (this == DISABLED) {
            return;
        }
        checkNotNull(watchedReference, "watchedReference");
        checkNotNull(referenceName, "referenceName");
        //随机生成 watchedReference 的 key 保证其唯一性
        final long watchStartNanoTime = System.nanoTime();
        String key = UUID.randomUUID().toString();
        retainedKeys.add(key);
        //这是一个弱引用的子类拓展类 用于 watchedReference 和  queue 的联合使用
        final KeyedWeakReference reference =
                new KeyedWeakReference(watchedReference, key, referenceName, queue);

        // 确定是否 内存泄漏
        ensureGoneAsync(watchStartNanoTime, reference);
    }

    /**
     * LeakCanary will stop watching any references that were passed to {@link #watch(Object, String)}
     * so far.
     */
    public void clearWatchedReferences() {
        retainedKeys.clear();
    }

    boolean isEmpty() {
        removeWeaklyReachableReferences();
        return retainedKeys.isEmpty();
    }

    HeapDump.Builder getHeapDumpBuilder() {
        return heapDumpBuilder;
    }

    Set<String> getRetainedKeys() {
        return new HashSet<>(retainedKeys);
    }

    private void ensureGoneAsync(final long watchStartNanoTime, final KeyedWeakReference reference) {
        watchExecutor.execute(new Retryable() {
            @Override
            public Retryable.Result run() {
                return ensureGone(reference, watchStartNanoTime);
            }
        });
    }

    @SuppressWarnings("ReferenceEquality")
        // Explicitly checking for named null.
    Retryable.Result ensureGone(final KeyedWeakReference reference, final long watchStartNanoTime) {
        long gcStartNanoTime = System.nanoTime();
        long watchDurationMs = NANOSECONDS.toMillis(gcStartNanoTime - watchStartNanoTime);

        // 把 queue 的引用 根据 key 从 retainedKeys 中引出 。
        // retainedKeys 中剩下的就是没有分析和内存泄漏的引用的 key
        removeWeaklyReachableReferences();

        // 处于 debug 模式那么就直接返回
        if (debuggerControl.isDebuggerAttached()) {
            // The debugger can create false leaks.
            return RETRY;
        }

        /**
         * 判断 retainedKeys 中是否包含当前 Activity 引用的 key 值。
         * <p>
         * 如果不包含，说明上一步操作中 retainedKeys 移除了该引用的 key 值，也就说上一步操作之前引用队列
         * queue 中包含该引用，GC 处理了该引用，未发生内存泄漏，返回 DONE，不再往下执行。
         * <p>
         * 如果包含，并不会立即判定发生内存泄漏，可能存在某个对象已经不可达，但是尚未进入引用队列 queue。
         * 这时会主动执行一次 GC 操作之后再次进行判断。
         */
        if (gone(reference)) {
            return DONE;
        }

        /**
         * 如果包含，并不会立即判定发生内存泄漏，可能存在某个对象已经不可达，但是尚未进入引用队列 queue。
         * 这时会主动执行一次 GC 操作之后再次进行判断。
         *
         * 注意这里调用 GC 的写法，并不是使用 System.gc。System.gc 仅仅只是通知系统在合适的时间进行
         * 一次垃圾回收操作，实际上并不能保证一定执行。这里调用是  Runtime.getRuntime().gc();
         *
         * 主动进行 GC 之后会再次进行判定，过程同上。首先调用 removeWeaklyReachableReferences()
         * 清除 retainedKeys 中弱引用的 key 值，再判断是否移除。如果仍然没有移除，判定为内存泄漏。
         */
        gcTrigger.runGc();
        // 再次 清理下 retainedKeys
        removeWeaklyReachableReferences();
        // 最后还有 就是说明内存泄漏了
        if (!gone(reference)) {
            long startDumpHeap = System.nanoTime();
            long gcDurationMs = NANOSECONDS.toMillis(startDumpHeap - gcStartNanoTime);

            // dump 出 Head 报告
            File heapDumpFile = heapDumper.dumpHeap();
            if (heapDumpFile == RETRY_LATER) {
                // Could not dump the heap.
                return RETRY;
            }
            long heapDumpDurationMs = NANOSECONDS.toMillis(System.nanoTime() - startDumpHeap);
            // 最后进行分析 这份 HeapDump，LeakCanary 分析内存泄露用的是一个第三方工具 HAHA
            HeapDump heapDump = heapDumpBuilder.heapDumpFile(heapDumpFile).referenceKey(reference.key)
                    .referenceName(reference.name)
                    .watchDurationMs(watchDurationMs)
                    .gcDurationMs(gcDurationMs)
                    .heapDumpDurationMs(heapDumpDurationMs)
                    .build();

            heapdumpListener.analyze(heapDump);
        }
        return DONE;
    }

    private boolean gone(KeyedWeakReference reference) {
        return !retainedKeys.contains(reference.key);
    }

    /**
     * 这里有个小知识点，弱引用和引用队列 ReferenceQueue 联合使用时，如果弱引用持有的对象被垃圾回收，
     * Java 虚拟机就会把这个弱引用加入到与之关联的引用队列中。即 KeyedWeakReference 持有的 Activity
     * 对象如果被垃圾回收，该对象 (KeyedWeakReference) 就会加入到引用队列 queue 中。
     */
    private void removeWeaklyReachableReferences() {
        // WeakReferences are enqueued as soon as the object to which they point to becomes weakly
        // reachable. This is before finalization or garbage collection has actually happened.
        KeyedWeakReference ref;
        // 如果queue中存在了某个弱引用，说明该弱引用中的对象被回收了，所以要从retainedKeys中移除，
        // 剩下的是没有被回收的
        while ((ref = (KeyedWeakReference) queue.poll()) != null) {
            retainedKeys.remove(ref.key);
        }
    }
}
