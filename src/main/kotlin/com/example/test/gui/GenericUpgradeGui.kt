package com.example.test

import com.example.test.UpgradeFormulas.getExcavatorChance
import com.example.test.UpgradeFormulas.getExcavatorEfficiency
import com.example.test.UpgradeFormulas.getLightningChance
import com.example.test.UpgradeFormulas.getMultiBreakMaxBlocks
import com.example.test.UpgradeFormulas.getOreBoostChance
import com.example.test.UpgradeFormulas.getVirtualJackhammerChance
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import kotlin.math.pow

private val openGuis: MutableMap<Player, GenericUpgradeGui> = mutableMapOf()
private val activeUpgradeViews: MutableMap<Player, Gui> = mutableMapOf()
private const val NON_RANK_UPGRADE_COST_MULTIPLIER = 0.9
private const val UPGRADE_ASCENSION_COST_MULTIPLIER_PER_ASCENSION = 1.25
private val PERM_UPGRADE_SLOTS = listOf(10, 11, 12, 13, 14, 19, 20, 21, 22, 23, 28, 29, 30, 31, 32)

enum class Currency { BALANCE }

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

        renderFrame(gui, player)
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
            TutorialManager.handleUpgradeGuiClosed(player)
            if (!TutorialManager.isRunning(data) && !data.hasSeenUpgradeHint) {
                player.sendMessage(TextUtil.colorize("&bMore to explore. &7If you get stuck or want more features, check out &f/spawn&7."))
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
        renderFrame(gui, player)
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
        upgrade.cost = getUpgradeCost(player, upgrade.key, upgrade.level)

        if (upgrade.level >= upgrade.maxLevel) {
            player.sendMessage(TextUtil.toComponent("&cThis upgrade is already maxed!"))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        val requiredRebirth = getRequiredRebirth(upgrade.key)
        if (requiredRebirth != null && playerData.rebirth < requiredRebirth) {
            player.sendMessage(TextUtil.toComponent("&cYou need rebirth ${formatRebirthRequirement(requiredRebirth)} before buying this upgrade."))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        var purchases = 0
        var totalCostSpent = 0L
        val startingLevel = upgrade.level
        while (upgrade.level < upgrade.maxLevel) {
            val currentCost = getUpgradeCost(player, upgrade.key, upgrade.level)
            val hasEnough = when (upgrade.currency) {
                Currency.BALANCE -> playerData.balance >= currentCost
            }

            if (!hasEnough) break

            when (upgrade.currency) {
                Currency.BALANCE -> playerData.balance -= currentCost
            }
            totalCostSpent += currentCost

            upgrade.onUpgrade(player)
            upgrade.level = getPlayerLevel(player, upgrade.key)
            upgrade.cost = getUpgradeCost(player, upgrade.key, upgrade.level)
            purchases += 1

            if (!buyMax) break
        }

        if (purchases == 0) {
            player.sendMessage(TextUtil.toComponent("&cYou don't have enough Coins!"))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return false
        }

        KitManager.refreshPickaxe(player)
        if (upgrade.key == "multiBreak") {
            TutorialManager.handleMultiBreakPurchased(player)
        }
        player.playSound(player.location, "entity.player.levelup", 1f, 1f)
        ScoreboardManager.updateBoard(player)
        val cleanDisplayName = stripLegacyCodes(upgrade.displayName)
        SessionTimelineManager.record(
            player,
            "Upgraded $cleanDisplayName from level $startingLevel to ${upgrade.level} for ${TextUtil.formatNum(totalCostSpent)} ${ItemManager.COIN_NAME_PLURAL}"
        )

        return true
    }

    private fun renderFrame(gui: Gui, player: Player) {
        val filler = createFrameItem()
        for (slot in 0 until gui.inventory.size) {
            gui.setItem(slot, GuiItem(filler))
        }

        gui.setItem(4, GuiItem(createHeaderItem(player)))
        gui.setItem(49, GuiItem(createWalletItem(player)))
    }

    private fun createUpgradeItem(player: Player, upgrade: Upgrade): ItemStack {
        val progressLine = "&7Level: &f${upgrade.level}&7/&f${upgrade.maxLevel}"
        val requirementLine =
            if (getRequiredRebirth(upgrade.key)?.let { DataStore.get(player.uniqueId).rebirth < it } == true) {
                "&cUnlocks at rebirth ${formatRebirthRequirement(getRequiredRebirth(upgrade.key)!!)}"
            } else {
                null
            }
        val statusLine =
            if (upgrade.level >= upgrade.maxLevel) {
                "&aStatus: &fMAXED"
            } else {
                "${getCostColor(player, upgrade)}Next cost: &b${TextUtil.formatNum(upgrade.cost)} ${ItemManager.COIN_NAME_PLURAL}"
            }
        val scrollsAppliedLine = getScrollsAppliedLine(player, upgrade.key)

        val clickLines =
            if (upgrade.level >= upgrade.maxLevel) {
                if (MasteryManager.canOpenFor(upgrade.key)) listOf("&dShift-click &7to view mastery") else emptyList()
            } else {
                val lines = mutableListOf("&aLeft-click &7to buy 1", "&aRight-click &7to buy max")
                if (MasteryManager.canOpenFor(upgrade.key)) {
                    lines += "&dShift-click &7to view mastery"
                }
                lines
            }

        val fullLore = mutableListOf<String>()
        fullLore += "&8${getUpgradeFlavor(upgrade.key)}"
        fullLore += ""
        fullLore += progressLine
        fullLore += "&7Power:"
        fullLore += upgrade.lore
        if (requirementLine != null) {
            fullLore += ""
            fullLore += requirementLine
        }
        fullLore += ""
        fullLore += "&7Upgrade:"
        fullLore += statusLine
        if (scrollsAppliedLine != null) {
            fullLore += scrollsAppliedLine
        }
        fullLore += ""
        fullLore += "&7Actions:"
        fullLore += clickLines

        val item = upgrade.displayItem.clone()
        item.editMeta { meta ->
            meta.displayName(TextUtil.toComponent(upgrade.displayName).decoration(TextDecoration.ITALIC, false))
            meta.lore(fullLore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        return item
    }

    private fun createFrameItem(): ItemStack =
        ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }

    private fun createHeaderItem(player: Player): ItemStack =
        ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&fUpgrade Station").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        "&7Push your mine further with permanent power.",
                        "",
                        "&7Left-click: &fBuy 1 level",
                        "&7Right-click: &fBuy max",
                        "&7Shift-click: &dOpen mastery when available"
                    ).map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) }
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }

    private fun createWalletItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        return ItemStack(Material.ENDER_CHEST).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&aYour Wallet").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        "&7Coins: &6${TextUtil.formatNum(data.balance)}",
                        "&7Tokens: &b${TextUtil.formatNum(data.tokens)}"
                    ).map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) }
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }

    private fun getCostColor(player: Player, upgrade: Upgrade): String {
        val playerData = DataStore.get(player.uniqueId)
        val affordable = when (upgrade.currency) {
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
            "backpack" -> data.backpackLevel
            "sellMultiplier" -> data.sellMultiplierLevel
            "tokenFinder" -> data.tokenFinderLevel
            "jackpot" -> data.jackpotLevel
            "combo" -> data.comboLevel
            "procPower" -> data.procPowerLevel
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
        upgrade.cost = getUpgradeCost(player, upgrade.key, upgrade.level)

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
            "backpack" -> ItemStack(Material.CHEST)
            "sellMultiplier" -> ItemStack(Material.GOLD_INGOT)
            "tokenFinder" -> ItemStack(Material.PRISMARINE_CRYSTALS)
            "jackpot" -> ItemStack(Material.TOTEM_OF_UNDYING)
            "combo" -> ItemStack(Material.BLAZE_POWDER)
            "procPower" -> ItemStack(Material.NETHER_STAR)
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
            "backpack" -> "&6Backpack Storage - Lvl ${upgrade.level}"
            "sellMultiplier" -> "&6Sell Multiplier - Lvl ${upgrade.level}"
            "tokenFinder" -> "&bToken Finder - Lvl ${upgrade.level}"
            "jackpot" -> "&dJackpot - Lvl ${upgrade.level}"
            "combo" -> "&6Combo Meter - Lvl ${upgrade.level}"
            "procPower" -> "&fProc Power - Lvl ${upgrade.level}"
            "autominerFortune" -> "&bAuto Miner Fortune - Lvl ${upgrade.level}"
            "autominerEfficiency" -> "&eAuto Miner Efficiency - Lvl ${upgrade.level}"
            "energyDrink" -> "&6Energy Drink - Lvl ${upgrade.level}"
            "autominerBackpack" -> "&aAuto Miner Backpack - Lvl ${upgrade.level}"
            "autominerLuck" -> "&dAuto Miner Luck - Lvl ${upgrade.level}"
            else -> upgrade.displayName
        }
    }
}

