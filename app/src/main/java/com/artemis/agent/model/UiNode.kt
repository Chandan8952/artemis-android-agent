package com.artemis.agent.model

import android.graphics.Rect

/**
 * Represents a compressed, token-efficient UI element from Android's Accessibility hierarchy.
 */
data class UiNode(
    val index: Int,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val packageName: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
) {
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    /**
     * Compact single-line summary for feeding into VLM context.
     * E.g.: [12] Button "Search" @ (350, 120) clickable=true
     */
    fun toCompactString(): String {
        val label = when {
            !text.isNullOrBlank() -> "\"$text\""
            !contentDescription.isNullOrBlank() -> "desc=\"$contentDescription\""
            else -> className.substringAfterLast('.')
        }
        val flags = buildList {
            if (isClickable) add("click")
            if (isEditable) add("edit")
            if (isScrollable) add("scroll")
        }.joinToString(",")

        return "[$index] $label @ (${centerX}, ${centerY}) bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}] $flags"
    }
}
