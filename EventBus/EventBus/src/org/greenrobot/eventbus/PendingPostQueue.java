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

final class PendingPostQueue {
    private PendingPost head;// 头
    private PendingPost tail;// 尾

    // 入队
    synchronized void enqueue(PendingPost pendingPost) {
        if (pendingPost == null) {
            throw new NullPointerException("null cannot be enqueued");
        }
        if (tail != null) {// 尾部不为null
            tail.next = pendingPost;// 放到最后
            tail = pendingPost;// 尾部引用后移，指向最后一个
        } else if (head == null) {// 首次加入，队列头部和尾部都指向同一个消息
            head = tail = pendingPost;
        } else {
            throw new IllegalStateException("Head present, but no tail");
        }
        notifyAll();
    }

    // 出队列（从头部开始）
    synchronized PendingPost poll() {
        PendingPost pendingPost = head;
        if (head != null) {
            head = head.next;
            if (head == null) {// 只有一个，清空队列
                tail = null;
            }
        }
        return pendingPost;
    }

    /**
     * 出队列
     *
     * @param maxMillisToWait 最大等待时间
     *
     * @return 消息
     *
     * @throws InterruptedException
     */
    synchronized PendingPost poll(int maxMillisToWait) throws InterruptedException {
        if (head == null) {
            wait(maxMillisToWait);
        }
        return poll();
    }

}
