package com.bumptech.glide.tests;

import android.os.Looper;
import android.os.MessageQueue;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadows.ShadowLegacyLooper;

import static org.mockito.Mockito.mock;

@Implements(Looper.class)
public class GlideShadowLooper extends ShadowLegacyLooper {
    public static MessageQueue queue = mock(MessageQueue.class);

    @Implementation
    public static MessageQueue myQueue() {
        return queue;
    }

    @Resetter
    @Override
    public void reset() {
        queue = mock(MessageQueue.class);
    }
}
