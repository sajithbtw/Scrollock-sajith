package com.sajith.scrolllock.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

val AppPlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val AppInterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val AppJetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

object GlassColors {
    val VoidBg = Color(0xFF080914)
    val DeepIndigoBg = Color(0xFF0E1024)
    val GlassSurface = Color(0xD9121429)
    val GlassCard = Color(0xC2171933)
    val GlassCardTop = Color(0xE021244A)
    val GlassCardBottom = Color(0xB8111327)
    val GlassBorder = Color(0xFF333663)
    val ActiveGlowBorder = Color(0xFF8C77FF)
    val VioletPrimary = Color(0xFF7C5CFF)
    val VioletBright = Color(0xFFA78BFF)
    val VioletDeep = Color(0xFF533CE0)
    val VioletSoft = Color(0x307C5CFF)
    val TextPrimary = Color(0xFFF6F7FC)
    val TextSecondary = Color(0xFFA3A7CE)
    val TextMuted = Color(0xFF70759E)
    val MintVerified = Color(0xFF4ADE80)
    val CyanAccent = Color(0xFF54D6C2)
    val AmberAlarm = Color(0xFFF59E0B)
    val AmberSoft = Color(0x2EF59E0B)
    val CoralError = Color(0xFFFF5E73)
    val CoralSoft = Color(0x2EFF5E73)
}

private val ScrollLockGlassColorScheme = darkColorScheme(
    primary = GlassColors.VioletPrimary,
    onPrimary = GlassColors.TextPrimary,
    primaryContainer = GlassColors.VioletSoft,
    onPrimaryContainer = GlassColors.VioletBright,
    secondary = GlassColors.CyanAccent,
    onSecondary = GlassColors.VoidBg,
    background = GlassColors.VoidBg,
    onBackground = GlassColors.TextPrimary,
    surface = GlassColors.GlassCard,
    onSurface = GlassColors.TextPrimary,
    surfaceVariant = GlassColors.GlassSurface,
    onSurfaceVariant = GlassColors.TextSecondary,
    outline = GlassColors.GlassBorder,
    error = GlassColors.CoralError
)

private val ScrollLockGlassTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = AppPlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = AppPlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppPlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppInterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AppPlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppInterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppInterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun ScrollLockGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScrollLockGlassColorScheme,
        typography = ScrollLockGlassTypography,
        content = content
    )
}

@Composable
fun AmbientNebulaBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.VoidBg)
            .drawBehind {
                // Soft upper-center indigo-violet aurora glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x526A4DFF),
                            Color(0x224933B8),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.12f),
                        radius = size.maxDimension * 0.65f
                    ),
                    radius = size.maxDimension * 0.65f,
                    center = Offset(size.width * 0.52f, size.height * 0.12f)
                )
                // Lower-right ambient violet depth orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x3D7C5CFF),
                            Color(0x143E2CA8),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.78f),
                        radius = size.maxDimension * 0.52f
                    ),
                    radius = size.maxDimension * 0.52f,
                    center = Offset(size.width * 0.88f, size.height * 0.78f)
                )
                // Left edge cool cosmic sheen
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x2E5440CC),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.08f, size.height * 0.42f),
                        radius = size.maxDimension * 0.42f
                    ),
                    radius = size.maxDimension * 0.42f,
                    center = Offset(size.width * 0.08f, size.height * 0.42f)
                )
            },
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    cornerRadius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (highlighted) {
        GlassColors.ActiveGlowBorder.copy(alpha = 0.72f)
    } else {
        GlassColors.GlassBorder.copy(alpha = 0.65f)
    }
    val gradient = Brush.verticalGradient(
        colors = if (highlighted) {
            listOf(Color(0xCC2A295E), Color(0xBF171836))
        } else {
            listOf(GlassColors.GlassCardTop.copy(alpha = 0.72f), GlassColors.GlassCardBottom)
        }
    )

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .background(gradient)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun GlowingNavPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val shape = RoundedCornerShape(18.dp)
    val bgBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(Color(0xCC6D54FF), Color(0x884A39B8), Color(0x44292556))
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val border = if (selected) {
        BorderStroke(1.dp, GlassColors.ActiveGlowBorder.copy(alpha = 0.8f))
    } else {
        null
    }

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = border,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(bgBrush)
            .clickable(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) GlassColors.TextPrimary else GlassColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) GlassColors.TextPrimary else GlassColors.TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ActivitySparklineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = GlassColors.VioletBright
) {
    val points = if (values.size >= 2) values else listOf(0.2f, 0.45f, 0.35f, 0.72f, 0.58f, 0.9f, 0.76f)
    Canvas(modifier = modifier) {
        val maxVal = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val padY = 6.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(1f)
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val coords = points.mapIndexed { idx, value ->
            val ratio = (value / maxVal).coerceIn(0.05f, 1f)
            Offset(
                x = idx * stepX,
                y = padY + usableH * (1f - ratio)
            )
        }

        val strokePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.lastIndex) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(coords.last().x, h)
            lineTo(coords.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.40f), Color.Transparent)
            )
        )
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
