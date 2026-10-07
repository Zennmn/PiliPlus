package com.example.piliplus

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.kyant.backdrop.backdrops.AndroidViewBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import io.flutter.FlutterInjector
import io.flutter.embedding.android.FlutterView
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class LiquidGlassViewFactory(
    private val messenger: BinaryMessenger,
    private val activity: MainActivity
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView =
        LiquidGlassPlatformView(context, viewId, messenger, activity, args as Map<*, *>)
}

private data class GlassTab(
    val id: String, val label: String, val icon: Int, val selectedIcon: Int,
    val font: String, val badge: String?
)

private data class GlassState(
    val tabs: List<GlassTab>, val index: Int, val light: Boolean,
    val scale: Float, val fontScale: Float, val active: Boolean,
    val badgeColor: Int, val badgeTextColor: Int
) {
    companion object {
        fun from(map: Map<*, *>) = GlassState(
            tabs = (map["tabs"] as List<*>).map {
                val tab = it as Map<*, *>
                GlassTab(tab["id"] as String, tab["label"] as String,
                    (tab["icon"] as Number).toInt(), (tab["selectedIcon"] as Number).toInt(),
                    tab["font"] as String, tab["badge"] as String?)
            },
            index = (map["selectedIndex"] as Number).toInt(),
            light = map["light"] as Boolean,
            scale = (map["uiScale"] as Number).toFloat(),
            fontScale = (map["textScale"] as Number).toFloat(),
            active = map["active"] as Boolean,
            badgeColor = (map["badgeColor"] as Number).toInt(),
            badgeTextColor = (map["badgeTextColor"] as Number).toInt()
        )
    }
}

/** Each platform view owns its composition and saved-state lifecycle; the audio host stays intact. */
private class GlassViewOwner(private val host: LifecycleOwner) :
    LifecycleOwner, SavedStateRegistryOwner, LifecycleEventObserver {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    init {
        saved.performAttach()
        saved.performRestore(null)
        host.lifecycle.addObserver(this)
    }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        registry.handleLifecycleEvent(event)
    }
    fun dispose() {
        host.lifecycle.removeObserver(this)
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

private class LiquidGlassPlatformView(
    context: Context, viewId: Int, messenger: BinaryMessenger,
    private val activity: MainActivity, initial: Map<*, *>
) : PlatformView {
    private var state by mutableStateOf(GlassState.from(initial))
    private val channel = MethodChannel(messenger, "piliplus/liquid_glass/$viewId")
    private val owner = GlassViewOwner(activity)
    private val view = ComposeView(context)
    private val recomposer = view.createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle)
    private val backdrop = AndroidViewBackdrop { flutterView()?.currentImageSurface }
    private var previousSource: View? = null
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        val source = flutterView()?.currentImageSurface
        if (state.active && (source !== previousSource || source?.isDirty == true)) {
            backdrop.invalidate()
        }
        previousSource = source
        true
    }

    init {
        view.clipChildren = false
        view.clipToPadding = false
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        // FlutterView has no view-tree owner. Supply this view's recomposer so
        // Compose does not look for a window recomposer on the Flutter root.
        view.setParentCompositionContext(recomposer)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { v.viewTreeObserver.addOnPreDrawListener(preDraw) }
            override fun onViewDetachedFromWindow(v: View) { v.viewTreeObserver.removeOnPreDrawListener(preDraw) }
        })
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "update" -> {
                    state = GlassState.from(call.arguments as Map<*, *>)
                    backdrop.invalidate()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
        view.setContent {
            val current = state
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(
                systemDensity.density * current.scale, current.fontScale
            )) {
                val fonts = remember { loadFonts(context) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    key(current.tabs.map { it.id }) {
                        LiquidBottomTabs(
                            selectedTabIndex = { state.index },
                            onTabSelected = { index, gesture -> select(index, gesture) },
                            backdrop = backdrop,
                            tabsCount = current.tabs.size,
                            isLightTheme = current.light,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        ) {
                            current.tabs.forEachIndexed { index, tab ->
                                LiquidBottomTab(
                                    onClick = { select(index, "tap") },
                                    modifier = Modifier.semantics {
                                        selected = index == current.index
                                        contentDescription = tab.label
                                    }
                                ) {
                                    val contentColor = if (current.light) Color.Black else Color.White
                                    BasicText(
                                        String(Character.toChars(if (index == current.index) tab.selectedIcon else tab.icon)),
                                        style = TextStyle(color = contentColor, fontSize = 24.sp,
                                            fontFamily = fonts.getValue(tab.font))
                                    )
                                    BasicText(tab.label, style = TextStyle(contentColor, 11.sp), maxLines = 1)
                                }
                            }
                        }
                    }
                    // Badges stay outside the upstream color-filtered glass content layer.
                    Row(Modifier.padding(horizontal = 44.dp).fillMaxWidth().height(56.dp)) {
                        current.tabs.forEach { tab ->
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                                tab.badge?.let { badge -> GlassBadge(badge, current.badgeColor, current.badgeTextColor) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun flutterView(): FlutterView? = activity.findViewById(FlutterActivityId)

    private fun select(index: Int, gesture: String) {
        if (state.active && index in state.tabs.indices) {
            channel.invokeMethod("select", mapOf("index" to index, "gesture" to gesture))
        }
    }

    override fun getView(): View = view

    override fun dispose() {
        channel.setMethodCallHandler(null)
        view.viewTreeObserver.removeOnPreDrawListener(preDraw)
        view.disposeComposition()
        recomposer.cancel()
        owner.dispose()
        previousSource = null
    }

    companion object {
        private val FlutterActivityId = io.flutter.embedding.android.FlutterActivity.FLUTTER_VIEW_ID
        private fun loadFonts(context: Context): Map<String, FontFamily> {
            val loader = FlutterInjector.instance().flutterLoader()
            return mapOf(
                "MaterialIcons" to FontFamily(Typeface.createFromAsset(context.assets,
                    loader.getLookupKeyForAsset("fonts/MaterialIcons-Regular.otf"))),
                "custom_icon" to FontFamily(Typeface.createFromAsset(context.assets,
                    loader.getLookupKeyForAsset("assets/fonts/custom_icon.ttf")))
            )
        }
    }
}

@Composable
private fun GlassBadge(value: String, backgroundColor: Int, textColor: Int) {
    val density = LocalDensity.current
    androidx.compose.foundation.Canvas(Modifier.offset(x = 15.dp, y = 5.dp).size(32.dp, 16.dp)) {
        val canvas = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = backgroundColor
        if (value.isEmpty()) {
            canvas.drawCircle(4.dp.toPx(), 4.dp.toPx(), 3.dp.toPx(), paint)
        } else {
            paint.textSize = with(density) { 10.sp.toPx() }
            val badgeWidth = maxOf(16.dp.toPx(), paint.measureText(value) + 10.dp.toPx())
            canvas.drawRoundRect(0f, 0f, badgeWidth, size.height, size.height / 2, size.height / 2, paint)
            paint.color = textColor
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText(value, badgeWidth / 2, (size.height - paint.ascent() - paint.descent()) / 2, paint)
        }
    }
}
