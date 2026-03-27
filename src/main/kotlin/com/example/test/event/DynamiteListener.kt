package com.example.test

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.Event
import kotlin.math.sqrt
import kotlin.math.roundToLong

class DynamiteListener : Listener {
    private val ownerKey = NamespacedKey(TestPlugin.instance, "dynamite_owner")
    private val markerKey = NamespacedKey(TestPlugin.instance, "mobsarena_dynamite")
    private val blastRadius = 3.5
    private val recentExplosions = ArrayDeque<RecentExplosion>()

    private companion object {
        const val DYNAMITE_FUSE_TICKS = 40
        const val DYNAMITE_ANIMATION_DURATION_TICKS = 12
        const val DYNAMITE_WAVE_DELAY_MULTIPLIER = 2.2
        const val RECENT_EXPLOSION_WINDOW_MS = 500L
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.name.contains("RIGHT_CLICK")) return

        val player = event.player
        val item = event.item ?: return
        if (!ItemManager.isDynamite(item)) return

        if (!MineManager.containsMineAreaXZ(player.location)) {
            event.isCancelled = true
            player.sendTitle("", TextUtil.colorize("&cYou need to be in the mine to throw dynamite"), 0, 40, 0)
            return
        }
        if (!MineManager.canBreakMineAreaIgnoringY(player, player.location)) {
            event.isCancelled = true
            player.sendTitle(
                TextUtil.colorize("&cTrying to steal?"),
                TextUtil.colorize("&7Miner rank is required to use dynamite in other players' mine areas."),
                0,
                40,
                10
            )
            return
        }

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        event.isCancelled = true

        val world = player.world
        val tnt = world.spawn(player.eyeLocation.add(player.location.direction.normalize()), TNTPrimed::class.java)
        tnt.fuseTicks = DYNAMITE_FUSE_TICKS
        tnt.velocity = player.location.direction.normalize().multiply(1.1)
        tnt.persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
        tnt.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
        tnt.source = player

