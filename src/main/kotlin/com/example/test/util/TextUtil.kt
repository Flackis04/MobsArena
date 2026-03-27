package com.example.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.math.RoundingMode
import java.text.DecimalFormat

object TextUtil {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun colorize(input: String): String {
        val withHex = replaceHexColors(input)
        return ChatColor.translateAlternateColorCodes('&', withHex)
    }

    fun toComponent(input: String): Component = legacySerializer.deserialize(colorize(input))

    fun toLegacyString(component: Component?): String? {
        if (component == null) return null
        return legacySerializer.serialize(component)
    }

    private fun replaceHexColors(input: String): String {
        val angleBracketHex = Regex("<#([A-Fa-f0-9]{6})>")
        val ampersandHex = Regex("&#([A-Fa-f0-9]{6})")

        val withAngleBracketHex = angleBracketHex.replace(input) { match ->
            val hex = match.groupValues[1]
            val chars = hex.toCharArray().joinToString("") { "§$it" }
            "§x$chars"
        }

        return ampersandHex.replace(withAngleBracketHex) { match ->
            val hex = match.groupValues[1]
            val chars = hex.toCharArray().joinToString("") { "§$it" }
            "§x$chars"
        }
    }

    fun formatNum(quantity: Number): String {
        val value = quantity.toDouble()
        val df = DecimalFormat("#.##")
        df.roundingMode = RoundingMode.HALF_UP
        return when {
            value >= 1_000_000_000_000 -> df.format(value / 1_000_000_000_000) + "T"
            value >= 1_000_000_000 -> df.format(value / 1_000_000_000) + "B"
            value >= 1_000_000 -> df.format(value / 1_000_000) + "M"
            value >= 1_000 -> df.format(value / 1_000) + "K"
            else -> if (value % 1.0 == 0.0) value.toLong().toString() else df.format(value)
        }
    }

    fun formatPlaytime(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            seconds < 604_800 -> "${seconds / 86_400}d"
            seconds < 2_419_200 -> "${seconds / 604_800}w"
            seconds < 29_030_400 -> "${seconds / 2_419_200}mo"
            else -> "${seconds / 29_030_400}y"
        }
    }

    fun sendActionBarFor(player: Player, seconds: Double, message: String) {
        ActionBarManager.sendActionBarFor(player, seconds, message)
    }
}
