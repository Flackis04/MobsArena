package com.example.test

import kotlin.random.Random

object SoundManager {
    const val ITEM_PICKUP_SOUND = "entity.item.pickup"
    const val EXPERIENCE_PICKUP_SOUND = "entity.experience_orb.pickup"
    const val LEVEL_UP_SOUND = "entity.player.levelup"
    const val LEVEL_UP_ACCENT_SOUND = "block.amethyst_block.chime"
    const val BREAK_IMPACT_VOLUME = 0.42f
    const val RARE_BREAK_VOLUME = 0.5f

    const val DEFAULT_VOLUME = 1f
    const val ITEM_PICKUP_VOLUME = 0.5f

    const val EXCAVATOR_BREAK_PITCH_MULTIPLIER = 0.92f
    const val ORE_BOOST_PICKUP_PITCH_MULTIPLIER = 1.5f

    const val VALUABLE_BREAK_MIN_PITCH = 0.88f
    const val VALUABLE_BREAK_MAX_PITCH = 1.06f

    fun getValuablePickupPitch(valuableIndex: Int, oreBoostActive: Boolean, excavatorActive: Boolean): Float {
        val pitchBase = (1 + valuableIndex / 9.0).toFloat()
        val oreBoostMultiplier = if (oreBoostActive) ORE_BOOST_PICKUP_PITCH_MULTIPLIER else 1f
        return pitchBase * oreBoostMultiplier
    }

    fun getBreakPitch(excavatorActive: Boolean): Float {
        val multiplier = if (excavatorActive) EXCAVATOR_BREAK_PITCH_MULTIPLIER else 1f

        val minPitch = VALUABLE_BREAK_MIN_PITCH * multiplier
        val maxPitch = VALUABLE_BREAK_MAX_PITCH * multiplier

        return Random.nextDouble(minPitch.toDouble(), maxPitch.toDouble()).toFloat()
    }

    fun getMineBreakSound(blockType: org.bukkit.Material): String = when {
        blockType == org.bukkit.Material.TRIAL_SPAWNER || blockType == org.bukkit.Material.SPAWNER -> "block.respawn_anchor.deplete"
        blockType in setOf(
            org.bukkit.Material.BEACON,
            org.bukkit.Material.PEARLESCENT_FROGLIGHT,
            org.bukkit.Material.VERDANT_FROGLIGHT,
            org.bukkit.Material.OCHRE_FROGLIGHT
        ) -> "block.amethyst_cluster.break"
        blockType in setOf(
            org.bukkit.Material.RESPAWN_ANCHOR,
            org.bukkit.Material.CRYING_OBSIDIAN,
            org.bukkit.Material.NETHERITE_BLOCK,
            org.bukkit.Material.ANCIENT_DEBRIS
        ) -> "block.amethyst_block.break"
        blockType in setOf(
            org.bukkit.Material.DEEPSLATE,
            org.bukkit.Material.COBBLED_DEEPSLATE,
            org.bukkit.Material.BLACKSTONE,
            org.bukkit.Material.BASALT
        ) -> "block.tuff.break"
        else -> "block.calcite.break"
    }

    fun getMineBreakVolume(blockType: org.bukkit.Material): Float =
        if (blockType in setOf(org.bukkit.Material.DEEPSLATE, org.bukkit.Material.COBBLED_DEEPSLATE)) BREAK_IMPACT_VOLUME else RARE_BREAK_VOLUME

    fun getRewardChimePitch(valuableIndex: Int, oreBoostActive: Boolean): Float {
        val basePitch = 0.85f + (valuableIndex.coerceAtLeast(0) * 0.025f)
        return if (oreBoostActive) basePitch + 0.12f else basePitch
    }

    fun getExperienceGainVolume(amount: Int): Float =
        (0.08f + amount.coerceAtMost(24) * 0.012f).coerceAtMost(0.34f)

    fun getExperienceGainPitch(amount: Int): Float =
        (0.75f + amount.coerceAtMost(24) * 0.018f).coerceAtMost(1.2f)

    fun getLevelUpMainPitch(newLevel: Int): Float =
        (1.0f + ((newLevel % 8) * 0.03f)).coerceAtMost(1.24f)

    fun getLevelUpAccentPitch(newLevel: Int): Float =
        (1.35f + ((newLevel % 8) * 0.02f)).coerceAtMost(1.55f)
}
