package com.kyant.backdrop.backdrops

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.internal.InverseLayerScope

/** Draws only Flutter's HC background surface into Backdrop's hardware effect layer. */
class AndroidViewBackdrop(private val source: () -> View?) : Backdrop {
    override val isCoordinatesDependent = true
    private var revision by mutableIntStateOf(0)
    private val sourceLocation = IntArray(2)
    private val inverseLayerScope = InverseLayerScope()

    fun invalidate() { revision++ }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        revision // Observed by Compose's draw cache; no screenshot or pixel transfer.
        val view = source() ?: return
        val location = coordinates?.takeIf { it.isAttached }?.positionInWindow() ?: return
        view.getLocationInWindow(sourceLocation)
        withTransform({
            if (layerBlock != null) {
                inverseLayerScope.reset()
                with(inverseLayerScope) { inverseTransform(density, layerBlock) }
            }
            translate(sourceLocation[0] - location.x, sourceLocation[1] - location.y)
        }) {
            view.draw(drawContext.canvas.nativeCanvas)
        }
    }
}
