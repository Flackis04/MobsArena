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
import org.bukkit.event.player.*
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class PlayerListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val data = DataStore.get(player.uniqueId)
        MineManager.ensureMineFor(player, teleportToMineTop = data.newPlayer != null)
        ExperienceManager.clamp(player)
        ExperienceManager.restoreStoredLevel(player)
        PotionUtil.applyNightVision(player)
        player.foodLevel = 20
        player.saturation = 20f
        player.playSound(player.location, "entity.item.pickup", 1f, 1f)
        if (data.newPlayer == null) {
            TutorialManager.newPlayer(player)
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
        KitManager.ensureRankArmorState(player)
        data.playtimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L
        ScoreboardManager.refreshTabListForAll()
        HeadHunterManager.updatePossibleTargets()
        BossbarManager.addPlayer(player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        MineManager.removeMineFor(event.player.uniqueId)
        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            ScoreboardManager.refreshTabListForAll()
        })
        HeadHunterManager.updatePossibleTargets()
        BossbarManager.removePlayer(event.player)
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
            KitManager.ensureRankArmorState(player)
            player.updateInventory()
        }, 1L)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            ExperienceManager.clamp(event.player)
            ExperienceManager.restoreStoredLevel(event.player)
        }, 1L)
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        event.isCancelled = true
        player.foodLevel = 20
        player.saturation = 20f
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
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
                player.sendMessage(TextUtil.colorize("&aYou redeemed &bx$amount ${ItemManager.COIN_NAME_PLURAL}&a!"))
                player.playSound(player.location, "entity.player.levelup", 0.5f, 1.2f)
            }
            return
        }

        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        when {
            ItemManager.isStorage(item) -> {
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
            val data = DataStore.get(player.uniqueId)
            if (!data.hasEnabledPvp && KitManager.isRankArmorPiece(player.inventory.helmet)) {
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
        if (event.player.world.name == "mine" && MineManager.valuables.contains(event.block.type)) {
            event.isCancelled = true
        }
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
        if (to.y < 0.0) {
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

        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            val data = DataStore.get(player.uniqueId)
            if (data.newPlayer != null) return@Runnable
            if (data.hasTouched) return@Runnable
            if (player.location.blockY == 66) {
                player.sendTitle(
                    TextUtil.colorize("&bYour mine is where the money starts"),
                    TextUtil.colorize("&7Break valuable ores, then sell them from your backpack."),
                    10, 60, 10
                )
                player.playSound(player.location, "entity.experience_orb.pickup", 1f, 1.2f)
                data.hasTouched = true
            }
        }, 50L)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        event.keepInventory = true
        event.keepLevel = true
        event.setShouldDropExperience(false)
        event.droppedExp = 0
        event.drops.clear()
        val victim = event.entity
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
        val attackerData = DataStore.get(attacker.uniqueId)
        val victimData = DataStore.get(victim.uniqueId)
        if (!attackerData.hasEnabledPvp || !victimData.hasEnabledPvp) {
            event.isCancelled = true
            return
        }
        CombatManager.tagPlayers(attacker, victim)
        if (attackerData.noobProtection) attackerData.noobProtection = false
        if (victimData.noobProtection) {
            attacker.sendTitle("", TextUtil.colorize("&cPlayer has noob-protection on!"), 0, 40, 0)
            event.isCancelled = true
        }
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
    victimData.deaths += 1
    if (count >= 2) {
        attacker.sendMessage(TextUtil.colorize("&cYou've killed this person too many times within a short period"))
        ScoreboardManager.updateBoard(attacker)
        ScoreboardManager.updateBoard(victim)
        return
    }
    attackerData.victims.add(victim.name)

    val stolenDeathpack = StorageManager.getDeathpackContents(victim)
    val stolenItems = stolenDeathpack.sumOf { it.amount.toLong() }
    for (item in stolenDeathpack) {
        if (item.type == Material.AIR || item.amount <= 0) continue
        StorageManager.addDropToDeathpack(attacker, item.clone())
    }
    StorageManager.clearDeathpack(victim)
    if (stolenItems > 0L) {
        attacker.sendMessage(TextUtil.colorize("&cLooted &7x${TextUtil.formatNum(stolenItems)} items &cfrom ${victim.name}'s deathpack."))
        victim.sendMessage(TextUtil.colorize("&cYour deathpack was claimed by ${attacker.name}."))
    }

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
