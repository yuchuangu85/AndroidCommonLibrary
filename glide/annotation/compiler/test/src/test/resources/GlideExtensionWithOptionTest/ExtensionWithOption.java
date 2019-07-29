package com.bumptech.glide.test;

import com.bumptech.glide.annotation.GlideExtension;
import com.bumptech.glide.annotation.GlideOption;
import com.bumptech.glide.request.BaseRequestOptions;

import androidx.annotation.NonNull;

@GlideExtension
public final class ExtensionWithOption {

    private ExtensionWithOption() {
        // Utility class.
    }

    @NonNull
    @GlideOption
    public static BaseRequestOptions<?> squareThumb(BaseRequestOptions<?> requestOptions) {
        return requestOptions.centerCrop();
    }
}
