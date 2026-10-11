package com.augustuz.openmathpad.canvas.model

import java.util.UUID

/**
 * Represents an inserted image or STEM problem screenshot on the infinite canvas.
 *
 * @param id Unique identifier for the image card.
 * @param uri Android content Uri string pointing to image asset.
 * @param x Scene space X position of top-left corner.
 * @param y Scene space Y position of top-left corner.
 * @param width Scene space width of the image.
 * @param height Scene space height of the image.
 * @param isSelected Whether the image card is currently selected for editing/resizing.
 */
data class CanvasImage(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val x: Float,
    val y: Float,
    val width: Float = 360f,
    val height: Float = 260f,
    val isSelected: Boolean = false
)
