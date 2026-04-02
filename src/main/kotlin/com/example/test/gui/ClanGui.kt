package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ClanGui {
    fun open(player: Player) {
        val clan = ClanManager.getClanFor(player.uniqueId)
        if (clan == null) {
            openLanding(player)
            return
        }

        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Clan Hub"))
            .rows(5)
            .disableAllInteractions()
            .create()

        fillBackground(gui, Material.GRAY_STAINED_GLASS_PANE)

        gui.setItem(4, GuiItem(createInfoItem(Material.NETHER_STAR, "&d&l${clan.name}", ClanManager.buildClanInfo(player.uniqueId))))
        gui.setItem(19, GuiItem(createProgressItem(clan)))
        gui.setItem(21, GuiItem(createLevelUpItem(player)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val response = ClanManager.levelUpClan(player, buyMax = it.isShiftClick)
            player.sendMessage(TextUtil.colorize(response))
            open(player)
        })
        gui.setItem(23, GuiItem(createUpgradeItem(player, "size", Material.CHEST)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val response = ClanManager.upgradeClan(player, "size", buyMax = it.isShiftClick)
            player.sendMessage(TextUtil.colorize(response))
            open(player)
        })
        gui.setItem(25, GuiItem(createUpgradeItem(player, "fortune", Material.DIAMOND_PICKAXE)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            val response = ClanManager.upgradeClan(player, "fortune", buyMax = it.isShiftClick)
            player.sendMessage(TextUtil.colorize(response))
            open(player)
        })

        gui.setItem(37, GuiItem(createBonusItem(player)))
        gui.setItem(39, GuiItem(createMembersItem(clan)))
        gui.setItem(41, GuiItem(createActionItem(player, clan)))
        gui.setItem(43, GuiItem(createLeaderboardItem()))

        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }
        gui.open(player)
    }

    private fun openLanding(player: Player) {
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Clan Hub"))
            .rows(4)
            .disableAllInteractions()
            .create()

        fillBackground(gui, Material.BLACK_STAINED_GLASS_PANE)

        val pendingInviteClan = ClanManager.getPendingInviteClan(player.uniqueId)
        gui.setItem(4, GuiItem(createInfoItem(
            Material.WRITABLE_BOOK,
            "&d&lCreate Clan",
            listOf(
                "&7Use &f/clan create <name>",
                "&7to create your clan.",
                "&7Names can be 3-12 letters,",
                "&7numbers, or underscores."
            )
        )))
        gui.setItem(11, GuiItem(createInviteItem(pendingInviteClan)) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            if (pendingInviteClan == null) return@GuiItem
            val response = ClanManager.acceptInvite(player)
            player.sendMessage(TextUtil.colorize(response))
            open(player)
        })
        gui.setItem(13, GuiItem(createTopClansPreview()))
        gui.setItem(15, GuiItem(createInfoItem(
            Material.GOLD_INGOT,
            "&6&lHow Clans Work",
            listOf(
                "&7Mine blocks with your clan to",
                "&7earn clan points automatically.",
                "&7Spend points on clan levels",
                "&7and permanent clan upgrades.",
                "&7Clan levels boost fortune",
                "&7and net mine weight."
            )
        )))
        gui.setItem(31, GuiItem(createInfoItem(Material.PAPER, "&e&lClan Commands", ClanManager.buildHelp())))

        gui.setCloseGuiAction { GuiClickDebounce.clear(player) }
        gui.open(player)
    }

    private fun fillBackground(gui: Gui, material: Material) {
        val filler = ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }
        for (slot in 0 until gui.inventory.size) {
            gui.setItem(slot, GuiItem(filler))
        }
    }

    private fun createInfoItem(material: Material, title: String, lore: List<String>): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent(title).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) })
            }
        }

    private fun createProgressItem(clan: ClanManager.Clan): ItemStack {
        val nextCost = ClanManager.getNextLevelCost(clan)
        val progressBar = buildProgressBar(ClanManager.getLevelProgressPercent(clan))
        val levelText = if (nextCost != null) {
            listOf(
                "&7Current Points: &f${TextUtil.formatNum(clan.points)}",
                "&7Next Level Cost: &f${TextUtil.formatNum(nextCost)}",
                "&7Still Needed: &f${TextUtil.formatNum(ClanManager.getPointsToNextLevel(clan))}",
                "&7Progress: $progressBar"
            )
        } else {
            listOf(
                "&7Current Points: &f${TextUtil.formatNum(clan.points)}",
                "&7Progress: &aMax Level",
                "&7Your clan has reached the",
                "&7highest level tier."
            )
        }
        return createInfoItem(
            Material.EXPERIENCE_BOTTLE,
            "&b&lClan Progress",
            listOf(
                "&7Clan Level: &f${clan.level}",
                "&7Lifetime Points: &f${TextUtil.formatNum(clan.totalPointsEarned)}",
                "&7Mining Rate: &f1 point &7per &f40 blocks",
                "&7Blocks Contributed: &f${TextUtil.formatNum(clan.blocksContributed)}"
            ) + levelText
        )
    }

    private fun createLevelUpItem(player: Player): ItemStack {
        val clan = ClanManager.getClanFor(player.uniqueId) ?: return ItemStack(Material.BARRIER)
        val nextCost = ClanManager.getNextLevelCost(clan)
        val owner = clan.ownerId == player.uniqueId
        return createInfoItem(
            Material.EMERALD,
            "&a&lLevel Up Clan",
            listOf(
                "&7Current Level: &f${clan.level}",
                if (nextCost != null) "&7Cost: &f${TextUtil.formatNum(nextCost)} clan points" else "&7Cost: &aMaxed",
                "&7Level Bonus: &f+${String.format("%.0f", ClanManager.getLevelFortuneBonus(clan) * 100)}% fortune",
                "&7             &f+${String.format("%.0f", ClanManager.getLevelMineWeightBonus(clan) * 100)}% mine weight",
                "",
                if (owner) "&7Left click: &aBuy 1 level" else "&7Only the clan owner can level the clan.",
                if (owner) "&7Shift click: &aBuy max levels" else "&7Use /clan to review clan status."
            )
        )
    }

    private fun createUpgradeItem(player: Player, key: String, material: Material): ItemStack {
        val clan = ClanManager.getClanFor(player.uniqueId)
        val level = clan?.let { ClanManager.getUpgradeLevel(it, key) } ?: 0
        val nextCost = clan?.let { ClanManager.getUpgradeCost(it, key) }
        val owner = clan?.ownerId == player.uniqueId
        val name = when (key) {
            "size" -> "Clan Size"
            "fortune" -> "Fortune Aura"
            else -> key
        }
        val effectLine = when (key) {
            "size" -> "&7Effect: &f+${level * 3} member slots from upgrade"
            "fortune" -> "&7Effect: &f+${String.format("%.0f", level * 5.0)}% fortune from upgrade"
            else -> "&7Effect: &f-"
        }
        return createInfoItem(
            material,
            "&e&l$name",
            listOf(
                "&7Current Level: &f$level",
                effectLine,
                if (nextCost != null) "&7Next Cost: &f${TextUtil.formatNum(nextCost)} clan points" else "&7Next Cost: &aMaxed",
                "",
                if (owner) "&7Left click: &aBuy 1 level" else "&7Only the clan owner can buy upgrades.",
                if (owner) "&7Shift click: &aBuy max levels" else "&7Use /clan to review clan status."
            )
        )
    }

    private fun createBonusItem(player: Player): ItemStack {
        val clan = ClanManager.getClanFor(player.uniqueId) ?: return ItemStack(Material.BARRIER)
        return createInfoItem(
            Material.BEACON,
            "&b&lClan Bonuses",
            listOf(
                "&7Mine Weight: &f${String.format("%.2f", ClanManager.getMineWeightMultiplier(player.uniqueId))}x",
                "&7Fortune: &f${String.format("%.2f", ClanManager.getPlayerFortuneMultiplier(player.uniqueId))}x",
                "",
                "&7From Clan Level: &f+${String.format("%.0f", ClanManager.getLevelFortuneBonus(clan) * 100)}% fortune",
                "&7                 &f+${String.format("%.0f", ClanManager.getLevelMineWeightBonus(clan) * 100)}% mine weight",
                "&7From Fortune Aura: &f+${String.format("%.0f", ClanManager.getUpgradeLevel(clan, "fortune") * 5.0)}% fortune",
                "&7From Size Upgrade: &f+${ClanManager.getUpgradeLevel(clan, "size") * 3} slots"
            )
        )
    }

    private fun createMembersItem(clan: ClanManager.Clan): ItemStack {
        val onlineMembers = clan.members.count { Bukkit.getPlayer(it)?.isOnline == true }
        val memberLore = clan.members
            .map { memberId ->
                val offline = Bukkit.getOfflinePlayer(memberId)
                val marker = if (memberId == clan.ownerId) "&6Leader" else "&7Member"
                val online = if (Bukkit.getPlayer(memberId)?.isOnline == true) "&aOnline" else "&8Offline"
                "$marker &f${offline.name ?: "Unknown"} &8- $online"
            }
            .sorted()
            .take(10)

        return createInfoItem(
            Material.PLAYER_HEAD,
            "&d&lMembers",
            listOf(
                "&7Roster: &f${clan.members.size}/${ClanManager.getMemberCap(clan)}",
                "&7Online: &f$onlineMembers",
                ""
            ) + memberLore
        )
    }

    private fun createActionItem(player: Player, clan: ClanManager.Clan): ItemStack {
        val owner = clan.ownerId == player.uniqueId
        return createInfoItem(
            Material.COMPASS,
            "&6&lNext Steps",
            listOf(
                "&7Mining blocks earns clan points.",
                "&7Spend those points on:",
                "&f- Clan levels for passive bonuses",
                "&f- Size and fortune upgrades",
                "",
                if (owner) "&7Owner tools:" else "&7Member tools:",
                if (owner) "&f/clan invite <player>" else "&f/clan leave",
                if (owner) "&f/clan kick <player>" else "&f/clan",
                if (owner) "&f/clan disband" else "&f/clan accept",
                if (owner) "&fShift-click upgrade buttons for max" else "&7Watch the clan progress here."
            )
        )
    }

    private fun createLeaderboardItem(): ItemStack {
        val topClans = ClanManager.getTopClans(5)
        val lore = if (topClans.isEmpty()) {
            listOf("&7No clans exist yet.")
        } else {
            topClans.mapIndexed { index, clan ->
                "&7${index + 1}. &f${clan.name} &8- &dL${clan.level} &8- &b${TextUtil.formatNum(clan.points)} pts"
            }
        }
        return createInfoItem(Material.GOLD_INGOT, "&e&lTop Clans", lore)
    }

    private fun createTopClansPreview(): ItemStack =
        createInfoItem(
            Material.NETHER_STAR,
            "&b&lTop Clans",
            ClanManager.getTopClans(5).mapIndexed { index, clan ->
                "&7${index + 1}. &f${clan.name} &8- &dL${clan.level}"
            }.ifEmpty { listOf("&7No clans exist yet.") }
        )

    private fun createInviteItem(pendingInviteClan: ClanManager.Clan?): ItemStack =
        if (pendingInviteClan == null) {
            createInfoItem(
                Material.NAME_TAG,
                "&7&lNo Pending Invite",
                listOf(
                    "&7When a clan owner invites you,",
                    "&7it will show up here.",
                    "&7You can also use",
                    "&f/clan accept"
                )
            )
        } else {
            createInfoItem(
                Material.LIME_DYE,
                "&a&lAccept Invite",
                listOf(
                    "&7Pending Invite: &f${pendingInviteClan.name}",
                    "&7Left click here to join",
                    "&7the invited clan instantly."
                )
            )
        }

    private fun buildProgressBar(progress: Double): String {
        val filled = (progress.coerceIn(0.0, 1.0) * 12).toInt()
        val empty = (12 - filled).coerceAtLeast(0)
        return "&a${"|".repeat(filled)}&7${"|".repeat(empty)} &f${String.format("%.0f", progress * 100)}%"
    }
}
