package app.loki.decks

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class SlideModelTest {
    @Test fun theSlideSizeIsReadOrDefaulted() {
        assertEquals(720f to 405f, parseSlideSize(Json.parseToJsonElement("""{"slideSize":[720.0,405.0]}""")))
        for (bad in listOf("""{"slideSize":[0,-3]}""", """{"slideSize":["a"]}""", "{}", "[]")) assertEquals(960f to 540f, parseSlideSize(Json.parseToJsonElement(bad)), bad)
        assertEquals(960f to 540f, parseSlideSize(null))
    }

    @Test fun aPointerEventIsInTheShapeTheEngineReads() {
        val p = pointerParams("drag", 12.5f, 40f, shift = true, alt = false, cmd = false)
        val e = ((p["events"] as JsonArray)[0]) as JsonObject
        assertEquals("drag", (e["kind"] as JsonPrimitive).content)
        assertEquals(12.5, (e["x"] as JsonPrimitive).doubleOrNull)
        val mods = e["mods"] as JsonObject
        assertEquals(true, (mods["shift"] as JsonPrimitive).booleanOrNull)
        assertEquals(false, (mods["alt"] as JsonPrimitive).booleanOrNull)
        assertEquals(false, e.containsKey("pressure"))
    }

    @Test fun toolsAndKeysHaveEngineNames() {
        assertEquals("textBox", engineTool("TextBox")); assertEquals("pen", engineTool("Ink")); assertEquals("select", engineTool("nonsense"))
        assertEquals("ArrowLeft", engineKeyName("ArrowLeft")); assertEquals(null, engineKeyName("Shift"))
    }

    @Test fun shapesAndTheSelectionAreRead() {
        val shapes = parseSlideShapes(Json.parseToJsonElement("""{"shapes":[{"id":258,"box":[120.0,88.0,720.0,188.0],"rotation":0.0},{"id":9,"box":[1,2,3,4],"rotation":30},{"id":1},{"id":2,"box":[1,2,3]},{"box":[1,2,3,4]}]}"""))
        assertEquals(listOf(258L, 9L), shapes.map { it.id })
        assertEquals(emptyList(), parseSlideShapes(null))
        val doc = Json.parseToJsonElement("""{"selection":{"slide":2,"shapes":[258,259],"text":null}}""")
        assertEquals(SlideSelection(2, setOf(258L, 259L), false), parseSlideSelection(doc))
        assertEquals(true, parseSlideSelection(Json.parseToJsonElement("""{"selection":{"slide":0,"shapes":[1],"text":{"anchor":[0,1]}}}""")).editingText)
        assertEquals(SlideSelection(-1, emptySet(), false), parseSlideSelection(null))
    }

    @Test fun handlesAreWhereTheEngineHasThem() {
        val s = SlideShape(1, 100f, 100f, 200f, 100f, 0f)
        val h = s.handles()
        assertEquals(9, h.size)
        assertEquals(Offset(100f, 100f), h[0]); assertEquals(Offset(200f, 100f), h[1]); assertEquals(Offset(300f, 200f), h[4])
        assertEquals(Offset(200f, 80f), h[8])          // the knob, 20 above the top middle
        // Turned a quarter, the knob swings to the right of the box.
        val t = SlideShape(1, 100f, 100f, 200f, 100f, 90f).handles()[8]
        assertEquals(270f, t.x, 0.01f); assertEquals(150f, t.y, 0.01f)
    }

    @Test fun theNearestHandleWithinReachIsPicked() {
        val hs = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(100f, 100f))
        assertEquals(1, handleAt(hs, Offset(8f, 1f), 6f))
        assertEquals(null, handleAt(hs, Offset(50f, 50f), 6f))
    }

    @Test fun pressesInARunAreCounted() {
        val c = ClickRun()
        assertEquals(1, c.press(1000, Offset(10f, 10f)))
        assertEquals(2, c.press(1200, Offset(12f, 11f)))
        assertEquals(3, c.press(1350, Offset(12f, 11f)))
        assertEquals(3, c.press(1500, Offset(12f, 11f)))       // never past a triple
        assertEquals(1, c.press(2500, Offset(12f, 11f)))       // too late
        assertEquals(1, c.press(2600, Offset(300f, 11f)))      // too far
    }

    @Test fun theCaretAndHighlightAreRead() {
        val g = parseTextGeometry(Json.parseToJsonElement("""{"caret":[[10,20],[10,40]],"rects":[[[1,2],[5,2],[5,9],[1,9]],[[1,2]],"junk"]}"""))
        assertEquals(listOf(Offset(10f, 20f), Offset(10f, 40f)), g.caret)
        assertEquals(1, g.rects.size)                        // the short and the junk ones are dropped
        assertEquals(TextGeometry.None, parseTextGeometry(Json.parseToJsonElement("""{"caret":null,"rects":[]}""")))
        assertEquals(TextGeometry.None, parseTextGeometry(null))
        assertEquals(null, parseTextGeometry(Json.parseToJsonElement("""{"caret":[[1,2]]}""")).caret)
    }
}
