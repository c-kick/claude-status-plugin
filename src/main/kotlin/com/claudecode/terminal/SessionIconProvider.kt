package com.claudecode.terminal

import com.claudecode.status.SessionState
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Provides colored dot icons for Claude session states.
 * Icons are pre-built for all 4 states.
 */
object SessionIconProvider {

    private const val ICON_SIZE = 13
    private const val DOT_SIZE = 8

    private val COLOR_WORKING = Color(220, 50, 50)     // Red
    private val COLOR_WAITING = Color(230, 150, 20)     // Orange
    private val COLOR_IDLE = Color(50, 180, 70)         // Green
    private val COLOR_STALE = Color(140, 140, 140)      // Gray

    private val icons: Map<SessionState, Icon> = SessionState.entries.associateWith { state ->
        DotIcon(colorForState(state))
    }

    private fun colorForState(state: SessionState): Color = when (state) {
        SessionState.WORKING -> COLOR_WORKING
        SessionState.WAITING -> COLOR_WAITING
        SessionState.IDLE -> COLOR_IDLE
        SessionState.STALE -> COLOR_STALE
    }

    fun getIcon(state: SessionState): Icon = icons.getValue(state)

    private class DotIcon(private val color: Color) : Icon {
        override fun getIconWidth(): Int = JBUI.scale(ICON_SIZE)
        override fun getIconHeight(): Int = JBUI.scale(ICON_SIZE)

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            val dotSize = JBUI.scale(DOT_SIZE)
            val offset = (JBUI.scale(ICON_SIZE) - dotSize) / 2
            g2.fillOval(x + offset, y + offset, dotSize, dotSize)
            g2.dispose()
        }
    }
}
