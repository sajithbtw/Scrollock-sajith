package com.sajith.scrolllock.ui.workout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.sajith.scrolllock.workout.LandmarkId
import com.sajith.scrolllock.workout.LandmarkPoint

/**
 * Custom Canvas view rendering:
 * - Live smoothed user skeleton with turquoise valid lines and coral/red highlighted posture-error joints
 * - Optional Ghost Trainer ideal reference skeleton in translucent cyan dashed lines
 * - Coordinate transformation matching CameraX PreviewView FILL_CENTER scaling
 */
class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var userLandmarks: Map<Int, LandmarkPoint> = emptyMap()
    private var ghostLandmarks: Map<Int, LandmarkPoint> = emptyMap()
    private var errorJoints: Set<Int> = emptySet()
    private var showGhost: Boolean = true
    private var mirrorHorizontal: Boolean = false

    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#54D6C2")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 7f
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val errorJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5A5F")
        style = Paint.Style.FILL
    }

    private val errorRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FF5A5F")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val ghostBonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88A8F5EA")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }

    private val ghostJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        style = Paint.Style.FILL
    }

    private val connections = listOf(
        LandmarkId.LEFT_SHOULDER to LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_SHOULDER to LandmarkId.LEFT_ELBOW,
        LandmarkId.LEFT_ELBOW to LandmarkId.LEFT_WRIST,
        LandmarkId.RIGHT_SHOULDER to LandmarkId.RIGHT_ELBOW,
        LandmarkId.RIGHT_ELBOW to LandmarkId.RIGHT_WRIST,
        LandmarkId.LEFT_SHOULDER to LandmarkId.LEFT_HIP,
        LandmarkId.RIGHT_SHOULDER to LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_HIP to LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_HIP to LandmarkId.LEFT_KNEE,
        LandmarkId.LEFT_KNEE to LandmarkId.LEFT_ANKLE,
        LandmarkId.RIGHT_HIP to LandmarkId.RIGHT_KNEE,
        LandmarkId.RIGHT_KNEE to LandmarkId.RIGHT_ANKLE
    )

    fun updatePose(
        landmarks: Map<Int, LandmarkPoint>,
        errors: Set<Int>,
        ghost: Map<Int, LandmarkPoint>,
        showGhostTrainer: Boolean,
        mirrorMode: Boolean,
        analysisWidth: Int = 0,
        analysisHeight: Int = 0
    ) {
        this.userLandmarks = landmarks
        this.errorJoints = errors
        this.ghostLandmarks = ghost
        this.showGhost = showGhostTrainer
        this.mirrorHorizontal = mirrorMode
        this.frameWidth = analysisWidth
        this.frameHeight = analysisHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        // Draw Ghost Trainer reference skeleton first (in normalized view coordinates)
        if (showGhost && ghostLandmarks.isNotEmpty()) {
            drawSkeleton(
                canvas = canvas,
                landmarks = ghostLandmarks,
                bPaint = ghostBonePaint,
                jPaint = ghostJointPaint,
                errors = emptySet(),
                useFillCenter = false
            )
        }

        // Draw user's live skeleton
        if (userLandmarks.isNotEmpty()) {
            drawSkeleton(
                canvas = canvas,
                landmarks = userLandmarks,
                bPaint = bonePaint,
                jPaint = jointPaint,
                errors = errorJoints,
                useFillCenter = (frameWidth > 0 && frameHeight > 0)
            )
        }
    }

    private fun drawSkeleton(
        canvas: Canvas,
        landmarks: Map<Int, LandmarkPoint>,
        bPaint: Paint,
        jPaint: Paint,
        errors: Set<Int>,
        useFillCenter: Boolean
    ) {
        for ((startId, endId) in connections) {
            val a = landmarks[startId] ?: continue
            val b = landmarks[endId] ?: continue
            if (a.inFrameLikelihood < 0.35f || b.inFrameLikelihood < 0.35f) continue

            val (ax, ay) = mapPoint(a.x, a.y, useFillCenter)
            val (bx, by) = mapPoint(b.x, b.y, useFillCenter)
            canvas.drawLine(ax, ay, bx, by, bPaint)
        }

        for ((id, pt) in landmarks) {
            if (pt.inFrameLikelihood < 0.35f) continue
            val (px, py) = mapPoint(pt.x, pt.y, useFillCenter)
            if (errors.contains(id)) {
                canvas.drawCircle(px, py, 16f, errorRingPaint)
                canvas.drawCircle(px, py, 10f, errorJointPaint)
            } else {
                canvas.drawCircle(px, py, 7f, jPaint)
            }
        }
    }

    private fun mapPoint(normX: Float, normY: Float, useFillCenter: Boolean): Pair<Float, Float> {
        val adjustedX = if (mirrorHorizontal) 1f - normX else normX
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (!useFillCenter || frameWidth <= 0 || frameHeight <= 0) {
            return (adjustedX * viewW) to (normY * viewH)
        }
        // Match PreviewView.ScaleType.FILL_CENTER aspect scaling
        val scale = maxOf(viewW / frameWidth.toFloat(), viewH / frameHeight.toFloat())
        val scaledW = frameWidth * scale
        val scaledH = frameHeight * scale
        val offsetX = (viewW - scaledW) * 0.5f
        val offsetY = (viewH - scaledH) * 0.5f
        return (adjustedX * scaledW + offsetX) to (normY * scaledH + offsetY)
    }
}
