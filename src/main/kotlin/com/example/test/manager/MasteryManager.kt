package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToLong

object MasteryManager {
    private const val MAX_MASTERY_LEVEL = 7
    private const val ACTIVATION_CHANCE_BONUS_PER_TIER = 0.005
    private const val VALUABLE_SELL_BONUS_PER_TIER = 0.15
    private const val VALUABLE_MASTERY_BASE_MULTIPLIER = 5.0
    private const val VALUABLE_MASTERY_TIER_EXPONENT = 3.0
    private val excludedKeys = setOf("fortune", "excavatorEfficiency")
    private val supportedKeys = setOf(
        "oreBoost",
        "excavator",
        "lightning",
        "virtualJackhammer"
    )

    fun canOpenFor(key: String): Boolean = key in supportedKeys && key !in excludedKeys

    fun recordActivation(player: Player, key: String, amount: Long = 1L) {
        if (!canOpenFor(key) || amount <= 0L) return
        val data = DataStore.get(player.uniqueId)
        val nextTotal = data.masteryActivations.getOrDefault(key, 0L) + amount
        data.masteryActivations[key] = nextTotal
        val previousLevel = getMasteryLevel(data, key)
        val newLevel = updateUnlockedMasteries(data, key)
        if (newLevel > previousLevel) {
            announceUpgradeMasteryLevelUp(player, key, newLevel)
        }
    }

    fun recordActivation(playerId: UUID, key: String, amount: Long = 1L) {
        if (!canOpenFor(key) || amount <= 0L) return
        val data = DataStore.get(playerId)
        val nextTotal = data.masteryActivations.getOrDefault(key, 0L) + amount
        data.masteryActivations[key] = nextTotal
        val previousLevel = getMasteryLevel(data, key)
        val newLevel = updateUnlockedMasteries(data, key)
        if (newLevel > previousLevel) {
            Bukkit.getPlayer(playerId)?.let { announceUpgradeMasteryLevelUp(it, key, newLevel) }
        }
    }

    fun getMasteryLevel(data: PlayerData, key: String): Int =
        data.masteryLevels.getOrDefault(key, 0).coerceIn(0, MAX_MASTERY_LEVEL)

    fun getActivationCount(data: PlayerData, key: String): Long =
        data.masteryActivations.getOrDefault(key, 0L).coerceAtLeast(0L)

    fun getRequiredActivations(key: String, masteryLevel: Int): Long {
        val tier = masteryLevel.coerceIn(1, MAX_MASTERY_LEVEL)
        return baseRequirement(key) * tier.toLong() * tier.toLong()
    }

    fun getActivationChanceBonus(data: PlayerData, key: String): Double {
        val masteryLevel = getMasteryLevel(data, key)
        return when (key) {
            "lightning" -> listOf(1, 3, 5).count { masteryLevel >= it } * ACTIVATION_CHANCE_BONUS_PER_TIER
            "oreBoost",
            "excavator",
            "virtualJackhammer" -> masteryLevel * ACTIVATION_CHANCE_BONUS_PER_TIER
            else -> 0.0
        }
    }

    fun getLightningTierSkip(data: PlayerData): Int {
        val masteryLevel = getMasteryLevel(data, "lightning")
        return listOf(2, 4, 6).count { masteryLevel >= it }
    }

    fun hasLightningAreaUpgrade(data: PlayerData): Boolean = getMasteryLevel(data, "lightning") >= 7

    fun openGui(player: Player, upgrade: Upgrade) {
        val data = DataStore.get(player.uniqueId)
        val gui = Gui.gui()
            .title(Component.text("${stripColor(upgrade.displayName)} Mastery"))
            .rows(3)
            .disableAllInteractions()
            .create()

        for (slot in 10..16) {
            gui.setItem(slot, GuiItem(createMasteryItem(data, upgrade, slot - 9)))
        }

        gui.open(player)
    }

    fun openValuableGui(player: Player, material: Material) {
        if (material !in MineManager.valuableDrops) return
        val data = DataStore.get(player.uniqueId)
        updateUnlockedValuableMasteries(data, material)
        val gui = Gui.gui()
            .title(Component.text("${stripColor(material.name.replace('_', ' '))} Mastery"))
            .rows(3)
            .disableAllInteractions()
            .create()

        for (slot in 10..16) {
            gui.setItem(slot, GuiItem(createValuableMasteryItem(data, material, slot - 9)))
        }

        gui.open(player)
    }

