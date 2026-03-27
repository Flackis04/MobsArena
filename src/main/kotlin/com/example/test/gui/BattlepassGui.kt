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

class BattlepassGui : Listener {
    private val slots = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 25, 24, 23, 22, 21, 20, 19, 18, 27, 36, 37, 38, 39, 40, 41, 42, 43, 44)
    private val blockRequirements = listOf(
        200L, 500L, 1000L, 2000L, 5000L, 10000L, 20000L, 40000L, 70000L, 100000L,
        150000L, 200000L, 300000L, 400000L, 550000L, 750000L, 1000000L, 1300000L, 1600000L,
        2000000L, 2400000L, 2800000L, 3200000L, 3700000L, 4100000L, 4400000L, 4600000L, 4800000L, 5000000L
    )
    private val playtimeRequirements = listOf(
        10L * 60L, 20L * 60L, 30L * 60L, 45L * 60L, 1L * 3600L, 90L * 60L, 2L * 3600L, 3L * 3600L, 4L * 3600L, 5L * 3600L,
        6L * 3600L, 8L * 3600L, 10L * 3600L, 12L * 3600L, 14L * 3600L, 16L * 3600L, 18L * 3600L, 20L * 3600L, 24L * 3600L,
        28L * 3600L, 32L * 3600L, 36L * 3600L, 40L * 3600L, 48L * 3600L, 56L * 3600L, 64L * 3600L, 72L * 3600L, 84L * 3600L, 96L * 3600L
    )

    fun open(player: Player) {
        openBlocksRewards(player)
    }

    private fun openBlocksRewards(player: Player) {
        openRewardsPage(
            player = player,
            title = "Blockbreak Rewards",
            requirements = blockRequirements,
            progressValue = DataStore.get(player.uniqueId).blocksMined,
            claimedTiers = DataStore.get(player.uniqueId).hasClaimedBattlepass,
            isPlaytimeReward = false,
            progressFormatter = { value -> "${TextUtil.formatNum(value)} blocks mined" }
        )
    }

    private fun openPlaytimeRewards(player: Player) {
        openRewardsPage(
            player = player,
            title = "Playtime Rewards",
            requirements = playtimeRequirements,
            progressValue = DataStore.get(player.uniqueId).playtimeSeconds,
            claimedTiers = DataStore.get(player.uniqueId).hasClaimedPlaytimeRewards,
            isPlaytimeReward = true,
            progressFormatter = { value -> "${formatExactPlaytime(value)} playtime" }
        )
    }

    private fun openRewardsPage(
        player: Player,
        title: String,
        requirements: List<Long>,
        progressValue: Long,
        claimedTiers: Set<Int>,
        isPlaytimeReward: Boolean,
        progressFormatter: (Long) -> String
    ) {
        val gui = Gui.gui()
            .title(Component.text(title))
            .rows(6)
            .disableAllInteractions()
            .create()
        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }

        for (i in slots.indices) {
            val tier = i + 1
            val requirement = requirements[i]
            val slot = slots[i]
            val unlocked = progressValue >= requirement
            val claimed = claimedTiers.contains(tier)
            val reward = getRewardForTier(tier, isPlaytimeReward = isPlaytimeReward)

            val pane = when {
                !unlocked -> Material.RED_STAINED_GLASS_PANE
                claimed -> Material.GREEN_STAINED_GLASS_PANE
                else -> Material.LIME_STAINED_GLASS_PANE
            }
            val unlockText = when {
                !unlocked -> "&cNot unlocked"
                claimed -> "&7&lUNLOCKED"
                else -> "&a&lUNLOCKED"
            }
            val lore = when {
                !unlocked -> listOf(
                    "&dReward: &b${TextUtil.formatNum(reward)} &ecoins",
                    "&c${progressFormatter(progressValue)} &7/ &a${progressFormatter(requirement)}"
                )
                claimed -> listOf(
                    "&dReward: &b${TextUtil.formatNum(reward)} &ecoins",
                    "&7Reward Claimed"
                )
                else -> listOf(
                    "&dReward: &b${TextUtil.formatNum(reward)} &ecoins",
                    "&aClick to claim a reward!"
                )
            }

            val item = ItemStack(pane)
            item.editMeta { meta ->
                meta.displayName(nonItalicComponent("&bTier $tier $unlockText"))
                meta.lore(lore.map(::nonItalicComponent))
            }

            gui.setItem(slot, GuiItem(item) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                val data = DataStore.get(player.uniqueId)
                val currentClaimed = if (isPlaytimeReward) data.hasClaimedPlaytimeRewards else data.hasClaimedBattlepass
                val currentProgress = if (isPlaytimeReward) data.playtimeSeconds else data.blocksMined
                if (currentProgress < requirement || currentClaimed.contains(tier)) return@GuiItem

                data.balance += getRewardForTier(tier, isPlaytimeReward)
                currentClaimed.add(tier)
                player.playSound(player.location, "entity.player.levelup", 1f, 1f)
                ScoreboardManager.updateBoard(player)

                if (isPlaytimeReward) openPlaytimeRewards(player) else openBlocksRewards(player)
            })
        }

        gui.setItem(48, GuiItem(createNavItem(Material.DIAMOND_PICKAXE, "&eBlock Rewards", !isPlaytimeReward)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            openBlocksRewards(player)
        })
        gui.setItem(50, GuiItem(createNavItem(Material.CLOCK, "&ePlaytime Rewards", isPlaytimeReward)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            openPlaytimeRewards(player)
        })

        gui.open(player)
    }

    private fun createNavItem(material: Material, name: String, isCurrentPage: Boolean): ItemStack {
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

    private fun nonItalicComponent(text: String): Component =
        TextUtil.toComponent(text).decoration(TextDecoration.ITALIC, false)

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

    private fun getRewardForTier(level: Int, isPlaytimeReward: Boolean): Long {
        val adjustedLevel = (level - 1).coerceAtLeast(0).toDouble()
        val baseReward = 250.0 + (120.0 * adjustedLevel.pow(3.2))
        val reward = if (isPlaytimeReward) baseReward / 8.0 else baseReward
        return reward.roundToLong()
    }
}
