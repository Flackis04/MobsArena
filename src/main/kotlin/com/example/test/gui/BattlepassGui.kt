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

class BattlepassGui : Listener {
    private data class BattlepassQuest(
        val id: String,
        val displayName: String,
        val icon: ItemStack,
        val points: Int,
        val requirement: Int,
        val progress: (PlayerData) -> Int,
        val description: String
    )

    private data class BattlepassReward(
        val tier: Int,
        val pointsRequired: Long,
        val displayName: String,
        val icon: ItemStack,
        val description: String,
        val grant: (Player) -> Unit
    )

    private val questSlots = listOf(10, 11, 12, 13, 14, 15, 16)
    private val rewardSlots = listOf(28, 29, 30, 31, 32, 33, 34)

    private val quests = listOf(
        BattlepassQuest(
            id = "pvp_total_5",
            displayName = "First Blood",
            icon = ItemStack(Material.SKELETON_SKULL),
            points = 10,
            requirement = 5,
            progress = { it.battlepassTotalKills },
            description = "Kill 5 players in the danger zone."
        ),
        BattlepassQuest(
            id = "sword_kills_10",
            displayName = "Sword Specialist",
            icon = ItemStack(Material.IRON_SWORD),
            points = 20,
            requirement = 10,
            progress = { it.battlepassSwordKills },
            description = "Kill 10 players with a sword."
        ),
        BattlepassQuest(
            id = "axe_kills_5",
            displayName = "Axe Hunter",
            icon = ItemStack(Material.DIAMOND_AXE),
            points = 16,
            requirement = 5,
            progress = { it.battlepassAxeKills },
            description = "Kill 5 players with an axe."
        ),
        BattlepassQuest(
            id = "mace_kills_4",
            displayName = "Mace Crusher",
            icon = ItemStack(Material.MACE),
            points = 24,
            requirement = 4,
            progress = { it.battlepassMaceKills },
            description = "Kill 4 players with a mace."
        ),
        BattlepassQuest(
            id = "mine_blocks_25000",
            displayName = "Tunnel Vision",
            icon = ItemStack(Material.DIAMOND_PICKAXE),
            points = 14,
            requirement = 25_000,
            progress = { it.blocksMined.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
            description = "Mine 25,000 blocks."
        ),
        BattlepassQuest(
            id = "playtime_4h",
            displayName = "Put The Hours In",
            icon = ItemStack(Material.CLOCK),
            points = 18,
            requirement = 4 * 60 * 60,
            progress = { it.playtimeSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
            description = "Play for 4 hours."
        ),
        BattlepassQuest(
            id = "pvp_total_20",
            displayName = "Arena Predator",
            icon = ItemStack(Material.NETHERITE_SWORD),
            points = 35,
            requirement = 20,
            progress = { it.battlepassTotalKills },
            description = "Kill 20 players."
        )
    )

    private val rewards = listOf(
        BattlepassReward(1, 10, "Coins Cache", ItemStack(Material.SUNFLOWER), "25,000 coins") { player ->
            DataStore.get(player.uniqueId).balance += 25_000L
        },
        BattlepassReward(2, 25, "Token Cache", ItemStack(Material.PRISMARINE_CRYSTALS), "1,000 tokens") { player ->
            DataStore.get(player.uniqueId).tokens += 1_000L
        },
        BattlepassReward(3, 45, "Gap Bundle", ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4), "4 enchanted golden apples") { player ->
            giveRewardItem(player, ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4))
        },
        BattlepassReward(4, 70, "Storm Kit", ItemStack(Material.WIND_CHARGE, 16), "16 wind charges") { player ->
            giveRewardItem(player, ItemStack(Material.WIND_CHARGE, 16))
        },
        BattlepassReward(5, 100, "Cobweb Cache", ItemStack(Material.COBWEB, 12), "12 cobwebs") { player ->
            giveRewardItem(player, ItemStack(Material.COBWEB, 12))
        },
        BattlepassReward(6, 140, "Contraband Mace", ItemManager.makeMace(8), "A battle-ready mace") { player ->
            giveRewardItem(player, ItemManager.makeMace(8))
        },
        BattlepassReward(7, 190, "Rare Key Drop", ItemStack(Material.ORANGE_DYE), "3 rare keys") { player ->
            repeat(3) {
                org.bukkit.Bukkit.dispatchCommand(
                    org.bukkit.Bukkit.getConsoleSender(),
                    "excellentcrates key give ${player.name} rare"
                )
            }
        }
    )

    fun open(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val gui = Gui.gui()
            .title(component("&8Battlepass"))
            .rows(6)
            .disableAllInteractions()
            .create()
        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }

        fillFrame(gui)
        gui.setItem(4, GuiItem(createSummaryCard(data)))

