package com.example.test

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sqrt

class DynamiteListener : Listener {
    private val ownerKey = NamespacedKey(TestPlugin.instance, "dynamite_owner")
    private val markerKey = NamespacedKey(TestPlugin.instance, "mobsarena_dynamite")
    private val explosiveTypeKey = NamespacedKey(TestPlugin.instance, "mobsarena_explosive_type")
    private val upgradeSnowballKey = NamespacedKey(TestPlugin.instance, "mobsarena_upgrade_snowball")
    private val normalExplosiveType = "normal"
    private val chargedExplosiveType = "charged"
    private val nukeExplosiveType = "nuke"
    private val normalBlastRadius = 3.5
    private val chargedBlastRadius = 4.2
    private val nukeBlastRadius = 10.5
    private val upgradeSnowballRadius = 3.5
    private val recentExplosions = ArrayDeque<RecentExplosion>()

    private companion object {
        const val RECENT_EXPLOSION_WINDOW_MS = 500L
        const val TOTAL_DYNAMITE_ANIMATION_TICKS = 10L
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.name.contains("RIGHT_CLICK")) return

        val player = event.player
        val item = event.item ?: return
        when {
            ItemManager.isDynamite(item) -> launchDynamite(event, player, item, normalExplosiveType)
            ItemManager.isChargedDynamite(item) -> launchDynamite(event, player, item, chargedExplosiveType)
            ItemManager.isNuke(item) -> launchDynamite(event, player, item, nukeExplosiveType)
            ItemManager.isUpgradeSnowball(item) -> launchUpgradeSnowball(event, player, item)
        }
    }

    private fun launchDynamite(event: PlayerInteractEvent, player: Player, item: ItemStack, explosiveType: String) {
        if (!canUseMineThrowable(event, player)) return

        val world = player.world
        val tnt = world.spawn(player.eyeLocation.add(player.location.direction.normalize()), TNTPrimed::class.java)
        tnt.fuseTicks = getFuseTicks(explosiveType)
        tnt.velocity = player.location.direction.normalize().multiply(1.1)
        tnt.persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
        tnt.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
        tnt.persistentDataContainer.set(explosiveTypeKey, PersistentDataType.STRING, explosiveType)
        tnt.source = player

        consumeOne(player, item)
        val pitch = when (explosiveType) {
            chargedExplosiveType -> 0.9f
            nukeExplosiveType -> 0.7f
            else -> 1.1f
        }
        player.playSound(player.location, Sound.ENTITY_TNT_PRIMED, 1f, pitch)
    }

    private fun launchUpgradeSnowball(event: PlayerInteractEvent, player: Player, item: ItemStack) {
        if (!canUseMineThrowable(event, player)) return

        val projectile = player.launchProjectile(Snowball::class.java)
        projectile.item = ItemManager.makeUpgradeSnowball()
        projectile.velocity = player.location.direction.normalize().multiply(1.6)
        projectile.persistentDataContainer.set(upgradeSnowballKey, PersistentDataType.BYTE, 1)
        projectile.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())

        consumeOne(player, item)
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.9f)
    }

    private fun canUseMineThrowable(event: PlayerInteractEvent, player: Player): Boolean {
        if (!MineManager.containsMineAreaXZ(player.location)) {
            event.isCancelled = true
            TextUtil.showTitle(player, "", "&cYou need to be in the mine to use this item", 0, 40, 0)
            return false
        }
        if (!MineManager.canBreakMineAreaIgnoringY(player, player.location)) {
            event.isCancelled = true
            TextUtil.showTitle(player, "&cNo mine access", "&7You need shared access or miner rank to use throwables here.", 0, 40, 10)
            return false
        }

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        event.isCancelled = true
        return true
    }

    @EventHandler(ignoreCancelled = true)
    fun onExplode(event: EntityExplodeEvent) {
        val tnt = event.entity as? TNTPrimed ?: return
        if (!tnt.persistentDataContainer.has(markerKey, PersistentDataType.BYTE)) return

        val blastRadius = getBlastRadius(tnt)
        event.blockList().clear()
        rememberExplosion(tnt.location, blastRadius)

        val ownerId = tnt.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
            ?.let(java.util.UUID::fromString)
        val player = ownerId?.let { tnt.server.getPlayer(it) }

        if (player == null || !player.isOnline) return
        if (player.world.name != "mine") return

        explodeMineBlocks(player, tnt.location, blastRadius, getExplosiveType(tnt))
        ScoreboardManager.updateBoard(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onUpgradeSnowballHit(event: ProjectileHitEvent) {
        val projectile = event.entity as? Snowball ?: return
        if (!projectile.persistentDataContainer.has(upgradeSnowballKey, PersistentDataType.BYTE)) return

        val ownerId = projectile.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
            ?.let(java.util.UUID::fromString)
        val player = ownerId?.let { projectile.server.getPlayer(it) } ?: return
        if (!player.isOnline || player.world.name != "mine") return

        val impactBlock = event.hitBlock ?: projectile.location.block
        upgradeMineBlocks(player, impactBlock)
        projectile.world.playSound(projectile.location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.2f)
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

    private fun explodeMineBlocks(player: Player, center: org.bukkit.Location, blastRadius: Double, explosiveType: String) {
        val data = DataStore.get(player.uniqueId)
        val radiusSquared = blastRadius * blastRadius
        val blocksToExplode = mutableListOf<ExplodedBlock>()
        val animationDurationTicks = getAnimationDurationTicks(explosiveType)
        val waveDelayMultiplier = getWaveDelayMultiplier(explosiveType)
        val rippleGroupSize = getRippleGroupSize(explosiveType)

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
        val maxStartDelay = (TOTAL_DYNAMITE_ANIMATION_TICKS - animationDurationTicks).coerceAtLeast(0)

        blocksToExplode
            .sortedBy { it.distanceSquared }
            .forEachIndexed { index, exploded ->
                val distanceDelay = (sqrt(exploded.distanceSquared) * waveDelayMultiplier).roundToLong()
                val rippleDelay = (index / rippleGroupSize).toLong()
                val startDelay = (distanceDelay + rippleDelay).coerceAtMost(maxStartDelay)
                AnimationManager.breakBlock(
                    player,
                    exploded.block.location,
                    exploded.material,
                    exploded.blockData,
                    durationTicks = animationDurationTicks,
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
        if (!TutorialManager.isTutorialMode(player)) {
            BossbarManager.blocksMinedGlobally += blocksMined
            if (BossbarManager.isBlocksMinedEvent && !BossbarManager.isActive) {
                BossbarManager.updateBlocksMinedEvent()
            }
        }
        player.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1f)
    }

    private fun upgradeMineBlocks(player: Player, hitBlock: Block) {
        val data = DataStore.get(player.uniqueId)
        val center = hitBlock.location.clone().add(0.5, 0.5, 0.5)
        val radiusSquared = upgradeSnowballRadius * upgradeSnowballRadius
        var upgradedBlocks = 0

        val minX = floor(center.x - upgradeSnowballRadius).toInt()
        val maxX = floor(center.x + upgradeSnowballRadius).toInt()
        val minY = floor(center.y - upgradeSnowballRadius).toInt()
        val maxY = floor(center.y + upgradeSnowballRadius).toInt()
        val minZ = floor(center.z - upgradeSnowballRadius).toInt()
        val maxZ = floor(center.z + upgradeSnowballRadius).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = center.world.getBlockAt(x, y, z)
                    val actualType = MineManager.getStoredOre(block.location) ?: block.type
                    if (actualType == Material.AIR || actualType == Material.RAW_GOLD_BLOCK) continue
                    if (!MineManager.mineableBlocks.contains(actualType)) continue
                    if (!MineManager.containsMineArea(block.location)) continue
                    if (!MineManager.canBreakMineBlock(player, block.location)) continue

                    val dx = block.x + 0.5 - center.x
                    val dy = block.y + 0.5 - center.y
                    val dz = block.z + 0.5 - center.z
                    val distanceSquared = (dx * dx) + (dy * dy) + (dz * dz)
                    if (distanceSquared > radiusSquared) continue

                    val tiers = (3 - floor(sqrt(distanceSquared)).toInt()).coerceAtLeast(1)
                    if (MineManager.upgradeBlockByTiers(block, data, tiers)) {
                        upgradedBlocks++
                    }
                }
            }
        }

        if (upgradedBlocks > 0) {
            player.sendMessage(TextUtil.colorize("&bUpgrade Snowball &7upgraded &f$upgradedBlocks &7block${if (upgradedBlocks == 1) "" else "s"}."))
        }
    }

    private fun consumeOne(player: Player, item: ItemStack) {
        if (player.gameMode.name == "CREATIVE") return

        if (item.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            item.amount -= 1
        }
    }

    private fun rememberExplosion(location: org.bukkit.Location, blastRadius: Double) {
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

    private fun getExplosiveType(tnt: TNTPrimed): String =
        tnt.persistentDataContainer.get(explosiveTypeKey, PersistentDataType.STRING) ?: normalExplosiveType

    private fun getFuseTicks(explosiveType: String): Int =
        when (explosiveType) {
            chargedExplosiveType -> 55
            nukeExplosiveType -> 70
            else -> 40
        }

    private fun getAnimationDurationTicks(explosiveType: String): Int =
        when (explosiveType) {
            chargedExplosiveType -> 3
            nukeExplosiveType -> 2
            else -> 4
        }

    private fun getWaveDelayMultiplier(explosiveType: String): Double =
        when (explosiveType) {
            chargedExplosiveType -> 0.35
            nukeExplosiveType -> 0.18
            else -> 0.5
        }

    private fun getRippleGroupSize(explosiveType: String): Int =
        when (explosiveType) {
            chargedExplosiveType -> 20
            nukeExplosiveType -> 40
            else -> 14
        }

    private fun getBlastRadius(tnt: TNTPrimed): Double =
        when (getExplosiveType(tnt)) {
            chargedExplosiveType -> chargedBlastRadius
            nukeExplosiveType -> nukeBlastRadius
            else -> normalBlastRadius
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
