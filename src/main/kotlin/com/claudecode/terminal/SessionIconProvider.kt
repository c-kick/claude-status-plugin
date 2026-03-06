package com.claudecode.terminal

import com.claudecode.status.SessionState
import com.intellij.ui.JBColor
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

    /** Cached badged icon: (baseIcon identity, state) → composited icon. */
    private var cachedBadge: Triple<Icon, SessionState, Icon>? = null

    /**
     * Returns a composite icon that overlays a small status dot badge on a base icon.
     * Cached per (baseIcon, state) pair to avoid re-allocation on every poll cycle.
     */
    fun getBadgedIcon(baseIcon: Icon, state: SessionState): Icon {
        cachedBadge?.let { (cachedBase, cachedState, cachedIcon) ->
            if (cachedBase === baseIcon && cachedState == state) return cachedIcon
        }
        val icon = BadgedIcon(baseIcon, colorForState(state))
        cachedBadge = Triple(baseIcon, state, icon)
        return icon
    }

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

    /**
     * Composite icon: paints a base icon with a small colored dot badge in the bottom-right corner.
     */
    private class BadgedIcon(private val baseIcon: Icon, private val badgeColor: Color) : Icon {
        override fun getIconWidth(): Int = baseIcon.iconWidth
        override fun getIconHeight(): Int = baseIcon.iconHeight

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            baseIcon.paintIcon(c, g, x, y)
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val badgeSize = JBUI.scale(6)
            val outlinePad = JBUI.scale(1)
            val badgeX = x + iconWidth - badgeSize
            val badgeY = y + iconHeight - badgeSize
            // Theme-aware outline for contrast
            g2.color = OUTLINE_COLOR
            g2.fillOval(badgeX - outlinePad, badgeY - outlinePad, badgeSize + outlinePad * 2, badgeSize + outlinePad * 2)
            g2.color = badgeColor
            g2.fillOval(badgeX, badgeY, badgeSize, badgeSize)
            g2.dispose()
        }

        companion object {
            private val OUTLINE_COLOR = JBColor(Color(200, 200, 200), Color(50, 50, 50))
        }
    }
}
