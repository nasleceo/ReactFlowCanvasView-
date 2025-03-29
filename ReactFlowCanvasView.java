package com.anass.halak.reactflow; // Replace with your actual package name

// --- Imports ---
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector; // Keep for listener definition
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableKt;

import com.anass.halak.R; // Import your project's R class

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ReactFlowCanvasView extends View {

    private static final String TAG = "ReactFlowCanvasView";
    private float gridSpacing = 50f;
    private static final float GRID_DOT_RADIUS = 1.5f;

    // --- State ---
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private Map<String, Handle> handleMap = new HashMap<>();
    private Map<String, Node> nodeMap = new HashMap<>();

    // --- Cache & Density ---
    private SparseArray<Bitmap> bitmapCache = new SparseArray<>(); // For Icons
    private SparseArray<Drawable.ConstantState> bgDrawableCache = new SparseArray<>(); // <<< Cache for Background Drawables
    private float density;

    // --- NEW: Default Node Dimensions ---
    private static final float DEFAULT_NODE_WIDTH_DP = 55f; // Adjusted slightly wider
    private static final float DEFAULT_NODE_HEIGHT_DP = 55f;
    private static final float DEFAULT_ICON_SIZE_DP = 25f; // Adjusted icon size


    // --- Panning and Zooming ---
    private float offsetX = 0f, offsetY = 0f, scaleFactor = 1.0f;
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 3.0f;
    private static final float ZOOM_STEP = 1.2f;
    private Matrix viewMatrix = new Matrix(), inverseViewMatrix = new Matrix();
    private float lastTouchX, lastTouchY;
    private boolean isPanning = false;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    // --- Interaction States ---
    private Node draggingNode = null;
    private float dragNodeStartXOffsetWorld, dragNodeStartYOffsetWorld;
    private float handleHitRadiusWorld = 15f; // Configurable hit radius
    private static final float HANDLE_VISUAL_RADIUS_BASE = 8f;
    private boolean isDrawingConnection = false;
    @Nullable private Handle connectionStartHandle = null;
    @NonNull private PointF connectionCurrentDragPointWorld = new PointF();
    @Nullable private Handle potentialTargetHandle = null;

    // --- Drawing Tools & Configurable Properties ---
    private Paint gridPaint, nodeBgPaint, nodeBorderPaint, edgePaint, textPaint;
    private Paint handlePaintInput, handlePaintOutput, handleBorderPaint, tempConnectionPaint;
    private Paint gridDotPaint;
    private Paint arrowHeadPaint;
    private Path edgeDrawingPath = new Path();
    private Path arrowHeadPath = new Path();
    private DashPathEffect animatedEdgeDashEffect = new DashPathEffect(new float[]{20, 10}, 0);
    private float defaultNodeCornerRadiusDp = 10f;
    private float defaultEdgeStrokeWidth = 3f;
    private float defaultArrowheadSize = 10f;
    private boolean drawArrowheads = true;
    private float gridDotBaseRadius = 1.5f;


    // --- Listener ---
    public interface ConnectionListener {
        void onEdgeConnected(Edge newEdge);
        void onConnectionAttempted(Handle startHandle, @Nullable Handle endHandle);
    }
    private ConnectionListener connectionListener = null;

    // --- Constructors & Init ---
    public ReactFlowCanvasView(Context context) { super(context); init(context); }
    public ReactFlowCanvasView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(context); }
    public ReactFlowCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(context); }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        // Initialize Paints (Keep styles, adjust defaults if needed)
        gridDotPaint = new Paint(); gridDotPaint.setStyle(Paint.Style.FILL); gridDotPaint.setAntiAlias(true);
        nodeBgPaint = new Paint(); nodeBgPaint.setStyle(Paint.Style.FILL); nodeBgPaint.setAntiAlias(true);
        nodeBorderPaint = new Paint(); nodeBorderPaint.setStyle(Paint.Style.STROKE); nodeBorderPaint.setAntiAlias(true);
        edgePaint = new Paint(); edgePaint.setStyle(Paint.Style.STROKE); edgePaint.setAntiAlias(true); edgePaint.setStrokeJoin(Paint.Join.ROUND); edgePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint = new Paint(); textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setAntiAlias(true);
        handlePaintInput = new Paint(); handlePaintInput.setStyle(Paint.Style.FILL); handlePaintInput.setAntiAlias(true);
        handlePaintOutput = new Paint(); handlePaintOutput.setStyle(Paint.Style.FILL); handlePaintOutput.setAntiAlias(true);
        handleBorderPaint = new Paint(); handleBorderPaint.setStyle(Paint.Style.STROKE); handleBorderPaint.setAntiAlias(true);
        tempConnectionPaint = new Paint(); tempConnectionPaint.setStyle(Paint.Style.STROKE); tempConnectionPaint.setAntiAlias(true); tempConnectionPaint.setPathEffect(new DashPathEffect(new float[]{15, 10}, 0));
        arrowHeadPaint = new Paint(); arrowHeadPaint.setStyle(Paint.Style.FILL); arrowHeadPaint.setAntiAlias(true);

        // Apply Default Configurable Values
        setGridDotColor(Color.WHITE); // Slightly lighter grid
        setDefaultNodeBgColor(Color.parseColor("#FAFAFA")); // Lighter node background
        setDefaultNodeBorderColor(Color.parseColor("#DDDDDD")); // Lighter border
        setDefaultNodeTextColor(Color.parseColor("#333333")); // Darker text
        setDefaultNodeTextSize(12f * getResources().getDisplayMetrics().scaledDensity); // Default text size in SP
        setEdgeColor(Color.parseColor("#B0BEC5")); // Lighter gray edges
        setEdgeStrokeWidth(2f); // Thinner edges
        setTempConnectionColor(Color.parseColor("#FF9800")); // Orange temp line
        setHandleInputColor(Color.parseColor("#90A4AE")); // Grayish input
        setHandleOutputColor(Color.parseColor("#90A4AE")); // Grayish output
        setHandleBorderColor(Color.parseColor("#CFD8DC")); // Light border for handles
        setArrowheadColor(edgePaint.getColor());
        setArrowheadSize(8f); // Slightly smaller arrowheads

        addSampleData();
        updateMaps();
        setBackgroundColor(Color.DKGRAY); // Lighter background overall
    }

    // --- Data Loading & Management ---
    private void addSampleData() {
        nodes.clear(); edges.clear();
        float nodeW = DEFAULT_NODE_WIDTH_DP * density;
        float nodeH = DEFAULT_NODE_HEIGHT_DP * density;
        // Use the full Node constructor with new dimensions
        // REPLACE R.drawable... with YOUR actual drawable IDs
        nodes.add(new Node("N1", new PointF(150f, 200f), nodeW, nodeH, NodeShape.CUSTOM_DRAWABLE, "Schedule", R.drawable.ic_launcher_foreground, R.drawable.node_background_shape, 1, 1));
        nodes.add(new Node("N2", new PointF(350f, 200f), nodeW, nodeH, NodeShape.CUSTOM_DRAWABLE, "HTTP", R.drawable.ic_launcher_foreground, R.drawable.node_background_shape, 1, 1));
        nodes.add(new Node("N3", new PointF(550f, 200f), nodeW, nodeH, NodeShape.CUSTOM_DRAWABLE, "Code", R.drawable.ic_launcher_foreground, R.drawable.node_background_shape, 1, 1));
        nodes.add(new Node("N4", new PointF(750f, 200f), nodeW, nodeH, NodeShape.CUSTOM_DRAWABLE, "Sheets", R.drawable.ic_launcher_foreground, R.drawable.node_background_shape, 1, 1));

        updateMaps(); // Update maps after adding nodes

        // Connect sample nodes
        Handle n1Out = nodeMap.get("N1").outputHandles.get(0); Handle n2In = nodeMap.get("N2").inputHandles.get(0);
        Handle n2Out = nodeMap.get("N2").outputHandles.get(0); Handle n3In = nodeMap.get("N3").inputHandles.get(0);
        Handle n3Out = nodeMap.get("N3").outputHandles.get(0); Handle n4In = nodeMap.get("N4").inputHandles.get(0);

        if (n1Out != null && n2In != null) edges.add(new Edge("N1", n1Out.id, "N2", n2In.id, true));
        if (n2Out != null && n3In != null) edges.add(new Edge("N2", n2Out.id, "N3", n3In.id, true));
        if (n3Out != null && n4In != null) edges.add(new Edge("N3", n3Out.id, "N4", n4In.id, true));
    }

    private void updateMaps() {
        nodeMap.clear();
        handleMap.clear();
        for (Node node : nodes) {
            nodeMap.put(node.id, node);
            for (Handle handle : node.getAllHandles()) {
                handleMap.put(handle.id, handle);
                handle.updateWorldPosition(node);
            }
        }
    }

    private void updateAllHandleWorldPositions() {
        for (Node node : nodes) {
            for (Handle handle : node.getAllHandles()) {
                handle.updateWorldPosition(node);
            }
        }
    }

    // --- Coordinate Transformation Helpers ---
    private void updateMatrices() {
        viewMatrix.reset();
        viewMatrix.postTranslate(offsetX, offsetY);
        viewMatrix.postScale(scaleFactor, scaleFactor);
        if (!viewMatrix.invert(inverseViewMatrix)) {
            Log.e(TAG, "Matrix inversion failed!");
            inverseViewMatrix.reset();
        }
    }

    private PointF screenToWorld(float screenX, float screenY) {
        float[] pts = {screenX, screenY};
        inverseViewMatrix.mapPoints(pts);
        return new PointF(pts[0], pts[1]);
    }

    // --- Drawing ---
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateMatrices();
        updateAllHandleWorldPositions();

        canvas.save();
        canvas.concat(viewMatrix); // Apply pan/zoom

        drawGrid(canvas);
        drawEdges(canvas);
        drawNodesAndHandles(canvas);
        drawTemporaryConnection(canvas);

        canvas.restore(); // Remove pan/zoom
    }

    private void drawGrid(Canvas canvas) {
        float scaledDotRadius = Math.max(0.5f, Math.min(3f, gridDotBaseRadius / scaleFactor * 1.2f));
        gridDotPaint.setAlpha((int) (Math.min(1.0f, scaleFactor) * 150));

        PointF topLeftWorld = screenToWorld(0, 0); PointF bottomRightWorld = screenToWorld(getWidth(), getHeight());
        float padding = gridSpacing * 2; float left = topLeftWorld.x - padding; float top = topLeftWorld.y - padding; float right = bottomRightWorld.x + padding; float bottom = bottomRightWorld.y + padding;
        float startX = (float) (Math.floor(left / gridSpacing) * gridSpacing); float endX = (float) (Math.ceil(right / gridSpacing) * gridSpacing);
        float startY = (float) (Math.floor(top / gridSpacing) * gridSpacing); float endY = (float) (Math.ceil(bottom / gridSpacing) * gridSpacing);

        float minVisibleSpacing = 4.0f; if (gridSpacing * scaleFactor < minVisibleSpacing) return;

        for (float x = startX; x <= endX; x += gridSpacing) {
            for (float y = startY; y <= endY; y += gridSpacing) {
                canvas.drawCircle(x, y, scaledDotRadius, gridDotPaint);
            }
        }
    }

    private void drawEdges(Canvas canvas) {
        float baseStrokeWidth = Math.max(1.0f, Math.min(6f, defaultEdgeStrokeWidth / scaleFactor));
        edgePaint.setStrokeWidth(baseStrokeWidth);
        arrowHeadPaint.setColor(edgePaint.getColor());
        float scaledArrowSize = defaultArrowheadSize / scaleFactor;

        PathMeasure pathMeasure = new PathMeasure();

        for (Edge edge : edges) {
            Handle sourceHandle = handleMap.get(edge.sourceHandleId); Handle targetHandle = handleMap.get(edge.targetHandleId);
            if (sourceHandle != null && targetHandle != null) {
                PointF start = sourceHandle.worldPosition; PointF end = targetHandle.worldPosition;
                edgeDrawingPath.reset(); edgeDrawingPath.moveTo(start.x, start.y);
                float midX = (start.x + end.x) / 2; float midY = (start.y + end.y) / 2;
                float dx = end.x - start.x; float dy = end.y - start.y;
                float controlOffsetScale = 0.25f;
                float controlX = midX - dy * controlOffsetScale; float controlY = midY + dx * controlOffsetScale;
                edgeDrawingPath.quadTo(controlX, controlY, end.x, end.y);

                edgePaint.setPathEffect(edge.animated ? animatedEdgeDashEffect : null);
                canvas.drawPath(edgeDrawingPath, edgePaint);
                edgePaint.setPathEffect(null);

                if (drawArrowheads) {
                    pathMeasure.setPath(edgeDrawingPath, false); float pathLength = pathMeasure.getLength();
                    if (pathLength > 0.01f) {
                        float[] pos = new float[2]; float[] tan = new float[2];
                        pathMeasure.getPosTan(pathLength, pos, tan);
                        double angle = Math.atan2(tan[1], tan[0]);

                        Path scaledArrowHeadPath = new Path();
                        scaledArrowHeadPath.moveTo(-scaledArrowSize, scaledArrowSize / 2);
                        scaledArrowHeadPath.lineTo(0, 0);
                        scaledArrowHeadPath.lineTo(-scaledArrowSize, -scaledArrowSize / 2);
                        scaledArrowHeadPath.close();

                        Matrix arrowMatrix = new Matrix(); arrowMatrix.setRotate((float) Math.toDegrees(angle)); arrowMatrix.postTranslate(pos[0], pos[1]);
                        Path transformedArrow = new Path();
                        scaledArrowHeadPath.transform(arrowMatrix, transformedArrow);
                        canvas.drawPath(transformedArrow, arrowHeadPaint);
                    }
                }
            }
        }
    }

    // UPDATED: Draw Nodes then Labels then Handles
    private void drawNodesAndHandles(Canvas canvas) {
        nodeBorderPaint.setStrokeWidth(Math.max(0.8f, Math.min(3f, 1.5f / scaleFactor)));
        float scaledTextSize = Math.max(10f * density, Math.min(16f * density, textPaint.getTextSize() / scaleFactor)); // Adjust SP range and scale
        textPaint.setTextSize(scaledTextSize);

        RectF tempBounds = new RectF();
        Rect tempTextBounds = new Rect();

        // --- Draw Node Backgrounds and Icons ---
        for (Node node : nodes) {
            tempBounds.set(node.getBounds());
            drawNodeContent(canvas, node, tempBounds); // Draws background and icon
        }

        // --- Draw Labels Below Nodes ---
        for (Node node : nodes) {
            tempBounds.set(node.getBounds()); // Get bounds again for positioning
            String label = node.label;
            textPaint.getTextBounds(label, 0, label.length(), tempTextBounds);
            float labelMargin = 6f * density / scaleFactor; // Margin in scaled DP
            float labelY = tempBounds.bottom + labelMargin - textPaint.ascent(); // Position below bottom + margin
            canvas.drawText(label, tempBounds.centerX(), labelY, textPaint);
        }

        // --- Draw Handles on Top ---
        for (Node node : nodes) {
            drawHandlesForNode(canvas, node);
        }
    }

    // === UPDATED Helper to Draw Node Background/Content ===
    private void drawNodeContent(Canvas canvas, Node node, RectF bounds) {
        boolean useShapeDrawable = node.backgroundDrawableResId != null;
        Drawable backgroundDrawable = null;
        float cornerRadius = defaultNodeCornerRadiusDp * density; // Use configured radius

        if (useShapeDrawable) {
            backgroundDrawable = loadAndCacheBackgroundDrawable(node.backgroundDrawableResId);
        }

        // Draw Drag Highlight (optional, under border)
        if(node == draggingNode) {
            Paint dragHighlightPaint = new Paint(nodeBorderPaint);
            dragHighlightPaint.setColor(Color.parseColor("#FFEB3B")); // Yellow highlight
            dragHighlightPaint.setStrokeWidth(nodeBorderPaint.getStrokeWidth() * 2f);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, dragHighlightPaint);
        }

        // Draw Background
        if (backgroundDrawable != null) {
            backgroundDrawable.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
            // Tinting the background shape drawable if needed (example)
            // Drawable mutableDrawable = DrawableCompat.wrap(backgroundDrawable).mutate();
            // DrawableCompat.setTint(mutableDrawable, nodeBgPaint.getColor()); // Tint with default node bg color
            // mutableDrawable.draw(canvas);
            backgroundDrawable.draw(canvas); // Draw original or tinted
        } else {
            // Fallback: Draw using default Paint if no shape drawable
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, nodeBgPaint);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, nodeBorderPaint); // Draw border only if no shape bg
        }

        // Draw Icon (if available), centered
        if (node.shape == NodeShape.CUSTOM_DRAWABLE && node.customDrawableResId != null) {
            float iconSizePx = DEFAULT_ICON_SIZE_DP * density; // Use constant for icon size
            int iconTargetSize = (int) Math.max(1, iconSizePx);

            Bitmap bitmap = loadAndCacheBitmap(node.customDrawableResId, iconTargetSize, iconTargetSize);
            if (bitmap != null) {
                // Calculate position to center the icon
                float iconX = bounds.centerX() - iconTargetSize / 2f;
                float iconY = bounds.centerY() - iconTargetSize / 2f;
                canvas.drawBitmap(bitmap, iconX, iconY, null);
            }
        }
        // Note: If using a shape drawable with stroke, border is drawn by backgroundDrawable.draw()
    }
    // === END Node Content Helper ===


    private void drawHandlesForNode(Canvas canvas, Node node) {
        float scaledVisualRadius = Math.max(3f, Math.min(12f, HANDLE_VISUAL_RADIUS_BASE / scaleFactor * 1.3f));
        handleBorderPaint.setStrokeWidth(Math.max(0.5f, Math.min(2f, 1.5f / scaleFactor)));

        for (Handle handle : node.getAllHandles()) {
            Paint fillPaint = (handle.type == Handle.Type.INPUT) ? handlePaintInput : handlePaintOutput;
            if (handle == potentialTargetHandle && isDrawingConnection) {
                Paint highlightPaint = new Paint(fillPaint); highlightPaint.setAlpha(100);
                canvas.drawCircle(handle.worldPosition.x, handle.worldPosition.y, scaledVisualRadius * 1.6f, highlightPaint);
            }
            canvas.drawCircle(handle.worldPosition.x, handle.worldPosition.y, scaledVisualRadius, fillPaint);
            canvas.drawCircle(handle.worldPosition.x, handle.worldPosition.y, scaledVisualRadius, handleBorderPaint);
        }
    }

    // === Cache method for Background Drawables ===
    @Nullable
    private Drawable loadAndCacheBackgroundDrawable(@Nullable Integer resId) {
        if (resId == null) return null;
        Drawable.ConstantState constantState = bgDrawableCache.get(resId);
        if (constantState == null) {
            try {
                // Use AppCompatResources to handle vector drawables correctly
                Drawable drawable = AppCompatResources.getDrawable(getContext(), resId);
                if (drawable != null) {
                    // Important: Mutate before caching or changing state (like bounds/tint)
                    constantState = drawable.getConstantState();
                    if (constantState != null) {
                        bgDrawableCache.put(resId, constantState);
                        Log.d(TAG, "Cached background drawable: " + resId);
                        // Return a new instance based on the constant state
                        return constantState.newDrawable(getResources()).mutate();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading background drawable: " + resId, e);
            }
            return null; // Failed to load or cache
        } else {
            // Return a new instance from the cached state
            return constantState.newDrawable(getResources()).mutate();
        }
    }

    private void drawTemporaryConnection(Canvas canvas) {
        if (isDrawingConnection && connectionStartHandle != null) {
            tempConnectionPaint.setStrokeWidth(Math.max(1.5f, Math.min(6f, 4f / scaleFactor)));
            canvas.drawLine(connectionStartHandle.worldPosition.x, connectionStartHandle.worldPosition.y,
                    connectionCurrentDragPointWorld.x, connectionCurrentDragPointWorld.y, tempConnectionPaint);
        }
    }

    // --- Node Shape Drawing Helpers ---
    private void drawRectNode(Canvas canvas, Node node, RectF bounds) {
        float cornerRadius = defaultNodeCornerRadiusDp * density;
        Paint currentBgPaint = nodeBgPaint;
        if(node == draggingNode) {
            Paint dragHighlight = new Paint(nodeBorderPaint); dragHighlight.setColor(Color.YELLOW);
            dragHighlight.setStrokeWidth(nodeBorderPaint.getStrokeWidth() * 1.5f);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, dragHighlight);
        }
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, currentBgPaint);
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, nodeBorderPaint);
    }
    private void drawCubeNode(Canvas canvas, Node node, RectF bounds) { drawRectNode(canvas, node, bounds); } // Fallback
    private void drawDrawableNode(Canvas canvas, Node node, RectF bounds) {
        float cornerRadius = defaultNodeCornerRadiusDp * density;
        Paint customBgPaint = nodeBgPaint;
        Paint customBorderPaint = nodeBorderPaint;
        if(node == draggingNode) {
            Paint dragHighlight = new Paint(customBorderPaint); dragHighlight.setColor(Color.YELLOW);
            dragHighlight.setStrokeWidth(customBorderPaint.getStrokeWidth() * 1.5f);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, dragHighlight);
        }
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, customBgPaint);
        if (node.customDrawableResId != null) {
            float iconSizeFactor = 0.45f; int iconTargetSize = (int) Math.max(1, bounds.height() * iconSizeFactor);
            Bitmap bitmap = loadAndCacheBitmap(node.customDrawableResId, iconTargetSize, iconTargetSize);
            if (bitmap != null) {
                float iconPaddingLeft = bounds.width() * 0.15f; float iconX = bounds.left + iconPaddingLeft;
                float iconY = bounds.centerY() - iconTargetSize / 2f; canvas.drawBitmap(bitmap, iconX, iconY, null);
            }
        }
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, customBorderPaint);
    }
    private Bitmap loadAndCacheBitmap(int resId, int reqWidth, int reqHeight) {
        Bitmap bitmap = bitmapCache.get(resId);
        if (bitmap == null) {
            try {
                Drawable drawable = AppCompatResources.getDrawable(getContext(), resId);
                if (drawable != null) {
                    bitmap = DrawableKt.toBitmap(drawable, reqWidth, reqHeight, null);
                    if (bitmap != null) bitmapCache.put(resId, bitmap);
                }
            } catch (Exception e) { Log.e(TAG, "Error loading drawable: " + resId, e); }
        }
        return bitmap;
    }

    // --- Public Zoom Methods ---
    public void zoomIn() { applyZoom(ZOOM_STEP); }
    public void zoomOut() { applyZoom(1.0f / ZOOM_STEP); }
    private void applyZoom(float scaleMultiplier) {
        float oldScaleFactor = scaleFactor;
        float newScaleFactor = oldScaleFactor * scaleMultiplier;
        newScaleFactor = Math.max(MIN_SCALE, Math.min(newScaleFactor, MAX_SCALE));
        float actualScaleChange = newScaleFactor / oldScaleFactor;
        if (actualScaleChange != 1.0f) {
            float viewCenterX = getWidth() / 2f; float viewCenterY = getHeight() / 2f;
            offsetX = viewCenterX - (viewCenterX - offsetX) * actualScaleChange;
            offsetY = viewCenterY - (viewCenterY - offsetY) * actualScaleChange;
            scaleFactor = newScaleFactor;
            Log.d(TAG, "Applied Zoom: New Scale=" + scaleFactor);
            invalidate();
        }
    }

    // --- Touch Input Handling ---
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateMatrices();

        boolean handledByScale = false; // Pinch zoom disabled

        final int action = event.getActionMasked();
        final int pointerIndex = event.getActionIndex();
        final int pointerId = event.getPointerId(pointerIndex);
        final int activePointerIndex = (activePointerId != MotionEvent.INVALID_POINTER_ID) ? event.findPointerIndex(activePointerId) : 0;

        if (activePointerIndex < 0 || activePointerIndex >= event.getPointerCount()) {
            if ((action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && activePointerId != MotionEvent.INVALID_POINTER_ID) { resetInteractions(); return true; }
            else if (action != MotionEvent.ACTION_POINTER_UP && action != MotionEvent.ACTION_DOWN) { return true; }
        }

        float currentX = (activePointerIndex >= 0 && activePointerIndex < event.getPointerCount()) ? event.getX(activePointerIndex) : event.getX(0);
        float currentY = (activePointerIndex >= 0 && activePointerIndex < event.getPointerCount()) ? event.getY(activePointerIndex) : event.getY(0);
        PointF worldPoint = screenToWorld(currentX, currentY);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0); lastTouchX = currentX; lastTouchY = currentY;
                PointF worldPointDown = screenToWorld(lastTouchX, lastTouchY);
                Handle touchedHandle = findHandleAtWorldPoint(worldPointDown);
                if (touchedHandle != null) { // Start Connection
                    isDrawingConnection = true; connectionStartHandle = touchedHandle;
                    connectionCurrentDragPointWorld.set(worldPointDown); draggingNode = null; isPanning = false;
                } else { // Try Drag Node
                    draggingNode = findNodeContainingWorldPoint(worldPointDown);
                    if (draggingNode != null) {
                        dragNodeStartXOffsetWorld = worldPointDown.x - draggingNode.position.x;
                        dragNodeStartYOffsetWorld = worldPointDown.y - draggingNode.position.y;
                        isDrawingConnection = false; isPanning = false; bringNodeToFront(draggingNode);
                    } else { // Pan
                        isDrawingConnection = false; draggingNode = null; isPanning = true;
                    }
                }
                invalidate();
                break;

            case MotionEvent.ACTION_MOVE:
                if (activePointerId == MotionEvent.INVALID_POINTER_ID || activePointerIndex < 0) break;
                float dx = currentX - lastTouchX; float dy = currentY - lastTouchY;
                if (isDrawingConnection) { // Update temporary line
                    connectionCurrentDragPointWorld.set(worldPoint);
                    potentialTargetHandle = findHandleAtWorldPoint(worldPoint);
                    if (potentialTargetHandle != null && !isValidConnectionTarget(connectionStartHandle, potentialTargetHandle)) { potentialTargetHandle = null; }
                    invalidate();
                } else if (draggingNode != null) { // Drag selected node
                    draggingNode.position.set(worldPoint.x - dragNodeStartXOffsetWorld, worldPoint.y - dragNodeStartYOffsetWorld);
                    invalidate();
                } else if (isPanning) { // Pan the canvas
                    offsetX += dx; offsetY += dy;
                    invalidate();
                }
                lastTouchX = currentX; lastTouchY = currentY;
                break;

            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                if (isDrawingConnection && connectionStartHandle != null) { // Attempt to finalize connection
                    Handle targetHandle = findHandleAtWorldPoint(worldPoint);
                    if (targetHandle != null && isValidConnectionTarget(connectionStartHandle, targetHandle)) {
                        createEdge(connectionStartHandle, targetHandle);
                    }
                    if (connectionListener != null) { connectionListener.onConnectionAttempted(connectionStartHandle, targetHandle); }
                }
                resetInteractions(); invalidate();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                final int pointerUpId = event.getPointerId(pointerIndex);
                if (pointerUpId == activePointerId) { // If the primary finger was lifted
                    final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                    if (newPointerIndex < event.getPointerCount()) { // If another finger remains
                        activePointerId = event.getPointerId(newPointerIndex); lastTouchX = event.getX(newPointerIndex); lastTouchY = event.getY(newPointerIndex);
                        if(isDrawingConnection || draggingNode != null) { // Cancel ongoing interaction if primary finger changed
                            if(isDrawingConnection && connectionStartHandle != null && connectionListener != null) { connectionListener.onConnectionAttempted(connectionStartHandle, null); }
                            resetInteractions(); invalidate();
                        }
                    } else { // Last finger lifted
                        if (isDrawingConnection && connectionStartHandle != null) {
                            PointF lastWorldPoint = screenToWorld(event.getX(pointerIndex), event.getY(pointerIndex));
                            Handle targetHandle = findHandleAtWorldPoint(lastWorldPoint);
                            if (targetHandle != null && isValidConnectionTarget(connectionStartHandle, targetHandle)) { createEdge(connectionStartHandle, targetHandle); }
                            if (connectionListener != null) { connectionListener.onConnectionAttempted(connectionStartHandle, targetHandle); }
                        }
                        resetInteractions(); invalidate();
                    }
                }
                break;
        }
        return true; // We handled the event
    }

    // --- Reset Interactions ---
    private void resetInteractions() {
        activePointerId = MotionEvent.INVALID_POINTER_ID; isPanning = false; draggingNode = null;
        isDrawingConnection = false; connectionStartHandle = null; potentialTargetHandle = null;
        connectionCurrentDragPointWorld.set(0, 0); dragNodeStartXOffsetWorld = 0; dragNodeStartYOffsetWorld = 0;
    }

    // --- Hit Testing ---
    @Nullable private Handle findHandleAtWorldPoint(PointF worldPoint) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            for (Handle handle : node.getAllHandles()) {
                float dx = worldPoint.x - handle.worldPosition.x; float dy = worldPoint.y - handle.worldPosition.y;
                if (dx * dx + dy * dy <= handleHitRadiusWorld * handleHitRadiusWorld) return handle;
            }
        } return null;
    }
    @Nullable private Node findNodeContainingWorldPoint(PointF worldPoint) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.getBounds().contains(worldPoint.x, worldPoint.y)) {
                if (findHandleAtWorldPoint(worldPoint) == null) return node; else return null;
            }
        } return null;
    }

    // --- Edge Creation Logic ---
    public boolean isValidConnectionTarget(@Nullable Handle start, @Nullable Handle end) {
        if (start == null || end == null) return false; if (Objects.equals(start.nodeId, end.nodeId)) return false;
        return start.type == Handle.Type.OUTPUT && end.type == Handle.Type.INPUT;
    }
    private void createEdge(@NonNull Handle sourceHandle, @NonNull Handle targetHandle) {
        Handle outputHandle = (sourceHandle.type == Handle.Type.OUTPUT) ? sourceHandle : targetHandle;
        Handle inputHandle = (sourceHandle.type == Handle.Type.INPUT) ? sourceHandle : targetHandle;
        for (Edge edge : edges) { if (Objects.equals(edge.sourceHandleId, outputHandle.id) && Objects.equals(edge.targetHandleId, inputHandle.id)) { Log.w(TAG, "Edge exists."); return; } }
        boolean isAnimated = true; // Default
        Edge newEdge = new Edge(outputHandle.nodeId, outputHandle.id, inputHandle.nodeId, inputHandle.id, isAnimated);
        edges.add(newEdge);
        if (connectionListener != null) { connectionListener.onEdgeConnected(newEdge); }
        invalidate();
    }

    // --- Utility ---
    private void bringNodeToFront(Node nodeToFront) { if (nodes.remove(nodeToFront)) { nodes.add(nodeToFront); invalidate(); } }

    public void addNode(@NonNull PointF worldPosition,
                        float width, float height, // Pass desired world dimensions
                        @NonNull String label,
                        @Nullable @DrawableRes Integer iconResId,
                        @Nullable @DrawableRes Integer backgroundResId,
                        int inputCount, int outputCount)
    {
        updateMatrices(); // Ensure coordinate space is updated

        // Use default DP size if provided size is invalid
        float nodeWidth = (width > 0) ? width : DEFAULT_NODE_WIDTH_DP * density;
        float nodeHeight = (height > 0) ? height : DEFAULT_NODE_HEIGHT_DP * density;

        String newNodeId = "N_" + UUID.randomUUID().toString().substring(0, 4);
        NodeShape shape = (iconResId != null || backgroundResId != null) ? NodeShape.CUSTOM_DRAWABLE : NodeShape.RECTANGLE;

        Node newNode = new Node( newNodeId, worldPosition, nodeWidth, nodeHeight, // Use calculated/provided size
                shape, label, iconResId, backgroundResId,
                inputCount, outputCount ); // Pass handle counts to constructor

        nodes.add(newNode);
        updateMaps();
        Log.d(TAG, "Added new node: " + newNodeId);
        invalidate();
    }


    // Overload to add node at view center with default 50x50dp size
    public void addNodeAtCenter(@NonNull String label,
                                @Nullable @DrawableRes Integer iconResId,
                                @Nullable @DrawableRes Integer backgroundResId,
                                int inputCount, int outputCount)
    {
        updateMatrices();
        PointF centerScreen = new PointF(getWidth() / 2f, getHeight() / 2f);
        PointF centerWorld = screenToWorld(centerScreen.x, centerScreen.y);
        float nodeW = DEFAULT_NODE_WIDTH_DP * density;
        float nodeH = DEFAULT_NODE_HEIGHT_DP * density;
        addNode(centerWorld, nodeW, nodeH, label, iconResId, backgroundResId, inputCount, outputCount);
    }
    // Overload to add node using the FAB style (similar to previous)

    // Overload similar to previous FAB method (uses default 50x50dp size)
    public void addNewCustomNodeWithOutputs(@DrawableRes int drawableResId, @NonNull String label, int outputCount) {
        // Uses the center method with default size and specified background/handles
        addNodeAtCenter(label, drawableResId, R.drawable.node_background_shape, 1, outputCount);
    }
    public void addNewCustomNode(@DrawableRes int drawableResId, @NonNull String label) {
        addNewCustomNodeWithOutputs(drawableResId, label, 1); // Default to 1 output
    }
    // === END Public Add Node Methods ===



    // --- Public Data Accessors ---
    public List<Node> getNodes() { return new ArrayList<>(nodes); }
    public List<Edge> getEdges() { return new ArrayList<>(edges); }

    // --- Public Listener Setter ---
    public void setConnectionListener(ConnectionListener listener) { this.connectionListener = listener; }

    // --- Public Configuration Methods ---
    public void setGridDotColor(@ColorInt int color) { gridDotPaint.setColor(color); invalidate(); }
    public void setGridSpacing(float spacing) { this.gridSpacing = Math.max(10f, spacing); invalidate(); }
    public void setGridDotBaseRadius(float radius) { this.gridDotBaseRadius = Math.max(0.5f, radius); invalidate(); }
    public void setDefaultNodeBgColor(@ColorInt int color) { nodeBgPaint.setColor(color); invalidate(); }
    public void setDefaultNodeBorderColor(@ColorInt int color) { nodeBorderPaint.setColor(color); invalidate(); }
    public void setDefaultNodeTextColor(@ColorInt int color) { textPaint.setColor(color); invalidate(); }
    public void setDefaultNodeTextSize(float sizePixels) { textPaint.setTextSize(sizePixels); invalidate(); }
    public void setDefaultNodeCornerRadiusDp(float radiusDp) { this.defaultNodeCornerRadiusDp = Math.max(0, radiusDp); invalidate(); }
    public void setEdgeColor(@ColorInt int color) { edgePaint.setColor(color); setArrowheadColor(color); invalidate(); }
    public void setEdgeStrokeWidth(float width) { this.defaultEdgeStrokeWidth = Math.max(1f, width); invalidate(); }
    public void setDrawArrowheads(boolean draw) { this.drawArrowheads = draw; invalidate(); }
    public void setArrowheadColor(@ColorInt int color) { arrowHeadPaint.setColor(color); invalidate(); }
    public void setArrowheadSize(float size) { this.defaultArrowheadSize = Math.max(3f, size); arrowHeadPath.reset(); arrowHeadPath.moveTo(-defaultArrowheadSize, defaultArrowheadSize / 2); arrowHeadPath.lineTo(0, 0); arrowHeadPath.lineTo(-defaultArrowheadSize, -defaultArrowheadSize / 2); arrowHeadPath.close(); invalidate(); }
    public void setTempConnectionColor(@ColorInt int color) { tempConnectionPaint.setColor(color); invalidate(); }
    public void setHandleInputColor(@ColorInt int color) { handlePaintInput.setColor(color); invalidate(); }
    public void setHandleOutputColor(@ColorInt int color) { handlePaintOutput.setColor(color); invalidate(); }
    public void setHandleBorderColor(@ColorInt int color) { handleBorderPaint.setColor(color); invalidate(); }
    public void setHandleHitRadiusWorld(float radius) { this.handleHitRadiusWorld = Math.max(5f, radius); }





} // End of ReactFlowCanvasView class