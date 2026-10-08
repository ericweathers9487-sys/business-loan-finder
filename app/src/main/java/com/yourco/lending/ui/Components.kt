package com.yourco.lending.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourco.lending.matching.EligibilityEngine

/** Standard page: a round Back button and optional progress, then scrolling content. */
@Composable
fun ScreenFrame(
    onBack: (() -> Unit)?,
    progress: Float? = null,
    stepLabel: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val hasTopBar = onBack != null || progress != null
    Column(Modifier.fillMaxSize()) {
        if (hasTopBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                if (progress != null) GradientProgress(progress, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (stepLabel != null) {
                    Text(stepLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (hasTopBar) 4.dp else 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c.surface,
        border = BorderStroke(1.dp, c.outlineVariant),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun GradientProgress(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(p, tween(450, easing = FastOutSlowInEasing), label = "progress")
    Box(
        modifier
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(CircleShape)
                .background(Brand.action)
        )
    }
}

/** The main call to action: full width, brand gradient, arrow. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = Icons.AutoMirrored.Filled.ArrowForward,
) {
    val c = MaterialTheme.colorScheme
    val fg = if (enabled) Color.White else c.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = Brand.Emerald, spotColor = Brand.Ocean)
                else Modifier
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(if (enabled) Brand.action else SolidColor(c.surfaceContainerHighest))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
                Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = fg)
                if (icon != null && !loading) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A tappable answer. Big enough to hit with a thumb; the selected one glows with the brand gradient. */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (compact) 16.dp else 18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = shape,
        color = if (selected) c.primary.copy(alpha = 0.08f).compositeOver(c.surface) else c.surface,
        border = if (selected) BorderStroke(2.dp, Brand.action) else BorderStroke(1.dp, c.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 13.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                IconBadge(
                    icon,
                    tint = if (selected) Color.White else c.primary,
                    background = if (selected) Brand.action else SolidColor(c.primaryContainer),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }
            SelectionDot(selected, small = compact)
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean, small: Boolean) {
    val c = MaterialTheme.colorScheme
    val pop by animateFloatAsState(if (selected) 1f else 0.6f, tween(220), label = "dot")
    val size = if (small) 20.dp else 24.dp
    if (selected) {
        Box(
            Modifier
                .size(size)
                .scale(pop)
                .clip(CircleShape)
                .background(Brand.action),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.65f))
        }
    } else {
        Box(
            Modifier
                .size(size)
                .border(1.5.dp, c.outline, CircleShape)
        )
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Brush,
    size: Dp = 44.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** White (or dark-surface) card with a hairline border and soft shadow. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = if (muted) c.surfaceVariant else c.surface,
        border = BorderStroke(1.dp, c.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (muted) Modifier
                else Modifier.shadow(6.dp, shape, ambientColor = Brand.Ink.copy(alpha = 0.06f), spotColor = Brand.Ink.copy(alpha = 0.10f))
            ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Dark gradient panel with soft color glows. Text on it should be white. */
@Composable
fun HeroPanel(
    brush: Brush,
    modifier: Modifier = Modifier,
    glow: Color = Brand.Mint,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.95f, 0f),
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.95f, 0f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Brand.Aqua.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(0f, size.height),
                )
            }
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Translucent chip for use on hero panels. */
@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

enum class ReasonKind { GOOD, WATCH, BLOCKER }

/** One reason with a small round icon: check for strengths, info for concerns, × for blockers. */
@Composable
fun ReasonRow(kind: ReasonKind, text: String) {
    val c = MaterialTheme.colorScheme
    val (icon, tint, bg) = when (kind) {
        ReasonKind.GOOD -> Triple(Icons.Filled.Check, c.primary, c.primaryContainer)
        ReasonKind.WATCH -> Triple(Icons.Filled.Info, c.tertiary, c.tertiaryContainer)
        ReasonKind.BLOCKER -> Triple(Icons.Filled.Close, c.error, c.errorContainer)
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Fit score as an animated gradient ring with the number in the middle. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 68.dp, stroke: Dp = 7.dp) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(score) { sweep.animateTo(score.coerceIn(0, 100) / 100f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = "Fit score $score out of 100" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(w))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(Brand.Emerald, Brand.Mint, Brand.Aqua, Brand.Ocean)),
                    0f, 360f * sweep.value, useCenter = false, topLeft = topLeft, size = arc,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp))
            Text("FIT", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small label-over-value tile. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun FinePrint(text: String, icon: ImageVector? = null) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(15.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}

@Composable
fun PageTitle(text: String, supporting: String? = null, eyebrow: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(text, style = MaterialTheme.typography.headlineMedium)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(text: String, count: Int? = null) {
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        if (count != null) {
            Pill("$count", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** $25k, $1.5k, $2M: short enough for tiles and hero numbers. */
fun compactMoney(v: Long): String = when {
    v >= 1_000_000 && v % 100_000 == 0L ->
        if (v % 1_000_000 == 0L) "\$${v / 1_000_000}M" else "\$${v / 1_000_000}.${(v % 1_000_000) / 100_000}M"
    v >= 1_000 && v % 1_000 == 0L -> "\$${v / 1_000}k"
    v >= 1_000 && v % 100 == 0L -> "\$${v / 1_000}.${(v % 1_000) / 100}k"
    else -> EligibilityEngine.money(v)
}

fun compactRange(r: LongRange): String =
    if (r.first == r.last) compactMoney(r.first) else "${compactMoney(r.first)} – ${compactMoney(r.last)}"
