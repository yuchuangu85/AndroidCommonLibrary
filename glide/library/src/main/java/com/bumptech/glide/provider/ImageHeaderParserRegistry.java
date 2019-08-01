package com.bumptech.glide.provider;

import com.bumptech.glide.load.ImageHeaderParser;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * Contains an unordered list of {@link ImageHeaderParser}s capable of parsing image headers.
 * 存储图片Header解析器
 */
public final class ImageHeaderParserRegistry {
    private final List<ImageHeaderParser> parsers = new ArrayList<>();

    @NonNull
    public synchronized List<ImageHeaderParser> getParsers() {
        return parsers;
    }

    public synchronized void add(@NonNull ImageHeaderParser parser) {
        parsers.add(parser);
    }
}
