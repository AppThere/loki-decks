package app.loki.decks

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/** The slide's size in points, from `document.inspect`; 960 × 540 if it says nothing usable. */
fun parseSlideSize(doc: JsonElement?): Pair<Float, Float> {
    val a = ((doc as? JsonObject)?.get("slideSize") as? JsonArray)?.map { (it as? JsonPrimitive)?.floatOrNull }
    val w = a?.getOrNull(0); val h = a?.getOrNull(1)
    return if (w != null && h != null && w.isFinite() && h.isFinite() && w > 0f && h > 0f) w to h else 960f to 540f
}

/** What a press, drag or lift of the pointer is called to the engine: `down`, `drag`, `up`, `doubleclick`, `tripleclick`. */
fun pointerParams(kind: String, x: Float, y: Float, shift: Boolean, alt: Boolean, cmd: Boolean, pressure: Float? = null): JsonObject = buildJsonObject {
    put("events", buildJsonArray {
        add(buildJsonObject {
            put("kind", kind); put("x", x.toDouble()); put("y", y.toDouble())
            put("mods", buildJsonObject { put("shift", shift); put("alt", alt); put("cmd", cmd) })
            if (pressure != null) put("pressure", pressure.toDouble())
        })
    })
}

/** The engine's tool for one of this app's tool-strip ids. */
fun engineTool(id: String): String = when (id) {
    "Select" -> "select"
    "Shape" -> "shape"
    "TextBox" -> "textBox"
    "Ink" -> "pen"
    else -> "select"
}

/** The engine's name for a key, or null for one it has no use for (a lone modifier). */
fun engineKeyName(name: String): String? = when (name) {
    "Delete", "Backspace", "Enter", "Escape", "Tab", "Home", "End", "PageUp", "PageDown",
    "ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Insert",
    -> name
    else -> null
}

/** A shape on a slide as `slide.inspect` lists it: its box in points (left, top, width, height) and its turn in degrees. */
data class SlideShape(val id: Long, val x: Float, val y: Float, val w: Float, val h: Float, val rotation: Float) {
    private fun turned(px: Float, py: Float): Offset {
        val a = Math.toRadians(rotation.toDouble())
        val co = cos(a).toFloat(); val si = sin(a).toFloat()
        val cx = x + w / 2; val cy = y + h / 2
        val dx = px - cx; val dy = py - cy
        return Offset(cx + dx * co - dy * si, cy + dx * si + dy * co)
    }

    /** The four corners (top-left, top-right, bottom-right, bottom-left) after the turn. */
    fun corners(): List<Offset> = listOf(turned(x, y), turned(x + w, y), turned(x + w, y + h), turned(x, y + h))

    /**
     * Where the engine's handles are: the eight of the box clockwise from the top left (corners and
     * side middles), then the rotate knob [KNOB] points above the top middle. They are the engine's own
     * (`box_handles`), so a press sent to one of these is a press on that handle.
     */
    fun handles(): List<Offset> = listOf(
        turned(x, y), turned(x + w / 2, y), turned(x + w, y), turned(x + w, y + h / 2),
        turned(x + w, y + h), turned(x + w / 2, y + h), turned(x, y + h), turned(x, y + h / 2),
        turned(x + w / 2, y - KNOB),
    )

    companion object { const val KNOB = 20f }
}

private fun JsonElement?.f(): Float? = (this as? JsonPrimitive)?.floatOrNull

/** The shapes in `slide.inspect`'s result, bottom first. Anything unreadable is left out, never thrown. */
fun parseSlideShapes(e: JsonElement?): List<SlideShape> =
    (((e as? JsonObject)?.get("shapes")) as? JsonArray).orEmpty().mapNotNull { s ->
        val o = s as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
        val box = (o["box"] as? JsonArray)?.map { it.f() } ?: return@mapNotNull null
        if (box.size < 4 || box.any { it == null || !it.isFinite() }) return@mapNotNull null
        SlideShape(id, box[0]!!, box[1]!!, box[2]!!, box[3]!!, o["rotation"].f()?.takeIf { it.isFinite() } ?: 0f)
    }

/** What `document.inspect` says is selected: the slide, the ids of the shapes on it, and whether text is being edited. */
data class SlideSelection(val slide: Int, val ids: Set<Long>, val editingText: Boolean)

fun parseSlideSelection(doc: JsonElement?): SlideSelection {
    val sel = (doc as? JsonObject)?.get("selection") as? JsonObject
    val slide = (sel?.get("slide") as? JsonPrimitive)?.longOrNull?.toInt() ?: -1
    val ids = (sel?.get("shapes") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.longOrNull }.toSet()
    val text = sel?.get("text")
    return SlideSelection(slide, ids, text != null && text !is kotlinx.serialization.json.JsonNull)
}

/** The index of the handle of [handles] within [reach] of [p], nearest first; null if none. */
fun handleAt(handles: List<Offset>, p: Offset, reach: Float): Int? =
    handles.withIndex().filter { (_, h) -> (h - p).getDistance() <= reach }.minByOrNull { (_, h) -> (h - p).getDistance() }?.index
