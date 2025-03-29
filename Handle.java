// Handle.java (or static inner class definition within ReactFlowCanvasView)
package com.anass.halak.reactflow; // Your package

import android.graphics.PointF;
import android.graphics.RectF; // If you add getBounds() back
import androidx.annotation.NonNull;
import java.util.Objects;
import java.util.UUID;

public class Handle { // Keep 'static' if it's an inner class
    public enum Type { INPUT, OUTPUT }

    // === ADD THIS CONSTANT ===
    /** Default radius for handle visuals and hit testing in world coordinates. */
    public static final float DEFAULT_RADIUS = 10f; // Adjust this value as needed (e.g., 8f, 10f, 12f)
    // === END ADDITION ===

    @NonNull public final String id;
    @NonNull public final String nodeId;
    @NonNull public final Type type;
    @NonNull public final PointF relativeOffset; // Relative to node's TOP-LEFT
    public final float radius; // Actual radius used for hit testing (can differ from visual)
    @NonNull public PointF worldPosition = new PointF(0, 0);

    // Constructor using default radius
    public Handle(@NonNull String nodeId, @NonNull Type type, @NonNull PointF relativeOffset) {
        this(nodeId, type, relativeOffset, DEFAULT_RADIUS); // Use the constant
    }
    // Main constructor
    public Handle(@NonNull String nodeId, @NonNull Type type, @NonNull PointF relativeOffset, float radius) {
        this.id = "H_" + UUID.randomUUID().toString().substring(0, 4);
        this.nodeId = nodeId;
        this.type = type;
        this.relativeOffset = relativeOffset;
        // Use the provided radius for hit testing, fall back to default if needed,
        // or just always use DEFAULT_RADIUS for hit testing regardless of visual base.
        // Let's use the constant for the HIT radius for consistency:
        this.radius = DEFAULT_RADIUS; // Use the constant for hit detection radius
    }

    public void updateWorldPosition(@NonNull Node parentNode) {
        float parentLeft = parentNode.position.x - parentNode.size.width() / 2f;
        float parentTop = parentNode.position.y - parentNode.size.height() / 2f;
        this.worldPosition.set(parentLeft + relativeOffset.x, parentTop + relativeOffset.y);
    }

    // Hit detection uses the handle's radius field (which is now DEFAULT_RADIUS)
    public boolean contains(PointF worldPoint, float tolerance) {
        float dx = worldPoint.x - worldPosition.x;
        float dy = worldPoint.y - worldPosition.y;
        float effectiveRadius = this.radius + tolerance; // Use handle's radius + touch tolerance
        return (dx * dx + dy * dy) <= (effectiveRadius * effectiveRadius);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Handle handle = (Handle) o;
        return id.equals(handle.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}