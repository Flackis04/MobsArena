package com.example.test

import com.example.test.UpgradeFormulas.getExcavatorChance
import com.example.test.UpgradeFormulas.getExcavatorEfficiency
import com.example.test.UpgradeFormulas.getLightningChance
import com.example.test.UpgradeFormulas.getMultiBreakMaxBlocks
import com.example.test.UpgradeFormulas.getOreBoostChance
import com.example.test.UpgradeFormulas.getVirtualJackhammerChance
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

private val openGuis: MutableMap<Player, GenericUpgradeGui> = mutableMapOf()
private val activeUpgradeViews: MutableMap<Player, Gui> = mutableMapOf()
private const val NON_RANK_UPGRADE_COST_MULTIPLIER = 0.9
private val PERM_UPGRADE_SLOTS = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21)

enum class Currency { TOKENS, BALANCE }

data class Upgrade(
    val key: String,
    var level: Int,
    val maxLevel: Int,
    var cost: Long,
    val currency: Currency,
    var displayItem: ItemStack,
    var lore: List<String>,
    var displayName: String,
    val onUpgrade: (player: Player) -> Unit
)

class GenericUpgradeGui(
    private val title: String,
    private val upgrades: List<Upgrade>
) : Listener {

    fun open(player: Player) {
        openGuis[player] = this
        val gui = buildGui(player)
        activeUpgradeViews[player] = gui
        gui.open(player)
    }

    private fun buildGui(player: Player): Gui {
        val gui = Gui.gui()
            .title(TextUtil.toComponent(title))
            .rows(6)
            .disableAllInteractions()
            .create()

        upgrades.forEachIndexed { index, upgrade ->
            val slot = upgradeSlot(index)
            upgrade.level = getPlayerLevel(player, upgrade.key)
            refreshUpgrade(player, upgrade)
            upgrade.lore = getDynamicUpgradeLore(player, upgrade.key, upgrade.level)
            gui.setItem(slot, GuiItem(createUpgradeItem(player, upgrade)) { event ->
                if (openGuis[player] !== this) return@GuiItem
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                if (event.isShiftClick && MasteryManager.canOpenFor(upgrade.key)) {
                    MasteryManager.openGui(player, upgrade)
                    return@GuiItem
                }
                val upgraded = processUpgrade(player, upgrade, event.isRightClick, event.isLeftClick)
                if (upgraded) {
                    refreshGui(player)
                }
            })
        }

        gui.setCloseGuiAction {
            val data = DataStore.get(player.uniqueId)
            if (!data.hasSeenUpgradeHint) {
                player.sendTitle(
                    TextUtil.colorize("&bMore to explore"),
                    TextUtil.colorize("&7If you ever get stuck or need more features, check out &f/spawn&7!"),
                    10,
                    70,
                    10
                )
                data.hasSeenUpgradeHint = true
            }
            openGuis.remove(player)
            activeUpgradeViews.remove(player)
            GuiClickDebounce.clear(player)
        }
        return gui
    }

    private fun refreshGui(player: Player) {
        val gui = activeUpgradeViews[player] ?: return
        upgrades.forEachIndexed { index, upgrade ->
            val slot = upgradeSlot(index)
            upgrade.level = getPlayerLevel(player, upgrade.key)
            refreshUpgrade(player, upgrade)
            upgrade.lore = getDynamicUpgradeLore(player, upgrade.key, upgrade.level)
            gui.updateItem(slot, GuiItem(createUpgradeItem(player, upgrade)) { event ->
                if (openGuis[player] !== this) return@GuiItem
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                if (event.isShiftClick && MasteryManager.canOpenFor(upgrade.key)) {
                    MasteryManager.openGui(player, upgrade)
                    return@GuiItem
                }
                val upgraded = processUpgrade(player, upgrade, event.isRightClick, event.isLeftClick)
                if (upgraded) {
                    refreshGui(player)
                }
            })
        }
        gui.update()
    }

    private fun processUpgrade(player: Player, upgrade: Upgrade, buyMax: Boolean, buySingle: Boolean): Boolean {
        if (!buyMax && !buySingle) return false

        val playerData = DataStore.get(player.uniqueId)

        upgrade.level = getPlayerLevel(player, upgrade.key)
        upgrade.cost = getUpgradeCost(upgrade.key, upgrade.level)

        if (upgrade.level >= upgrade.maxLevel) {
            player.sendMessage(TextUtil.toComponent("&cThis upgrade is already maxed!"))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        if (requiresRebirth(upgrade.key) && playerData.rebirth < 1) {
            player.sendMessage(TextUtil.toComponent("&cYou need rebirth 1 before buying this upgrade."))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        var purchases = 0
        while (upgrade.level < upgrade.maxLevel) {
            val currentCost = getUpgradeCost(upgrade.key, upgrade.level)
            val hasEnough = when (upgrade.currency) {
                Currency.TOKENS -> playerData.tokens >= currentCost
                Currency.BALANCE -> playerData.balance >= currentCost
            }

            if (!hasEnough) break

            when (upgrade.currency) {
                Currency.TOKENS -> playerData.tokens -= currentCost
                Currency.BALANCE -> playerData.balance -= currentCost
            }

            upgrade.onUpgrade(player)
            upgrade.level = getPlayerLevel(player, upgrade.key)
            upgrade.cost = getUpgradeCost(upgrade.key, upgrade.level)
            purchases += 1

            if (!buyMax) break
        }

        if (purchases == 0) {
            player.sendMessage(TextUtil.toComponent("&cYou don't have enough ${if (upgrade.currency == Currency.TOKENS) "Tokens" else "Coins"}!"))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        KitManager.refreshPickaxe(player)
        player.playSound(player.location, "entity.player.levelup", 1f, 1f)
        ScoreboardManager.updateBoard(player)

        return true
    }

    private fun createUpgradeItem(player: Player, upgrade: Upgrade): ItemStack {
        val requirementLine =
            if (requiresRebirth(upgrade.key) && DataStore.get(player.uniqueId).rebirth < 1) {
                "&cRequires rebirth 1"
            } else {
                null
            }
        val statusLine =
            if (upgrade.level >= upgrade.maxLevel) {
                "&8- Maxed"
            } else {
                "${getCostColor(player, upgrade)}Cost: &b${TextUtil.formatNum(upgrade.cost)} ${
                    if (upgrade.currency == Currency.TOKENS) "&6Tokens" else ItemManager.COIN_NAME_PLURAL
                }"
            }
        val scrollsAppliedLine = getScrollsAppliedLine(player, upgrade.key)

        val clickLines =
            if (upgrade.level >= upgrade.maxLevel) {
                if (MasteryManager.canOpenFor(upgrade.key)) listOf("&7Shift-click: &dView mastery") else emptyList()
            } else {
                val lines = mutableListOf("&7Left-click: &fBuy 1", "&7Right-click: &fBuy max")
                if (MasteryManager.canOpenFor(upgrade.key)) {
                    lines += "&7Shift-click: &dView mastery"
                }
                lines
            }

        val fullLore = mutableListOf<String>()
        fullLore += upgrade.lore
        if (requirementLine != null) {
            fullLore += requirementLine
        }
        fullLore += statusLine
        if (scrollsAppliedLine != null) {
            fullLore += scrollsAppliedLine
        }
        fullLore += clickLines

        val item = upgrade.displayItem.clone()
        item.editMeta { meta ->
            meta.displayName(TextUtil.toComponent(upgrade.displayName).decoration(TextDecoration.ITALIC, false))
            meta.lore(fullLore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
        }
        return item
    }

    private fun getCostColor(player: Player, upgrade: Upgrade): String {
        val playerData = DataStore.get(player.uniqueId)
        val affordable = when (upgrade.currency) {
            Currency.TOKENS -> playerData.tokens >= upgrade.cost
            Currency.BALANCE -> playerData.balance >= upgrade.cost
        }
        return if (affordable) "&a" else "&c"
    }

    private fun getPlayerLevel(player: Player, key: String): Int {
        val data = DataStore.get(player.uniqueId)
        return when (key) {
            "multiBreak" -> data.multiBreakLevel
            "oreBoost" -> data.oreBoostLevel
            "fortune" -> data.fortuneLevel
            "excavator" -> data.excavatorLevel
            "lightning" -> data.lightningLevel
            "virtualJackhammer" -> data.virtualJackhammerLevel
            "excavatorEfficiency" -> data.excavatorEfficiencyLevel
            "xpGain" -> data.xpGainLevel
            "oreFrequency" -> data.oreFrequencyLevel
            "scrollFinder" -> data.scrollFinderLevel
            "autominerFortune" -> data.autoMinerFortuneLevel
            "autominerEfficiency" -> data.autoMinerEfficiencyLevel
            "energyDrink" -> data.autoMinerEnergyDrinkLevel
            "autominerBackpack" -> data.autoMinerBackpackLevel
            "autominerLuck" -> data.autoMinerLuckLevel
            "rank" -> data.rank
            else -> 0
        }
    }

    private fun refreshUpgrade(player: Player, upgrade: Upgrade) {
        upgrade.level = getPlayerLevel(player, upgrade.key)
        upgrade.cost = getUpgradeCost(upgrade.key, upgrade.level)

        upgrade.displayItem = when (upgrade.key) {
            "rank" -> TierManager.makeTierHead(upgrade.level)
            "multiBreak" -> ItemStack(Material.DIAMOND_PICKAXE)
            "oreBoost" -> ItemStack(Material.RAW_GOLD)
            "fortune" -> ItemStack(Material.DIAMOND)
            "excavator" -> ItemStack(Material.BEACON)
            "lightning" -> ItemStack(Material.LIGHTNING_ROD)
            "virtualJackhammer" -> ItemStack(Material.NETHERITE_PICKAXE)
            "excavatorEfficiency" -> ItemStack(Material.SUGAR)
            "xpGain" -> ItemStack(Material.EXPERIENCE_BOTTLE)
            "oreFrequency" -> ItemStack(Material.EMERALD)
            "scrollFinder" -> ItemStack(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE)
            "autominerFortune" -> ItemStack(Material.DIAMOND)
            "autominerEfficiency" -> ItemStack(Material.HOPPER)
            "energyDrink" -> ItemStack(Material.POTION)
            "autominerBackpack" -> ItemStack(Material.CHEST)
            "autominerLuck" -> ItemStack(Material.RABBIT_FOOT)
            else -> upgrade.displayItem
        }

        upgrade.displayName = when (upgrade.key) {
            "rank" -> {
                val tier = TierManager.getTier(upgrade.level)
                tier?.let { "${it.color}&lT${upgrade.level} ${it.name}" } ?: "&cMissing Head"
            }
            "multiBreak" -> "&bMulti-Break - Lvl ${upgrade.level}"
            "oreBoost" -> "&bOre Booster - Lvl ${upgrade.level}"
            "fortune" -> "&dFortune - Lvl ${upgrade.level}"
            "excavator" -> "&7Excavator - Lvl ${upgrade.level}"
            "lightning" -> "&eLightning - Lvl ${upgrade.level}"
            "virtualJackhammer" -> "&6Jackhammer - Lvl ${upgrade.level}"
            "excavatorEfficiency" -> "&7Excavator Efficiency - Lvl ${upgrade.level}"
            "xpGain" -> "&aXP Gain - Lvl ${upgrade.level}"
            "oreFrequency" -> "&2Ore Frequency - Lvl ${upgrade.level}"
            "scrollFinder" -> "&dScroll Finder - Lvl ${upgrade.level}"
            "autominerFortune" -> "&bAuto Miner Fortune - Lvl ${upgrade.level}"
            "autominerEfficiency" -> "&eAuto Miner Efficiency - Lvl ${upgrade.level}"
            "energyDrink" -> "&6Energy Drink - Lvl ${upgrade.level}"
            "autominerBackpack" -> "&aAuto Miner Backpack - Lvl ${upgrade.level}"
            "autominerLuck" -> "&dAuto Miner Luck - Lvl ${upgrade.level}"
            else -> upgrade.displayName
        }
    }
}

private fun getScrollsAppliedLine(player: Player, key: String): String? {
    val data = DataStore.get(player.uniqueId)
    val scrollType = when (key) {
        "lightning" -> UpgradeScrollType.LIGHTNING
        "virtualJackhammer" -> UpgradeScrollType.VIRTUAL_JACKHAMMER
        "xpGain" -> UpgradeScrollType.XP_GAIN
        "oreFrequency" -> UpgradeScrollType.ORE_FREQUENCY
        else -> null
    } ?: return null

    val applied = scrollType.currentExtraLevels(data)
    if (applied <= 0) return null
    return "&dScrolls applied: &f+$applied"
}

private fun upgradeSlot(index: Int): Int = PERM_UPGRADE_SLOTS.getOrElse(index) { 10 + index }

fun removeItem(player: Player, material: Material) {
    val inv = player.inventory
    for (i in inv.contents.indices) {
        val item = inv.contents[i] ?: continue
        if (item.type == material) {
            inv.clear(i)
        }
    }
}

fun getDynamicUpgradeLore(player: Player, key: String, level: Int): List<String> {
    fun formatDecimal(value: Number): String = "%.3f".format(value.toDouble())

    return when (key) {
        "multiBreak" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.multiBreakMaxLevel
            val boost = if (ItemManager.isProcBooster(player.inventory.itemInOffHand)) 3.0 else 0.0
            val current = getMultiBreakMaxBlocks(level, maxLevel) + boost
            val next = getMultiBreakMaxBlocks((level + 1).coerceAtMost(maxLevel), maxLevel) + boost
            listOf(
                "&8Chance to break extra blocks",
                "&7Extra blocks broken cap: &b${formatDecimal(current)} -> ${formatDecimal(next)}"
            )
        }
        "oreBoost" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.oreBoostMaxLevel
            val nextLevel = (level + 1).coerceAtMost(maxLevel)
            val next = getOreBoostChance(nextLevel, maxLevel, 0.0) * 100
            val currentAdjusted = getOreBoostChance(level, maxLevel, 0.0) * 100
            listOf("&8Chance for ores to duplicate for 5 seconds", "&7Activation chance: &b${formatDecimal(currentAdjusted)}% -> ${formatDecimal(next)}%")
        }
        "excavator" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.excavatorMaxLevel
            val current = getExcavatorChance(level, maxLevel, 0.0) * 100
            val next = getExcavatorChance((level + 1).coerceAtMost(maxLevel), maxLevel, 0.0) * 100
            listOf("&8Chance to apply a multiplier to the blocks broken from MultiBreak", "&7Activation chance: &b${formatDecimal(current)}% -> ${formatDecimal(next)}%")
        }
        "lightning" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.lightningMaxLevel
            val current = getLightningChance(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.LIGHTNING)) * 100
            val next = getLightningChance((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.LIGHTNING)) * 100
            listOf(
                "&8Chance to strike a random mine column with lightning",
                "&8Upgrades the struck block and its 3x3 area by one tier",
                "&7Activation chance: &b${formatDecimal(current)}% -> ${formatDecimal(next)}%"
            )
        }
        "virtualJackhammer" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.virtualJackhammerMaxLevel
            val current = getVirtualJackhammerChance(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.VIRTUAL_JACKHAMMER)) * 100
            val next = getVirtualJackhammerChance((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.VIRTUAL_JACKHAMMER)) * 100
            listOf(
                "&8Super rare chance to scan one full mine layer",
                "&8Pays out every valuable on that Y level without clearing blocks",
                "&7Activation chance: &b${formatDecimal(current)}% -> ${formatDecimal(next)}%"
            )
        }
        "excavatorEfficiency" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.excavatorEfficiencyMaxLevel
            val current = getExcavatorEfficiency(level, maxLevel, 0.0)
            val next = getExcavatorEfficiency((level + 1).coerceAtMost(maxLevel), maxLevel, 0.0)
            listOf("&8Increases the excavator multiplier", "&7Multiplier: &b${formatDecimal(current)} -> ${formatDecimal(next)}")
        }
        "xpGain" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.xpGainMaxLevel
            val current = ExperienceManager.getExperienceMultiplier(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.XP_GAIN))
            val next = ExperienceManager.getExperienceMultiplier((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.XP_GAIN))
            listOf("&8Adds a multiplier on top of your xp gained", "&7XP Multiplier: &b${formatDecimal(current)}x -> ${formatDecimal(next)}x")
        }
        "oreFrequency" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.oreFrequencyMaxLevel
            val current = MineManager.getOreFrequencyMultiplier(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.ORE_FREQUENCY))
            val next = MineManager.getOreFrequencyMultiplier((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.ORE_FREQUENCY))
            listOf(
                "&8Increases valuable ore frequency in your personal mine",
                "&7Valuable weight multiplier: &b${formatDecimal(current)}x -> ${formatDecimal(next)}x"
            )
        }
        "scrollFinder" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.scrollFinderMaxLevel
            val current = UpgradeFormulas.getScrollFinderChance(level, maxLevel) * 100
            val next = UpgradeFormulas.getScrollFinderChance((level + 1).coerceAtMost(maxLevel), maxLevel) * 100
            listOf(
                "&8Very small chance to find a scroll while mining",
                "&7Find chance: &b${formatDecimal(current)}% -> ${formatDecimal(next)}%"
            )
        }
        "fortune" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.fortuneMaxLevel
            val current = UpgradeFormulas.getFortuneMultiplier(level, maxLevel, 0.0)
            val next = UpgradeFormulas.getFortuneMultiplier((level + 1).coerceAtMost(maxLevel), maxLevel, 0.0)
            listOf("&8Increase the drop rate of blocks", "&7Drop multiplier: &b${formatDecimal(current)}x -> ${formatDecimal(next)}x")
        }
        "autominerFortune" -> {
            val current = UpgradeFormulas.getFortuneMultiplier(level, LevelManager.autoMinerFortuneMaxLevel)
            val next = UpgradeFormulas.getFortuneMultiplier((level + 1).coerceAtMost(LevelManager.autoMinerFortuneMaxLevel), LevelManager.autoMinerFortuneMaxLevel)
            listOf("&8Uses the same fortune roll as your pickaxe", "&7Drop multiplier: &b${formatDecimal(current)}x -> ${formatDecimal(next)}x")
        }
        "autominerEfficiency" -> {
            val current = AutoMinerManager.getProcessingAttempts(level)
            val next = AutoMinerManager.getProcessingAttempts(level + 1)
            listOf("&8Controls how many mine blocks are processed every second", "&7Blocks/sec: &b${formatDecimal(current)} -> ${formatDecimal(next)}")
        }
        "energyDrink" -> {
            val current = AutoMinerManager.getOfflineYieldRate(level) * 100
            val next = AutoMinerManager.getOfflineYieldRate(level + 1) * 100
            listOf("&8Keeps your autominer earning while you are offline", "&7Offline pace: &b${formatDecimal(current)}% -> ${formatDecimal(next)}%")
        }
        "autominerBackpack" -> {
            val current = (level.coerceAtLeast(1).toLong() * 10_000L).coerceAtMost(2_500_000L)
            val next = ((level + 1).coerceAtLeast(1).toLong() * 10_000L).coerceAtMost(2_500_000L)
            listOf("&8Increases how many items your autominer can hold", "&7Capacity: &b${formatDecimal(current)} -> ${formatDecimal(next)}")
        }
        "autominerLuck" -> {
            val current = AutoMinerManager.getLuckMultiplier(level)
            val next = AutoMinerManager.getLuckMultiplier((level + 1).coerceAtMost(LevelManager.autoMinerLuckMaxLevel))
            listOf(
                "&8Makes mine valuables more likely in autominer rolls",
                "&7Valuable weight multiplier: &b${formatDecimal(current)}x -> ${formatDecimal(next)}x"
            )
        }
        else -> listOf()
    }
}