        consumeOne(player, item)
        player.playSound(player.location, Sound.ENTITY_TNT_PRIMED, 1f, 1.1f)
    }

    @EventHandler(ignoreCancelled = true)
    fun onExplode(event: EntityExplodeEvent) {
        val tnt = event.entity as? TNTPrimed ?: return
        if (!tnt.persistentDataContainer.has(markerKey, PersistentDataType.BYTE)) return

        event.blockList().clear()
        rememberExplosion(tnt.location)

        val ownerId = tnt.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
            ?.let(java.util.UUID::fromString)
        val player = ownerId?.let { tnt.server.getPlayer(it) }

        if (player == null || !player.isOnline) return
        if (player.world.name != "mine") return

        explodeMineBlocks(player, tnt.location)
        ScoreboardManager.updateBoard(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDynamiteDamage(event: EntityDamageByEntityEvent) {
        val tnt = event.damager as? TNTPrimed ?: return
        if (!tnt.persistentDataContainer.has(markerKey, PersistentDataType.BYTE)) return
        event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onExplosionDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION &&
            event.cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
        ) return

        pruneRecentExplosions()
        val entityLoc = event.entity.location
        val matchedExplosion = recentExplosions.any { explosion ->
            explosion.worldName == entityLoc.world?.name &&
                entityLoc.distanceSquared(explosion.location) <= explosion.radiusSquared
        }
        if (matchedExplosion) {
            event.isCancelled = true
        }
    }

    private fun explodeMineBlocks(player: Player, center: org.bukkit.Location) {
        val data = DataStore.get(player.uniqueId)
        val radiusSquared = blastRadius * blastRadius
        val blocksToExplode = mutableListOf<ExplodedBlock>()

        val minX = kotlin.math.floor(center.x - blastRadius).toInt()
        val maxX = kotlin.math.floor(center.x + blastRadius).toInt()
        val minY = kotlin.math.floor(center.y - blastRadius).toInt()
        val maxY = kotlin.math.floor(center.y + blastRadius).toInt()
        val minZ = kotlin.math.floor(center.z - blastRadius).toInt()
        val maxZ = kotlin.math.floor(center.z + blastRadius).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = center.world.getBlockAt(x, y, z)
                    if (block.type == Material.AIR || block.type == Material.RAW_GOLD_BLOCK) continue

                    val dx = block.x + 0.5 - center.x
                    val dy = block.y + 0.5 - center.y
                    val dz = block.z + 0.5 - center.z
                    val distanceSquared = (dx * dx) + (dy * dy) + (dz * dz)
                    if (distanceSquared > radiusSquared) continue

                    val stored = MineManager.getStoredOre(block.location)
                    val actualType = stored ?: block.type
                    if (!MineManager.mineableBlocks.contains(actualType)) continue
                    if (!MineManager.containsMineArea(block.location)) continue
                    if (!MineManager.canBreakMineBlock(player, block.location)) continue

                    if (stored != null && block.type != stored) {
                        block.type = stored
                    }
                    val blockData = block.blockData.clone()
                    val fortuneMaxLevel = data.fortuneMaxLevel
                    val fortuneScrollBonus = 0.0
                    val blockDrops = if (actualType in MineManager.valuables) {
                        MineManager.getConfiguredDrops(actualType, data.fortuneLevel, fortuneMaxLevel, fortuneScrollBonus)
                    } else {
                        block.getDrops(ItemStack(Material.DIAMOND_PICKAXE), player).map {
                            it.clone().apply {
                                amount = BlockRemovalManager.rollScaledAmount(
                                    amount,
                                    BlockRemovalManager.getFortuneMultiplier(actualType, data.fortuneLevel, fortuneMaxLevel, fortuneScrollBonus)
                                )
                            }
                        }
                    }

                    blocksToExplode += ExplodedBlock(block, actualType, blockData, blockDrops, distanceSquared)
                }
            }
        }

        val blocksMined = blocksToExplode.size
        if (blocksMined <= 0) return

        blocksToExplode
            .sortedBy { it.distanceSquared }
            .forEachIndexed { index, exploded ->
                val distanceDelay = (sqrt(exploded.distanceSquared) * DYNAMITE_WAVE_DELAY_MULTIPLIER).roundToLong()
                val rippleDelay = (index / 5).toLong()
                val startDelay = distanceDelay + rippleDelay
                AnimationManager.breakBlock(
                    player,
                    exploded.block.location,
                    exploded.material,
                    exploded.blockData,
                    durationTicks = DYNAMITE_ANIMATION_DURATION_TICKS,
                    startDelayTicks = startDelay,
                    onStart = {
                        BlockRemovalManager.announceRareFind(player, exploded.material)
                        BlockRemovalManager.deliverDrops(player, exploded.drops, exploded.block.location)
                        ExperienceManager.giveValuableExperience(player, exploded.material)
                    }
                )
            }

        MineManager.recordBlocksMined(blocksMined)
        data.blocksMined += blocksMined
        BossbarManager.blocksMinedGlobally += blocksMined
        if (BossbarManager.isBlocksMinedEvent && !BossbarManager.isActive) {
            BossbarManager.updateBlocksMinedEvent()
        }
        player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1f)
    }

    private fun consumeOne(player: Player, item: ItemStack) {
        if (player.gameMode.name == "CREATIVE") return

        if (item.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            item.amount -= 1
        }
    }

    private fun rememberExplosion(location: org.bukkit.Location) {
        pruneRecentExplosions()
        recentExplosions.addLast(
            RecentExplosion(
                worldName = location.world?.name ?: return,
                location = location.clone(),
                radiusSquared = (blastRadius + 2.0) * (blastRadius + 2.0),
                recordedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun pruneRecentExplosions() {
        val cutoff = System.currentTimeMillis() - RECENT_EXPLOSION_WINDOW_MS
        while (recentExplosions.isNotEmpty() && recentExplosions.first().recordedAtMs < cutoff) {
            recentExplosions.removeFirst()
        }
    }

    private data class ExplodedBlock(
        val block: Block,
        val material: Material,
        val blockData: BlockData,
        val drops: List<ItemStack>,
        val distanceSquared: Double
    )

    private data class RecentExplosion(
        val worldName: String,
        val location: org.bukkit.Location,
        val radiusSquared: Double,
        val recordedAtMs: Long
    )
}
