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

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.tryOrNull

enum class TargetType {
    NONE,

    /** Open an app by package name. */
    APP,

    /** Open a specific activity ([ActionTarget.packageName] + [ActionTarget.className]). */
    ACTIVITY,

    /** Open a link / URI (https://, tel:, geo:, market:, any app scheme). */
    URI,

    /** Start an activity with a custom intent action (for example one handled by MacroDroid or Tasker). */
    ACTION,

    /** Send a broadcast with a custom action (for Tasker / MacroDroid intent triggers). */
    BROADCAST,

    /** Insert fixed text into the edited field. */
    TEXT,
}

/** Something that is executed when a text expander rule fires or a custom smartbar action is pressed. */
@Serializable
data class ActionTarget(
    val type: TargetType = TargetType.NONE,
    val packageName: String = "",
    val className: String = "",
    /** URI, intent action or text, depending on [type]. */
    val value: String = "",
)

object ActionRunner {
    /**
     * Runs [target]. Returns false for types that are not handled here (TEXT must be committed by the caller)
     * or when nothing could be started.
     */
    fun run(context: Context, target: ActionTarget): Boolean {
        return try {
            when (target.type) {
                TargetType.NONE, TargetType.TEXT -> false
                TargetType.APP -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(target.packageName) ?: return false
                    start(context, intent)
                }
                TargetType.ACTIVITY -> {
                    start(context, Intent().setClassName(target.packageName, target.className))
                }
                TargetType.URI -> {
                    start(context, Intent(Intent.ACTION_VIEW, Uri.parse(target.value.trim())))
                }
                TargetType.ACTION -> {
                    val intent = Intent(target.value.trim())
                    if (target.packageName.isNotBlank()) intent.setPackage(target.packageName)
                    start(context, intent)
                }
                TargetType.BROADCAST -> {
                    val intent = Intent(target.value.trim().ifEmpty { return false })
                    if (target.packageName.isNotBlank()) intent.setPackage(target.packageName)
                    context.sendBroadcast(intent)
                    true
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun start(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

/** A user-defined smartbar action. */
@Serializable
data class CustomAction(
    val id: Int,
    /** Short text (or emoji) shown on the smartbar button. */
    val name: String,
    val target: ActionTarget = ActionTarget(),
)

@Serializable
data class CustomActionList(
    val actions: List<CustomAction> = emptyList(),
) {
    fun find(id: Int): CustomAction? = actions.firstOrNull { it.id == id }

    object Serializer : PreferenceSerializer<CustomActionList> {
        override fun serialize(value: CustomActionList): String = Json.encodeToString(value)

        override fun deserialize(value: String): CustomActionList {
            return tryOrNull { Json.decodeFromString<CustomActionList>(value) } ?: CustomActionList()
        }
    }
}
