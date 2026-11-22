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

package dev.patrickgold.florisboard.app.settings.additions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.additions.ActionTarget
import dev.patrickgold.florisboard.ime.additions.CustomAction
import dev.patrickgold.florisboard.ime.additions.CustomActionList
import dev.patrickgold.florisboard.ime.additions.TargetType
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private fun targetSummary(t: ActionTarget): String = when (t.type) {
    TargetType.NONE -> "-"
    TargetType.APP -> t.packageName
    TargetType.ACTIVITY -> t.packageName + "/" + t.className.substringAfterLast('.')
    else -> t.value
}

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun CustomActionsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__custom_actions__title)
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val list by prefs.additions.customActions.observeAsState()
    // null = closed, -1 = new action, otherwise the id of the edited action
    var editing by remember { mutableStateOf<Int?>(null) }

    /** Keeps the smartbar arrangement in sync: new actions are appended, renamed ones updated, deleted ones removed. */
    suspend fun syncArrangement(old: CustomAction?, new: CustomAction?) {
        val arrangement = prefs.smartbar.actionArrangement.get()
        val oldKey = old?.let { TextKeyData.customAction(it.id, it.name) }
        val newKey = new?.let { TextKeyData.customAction(it.id, it.name) }
        fun map(actions: List<QuickAction>): List<QuickAction> = actions.mapNotNull { a ->
            if (oldKey != null && a is QuickAction.InsertKey && a.data == oldKey) {
                newKey?.let { QuickAction.InsertKey(it) }
            } else {
                a
            }
        }
        var updated = arrangement.copy(
            stickyAction = arrangement.stickyAction?.let { s ->
                if (oldKey != null && s is QuickAction.InsertKey && s.data == oldKey) {
                    newKey?.let { QuickAction.InsertKey(it) }
                } else {
                    s
                }
            },
            dynamicActions = map(arrangement.dynamicActions),
            hiddenActions = map(arrangement.hiddenActions),
        )
        if (old == null && newKey != null) {
            updated = updated.copy(dynamicActions = updated.dynamicActions + QuickAction.InsertKey(newKey))
        }
        prefs.smartbar.actionArrangement.set(updated)
    }

    content {
        Preference(
            title = stringRes(R.string.settings__custom_actions__add),
            summary = stringRes(R.string.settings__custom_actions__summary),
            onClick = { editing = -1 },
        )
        for (action in list.actions) {
            Preference(
                title = action.name,
                summary = targetSummary(action.target),
                onClick = { editing = action.id },
            )
        }

        editing?.let { id ->
            val initial = list.find(id)
            var name by remember(id) { mutableStateOf(initial?.name ?: "") }
            var target by remember(id) { mutableStateOf(initial?.target ?: ActionTarget(type = TargetType.APP)) }
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__custom_actions__add),
                confirmLabel = stringRes(R.string.action__save),
                onConfirm = {
                    if (name.isBlank() || target.type == TargetType.NONE) return@JetPrefAlertDialog
                    scope.launch {
                        val newId = initial?.id ?: ((list.actions.maxOfOrNull { it.id } ?: 999) + 1)
                        val created = CustomAction(newId, name.trim(), target)
                        val actions = if (initial != null) {
                            list.actions.map { if (it.id == initial.id) created else it }
                        } else {
                            list.actions + created
                        }
                        prefs.additions.customActions.set(CustomActionList(actions))
                        syncArrangement(initial, created)
                        editing = null
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { editing = null },
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringRes(R.string.settings__custom_actions__name)) },
                        supportingText = { Text(stringRes(R.string.settings__custom_actions__name_hint)) },
                    )
                    ActionTargetEditor(target = target, onChange = { target = it }, allowNone = false)
                    if (initial != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    prefs.additions.customActions.set(
                                        CustomActionList(list.actions.filter { it.id != initial.id })
                                    )
                                    syncArrangement(initial, null)
                                    editing = null
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringRes(R.string.action__delete)) }
                    }
                }
            }
        }
    }
}
