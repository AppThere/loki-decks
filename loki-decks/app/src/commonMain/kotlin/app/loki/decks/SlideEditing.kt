package app.loki.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import app.loki.kit.EngineClient
import app.loki.kit.TouchModifiers
import app.loki.kit.isTouchPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One call to the engine, queued: [final] when it changes the deck (a drag in progress does not). */
private class Op(val method: String, val params: JsonObject, val final: Boolean)

private fun keyName(k: Key): String? = when (k) {
    Key.Delete -> "Delete"; Key.Backspace -> "Backspace"; Key.Enter, Key.NumPadEnter -> "Enter"; Key.Escape -> "Escape"; Key.Tab -> "Tab"
    Key.MoveHome -> "Home"; Key.MoveEnd -> "End"; Key.PageUp -> "PageUp"; Key.PageDown -> "PageDown"
    Key.DirectionLeft -> "ArrowLeft"; Key.DirectionRight -> "ArrowRight"; Key.DirectionUp -> "ArrowUp"; Key.DirectionDown -> "ArrowDown"
    else -> null
}

/**
 * What lies over a slide on the page column. On the slide in hand it forwards the pointer to the
 * engine's tool as `ui.pointer`, keys as `ui.key` and typing as `ui.text` (the engine selects, moves,
 * resizes, turns, draws and edits text: the same path as the desktop's mouse), and draws the
 * selection and its handles, which the renderer does not. A press within reach of a handle is sent
 * to the handle's exact spot, so a finger's wide reach picks what the engine's own 3-point reach
 * would miss. A press on any other slide makes it the slide in hand.
 */
@Composable
fun SlideOverlay(
    client: EngineClient, index: Int, current: Boolean, slideWidth: Float, revision: Int,
    onLive: () -> Unit, onChanged: () -> Unit, modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<List<SlideShape>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextGeometry.None) }
    var blink by remember { mutableStateOf(true) }
    val imeFocus = remember { FocusRequester() }
    val queue = remember { Channel<Op>(Channel.UNLIMITED) }
    val k = if (slideWidth > 0f) widthPx / slideWidth else 0f
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reach = with(density) { (if (isTouchPlatform) 22.dp else 9.dp).toPx() }
    val clicks = remember { ClickRun() }
    // The gesture reads these as they are when it needs them: a key that restarted it would cut a drag short when the press changes the selection.
    val selectedNow by rememberUpdatedState(selected)
    val editingNow by rememberUpdatedState(editing)

    // The engine's calls go one at a time, in order.
    LaunchedEffect(client) {
        while (true) {
            val batch = mutableListOf(queue.receive())
            while (true) batch += queue.tryReceive().getOrNull() ?: break
            // Every drag goes: the engine moves a shape by the step between one drag and the next.
            val ops = batch
            withContext(Dispatchers.Default) { for (op in ops) runCatching { client.call(op.method, op.params) } }
            if (ops.any { it.final }) onChanged() else onLive()
        }
    }
    // What is selected on this slide, again after every change.
    LaunchedEffect(client, index, current, revision) {
        if (!current) { selected = emptyList(); return@LaunchedEffect }
        val (sel, shapes) = withContext(Dispatchers.Default) {
            val doc = runCatching { client.call("document.inspect") }.getOrNull()
            val slide = runCatching { client.execute("slide.inspect", buildJsonObject { put("index", index) }) }.getOrNull()
            parseSlideSelection(doc) to parseSlideShapes(slide)
        }
        editing = sel.editingText
        selected = if (sel.slide == index) shapes.filter { it.id in sel.ids } else emptyList()
        // Where the caret and the highlight are, from the engine's own layout.
        text = if (sel.editingText) withContext(Dispatchers.Default) { parseTextGeometry(runCatching { client.execute("text.caret") }.getOrNull()) } else TextGeometry.None
    }

    // The caret blinks while text is being edited, and stands steady while it moves.
    LaunchedEffect(editing, text) {
        blink = true
        while (editing) { delay(530); blink = !blink }
    }
    // A touch screen has no key events for a soft keyboard: a field that is not there takes its text.
    LaunchedEffect(editing) { if (editing && isTouchPlatform) runCatching { imeFocus.requestFocus() } }

    fun send(method: String, params: JsonObject, final: Boolean) { queue.trySend(Op(method, params, final)) }

    Box(
        modifier.onSizeChanged { widthPx = it.width.toFloat() }.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
            if (!current || e.type != KeyEventType.KeyDown || e.isCtrlPressed || e.isMetaPressed) return@onPreviewKeyEvent false
            val name = keyName(e.key)
            when {
                name != null -> send("ui.key", buildJsonObject { put("key", name); put("shift", e.isShiftPressed); put("alt", e.isAltPressed) }, final = true)
                e.utf16CodePoint in 32..0xFFFF && e.utf16CodePoint != 127 && !e.isAltPressed -> send("ui.text", buildJsonObject { put("text", e.utf16CodePoint.toChar().toString()) }, final = true)
                else -> return@onPreviewKeyEvent false
            }
            true
        },
    ) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(current, index, k, reach) {
                if (k <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    if (!current) {
                        // A tap on another slide makes it the slide in hand; the drag that scrolls is left alone.
                        val up = waitForUpOrCancellation()
                        if (up != null && (up.position - down.position).getDistance() <= touchSlop) {
                            send("engine.execute", buildJsonObject { put("command", "slide.go"); put("params", buildJsonObject { put("index", index) }) }, final = true)
                        }
                        return@awaitEachGesture
                    }
                    focus.requestFocus()
                    val mods = currentEvent.keyboardModifiers
                    val shift = mods.isShiftPressed || TouchModifiers.shift
                    val alt = TouchModifiers.alt
                    val cmd = mods.isCtrlPressed || mods.isMetaPressed
                    // A second press close in time and place is a double click, a third a triple.
                    val run = clicks.press(down.uptimeMillis, down.position)
                    fun at(p: Offset) = Offset(p.x / k, p.y / k)
                    if (run >= 2) {
                        val p = at(down.position)
                        send("ui.pointer", pointerParams(if (run == 2) "doubleclick" else "tripleclick", p.x, p.y, shift, alt, cmd), final = true)
                        down.consume()
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    // A press near a handle goes to the handle's own spot; the drag carries the finger's offset from it.
                    val handles = if (selectedNow.size == 1 && !editingNow) selectedNow[0].handles() else emptyList()
                    val grabbed = handleAt(handles.map { it * k }, down.position, reach)
                    val grip = grabbed?.let { handles[it] * k - down.position } ?: Offset.Zero
                    fun sent(p: Offset) = at(p + grip)
                    val start = sent(down.position)
                    send("ui.pointer", pointerParams("down", start.x, start.y, shift, alt, cmd), final = true)
                    down.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val p = sent(ch.position)
                        val pen = if (ch.type == PointerType.Stylus) ch.pressure else null
                        if (!ch.pressed) {
                            send("ui.pointer", pointerParams("up", p.x, p.y, shift, alt, cmd), final = true)
                            ch.consume()
                            break
                        }
                        send("ui.pointer", pointerParams("drag", p.x, p.y, shift, alt, cmd, pen), final = false)
                        ch.consume()
                    }
                }
            },
        ) {
            if (!current || k <= 0f) return@Canvas
            val accent = Color(0xFF2F7DF6)
            for (r in text.rects) {
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(r[0].x * k, r[0].y * k); for (i in 1..3) lineTo(r[i].x * k, r[i].y * k); close() }
                drawPath(path, accent.copy(alpha = 0.3f))
            }
            text.caret?.let { c -> if (blink) drawLine(Color(0xFF111111), c[0] * k, c[1] * k, strokeWidth = 2f) }
            for (s in selected) {
                val c = s.corners().map { it * k }
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(c[0].x, c[0].y); for (i in 1..3) lineTo(c[i].x, c[i].y); close() }
                drawPath(path, accent, style = Stroke(1.5f, pathEffect = if (editing) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null))
            }
            if (selected.size == 1 && !editing) {
                val hs = selected[0].handles().map { it * k }
                val half = if (isTouchPlatform) 7f else 4.5f
                drawLine(accent.copy(alpha = 0.7f), hs[1], hs[8], 1f)
                for ((i, h) in hs.withIndex()) {
                    if (i == 8) { drawCircle(Color.White, half + 1.5f, h); drawCircle(accent, half + 1.5f, h, style = Stroke(1.5f)) }
                    else { drawRect(Color.White, h - Offset(half, half), Size(half * 2, half * 2)); drawRect(accent, h - Offset(half, half), Size(half * 2, half * 2), style = Stroke(1.5f)) }
                }
            }
        }
        if (editing && isTouchPlatform) {
            ImeBridge(
                imeFocus,
                onType = { t -> send("ui.text", buildJsonObject { put("text", t) }, final = true) },
                onBackspace = { send("ui.key", buildJsonObject { put("key", "Backspace") }, final = true) },
            )
        }
    }
}

