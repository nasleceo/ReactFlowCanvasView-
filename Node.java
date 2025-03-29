// Node.java (Inner Class or Separate File)
package com.anass.halak.reactflow;

// ... (Keep imports: PointF, RectF, DrawableRes, NonNull, Nullable, List, ArrayList, Objects, UUID) ...
import android.graphics.PointF;
import android.graphics.RectF;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Node {
    @NonNull public final String id;
    @NonNull public PointF position; // Center position in WORLD coordinates
    @NonNull public RectF size; // Width/Height (relative to 0,0) - Set in constructor
    @NonNull public final NodeShape shape;
    @NonNull public String label;
    @Nullable @DrawableRes public final Integer customDrawableResId; // Icon
    @Nullable @DrawableRes public Integer backgroundDrawableResId;
    @NonNull public final List<Handle> inputHandles = new ArrayList<>();
    @NonNull public final List<Handle> outputHandles = new ArrayList<>();

    // Primary constructor setting size explicitly
    public Node(@NonNull String id,
                @NonNull PointF position,
                float width, // Accept specific width/height
                float height,
                @NonNull NodeShape shape,
                @NonNull String label,
                @Nullable @DrawableRes Integer customIconResId,
                @Nullable @DrawableRes Integer backgroundResId,
                int inputHandleCount,
                int outputHandleCount) {
        this.id = id;
        this.position = position;
        // --- Set size based on provided width/height ---
        this.size = new RectF(0, 0, Math.max(30f, width), Math.max(30f, height)); // Ensure min size
        // ---
        this.shape = shape;
        this.label = label;
        this.customDrawableResId = customIconResId;
        this.backgroundDrawableResId = backgroundResId;
        setupHandles(inputHandleCount, outputHandleCount); // Setup handles based on counts and *final* size
    }

    // Constructor for simple rectangle (will use default size defined here)
    public Node(@NonNull String id, @NonNull PointF position) {
        // --- Use desired default 50x50 dp equivalent (converted later) ---
        // Placeholder size, will be set properly if density is known,
        // otherwise use approximate pixel value or set later.
        // For now, use fixed pixels, updated in View's add methods.
        this(id, position, 120f, 120f, // Approx 50dp, adjust as needed or set in addNode
                NodeShape.RECTANGLE, "Node " + id, null, null, 1, 1);
    }

    // Setup handles based on counts and current *final* size
    public void setupHandles(int inputCount, int outputCount) {
        inputHandles.clear(); outputHandles.clear();
        float handleRadius = Handle.DEFAULT_RADIUS;

        // Position handles vertically centered on edges
        if (inputCount > 0) {
            // Simple case: center vertically for single input handle
            float inputY = this.size.height() / 2f;
            inputHandles.add(new Handle(this.id, Handle.Type.INPUT, new PointF(0, inputY), handleRadius));
            // TODO: Add logic for multiple evenly spaced input handles if needed later
        }
        if (outputCount > 0) {
            // Simple case: center vertically for single output handle
            float outputY = this.size.height() / 2f;
            outputHandles.add(new Handle(this.id, Handle.Type.OUTPUT, new PointF(this.size.width(), outputY), handleRadius));
            // TODO: Add logic for multiple evenly spaced output handles if needed later
        }
    }
    public RectF getBounds() { return new RectF(position.x - size.width() / 2, position.y - size.height() / 2, position.x + size.width() / 2, position.y + size.height() / 2); }
    public List<Handle> getAllHandles() { List<Handle> all = new ArrayList<>(inputHandles); all.addAll(outputHandles); return all; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Node node = (Node) o; return id.equals(node.id); }
    @Override public int hashCode() { return Objects.hash(id); }
}