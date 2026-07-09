package com.example.cubemaster.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CubeMasterColorsTest {

    @Test
    fun `red and gold are distinct brand colors`() {
        assertNotEquals(CubeMasterColors.red, CubeMasterColors.gold)
    }

    @Test
    fun `redMuted keeps the red hue, only alpha is reduced`() {
        val base = CubeMasterColors.red
        val muted = CubeMasterColors.redMuted
        assertEquals(base.red, muted.red, 0.001f)
        assertEquals(base.green, muted.green, 0.001f)
        assertEquals(base.blue, muted.blue, 0.001f)
        assertNotEquals(1f, muted.alpha)
    }
}
