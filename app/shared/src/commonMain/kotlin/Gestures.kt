import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Hides the keyboard when the user taps anywhere that is not itself interactive.
 *
 * iOS has no system back key to dismiss the keyboard, so without this a focused field kept the
 * keyboard up until the screen changed — on the login screen it covered the Sign in button.
 * Text fields, buttons and list items consume their own taps, so this only fires on taps that
 * landed on empty space.
 */
fun Modifier.dismissKeyboardOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus()
            keyboard?.hide()
        })
    }
}

/**
 * iOS-style back navigation: a rightward drag that starts at the left screen edge pulls the
 * current screen aside and, past a threshold (or on a quick flick), slides it off and calls
 * [onBack]. [onBack] runs only once the screen is fully off, so the caller should pop without its
 * usual transition — otherwise the screen that was just swiped away fades back in to fade out.
 *
 * The gesture is watched in the Initial pass so it wins over scrolling lists and clickable rows
 * underneath; once it is recognised as a horizontal edge swipe its moves are consumed, which
 * cancels any press a child had started. Vertical drags, taps and touches away from the edge are
 * never consumed, so they reach the content untouched.
 */
@Composable
fun Modifier.edgeSwipeBack(enabled: Boolean, canGoBack: () -> Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // pointerInput(Unit) keeps one gesture loop for the modifier's lifetime, so the callbacks are
    // read through updated state rather than captured from the first composition.
    val currentCanGoBack by rememberUpdatedState(canGoBack)
    val currentOnBack by rememberUpdatedState(onBack)
    // pointerInput sits outside graphicsLayer so drag deltas are measured in untranslated
    // coordinates — otherwise the layer moving under the finger would feed back into the drag.
    return this
        .pointerInput(Unit) {
            val edgeWidth = 28.dp.toPx()
            val flickVelocity = 1000.dp.toPx()
            val slop = viewConfiguration.touchSlop
            // Set while a committed swipe slides off and pops; a second swipe in that window
            // would pop a second screen.
            var committing = false
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (committing || down.position.x > edgeWidth || !currentCanGoBack()) return@awaitEachGesture

                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                var dx = 0f
                var dy = 0f
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.positionChange()
                    dx += delta.x
                    dy += delta.y
                    velocity.addPosition(change.uptimeMillis, change.position)
                    if (!dragging) {
                        if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                        if (dx > slop && dx > abs(dy)) {
                            dragging = true
                            focusManager.clearFocus()
                        }
                    }
                    if (dragging) {
                        change.consume()
                        scope.launch { offset.snapTo(dx.coerceIn(0f, size.width.toFloat())) }
                    }
                }
                if (!dragging) return@awaitEachGesture

                // Like UIKit: a flick decides on its own, in either direction; otherwise how far
                // the screen was pulled decides.
                val vx = velocity.calculateVelocity().x
                val committed = vx > flickVelocity || (dx > size.width * 0.3f && vx > -flickVelocity)
                if (committed) committing = true
                scope.launch {
                    if (committed) {
                        try {
                            offset.animateTo(size.width.toFloat(), tween(durationMillis = 200))
                            currentOnBack()
                            offset.snapTo(0f)
                        } finally {
                            committing = false
                        }
                    } else {
                        offset.animateTo(0f)
                    }
                }
            }
        }
        .graphicsLayer { translationX = offset.value }
}
