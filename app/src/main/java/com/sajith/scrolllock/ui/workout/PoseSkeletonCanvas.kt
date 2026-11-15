package com.sajith.scrolllock.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sajith.scrolllock.workout.LandmarkId
import com.sajith.scrolllock.workout.LandmarkPoint

private val SkeletonConnections = listOf(
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

/**
 * Pure Jetpack Compose Canvas overlay rendering:
 * - Corner framing reticle guides
 * - Optional Ghost Trainer ideal posture skeleton (dashed translucent cyan)
 * - Live smoothed user skeleton aligned to CameraX PreviewView FILL_CENTER scaling
 * - Highlighted posture-error joints when anti-cheat or form deviations occur
 */
@Composable
fun PoseSkeletonCanvas(
    landmarks: Map<Int, LandmarkPoint>,
    errorJoints: Set<Int>,
    ghostLandmarks: Map<Int, LandmarkPoint>,
    showGhostTrainer: Boolean,
    analysisWidth: Int,
    analysisHeight: Int,
    isBodyAligned: Boolean,
    modifier: Modifier = Modifier,
    mirrorHorizontal: Boolean = false
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        drawFramingBrackets(
            color = if (isBodyAligned) {
                WorkoutColors.TurquoiseAccent.copy(alpha = 0.55f)
            } else {
                Color.White.copy(alpha = 0.28f)
            }
        )

        if (showGhostTrainer && ghostLandmarks.isNotEmpty()) {
            drawPoseSkeleton(
                landmarks = ghostLandmarks,
                errorJoints = emptySet(),
                analysisWidth = 0,
                analysisHeight = 0,
                mirrorHorizontal = mirrorHorizontal,
                isGhost = true
            )
        }

        if (landmarks.isNotEmpty()) {
            drawPoseSkeleton(
                landmarks = landmarks,
                errorJoints = errorJoints,
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight,
                mirrorHorizontal = mirrorHorizontal,
                isGhost = false
            )
        }
    }
}