private fun getUpgradeFlavor(key: String): String =
    when (key) {
        "multiBreak" -> "Rip through more of the mine every swing."
        "oreBoost" -> "Turn hot streaks into bigger payouts."
        "fortune" -> "Squeeze more loot out of every valuable."
        "excavator" -> "Supercharge your proc chain."
        "lightning" -> "Call down tier-upgrading strikes."
        "virtualJackhammer" -> "Cash out entire mine layers in one proc."
        "excavatorEfficiency" -> "Make excavator hits land harder."
        "xpGain" -> "Level faster and keep your pick climbing."
        "oreFrequency" -> "Seed your mine with richer valuables."
        "scrollFinder" -> "Hunt hidden scrolls while you mine."
        "backpack" -> "Hold more loot before you need to sell."
        "sellMultiplier" -> "Make every backpack cashout hit harder."
        "tokenFinder" -> "Find bonus tokens while you mine."
        "jackpot" -> "Spike random drops into huge wins."
        "combo" -> "Build momentum for bigger mining payouts."
        "procPower" -> "Make your mining procs hit much harder."
        "autominerFortune" -> "Boost your autominer drop payout."
        "autominerEfficiency" -> "Let your autominer chew through more blocks."
        "energyDrink" -> "Keep the autominer earning while you're away."
        "autominerBackpack" -> "Store longer before your autominer caps out."
        "autominerLuck" -> "Roll better valuables for your autominer."
        else -> "Upgrade your progression."
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
    fun formatFiveDecimals(value: Number): String = "%.5f".format(value.toDouble())

    return when (key) {
        "multiBreak" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.multiBreakMaxLevel
            val boost = if (ItemManager.isProcBooster(player.inventory.itemInOffHand)) 3.0 else 0.0
            val current = getMultiBreakMaxBlocks(level, maxLevel) + boost
            val next = getMultiBreakMaxBlocks((level + 1).coerceAtMost(maxLevel), maxLevel) + boost
            listOf(
                "&fBreak more blocks with one swing.",
                "&bCap: &f${formatDecimal(current)} &8-> &b${formatDecimal(next)}"
            )
        }
        "oreBoost" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.oreBoostMaxLevel
            val nextLevel = (level + 1).coerceAtMost(maxLevel)
            val next = MineManager.applyProcMultiplier(getOreBoostChance(nextLevel, maxLevel, 0.0), player) * 100
            val currentAdjusted = MineManager.applyProcMultiplier(getOreBoostChance(level, maxLevel, 0.0), player) * 100
            listOf("&fDuplicate ore payouts for 5 seconds.", "&bProc chance: &f${formatDecimal(currentAdjusted)}% &8-> &b${formatDecimal(next)}%")
        }
        "excavator" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.excavatorMaxLevel
            val current = MineManager.applyProcMultiplier(getExcavatorChance(level, maxLevel, 0.0), player) * 100
            val next = MineManager.applyProcMultiplier(getExcavatorChance((level + 1).coerceAtMost(maxLevel), maxLevel, 0.0), player) * 100
            listOf("&fBoost Multi Break payouts with a proc.", "&bProc chance: &f${formatDecimal(current)}% &8-> &b${formatDecimal(next)}%")
        }
        "lightning" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.lightningMaxLevel
            val current = MineManager.applyProcMultiplier(getLightningChance(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.LIGHTNING)), player) * 100
            val next = MineManager.applyProcMultiplier(getLightningChance((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.LIGHTNING)), player) * 100
            listOf(
                "&fStrike a random mine column with lightning.",
                "&fUpgrades the hit block and nearby 3x3 by one tier.",
                "&bProc chance: &f${formatDecimal(current)}% &8-> &b${formatDecimal(next)}%"
            )
        }
        "virtualJackhammer" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.virtualJackhammerMaxLevel
            val current = MineManager.applyProcMultiplier(getVirtualJackhammerChance(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.VIRTUAL_JACKHAMMER)), player) * 100
            val next = MineManager.applyProcMultiplier(getVirtualJackhammerChance((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.VIRTUAL_JACKHAMMER)), player) * 100
            listOf(
                "&fClear out one full mine layer in a proc.",
                "&fPays every valuable on that Y level instantly.",
                "&bProc chance: &f${formatDecimal(current)}% &8-> &b${formatDecimal(next)}%"
            )
        }
        "excavatorEfficiency" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.excavatorEfficiencyMaxLevel
            val current = getExcavatorEfficiency(level, maxLevel, 0.0)
            val next = getExcavatorEfficiency((level + 1).coerceAtMost(maxLevel), maxLevel, 0.0)
            listOf("&fIncrease the excavator payout multiplier.", "&bMultiplier: &f${formatDecimal(current)} &8-> &b${formatDecimal(next)}")
        }
        "xpGain" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.xpGainMaxLevel
            val current = ExperienceManager.getExperienceMultiplier(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.XP_GAIN))
            val next = ExperienceManager.getExperienceMultiplier((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.XP_GAIN))
            listOf("&fMultiply all XP you earn while mining.", "&bXP multiplier: &f${formatDecimal(current)}x &8-> &b${formatDecimal(next)}x")
        }
        "oreFrequency" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.oreFrequencyMaxLevel
            val current = MineManager.getOreFrequencyMultiplier(level, maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.ORE_FREQUENCY))
            val next = MineManager.getOreFrequencyMultiplier((level + 1).coerceAtMost(maxLevel), maxLevel, ScrollManager.getBonus(data, UpgradeScrollType.ORE_FREQUENCY))
            listOf(
                "&fIncrease how often valuables spawn in your mine.",
                "&bMine weight: &f${formatDecimal(current)}x &8-> &b${formatDecimal(next)}x"
            )
        }
        "scrollFinder" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.scrollFinderMaxLevel
            val current = MineManager.applyProcMultiplier(UpgradeFormulas.getScrollFinderChance(level, maxLevel), player) * 100
            val next = MineManager.applyProcMultiplier(UpgradeFormulas.getScrollFinderChance((level + 1).coerceAtMost(maxLevel), maxLevel), player) * 100
            listOf(
                "&fFind upgrade scrolls while mining.",
                "&bFind chance: &f${formatFiveDecimals(current)}% &8-> &b${formatFiveDecimals(next)}%"
            )
        }
        "backpack" -> {
            val data = DataStore.get(player.uniqueId)
            val current = StorageManager.getBackpackCapacity(data)
            val next = StorageManager.getBackpackCapacityForLevel((level + 1).coerceAtMost(data.backpackMaxLevel))
            listOf(
                "&fIncrease how much loot your backpack can hold.",
                "&bCapacity: &f${TextUtil.formatNum(current)} &8-> &b${TextUtil.formatNum(next)}"
            )
        }
        "sellMultiplier" -> {
            val data = DataStore.get(player.uniqueId)
            val current = UpgradeFormulas.getSellMultiplier(level, data.sellMultiplierMaxLevel)
            val next = UpgradeFormulas.getSellMultiplier((level + 1).coerceAtMost(data.sellMultiplierMaxLevel), data.sellMultiplierMaxLevel)
            listOf(
                "&fMultiply backpack sell value on every cashout.",
                "&bSell value: &f${formatDecimal(current)}x &8-> &b${formatDecimal(next)}x"
            )
        }
        "tokenFinder" -> {
            val data = DataStore.get(player.uniqueId)
            val currentChance = UpgradeFormulas.getTokenFinderChance(level, data.tokenFinderMaxLevel) * 100
            val nextChance = UpgradeFormulas.getTokenFinderChance((level + 1).coerceAtMost(data.tokenFinderMaxLevel), data.tokenFinderMaxLevel) * 100
            val currentAmount = UpgradeFormulas.getTokenFinderAmount(level, data.tokenFinderMaxLevel)
            val nextAmount = UpgradeFormulas.getTokenFinderAmount((level + 1).coerceAtMost(data.tokenFinderMaxLevel), data.tokenFinderMaxLevel)
            listOf(
                "&fMine blocks to find bonus tokens.",
                "&bChance: &f${formatFiveDecimals(currentChance)}% &8-> &b${formatFiveDecimals(nextChance)}%",
                "&bToken roll: &f1-${currentAmount} &8-> &b1-${nextAmount}"
            )
        }
        "jackpot" -> {
            val data = DataStore.get(player.uniqueId)
            val currentChance = UpgradeFormulas.getJackpotChance(level, data.jackpotMaxLevel) * 100
            val nextChance = UpgradeFormulas.getJackpotChance((level + 1).coerceAtMost(data.jackpotMaxLevel), data.jackpotMaxLevel) * 100
            val currentMultiplier = UpgradeFormulas.getJackpotMultiplier(level, data.jackpotMaxLevel)
            val nextMultiplier = UpgradeFormulas.getJackpotMultiplier((level + 1).coerceAtMost(data.jackpotMaxLevel), data.jackpotMaxLevel)
            listOf(
                "&fChance for valuable payouts to explode upward.",
                "&bChance: &f${formatFiveDecimals(currentChance)}% &8-> &b${formatFiveDecimals(nextChance)}%",
                "&bJackpot: &f${formatDecimal(currentMultiplier)}x &8-> &b${formatDecimal(nextMultiplier)}x"
            )
        }
        "combo" -> {
            val data = DataStore.get(player.uniqueId)
            val currentCap = UpgradeFormulas.getComboMaxStreak(level, data.comboMaxLevel)
            val nextCap = UpgradeFormulas.getComboMaxStreak((level + 1).coerceAtMost(data.comboMaxLevel), data.comboMaxLevel)
            val currentBonus = UpgradeFormulas.getComboBonusMultiplier(level, currentCap, data.comboMaxLevel)
            val nextBonus = UpgradeFormulas.getComboBonusMultiplier((level + 1).coerceAtMost(data.comboMaxLevel), nextCap, data.comboMaxLevel)
            listOf(
                "&fKeep mining to ramp up temporary payout power.",
                "&bCombo cap: &f${currentCap} &8-> &b${nextCap}",
                "&bMax payout: &f${formatDecimal(currentBonus)}x &8-> &b${formatDecimal(nextBonus)}x"
            )
        }
        "procPower" -> {
            val data = DataStore.get(player.uniqueId)
            val nextLevel = (level + 1).coerceAtMost(data.procPowerMaxLevel)
            val currentOreBoost = UpgradeFormulas.getProcPowerOreBoostMultiplier(level, data.procPowerMaxLevel)
            val nextOreBoost = UpgradeFormulas.getProcPowerOreBoostMultiplier(nextLevel, data.procPowerMaxLevel)
            val currentExcavator = UpgradeFormulas.getProcPowerExcavatorMultiplier(level, data.procPowerMaxLevel)
            val nextExcavator = UpgradeFormulas.getProcPowerExcavatorMultiplier(nextLevel, data.procPowerMaxLevel)
            val currentLightningRadius = UpgradeFormulas.getProcPowerLightningRadiusBonus(level, data.procPowerMaxLevel)
            val nextLightningRadius = UpgradeFormulas.getProcPowerLightningRadiusBonus(nextLevel, data.procPowerMaxLevel)
            val currentLightningTier = UpgradeFormulas.getProcPowerLightningTierBonus(level, data.procPowerMaxLevel)
            val nextLightningTier = UpgradeFormulas.getProcPowerLightningTierBonus(nextLevel, data.procPowerMaxLevel)
            val currentJackhammer = UpgradeFormulas.getProcPowerJackhammerMultiplier(level, data.procPowerMaxLevel)
            val nextJackhammer = UpgradeFormulas.getProcPowerJackhammerMultiplier(nextLevel, data.procPowerMaxLevel)
            listOf(
                "&fAmplify ore boost, excavator, lightning, and jackhammer.",
                "&bOre Boost: &f${formatDecimal(currentOreBoost)}x &8-> &b${formatDecimal(nextOreBoost)}x",
                "&bExcavator force: &f${formatDecimal(currentExcavator)}x &8-> &b${formatDecimal(nextExcavator)}x",
                "&bLightning radius: &f+${currentLightningRadius} &8-> &b+${nextLightningRadius}",
                "&bLightning tier skip: &f+${currentLightningTier} &8-> &b+${nextLightningTier}",
                "&bJackhammer payout: &f${formatDecimal(currentJackhammer)}x &8-> &b${formatDecimal(nextJackhammer)}x"
            )
        }
        "fortune" -> {
            val data = DataStore.get(player.uniqueId)
            val maxLevel = data.fortuneMaxLevel
            val current = UpgradeFormulas.getFortuneMultiplier(level, maxLevel, 0.0)
            val next = UpgradeFormulas.getFortuneMultiplier((level + 1).coerceAtMost(maxLevel), maxLevel, 0.0)
            listOf("&fIncrease drop payout from valuables.", "&bDrop multiplier: &f${formatDecimal(current)}x &8-> &b${formatDecimal(next)}x")
        }
        "autominerFortune" -> {
            val current = UpgradeFormulas.getFortuneMultiplier(level, LevelManager.autoMinerFortuneMaxLevel)
            val next = UpgradeFormulas.getFortuneMultiplier((level + 1).coerceAtMost(LevelManager.autoMinerFortuneMaxLevel), LevelManager.autoMinerFortuneMaxLevel)
            listOf("&fIncrease autominer valuable payout.", "&bDrop multiplier: &f${formatDecimal(current)}x &8-> &b${formatDecimal(next)}x")
        }
        "autominerEfficiency" -> {
            val maxLevel = LevelManager.autoMinerEfficiencyMaxLevel
            val current = AutoMinerManager.getProcessingAttempts(level.coerceAtMost(maxLevel))
            val next = AutoMinerManager.getProcessingAttempts((level + 1).coerceAtMost(maxLevel))
            listOf("&fProcess more mine blocks every second.", "&bBlocks/sec: &f${formatDecimal(current)} &8-> &b${formatDecimal(next)}")
        }
        "energyDrink" -> {
            val current = AutoMinerManager.getOfflineYieldRate(level) * 100
            val next = AutoMinerManager.getOfflineYieldRate(level + 1) * 100
            listOf("&fKeep your autominer productive offline.", "&bOffline pace: &f${formatDecimal(current)}% &8-> &b${formatDecimal(next)}%")
        }
        "autominerBackpack" -> {
            val current = (level.coerceAtLeast(1).toLong() * 10_000L).coerceAtMost(2_500_000L)
            val next = ((level + 1).coerceAtLeast(1).toLong() * 10_000L).coerceAtMost(2_500_000L)
            listOf("&fIncrease autominer storage capacity.", "&bCapacity: &f${TextUtil.formatNum(current)} &8-> &b${TextUtil.formatNum(next)}")
        }
        "autominerLuck" -> {
            val current = AutoMinerManager.getLuckMultiplier(level)
            val next = AutoMinerManager.getLuckMultiplier((level + 1).coerceAtMost(LevelManager.autoMinerLuckMaxLevel))
            listOf(
                "&fImprove autominer valuable rolls.",
                "&bMine weight: &f${formatDecimal(current)}x &8-> &b${formatDecimal(next)}x"
            )
        }
        else -> listOf()
    }
}

