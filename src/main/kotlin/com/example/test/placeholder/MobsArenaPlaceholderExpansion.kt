package com.example.test

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.time.Duration

class MobsArenaPlaceholderExpansion(
    private val plugin: TestPlugin
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "mobsarena"

    override fun getAuthor(): String = plugin.pluginMeta.authors.joinToString(", ").ifBlank { "Stephen" }

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return when (params.lowercase()) {
            "event_status" -> if (BossbarManager.isActive) "Active" else "Waiting"
            "event_name" -> BossbarManager.type
            "event_timer" -> if (BossbarManager.isActive) MobsArenaPlaceholderData.getActiveEventTimeRemainingFormatted() else "00:00"
            "event_requirement_type" -> MobsArenaPlaceholderData.getRequirementTypeLabel()
            "event_requirement_current" -> TextUtil.formatNum(MobsArenaPlaceholderData.getRequirementProgressCurrent())
            "event_requirement_target" -> TextUtil.formatNum(MobsArenaPlaceholderData.getRequirementProgressTarget())
            "event_progress" -> "${TextUtil.formatNum(MobsArenaPlaceholderData.getRequirementProgressCurrent())}/${TextUtil.formatNum(MobsArenaPlaceholderData.getRequirementProgressTarget())}"
            "blackmarket_timer" -> {
                val playerId = player?.uniqueId ?: return "N/A"
                formatDuration(plugin.blackMarketGui.getTimeUntilRestock(playerId))
            }
            else -> null
        }
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d", minutes, seconds)
    }
}
