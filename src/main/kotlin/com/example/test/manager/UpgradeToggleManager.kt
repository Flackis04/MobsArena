package com.example.test

import org.bukkit.Material
import org.bukkit.entity.Player

data class UpgradeToggleDefinition(
    val key: String,
    val displayName: String,
    val material: Material,
    val description: String
)

object UpgradeToggleManager {
    val definitions = listOf(
        UpgradeToggleDefinition("multiBreak", "Multi-Break", Material.DIAMOND_PICKAXE, "Break extra blocks while mining."),
        UpgradeToggleDefinition("oreBoost", "Ore Booster", Material.EMERALD, "Trigger Ore Boost bursts."),
        UpgradeToggleDefinition("fortune", "Fortune", Material.GOLD_INGOT, "Increase mining drops."),
        UpgradeToggleDefinition("excavator", "Excavator", Material.IRON_SHOVEL, "Trigger Excavator chains."),
        UpgradeToggleDefinition("lightning", "Lightning", Material.LIGHTNING_ROD, "Call down lightning procs."),
        UpgradeToggleDefinition("virtualJackhammer", "Jackhammer", Material.TNT, "Trigger virtual jackhammer clears."),
        UpgradeToggleDefinition("excavatorEfficiency", "Excavator Efficiency", Material.NETHERITE_SHOVEL, "Increase excavator extra blocks."),
        UpgradeToggleDefinition("xpGain", "XP Gain", Material.EXPERIENCE_BOTTLE, "Boost mining experience."),
        UpgradeToggleDefinition("oreFrequency", "Mine Richness", Material.EMERALD_ORE, "Generate richer mines on reset."),
        UpgradeToggleDefinition("scrollFinder", "Scroll Finder", Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, "Find upgrade scrolls while mining."),
        UpgradeToggleDefinition("backpack", "Backpack Storage", Material.CHEST, "Auto-store valuables in your backpack."),
        UpgradeToggleDefinition("sellMultiplier", "Sell Multiplier", Material.HOPPER, "Boost backpack sell value."),
        UpgradeToggleDefinition("tokenFinder", "Token Finder", Material.PRISMARINE_CRYSTALS, "Find bonus tokens while mining."),
        UpgradeToggleDefinition("keyFinder", "Key Finder", Material.TRIPWIRE_HOOK, "Find crate keys while mining."),
        UpgradeToggleDefinition("jackpot", "Jackpot", Material.TOTEM_OF_UNDYING, "Roll rare contraband drops."),
        UpgradeToggleDefinition("combo", "Combo Meter", Material.BLAZE_POWDER, "Build mining payout streaks."),
        UpgradeToggleDefinition("procPower", "Proc Power", Material.NETHER_STAR, "Boost proc chances on core enchants.")
    )

    private val definitionsByKey = definitions.associateBy { it.key }

    fun getDefinition(key: String): UpgradeToggleDefinition? = definitionsByKey[key]

    fun isEnabled(player: Player, key: String): Boolean = isEnabled(DataStore.get(player.uniqueId), key)

    fun isEnabled(data: PlayerData, key: String): Boolean =
        when (key) {
            "multiBreak" -> data.multiBreakEnabled
            "oreBoost" -> data.oreBoostEnabled
            "fortune" -> data.fortuneEnabled
            "excavator" -> data.excavatorEnabled
            "lightning" -> data.lightningEnabled
            "virtualJackhammer" -> data.virtualJackhammerEnabled
            "excavatorEfficiency" -> data.excavatorEfficiencyEnabled
            "xpGain" -> data.xpGainEnabled
            "oreFrequency" -> data.oreFrequencyEnabled
            "scrollFinder" -> data.scrollFinderEnabled
            "backpack" -> data.backpackEnabled
            "sellMultiplier" -> data.sellMultiplierEnabled
            "tokenFinder" -> data.tokenFinderEnabled
            "keyFinder" -> data.keyFinderEnabled
            "jackpot" -> data.jackpotEnabled
            "combo" -> data.comboEnabled
            "procPower" -> data.procPowerEnabled
            else -> true
        }

    fun setEnabled(data: PlayerData, key: String, enabled: Boolean) {
        when (key) {
            "multiBreak" -> data.multiBreakEnabled = enabled
            "oreBoost" -> data.oreBoostEnabled = enabled
            "fortune" -> data.fortuneEnabled = enabled
            "excavator" -> data.excavatorEnabled = enabled
            "lightning" -> data.lightningEnabled = enabled
            "virtualJackhammer" -> data.virtualJackhammerEnabled = enabled
            "excavatorEfficiency" -> data.excavatorEfficiencyEnabled = enabled
            "xpGain" -> data.xpGainEnabled = enabled
            "oreFrequency" -> data.oreFrequencyEnabled = enabled
            "scrollFinder" -> data.scrollFinderEnabled = enabled
            "backpack" -> data.backpackEnabled = enabled
            "sellMultiplier" -> data.sellMultiplierEnabled = enabled
            "tokenFinder" -> data.tokenFinderEnabled = enabled
            "keyFinder" -> data.keyFinderEnabled = enabled
            "jackpot" -> data.jackpotEnabled = enabled
            "combo" -> data.comboEnabled = enabled
            "procPower" -> data.procPowerEnabled = enabled
        }
    }

    fun getEffectiveLevel(data: PlayerData, key: String, actualLevel: Int): Int =
        if (isEnabled(data, key)) actualLevel else 1

    fun getProcPowerBonus(data: PlayerData): Double =
        if (!data.procPowerEnabled) 0.0 else UpgradeFormulas.getProcPowerChanceBonus(data.procPowerLevel, data.procPowerMaxLevel)

    fun resetToDefaults(data: PlayerData) {
        definitions.forEach { definition -> setEnabled(data, definition.key, true) }
    }
}
