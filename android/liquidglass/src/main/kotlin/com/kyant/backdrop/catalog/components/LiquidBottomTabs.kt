// PiliPlus adaptation: AM-plus-plus full-bar gestures and 56dp capsule thickness.
package com.kyant.backdrop.catalog.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int, gesture: String) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isLightTheme: Boolean = !isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()
    val animationScope = rememberCoroutineScope()
    val highlightAnchor = remember { PillCentre() }
    val freeDragBridge = remember { FreeDragBridge() }
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ -> Offset(highlightAnchor.x(), size.height / 2f) }
        )
    }
    val completed = rememberUpdatedState<(Int, Boolean, String) -> Unit> { index, reselect, gesture ->
        if (index != selectedTabIndex() || reselect) onTabSelected(index, gesture)
    }

    BoxWithConstraints(
        modifier.then(freeDragBridge.modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val liveTabWidth = rememberUpdatedState(tabWidth)
        val liveIsLtr = rememberUpdatedState(isLtr)
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { interactiveHighlight.pressAt(Offset.Zero) },
                onDragStopped = {
                    interactiveHighlight.releasePress()
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    val width = liveTabWidth.value
                    if (width > 0f) updateValue(
                        (targetValue + dragAmount.x / width * if (liveIsLtr.value) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
                onDragCancelled = {
                    interactiveHighlight.releasePress()
                    animateToValue(selectedTabIndex().toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        val panelInset = with(density) { 4f.dp.toPx() }
        highlightAnchor.pillValue = { dampedDragAnimation.value }
        highlightAnchor.panelWidth = constraints.maxWidth.toFloat()
        highlightAnchor.panelInset = panelInset
        highlightAnchor.tabWidth = tabWidth
        highlightAnchor.isLtr = isLtr
        freeDragBridge.animation = dampedDragAnimation
        freeDragBridge.tabWidth = tabWidth
        freeDragBridge.panelWidth = constraints.maxWidth.toFloat()
        freeDragBridge.panelInset = panelInset
        freeDragBridge.panelOffset = panelOffset
        freeDragBridge.tabsCount = tabsCount
        freeDragBridge.isLtr = isLtr
        freeDragBridge.onCanceled = { interactiveHighlight.releasePress() }
        freeDragBridge.onCompleted = { index, reselect, gesture -> completed.value(index, reselect, gesture) }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(56f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(4f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(48f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(48f.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/** AM-plus-plus: the interactive light stays at the thumb's center. */
private class PillCentre {
    var pillValue: () -> Float = { 0f }
    var panelWidth = 0f
    var panelInset = 0f
    var tabWidth = 0f
    var isLtr = true

    fun x(): Float {
        val fromStart = panelInset + (pillValue() + 0.5f) * tabWidth
        return if (isLtr) fromStart else panelWidth - fromStart
    }
}

/**
 * Ported from AM-plus-plus 075f5f5: all cells can grab the thumb; other pointers
 * wait until the owner lifts, then the newest held pointer takes over.
 * PiliPlus emits only one user selection at completion, without state-update echoes.
 */
private class FreeDragBridge {
    var animation: DampedDragAnimation? = null
    var tabWidth = 0f
    var panelWidth = 0f
    var panelInset = 0f
    var panelOffset = 0f
    var tabsCount = 0
    var isLtr = true
    var onCanceled: () -> Unit = {}
    var onCompleted: (Int, Boolean, String) -> Unit = { _, _, _ -> }

    val modifier = Modifier.pointerInput(this) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val targetIndex = tabIndexAt(down.position.x)
            val damped = animation
            val originalIndex = damped?.targetValue?.fastRoundToInt()?.fastCoerceIn(0, tabsCount - 1)
            if (damped != null && targetIndex != null && originalIndex != null) {
                val grabsInPlace = targetIndex == originalIndex
                down.consume()
                damped.onDragStarted.invoke(damped, down.position)
                damped.press()
                if (!grabsInPlace) damped.updateValue(valueAt(down.position.x))

                var ownerId = down.id
                var ownerPosition = down.position
                var traveled = 0f
                var tapEligible = grabsInPlace
                var handedOver = false
                val held = mutableListOf<Pair<PointerId, Offset>>()
                var canceled = false
                var finished = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            if (change.id == ownerId || (!change.pressed && !change.previousPressed)) continue
                            change.consume()
                            when {
                                !change.previousPressed -> {
                                    if (tabIndexAt(change.position.x) != null) {
                                        held.removeAll { it.first == change.id }
                                        held.add(change.id to change.position)
                                    }
                                }
                                change.pressed -> {
                                    val index = held.indexOfFirst { it.first == change.id }
                                    if (index >= 0) held[index] = change.id to change.position
                                }
                                else -> held.removeAll { it.first == change.id }
                            }
                        }
                        val change = event.changes.fastFirstOrNull { it.id == ownerId }
                        if (change == null) {
                            canceled = true
                            break
                        }
                        change.consume()
                        if (change.changedToUpIgnoreConsumed()) {
                            val next = held.lastOrNull() ?: break
                            held.removeAt(held.lastIndex)
                            ownerId = next.first
                            ownerPosition = next.second
                            tapEligible = false
                            handedOver = true
                            damped.updateValue(valueAt(ownerPosition.x))
                            continue
                        }
                        val dragAmount = change.position - ownerPosition
                        if (dragAmount != Offset.Zero) {
                            traveled += dragAmount.getDistance()
                            damped.onDrag.invoke(damped, IntSize.Zero, dragAmount)
                        }
                        ownerPosition = change.position
                    }
                    if (!canceled) {
                        val index = damped.targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        damped.onDragStopped.invoke(damped)
                        val isTap = !handedOver && traveled <= viewConfiguration.touchSlop
                        onCompleted(index, tapEligible && isTap, if (isTap) "tap" else "drag")
                        finished = true
                    }
                } finally {
                    if (!finished) damped.onDragCancelled.invoke(damped)
                    onCanceled()
                    damped.release()
                }
            }
        }
    }

    private fun tabIndexAt(x: Float): Int? {
        if (tabWidth <= 0f) return null
        val contentX = logicalX(x)
        if (contentX < 0f || contentX >= tabWidth * tabsCount) return null
        return (contentX / tabWidth).toInt().fastCoerceIn(0, tabsCount - 1)
    }

    private fun valueAt(x: Float) = ((logicalX(x) / tabWidth) - 0.5f).fastCoerceIn(0f, (tabsCount - 1).toFloat())

    private fun logicalX(x: Float) =
        if (isLtr) x - panelOffset - panelInset
        else panelWidth - x + panelOffset - panelInset
}
