package com.augustuz.openmathpad.canvas.processing

import android.view.MotionEvent

/**
 * Implements hardware palm rejection filtering for stylus tablet input.
 * Rejects finger/palm events when in Stylus-only mode.
 */
object PalmRejectionFilter {

    /**
     * Determines whether input event with specified toolType is allowed.
     *
     * @param toolType Tool type from MotionEvent (e.g. TOOL_TYPE_STYLUS, TOOL_TYPE_FINGER).
     * @param touchModeEnabled Whether non-stylus touch input is explicitly enabled.
     * @return True if input should be processed, false if rejected as palm/finger.
     */
    fun isInputAllowed(toolType: Int, touchModeEnabled: Boolean): Boolean {
        return when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_ERASER -> true

            MotionEvent.TOOL_TYPE_FINGER,
            MotionEvent.TOOL_TYPE_UNKNOWN,
            MotionEvent.TOOL_TYPE_MOUSE -> touchModeEnabled

            else -> touchModeEnabled
        }
    }

    /**
     * Checks whether the event tool type represents a hardware stylus eraser tail.
     */
    fun isHardwareEraser(toolType: Int): Boolean {
        return toolType == MotionEvent.TOOL_TYPE_ERASER
    }
}