    private fun createMasteryItem(data: PlayerData, upgrade: Upgrade, masteryTier: Int): ItemStack {
        val unlocked = getMasteryLevel(data, upgrade.key) >= masteryTier
        val totalActivations = getActivationCount(data, upgrade.key)
        val requiredActivations = getRequiredActivations(upgrade.key, masteryTier)
        val progressColor = if (unlocked) "&a" else "&7"
        val item = ItemStack(if (unlocked) Material.LIME_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE)

        item.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent("${if (unlocked) "&a" else "&7"}Mastery $masteryTier")
                    .decoration(TextDecoration.ITALIC, false)
            )
            meta.lore(
                listOf(
                    TextUtil.toComponent("${progressColor}Activations: &f${TextUtil.formatNum(totalActivations)}&7/&f${TextUtil.formatNum(requiredActivations)}")
                        .decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Reward: &f${getRewardDescription(upgrade.key, masteryTier)}")
                        .decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Status: ${if (unlocked) "&aUnlocked" else "&cLocked"}")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
        }
        return item
    }

    private fun createValuableMasteryItem(data: PlayerData, material: Material, masteryTier: Int): ItemStack {
        val unlocked = getValuableMasteryLevel(data, material) >= masteryTier
        val totalBlocksMined = getValuableBlocksMined(data, material)
        val requiredBlocksMined = getRequiredValuableBlocksMined(material, masteryTier)
        val progressColor = if (unlocked) "&a" else "&7"
        val item = ItemStack(if (unlocked) Material.LIME_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE)

        item.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent("${if (unlocked) "&a" else "&7"}Mastery $masteryTier")
                    .decoration(TextDecoration.ITALIC, false)
            )
            meta.lore(
                listOf(
                    TextUtil.toComponent("${progressColor}Collected: &f${TextUtil.formatNum(totalBlocksMined)}&7/&f${TextUtil.formatNum(requiredBlocksMined)}")
                        .decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Reward: &f+${TextUtil.formatNum(VALUABLE_SELL_BONUS_PER_TIER)}x sell value")
                        .decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Sell value at this tier: &b${TextUtil.formatNum(getValuableSellValueAtTier(material, masteryTier))} ${ItemManager.COIN_NAME_PLURAL}")
                        .decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Status: ${if (unlocked) "&aUnlocked" else "&cLocked"}")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
        }
        return item
    }

    private fun updateUnlockedMasteries(data: PlayerData, key: String): Int {
        var currentLevel = getMasteryLevel(data, key)
        while (currentLevel < MAX_MASTERY_LEVEL) {
            val nextLevel = currentLevel + 1
            if (getActivationCount(data, key) < getRequiredActivations(key, nextLevel)) break
            currentLevel = nextLevel
        }
        data.masteryLevels[key] = currentLevel
        return currentLevel
    }

    fun getValuableMasteryLevel(data: PlayerData, material: Material): Int {
        updateUnlockedValuableMasteries(data, material)
        return data.masteryLevels.getOrDefault(getValuableMasteryKey(material), 0).coerceIn(0, MAX_MASTERY_LEVEL)
    }

    fun getValuableBlocksMined(data: PlayerData, material: Material): Long =
        data.getCollectedAmount(material).coerceAtLeast(0L)

    fun getRequiredValuableBlocksMined(material: Material, masteryTier: Int): Long {
        val tier = masteryTier.coerceIn(1, MAX_MASTERY_LEVEL)
        val rarityProgress = getValuableRarityProgress(material)
        val longTermRarityMultiplier = 1.0 + (rarityProgress * 0.25 * ((tier - 1).toDouble() / (MAX_MASTERY_LEVEL - 1).toDouble()))
        return (baseValuableRequirement(material) * VALUABLE_MASTERY_BASE_MULTIPLIER * tier.toDouble().pow(VALUABLE_MASTERY_TIER_EXPONENT) * longTermRarityMultiplier)
            .roundToLong()
    }

    fun getValuableSellMultiplier(data: PlayerData, material: Material): Double =
        1.0 + (getValuableMasteryLevel(data, material) * VALUABLE_SELL_BONUS_PER_TIER)

    fun getValuableSellValue(data: PlayerData, material: Material): Long {
        val baseValue = StorageManager.getBaseSellValue(material) ?: return 0L
        return (baseValue * getValuableSellMultiplier(data, material)).roundToLong()
    }

    fun getValuableSellValueAtTier(material: Material, masteryTier: Int): Long {
        val baseValue = StorageManager.getBaseSellValue(material) ?: return 0L
        return (baseValue * (1.0 + (masteryTier.coerceIn(0, MAX_MASTERY_LEVEL) * VALUABLE_SELL_BONUS_PER_TIER))).roundToLong()
    }

