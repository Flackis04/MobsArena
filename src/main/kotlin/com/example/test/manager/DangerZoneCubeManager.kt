package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import kotlin.random.Random

object DangerZoneCubeManager {
    private const val SPAWN_INTERVAL_TICKS = 20L * 6L
    private const val SPAWN_CHANCE_PER_PLAYER = 0.22
    private const val MAX_ACTIVE_CUBES = 28
    private const val MAX_SPAWN_ATTEMPTS = 60
    private const val DANGER_ZONE_HALF_SIZE = 250
    private const val SPAWN_Y_LEVEL = 52
    private const val TOKENS_PER_BLOCK = 8L

    private val glassMaterials = listOf(
        Material.RED_STAINED_GLASS,
        Material.ORANGE_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS,
        Material.LIME_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS,
        Material.BLUE_STAINED_GLASS,
        Material.MAGENTA_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS
    )

    private val cubesById = mutableMapOf<String, DangerCube>()
    private val cubeIdByBlockKey = mutableMapOf<String, String>()

    fun init() {
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable { trySpawnRareCubes() },
            SPAWN_INTERVAL_TICKS,
            SPAWN_INTERVAL_TICKS
        )
    }

    fun shutdown() {
        val world = Bukkit.getWorld("mine") ?: return
        cubesById.values.toList().forEach { clearCube(world, it) }
        cubesById.clear()
        cubeIdByBlockKey.clear()
    }

    fun tryBreakCube(player: Player, block: Block): Boolean {
        if (block.world.name != "mine") return false
        val cubeId = cubeIdByBlockKey[blockKey(block.location)] ?: return false
        val cube = cubesById.remove(cubeId) ?: return false

        clearCube(block.world, cube)
        val tokensAwarded = cube.volume.toLong() * TOKENS_PER_BLOCK
        val data = DataStore.get(player.uniqueId)
        data.tokens += tokensAwarded
        ScoreboardManager.updateBoard(player)
        ActionBarManager.sendActionBarFor(player, 1.2, "&dDanger Cube &8| &b+${TextUtil.formatNum(tokensAwarded)} tokens")
        player.playSound(player.location, "entity.experience_orb.pickup", 0.7f, 1.35f)
        SessionTimelineManager.record(player, "Shattered a danger cube (${cube.size}x${cube.size}x${cube.size}) for ${TextUtil.formatNum(tokensAwarded)} tokens")
        return true
    }

    private fun trySpawnRareCubes() {
        val world = Bukkit.getWorld("mine") ?: return
        if (cubesById.size >= MAX_ACTIVE_CUBES) return

        Bukkit.getOnlinePlayers()
            .filter { isDangerZone(it.location) }
            .shuffled()
            .forEach { player ->
                if (cubesById.size >= MAX_ACTIVE_CUBES) return@forEach
                if (Random.nextDouble() > SPAWN_CHANCE_PER_PLAYER) return@forEach
                spawnCube(world)
                if (Random.nextDouble() <= 0.35 && cubesById.size < MAX_ACTIVE_CUBES) {
                    spawnCube(world)
                }
            }
    }

    private fun spawnCube(world: World) {
        val center = MineManager.getSpawnLocation()
        repeat(MAX_SPAWN_ATTEMPTS) {
            val size = Random.nextInt(1, 6)
            val minX = center.blockX - DANGER_ZONE_HALF_SIZE
            val minZ = center.blockZ - DANGER_ZONE_HALF_SIZE
            val maxOriginX = center.blockX + DANGER_ZONE_HALF_SIZE - size
            val maxOriginZ = center.blockZ + DANGER_ZONE_HALF_SIZE - size
            if (maxOriginX < minX || maxOriginZ < minZ) return@repeat
            val originX = Random.nextInt(minX, maxOriginX + 1)
            val originZ = Random.nextInt(minZ, maxOriginZ + 1)
            val cube = DangerCube(
                id = "danger_${System.nanoTime()}_${Random.nextInt(10_000)}",
                minX = originX,
                minY = SPAWN_Y_LEVEL,
                minZ = originZ,
                size = size,
                material = glassMaterials.random()
            )
            if (!canPlaceCube(world, cube)) return@repeat
            placeCube(world, cube)
            return
        }
    }

    private fun canPlaceCube(world: World, cube: DangerCube): Boolean {
        for (x in cube.minX..cube.maxX) {
            for (y in cube.minY..cube.maxY) {
                for (z in cube.minZ..cube.maxZ) {
                    val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    if (!isDangerZone(location)) return false
                    val key = blockKey(location)
                    if (cubeIdByBlockKey.containsKey(key)) return false
                    if (!world.getBlockAt(x, y, z).type.isAir) return false
                }
            }
        }
        return true
    }

    private fun placeCube(world: World, cube: DangerCube) {
        cubesById[cube.id] = cube
        for (x in cube.minX..cube.maxX) {
            for (y in cube.minY..cube.maxY) {
                for (z in cube.minZ..cube.maxZ) {
                    val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    world.getBlockAt(x, y, z).type = cube.material
                    cubeIdByBlockKey[blockKey(location)] = cube.id
                }
            }
        }
    }

    private fun clearCube(world: World, cube: DangerCube) {
        for (x in cube.minX..cube.maxX) {
            for (y in cube.minY..cube.maxY) {
                for (z in cube.minZ..cube.maxZ) {
                    val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    cubeIdByBlockKey.remove(blockKey(location))
                    val block = world.getBlockAt(x, y, z)
                    if (block.type == cube.material) {
                        block.type = Material.AIR
                    }
                }
            }
        }
    }

    private fun isDangerZone(location: Location): Boolean {
        if (location.world?.name != "mine") return false
        if (location.blockY < SPAWN_Y_LEVEL) return false
        return !MineManager.containsMineAreaXZ(location)
    }

    private fun blockKey(location: Location): String =
        "${location.world?.name}:${location.blockX},${location.blockY},${location.blockZ}"

    private data class DangerCube(
        val id: String,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val size: Int,
        val material: Material
    ) {
        val maxX: Int get() = minX + size - 1
        val maxY: Int get() = minY + size - 1
        val maxZ: Int get() = minZ + size - 1
        val volume: Int get() = size * size * size
    }
}
