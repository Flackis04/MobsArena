package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Vindicator
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random

object VindicatorManager : Listener {
    private const val WORLD_NAME = "mine"
    private const val MAP_RADIUS = 250
    private const val ACTIVE_CAP = 180
    private const val SPAWN_INTERVAL_TICKS = 20L * 2L
    private const val CONTROL_INTERVAL_TICKS = 20L
    private const val SPAWN_ATTEMPTS = 40
    private const val SURFACE_Y = 52.0
    private const val MAX_SPAWNS_PER_CYCLE = 18
    private const val MIN_SPAWN_DISTANCE = 20
    private const val MAX_SPAWN_DISTANCE = 90
    private const val DESPAWN_DISTANCE = 140.0
    private const val DESPAWN_DISTANCE_SQUARED = DESPAWN_DISTANCE * DESPAWN_DISTANCE

    private val mobKey by lazy { NamespacedKey(TestPlugin.instance, "wild_vindicator") }
    private val rewardKey by lazy { NamespacedKey(TestPlugin.instance, "wild_vindicator_reward_levels") }
    private var spawnTask: BukkitTask? = null
    private var controlTask: BukkitTask? = null

    private data class Variant(
        val displayName: String,
        val color: String,
        val scale: Double,
        val maxHealth: Double,
        val attackDamage: Double,
        val levelReward: Int,
        val minAxeTier: Int,
        val maxAxeTier: Int,
        val weight: Int
    )

    private val variants = listOf(
        Variant("Rogue Vindicator", "<#D8D8D8>", 0.85, 24.0, 5.0, 110, 2, 8, 52),
        Variant("Savage Vindicator", "<#FFB347>", 1.10, 40.0, 7.0, 150, 6, 14, 26),
        Variant("Titan Vindicator", "<#FF5E7E>", 1.45, 70.0, 10.0, 225, 12, 22, 11),
        Variant("Colossus Vindicator", "<#B388FF>", 1.80, 110.0, 14.0, 325, 18, 30, 6),
        Variant("Behemoth Vindicator", "<#69F0FF>", 2.20, 170.0, 18.0, 475, 24, 38, 3),
        Variant("Worldbreaker Vindicator", "<#FFE082>", 2.75, 260.0, 24.0, 650, 30, 42, 1)
    )

