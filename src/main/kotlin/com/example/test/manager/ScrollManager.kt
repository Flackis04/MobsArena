package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

data class ScrollApplicationResult(val levelsAdded: Int = 0, val maxLevelsAdded: Int = 0) {
    val didApply: Boolean get() = levelsAdded > 0 || maxLevelsAdded > 0
}

private data class UpgradeEffectSnapshot(val level: Int, val maxLevel: Int)

enum class UpgradeScrollType(val id: String, val displayName: String) {
    LIGHTNING("lightning", "Lightning"),
    VIRTUAL_JACKHAMMER("virtualJackhammer", "Jackhammer"),
    XP_GAIN("xpGain", "XP Gain"),
    ORE_FREQUENCY("oreFrequency", "Ore Frequency");

    fun currentExtraLevels(data: PlayerData): Int = (currentMaxLevel(data) - baseMaxLevel(data)).coerceAtLeast(0)

    fun canReceiveMore(data: PlayerData): Boolean =
        currentLevel(data) < currentMaxLevel(data) || currentMaxLevel(data) < maxLevelWithScroll(data)

    fun applyScroll(data: PlayerData, amount: Int = 1): ScrollApplicationResult {
        val requested = amount.coerceAtLeast(0)
        if (requested <= 0) return ScrollApplicationResult()

        val currentLevel = currentLevel(data)
        val currentMaxLevel = currentMaxLevel(data)
        if (currentLevel < currentMaxLevel) {
            val appliedLevels = requested.coerceAtMost(currentMaxLevel - currentLevel)
            if (appliedLevels <= 0) return ScrollApplicationResult()
            setCurrentLevel(data, currentLevel + appliedLevels)
            return ScrollApplicationResult(levelsAdded = appliedLevels)
        }

        val appliedMaxLevels = requested.coerceAtMost(maxLevelWithScroll(data) - currentMaxLevel).coerceAtLeast(0)
        if (appliedMaxLevels <= 0) return ScrollApplicationResult()
        setCurrentMaxLevel(data, currentMaxLevel + appliedMaxLevels)
        return ScrollApplicationResult(maxLevelsAdded = appliedMaxLevels)
    }

    fun currentLevel(data: PlayerData): Int =
        when (this) {
            LIGHTNING -> data.lightningLevel
            VIRTUAL_JACKHAMMER -> data.virtualJackhammerLevel
            XP_GAIN -> data.xpGainLevel
            ORE_FREQUENCY -> data.oreFrequencyLevel
        }

    fun setCurrentLevel(data: PlayerData, level: Int) {
        when (this) {
            LIGHTNING -> data.lightningLevel = level
            VIRTUAL_JACKHAMMER -> data.virtualJackhammerLevel = level
            XP_GAIN -> data.xpGainLevel = level
            ORE_FREQUENCY -> data.oreFrequencyLevel = level
        }
    }

    fun currentMaxLevel(data: PlayerData): Int =
        when (this) {
            LIGHTNING -> data.lightningMaxLevel
            VIRTUAL_JACKHAMMER -> data.virtualJackhammerMaxLevel
            XP_GAIN -> data.xpGainMaxLevel
            ORE_FREQUENCY -> data.oreFrequencyMaxLevel
        }

    fun setCurrentMaxLevel(data: PlayerData, level: Int) {
        when (this) {
            LIGHTNING -> data.lightningMaxLevel = level
            VIRTUAL_JACKHAMMER -> data.virtualJackhammerMaxLevel = level
            XP_GAIN -> data.xpGainMaxLevel = level
            ORE_FREQUENCY -> data.oreFrequencyMaxLevel = level
        }
    }

    fun baseMaxLevel(data: PlayerData): Int =
        when (this) {
            LIGHTNING -> LevelManager.lightningMaxLevel
            VIRTUAL_JACKHAMMER -> LevelManager.virtualJackhammerMaxLevel
            XP_GAIN -> LevelManager.xpGainMaxLevel
            ORE_FREQUENCY -> LevelManager.oreFrequencyMaxLevel
        }

    fun maxLevelWithScroll(data: PlayerData): Int =
        when (this) {
            LIGHTNING -> LevelManager.lightningMaxLevelWithScroll
            VIRTUAL_JACKHAMMER -> LevelManager.virtualJackhammerMaxLevelWithScroll
            XP_GAIN -> LevelManager.xpGainMaxLevelWithScroll
            ORE_FREQUENCY -> LevelManager.oreFrequencyMaxLevelWithScroll
        }

