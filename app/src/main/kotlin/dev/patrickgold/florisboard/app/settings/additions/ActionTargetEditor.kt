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

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.additions.ActionRunner
import dev.patrickgold.florisboard.ime.additions.ActionTarget
import dev.patrickgold.florisboard.ime.additions.TargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

private class AppEntry(val label: String, val packageName: String)

private class ActivityEntry(val label: String, val className: String)

private fun targetLabel(type: TargetType): Int = when (type) {
    TargetType.NONE -> R.string.settings__target__none
    TargetType.APP -> R.string.settings__target__app
    TargetType.ACTIVITY -> R.string.settings__target__activity
    TargetType.URI -> R.string.settings__target__uri
    TargetType.ACTION -> R.string.settings__target__action
    TargetType.BROADCAST -> R.string.settings__target__broadcast
    TargetType.TEXT -> R.string.settings__target__text
}

/** Editor for an [ActionTarget]: choose what happens and pick the app / activity / link / intent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionTargetEditor(
    target: ActionTarget,
    onChange: (ActionTarget) -> Unit,
    allowNone: Boolean = true,
) {
    val context = LocalContext.current
    var pickApp by remember { mutableStateOf(false) }
    var pickActivity by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(R.string.settings__target__title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (type in TargetType.entries) {
                if (type == TargetType.NONE && !allowNone) continue
                val label = stringRes(targetLabel(type))
                if (type == target.type) {
                    Button(onClick = { }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onChange(ActionTarget(type = type)) }) { Text(label) }
                }
            }
        }
        when (target.type) {
            TargetType.NONE -> Unit
            TargetType.APP, TargetType.ACTIVITY -> {
                OutlinedButton(onClick = { pickApp = true }) {
                    Text(target.packageName.ifEmpty { stringRes(R.string.settings__target__pick_app) })
                }
                if (target.type == TargetType.ACTIVITY) {
                    OutlinedButton(
                        enabled = target.packageName.isNotEmpty(),
                        onClick = { pickActivity = true },
                    ) {
                        Text(
                            target.className.substringAfterLast('.').ifEmpty {
                                stringRes(R.string.settings__target__pick_activity)
                            }
                        )
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = target.className,
                        onValueChange = { onChange(target.copy(className = it.trim())) },
                        singleLine = true,
                        label = { Text(stringRes(R.string.settings__target__class)) },
                    )
                }
            }
            TargetType.URI -> OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = target.value,
                onValueChange = { onChange(target.copy(value = it)) },
                singleLine = true,
                label = { Text(stringRes(R.string.settings__target__value_uri)) },
            )
            TargetType.ACTION, TargetType.BROADCAST -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = target.value,
                    onValueChange = { onChange(target.copy(value = it)) },
                    singleLine = true,
                    label = { Text(stringRes(R.string.settings__target__value_action)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = target.packageName,
                    onValueChange = { onChange(target.copy(packageName = it.trim())) },
                    singleLine = true,
                    label = { Text(stringRes(R.string.settings__target__package)) },
                )
            }
            TargetType.TEXT -> OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = target.value,
                onValueChange = { onChange(target.copy(value = it)) },
                label = { Text(stringRes(R.string.settings__target__value_text)) },
            )
        }
        if (target.type != TargetType.NONE && target.type != TargetType.TEXT) {
            TextButton(onClick = { ActionRunner.run(context, target) }) {
                Text(stringRes(R.string.settings__target__test))
            }
        }
    }

    if (pickApp) {
        AppPickerDialog(
            onDismiss = { pickApp = false },
            onPick = {
                pickApp = false
                onChange(target.copy(packageName = it, className = if (it == target.packageName) target.className else ""))
            },
        )
    }
    if (pickActivity) {
        ActivityPickerDialog(
            packageName = target.packageName,
            onDismiss = { pickActivity = false },
            onPick = {
                pickActivity = false
                onChange(target.copy(className = it))
            },
        )
    }
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    val filtered = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) } },
        title = { Text(stringRes(R.string.settings__target__pick_app)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.settings__target__search)) },
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.packageName) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(app.label)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}

@Suppress("DEPRECATION")
@Composable
private fun ActivityPickerDialog(packageName: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var activities by remember { mutableStateOf<List<ActivityEntry>>(emptyList()) }
    LaunchedEffect(packageName) {
        activities = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            try {
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
                    .filter { it.exported && it.enabled }
                    .map { ActivityEntry(it.loadLabel(pm).toString(), it.name) }
                    .sortedBy { it.className }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) } },
        title = { Text(stringRes(R.string.settings__target__pick_activity)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(activities) { a ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(a.className) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(a.label)
                        Text(a.className, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}
