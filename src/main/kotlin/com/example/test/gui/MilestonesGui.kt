package com.example.test

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
import kotlin.math.roundToLong

class MilestonesGui : Listener {
    private enum class RewardTrack {
        BLOCKS,
        PLAYTIME
    }

    private data class MilestonesReward(
        val displayText: String,
        val icon: ItemStack,
        val grant: (Player) -> Unit
    )

    private companion object {
        const val TIERS_PER_PAGE = 29
        const val TOTAL_PAGES = 3
    }

    private val slots = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 25, 24, 23, 22, 21, 20, 19, 18, 27, 36, 37, 38, 39, 40, 41, 42, 43, 44)
    private val baseBlockRequirements = listOf(
        200L, 500L, 1000L, 2000L, 5000L, 10000L, 20000L, 40000L, 70000L, 100000L,
        150000L, 200000L, 300000L, 400000L, 550000L, 750000L, 1000000L, 1300000L, 1600000L,
        2000000L, 2400000L, 2800000L, 3200000L, 3700000L, 4100000L, 4400000L, 4600000L, 4800000L, 5000000L
    )
    private val basePlaytimeRequirements = listOf(
        10L * 60L, 20L * 60L, 30L * 60L, 45L * 60L, 1L * 3600L, 90L * 60L, 2L * 3600L, 3L * 3600L, 4L * 3600L, 5L * 3600L,
        6L * 3600L, 8L * 3600L, 10L * 3600L, 12L * 3600L, 14L * 3600L, 16L * 3600L, 18L * 3600L, 20L * 3600L, 24L * 3600L,
        28L * 3600L, 32L * 3600L, 36L * 3600L, 40L * 3600L, 48L * 3600L, 56L * 3600L, 64L * 3600L, 72L * 3600L, 84L * 3600L, 96L * 3600L
    )
    private val blockRequirements = buildScaledRequirements(baseBlockRequirements, 12_500_000L)
    private val playtimeRequirements = buildScaledRequirements(basePlaytimeRequirements, 7L * 24L * 3600L)

    fun open(player: Player) {
        openBlocksRewards(player, 0)
    }

    private fun openBlocksRewards(player: Player, page: Int) {
        openRewardsPage(
            player = player,
            title = "Blockbreak Rewards",
            requirements = blockRequirements,
            progressValue = DataStore.get(player.uniqueId).blocksMined,
            claimedTiers = DataStore.get(player.uniqueId).hasClaimedBattlepass,
            track = RewardTrack.BLOCKS,
            page = page,
            progressFormatter = { value -> "${TextUtil.formatNum(value)} blocks mined" }
        )
    }

    private fun openPlaytimeRewards(player: Player, page: Int) {
        openRewardsPage(
            player = player,
            title = "Playtime Rewards",
            requirements = playtimeRequirements,
            progressValue = DataStore.get(player.uniqueId).playtimeSeconds,
            claimedTiers = DataStore.get(player.uniqueId).hasClaimedPlaytimeRewards,
            track = RewardTrack.PLAYTIME,
            page = page,
            progressFormatter = { value -> "${formatExactPlaytime(value)} playtime" }
        )
    }

    private fun openRewardsPage(
        player: Player,
        title: String,
        requirements: List<Long>,
        progressValue: Long,
        claimedTiers: Set<Int>,
        track: RewardTrack,
        page: Int,
        progressFormatter: (Long) -> String
    ) {
        val safePage = page.coerceIn(0, TOTAL_PAGES - 1)
        val gui = Gui.gui()
            .title(Component.text("$title - Page ${safePage + 1}"))
            .rows(6)
            .disableAllInteractions()
            .create()
        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }

        val pageStartIndex = safePage * TIERS_PER_PAGE
        for (i in slots.indices) {
            val tier = pageStartIndex + i + 1
            val requirement = requirements[pageStartIndex + i]
            val slot = slots[i]
            val unlocked = progressValue >= requirement
            val claimed = claimedTiers.contains(tier)
            val reward = getRewardForTier(tier, track)

            val unlockText = when {
                !unlocked -> "&cNot unlocked"
                claimed -> "&7&lUNLOCKED"
                else -> "&a&lUNLOCKED"
            }
            val lore = when {
                !unlocked -> listOf(
                    "&dReward: ${reward.displayText}",
                    "&c${progressFormatter(progressValue)} &7/ &a${progressFormatter(requirement)}"
                )
                claimed -> listOf(
                    "&dReward: ${reward.displayText}",
                    "&7Reward Claimed"
                )
                else -> listOf(
                    "&dReward: ${reward.displayText}",
                    "&aClick to claim a reward!"
                )
            }

            val item = reward.icon.clone()
            item.editMeta { meta ->
                meta.displayName(nonItalicComponent("&bTier $tier $unlockText"))
                meta.lore(lore.map(::nonItalicComponent))
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }

            gui.setItem(slot, GuiItem(item) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                val data = DataStore.get(player.uniqueId)
                val currentClaimed = if (track == RewardTrack.PLAYTIME) data.hasClaimedPlaytimeRewards else data.hasClaimedBattlepass
                val currentProgress = if (track == RewardTrack.PLAYTIME) data.playtimeSeconds else data.blocksMined
                if (currentProgress < requirement || currentClaimed.contains(tier)) return@GuiItem

                reward.grant(player)
                currentClaimed.add(tier)
                player.playSound(player.location, "entity.player.levelup", 1f, 1f)
                ScoreboardManager.updateBoard(player)

                if (track == RewardTrack.PLAYTIME) openPlaytimeRewards(player, safePage) else openBlocksRewards(player, safePage)
            })
        }

        gui.setItem(45, GuiItem(createPageNavItem(Material.ARROW, "&ePrevious Page", safePage > 0)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            if (safePage <= 0) return@GuiItem
            if (track == RewardTrack.PLAYTIME) openPlaytimeRewards(player, safePage - 1) else openBlocksRewards(player, safePage - 1)
        })
        gui.setItem(48, GuiItem(createTrackNavItem(Material.DIAMOND_PICKAXE, "&eBlock Rewards", track == RewardTrack.BLOCKS)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            openBlocksRewards(player, safePage)
        })
        gui.setItem(49, GuiItem(createPageInfoItem(safePage)))
        gui.setItem(50, GuiItem(createTrackNavItem(Material.CLOCK, "&ePlaytime Rewards", track == RewardTrack.PLAYTIME)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            openPlaytimeRewards(player, safePage)
        })
        gui.setItem(53, GuiItem(createPageNavItem(Material.ARROW, "&eNext Page", safePage < TOTAL_PAGES - 1)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            if (safePage >= TOTAL_PAGES - 1) return@GuiItem
            if (track == RewardTrack.PLAYTIME) openPlaytimeRewards(player, safePage + 1) else openBlocksRewards(player, safePage + 1)
        })

        gui.open(player)
    }

    private fun createTrackNavItem(material: Material, name: String, isCurrentPage: Boolean): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(nonItalicComponent(if (isCurrentPage) "$name &7(Current)" else name))
                meta.lore(
                    listOf(
                        nonItalicComponent(if (isCurrentPage) "&7You are already viewing this page." else "&aClick to open this page.")
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }
    }

    private fun createPageNavItem(material: Material, name: String, enabled: Boolean): ItemStack {
        return ItemStack(if (enabled) material else Material.GRAY_DYE).apply {
            editMeta { meta ->
                meta.displayName(nonItalicComponent(name))
                meta.lore(
                    listOf(
                        nonItalicComponent(if (enabled) "&aClick to change page." else "&7No more pages in this direction.")
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }
    }

    private fun createPageInfoItem(page: Int): ItemStack =
            ItemStack(Material.BOOK).apply {
            editMeta { meta ->
                meta.displayName(nonItalicComponent("&bMilestones"))
                meta.lore(
                    listOf(
                        nonItalicComponent("&7Current page: &f${page + 1}/$TOTAL_PAGES"),
                        nonItalicComponent("&7Later pages have tougher goals"),
                        nonItalicComponent("&7and stronger rewards.")
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }

    private fun nonItalicComponent(text: String): Component =
        TextUtil.toComponent(text).decoration(TextDecoration.ITALIC, false)

    private fun buildScaledRequirements(base: List<Long>, finalTarget: Long): List<Long> {
        val totalTiers = base.size * TOTAL_PAGES
        val start = base.first().coerceAtLeast(1L)
        val step = (finalTarget - start).toDouble() / (totalTiers - 1).coerceAtLeast(1)
        return List(totalTiers) { index ->
            if (index == totalTiers - 1) {
                finalTarget
            } else {
                (start + (step * index)).roundToLong().coerceAtLeast(1L)
            }
        }
    }

    private fun formatExactPlaytime(totalSeconds: Long): String {
        var remaining = totalSeconds.coerceAtLeast(0L)
        val days = remaining / 86_400L
        remaining %= 86_400L
        val hours = remaining / 3_600L
        remaining %= 3_600L
        val minutes = remaining / 60L
        val seconds = remaining % 60L

        val parts = mutableListOf<String>()
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (seconds > 0 || parts.isEmpty()) parts += "${seconds}s"
        return parts.joinToString(" ")
    }

    private fun getRewardForTier(level: Int, track: RewardTrack): MilestonesReward {
        fun coinReward(amount: Long): MilestonesReward = MilestonesReward(
            displayText = "&b${TextUtil.formatNum(amount)} &ecoins",
            icon = ItemStack(Material.SUNFLOWER),
            grant = { player -> DataStore.get(player.uniqueId).balance += amount }
        )

        fun itemReward(item: ItemStack, amount: Int, displayText: String): MilestonesReward = MilestonesReward(
            displayText = displayText,
            icon = item.clone().apply { this.amount = amount.coerceAtLeast(1) },
            grant = { player ->
                val rewardItem = item.clone().apply { this.amount = amount.coerceAtLeast(1) }
                val leftovers = player.inventory.addItem(rewardItem)
                leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            }
        )

        val adjustedLevel = (level - 1).coerceAtLeast(0).toDouble()
        val baseReward = 250.0 + (120.0 * adjustedLevel.pow(3.2))
        val coinAmount = (if (track == RewardTrack.PLAYTIME) baseReward / 8.0 else baseReward).roundToLong()
        val page = (level - 1) / TIERS_PER_PAGE
        val pageTier = ((level - 1) % TIERS_PER_PAGE) + 1

        return if (track == RewardTrack.PLAYTIME) {
            when {
                page == 0 -> when (pageTier) {
                3 -> itemReward(ItemManager.makeDynamite(), 3, "&cx3 Dynamite")
                5 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.NORMAL), 1, "&fNormal Upgrade Scroll")
                7 -> itemReward(ItemManager.makeChargedDynamite(), 2, "&bx2 Charged Dynamite")
                9 -> itemReward(ItemManager.makeDynamite(), 6, "&cx6 Dynamite")
                11 -> itemReward(ItemManager.makeChargedDynamite(), 1, "&9x1 Charged Dynamite")
                13 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.RARE), 1, "&9Rare Upgrade Scroll")
                15 -> itemReward(ItemManager.makeChargedDynamite(), 3, "&bx3 Charged Dynamite")
                17 -> itemReward(ItemManager.makeDynamite(), 10, "&cx10 Dynamite")
                19 -> itemReward(ItemManager.makeChargedDynamite(), 2, "&9x2 Charged Dynamite")
                21 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.MYTHIC), 1, "&5Mythic Upgrade Scroll")
                23 -> itemReward(ItemManager.lightningRodDeployable.clone(), 1, "&ex1 Storm Rod")
                25 -> itemReward(ItemManager.makeChargedDynamite(), 3, "&9x3 Charged Dynamite")
                27 -> itemReward(ItemManager.makeNuke(), 1, "&ex1 Nuke")
                29 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.GOD), 1, "&6God Upgrade Scroll")
                else -> coinReward(coinAmount)
                }
                page == 1 -> when (pageTier) {
                    4 -> itemReward(ItemManager.makeChargedDynamite(), 4, "&9x4 Charged Dynamite")
                    8 -> itemReward(ItemManager.makeChargedDynamite(), 4, "&bx4 Charged Dynamite")
                    12 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.MYTHIC), 1, "&5Mythic Upgrade Scroll")
                    16 -> itemReward(ItemManager.makeNuke(), 2, "&ex2 Nukes")
                    20 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.GOD), 1, "&6God Upgrade Scroll")
                    24 -> itemReward(ItemManager.lightningRodDeployable.clone(), 2, "&ex2 Storm Rods")
                    27 -> itemReward(ItemManager.makeLightningRodDeployable(), 1, "&cStorm Rod")
                    29 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.SECRET), 1, "&dSecret Upgrade Scroll")
                    else -> coinReward((coinAmount * 1.75).roundToLong())
                }
                else -> when (pageTier) {
                    5 -> itemReward(ItemManager.makeNuke(), 3, "&ex3 Nukes")
                    10 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.GOD), 2, "&6x2 God Upgrade Scrolls")
                    15 -> itemReward(ItemManager.makeChargedDynamite(), 6, "&bx6 Charged Dynamite")
                    20 -> itemReward(ItemManager.makeLightningRodDeployable(), 2, "&cx2 Storm Rods")
                    24 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.SECRET), 1, "&dSecret Upgrade Scroll")
                    27 -> itemReward(ItemManager.makeNuke(), 5, "&ex5 Nukes")
                    29 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.SECRET), 2, "&dx2 Secret Upgrade Scrolls")
                    else -> coinReward((coinAmount * 2.5).roundToLong())
                }
            }
        } else {
            when {
                page == 0 -> when (pageTier) {
                2 -> itemReward(ItemManager.makeDynamite(), 4, "&cx4 Dynamite")
                4 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.NORMAL), 1, "&fNormal Upgrade Scroll")
                6 -> itemReward(ItemManager.makeChargedDynamite(), 2, "&bx2 Charged Dynamite")
                8 -> itemReward(ItemManager.makeDynamite(), 8, "&cx8 Dynamite")
                10 -> itemReward(ItemManager.makeChargedDynamite(), 1, "&9x1 Charged Dynamite")
                12 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.RARE), 1, "&9Rare Upgrade Scroll")
                14 -> itemReward(ItemManager.lightningRodDeployable.clone(), 1, "&ex1 Storm Rod")
                16 -> itemReward(ItemManager.makeDynamite(), 12, "&cx12 Dynamite")
                18 -> itemReward(ItemManager.makeChargedDynamite(), 2, "&9x2 Charged Dynamite")
                20 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.MYTHIC), 1, "&5Mythic Upgrade Scroll")
                22 -> itemReward(ItemManager.makeChargedDynamite(), 4, "&bx4 Charged Dynamite")
                24 -> itemReward(ItemManager.makeChargedDynamite(), 3, "&9x3 Charged Dynamite")
                26 -> itemReward(ItemManager.makeNuke(), 1, "&ex1 Nuke")
                28 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.GOD), 1, "&6God Upgrade Scroll")
                29 -> itemReward(ItemManager.makeNuke(), 2, "&ex2 Nukes")
                else -> coinReward(coinAmount)
                }
                page == 1 -> when (pageTier) {
                    3 -> itemReward(ItemManager.makeChargedDynamite(), 5, "&9x5 Charged Dynamite")
                    6 -> itemReward(ItemManager.lightningRodDeployable.clone(), 2, "&ex2 Storm Rods")
                    9 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.MYTHIC), 2, "&5x2 Mythic Upgrade Scrolls")
                    12 -> itemReward(ItemManager.makeNuke(), 2, "&ex2 Nukes")
                    15 -> itemReward(ItemManager.makeChargedDynamite(), 6, "&bx6 Charged Dynamite")
                    18 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.GOD), 1, "&6God Upgrade Scroll")
                    21 -> itemReward(ItemManager.makeNuke(), 3, "&ex3 Nukes")
                    24 -> itemReward(ItemManager.makeLightningRodDeployable(), 1, "&cStorm Rod")
                    27 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.SECRET), 1, "&dSecret Upgrade Scroll")
                    29 -> itemReward(ItemManager.makeNuke(), 4, "&ex4 Nukes")
                    else -> coinReward((coinAmount * 1.85).roundToLong())
                }
                else -> when (pageTier) {
                    4 -> itemReward(ItemManager.lightningRodDeployable.clone(), 3, "&ex3 Storm Rods")
                    8 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.GOD), 2, "&6x2 God Upgrade Scrolls")
                    12 -> itemReward(ItemManager.makeNuke(), 5, "&ex5 Nukes")
                    16 -> itemReward(ItemManager.makeLightningRodDeployable(), 2, "&cx2 Storm Rods")
                    20 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.SECRET), 1, "&dSecret Upgrade Scroll")
                    24 -> itemReward(ItemManager.makeNuke(), 7, "&ex7 Nukes")
                    27 -> itemReward(ItemManager.lightningRodDeployable.clone(), 5, "&ex5 Storm Rods")
                    29 -> itemReward(ItemManager.makeUpgradeScroll(ScrollRarity.SECRET), 2, "&dx2 Secret Upgrade Scrolls")
                    else -> coinReward((coinAmount * 2.75).roundToLong())
                }
            }
        }
    }
}