    companion object {
        val upgradeMenuTypes = entries.toList()
        fun rollRandom(): UpgradeScrollType = upgradeMenuTypes.random(Random)
    }
}

enum class ScrollRarity(
    val id: String,
    val displayName: String,
    val color: String,
    val minBonus: Double,
    val maxBonus: Double,
    val material: Material
) {
    NORMAL("normal", "Normal", "&f", 0.005, 0.010, Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
    RARE("rare", "Rare", "&9", 0.010, 0.020, Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE),
    MYTHIC("mythic", "Mythic", "&5", 0.020, 0.050, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE),
    GOD("god", "God", "&6", 0.050, 0.100, Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
    SECRET("secret", "Secret", "&d", 0.100, 0.250, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);

    fun getGuaranteedLevels(): Int =
        when (this) {
            NORMAL, RARE -> 0
            MYTHIC -> 1
            GOD -> 2
            SECRET -> 5
        }

    fun rollChanceSuccess(): Boolean =
        when (this) {
            NORMAL -> Random.nextDouble() < 0.20
            RARE -> Random.nextDouble() < 0.50
            MYTHIC, GOD, SECRET -> true
        }
}

object ScrollManager : Listener {
    private const val MINED_SCROLL_NORMAL_WEIGHT = 80.0
    private const val MINED_SCROLL_RARE_WEIGHT = 15.0
    private const val MINED_SCROLL_MYTHIC_WEIGHT = 4.0
    private const val MINED_SCROLL_GOD_WEIGHT = 0.9
    private const val MINED_SCROLL_SECRET_WEIGHT = 0.1
    private val activeAnimations = mutableSetOf<java.util.UUID>()
    private val animationTicks = (0..14).map { it * 3L }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = event.item ?: return
        if (!ItemManager.isUpgradeScroll(item)) return

        event.isCancelled = true
        if (activeAnimations.contains(player.uniqueId)) return

        val rarity = ItemManager.getScrollRarity(item) ?: return
        val hand = event.hand ?: EquipmentSlot.HAND
        if (player.isSneaking) {
            applyScrollsInstantly(player, item, rarity, hand)
            return
        }

        applyAnimatedScroll(player, item, rarity, hand)
    }

    fun getBonus(data: PlayerData, type: UpgradeScrollType): Double = 0.0

    fun tryAwardMiningScroll(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val procChance = MineManager.applyProcMultiplier(
            UpgradeFormulas.getScrollFinderChance(data.scrollFinderLevel, data.scrollFinderMaxLevel),
            player
        )
        if (procChance <= 0.0 || Random.nextDouble() > procChance) return

        val rarity = rollMiningScrollRarity()
        val scroll = ItemManager.makeUpgradeScroll(rarity)
        val leftovers = player.inventory.addItem(scroll)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }

        player.sendMessage(TextUtil.colorize("${rarity.color}${rarity.displayName} Scroll &7found while mining."))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f + (rarity.ordinal * 0.15f))
    }

    private fun applyAnimatedScroll(player: Player, item: ItemStack, rarity: ScrollRarity, hand: EquipmentSlot) {
        if (item.amount <= 0) return
        if (!consumeScrollFromHand(player, hand, 1)) return
        activeAnimations += player.uniqueId
        val data = DataStore.get(player.uniqueId)
        val eligibleTypes = UpgradeScrollType.upgradeMenuTypes.filter { it.canReceiveMore(data) }
        val finalResult = rollScrollOutcome(data, rarity, eligibleTypes)

        animationTicks.forEachIndexed { index, tick ->
            Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
                if (!player.isOnline) {
                    activeAnimations.remove(player.uniqueId)
                    return@Runnable
                }

                val previewOutcome = if (index == animationTicks.lastIndex) {
                    finalResult
                } else {
                    rollScrollOutcome(data, rarity, UpgradeScrollType.upgradeMenuTypes)
                }

                TextUtil.showTitle(player, "&eScrolling...", buildRollText(rarity, previewOutcome), 0, 12, 0)
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.0f + (index * 0.015f))

                if (index == animationTicks.lastIndex) {
                    applyRolledOutcome(player, rarity, finalResult, 1)
                    activeAnimations.remove(player.uniqueId)
                }
            }, tick)
        }
    }

    private fun applyScrollsInstantly(player: Player, item: ItemStack, rarity: ScrollRarity, hand: EquipmentSlot) {
        val quantity = item.amount.coerceAtLeast(1)
        if (!consumeScrollFromHand(player, hand, quantity)) return
        val data = DataStore.get(player.uniqueId)
        val levelTotals = linkedMapOf<UpgradeScrollType, Int>()
        val maxLevelTotals = linkedMapOf<UpgradeScrollType, Int>()
        val levelStartSnapshots = mutableMapOf<UpgradeScrollType, UpgradeEffectSnapshot>()
        var failures = 0
        repeat(quantity) {
            val outcome = rollScrollOutcome(data, rarity, UpgradeScrollType.upgradeMenuTypes.filter { it.canReceiveMore(data) })
            if (outcome.levels <= 0 || outcome.type == null) {
                failures++
                return@repeat
            }
            if (outcome.type.currentLevel(data) < outcome.type.currentMaxLevel(data)) {
                levelStartSnapshots.putIfAbsent(
                    outcome.type,
                    UpgradeEffectSnapshot(
                        level = outcome.type.currentLevel(data),
                        maxLevel = outcome.type.currentMaxLevel(data)
                    )
                )
            }
            val result = outcome.type.applyScroll(data, outcome.levels)
            if (!result.didApply) {
                failures++
                return@repeat
            }
            if (result.levelsAdded > 0) {
                levelTotals[outcome.type] = (levelTotals[outcome.type] ?: 0) + result.levelsAdded
            }
            if (result.maxLevelsAdded > 0) {
                maxLevelTotals[outcome.type] = (maxLevelTotals[outcome.type] ?: 0) + result.maxLevelsAdded
            }
        }

        levelTotals.entries.sortedBy { it.key.displayName }.forEach { (type, totalLevels) ->
            val effectProgress = formatEffectProgress(type, data, levelStartSnapshots[type])
            player.sendMessage(
                TextUtil.colorize(
                    "${rarity.color}${rarity.displayName} ${type.displayName} Scroll${if (quantity > 1) " x$quantity" else ""} &7added &b+$totalLevels &7level${if (totalLevels == 1) "" else "s"}${effectProgress ?: ""}"
                )
            )
        }
        maxLevelTotals.entries.sortedBy { it.key.displayName }.forEach { (type, totalLevels) ->
            player.sendMessage(
                TextUtil.colorize(
                    "${rarity.color}${rarity.displayName} ${type.displayName} Scroll${if (quantity > 1) " x$quantity" else ""} &7added &b+$totalLevels &7max level${if (totalLevels == 1) "" else "s"}"
                )
            )
        }
        if (failures > 0) {
            player.sendMessage(TextUtil.colorize("&7${failures} scroll${if (failures == 1) "" else "s"} failed to upgrade anything."))
        }
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.15f)
        KitManager.refreshPickaxe(player)
    }

    private fun applyRolledOutcome(player: Player, rarity: ScrollRarity, outcome: ScrollOutcome, quantity: Int) {
        if (outcome.type == null || outcome.levels <= 0) {
            player.sendMessage(TextUtil.colorize("${rarity.color}${rarity.displayName} Scroll &7did not upgrade anything."))
            return
        }
        val data = DataStore.get(player.uniqueId)
        val startingSnapshot = if (outcome.type.currentLevel(data) < outcome.type.currentMaxLevel(data)) {
            UpgradeEffectSnapshot(
                level = outcome.type.currentLevel(data),
                maxLevel = outcome.type.currentMaxLevel(data)
            )
        } else {
            null
        }
        val result = outcome.type.applyScroll(data, outcome.levels)
        if (!result.didApply) {
            player.sendMessage(TextUtil.colorize("${rarity.color}${rarity.displayName} Scroll &7did not upgrade anything."))
            return
        }
        val effectProgress = if (result.levelsAdded > 0) {
            formatEffectProgress(outcome.type, data, startingSnapshot)
        } else {
            null
        }
        player.sendMessage(
            TextUtil.colorize(
                if (result.levelsAdded > 0) {
                    "${rarity.color}${rarity.displayName} ${outcome.type.displayName} Scroll${if (quantity > 1) " x$quantity" else ""} &7added &b+${result.levelsAdded} &7level${if (result.levelsAdded == 1) "" else "s"}${effectProgress ?: ""}"
                } else {
                    "${rarity.color}${rarity.displayName} ${outcome.type.displayName} Scroll${if (quantity > 1) " x$quantity" else ""} &7added &b+${result.maxLevelsAdded} &7max level${if (result.maxLevelsAdded == 1) "" else "s"}"
                }
            )
        )
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.15f)
        KitManager.refreshPickaxe(player)
    }

    private fun consumeScrollFromHand(player: Player, hand: EquipmentSlot, amount: Int): Boolean {
        val item = when (hand) {
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> player.inventory.itemInMainHand
        }
        if (!ItemManager.isUpgradeScroll(item)) return false
        if (item.amount < amount) return false
        if (item.amount == amount) {
            when (hand) {
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(null)
                else -> player.inventory.setItemInMainHand(null)
            }
        } else {
            item.amount -= amount
            when (hand) {
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(item)
                else -> player.inventory.setItemInMainHand(item)
            }
        }
        player.updateInventory()
        return true
    }

    private fun buildRollText(rarity: ScrollRarity, outcome: ScrollOutcome): String =
        if (outcome.type == null || outcome.levels <= 0) {
            "&cNo upgrade"
        } else {
            "${rarity.color}${rarity.displayName} ${outcome.type.displayName} &7+&b${outcome.levels}"
        }

    private fun formatEffectProgress(type: UpgradeScrollType, data: PlayerData, startingSnapshot: UpgradeEffectSnapshot?): String? {
        val start = startingSnapshot ?: return null
        val previousEffect = formatUpgradeEffect(type, data, start.level, start.maxLevel) ?: return null
        val currentEffect = formatUpgradeEffect(type, data, type.currentLevel(data), type.currentMaxLevel(data)) ?: return null
        if (previousEffect == currentEffect) return null
        return " &8(${previousEffect} &7-> ${currentEffect}&8)"
    }

    private fun formatUpgradeEffect(type: UpgradeScrollType, data: PlayerData, level: Int, maxLevel: Int): String? {
        fun formatDecimal(value: Number): String = "%.3f".format(value.toDouble())

        return when (type) {
            UpgradeScrollType.LIGHTNING ->
                "${formatDecimal(UpgradeFormulas.getLightningChance(level, maxLevel, getBonus(data, type)) * 100)}%"
            UpgradeScrollType.VIRTUAL_JACKHAMMER ->
                "${formatDecimal(UpgradeFormulas.getVirtualJackhammerChance(level, maxLevel, getBonus(data, type)) * 100)}%"
            UpgradeScrollType.XP_GAIN ->
                "${formatDecimal(ExperienceManager.getExperienceMultiplier(level, maxLevel, getBonus(data, type)))}x"
            UpgradeScrollType.ORE_FREQUENCY ->
                "${formatDecimal(MineManager.getOreFrequencyMultiplier(level, maxLevel, getBonus(data, type)))}x"
        }
    }

    private fun rollScrollOutcome(data: PlayerData, rarity: ScrollRarity, eligibleTypes: List<UpgradeScrollType>): ScrollOutcome {
        if (eligibleTypes.isEmpty()) return ScrollOutcome(null, 0)

        val guaranteed = rarity.getGuaranteedLevels()
        if (guaranteed > 0) {
            val type = eligibleTypes.random(Random)
            val available = (type.maxLevelWithScroll(data) - type.currentMaxLevel(data)).coerceAtLeast(0)
            return ScrollOutcome(type, guaranteed.coerceAtMost(available))
        }

        if (!rarity.rollChanceSuccess()) return ScrollOutcome(null, 0)
        return ScrollOutcome(eligibleTypes.random(Random), 1)
    }

    private fun rollMiningScrollRarity(): ScrollRarity {
        val roll = Random.nextDouble() * (
            MINED_SCROLL_NORMAL_WEIGHT +
                MINED_SCROLL_RARE_WEIGHT +
                MINED_SCROLL_MYTHIC_WEIGHT +
                MINED_SCROLL_GOD_WEIGHT +
                MINED_SCROLL_SECRET_WEIGHT
            )

        return when {
            roll < MINED_SCROLL_NORMAL_WEIGHT -> ScrollRarity.NORMAL
            roll < MINED_SCROLL_NORMAL_WEIGHT + MINED_SCROLL_RARE_WEIGHT -> ScrollRarity.RARE
            roll < MINED_SCROLL_NORMAL_WEIGHT + MINED_SCROLL_RARE_WEIGHT + MINED_SCROLL_MYTHIC_WEIGHT -> ScrollRarity.MYTHIC
            roll < MINED_SCROLL_NORMAL_WEIGHT + MINED_SCROLL_RARE_WEIGHT + MINED_SCROLL_MYTHIC_WEIGHT + MINED_SCROLL_GOD_WEIGHT -> ScrollRarity.GOD
            else -> ScrollRarity.SECRET
        }
    }

    private data class ScrollOutcome(val type: UpgradeScrollType?, val levels: Int)
}