/**
 * The soft keyboard's way in. A field nobody sees holds two zero-width characters; what the keyboard
 * adds after them is typed text, what it takes from them is a Backspace, and the field is put back
 * each time. A composing keyboard (Chinese, Japanese, predictive text) is not followed: each change
 * is taken as typed.
 */
@Composable
private fun ImeBridge(focus: FocusRequester, onType: (String) -> Unit, onBackspace: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(IME_ANCHOR, TextRange(IME_ANCHOR.length))) }
    BasicTextField(
        value,
        { next ->
            val t = next.text
            if (t.length < IME_ANCHOR.length) repeat(IME_ANCHOR.length - t.length) { onBackspace() }
            else {
                val typed = (if (t.startsWith(IME_ANCHOR)) t.substring(IME_ANCHOR.length) else t.replace("\u200B", ""))
                if (typed.isNotEmpty()) onType(typed)
            }
            value = TextFieldValue(IME_ANCHOR, TextRange(IME_ANCHOR.length))
        },
        Modifier.size(1.dp).alpha(0f).focusRequester(focus),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    )
}

private const val IME_ANCHOR = "\u200B\u200B"

/** Presses in a row: a press within [WINDOW] ms and [REACH] pixels of the last one adds to the run, up to three. */
internal class ClickRun {
    private var time = Long.MIN_VALUE / 2
    private var at = Offset.Zero
    private var n = 0

    fun press(now: Long, where: Offset): Int {
        n = if (now - time <= WINDOW && (where - at).getDistance() <= REACH) (n + 1).coerceAtMost(3) else 1
        time = now; at = where
        return n
    }

    companion object { const val WINDOW = 400L; const val REACH = 12f }
}