    fun init() {
        spawnTask?.cancel()
        controlTask?.cancel()
        spawnTask = Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable { spawnCycle() }, 80L, SPAWN_INTERVAL_TICKS)
        controlTask = Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable { controlVindicators() }, 40L, CONTROL_INTERVAL_TICKS)
    }

    fun shutdown() {
        spawnTask?.cancel()
        controlTask?.cancel()
        spawnTask = null
        controlTask = null
    }

    @EventHandler
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val vindicator = event.entity as? Vindicator ?: return
        if (!isManaged(vindicator)) return
        val target = event.target as? Player ?: return
        if (KitManager.isMineMode(target.location)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val vindicator = event.entity as? Vindicator ?: return
        if (!isManaged(vindicator)) return

        event.drops.clear()
        event.droppedExp = 0

        val killer = vindicator.killer ?: return
        val rewardLevels = vindicator.persistentDataContainer.getOrDefault(rewardKey, PersistentDataType.INTEGER, 100)
        val scroll = ItemManager.makeUpgradeScroll(ScrollManager.rollNaturalRewardScrollRarity()).apply { amount = 1 }
        vindicator.world.dropItemNaturally(vindicator.location, scroll)
        killer.giveExpLevels(rewardLevels)
        DataStore.get(killer.uniqueId).level = killer.level
        ScoreboardManager.updateBoard(killer)
        SessionTimelineManager.record(killer, "Killed a vindicator for +$rewardLevels levels and a scroll")
        TextUtil.showTitle(killer, "&cVindicator Down", "&7+${rewardLevels} levels &8| &dRandom scroll", 0, 35, 10)
        killer.playSound(killer.location, "entity.vindicator.death", 1f, 0.8f)
    }

    private fun spawnCycle() {
        val world = Bukkit.getWorld(WORLD_NAME) ?: return
        val anchors = getSpawnAnchors(world)
        if (anchors.isEmpty()) return
        val activeCount = world.entities.count { it.type == EntityType.VINDICATOR && isManaged(it as LivingEntity) }
        if (activeCount >= ACTIVE_CAP) return

        val availableSpawns = (ACTIVE_CAP - activeCount).coerceAtMost(MAX_SPAWNS_PER_CYCLE)
        repeat(availableSpawns) {
            val location = findSpawnLocation(world, anchors) ?: return@repeat
            spawnVindicator(location)
        }
    }

    private fun controlVindicators() {
        val world = Bukkit.getWorld(WORLD_NAME) ?: return
        val anchors = getSpawnAnchors(world)
        world.entities
            .asSequence()
            .filterIsInstance<Vindicator>()
            .filter { isManaged(it) }
            .forEach { vindicator ->
                if (anchors.none { it.location.distanceSquared(vindicator.location) <= DESPAWN_DISTANCE_SQUARED }) {
                    vindicator.remove()
                    return@forEach
                }
                if (MineManager.containsMineAreaXZ(vindicator.location) || vindicator.location.y > 95.0) {
                    val safeLocation = findSpawnLocation(world, anchors)
                    if (safeLocation != null) {
                        vindicator.teleport(safeLocation)
                    } else {
                        vindicator.remove()
                    }
                }
            }
    }

    private fun spawnVindicator(location: Location) {
        val world = location.world ?: return
        val variant = rollVariant()
        val axeTier = Random.nextInt(variant.minAxeTier, variant.maxAxeTier + 1)
        val vindicator = world.spawn(location, Vindicator::class.java) { mob ->
            mob.persistentDataContainer.set(mobKey, PersistentDataType.BYTE, 1)
            mob.persistentDataContainer.set(rewardKey, PersistentDataType.INTEGER, variant.levelReward)
            mob.customName(TextUtil.toComponent("${variant.color}&l${variant.displayName}"))
            mob.isCustomNameVisible = false
            mob.removeWhenFarAway = false
            mob.equipment.clear()
            mob.equipment.setItemInMainHand(ItemManager.makeAxe(axeTier))
            mob.equipment.itemInMainHandDropChance = 0f
            mob.canPickupItems = false
            mob.getAttribute(Attribute.MAX_HEALTH)?.baseValue = variant.maxHealth
            mob.health = variant.maxHealth
            mob.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = variant.attackDamage
            mob.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.37
            mob.getAttribute(Attribute.SCALE)?.baseValue = variant.scale
        }
        vindicator.world.playSound(vindicator.location, Sound.ENTITY_VINDICATOR_AMBIENT, 0.8f, 0.85f)
    }

    private fun findSpawnLocation(world: org.bukkit.World, anchors: List<Player>): Location? {
        val center = MineManager.getSpawnLocation()
        repeat(SPAWN_ATTEMPTS) {
            val anchor = anchors.random(Random)
            val distance = Random.nextInt(MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE + 1)
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val x = (anchor.location.x + kotlin.math.cos(angle) * distance).toInt().coerceIn(center.blockX - MAP_RADIUS, center.blockX + MAP_RADIUS)
            val z = (anchor.location.z + kotlin.math.sin(angle) * distance).toInt().coerceIn(center.blockZ - MAP_RADIUS, center.blockZ + MAP_RADIUS)
            val location = Location(world, x + 0.5, SURFACE_Y, z + 0.5)
            if (MineManager.containsMineAreaXZ(location)) return@repeat
            if (location.y > 95.0) return@repeat
            val feet = world.getBlockAt(x, SURFACE_Y.toInt(), z)
            val head = feet.location.clone().add(0.0, 1.0, 0.0).block
            if (!feet.type.isAir && head.type.isAir && head.location.y <= 95.0) {
                return head.location.add(0.5, 0.0, 0.5)
            }
            if (feet.type == Material.AIR && head.type == Material.AIR) {
                return location
            }
        }
        return null
    }

    private fun getSpawnAnchors(world: org.bukkit.World): List<Player> {
        val players = world.players.filter { it.isOnline }
        val dangerPlayers = players.filter { KitManager.isDangerZone(it.location) }
        return if (dangerPlayers.isNotEmpty()) dangerPlayers else players
    }

    private fun rollVariant(): Variant {
        val totalWeight = variants.sumOf { it.weight }
        var roll = Random.nextInt(totalWeight)
        for (variant in variants) {
            roll -= variant.weight
            if (roll < 0) return variant
        }
        return variants.first()
    }

    fun isManaged(entity: LivingEntity): Boolean =
        entity.persistentDataContainer.getOrDefault(mobKey, PersistentDataType.BYTE, 0) == 1.toByte()
}
