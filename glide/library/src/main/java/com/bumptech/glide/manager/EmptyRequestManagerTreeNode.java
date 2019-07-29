package com.bumptech.glide.manager;

import com.bumptech.glide.RequestManager;

import java.util.Collections;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * A {@link RequestManagerTreeNode} that returns no relatives.
 */
final class EmptyRequestManagerTreeNode implements RequestManagerTreeNode {
    @NonNull
    @Override
    public Set<RequestManager> getDescendants() {
        return Collections.emptySet();
    }
}
