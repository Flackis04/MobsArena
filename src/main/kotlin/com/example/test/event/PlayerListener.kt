package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.floor

class PlayerListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val data = DataStore.get(player.uniqueId)
        MineManager.ensureMineFor(player, teleportToMineTop = false)
        ExperienceManager.clamp(player)
        ExperienceManager.restoreStoredLevel(player)
        PotionUtil.applyNightVision(player)
        player.foodLevel = 20
        player.saturation = 20f
        if (data.newPlayer == null) {
            TutorialManager.initializeFirstJoin(player)
            player.teleport(MineManager.getSpawnLocation())
            playFirstJoinWelcomeSounds(player)
        } else {
            player.playSound(player.location, "entity.item.pickup", 0.9f, 1.1f)
        }
        if (!data.hasReceivedJoinLoadout || KitManager.shouldReceiveStarterLoot(player)) {
            KitManager.giveStarterSpawnLoadout(player)
            data.hasReceivedJoinLoadout = true
        }
        DataIntegrityManager.reconcilePlayer(player)
        player.allowFlight = data.flight
        if (!data.flight) {
            player.isFlying = false
        }
        LightningRodManager.handleJoin(player)
        KitManager.syncLoadoutMode(player, force = true)
        data.playtimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L
        ScoreboardManager.refreshTabListForAll()
        HeadHunterManager.updatePossibleTargets()
        BossbarManager.addPlayer(player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        KitManager.prepareForLogout(event.player)
        LightningRodManager.handleQuit(event.player)
        RetentionUpgradeManager.reset(event.player.uniqueId)
        MineManager.removeMineFor(event.player.uniqueId)
        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            ScoreboardManager.refreshTabListForAll()
        })
        HeadHunterManager.updatePossibleTargets()
        BossbarManager.removePlayer(event.player)
    }

    private fun playFirstJoinWelcomeSounds(player: Player) {
        player.playSound(player.location, "ui.toast.challenge_complete", 0.9f, 1.05f)
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            player.playSound(player.location, "entity.player.levelup", 0.7f, 1.2f)
        }, 6L)
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            player.playSound(player.location, "block.amethyst_block.resonate", 0.8f, 1.15f)
        }, 14L)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        TutorialManager.handleInventoryClosed(player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        event.respawnLocation = MineManager.getPlayerMineCenterLocation(player) ?: MineManager.getSpawnLocation()
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            ExperienceManager.clamp(player)
            ExperienceManager.restoreStoredLevel(player)
            PotionUtil.applyNightVision(player)
            if (KitManager.shouldReceiveStarterLoot(player)) {
                KitManager.giveStarterSpawnLoadout(player)
            }
            player.foodLevel = 20
            player.saturation = 20f
            val data = DataStore.get(player.uniqueId)
            player.allowFlight = data.flight
            if (!data.flight) {
                player.isFlying = false
            }
            KitManager.syncLoadoutMode(player, force = true)
            player.updateInventory()
        }, 1L)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            ExperienceManager.clamp(event.player)
            ExperienceManager.restoreStoredLevel(event.player)
            KitManager.syncLoadoutMode(event.player, force = true)
        }, 1L)
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (KitManager.isDangerZone(player.location)) {
            return
        }
        event.isCancelled = true
        player.foodLevel = 20
        player.saturation = 20f
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        val action = event.action
        if (ItemManager.isBanknote(item)) {
            event.isCancelled = true
            val amount = ItemManager.getBanknoteAmount(item) ?: return
            if (amount <= 0L) return
            if (item!!.amount <= 1) {
                player.inventory.setItemInMainHand(null)
            } else {
                item.amount -= 1
            }
            val data = DataStore.get(player.uniqueId)
            data.balance += amount
            ScoreboardManager.updateBoard(player)
            SessionTimelineManager.record(player, "Redeemed banknote for ${TextUtil.formatNum(amount)} ${ItemManager.COIN_NAME_PLURAL}")
            player.sendMessage(TextUtil.colorize("&aRedeemed a banknote for &b${TextUtil.formatNum(amount)} ${ItemManager.COIN_NAME_PLURAL}&a!"))
            player.playSound(player.location, "entity.player.levelup", 0.5f, 1.2f)
            return
        }
        if (ItemManager.isCoin(item)) {
            event.isCancelled = true
            val amount = player.inventory.all(item!!.type).values.sumOf { it.amount }
            if (amount > 0) {
                player.inventory.remove(item.type)
                val data = DataStore.get(player.uniqueId)
                data.balance += amount
                ScoreboardManager.updateBoard(player)
                SessionTimelineManager.record(player, "Redeemed x$amount ${ItemManager.COIN_NAME_PLURAL}")
                player.sendMessage(TextUtil.colorize("&aYou redeemed &bx$amount ${ItemManager.COIN_NAME_PLURAL}&a!"))
                player.playSound(player.location, "entity.player.levelup", 0.5f, 1.2f)
            }
            return
        }
        if (ItemManager.isTokenShard(item)) {
            event.isCancelled = true
            val amount = player.inventory.all(item!!.type).values.sumOf { it.amount }
            if (amount > 0) {
                player.inventory.remove(item.type)
                val data = DataStore.get(player.uniqueId)
                data.tokens += amount.toLong()
                ScoreboardManager.updateBoard(player)
                SessionTimelineManager.record(player, "Redeemed x$amount ${ItemManager.TOKEN_NAME_PLURAL}")
                player.sendMessage(TextUtil.colorize("&aYou redeemed &bx$amount ${ItemManager.TOKEN_NAME_PLURAL}&a!"))
                player.playSound(player.location, "entity.player.levelup", 0.5f, 1.25f)
            }
            return
        }
        val xpBottleTier = ItemManager.getJackpotXpBottleTier(item)
        if (xpBottleTier != null) {
            event.isCancelled = true
            if (item!!.amount <= 1) {
                player.inventory.setItemInMainHand(null)
            } else {
                item.amount -= 1
            }
            player.giveExpLevels(xpBottleTier.levelsGranted)
            val data = DataStore.get(player.uniqueId)
            data.level = player.level
            SessionTimelineManager.record(player, "Used ${xpBottleTier.displayName} for +${xpBottleTier.levelsGranted} levels")
            TextUtil.showTitle(
                player,
                "&b${xpBottleTier.displayName}",
                "&7Gained &b+${xpBottleTier.levelsGranted} levels",
                10,
                35,
                10
            )
            player.playSound(player.location, "entity.experience_orb.pickup", 0.8f, 1.45f)
            ScoreboardManager.updateBoard(player)
            return
        }
        val buffPotionType = ItemManager.getBuffPotionType(item)
        if (buffPotionType != null) {
            if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
            event.isCancelled = true
            if (item!!.amount <= 1) {
                player.inventory.setItemInMainHand(null)
            } else {
                item.amount -= 1
            }
            PotionsManager.activateBuff(player, buffPotionType)
            return
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        when {
            ItemManager.isBackpack(item) -> {
                event.isCancelled = true
                StorageGui(player, 0)
            }

            ItemManager.isPickaxe(item) -> {
                if (player.isSneaking) {
                    event.isCancelled = true
                    tryOpenPermUpgradeGui(player)
                }
            }
        }

        if (item != null && item.type == Material.PLAYER_HEAD) {
            if (KitManager.isMineMode(player.location) && KitManager.isRankArmorPiece(player.inventory.helmet)) {
                event.isCancelled = true
                return
            }
            val helmet = player.inventory.helmet
            player.inventory.helmet = item
            player.inventory.setItemInMainHand(helmet)
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.player.world.name != "mine") return

        if (event.block.y > 95) {
            return
        }

        if (MineManager.containsMineAreaXZ(event.block.location)) {
            event.isCancelled = true
            return
        }

        if (MineManager.valuables.contains(event.block.type)) {
            event.isCancelled = true
            return
        }

        val placedBlock = event.block
        val placedType = placedBlock.type
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            if (placedBlock.type == placedType) {
                placedBlock.type = Material.AIR
            }
        }, 20L * 15L)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        if (event.player.world.name == "mine" && ((to.x * to.x) + (to.z * to.z) > 250_000.0)) {
            event.player.teleport(MineManager.getSpawnLocation())
            return
        }
        val from = event.from
        if (to.blockX == from.blockX && to.blockY == from.blockY && to.blockZ == from.blockZ) return
        val player = event.player
        val wasInMineZone = KitManager.isMineMode(from)
        val isInMineZone = KitManager.isMineMode(to)
        if (!wasInMineZone && isInMineZone && CombatManager.isInCombat(player)) {
            event.setTo(from)
            player.sendMessage(TextUtil.colorize("&cYou cannot enter a mine while in combat."))
            return
        }
        if (wasInMineZone != isInMineZone) {
            KitManager.syncLoadoutMode(player, to)
        }
        if (to.y <= 33.0) {
            val mineCenter = MineManager.getPlayerMineCenterLocation(player, 1.0) ?: MineManager.getSpawnLocation()
            player.teleport(mineCenter)
            return
        }
        val blockUnder = to.clone().subtract(0.0, 1.0, 0.0).block
        if (blockUnder.type != Material.TINTED_GLASS) return
        val loc = org.bukkit.Location(
            player.world,
            0.5,
            64.0,
            -39.5,
            0f,
            0f
        )

        player.teleport(loc)
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            player.playSound(player.location, "entity.ender_dragon.flap", 1f, 1f)
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 2, 5, false, false, false))
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false))
        }, 1L)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val data = DataStore.get(victim.uniqueId)
        val diedInDangerZone = KitManager.isDangerZone(victim.location)
        event.keepInventory = !diedInDangerZone
        event.keepLevel = true
        event.setShouldDropExperience(false)
        event.droppedExp = 0
        val remainingLevels = floor(victim.level * 0.5).toInt().coerceAtLeast(0)
        victim.level = remainingLevels
        data.level = remainingLevels
        if (!diedInDangerZone) {
            event.drops.clear()
        } else {
            KitManager.handlePvpDeath(victim)
        }
        val attacker = victim.killer
        if (attacker != null) {
            handlePlayerKill(attacker, victim)
        }
        CombatManager.clearCombat(victim)
    }

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is org.bukkit.entity.Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        val victim = event.entity as? Player ?: return
        if (
            attacker.location.y > 95.0 ||
            victim.location.y > 95.0 ||
            MineManager.containsMineAreaXZ(attacker.location) ||
            MineManager.containsMineAreaXZ(victim.location)
        ) {
            event.isCancelled = true
            return
        }
        CombatManager.tagPlayers(attacker, victim)
    }

}

