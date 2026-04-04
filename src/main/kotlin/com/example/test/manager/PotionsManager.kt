package com.example.test

import org.bukkit.entity.Player
import kotlin.math.max

object PotionsManager {
    enum class BuffType(
        val displayName: String,
        val tokenCost: Long,
        val durationMinutes: Int,
        val multiplier: Double
    ) {
        MINE_RICHNESS("Mine Richness Boost", 3_000L, 12, 1.12),
        XP("XP Boost", 2_500L, 12, 1.15),
        PROC("Proc Boost", 3_500L, 12, 1.06),
        FORTUNE("Fortune Boost", 2_750L, 12, 1.10)
    }

    fun getMineRichnessMultiplier(data: PlayerData): Double = activeMultiplier(data.minePotionExpiresAt, data.minePotionMultiplier)
    fun getXpMultiplier(data: PlayerData): Double = activeMultiplier(data.xpPotionExpiresAt, data.xpPotionMultiplier)
    fun getProcMultiplier(data: PlayerData): Double = activeMultiplier(data.procPotionExpiresAt, data.procPotionMultiplier)
    fun getFortuneMultiplier(data: PlayerData): Double = activeMultiplier(data.fortunePotionExpiresAt, data.fortunePotionMultiplier)

    fun getRemainingMillis(data: PlayerData, buffType: BuffType): Long =
        (getExpiry(data, buffType) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun buyBuffItem(player: Player, buffType: BuffType): Boolean {
        val data = DataStore.get(player.uniqueId)
        if (data.tokens < buffType.tokenCost) {
            player.sendMessage(TextUtil.colorize("&cYou need &b${TextUtil.formatNum(buffType.tokenCost)} tokens &cfor ${buffType.displayName}."))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        data.tokens -= buffType.tokenCost
        val potion = ItemManager.makeBuffPotion(buffType)
        val leftovers = player.inventory.addItem(potion)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
        SessionTimelineManager.record(player, "Bought ${buffType.displayName} potion for ${TextUtil.formatNum(buffType.tokenCost)} tokens")
        ScoreboardManager.updateBoard(player)
        player.sendMessage(
            TextUtil.colorize(
                "&aBought &f${buffType.displayName} &apotion for &b${TextUtil.formatNum(buffType.tokenCost)} tokens&a. &7Right-click it to activate."
            )
        )
        player.playSound(player.location, "entity.item.pickup", 0.8f, 1.15f)
        return true
    }

    fun activateBuff(player: Player, buffType: BuffType) {
        val data = DataStore.get(player.uniqueId)
        val currentRemaining = getRemainingMillis(data, buffType)
        val newExpiry = System.currentTimeMillis() + currentRemaining + (buffType.durationMinutes * 60_000L)
        setState(data, buffType, max(currentMultiplier(data, buffType), buffType.multiplier), newExpiry)
        SessionTimelineManager.record(player, "Activated ${buffType.displayName} for ${buffType.durationMinutes}m")
        ScoreboardManager.updateBoard(player)
        player.sendMessage(
            TextUtil.colorize(
                "&aActivated &f${buffType.displayName}&a for &f${buffType.durationMinutes}m&a. Remaining: &f${formatDuration(getRemainingMillis(data, buffType))}"
            )
        )
        player.playSound(player.location, "entity.player.levelup", 0.7f, 1.2f)
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private fun activeMultiplier(expiry: Long, multiplier: Double): Double =
        if (expiry > System.currentTimeMillis()) multiplier.coerceAtLeast(1.0) else 1.0

    private fun currentMultiplier(data: PlayerData, buffType: BuffType): Double =
        when (buffType) {
            BuffType.MINE_RICHNESS -> data.minePotionMultiplier
            BuffType.XP -> data.xpPotionMultiplier
            BuffType.PROC -> data.procPotionMultiplier
            BuffType.FORTUNE -> data.fortunePotionMultiplier
        }

    private fun getExpiry(data: PlayerData, buffType: BuffType): Long =
        when (buffType) {
            BuffType.MINE_RICHNESS -> data.minePotionExpiresAt
            BuffType.XP -> data.xpPotionExpiresAt
            BuffType.PROC -> data.procPotionExpiresAt
            BuffType.FORTUNE -> data.fortunePotionExpiresAt
        }

    private fun setState(data: PlayerData, buffType: BuffType, multiplier: Double, expiry: Long) {
        when (buffType) {
            BuffType.MINE_RICHNESS -> {
                data.minePotionMultiplier = multiplier
                data.minePotionExpiresAt = expiry
            }
            BuffType.XP -> {
                data.xpPotionMultiplier = multiplier
                data.xpPotionExpiresAt = expiry
            }
            BuffType.PROC -> {
                data.procPotionMultiplier = multiplier
                data.procPotionExpiresAt = expiry
            }
            BuffType.FORTUNE -> {
                data.fortunePotionMultiplier = multiplier
                data.fortunePotionExpiresAt = expiry
            }
        }
    }
}