private fun DrawScope.drawPoseSkeleton(
    landmarks: Map<Int, LandmarkPoint>,
    errorJoints: Set<Int>,
    analysisWidth: Int,
    analysisHeight: Int,
    mirrorHorizontal: Boolean,
    isGhost: Boolean
) {
    val dashEffect = if (isGhost) {
        PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 10.dp.toPx()), 0f)
    } else {
        null
    }

    val primaryStrokePx = if (isGhost) 2.2.dp.toPx() else 3.2.dp.toPx()
    val glowStrokePx = 7.dp.toPx()

    // Draw bones connecting body joints
    for ((startId, endId) in SkeletonConnections) {
        val start = landmarks[startId] ?: continue
        val end = landmarks[endId] ?: continue
        if (start.inFrameLikelihood < 0.35f || end.inFrameLikelihood < 0.35f) continue

        val startOffset = projectNormalizedPoint(
            normX = start.x,
            normY = start.y,
            analysisWidth = analysisWidth,
            analysisHeight = analysisHeight,
            mirrorHorizontal = mirrorHorizontal
        )
        val endOffset = projectNormalizedPoint(
            normX = end.x,
            normY = end.y,
            analysisWidth = analysisWidth,
            analysisHeight = analysisHeight,
            mirrorHorizontal = mirrorHorizontal
        )

        val hasErrorSegment = !isGhost && (errorJoints.contains(startId) || errorJoints.contains(endId))

        if (!isGhost) {
            drawLine(
                color = if (hasErrorSegment) {
                    WorkoutColors.ErrorCoral.copy(alpha = 0.25f)
                } else {
                    WorkoutColors.TurquoiseAccent.copy(alpha = 0.24f)
                },
                start = startOffset,
                end = endOffset,
                strokeWidth = glowStrokePx,
                cap = StrokeCap.Round
            )
        }

        drawLine(
            color = when {
                isGhost -> WorkoutColors.GhostCyan
                hasErrorSegment -> WorkoutColors.ErrorCoral
                else -> WorkoutColors.TurquoiseAccent
            },
            start = startOffset,
            end = endOffset,
            strokeWidth = primaryStrokePx,
            cap = StrokeCap.Round,
            pathEffect = dashEffect
        )
    }

    // Subtle head ring anchored on NOSE landmark for natural alignment cue
    val nose = landmarks[LandmarkId.NOSE]
    if (nose != null && nose.inFrameLikelihood >= 0.35f) {
        val headCenter = projectNormalizedPoint(
            normX = nose.x,
            normY = nose.y,
            analysisWidth = analysisWidth,
            analysisHeight = analysisHeight,
            mirrorHorizontal = mirrorHorizontal
        )
        drawCircle(
            color = if (isGhost) WorkoutColors.GhostCyan else WorkoutColors.TurquoiseAccent.copy(alpha = 0.8f),
            radius = 16.dp.toPx(),
            center = headCenter,
            style = Stroke(width = 2.dp.toPx(), pathEffect = dashEffect)
        )
    }

    // Draw individual keypoint nodes
    for ((id, point) in landmarks) {
        if (point.inFrameLikelihood < 0.35f) continue
        val center = projectNormalizedPoint(
            normX = point.x,
            normY = point.y,
            analysisWidth = analysisWidth,
            analysisHeight = analysisHeight,
            mirrorHorizontal = mirrorHorizontal
        )

        if (isGhost) {
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 3.dp.toPx(),
                center = center
            )
        } else if (errorJoints.contains(id)) {
            drawCircle(
                color = WorkoutColors.ErrorCoralSoft,
                radius = 11.dp.toPx(),
                center = center
            )
            drawCircle(
                color = WorkoutColors.ErrorCoral,
                radius = 5.5.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )
        } else {
            drawCircle(
                color = WorkoutColors.TurquoiseAccent.copy(alpha = 0.35f),
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 3.2.dp.toPx(),
                center = center
            )
        }
    }
}

private fun DrawScope.drawFramingBrackets(color: Color) {
    val margin = 20.dp.toPx()
    val arm = 22.dp.toPx()
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    val left = margin
    val top = size.height * 0.21f
    val right = size.width - margin
    val bottom = size.height * 0.67f
    if (bottom <= top + arm * 2) return

    // Top-left
    drawLine(color, Offset(left, top + arm), Offset(left, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(left, top), Offset(left + arm, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Top-right
    drawLine(color, Offset(right - arm, top), Offset(right, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(right, top), Offset(right, top + arm), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Bottom-left
    drawLine(color, Offset(left, bottom - arm), Offset(left, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(left, bottom), Offset(left + arm, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Bottom-right
    drawLine(color, Offset(right - arm, bottom), Offset(right, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - arm), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

/**
 * Projects normalized [0..1] ML Kit landmark coordinates onto Canvas size,
 * replicating CameraX PreviewView.ScaleType.FILL_CENTER so the skeleton matches the preview.
 */
private fun DrawScope.projectNormalizedPoint(
    normX: Float,
    normY: Float,
    analysisWidth: Int,
    analysisHeight: Int,
    mirrorHorizontal: Boolean
): Offset {
    val adjustedX = if (mirrorHorizontal) 1f - normX else normX
    val viewW = size.width
    val viewH = size.height
    if (analysisWidth <= 0 || analysisHeight <= 0) {
        return Offset(adjustedX * viewW, normY * viewH)
    }
    val scale = maxOf(viewW / analysisWidth.toFloat(), viewH / analysisHeight.toFloat())
    val scaledW = analysisWidth * scale
    val scaledH = analysisHeight * scale
    val offsetX = (viewW - scaledW) * 0.5f
    val offsetY = (viewH - scaledH) * 0.5f
    return Offset(
        x = adjustedX * scaledW + offsetX,
        y = normY * scaledH + offsetY
    )
}