        quests.forEachIndexed { index, quest ->
            val slot = questSlots[index]
            val progress = quest.progress(data)
            val completed = data.battlepassClaimedQuests.contains(quest.id)
            val unlocked = progress >= quest.requirement
            val item = quest.icon.clone()
            item.editMeta { meta ->
                val status = when {
                    completed -> "&7Claimed"
                    unlocked -> "&aReady"
                    else -> "&cLocked"
                }
                meta.displayName(component("&b${quest.displayName} &8- $status"))
                meta.lore(
                    listOf(
                        "&7${quest.description}",
                        "&7Reward: &d${quest.points} battlepass points",
                        "&7Progress: &f${formatQuestProgress(progress, quest.requirement, quest.id)}",
                        if (completed) "&7Already claimed." else if (unlocked) "&aClick to claim points." else "&cComplete this quest first."
                    ).map(::component)
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
            gui.setItem(slot, GuiItem(item) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                claimQuest(player, quest)
            })
        }

        rewards.forEachIndexed { index, reward ->
            val slot = rewardSlots[index]
            val claimed = data.battlepassClaimedRewards.contains(reward.tier)
            val unlocked = data.battlepassPoints >= reward.pointsRequired
            val item = reward.icon.clone()
            item.editMeta { meta ->
                val status = when {
                    claimed -> "&7Claimed"
                    unlocked -> "&aReady"
                    else -> "&cLocked"
                }
                meta.displayName(component("&6Tier ${reward.tier} &8- $status"))
                meta.lore(
                    listOf(
                        "&7Reward: &f${reward.displayName}",
                        "&7Need: &d${TextUtil.formatNum(reward.pointsRequired)} battlepass points",
                        "&7Current: &d${TextUtil.formatNum(data.battlepassPoints)}",
                        "&7${reward.description}",
                        if (claimed) "&7Already claimed." else if (unlocked) "&aClick to unlock this reward." else "&cEarn more points first."
                    ).map(::component)
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
            gui.setItem(slot, GuiItem(item) {
                if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
                claimReward(player, reward)
            })
        }

        gui.open(player)
    }

    private fun claimQuest(player: Player, quest: BattlepassQuest) {
        val data = DataStore.get(player.uniqueId)
        if (data.battlepassClaimedQuests.contains(quest.id)) return
        if (quest.progress(data) < quest.requirement) return
        data.battlepassClaimedQuests += quest.id
        data.battlepassPoints += quest.points.toLong()
        player.sendMessage(TextUtil.colorize("&aClaimed &f${quest.displayName}&a for &d${quest.points} battlepass points&a."))
        player.playSound(player.location, "entity.player.levelup", 1f, 1.2f)
        ScoreboardManager.updateBoard(player)
        open(player)
    }

    private fun claimReward(player: Player, reward: BattlepassReward) {
        val data = DataStore.get(player.uniqueId)
        if (data.battlepassClaimedRewards.contains(reward.tier)) return
        if (data.battlepassPoints < reward.pointsRequired) return
        data.battlepassClaimedRewards += reward.tier
        reward.grant(player)
        player.sendMessage(TextUtil.colorize("&aUnlocked battlepass reward &f${reward.displayName}&a."))
        player.playSound(player.location, "entity.player.levelup", 1f, 0.9f)
        ScoreboardManager.updateBoard(player)
        open(player)
    }

    private fun createSummaryCard(data: PlayerData): ItemStack =
        ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(component("&dBattlepass"))
                meta.lore(
                    listOf(
                        "&7Complete PvP and activity quests to earn points.",
                        "&7Battlepass Points: &d${TextUtil.formatNum(data.battlepassPoints)}",
                        "&7Quests Claimed: &f${data.battlepassClaimedQuests.size}/${quests.size}",
                        "&7Rewards Claimed: &f${data.battlepassClaimedRewards.size}/${rewards.size}"
                    ).map(::component)
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }

    private fun fillFrame(gui: Gui) {
        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { it.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)) }
        }
        for (slot in 0 until gui.inventory.size) {
            gui.setItem(slot, GuiItem(filler))
        }
    }

    private fun component(text: String): Component =
        TextUtil.toComponent(text).decoration(TextDecoration.ITALIC, false)

    private fun giveRewardItem(player: Player, item: ItemStack) {
        val leftovers = player.inventory.addItem(item.clone())
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun formatQuestProgress(progress: Int, requirement: Int, questId: String): String =
        if (questId == "playtime_4h") {
            "${TextUtil.formatPlaytime(progress.toLong())} / ${TextUtil.formatPlaytime(requirement.toLong())}"
        } else {
            "${TextUtil.formatNum(progress.toLong())}/${TextUtil.formatNum(requirement.toLong())}"
        }
}
