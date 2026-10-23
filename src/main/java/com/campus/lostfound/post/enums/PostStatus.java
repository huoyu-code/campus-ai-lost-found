package com.campus.lostfound.post.enums;

import java.util.EnumSet;
import java.util.Set;

public enum PostStatus {
    PUBLISHED,
    PROCESSING,
    RESOLVED,
    CLOSED,
    HIDDEN;

    public static Set<PostStatus> publiclyVisible() {
        return EnumSet.of(PUBLISHED, PROCESSING, RESOLVED);
    }

    public boolean isPubliclyVisible() {
        return publiclyVisible().contains(this);
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}

