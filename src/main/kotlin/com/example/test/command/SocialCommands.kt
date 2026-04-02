package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ClanCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (args.isEmpty()) {
            ClanGui().open(player)
            return true
        }

        val response = when (args[0].lowercase()) {
            "create" -> {
                val name = args.getOrNull(1) ?: return false
                ClanManager.createClan(player, name)
            }
            "invite" -> {
                val targetName = args.getOrNull(1) ?: return false
                val target = Bukkit.getPlayerExact(targetName) ?: run {
                    player.sendMessage(TextUtil.colorize("&cThat player must be online to be invited."))
                    return true
                }
                ClanManager.invitePlayer(player, target)
            }
            "accept" -> ClanManager.acceptInvite(player)
            "leave" -> ClanManager.leaveClan(player)
            "kick" -> {
                val targetName = args.getOrNull(1) ?: return false
                ClanManager.kickMember(player, targetName)
            }
            "promote" -> {
                val targetName = args.getOrNull(1) ?: return false
                ClanManager.promoteMember(player, targetName)
            }
            "disband" -> ClanManager.disbandClan(player)
            "info" -> {
                ClanGui().open(player)
                return true
            }
            "levelup" -> ClanManager.levelUpClan(player, buyMax = args.getOrNull(1)?.equals("max", ignoreCase = true) == true)
            "upgrade" -> {
                val upgradeKey = args.getOrNull(1) ?: return false
                ClanManager.upgradeClan(player, upgradeKey, buyMax = args.getOrNull(2)?.equals("max", ignoreCase = true) == true)
            }
            else -> {
                ClanGui().open(player)
                return true
            }
        }

        player.sendMessage(TextUtil.colorize(response))
        return true
    }
}

class TrustCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (args.isEmpty()) {
            sendHelp(player)
            return true
        }

        val response = when (args[0].lowercase()) {
            "add" -> {
                val targetName = args.getOrNull(1) ?: return false
                val target = Bukkit.getOfflinePlayer(targetName)
                MineAccessManager.trustPlayer(player, target)
            }
            "remove" -> {
                val targetName = args.getOrNull(1) ?: return false
                val target = Bukkit.getOfflinePlayer(targetName)
                MineAccessManager.untrustPlayer(player, target)
            }
            "list" -> {
                val trusted = MineAccessManager.getTrustedPlayers(player.uniqueId)
                if (trusted.isEmpty()) {
                    player.sendMessage(TextUtil.colorize("&7You are not trusting anyone on your mine."))
                    return true
                }
                player.sendMessage(TextUtil.colorize("&d&lTrusted Players"))
                trusted.forEach { target ->
                    player.sendMessage(TextUtil.colorize("&7- &f${target.name ?: "Unknown"}"))
                }
                return true
            }
            else -> {
                sendHelp(player)
                return true
            }
        }

        player.sendMessage(TextUtil.colorize(response))
        return true
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(TextUtil.colorize("&d/trust add <player> &8- &7Grant mine access to a player."))
        player.sendMessage(TextUtil.colorize("&d/trust remove <player> &8- &7Remove a trusted player."))
        player.sendMessage(TextUtil.colorize("&d/trust list &8- &7List all trusted players."))
    }
}

class GiveClanPointsCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.size < 2) return false

        val target = Bukkit.getOfflinePlayer(args[0])
        val amount = args[1].toLongOrNull()
        if (amount == null || amount <= 0L) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be a positive whole number."))
            return true
        }

        val response = ClanManager.addPointsToClan(target.uniqueId, amount)
        sender.sendMessage(TextUtil.colorize(response))
        target.player?.let { ScoreboardManager.updateBoard(it) }
        StatsManager.refreshWallOfFameIfPlaced()
        return true
    }
}

class GiveLightningRodCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.hasPermission("command.dev")) {
            sendPermissionMessage(sender)
            return true
        }
        if (args.isEmpty()) return false

        val target = Bukkit.getPlayerExact(args[0])
        if (target == null || !target.isOnline) {
            sender.sendMessage(TextUtil.colorize("&cThat player must be online."))
            return true
        }
        val amount = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        target.inventory.addItem(ItemManager.lightningRodDeployable.clone().apply { this.amount = amount })
        sender.sendMessage(TextUtil.colorize("&aGave &f${target.name} &ax${TextUtil.formatNum(amount.toLong())} Storm Rod&a."))
        target.sendMessage(TextUtil.colorize("&eYou received &fx${TextUtil.formatNum(amount.toLong())} Storm Rod&e."))
        return true
    }
}
