package com.puzzlenook.ui

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text that is looked up in the string resources when it is shown, so it follows the app language.
 * Pure code (view models, rule texts, tests) passes this around instead of a String.
 * With [quantity] set, [id] is a plurals resource; [args] are the format arguments as they are
 * (the caller repeats the quantity in them when the string shows it). An argument may itself be a
 * [UiText] and is resolved first.
 */
data class UiText(
    @StringRes val id: Int,
    val args: List<Any> = emptyList(),
    val quantity: Int? = null,
) {
    @Composable
    fun resolve(): String = resolve(LocalContext.current.resources)

    @SuppressLint("ResourceType")
    fun resolve(res: Resources): String {
        val resolved = args.map { if (it is UiText) it.resolve(res) else it }.toTypedArray()
        return when {
            quantity != null -> res.getQuantityString(id, quantity, *resolved)
            resolved.isEmpty() -> res.getString(id)
            else -> res.getString(id, *resolved)
        }
    }

    companion object {
        /** A plurals resource: the form is picked by [quantity]. */
        @SuppressLint("ResourceType")
        fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any) = UiText(id, args.toList(), quantity)
    }
}