private fun getUpgradeCost(key: String, currentLevel: Int): Long {
    val nextLevel = currentLevel + 1
    return when (key) {
        "rank" -> LevelManager.upgradeRankCosts[nextLevel] ?: 0
        "multiBreak" -> getDiscountedUpgradeCost(LevelManager.upgradeMultiBreakCosts[nextLevel])
        "oreBoost" -> getDiscountedUpgradeCost(LevelManager.upgradeOreBoostCosts[nextLevel])
        "fortune" -> getDiscountedUpgradeCost(LevelManager.upgradeFortuneCosts[nextLevel])
        "excavator" -> getDiscountedUpgradeCost(LevelManager.upgradeExcavatorCosts[nextLevel])
        "lightning" -> getDiscountedUpgradeCost(LevelManager.upgradeLightningCosts[nextLevel])
        "virtualJackhammer" -> getDiscountedUpgradeCost(LevelManager.upgradeVirtualJackhammerCosts[nextLevel])
        "excavatorEfficiency" -> getDiscountedUpgradeCost(LevelManager.upgradeExcavatorEfficiencyCosts[nextLevel])
        "xpGain" -> getDiscountedUpgradeCost(LevelManager.upgradeXpGainCosts[nextLevel])
        "oreFrequency" -> getDiscountedUpgradeCost(LevelManager.upgradeOreFrequencyCosts[nextLevel])
        "scrollFinder" -> getDiscountedUpgradeCost(LevelManager.upgradeScrollFinderCosts[nextLevel])
        "autominerFortune" -> getDiscountedUpgradeCost(LevelManager.upgradeAutoMinerFortuneCosts[nextLevel])
        "autominerEfficiency" -> getDiscountedUpgradeCost(LevelManager.upgradeAutoMinerEfficiencyCosts[nextLevel])
        "energyDrink" -> getDiscountedUpgradeCost(LevelManager.upgradeAutoMinerEnergyDrinkCosts[nextLevel])
        "autominerBackpack" -> getDiscountedUpgradeCost(LevelManager.upgradeAutoMinerBackpackCosts[nextLevel])
        "autominerLuck" -> getDiscountedUpgradeCost(LevelManager.upgradeAutoMinerLuckCosts[nextLevel])
        else -> 0
    }
}

