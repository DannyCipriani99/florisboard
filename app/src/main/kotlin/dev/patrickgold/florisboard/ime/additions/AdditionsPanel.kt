/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.additions

import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType

enum class AdditionsPanelKind { NONE, FIND_REPLACE, EXPANDER }

enum class PanelField { FIND, REPLACE, ABBREVIATION }

/**
 * State of the small tool panels shown between the smartbar and the keyboard (find & replace, text expander).
 *
 * An input method cannot host real text fields, so the panels use "virtual" fields: while a panel is open the
 * typed keys are appended to the focused virtual field instead of being sent to the edited app (see [captureKey]).
 */
object AdditionsPanel {
    var kind by mutableStateOf(AdditionsPanelKind.NONE)
        private set
    var focus by mutableStateOf(PanelField.FIND)
    var find by mutableStateOf("")
    var replace by mutableStateOf("")
    var abbreviation by mutableStateOf("")
    var useRegex by mutableStateOf(false)
    var caseSensitive by mutableStateOf(false)
    var status by mutableStateOf("")

    fun toggle(target: AdditionsPanelKind) {
        if (kind == target) {
            close()
        } else {
            kind = target
            status = ""
            focus = if (target == AdditionsPanelKind.EXPANDER) PanelField.ABBREVIATION else PanelField.FIND
        }
    }

    fun close() {
        kind = AdditionsPanelKind.NONE
        status = ""
    }

    private fun focusedText(): String = when (focus) {
        PanelField.FIND -> find
        PanelField.REPLACE -> replace
        PanelField.ABBREVIATION -> abbreviation
    }

    private fun setFocusedText(value: String) {
        when (focus) {
            PanelField.FIND -> find = value
            PanelField.REPLACE -> replace = value
            PanelField.ABBREVIATION -> abbreviation = value
        }
    }

    /** Returns true if the key was consumed by the open panel and must not reach the edited app. */
    fun captureKey(data: KeyData): Boolean {
        if (kind == AdditionsPanelKind.NONE) return false
        if (kind == AdditionsPanelKind.EXPANDER && focus != PanelField.ABBREVIATION) return false
        when {
            data.code == KeyCode.DELETE -> {
                setFocusedText(focusedText().dropLast(1))
            }
            data.code == KeyCode.SPACE -> {
                if (focus != PanelField.ABBREVIATION) setFocusedText(focusedText() + " ")
            }
            data.code == KeyCode.ENTER -> {
                if (kind == AdditionsPanelKind.FIND_REPLACE) findNext()
            }
            data.type == KeyType.CHARACTER || data.type == KeyType.NUMERIC -> {
                val text = data.asString(isForDisplay = false)
                if (text.isEmpty()) return false
                if (focus == PanelField.ABBREVIATION && text.any { it.isWhitespace() }) return true
                setFocusedText(focusedText() + text)
            }
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Find & replace working on the text of the edited field
    // ---------------------------------------------------------------------------------------------------------------

    private class FieldText(val text: String, val selStart: Int, val selEnd: Int)

    private fun readField(): FieldText? {
        val ic = FlorisImeService.currentInputConnection() ?: return null
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted?.text != null) {
            val text = extracted.text.toString()
            val start = (extracted.startOffset + extracted.selectionStart).coerceIn(0, text.length)
            val end = (extracted.startOffset + extracted.selectionEnd).coerceIn(0, text.length)
            return FieldText(text, minOf(start, end), maxOf(start, end))
        }
        val before = ic.getTextBeforeCursor(100_000, 0)?.toString() ?: return null
        val selected = ic.getSelectedText(0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(100_000, 0)?.toString() ?: ""
        return FieldText(before + selected + after, before.length, before.length + selected.length)
    }

    private fun buildRegex(): Regex? {
        if (find.isEmpty()) return null
        return try {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            if (useRegex) Regex(find, options) else Regex(Regex.escape(find), options)
        } catch (e: Exception) {
            status = "Regex: ${e.message?.lineSequence()?.firstOrNull() ?: "invalid"}"
            null
        }
    }

    private fun replacementFor(): String {
        // In regex mode `$1` style group references work, in plain mode the replacement is used literally.
        return if (useRegex) replace else replace.replace("\\", "\\\\").replace("$", "\\$")
    }

    fun findNext(forward: Boolean = true) {
        val regex = buildRegex() ?: return
        val field = readField() ?: run { status = "-"; return }
        val ic = FlorisImeService.currentInputConnection() ?: return
        val matches = regex.findAll(field.text).filter { it.value.isNotEmpty() }.toList()
        if (matches.isEmpty()) {
            status = "0"
            return
        }
        val target = if (forward) {
            matches.firstOrNull { it.range.first >= field.selEnd && (it.range.first > field.selStart || field.selEnd == field.selStart) }
                ?: matches.firstOrNull { it.range.first >= field.selEnd }
                ?: matches.first()
        } else {
            matches.lastOrNull { it.range.last + 1 <= field.selStart } ?: matches.last()
        }
        ic.setSelection(target.range.first, target.range.last + 1)
        status = "${matches.indexOf(target) + 1}/${matches.size}"
    }

    fun replaceCurrent() {
        val regex = buildRegex() ?: return
        val field = readField() ?: return
        val ic = FlorisImeService.currentInputConnection() ?: return
        if (field.selStart != field.selEnd) {
            val selected = field.text.substring(field.selStart, field.selEnd)
            val m = regex.matchEntire(selected)
            if (m != null) {
                val replaced = try {
                    regex.replace(selected, replacementFor())
                } catch (e: Exception) {
                    status = e.message ?: "error"
                    return
                }
                ic.commitText(replaced, 1)
            }
        }
        findNext()
    }

    fun replaceAll() {
        val regex = buildRegex() ?: return
        val field = readField() ?: return
        val ic = FlorisImeService.currentInputConnection() ?: return
        val count = regex.findAll(field.text).count { it.value.isNotEmpty() }
        if (count == 0) {
            status = "0"
            return
        }
        val newText = try {
            regex.replace(field.text, replacementFor())
        } catch (e: Exception) {
            status = e.message ?: "error"
            return
        }
        ic.beginBatchEdit()
        ic.setSelection(0, field.text.length)
        ic.commitText(newText, 1)
        ic.endBatchEdit()
        status = "✓ $count"
    }
}