private fun getUpgradeCost(player: Player, key: String, currentLevel: Int): Long {
    val nextLevel = currentLevel + 1
    return when (key) {
        "rank" -> LevelManager.upgradeRankCosts[nextLevel] ?: 0
        "multiBreak" -> getDiscountedUpgradeCost(player, LevelManager.upgradeMultiBreakCosts[nextLevel])
        "oreBoost" -> getDiscountedUpgradeCost(player, LevelManager.upgradeOreBoostCosts[nextLevel])
        "fortune" -> getDiscountedUpgradeCost(player, LevelManager.upgradeFortuneCosts[nextLevel])
        "excavator" -> getDiscountedUpgradeCost(player, LevelManager.upgradeExcavatorCosts[nextLevel])
        "lightning" -> getDiscountedUpgradeCost(player, LevelManager.upgradeLightningCosts[nextLevel])
        "virtualJackhammer" -> getDiscountedUpgradeCost(player, LevelManager.upgradeVirtualJackhammerCosts[nextLevel])
        "excavatorEfficiency" -> getDiscountedUpgradeCost(player, LevelManager.upgradeExcavatorEfficiencyCosts[nextLevel])
        "xpGain" -> getDiscountedUpgradeCost(player, LevelManager.upgradeXpGainCosts[nextLevel])
        "oreFrequency" -> getDiscountedUpgradeCost(player, LevelManager.upgradeOreFrequencyCosts[nextLevel])
        "scrollFinder" -> getDiscountedUpgradeCost(player, LevelManager.upgradeScrollFinderCosts[nextLevel])
        "backpack" -> getDiscountedUpgradeCost(player, LevelManager.upgradeBackpackCosts[nextLevel])
        "sellMultiplier" -> getDiscountedUpgradeCost(player, LevelManager.upgradeSellMultiplierCosts[nextLevel])
        "tokenFinder" -> getDiscountedUpgradeCost(player, LevelManager.upgradeTokenFinderCosts[nextLevel])
        "jackpot" -> getDiscountedUpgradeCost(player, LevelManager.upgradeJackpotCosts[nextLevel])
        "combo" -> getDiscountedUpgradeCost(player, LevelManager.upgradeComboCosts[nextLevel])
        "procPower" -> getDiscountedUpgradeCost(player, LevelManager.upgradeProcPowerCosts[nextLevel])
        "autominerFortune" -> getDiscountedUpgradeCost(player, LevelManager.upgradeAutoMinerFortuneCosts[nextLevel])
        "autominerEfficiency" -> getDiscountedUpgradeCost(player, LevelManager.upgradeAutoMinerEfficiencyCosts[nextLevel])
        "energyDrink" -> getDiscountedUpgradeCost(player, LevelManager.upgradeAutoMinerEnergyDrinkCosts[nextLevel])
        "autominerBackpack" -> getDiscountedUpgradeCost(player, LevelManager.upgradeAutoMinerBackpackCosts[nextLevel])
        "autominerLuck" -> getDiscountedUpgradeCost(player, LevelManager.upgradeAutoMinerLuckCosts[nextLevel])
        else -> 0
    }
}