private fun getDiscountedUpgradeCost(baseCost: Long?): Long {
    val cost = baseCost ?: return 0L
    return kotlin.math.ceil(cost * NON_RANK_UPGRADE_COST_MULTIPLIER).toLong().coerceAtLeast(1L)
}


fun openPermUpgradeGui(player: Player) {
    val data = DataStore.get(player.uniqueId)

    val permUpgrades = listOf(
        Upgrade("multiBreak", data.multiBreakLevel, data.multiBreakMaxLevel,
            getUpgradeCost("multiBreak", data.multiBreakLevel), Currency.BALANCE,
            ItemStack(Material.DIAMOND_PICKAXE),
            getDynamicUpgradeLore(player, "multiBreak", data.multiBreakLevel),
            "&eMulti-Break") { p -> DataStore.get(p.uniqueId).multiBreakLevel += 1 },

        Upgrade("oreBoost", data.oreBoostLevel, data.oreBoostMaxLevel,
            getUpgradeCost("oreBoost", data.oreBoostLevel), Currency.BALANCE,
            ItemStack(Material.RAW_GOLD),
            getDynamicUpgradeLore(player, "oreBoost", data.oreBoostLevel),
            "&bOre Booster") { p -> DataStore.get(p.uniqueId).oreBoostLevel += 1 },

        Upgrade("fortune", data.fortuneLevel, data.fortuneMaxLevel,
            getUpgradeCost("fortune", data.fortuneLevel), Currency.BALANCE,
            ItemStack(Material.DIAMOND),
            getDynamicUpgradeLore(player, "fortune", data.fortuneLevel),
            "&dFortune") { p ->
            val pdata = DataStore.get(p.uniqueId)
            pdata.fortuneLevel += 1
            removeItem(p, Material.DIAMOND_PICKAXE)
            KitManager.givePickaxe(p)
        },

        Upgrade("excavator", data.excavatorLevel, data.excavatorMaxLevel,
            getUpgradeCost("excavator", data.excavatorLevel), Currency.BALANCE,
            ItemStack(Material.BEACON),
            getDynamicUpgradeLore(player, "excavator", data.excavatorLevel),
            "&7Excavator") { p -> DataStore.get(p.uniqueId).excavatorLevel += 1 },

        Upgrade("lightning", data.lightningLevel, data.lightningMaxLevel,
            getUpgradeCost("lightning", data.lightningLevel), Currency.BALANCE,
            ItemStack(Material.LIGHTNING_ROD),
            getDynamicUpgradeLore(player, "lightning", data.lightningLevel),
            "&eLightning") { p -> DataStore.get(p.uniqueId).lightningLevel += 1 },

        Upgrade("virtualJackhammer", data.virtualJackhammerLevel, data.virtualJackhammerMaxLevel,
            getUpgradeCost("virtualJackhammer", data.virtualJackhammerLevel), Currency.BALANCE,
            ItemStack(Material.NETHERITE_PICKAXE),
            getDynamicUpgradeLore(player, "virtualJackhammer", data.virtualJackhammerLevel),
            "&6Jackhammer") { p -> DataStore.get(p.uniqueId).virtualJackhammerLevel += 1 },

        Upgrade("excavatorEfficiency", data.excavatorEfficiencyLevel, data.excavatorEfficiencyMaxLevel,
            getUpgradeCost("excavatorEfficiency", data.excavatorEfficiencyLevel), Currency.BALANCE,
            ItemStack(Material.SUGAR),
            getDynamicUpgradeLore(player, "excavatorEfficiency", data.excavatorEfficiencyLevel),
            "&7&lExcavator Efficiency") { p -> DataStore.get(p.uniqueId).excavatorEfficiencyLevel += 1 },

        Upgrade("xpGain", data.xpGainLevel, data.xpGainMaxLevel,
            getUpgradeCost("xpGain", data.xpGainLevel), Currency.BALANCE,
            ItemStack(Material.EXPERIENCE_BOTTLE),
            getDynamicUpgradeLore(player, "xpGain", data.xpGainLevel),
            "&aXP Gain") { p -> DataStore.get(p.uniqueId).xpGainLevel += 1 },

        Upgrade("oreFrequency", data.oreFrequencyLevel, data.oreFrequencyMaxLevel,
            getUpgradeCost("oreFrequency", data.oreFrequencyLevel), Currency.BALANCE,
            ItemStack(Material.EMERALD),
            getDynamicUpgradeLore(player, "oreFrequency", data.oreFrequencyLevel),
            "&2Ore Frequency") { p -> DataStore.get(p.uniqueId).oreFrequencyLevel += 1 },

        Upgrade("scrollFinder", data.scrollFinderLevel, data.scrollFinderMaxLevel,
            getUpgradeCost("scrollFinder", data.scrollFinderLevel), Currency.BALANCE,
            ItemStack(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
            getDynamicUpgradeLore(player, "scrollFinder", data.scrollFinderLevel),
            "&dScroll Finder") { p -> DataStore.get(p.uniqueId).scrollFinderLevel += 1 }
    )

    val gui = GenericUpgradeGui("&8ᴘᴇʀᴍᴀɴᴇɴᴛ &bᴜᴘɢʀᴀᴅᴇs", permUpgrades)
    openGuis[player] = gui
    gui.open(player)
}

fun openAutoMinerUpgradeGui(player: Player) {
    val data = DataStore.get(player.uniqueId)

    val upgrades = listOf(
        Upgrade(
            "autominerFortune",
            data.autoMinerFortuneLevel,
            data.autoMinerFortuneMaxLevel,
            getUpgradeCost("autominerFortune", data.autoMinerFortuneLevel),
            Currency.BALANCE,
            ItemStack(Material.DIAMOND),
            getDynamicUpgradeLore(player, "autominerFortune", data.autoMinerFortuneLevel),
            "&bAuto Miner Fortune"
        ) { p -> DataStore.get(p.uniqueId).autoMinerFortuneLevel += 1 },
        Upgrade(
            "autominerEfficiency",
            data.autoMinerEfficiencyLevel,
            data.autoMinerEfficiencyMaxLevel,
            getUpgradeCost("autominerEfficiency", data.autoMinerEfficiencyLevel),
            Currency.BALANCE,
            ItemStack(Material.HOPPER),
            getDynamicUpgradeLore(player, "autominerEfficiency", data.autoMinerEfficiencyLevel),
            "&eAuto Miner Efficiency"
        ) { p -> DataStore.get(p.uniqueId).autoMinerEfficiencyLevel += 1 },
        Upgrade(
            "energyDrink",
            data.autoMinerEnergyDrinkLevel,
            data.autoMinerEnergyDrinkMaxLevel,
            getUpgradeCost("energyDrink", data.autoMinerEnergyDrinkLevel),
            Currency.BALANCE,
            ItemStack(Material.POTION),
            getDynamicUpgradeLore(player, "energyDrink", data.autoMinerEnergyDrinkLevel),
            "&6Energy Drink"
        ) { p -> DataStore.get(p.uniqueId).autoMinerEnergyDrinkLevel += 1 },
        Upgrade(
            "autominerBackpack",
            data.autoMinerBackpackLevel,
            data.autoMinerBackpackMaxLevel,
            getUpgradeCost("autominerBackpack", data.autoMinerBackpackLevel),
            Currency.BALANCE,
            ItemStack(Material.CHEST),
            getDynamicUpgradeLore(player, "autominerBackpack", data.autoMinerBackpackLevel),
            "&aAuto Miner Backpack"
        ) { p -> DataStore.get(p.uniqueId).autoMinerBackpackLevel += 1 },
        Upgrade(
            "autominerLuck",
            data.autoMinerLuckLevel,
            data.autoMinerLuckMaxLevel,
            getUpgradeCost("autominerLuck", data.autoMinerLuckLevel),
            Currency.BALANCE,
            ItemStack(Material.RABBIT_FOOT),
            getDynamicUpgradeLore(player, "autominerLuck", data.autoMinerLuckLevel),
            "&dAuto Miner Luck"
        ) { p -> DataStore.get(p.uniqueId).autoMinerLuckLevel += 1 }
    )

    val gui = GenericUpgradeGui("&8ᴀᴜᴛᴏ ᴍɪɴᴇʀ &bᴜᴘɢʀᴀᴅᴇs", upgrades)
    openGuis[player] = gui
    gui.open(player)
}

private fun requiresRebirth(key: String): Boolean = key == "lightning" || key == "virtualJackhammer"
