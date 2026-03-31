package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

object HeadHunterManager {
    private val hasPressed = mutableMapOf<UUID, Boolean>()
    private val hasToReturnSkull = mutableMapOf<UUID, Boolean>()
    private val targetPlayer = mutableMapOf<UUID, UUID>()
    private val targetName = mutableMapOf<UUID, String>()
    private val targetSkull = mutableMapOf<UUID, ItemStack>()

    private val possibleTargets = mutableSetOf<Player>()

    fun init() {
        Bukkit.getOnlinePlayers().forEach {
            hasPressed[it.uniqueId] = false
            hasToReturnSkull[it.uniqueId] = false
            targetPlayer.remove(it.uniqueId)
        }
        Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            Bukkit.getOnlinePlayers().forEach { p ->
                if (hasToReturnSkull[p.uniqueId] != true) {
                    hasPressed[p.uniqueId] = false
                }
                val t = targetPlayer[p.uniqueId]
                if (t != null) Bukkit.getPlayer(t)?.isGlowing = false
            }
        }, 20L * 60L * 5L, 20L * 60L * 5L)
    }

    fun runHeadHunter(player: Player) {
        if (hasPressed[player.uniqueId] != true) {
            startHeadHunter(player)
        } else {
            handleActive(player)
        }
    }

    private fun startHeadHunter(player: Player) {
        hasPressed[player.uniqueId] = true
        headHunter(player)
    }

    private fun handleActive(player: Player) {
        val skull = targetSkull[player.uniqueId]
        if (skull != null && player.inventory.containsAtLeast(skull, 1)) {
            completeHeadHunter(player)
            return
        }
        if (hasToReturnSkull[player.uniqueId] == true) {
            TextUtil.showTitle(player, "", "&7Return &c${targetName[player.uniqueId]}&7's &7head for a reward", 0, 40, 0)
            return
        }
        updatePossibleTargets()
        checkTargetReminder(player)
    }

    private fun headHunter(player: Player) {
        updatePossibleTargets()
        possibleTargets.remove(player)
        if (possibleTargets.size < 2) {
            TextUtil.showTitle(player, "", "&7Atleast 2 players needs to be in the arena in order to use this", 0, 40, 0)
            return
        }
        val target = possibleTargets.random()
        target.isGlowing = true
        targetPlayer[player.uniqueId] = target.uniqueId
        targetName[player.uniqueId] = target.name
        TextUtil.showTitle(player, "", "&7Return &c${target.name}&7's &7head for a reward", 0, 40, 0)
    }

    private fun completeHeadHunter(player: Player) {
        val targetId = targetPlayer[player.uniqueId]
        if (targetId != null) Bukkit.getPlayer(targetId)?.isGlowing = false
        hasToReturnSkull[player.uniqueId] = false
        hasPressed[player.uniqueId] = false
        val skull = targetSkull[player.uniqueId]
        if (skull != null) player.inventory.removeItem(skull)
        calculateHeadHunterReward(player)
        player.playSound(player.location, "entity.experience_orb.pickup", 1f, 1f)
        ScoreboardManager.updateBoard(player)
    }

    private fun calculateHeadHunterReward(player: Player) {
        val roll = Random.nextInt(1, 13)
        val data = DataStore.get(player.uniqueId)
        val amount = 10L * roll
        val luckyText = if (roll > 6) "&aYou got Lucky!" else "&cYou got unlucky."
        data.balance += amount
        player.sendMessage(TextUtil.colorize("$luckyText &8You received &e$amount ${ItemManager.COIN_NAME_PLURAL}&8 from the Head Hunter."))
    }

    private fun checkTargetReminder(player: Player) {
        val targetNameValue = targetName[player.uniqueId] ?: return
        if (possibleTargets.any { it.name == targetNameValue }) {
            TextUtil.showTitle(player, "", "&7Return &c$targetNameValue&7's &7head for a reward", 0, 40, 0)
            return
        }
        headHunter(player)
    }

    fun updatePossibleTargets() {
        possibleTargets.clear()
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p.world.name == "mine" && p.location.y < 140) {
                possibleTargets.add(p)
            }
        }
    }

    fun handleKill(attacker: Player, victim: Player) {
        val targetId = targetPlayer[attacker.uniqueId]
        if (targetId != null && targetId == victim.uniqueId) {
            if (hasToReturnSkull[attacker.uniqueId] != true) {
                val skull = ItemStack(Material.PLAYER_HEAD)
                skull.editMeta { meta ->
                    meta.displayName(TextUtil.toComponent("${victim.name}'s Head"))
                    meta.lore(listOf(TextUtil.toComponent("&cReturn to the Head Hunter for rewards")))
                }
                targetSkull[attacker.uniqueId] = skull
                attacker.inventory.addItem(skull)
            }
            targetPlayer[attacker.uniqueId] = victim.uniqueId
            targetName[attacker.uniqueId] = victim.name
            hasToReturnSkull[attacker.uniqueId] = true
        }
    }
}
