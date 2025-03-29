package com.anass.halak.reactflow;


import androidx.annotation.NonNull;
import java.util.Objects;

import androidx.annotation.NonNull;
import java.util.Objects;
import java.util.UUID; // For generating ID

public class Edge {
    @NonNull public final String id; @NonNull public final String sourceNodeId; @NonNull public final String targetNodeId;
    @NonNull public final String sourceHandleId; @NonNull public final String targetHandleId;
    public final boolean animated; // <<< Added animated flag

    public Edge(@NonNull String sourceNodeId, @NonNull String sourceHandleId, @NonNull String targetNodeId, @NonNull String targetHandleId, boolean animated) {
        this.id = "E_" + UUID.randomUUID().toString().substring(0, 4); this.sourceNodeId = sourceNodeId; this.sourceHandleId = sourceHandleId;
        this.targetNodeId = targetNodeId; this.targetHandleId = targetHandleId; this.animated = animated;
    }
    public Edge(@NonNull String sourceNodeId, @NonNull String sourceHandleId, @NonNull String targetNodeId, @NonNull String targetHandleId) {
        this(sourceNodeId, sourceHandleId, targetNodeId, targetHandleId, false);
    }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Edge edge = (Edge) o; return id.equals(edge.id); }
    @Override public int hashCode() { return Objects.hash(id); }}
