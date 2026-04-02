package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Transformation
import java.util.*
import kotlin.random.Random

object LightningRodManager : Listener {
    private const val DISPLAY_SCALE = 6.0f
    private const val BASE_SCALE = 4.5f
    private const val BASE_HEIGHT_SCALE = 0.8f
    private const val MAX_RODS_PER_MINE = 100
    private const val MAX_TRIPLE_LIGHTNING_CHANCE_MULTIPLIER = 2.5
    private const val DISPLAY_HEIGHT_OFFSET = 0.5
    private const val AMBIENT_RADIUS = 28.0
    private const val AMBIENT_RADIUS_SQUARED = AMBIENT_RADIUS * AMBIENT_RADIUS
    private const val AMBIENT_MAX_VOLUME = 0.28f
    private const val AMBIENT_MIN_VOLUME = 0.04f
    private const val AMBIENT_PITCH = 1.35f
    private const val AMBIENT_SOUND = "block.beacon.ambient"
    private const val AMBIENT_INTERVAL_TICKS = 30L

    private val activeDisplays = mutableMapOf<UUID, RodDisplay>()

    fun init() {
        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            Bukkit.getOnlinePlayers().forEach(::handleJoin)
        })
        Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            playAmbientSounds()
        }, AMBIENT_INTERVAL_TICKS, AMBIENT_INTERVAL_TICKS)
    }

    fun shutdown() {
        activeDisplays.values.forEach(::removeDisplay)
        activeDisplays.clear()
    }

    fun handleJoin(player: Player) {
        migrateLegacyState(player.uniqueId)
        if (getPlacedRodCount(player.uniqueId) > 0) {
            spawnDisplay(player.uniqueId)
        }
    }

    fun handleQuit(player: Player) {
        removeDisplay(player.uniqueId)
    }

    fun hasActiveRod(playerId: UUID): Boolean = getPlacedRodCount(playerId) > 0

    fun getPlacedRodCount(playerId: UUID): Int {
        val data = DataStore.get(playerId)
        if (data.lightningRodCount <= 0 && data.lightningRodPlaced) {
            data.lightningRodCount = 1
        }
        return data.lightningRodCount.coerceIn(0, MAX_RODS_PER_MINE)
    }

    fun tryTriggerTripleLightning(player: Player, lightningChance: Double): Boolean {
        val placedCount = getPlacedRodCount(player.uniqueId)
        if (placedCount <= 0) return false
        val tripleLightningChance = (lightningChance.coerceAtLeast(0.0) * getTripleLightningChanceMultiplier(placedCount)).coerceAtMost(1.0)
        if (Random.nextDouble() > tripleLightningChance) return false

        var triggered = false
        repeat(3) {
            triggered = MineManager.triggerLightningUpgrade(player, sendMessage = false) || triggered
        }
        if (triggered) {
            player.sendMessage(TextUtil.colorize("&eStorm Rod triggered &f3x Lightning&e!"))
        }
        return triggered
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.name.contains("RIGHT_CLICK")) return

        val player = event.player
        val item = event.item ?: return
        if (!ItemManager.isLightningRodDeployable(item)) return

        if (!MineManager.containsMineAreaXZ(player.location) || MineManager.getMineAreaOwnerAtIgnoringY(player.location) != player.uniqueId) {
            player.sendMessage(TextUtil.colorize("&cYou must be inside your own mine to place the Storm Rod."))
            event.isCancelled = true
            return
        }
        val data = DataStore.get(player.uniqueId)
        migrateLegacyState(player.uniqueId)
        if (data.lightningRodCount >= MAX_RODS_PER_MINE) {
            player.sendMessage(TextUtil.colorize("&cYou already have the maximum &f$MAX_RODS_PER_MINE &cStorm Rods placed on your mine."))
            event.isCancelled = true
            return
        }

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        event.isCancelled = true

        consumeOne(player)
        data.lightningRodCount = (data.lightningRodCount + 1).coerceAtMost(MAX_RODS_PER_MINE)
        data.lightningRodPlaced = data.lightningRodCount > 0
        spawnDisplay(player.uniqueId)
        SessionTimelineManager.record(player, "Placed Storm Rod (${data.lightningRodCount}/$MAX_RODS_PER_MINE)")
        player.sendMessage(TextUtil.colorize("&eStorm Rod power increased to &f${data.lightningRodCount}&e/&f$MAX_RODS_PER_MINE&e."))
    }

    private fun spawnDisplay(ownerId: UUID) {
        removeDisplay(ownerId)
        val baseLocation = MineManager.getPlayerMineCenterLocation(ownerId, DISPLAY_HEIGHT_OFFSET) ?: return
        val world = baseLocation.world ?: return
        val anchor = baseLocation.block.location.add(0.5, 0.0, 0.5)
        val rodDisplay = world.spawn(anchor, BlockDisplay::class.java) { display ->
            display.block = Material.LIGHTNING_ROD.createBlockData()
            display.transformation = currentRodTransformation()
            display.interpolationDuration = 0
            display.interpolationDelay = 0
        }
        val baseDisplay = world.spawn(anchor, BlockDisplay::class.java) { display ->
            display.block = getBaseMaterial(getPlacedRodCount(ownerId)).createBlockData()
            display.transformation = currentBaseTransformation()
            display.interpolationDuration = 0
            display.interpolationDelay = 0
        }
        activeDisplays[ownerId] = RodDisplay(rodDisplay, baseDisplay)
    }

    private fun removeDisplay(ownerId: UUID) {
        val display = activeDisplays.remove(ownerId) ?: return
        removeDisplay(display)
    }

    private fun removeDisplay(display: RodDisplay) {
        if (display.rod.isValid) display.rod.remove()
        if (display.base.isValid) display.base.remove()
    }

    private fun currentRodTransformation(): Transformation {
        return Transformation(
            org.joml.Vector3f(-(DISPLAY_SCALE - 1f) / 2f, 0f, -(DISPLAY_SCALE - 1f) / 2f),
            org.joml.Quaternionf(),
            org.joml.Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE),
            org.joml.Quaternionf()
        )
    }

    private fun currentBaseTransformation(): Transformation {
        return Transformation(
            org.joml.Vector3f(-(BASE_SCALE - 1f) / 2f, -0.4f, -(BASE_SCALE - 1f) / 2f),
            org.joml.Quaternionf(),
            org.joml.Vector3f(BASE_SCALE, BASE_HEIGHT_SCALE, BASE_SCALE),
            org.joml.Quaternionf()
        )
    }

    private fun getBaseMaterial(placedCount: Int): Material {
        val clamped = placedCount.coerceIn(1, MAX_RODS_PER_MINE)
        val progress = (clamped - 1).toDouble() / (MAX_RODS_PER_MINE - 1).toDouble()
        return when {
            progress >= 0.75 -> Material.OXIDIZED_COPPER
            progress >= 0.5 -> Material.WEATHERED_COPPER
            progress >= 0.25 -> Material.EXPOSED_COPPER
            else -> Material.COPPER_BLOCK
        }
    }

    private fun getTripleLightningChanceMultiplier(placedCount: Int): Double {
        val clamped = placedCount.coerceIn(1, MAX_RODS_PER_MINE)
        if (clamped <= 1) return 1.0
        val progress = (clamped - 1).toDouble() / (MAX_RODS_PER_MINE - 1).toDouble()
        return 1.0 + ((MAX_TRIPLE_LIGHTNING_CHANCE_MULTIPLIER - 1.0) * progress)
    }

    private fun migrateLegacyState(ownerId: UUID) {
        val data = DataStore.get(ownerId)
        if (data.lightningRodCount <= 0 && data.lightningRodPlaced) {
            data.lightningRodCount = 1
        }
        data.lightningRodPlaced = data.lightningRodCount > 0
    }

    private fun playAmbientSounds() {
        if (activeDisplays.isEmpty()) return
        activeDisplays.entries.removeIf { (_, display) -> !display.rod.isValid || !display.base.isValid }

        activeDisplays.values.forEach { display ->
            val source = display.rod.location
            val world = source.world ?: return@forEach
            world.players.forEach { player ->
                val distanceSquared = player.location.distanceSquared(source)
                if (distanceSquared > AMBIENT_RADIUS_SQUARED) return@forEach

                val distanceRatio = (kotlin.math.sqrt(distanceSquared) / AMBIENT_RADIUS).coerceIn(0.0, 1.0)
                val volume = (AMBIENT_MAX_VOLUME * (1.0 - distanceRatio)).toFloat().coerceAtLeast(AMBIENT_MIN_VOLUME)
                player.playSound(source, AMBIENT_SOUND, volume, AMBIENT_PITCH)
            }
        }
    }

    private fun consumeOne(player: Player) {
        val item = player.inventory.itemInMainHand
        if (item.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            item.amount -= 1
        }
    }

    private data class RodDisplay(
        val rod: BlockDisplay,
        val base: BlockDisplay
    )
}