private fun getDiscountedUpgradeCost(player: Player, baseCost: Long?): Long {
    val cost = baseCost ?: return 0L
    val ascension = DataStore.get(player.uniqueId).ascension.coerceAtLeast(0)
    val ascensionMultiplier = if (ascension <= 0) 1.0 else UPGRADE_ASCENSION_COST_MULTIPLIER_PER_ASCENSION.pow(ascension.toDouble())
    return kotlin.math.ceil(cost * NON_RANK_UPGRADE_COST_MULTIPLIER * ascensionMultiplier).toLong().coerceAtLeast(1L)
}


fun openPermUpgradeGui(player: Player) {
    val data = DataStore.get(player.uniqueId)

    val permUpgrades = listOf(
        Upgrade("multiBreak", data.multiBreakLevel, data.multiBreakMaxLevel,
            getUpgradeCost(player, "multiBreak", data.multiBreakLevel), Currency.BALANCE,
            ItemStack(Material.DIAMOND_PICKAXE),
            getDynamicUpgradeLore(player, "multiBreak", data.multiBreakLevel),
            "&eMulti-Break") { p -> DataStore.get(p.uniqueId).multiBreakLevel += 1 },

        Upgrade("oreBoost", data.oreBoostLevel, data.oreBoostMaxLevel,
            getUpgradeCost(player, "oreBoost", data.oreBoostLevel), Currency.BALANCE,
            ItemStack(Material.RAW_GOLD),
            getDynamicUpgradeLore(player, "oreBoost", data.oreBoostLevel),
            "&bOre Booster") { p -> DataStore.get(p.uniqueId).oreBoostLevel += 1 },

        Upgrade("fortune", data.fortuneLevel, data.fortuneMaxLevel,
            getUpgradeCost(player, "fortune", data.fortuneLevel), Currency.BALANCE,
            ItemStack(Material.DIAMOND),
            getDynamicUpgradeLore(player, "fortune", data.fortuneLevel),
            "&dFortune") { p ->
            val pdata = DataStore.get(p.uniqueId)
            pdata.fortuneLevel += 1
            removeItem(p, Material.DIAMOND_PICKAXE)
            KitManager.givePickaxe(p)
        },

        Upgrade("excavator", data.excavatorLevel, data.excavatorMaxLevel,
            getUpgradeCost(player, "excavator", data.excavatorLevel), Currency.BALANCE,
            ItemStack(Material.BEACON),
            getDynamicUpgradeLore(player, "excavator", data.excavatorLevel),
            "&7Excavator") { p -> DataStore.get(p.uniqueId).excavatorLevel += 1 },

        Upgrade("lightning", data.lightningLevel, data.lightningMaxLevel,
            getUpgradeCost(player, "lightning", data.lightningLevel), Currency.BALANCE,
            ItemStack(Material.LIGHTNING_ROD),
            getDynamicUpgradeLore(player, "lightning", data.lightningLevel),
            "&eLightning") { p -> DataStore.get(p.uniqueId).lightningLevel += 1 },

        Upgrade("virtualJackhammer", data.virtualJackhammerLevel, data.virtualJackhammerMaxLevel,
            getUpgradeCost(player, "virtualJackhammer", data.virtualJackhammerLevel), Currency.BALANCE,
            ItemStack(Material.NETHERITE_PICKAXE),
            getDynamicUpgradeLore(player, "virtualJackhammer", data.virtualJackhammerLevel),
            "&6Jackhammer") { p -> DataStore.get(p.uniqueId).virtualJackhammerLevel += 1 },

        Upgrade("excavatorEfficiency", data.excavatorEfficiencyLevel, data.excavatorEfficiencyMaxLevel,
            getUpgradeCost(player, "excavatorEfficiency", data.excavatorEfficiencyLevel), Currency.BALANCE,
            ItemStack(Material.SUGAR),
            getDynamicUpgradeLore(player, "excavatorEfficiency", data.excavatorEfficiencyLevel),
            "&7&lExcavator Efficiency") { p -> DataStore.get(p.uniqueId).excavatorEfficiencyLevel += 1 },

        Upgrade("xpGain", data.xpGainLevel, data.xpGainMaxLevel,
            getUpgradeCost(player, "xpGain", data.xpGainLevel), Currency.BALANCE,
            ItemStack(Material.EXPERIENCE_BOTTLE),
            getDynamicUpgradeLore(player, "xpGain", data.xpGainLevel),
            "&aXP Gain") { p -> DataStore.get(p.uniqueId).xpGainLevel += 1 },

        Upgrade("oreFrequency", data.oreFrequencyLevel, data.oreFrequencyMaxLevel,
            getUpgradeCost(player, "oreFrequency", data.oreFrequencyLevel), Currency.BALANCE,
            ItemStack(Material.EMERALD),
            getDynamicUpgradeLore(player, "oreFrequency", data.oreFrequencyLevel),
            "&2Ore Frequency") { p -> DataStore.get(p.uniqueId).oreFrequencyLevel += 1 },

        Upgrade("scrollFinder", data.scrollFinderLevel, data.scrollFinderMaxLevel,
            getUpgradeCost(player, "scrollFinder", data.scrollFinderLevel), Currency.BALANCE,
            ItemStack(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
            getDynamicUpgradeLore(player, "scrollFinder", data.scrollFinderLevel),
            "&dScroll Finder") { p -> DataStore.get(p.uniqueId).scrollFinderLevel += 1 }
        ,
        Upgrade("sellMultiplier", data.sellMultiplierLevel, data.sellMultiplierMaxLevel,
            getUpgradeCost(player, "sellMultiplier", data.sellMultiplierLevel), Currency.BALANCE,
            ItemStack(Material.GOLD_INGOT),
            getDynamicUpgradeLore(player, "sellMultiplier", data.sellMultiplierLevel),
            "&6Sell Multiplier") { p -> DataStore.get(p.uniqueId).sellMultiplierLevel += 1 },

        Upgrade("tokenFinder", data.tokenFinderLevel, data.tokenFinderMaxLevel,
            getUpgradeCost(player, "tokenFinder", data.tokenFinderLevel), Currency.BALANCE,
            ItemStack(Material.PRISMARINE_CRYSTALS),
            getDynamicUpgradeLore(player, "tokenFinder", data.tokenFinderLevel),
            "&bToken Finder") { p -> DataStore.get(p.uniqueId).tokenFinderLevel += 1 },

        Upgrade("jackpot", data.jackpotLevel, data.jackpotMaxLevel,
            getUpgradeCost(player, "jackpot", data.jackpotLevel), Currency.BALANCE,
            ItemStack(Material.TOTEM_OF_UNDYING),
            getDynamicUpgradeLore(player, "jackpot", data.jackpotLevel),
            "&dJackpot") { p -> DataStore.get(p.uniqueId).jackpotLevel += 1 },

        Upgrade("combo", data.comboLevel, data.comboMaxLevel,
            getUpgradeCost(player, "combo", data.comboLevel), Currency.BALANCE,
            ItemStack(Material.BLAZE_POWDER),
            getDynamicUpgradeLore(player, "combo", data.comboLevel),
            "&6Combo Meter") { p -> DataStore.get(p.uniqueId).comboLevel += 1 },

        Upgrade("procPower", data.procPowerLevel, data.procPowerMaxLevel,
            getUpgradeCost(player, "procPower", data.procPowerLevel), Currency.BALANCE,
            ItemStack(Material.NETHER_STAR),
            getDynamicUpgradeLore(player, "procPower", data.procPowerLevel),
            "&fProc Power") { p -> DataStore.get(p.uniqueId).procPowerLevel += 1 }
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
            getUpgradeCost(player, "autominerFortune", data.autoMinerFortuneLevel),
            Currency.BALANCE,
            ItemStack(Material.DIAMOND),
            getDynamicUpgradeLore(player, "autominerFortune", data.autoMinerFortuneLevel),
            "&bAuto Miner Fortune"
        ) { p -> DataStore.get(p.uniqueId).autoMinerFortuneLevel += 1 },
        Upgrade(
            "autominerEfficiency",
            data.autoMinerEfficiencyLevel,
            data.autoMinerEfficiencyMaxLevel,
            getUpgradeCost(player, "autominerEfficiency", data.autoMinerEfficiencyLevel),
            Currency.BALANCE,
            ItemStack(Material.HOPPER),
            getDynamicUpgradeLore(player, "autominerEfficiency", data.autoMinerEfficiencyLevel),
            "&eAuto Miner Efficiency"
        ) { p -> DataStore.get(p.uniqueId).autoMinerEfficiencyLevel += 1 },
        Upgrade(
            "energyDrink",
            data.autoMinerEnergyDrinkLevel,
            data.autoMinerEnergyDrinkMaxLevel,
            getUpgradeCost(player, "energyDrink", data.autoMinerEnergyDrinkLevel),
            Currency.BALANCE,
            ItemStack(Material.POTION),
            getDynamicUpgradeLore(player, "energyDrink", data.autoMinerEnergyDrinkLevel),
            "&6Energy Drink"
        ) { p -> DataStore.get(p.uniqueId).autoMinerEnergyDrinkLevel += 1 },
        Upgrade(
            "autominerBackpack",
            data.autoMinerBackpackLevel,
            data.autoMinerBackpackMaxLevel,
            getUpgradeCost(player, "autominerBackpack", data.autoMinerBackpackLevel),
            Currency.BALANCE,
            ItemStack(Material.CHEST),
            getDynamicUpgradeLore(player, "autominerBackpack", data.autoMinerBackpackLevel),
            "&aAuto Miner Backpack"
        ) { p -> DataStore.get(p.uniqueId).autoMinerBackpackLevel += 1 },
        Upgrade(
            "autominerLuck",
            data.autoMinerLuckLevel,
            data.autoMinerLuckMaxLevel,
            getUpgradeCost(player, "autominerLuck", data.autoMinerLuckLevel),
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

private fun getRequiredRebirth(key: String): Int? =
    when (key) {
        "lightning" -> 1
        "virtualJackhammer" -> 3
        "scrollFinder" -> 5
        else -> null
    }

private fun requiresRebirth(key: String): Boolean = getRequiredRebirth(key) != null

private fun stripLegacyCodes(text: String): String =
    text.replace(Regex("&[0-9a-fk-or]", RegexOption.IGNORE_CASE), "").trim()
