package com.example.test

import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.floor

object ExperienceManager {
    private const val LEVELING_PACE_MULTIPLIER = 0.9

    private val valuableExperience = mapOf(
        Material.DEEPSLATE_GOLD_ORE to 3,
        Material.DEEPSLATE_DIAMOND_ORE to 5,
        Material.DEEPSLATE_EMERALD_ORE to 7,
        Material.AMETHYST_BLOCK to 10,
        Material.GOLD_BLOCK to 14,
        Material.REDSTONE_BLOCK to 18,
        Material.LAPIS_BLOCK to 22,
        Material.DIAMOND_BLOCK to 28,
        Material.EMERALD_BLOCK to 36,
        Material.ANCIENT_DEBRIS to 48,
        Material.NETHERITE_BLOCK to 64,
        Material.OBSIDIAN to 96,
        Material.CRYING_OBSIDIAN to 140,
        Material.MAGMA_BLOCK to 160,
        Material.SCULK to 170,
        Material.SCULK_CATALYST to 180,
        Material.RESPAWN_ANCHOR to 220,
        Material.BEACON to 320,
        Material.OCHRE_FROGLIGHT to 450,
        Material.VERDANT_FROGLIGHT to 650,
        Material.PEARLESCENT_FROGLIGHT to 900
    )

    fun clamp(player: Player) {
        if (player.level < 0) player.level = 0
        if (player.exp < 0f) player.exp = 0f
        if (player.exp > 1f) player.exp = 0f

        val data = DataStore.get(player.uniqueId)
        if (data.experienceBuffer.isNaN() || data.experienceBuffer.isInfinite()) {
            data.experienceBuffer = 0.0
        }
        if (data.experienceBuffer < 0.0) {
            data.experienceBuffer = 0.0
        }
    }

    fun restoreStoredLevel(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val storedLevel = data.level.coerceAtLeast(0)
        if (player.level != storedLevel) {
            player.level = storedLevel
        }
    }

    fun giveValuableExperience(player: Player, material: Material) {
        val baseExperience = getValuableExperience(material)
        if (baseExperience <= 0) return
        giveRawExperience(player, baseExperience.toDouble())
    }

    fun giveRawExperience(player: Player, baseExperience: Double) {
        if (baseExperience <= 0.0) return
        val data = DataStore.get(player.uniqueId)
        MasteryManager.recordActivation(player, "xpGain")
        val totalExperience = baseExperience *
            getExperienceMultiplier(data.xpGainLevel, data.xpGainMaxLevel, ScrollManager.getBonus(data, UpgradeScrollType.XP_GAIN)) *
            data.extraExperienceMultiplier *
            LEVELING_PACE_MULTIPLIER
        val wholeExperience = floor(totalExperience).toInt()
        val fractionalExperience = totalExperience - wholeExperience

        data.experienceBuffer += fractionalExperience
        var bufferedWhole = 0
        if (data.experienceBuffer >= 1.0) {
            bufferedWhole = floor(data.experienceBuffer).toInt()
            data.experienceBuffer -= bufferedWhole
        }

        val amountToGive = wholeExperience + bufferedWhole
        if (amountToGive > 0) {
            player.giveExp(amountToGive)
            data.level = player.level
        }
    }

    fun getValuableExperience(material: Material): Int = valuableExperience[material] ?: 0

    fun getExperienceMultiplier(level: Int, maxLevel: Int = LevelManager.xpGainMaxLevel, scrollBonus: Double = 0.0): Double =
        UpgradeFormulas.getExperienceMultiplier(level, maxLevel, scrollBonus)
}
