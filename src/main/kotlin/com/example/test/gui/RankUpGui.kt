package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class RankUpGui : Listener {
    private val activeViews = mutableMapOf<Player, ActiveView>()

    fun open(player: Player) = open(player, ViewMode.RANKUP)

    fun openRebirth(player: Player) = open(player, ViewMode.REBIRTH)

    fun openAscend(player: Player) = open(player, ViewMode.ASCEND)

    private fun open(player: Player, mode: ViewMode) {
        val gui = Gui.gui()
            .title(Component.text("Progression"))
            .rows(4)
            .disableAllInteractions()
            .create()

        activeViews[player] = ActiveView(gui, mode)
        refreshGui(player)
        gui.setCloseGuiAction {
            activeViews.remove(player)
            GuiClickDebounce.clear(player)
        }

        gui.open(player)
    }

    private fun refreshGui(player: Player) {
        val activeView = activeViews[player] ?: return
        val gui = activeView.gui

        renderFrame(gui)
        renderTabs(player, activeView)
        renderDetails(player, activeView)
        gui.update()
    }

    private fun renderFrame(gui: Gui) {
        val filler = GuiItem(ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta -> meta.displayName(TextUtil.toComponent("&0 ")) }
        })

        for (slot in 0 until 36) {
            gui.setItem(slot, filler)
        }
    }

    private fun renderTabs(player: Player, activeView: ActiveView) {
        val gui = activeView.gui
        gui.setItem(10, createTabItem(player, ViewMode.RANKUP, activeView.mode == ViewMode.RANKUP))
        gui.setItem(13, createTabItem(player, ViewMode.REBIRTH, activeView.mode == ViewMode.REBIRTH))
        gui.setItem(16, createTabItem(player, ViewMode.ASCEND, activeView.mode == ViewMode.ASCEND))
    }

    private fun createTabItem(player: Player, mode: ViewMode, selected: Boolean): GuiItem {
        val material = when (mode) {
            ViewMode.RANKUP -> Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
            ViewMode.REBIRTH -> Material.NETHER_STAR
            ViewMode.ASCEND -> Material.END_CRYSTAL
        }
        val item = ItemStack(material).apply {
            editMeta { meta ->
                val color = if (selected) "&a&l" else "&7&l"
                meta.displayName(TextUtil.toComponent("$color${mode.display}").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent(if (selected) "&aOpen" else mode.description).decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }

        return GuiItem(item) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            open(player, mode)
        }
    }

    private fun renderDetails(player: Player, activeView: ActiveView) {
        val gui = activeView.gui
        gui.setItem(22, GuiItem(createMainActionItem(player, activeView.mode)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            when (activeView.mode) {
                ViewMode.RANKUP -> handleRankupClick(player, activeView)
                ViewMode.REBIRTH -> handleRebirthClick(player, activeView)
                ViewMode.ASCEND -> handleAscendClick(player, activeView)
            }
        })
        gui.setItem(30, GuiItem(createProgressSnapshotItem(player, activeView.mode)))
        gui.setItem(32, GuiItem(createNextGoalItem(player, activeView.mode)))
    }

    private fun handleRankupClick(player: Player, activeView: ActiveView) {
        val data = DataStore.get(player.uniqueId)
        if (data.rank >= LevelManager.getRankMaxLevel(data)) {
            open(player, ViewMode.REBIRTH)
            return
        }

        val cost = getRankupCost(data)
        if (data.balance < cost) {
            player.sendMessage(TextUtil.colorize("&cYou can't afford this rankup."))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            refreshGui(player)
            return
        }

        data.balance -= cost
        data.rank += 1
        data.multiplier += KitManager.KIT_SET_MULTIPLIER_PER_TIER
        activeView.rebirthConfirmationArmed = false
        activeView.ascensionConfirmationArmed = false
        KitManager.equipHead(player, data.rank)
        KitManager.equipArmor(player, data.rank)
        val dynamiteReward = getRankupDynamiteReward(data.rank)
        if (dynamiteReward > 0) {
            KitManager.giveDynamite(player, dynamiteReward)
        }
        SessionTimelineManager.record(
            player,
            "Ranked up to ${formatDisplayedRank(data, data.rank)} for ${TextUtil.formatNum(cost)} ${ItemManager.COIN_NAME_PLURAL}"
        )

        player.sendMessage(TextUtil.colorize("&aRanked up to &bRank ${formatDisplayedRank(data, data.rank)}&a."))
        if (dynamiteReward > 0) {
            player.sendMessage(TextUtil.colorize("&aRankup reward: &cx$dynamiteReward Dynamite&a."))
        }
        player.playSound(player.location, "entity.player.levelup", 1f, 1f)
        ScoreboardManager.updateBoard(player)
        if (data.rank >= LevelManager.getRankMaxLevel(data)) {
            open(player, ViewMode.REBIRTH)
            return
        }
        refreshGui(player)
    }

    private fun handleRebirthClick(player: Player, activeView: ActiveView) {
        val data = DataStore.get(player.uniqueId)
        if (!checkRebirthRequirementsSilently(data)) {
            activeView.rebirthConfirmationArmed = false
            refreshGui(player)
            return
        }

        if (!activeView.rebirthConfirmationArmed) {
            activeView.rebirthConfirmationArmed = true
            player.sendMessage(TextUtil.colorize("&eClick rebirth again to confirm."))
            player.playSound(player.location, "block.note_block.hat", 1f, 1f)
            refreshGui(player)
            return
        }

        activeView.rebirthConfirmationArmed = false
        if (performRebirth(player)) {
            refreshGui(player)
        }
    }

    private fun handleAscendClick(player: Player, activeView: ActiveView) {
        val data = DataStore.get(player.uniqueId)
        if (!checkAscensionRequirementsSilently(data)) {
            activeView.ascensionConfirmationArmed = false
            refreshGui(player)
            return
        }

        if (!activeView.ascensionConfirmationArmed) {
            activeView.ascensionConfirmationArmed = true
            player.sendMessage(TextUtil.colorize("&eClick ascend again to confirm."))
            player.playSound(player.location, "block.note_block.hat", 1f, 1f)
            refreshGui(player)
            return
        }

        activeView.ascensionConfirmationArmed = false
        if (performAscension(player)) {
            openAscend(player)
        }
    }

    private fun createMainActionItem(player: Player, mode: ViewMode): ItemStack = when (mode) {
        ViewMode.RANKUP -> createRankupItem(player)
        ViewMode.REBIRTH -> createRebirthItem(player)
        ViewMode.ASCEND -> createAscendItem(player)
    }

    private fun createRankupItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        if (data.rank >= LevelManager.getRankMaxLevel(data)) {
            return ItemStack(Material.NETHER_STAR).apply {
                editMeta { meta ->
                    meta.displayName(TextUtil.toComponent("&b&lRank Maxed").decoration(TextDecoration.ITALIC, false))
                    meta.lore(
                        listOf(
                            TextUtil.toComponent("&7You reached the final rank for this rebirth.").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&7Head to the rebirth tab to continue.").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&eClick Rebirth above").decoration(TextDecoration.ITALIC, false)
                        )
                    )
                }
            }
        }

        val cost = getRankupCost(data)
        val affordColor = if (data.balance >= cost) "&a" else "&c"
        val nextRank = data.rank + 1
        val dynamiteReward = getRankupDynamiteReward(nextRank)

        return ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&d&lRankup").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&8• &7Next: &b${formatDisplayedRank(data, nextRank)}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&8• ${affordColor}Cost: &b${TextUtil.formatNum(cost)} ${ItemManager.COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&8• &7Reward: &b+${TextUtil.formatNum(KitManager.KIT_SET_MULTIPLIER_PER_TIER)}x &7multiplier").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&8• &7Bonus: &cx$dynamiteReward &7dynamite").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent(if (data.balance >= cost) "&a&lClick to rank up" else "&c&lNot enough coins").decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createRebirthItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val requiredLevel = LevelManager.getRequiredLevelForNextRebirth(data.rebirth)
        val requiredValuableName = getRequiredRebirthValuableDisplayName(data.rebirth)
        val nextStartingUpgradeLevel = 1 + (data.rebirth + 1)
        val canRebirth = checkRebirthRequirementsSilently(data)
        val confirmRebirth = activeViews[player]?.rebirthConfirmationArmed == true

        return ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(
                    TextUtil.toComponent(if (confirmRebirth) "&c&lConfirm Rebirth" else "&b&lRebirth")
                        .decoration(TextDecoration.ITALIC, false)
                )
                val lore = mutableListOf<Component>(
                    TextUtil.toComponent("&8• &7Need: &b${formatDisplayedRank(data, LevelManager.getRankMaxLevel(data))}&7, &aL$requiredLevel&7, $requiredValuableName").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &7Perk: &d+${TextUtil.formatNum(REBIRTH_MULTIPLIER_PER_REBIRTH)}x &7permanent multiplier").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &7Perk: &bUpgrades start at level $nextStartingUpgradeLevel").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &cResets rank, level, coins, inventory, storage, and non-permanent upgrades").decoration(TextDecoration.ITALIC, false)
                )
                if (data.rebirth == 0) {
                    lore += TextUtil.toComponent("&8• &6First rebirth unlocks autominer, jackhammer, and lightning").decoration(TextDecoration.ITALIC, false)
                }
                lore += TextUtil.toComponent(
                    when {
                        confirmRebirth -> "&c&lClick again to confirm"
                        canRebirth -> "&a&lClick to rebirth"
                        else -> "&c&lRequirements not met"
                    }
                ).decoration(TextDecoration.ITALIC, false)
                meta.lore(lore)
            }
        }
    }

    private fun createAscendItem(player: Player): ItemStack {
        val data = DataStore.get(player.uniqueId)
        val confirmAscension = activeViews[player]?.ascensionConfirmationArmed == true
        val canAscend = checkAscensionRequirementsSilently(data)
        return ItemStack(Material.END_CRYSTAL).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent(if (confirmAscension) "&c&lConfirm Ascension" else "&5&lAscension").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&8• &7Need: &bRebirth Z &7and &b${formatDisplayedRank(data, LevelManager.getRankMaxLevel(data))}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&8• &7Perk: &dBase tag &7${if (data.ascension > 0) "${formatAscensionLabel(data.ascension)}1" else "None"} &8-> &d${formatAscensionLabel(data.ascension + 1)}1").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&8• &7Perk: &dFresh ascension tier with A-Z indexing").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&8• &cResets rank, rebirth, level, coins, inventory, storage, non-permanent upgrades, and autominer").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent(
                            when {
                                confirmAscension -> "&c&lClick again to confirm"
                                canAscend -> "&a&lClick to ascend"
                                else -> "&c&lRequirements not met"
                            }
                        ).decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    private fun createProgressSnapshotItem(player: Player, mode: ViewMode): ItemStack {
        val data = DataStore.get(player.uniqueId)
        return ItemStack(Material.BOOK).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&f&lCurrent").decoration(TextDecoration.ITALIC, false))
                val lines = mutableListOf<Component>(
                    TextUtil.toComponent("&8• &7Rank: &b${formatDisplayedRank(data)}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &7Rebirth: &d${formatRebirthRequirement(data.rebirth)}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &7Ascension: &5${if (data.ascension > 0) formatAscensionLabel(data.ascension) else "None"}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &7Level: &a${TextUtil.formatNum(data.level.toLong())}").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&8• &7Coins: &6${TextUtil.formatNum(data.balance)}").decoration(TextDecoration.ITALIC, false)
                )
                if (mode == ViewMode.REBIRTH) {
                    lines += TextUtil.toComponent("&8• &7Valuable: ${getRequiredRebirthValuableDisplayName(data.rebirth)}").decoration(TextDecoration.ITALIC, false)
                }
                meta.lore(lines)
            }
        }
    }

    private fun createNextGoalItem(player: Player, mode: ViewMode): ItemStack {
        val data = DataStore.get(player.uniqueId)
        return ItemStack(Material.COMPASS).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&e&lNext").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    when (mode) {
                        ViewMode.RANKUP -> listOf(
                            TextUtil.toComponent("&8• &7Cost: &6${TextUtil.formatNum(getRankupCost(data))}").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&8• &7Target: &b${formatDisplayedRank(data, (data.rank + 1).coerceAtMost(LevelManager.getRankMaxLevel(data)))}").decoration(TextDecoration.ITALIC, false)
                        )
                        ViewMode.REBIRTH -> listOf(
                            TextUtil.toComponent("&8• &7Reach: &b${formatDisplayedRank(data, LevelManager.getRankMaxLevel(data))}").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&8• &7Level: &a${LevelManager.getRequiredLevelForNextRebirth(data.rebirth)}").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&8• &7Own: ${getRequiredRebirthValuableDisplayName(data.rebirth)}").decoration(TextDecoration.ITALIC, false)
                        )
                        ViewMode.ASCEND -> listOf(
                            TextUtil.toComponent("&8• &7Reach: &bRebirth Z").decoration(TextDecoration.ITALIC, false),
                            TextUtil.toComponent("&8• &7Reach: &b${formatDisplayedRank(data, LevelManager.getRankMaxLevel(data))}").decoration(TextDecoration.ITALIC, false)
                        )
                    }
                )
            }
        }
    }

    private fun getRankupCost(data: PlayerData): Long {
        val baseCost = LevelManager.upgradeRankCosts[data.rank + 1] ?: 0L
        val rebirthMultiplier = 1.0 + (data.rebirth * 0.55)
        return kotlin.math.ceil(baseCost * rebirthMultiplier).toLong()
    }

    private fun getRankupDynamiteReward(rank: Int): Int = (rank / 4).coerceAtLeast(0)

    private fun checkRebirthRequirementsSilently(data: PlayerData): Boolean {
        if (data.rebirth >= 26) return false
        val requiredLevel = LevelManager.getRequiredLevelForNextRebirth(data.rebirth)
        val requiredValuable = getRequiredRebirthValuable(data.rebirth)
        return data.rank >= LevelManager.getRankMaxLevel(data) &&
            data.level >= requiredLevel &&
            (requiredValuable == null || data.getCollectedAmount(requiredValuable) >= 1L)
    }

    private fun checkAscensionRequirementsSilently(data: PlayerData): Boolean =
        data.rebirth >= 26 && data.rank >= LevelManager.getRankMaxLevel(data)

    private data class ActiveView(
        val gui: Gui,
        var mode: ViewMode,
        var rebirthConfirmationArmed: Boolean = false,
        var ascensionConfirmationArmed: Boolean = false
    )

    private enum class ViewMode(val display: String, val description: String) {
        RANKUP("Rankup", "&7Next rank"),
        REBIRTH("Rebirth", "&7Permanent power"),
        ASCEND("Ascend", "&7New ascension tier")
    }
}
