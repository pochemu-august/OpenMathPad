package com.augustuz.openmathpad.canvas

import android.view.MotionEvent
import com.augustuz.openmathpad.canvas.processing.PalmRejectionFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalmRejectionFilterTest {

    // MotionEvent Tool Type Constants
    private val toolTypeStylus = MotionEvent.TOOL_TYPE_STYLUS
    private val toolTypeEraser = MotionEvent.TOOL_TYPE_ERASER
    private val toolTypeFinger = MotionEvent.TOOL_TYPE_FINGER
    private val toolTypeUnknown = MotionEvent.TOOL_TYPE_UNKNOWN
    private val toolTypeMouse = MotionEvent.TOOL_TYPE_MOUSE

    @Test
    fun stylusInput_allowed_whenTouchModeDisabled() {
        val allowed = PalmRejectionFilter.isInputAllowed(toolTypeStylus, touchModeEnabled = false)
        assertTrue("Stylus input must be accepted in palm rejection mode", allowed)
    }

    @Test
    fun hardwareEraserInput_allowed_whenTouchModeDisabled() {
        val allowed = PalmRejectionFilter.isInputAllowed(toolTypeEraser, touchModeEnabled = false)
        assertTrue("Stylus eraser input must be accepted in palm rejection mode", allowed)
    }

    @Test
    fun fingerInput_rejected_whenTouchModeDisabled() {
        val allowed = PalmRejectionFilter.isInputAllowed(toolTypeFinger, touchModeEnabled = false)
        assertFalse("Finger touch must be rejected when palm rejection is active", allowed)
    }

    @Test
    fun unknownInput_rejected_whenTouchModeDisabled() {
        val allowed = PalmRejectionFilter.isInputAllowed(toolTypeUnknown, touchModeEnabled = false)
        assertFalse("Unknown touch must be rejected when palm rejection is active", allowed)
    }

    @Test
    fun fingerInput_accepted_whenTouchModeEnabled() {
        val allowed = PalmRejectionFilter.isInputAllowed(toolTypeFinger, touchModeEnabled = true)
        assertTrue("Finger touch must be allowed when touch toggle mode is explicitly enabled", allowed)
    }

    @Test
    fun mouseInput_accepted_whenTouchModeEnabled() {
        val allowed = PalmRejectionFilter.isInputAllowed(toolTypeMouse, touchModeEnabled = true)
        assertTrue("Mouse touch must be allowed when touch toggle mode is enabled", allowed)
    }

    @Test
    fun isHardwareEraser_identifiesEraserToolType() {
        assertTrue(PalmRejectionFilter.isHardwareEraser(toolTypeEraser))
        assertFalse(PalmRejectionFilter.isHardwareEraser(toolTypeStylus))
        assertFalse(PalmRejectionFilter.isHardwareEraser(toolTypeFinger))
    }
}
