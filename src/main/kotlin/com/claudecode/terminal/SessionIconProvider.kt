package com.claudecode.terminal

import com.claudecode.status.SessionState
import com.intellij.ui.LayeredIcon
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
    private const val BADGE_SIZE = 7

    private val COLOR_WORKING = Color(220, 50, 50)     // Red
    private val COLOR_WAITING = Color(230, 150, 20)     // Orange
    private val COLOR_IDLE = Color(50, 180, 70)         // Green
    private val COLOR_STALE = Color(140, 140, 140)      // Gray

    private val icons: Map<SessionState, Icon> = SessionState.entries.associateWith { state ->
        DotIcon(colorForState(state), ICON_SIZE, DOT_SIZE)
    }

    /** Small badge dots for tool window stripe overlay. */
    private val badgeIcons: Map<SessionState, Icon> = SessionState.entries.associateWith { state ->
        DotIcon(colorForState(state), BADGE_SIZE, BADGE_SIZE)
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
     * Uses IntelliJ's [LayeredIcon] for correct HiDPI scaling and stripe rendering.
     * Cached per (baseIcon, state) pair to avoid re-allocation on every poll cycle.
     */
    fun getBadgedIcon(baseIcon: Icon, state: SessionState): Icon {
        cachedBadge?.let { (cachedBase, cachedState, cachedIcon) ->
            if (cachedBase === baseIcon && cachedState == state) return cachedIcon
        }
        val badge = badgeIcons.getValue(state)
        val layered = LayeredIcon(2)
        layered.setIcon(baseIcon, 0)
        // Position badge in bottom-right corner of the base icon
        val xOffset = baseIcon.iconWidth - badge.iconWidth
        val yOffset = baseIcon.iconHeight - badge.iconHeight
        layered.setIcon(badge, 1, xOffset, yOffset)
        cachedBadge = Triple(baseIcon, state, layered)
        return layered
    }

    /**
     * Simple filled-circle icon. All dimensions in logical pixels — no manual JBUI.scale()
     * on position math, keeping coordinate space consistent with [LayeredIcon] offsets.
     */
    private class DotIcon(
        private val color: Color,
        private val iconSize: Int,
        private val dotSize: Int
    ) : Icon {
        override fun getIconWidth(): Int = JBUI.scale(iconSize)
        override fun getIconHeight(): Int = JBUI.scale(iconSize)

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            val scaledDot = JBUI.scale(dotSize)
            val scaledIcon = JBUI.scale(iconSize)
            val offset = (scaledIcon - scaledDot) / 2
            g2.fillOval(x + offset, y + offset, scaledDot, scaledDot)
            g2.dispose()
        }
    }
}
