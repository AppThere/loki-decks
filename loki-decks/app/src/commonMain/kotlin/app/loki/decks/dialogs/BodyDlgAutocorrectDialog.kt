package app.loki.decks.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Body dlg autocorrect: translated from `dialogs/body.rs` (fn `dlg_autocorrect`, line 1293) by egui2compose.
 *
 * The controls and their order are the desktop dialog's. NOT translated: the starting values (the
 * desktop reads them from its own state), anything it draws by hand, and enabling rules. The
 * parameter names are guessed from the Rust fields each control was bound to: check them against
 * the command's params doc before trusting OK.
 *   command: text.autocorrect
 *   params:  {on?, twoInitials?, sentenceCaps?, days?, capsLock?, fractions?, ordinals?, lists?: bool, add?: [from, to], remove?: from} → the options and the replacement list
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyDlgAutocorrectDialog(onRun: (command: String, params: JsonObject) -> Unit, onDismiss: () -> Unit) {
    var f0 by remember { mutableStateOf(false) }
    var f1 by remember { mutableStateOf(false) }
    var f2 by remember { mutableStateOf(false) }
    var f3 by remember { mutableStateOf(false) }
    var f4 by remember { mutableStateOf(false) }
    var f5 by remember { mutableStateOf(false) }
    var f6 by remember { mutableStateOf(false) }
    var f7 by remember { mutableStateOf(false) }
    var f8 by remember { mutableStateOf("") }
    var f9 by remember { mutableStateOf("") }
    SheetDialog(
        "Body dlg autocorrect", "", onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val params = buildJsonObject {
                put("acTwoInitials", f0)
                put("acSentenceCaps", f1)
                put("acDays", f2)
                put("acCapsLock", f3)
                put("acFractions", f4)
                put("acOrdinals", f5)
                put("acLists", f6)
                put("autocorrect", f7)
                if (f8.isNotBlank()) put("from", f8)
                if (f9.isNotBlank()) put("to", f9)
                }
                onRun("text.autocorrect", params)
                onDismiss()
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { f0 = !f0 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f0, { f0 = it })
                Text("Correct TWo INitial CApitals")
            }
            Row(Modifier.fillMaxWidth().clickable { f1 = !f1 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f1, { f1 = it })
                Text("Capitalize first letter of sentences")
            }
            Row(Modifier.fillMaxWidth().clickable { f2 = !f2 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f2, { f2 = it })
                Text("Capitalize names of days")
            }
            Row(Modifier.fillMaxWidth().clickable { f3 = !f3 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f3, { f3 = it })
                Text("Correct accidental use of cAPS LOCK key")
            }
            Row(Modifier.fillMaxWidth().clickable { f4 = !f4 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f4, { f4 = it })
                Text("Replace fractions (1/2) with fraction characters (½)")
            }
            Row(Modifier.fillMaxWidth().clickable { f5 = !f5 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f5, { f5 = it })
                Text("Ordinals (1st) with superscript")
            }
            Row(Modifier.fillMaxWidth().clickable { f6 = !f6 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f6, { f6 = it })
                Text("Automatic bulleted and numbered lists")
            }
            Row(Modifier.fillMaxWidth().clickable { f7 = !f7 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f7, { f7 = it })
                Text("Replace text as you type")
            }
            OutlinedTextField(f8, { f8 = it }, label = { Text("Replace") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f9, { f9 = it }, label = { Text("With") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // egui button "Delete" runs text.autocorrect
        }
    }
}
