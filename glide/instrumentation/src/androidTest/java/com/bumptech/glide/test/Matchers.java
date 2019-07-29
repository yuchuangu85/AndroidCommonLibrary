package com.bumptech.glide.test;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.bumptech.glide.request.target.Target;

import static org.mockito.ArgumentMatchers.any;

/**
 * Mockito matchers for various common classes.
 */
public final class Matchers {

    private Matchers() {
        // Utility class.
    }

    public static Target<Drawable> anyDrawableTarget() {
        return anyTarget();
    }

    public static Target<Bitmap> anyBitmapTarget() {
        return anyTarget();
    }

    @SuppressWarnings("unchecked")
    public static <T> Target<T> anyTarget() {
        return (Target<T>) any(Target.class);
    }

    public static Bitmap anyBitmap() {
        return any();
    }

    public static Drawable anyDrawable() {
        return any();
    }
}