    fun recordValuableCollection(player: Player, material: Material, amount: Int) {
        if (material !in MineManager.valuableDrops || amount <= 0) return
        val data = DataStore.get(player.uniqueId)
        data.addCollectedAmount(material, amount)
        val key = getValuableMasteryKey(material)
        val previousLevel = data.masteryLevels.getOrDefault(key, 0).coerceIn(0, MAX_MASTERY_LEVEL)
        val newLevel = updateUnlockedValuableMasteries(data, material)
        if (newLevel > previousLevel) {
            announceBlockMasteryLevelUp(player, material, newLevel)
        }
    }

    private fun updateUnlockedValuableMasteries(data: PlayerData, material: Material): Int {
        val key = getValuableMasteryKey(material)
        var currentLevel = data.masteryLevels.getOrDefault(key, 0).coerceIn(0, MAX_MASTERY_LEVEL)
        while (currentLevel < MAX_MASTERY_LEVEL) {
            val nextLevel = currentLevel + 1
            if (getValuableBlocksMined(data, material) < getRequiredValuableBlocksMined(material, nextLevel)) break
            currentLevel = nextLevel
        }
        data.masteryLevels[key] = currentLevel
        return currentLevel
    }

    private fun getRewardDescription(key: String, masteryTier: Int): String =
        when (key) {
            "lightning" -> when (masteryTier) {
                1, 3, 5 -> "+0.5% Lightning activation chance"
                2, 4, 6 -> "+1 extra Lightning tier jump"
                7 -> "Lightning upgrades a 5x5 area instead of 3x3"
                else -> "+0.5% Lightning activation chance"
            }
            "virtualJackhammer" -> when (masteryTier) {
                7 -> "Jackhammer clears 2 layers instead of 1"
                else -> "+0.5% activation chance"
            }
            else -> "+0.5% activation chance"
        }

    private fun baseRequirement(key: String): Long =
        when (key) {
            "oreBoost" -> 500L
            "excavator" -> 500L
            "lightning" -> 250L
            "virtualJackhammer" -> 100L
            else -> 2_500L
        }

    private fun getValuableMasteryKey(material: Material): String = "valuable:${material.name}"

    private fun baseValuableRequirement(material: Material): Long {
        val weight = getReversedValuableMasteryWeight(material) ?: return 1_000L
        val maxWeight = MineManager.valuableSpawnWeights.max()
        val relativeRarity = (maxWeight / weight).coerceAtLeast(1.0)
        return (750.0 * relativeRarity.pow(0.18)).roundToLong().coerceAtLeast(750L)
    }

    private fun getValuableRarityProgress(material: Material): Double {
        val weight = getReversedValuableMasteryWeight(material) ?: return 0.0
        val maxWeight = MineManager.valuableSpawnWeights.max()
        val minWeight = MineManager.valuableSpawnWeights.min()
        val relativeRarity = (maxWeight / weight).coerceAtLeast(1.0)
        val maxRelativeRarity = (maxWeight / minWeight).coerceAtLeast(1.0)
        return if (maxRelativeRarity <= 1.0) 0.0
        else ((relativeRarity - 1.0) / (maxRelativeRarity - 1.0)).coerceIn(0.0, 1.0)
    }

    private fun getReversedValuableMasteryWeight(material: Material): Double? {
        val index = MineManager.valuableDrops.indexOf(material)
        if (index == -1) return null
        val reversedIndex = MineManager.valuableDrops.lastIndex - index
        return MineManager.valuableSpawnWeights.getOrNull(reversedIndex)
    }

    private fun announceUpgradeMasteryLevelUp(player: Player, key: String, level: Int) {
        player.sendTitle(
            TextUtil.colorize("&d&lMastery Level Up"),
            TextUtil.colorize("&7${formatUpgradeName(key)} mastery is now &d$level&7."),
            10,
            50,
            10
        )
    }

    private fun announceBlockMasteryLevelUp(player: Player, material: Material, level: Int) {
        player.sendTitle(
            TextUtil.colorize("&6&lBlock Mastery Up"),
            TextUtil.colorize("&7${formatMaterialName(material)} mastery is now &6$level&7."),
            10,
            50,
            10
        )
    }

    private fun formatUpgradeName(key: String): String =
        when (key) {
            "oreBoost" -> "Ore Boost"
            "excavator" -> "Excavator"
            "lightning" -> "Lightning"
            "virtualJackhammer" -> "Jackhammer"
            else -> key
        }

    private fun formatMaterialName(material: Material): String =
        material.name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

    private fun stripColor(text: String): String {
        val colored = TextUtil.colorize(text)
        return colored.replace(Regex("(?i)[§&][0-9A-FK-ORX]"), "").replace("Â", "").trim()
    }
}
