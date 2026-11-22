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

import android.view.KeyEvent
import dev.patrickgold.florisboard.ime.editor.EditorInstance

/**
 * One-shot Ctrl / Alt modifiers set by the Ctrl and Alt keys of the layout editor. The next letter or digit is sent
 * as a real key event with the modifier (for example Ctrl+C) instead of being typed as text.
 */
object ModifierKeys {
    @Volatile var ctrl = false
    @Volatile var alt = false

    /** Returns true if the key was sent as a shortcut and must not be typed as text. */
    fun trySendShortcut(editor: EditorInstance, text: String): Boolean {
        if (!ctrl && !alt) return false
        val c = text.singleOrNull()?.lowercaseChar() ?: return false
        val keyCode = when (c) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            else -> return false
        }
        val sent = editor.sendDownUpKeyEvent(keyCode, editor.meta(ctrl = ctrl, alt = alt))
        ctrl = false
        alt = false
        return sent
    }
}
