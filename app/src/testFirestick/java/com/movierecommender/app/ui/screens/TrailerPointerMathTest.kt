package com.movierecommender.app.ui.screens.firestick

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPointerMathTest {
    @Test fun directionalKeysMoveThePointer() {
        val up = TrailerPointerMath.move(400f, 300f, 1280, 720, 2f, KeyEvent.KEYCODE_DPAD_UP, 0)
        val right = TrailerPointerMath.move(400f, 300f, 1280, 720, 2f, KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        assertEquals(400f, up.first)
        assertEquals(216f, up.second)
        assertEquals(484f, right.first)
        assertEquals(300f, right.second)
    }

    @Test fun pointerCannotMoveOutsideVisiblePlayer() {
        val left = TrailerPointerMath.move(10f, 10f, 640, 360, 1f, KeyEvent.KEYCODE_DPAD_LEFT, 0)
        val bottom = TrailerPointerMath.move(639f, 359f, 640, 360, 1f, KeyEvent.KEYCODE_DPAD_DOWN, 0)
        assertEquals(0f, left.first)
        assertEquals(344f, bottom.second)
        assertEquals(624f, bottom.first)
    }

    @Test fun heldDirectionMovesMoreQuickly() {
        val single = TrailerPointerMath.move(200f, 200f, 1280, 720, 1f, KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        val held = TrailerPointerMath.move(200f, 200f, 1280, 720, 1f, KeyEvent.KEYCODE_DPAD_RIGHT, 6)
        assertTrue(held.first > single.first)
    }
}