fun handlePlayerKill(attacker: Player, victim: Player) {
    val attackerData = DataStore.get(attacker.uniqueId)
    val victimData = DataStore.get(victim.uniqueId)
    victimData.killStreak = 0
    attackerData.killStreak += 1

    if (attackerData.killStreak % 5 == 0 && attackerData.killStreak != 0) {
        attacker.inventory.addItem(ItemManager.soulFragment.clone())
    }

    HeadHunterManager.handleKill(attacker, victim)

    val count = attackerData.victims.count { it == victim.name }
    attackerData.kills += 1
    attackerData.battlepassTotalKills += 1
    recordBattlepassWeaponKill(attackerData, attacker.inventory.itemInMainHand)
    victimData.deaths += 1
    if (count >= 2) {
        attacker.sendMessage(TextUtil.colorize("&cYou've killed this person too many times within a short period"))
        ScoreboardManager.updateBoard(attacker)
        ScoreboardManager.updateBoard(victim)
        return
    }
    attackerData.victims.add(victim.name)

    attacker.playSound(attacker.location, "entity.lightning_bolt.thunder", 1f, 1f)
    val killReward = 10L
    val stack = ItemManager.coin.clone().apply { amount = killReward.toInt().coerceAtMost(64) }
    val fullStacks = killReward / 64
    val remainder = killReward % 64
    repeat(fullStacks.toInt()) { attacker.inventory.addItem(ItemManager.coin.clone().apply { amount = 64 }) }
    if (remainder > 0) attacker.inventory.addItem(stack.apply { amount = remainder.toInt() })
    ScoreboardManager.updateBoard(attacker)
    ScoreboardManager.updateBoard(victim)
}

private fun recordBattlepassWeaponKill(data: PlayerData, weapon: ItemStack?) {
    when (weapon?.type) {
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.IRON_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD -> data.battlepassSwordKills += 1

        Material.WOODEN_AXE,
        Material.STONE_AXE,
        Material.IRON_AXE,
        Material.DIAMOND_AXE,
        Material.NETHERITE_AXE -> data.battlepassAxeKills += 1

        Material.MACE -> data.battlepassMaceKills += 1
        else -> Unit
    }
}
