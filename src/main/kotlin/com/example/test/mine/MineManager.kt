package com.example.test

import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

object MineManager {
    private const val HALF_BOX_SIZE = 20 //max = 20 or 60
    private const val MINE_MIN_Y = 32
    private const val MINE_MAX_Y = 52
    private const val INITIAL_MINE_CENTER_X = 0
    private const val INITIAL_MINE_CENTER_Z = -12
    private const val SPAWN_X = 0.5
    private const val SPAWN_Y = 100.0
    private const val SPAWN_Z = -40.5
    private const val MINE_GAP_BLOCKS = 3
    private const val ZONE_BUILD_LIMIT = 132
    private const val RESET_THRESHOLD = 0.04

    private val mineOre = mutableMapOf<String, Material>()
    private val veinBounds = mutableListOf<Cuboid>()
    private var placedVeins = 0
    private val mineStride = (HALF_BOX_SIZE * 2) + 1 + MINE_GAP_BLOCKS
    private val defaultMineLayout = createMineLayout(INITIAL_MINE_CENTER_X, INITIAL_MINE_CENTER_Z)
    private val playerMines = linkedMapOf<UUID, PlayerMine>()
    val mine: Cuboid get() = defaultMineLayout.mine
    val mineArea: Cuboid get() = defaultMineLayout.mineArea
    private val singleMineBlocks = mine.volume()

    val valuables = listOf(
        Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.AMETHYST_BLOCK,
        Material.GOLD_BLOCK,
        Material.REDSTONE_BLOCK,
        Material.LAPIS_BLOCK,
        Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK,
        Material.ANCIENT_DEBRIS,
        Material.NETHERITE_BLOCK,
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.MAGMA_BLOCK,
        Material.RESPAWN_ANCHOR,
        Material.SCULK,
        Material.SCULK_CATALYST,
        Material.BEACON,
        Material.OCHRE_FROGLIGHT,
        Material.VERDANT_FROGLIGHT,
        Material.PEARLESCENT_FROGLIGHT,
        Material.SPAWNER,
        Material.TRIAL_SPAWNER
    )

    val valuableNames = listOf(
        "<#FFD700>RAW GOLD",
        "<#00FFFF>DIAMOND",
        "<#50C878>EMERALD",
        "<#A06CD5>AMETHYST BLOCK",
        "<#FFD700>GOLD BLOCK",
        "<#FF3030>REDSTONE BLOCK",
        "<#4D8DFF>LAPIS BLOCK",
        "<#00FFFF>DIAMOND BLOCK",
        "<#50C878>EMERALD BLOCK",
        "<#5A3A2E>ANCIENT DEBRIS",
        "<#4C515A>NETHERITE BLOCK",
        "<#551A8B>OBSIDIAN",
        "<#FF00FF>CRYING OBSIDIAN",
        "<#FF6A00>MAGMA BLOCK",
        "<#FFFF66>RESPAWN ANCHOR",
        "<#4A6A5A>SCULK",
        "<#6EC6FF>SCULK CATALYST",
        "<#87F5FF>BEACON",
        "<#D89A4A>OCHRE FROGLIGHT",
        "<#6FD66F>VERDANT FROGLIGHT",
        "<#FFB7FF>PEARLESCENT FROGLIGHT",
        "<#6A5ACD>MONSTER SPAWNER",
        "<#E6E6FA>TRIAL SPAWNER"
    )

    val valuableDrops = listOf(
        Material.RAW_GOLD,
        Material.DIAMOND,
        Material.EMERALD,
        Material.AMETHYST_BLOCK,
        Material.GOLD_BLOCK,
        Material.REDSTONE_BLOCK,
        Material.LAPIS_BLOCK,
        Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK,
        Material.ANCIENT_DEBRIS,
        Material.NETHERITE_BLOCK,
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.MAGMA_BLOCK,
        Material.RESPAWN_ANCHOR,
        Material.SCULK,
        Material.SCULK_CATALYST,
        Material.BEACON,
        Material.OCHRE_FROGLIGHT,
        Material.VERDANT_FROGLIGHT,
        Material.PEARLESCENT_FROGLIGHT,
        Material.SPAWNER,
        Material.TRIAL_SPAWNER
    )

    val valuableSellValues = listOf(
        2L,
        3L,
        6L,
        12L,
        25L,
        50L,
        85L,
        150L,
        260L,
        450L,
        800L,
        1600L,
        4000L,
        7000L,
        10000L,
        15000L,
        35000L,
        80000L,
        180000L,
        300000L,
        500000L,
        900000L,
        1500000L
    )

    val valuableSpawnWeights = listOf(
        2.0,
        1.4,
        0.8,
        0.45,
        0.20,
        0.10,
        0.06,
        0.03,
        0.0175,
        0.01,
        0.0055,
        0.0025,
        0.001,
        0.0006,
        0.00045,
        0.00032,
        0.00015,
        0.000075,
        0.000016, // 0.000011 this value works
        0.000008,
        0.000004,
        0.0000025,
        0.000001
    )

    val nonDroppableBlocks = setOf(
        Material.COBBLED_DEEPSLATE,
        Material.DEEPSLATE_TILE_SLAB
    )

    val mineableBlocks = setOf(
        Material.DEEPSLATE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.AMETHYST_BLOCK,
        Material.GOLD_BLOCK,
        Material.REDSTONE_BLOCK,
        Material.LAPIS_BLOCK,
        Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK,
        Material.ANCIENT_DEBRIS,
        Material.NETHERITE_BLOCK,
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.MAGMA_BLOCK,
        Material.SCULK,
        Material.SCULK_CATALYST,
        Material.RESPAWN_ANCHOR,
        Material.BEACON,
        Material.OCHRE_FROGLIGHT,
        Material.VERDANT_FROGLIGHT,
        Material.PEARLESCENT_FROGLIGHT,
        Material.SPAWNER,
        Material.TRIAL_SPAWNER
    )

    private val world: World? get() = Bukkit.getWorld("mine")
    private val random = ThreadLocalRandom.current()

    fun init() {
        setMineWorldSpawn()
        MineStatusHologram.init()
        restoreOnlinePlayerMines()

        Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            Bukkit.broadcast(TextUtil.toComponent("&9Blocks Cleared!"))
        }, 20L * 60L * 60L, 20L * 60L * 60L)
    }

    fun ensureMineFor(player: Player, teleportToMineTop: Boolean = false) {
        val w = world ?: return
        val existing = playerMines[player.uniqueId]
        if (existing != null) {
            if (teleportToMineTop) {
                teleportToMineTop(player, existing.centerX, existing.centerZ, w)
            }
            return
        }

        val data = DataStore.get(player.uniqueId)
        val savedCenter = getSavedMineCenter(data)
        val (centerX, centerZ) = if (savedCenter != null && canUseSavedMineCenter(player.uniqueId, savedCenter.first, savedCenter.second)) {
            savedCenter
        } else {
            findNearestMineCenter()
        }
        val layout = createMineLayout(centerX, centerZ)
        playerMines[player.uniqueId] = PlayerMine(player.uniqueId, centerX, centerZ, layout.mine, layout.mineArea)
        data.mineCenterX = centerX
        data.mineCenterZ = centerZ
        val eventMultiplier = if (RareOresEventManager.isActive()) RareOresEventManager.getValuableWeightMultiplier() else 1.0
        fillMineForLayout(layout, getOwnerValuableMultiplier(player.uniqueId, eventMultiplier))
        if (teleportToMineTop) {
            teleportToMineTop(player, layout.centerX, layout.centerZ, w)
        }
    }

    fun removeMineFor(playerId: UUID) {
        val w = world ?: return
        val mineData = playerMines.remove(playerId) ?: return
        mineData.mine.forEach(w) { block ->
            mineOre.remove(locKey(block.location))
            block.type = Material.AIR
        }
        clearMineBedrockFloor(w, mineData.mine)
    }

    fun containsMine(loc: Location): Boolean = getMineAt(loc) != null

    fun containsMineArea(loc: Location): Boolean = getMineAreaAt(loc) != null

    fun containsMineAreaXZ(loc: Location): Boolean = getMineAreaAtIgnoringY(loc) != null

    fun getMineOwnerAt(loc: Location): UUID? = getMineAt(loc)?.ownerId

    fun getMineAreaOwnerAt(loc: Location): UUID? = getMineAreaAt(loc)?.ownerId

    fun getMineAreaOwnerAtIgnoringY(loc: Location): UUID? = getMineAreaAtIgnoringY(loc)?.ownerId

    fun isPlayersOwnMine(player: Player, loc: Location): Boolean = getMineOwnerAt(loc) == player.uniqueId

    fun hasMinerRank(player: Player): Boolean {
        val data = DataStore.get(player.uniqueId)
        return data.hasDonorRank || player.hasPermission("group.miner") || player.hasPermission("rank.miner")
    }

    fun getProcMultiplier(player: Player): Double =
        getProcMultiplier(DataStore.get(player.uniqueId), player)

    fun getProcMultiplier(data: PlayerData, player: Player? = null): Double = when {
        data.donorRankMultiplier >= 5.0 -> 1.5
        data.donorRankMultiplier >= 2.5 -> 1.25
        data.donorRankMultiplier >= 1.0 -> 1.1
        player != null && hasMinerRank(player) -> 1.1
        else -> 1.0
    }

    fun applyProcMultiplier(baseChance: Double, player: Player): Double =
        (baseChance * getProcMultiplier(player)).coerceAtMost(1.0)

    fun canBreakMineBlock(player: Player, loc: Location): Boolean {
        val owner = getMineOwnerAt(loc) ?: return false
        return owner == player.uniqueId || MineAccessManager.canAccessMine(owner, player.uniqueId) || hasMinerRank(player)
    }

    fun canBreakMineArea(player: Player, loc: Location): Boolean {
        val owner = getMineAreaOwnerAt(loc) ?: return false
        return owner == player.uniqueId || MineAccessManager.canAccessMine(owner, player.uniqueId) || hasMinerRank(player)
    }

    fun canBreakMineAreaIgnoringY(player: Player, loc: Location): Boolean {
        val owner = getMineAreaOwnerAtIgnoringY(loc) ?: return false
        return owner == player.uniqueId || MineAccessManager.canAccessMine(owner, player.uniqueId) || hasMinerRank(player)
    }

    fun setMine(valuableWeightMultiplier: Double = 1.0) {
        mineOre.clear()
        veinBounds.clear()
        placedVeins = 0

        val w = world ?: return
        playerMines.values.forEach { mineData ->
            mineData.minedBlocksSinceReset = 0
            mineData.resetQueued = false
            fillMineForLayout(
                MineLayout(mineData.centerX, mineData.centerZ, mineData.mine, mineData.mineArea),
                getOwnerValuableMultiplier(mineData.ownerId, valuableWeightMultiplier)
            )
        }

        Bukkit.getOnlinePlayers().forEach { player ->
            val mineData = playerMines[player.uniqueId] ?: return@forEach
            if (player.world != w) return@forEach
            if (mineData.mine.contains(player.location)) {
                teleportPlayerToMineTopAtCurrentXZ(player, mineData.mine, w)
            }
        }
    }

    fun refreshMineFor(ownerId: UUID) {
        val w = world ?: return
        val mineData = playerMines[ownerId] ?: return
        val layout = MineLayout(mineData.centerX, mineData.centerZ, mineData.mine, mineData.mineArea)
        mineData.minedBlocksSinceReset = 0
        mineData.resetQueued = false

        layout.mine.forEach(w) { block ->
            mineOre.remove(locKey(block.location))
        }

        val eventMultiplier = if (RareOresEventManager.isActive()) {
            RareOresEventManager.getValuableWeightMultiplier()
        } else {
            1.0
        }
        fillMineForLayout(layout, getOwnerValuableMultiplier(ownerId, eventMultiplier))

        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.world != w) return@forEach
            if (mineData.mine.contains(player.location)) {
                teleportPlayerToMineTopAtCurrentXZ(player, mineData.mine, w)
            }
        }
    }

    fun getTotalValuableMultiplier(valuableWeightMultiplier: Double = 1.0): Double {
        return valuableWeightMultiplier * getMineAreaPlayerBonusMultiplier()
    }

    fun getNetMineWeightMultiplier(ownerId: UUID): Double {
        val eventMultiplier = if (RareOresEventManager.isActive()) {
            RareOresEventManager.getValuableWeightMultiplier()
        } else {
            1.0
        }
        return getOwnerValuableMultiplier(ownerId, eventMultiplier)
    }

    fun getOreFrequencyMultiplier(level: Int, maxLevel: Int = LevelManager.oreFrequencyMaxLevel, scrollBonus: Double = 0.0): Double =
        UpgradeFormulas.getOreFrequencyMultiplier(level, maxLevel, scrollBonus)

    private fun getMineAreaPlayerBonusMultiplier(): Double {
        val w = world ?: return 1.0
        val uniqueMultipliers = linkedSetOf<Double>()
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.world != w || !containsMineArea(player.location)) continue
            uniqueMultipliers += DataStore.get(player.uniqueId).mineWeightBonusMultiplier.coerceAtLeast(1.0)
        }

        var highestMultiplier = 1.0
        for (multiplier in uniqueMultipliers) {
            if (multiplier > highestMultiplier) {
                highestMultiplier = multiplier
            }
        }
        return highestMultiplier
    }

    fun startRareOresReset(durationTicks: Long, valuableWeightMultiplier: Double = BossbarManager.weightMultiplier) {
        setMine(valuableWeightMultiplier)
    }

    fun endRareOresReset() {
        setMine()
        Bukkit.broadcast(TextUtil.toComponent("&9Mine Reset!"))
    }

    private fun resetMine(ownerId: UUID) {
        val mineData = playerMines[ownerId] ?: return
        veinBounds.clear()
        placedVeins = 0
        mineData.minedBlocksSinceReset = 0
        mineData.resetQueued = false
        if (RareOresEventManager.isActive()) {
            refreshMineFor(ownerId)
            Bukkit.getPlayer(ownerId)?.sendMessage(TextUtil.colorize("&9Your mine reset!"))
            return
        }
        refreshMineFor(ownerId)
        Bukkit.getPlayer(ownerId)?.sendMessage(TextUtil.colorize("&9Your mine reset!"))
    }

    fun getCenterLocation(heightOffset: Double = 0.0): Location {
        val w = world ?: error("World 'mine' is not loaded")
        return Location(w, INITIAL_MINE_CENTER_X + 0.5, MINE_MAX_Y + 0.5 + heightOffset, INITIAL_MINE_CENTER_Z + 0.5)
    }

    fun getSpawnLocation(): Location {
        val w = world ?: error("World 'mine' is not loaded")
        return Location(w, SPAWN_X, SPAWN_Y, SPAWN_Z, 0f, 0f)
    }

    fun setMineWorldSpawn() {
        val w = world ?: return
        w.setSpawnLocation(getSpawnLocation())
    }

    fun getPlayerMineCenterLocation(player: Player, heightOffset: Double = 0.0): Location? {
        val w = world ?: return null
        val mineData = playerMines[player.uniqueId] ?: return null
        return Location(w, mineData.centerX + 0.5, MINE_MAX_Y + 0.5 + heightOffset + 1, mineData.centerZ + 0.5)
    }

    fun getPlayerMineCenterLocation(ownerId: UUID, heightOffset: Double = 0.0): Location? {
        val w = world ?: return null
        val mineData = playerMines[ownerId] ?: return null
        return Location(w, mineData.centerX + 0.5, MINE_MAX_Y + 0.5 + heightOffset, mineData.centerZ + 0.5)
    }

    fun getPlayerMine(ownerId: UUID): PlayerMine? = playerMines[ownerId]

    fun getPlayerMines(): List<PlayerMine> = playerMines.values.toList()

    private fun restoreOnlinePlayerMines() {
        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                ensureMineFor(player)
            }
        })
    }

    private fun getSavedMineCenter(data: PlayerData): Pair<Int, Int>? {
        if (data.mineCenterX == Int.MIN_VALUE || data.mineCenterZ == Int.MIN_VALUE) return null
        return data.mineCenterX to data.mineCenterZ
    }

    private fun canUseSavedMineCenter(ownerId: UUID, centerX: Int, centerZ: Int): Boolean =
        isMineCenterAvailable(ownerId, centerX, centerZ)

    fun getPlayersInMineArea(): Int {
        val w = world ?: return 0
        return Bukkit.getOnlinePlayers()
            .filter { it.world == w && containsMineArea(it.location) }
            .size
    }

    fun getActivePlayersInMine(): List<Player> {
        val w = world ?: return emptyList()
        return Bukkit.getOnlinePlayers()
            .filter { it.world == w && containsMineArea(it.location) }
            .filter { player -> !ActivityTracker.isAfk(player) }
    }

    fun getClearedPercent(ownerId: UUID): Double {
        val mineData = playerMines[ownerId] ?: return 0.0
        return mineData.minedBlocksSinceReset.toDouble() / singleMineBlocks.toDouble() * 100.0
    }

    fun getClearedPercentText(ownerId: UUID): String = String.format("%.1f%%", getClearedPercent(ownerId))

    fun recordBlocksMined(ownerId: UUID, amount: Int = 1) {
        if (amount <= 0) return
        val mineData = playerMines[ownerId] ?: return
        mineData.minedBlocksSinceReset += amount
        if (mineData.resetQueued) return

        val clearedPercent = mineData.minedBlocksSinceReset.toDouble() / singleMineBlocks.toDouble()
        if (clearedPercent >= RESET_THRESHOLD) {
            mineData.resetQueued = true
            Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
                if (mineData.resetQueued) {
                    resetMine(ownerId)
                }
            })
        }
    }

    fun revealBlock(loc: Location) {
        val block = loc.block
        if (!mineableBlocks.contains(block.type)) return
        val key = locKey(loc)
        val stored = mineOre[key]
        if (stored != null) {
            block.type = stored
        }
    }

    fun revealExposedBlocks(origin: Block) {
        getFaceAdjacentBlocks(origin).forEach { adjacent ->
            revealBlock(adjacent.location)
        }
    }

    fun getStoredOre(loc: Location): Material? = mineOre[locKey(loc)]

    fun removeStoredOre(loc: Location) {
        mineOre.remove(locKey(loc))
    }

    fun createValuableItem(dropType: Material, amount: Int): ItemStack {
        val valuableIndex = valuableDrops.indexOf(dropType)
        if (valuableIndex == -1) return ItemStack(dropType, amount)

        return ItemStack(dropType, amount).apply {
            editMeta { meta ->
                meta.displayName(
                    TextUtil.toComponent(valuableNames[valuableIndex])
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&dRarity: &b1 in ${formatOdds(valuableSpawnWeights[valuableIndex])}")
                            .decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&aSell Value: &b${TextUtil.formatNum(valuableSellValues[valuableIndex])} ${ItemManager.COIN_NAME_PLURAL}")
                            .decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&9XP: &b${ExperienceManager.getValuableExperience(dropType)}")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                )
            }
        }
    }

    fun getConfiguredDrops(blockType: Material, fortuneLevel: Int, fortuneMaxLevel: Int = LevelManager.fortuneMaxLevel, fortuneScrollBonus: Double = 0.0): List<ItemStack> {
        val valuableIndex = valuables.indexOf(blockType)
        if (valuableIndex == -1) return emptyList()

        val dropType = valuableDrops[valuableIndex]
        val amount = BlockRemovalManager.rollFortuneAmount(blockType, fortuneLevel, fortuneMaxLevel, fortuneScrollBonus)
        return listOf(createValuableItem(dropType, amount))
    }

    fun getRandomMineBlock(valuableWeightMultiplier: Double = 1.0): Material {
        val totalValuableMultiplier = getTotalValuableMultiplier(valuableWeightMultiplier)
        return selectMineMaterial(totalValuableMultiplier)
    }

    fun triggerLightningUpgrade(player: Player, sendMessage: Boolean = true): Boolean {
        val data = DataStore.get(player.uniqueId)
        val mineData = playerMines[player.uniqueId] ?: return false
        val targetBlock = getRandomLightningTargetBlock(mineData.mine) ?: return false
        targetBlock.world.spawnEntity(targetBlock.location.clone().add(0.5, 0.0, 0.5), EntityType.LIGHTNING_BOLT)
        val radius = (if (MasteryManager.hasLightningAreaUpgrade(data)) 2 else 1) +
            UpgradeFormulas.getProcPowerLightningRadiusBonus(data.procPowerLevel, data.procPowerMaxLevel)
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val block = targetBlock.world.getBlockAt(targetBlock.x + dx, targetBlock.y, targetBlock.z + dz)
                if (!mineData.mine.contains(block.location)) continue
                upgradeBlockToNextLightningTier(block, data)
            }
        }
        if (sendMessage) {
            RetentionUpgradeManager.queueActivationMessage(player.uniqueId, "&eLightning activated")
        }
        return true
    }

    fun triggerVirtualJackhammer(player: Player, yLevel: Int): Boolean {
        val activeWorld = world ?: return false
        val mineData = playerMines[player.uniqueId] ?: return false
        if (yLevel !in MINE_MIN_Y..MINE_MAX_Y) return false

        val aggregatedDrops = linkedMapOf<Material, Int>()
        var totalBaseExperience = 0.0
        val ownerData = DataStore.get(player.uniqueId)
        val fortuneLevel = ownerData.fortuneLevel
        val fortuneMaxLevel = ownerData.fortuneMaxLevel
        val fortuneScrollBonus = 0.0
        val clearedLayers = if (MasteryManager.getMasteryLevel(ownerData, "virtualJackhammer") >= 7) {
            buildList {
                add(yLevel)
                val secondLayer = if (yLevel > MINE_MIN_Y) yLevel - 1 else (yLevel + 1).coerceAtMost(MINE_MAX_Y)
                if (secondLayer != yLevel) add(secondLayer)
            }
        } else {
            listOf(yLevel)
        }
        var blocksCleared = 0

        for (targetY in clearedLayers) {
            for (x in mineData.mine.minX()..mineData.mine.maxX()) {
                for (z in mineData.mine.minZ()..mineData.mine.maxZ()) {
                    val block = activeWorld.getBlockAt(x, targetY, z)
                    if (!mineData.mine.contains(block.location)) continue

                    val actualType = getStoredOre(block.location) ?: block.type
                    if (actualType !in mineableBlocks) continue
                    val drops = if (actualType in valuables) {
                        getConfiguredDrops(actualType, fortuneLevel, fortuneMaxLevel, fortuneScrollBonus)
                    } else {
                        block.getDrops(ItemStack(Material.DIAMOND_PICKAXE), player).map { drop ->
                            drop.clone().apply {
                                amount = BlockRemovalManager.rollScaledAmount(
                                    amount,
                                    BlockRemovalManager.getFortuneMultiplier(actualType, fortuneLevel, fortuneMaxLevel, fortuneScrollBonus)
                                )
                            }
                        }
                    }
                    for (drop in drops) {
                        if (drop.type == Material.AIR || drop.amount <= 0) continue
                        aggregatedDrops[drop.type] = (aggregatedDrops[drop.type] ?: 0) + drop.amount
                    }
                    totalBaseExperience += ExperienceManager.getValuableExperience(actualType)
                    removeStoredOre(block.location)
                    block.type = Material.AIR
                    revealExposedBlocks(block)
                    blocksCleared++
                }
            }
        }

        if (aggregatedDrops.isEmpty() || blocksCleared <= 0) return false

        val jackhammerMultiplier = UpgradeFormulas.getProcPowerJackhammerMultiplier(ownerData.procPowerLevel, ownerData.procPowerMaxLevel)
        if (jackhammerMultiplier > 1.0) {
            aggregatedDrops.replaceAll { _, amount ->
                BlockRemovalManager.rollScaledAmount(amount, jackhammerMultiplier)
            }
        }

        val rarestDrop = aggregatedDrops.maxByOrNull { (material, _) -> valuableDrops.indexOf(material) } ?: return false
        val rarestIndex = valuableDrops.indexOf(rarestDrop.key)
        val rarestName = if (rarestIndex >= 0) valuableNames[rarestIndex] else rarestDrop.key.name
        val rarestAmount = rarestDrop.value

        val payoutDrops = aggregatedDrops.map { (material, amount) -> createValuableItem(material, amount) }
        BlockRemovalManager.deliverDrops(player, payoutDrops, player.location)
        ExperienceManager.giveRawExperience(player, totalBaseExperience)
        recordBlocksMined(player.uniqueId, blocksCleared)
        player.sendMessage(
            TextUtil.colorize(
                if (clearedLayers.size >= 2) {
                    "&6Jackhammer cleared the entire Y${clearedLayers[0]} and Y${clearedLayers[1]} layers in your mine."
                } else {
                    "&6Jackhammer cleared the entire Y${yLevel} layer in your mine."
                }
            )
        )
        player.sendMessage(TextUtil.colorize("&7Rarest find: $rarestName &7x&f${TextUtil.formatNum(rarestAmount)}"))
        RetentionUpgradeManager.queueActivationMessage(
            player.uniqueId,
            if (clearedLayers.size >= 2) "&6Jackhammer x2 layers" else "&6Jackhammer activated"
        )
        return true
    }

    fun mineValuableForAutoMiner(ownerId: UUID, luckMultiplier: Double, fortuneLevel: Int): ItemStack? {
        val w = world ?: return null
        val mineData = playerMines[ownerId] ?: return null
        repeat(32) {
            val x = random.nextInt(mineData.mine.minX(), mineData.mine.maxX() + 1)
            val y = random.nextInt(MINE_MIN_Y, MINE_MAX_Y + 1)
            val z = random.nextInt(mineData.mine.minZ(), mineData.mine.maxZ() + 1)
            val block = w.getBlockAt(x, y, z)
            if (!mineData.mine.contains(block.location)) return@repeat
            val actualType = getStoredOre(block.location) ?: block.type
            if (actualType !in valuables) return@repeat
            val dropType = valuableDrops[valuables.indexOf(actualType)]
            val ownerData = DataStore.get(ownerId)
            val amount = max(1, BlockRemovalManager.rollScaledAmount(1, BlockRemovalManager.getFortuneMultiplier(actualType, fortuneLevel, ownerData.fortuneMaxLevel, 0.0) * luckMultiplier))
            return ItemStack(dropType, amount)
        }
        return null
    }

    private fun getRandomLightningTargetBlock(targetMine: Cuboid): Block? {
        val activeWorld = world ?: return null
        repeat(20) {
            val x = random.nextInt(targetMine.minX(), targetMine.maxX() + 1)
            val z = random.nextInt(targetMine.minZ(), targetMine.maxZ() + 1)
            for (y in MINE_MAX_Y downTo MINE_MIN_Y) {
                val block = activeWorld.getBlockAt(x, y, z)
                val actualType = getStoredOre(block.location) ?: block.type
                if (actualType == Material.DEEPSLATE || actualType in valuables) {
                    return block
                }
            }
        }
        return null
    }

    fun upgradeBlockByTiers(block: Block, data: PlayerData, tiers: Int): Boolean {
        val appliedTiers = tiers.coerceAtLeast(0)
        if (appliedTiers <= 0) return false

        var currentType = getStoredOre(block.location) ?: block.type
        var upgraded = false
        repeat(appliedTiers) {
            val nextType = getNextLightningTier(currentType, data) ?: return@repeat
            currentType = nextType
            upgraded = true
        }

        if (!upgraded) return false
        mineOre[locKey(block.location)] = currentType
        block.type = currentType
        return true
    }

    private fun upgradeBlockToNextLightningTier(block: Block, data: PlayerData) {
        upgradeBlockByTiers(block, data, 1)
    }

    private fun getNextLightningTier(material: Material, data: PlayerData): Material? {
        val tierSkip = MasteryManager.getLightningTierSkip(data) +
            UpgradeFormulas.getProcPowerLightningTierBonus(data.procPowerLevel, data.procPowerMaxLevel)
        if (material == Material.DEEPSLATE) {
            return valuables.getOrNull(tierSkip) ?: valuables.last()
        }

        val currentIndex = valuables.indexOf(material)
        if (currentIndex == -1) return null
        return valuables.getOrNull(currentIndex + 1 + tierSkip) ?: valuables.last()
    }

    private fun formatOdds(weight: Double): String {
        if (weight <= 0.0) return "?"
        val oneIn = kotlin.math.round(100.0 / weight).toLong().coerceAtLeast(1L)
        return TextUtil.formatNum(oneIn)
    }

    fun getFaceAdjacentBlocks(block: Block): List<Block> {
        val offsets = listOf(
            Triple(0, 0, 1), Triple(0, 0, -1),
            Triple(0, 1, 0), Triple(0, -1, 0),
            Triple(1, 0, 0), Triple(-1, 0, 0)
        )

        return offsets.mapNotNull { (dx, dy, dz) ->
            block.world.getBlockAt(block.x + dx, block.y + dy, block.z + dz)
                .takeIf { it.type != Material.AIR }
        }
    }

    fun getBlocks(block: Block, player: Player, range: Int): List<Block> {
        val blocks = mutableListOf<Block>()
        val direction = player.location.direction
        val absX = kotlin.math.abs(direction.x)
        val absY = kotlin.math.abs(direction.y)
        val absZ = kotlin.math.abs(direction.z)
        val halfRange = range / 2

        if (absY >= absX && absY >= absZ) {
            val forward = if (direction.y >= 0) 1 else -1
            for (dx in -range..range) {
                for (depth in -halfRange..halfRange) {
                    for (dz in -range..range) {
                        val candidate = block.world.getBlockAt(
                            block.x + dx,
                            block.y + forward + depth,
                            block.z + dz
                        )
                        if (candidate.type != Material.AIR) {
                            blocks += candidate
                        }
                    }
                }
            }
            return blocks
        }

        if (absX >= absZ) {
            val forward = if (direction.x >= 0) 1 else -1
            for (depth in -halfRange..halfRange) {
                for (dy in -range..range) {
                    for (dz in -range..range) {
                        val candidate = block.world.getBlockAt(
                            block.x + forward + depth,
                            block.y + dy,
                            block.z + dz
                        )
                        if (candidate.type != Material.AIR) {
                            blocks += candidate
                        }
                    }
                }
            }
            return blocks
        }

        val forward = if (direction.z >= 0) 1 else -1
        for (dx in -range..range) {
            for (dy in -range..range) {
                for (depth in -halfRange..halfRange) {
                    val candidate = block.world.getBlockAt(
                        block.x + dx,
                        block.y + dy,
                        block.z + forward + depth
                    )
                    if (candidate.type != Material.AIR) {
                        blocks += candidate
                    }
                }
            }
        }
        return blocks
    }

    fun getClosestBlocks(origin: Block, blocks: Collection<Block>, blocksQuantityRoll: Int): List<Block> {
        if (blocksQuantityRoll <= 0) return emptyList()

        return blocks.asSequence()
            .filter { it.location != origin.location }
            .groupBy { candidate ->
                val dx = candidate.x - origin.x
                val dy = candidate.y - origin.y
                val dz = candidate.z - origin.z
                (dx * dx) + (dy * dy) + (dz * dz)
            }
            .toSortedMap()
            .values
            .asSequence()
            .flatMap { it.shuffled().asSequence() }
            .take(blocksQuantityRoll)
            .toList()
    }

    private fun fillMineForLayout(layout: MineLayout, valuableWeightMultiplier: Double) {
        val w = world ?: return
        clearMineBedrockFloor(w, layout.mine)
        fillMineWeighted(w, layout.mine, valuableWeightMultiplier)
    }

    private fun selectMineMaterial(valuableWeightMultiplier: Double): Material {
        val safeMultiplier = valuableWeightMultiplier.coerceAtLeast(0.0)
        var chosen: Material? = null

        for (index in valuables.indices) {
            val chancePercent = valuableSpawnWeights[index] * safeMultiplier
            if (chancePercent <= 0.0) continue

            if (random.nextDouble(100.0) <= chancePercent) {
                chosen = valuables[index]
            }
        }

        return chosen ?: Material.DEEPSLATE
    }

    private fun getOwnerValuableMultiplier(ownerId: UUID, eventWeightMultiplier: Double): Double {
        val ownerData = DataStore.get(ownerId)
        val effectiveEventMultiplier = if (TutorialManager.isRunning(ownerData)) 1.0 else eventWeightMultiplier
        val donorMineWeight = ownerData.mineWeightBonusMultiplier.coerceAtLeast(1.0)
        val ascensionMineWeight = getAscensionMineWeightMultiplier(ownerData)
        val oreFrequency = getOreFrequencyMultiplier(ownerData.oreFrequencyLevel, ownerData.oreFrequencyMaxLevel, ScrollManager.getBonus(ownerData, UpgradeScrollType.ORE_FREQUENCY))
        val clanMineWeight = ClanManager.getMineWeightMultiplier(ownerId)
        return effectiveEventMultiplier * donorMineWeight * ascensionMineWeight * oreFrequency * clanMineWeight
    }

    private fun findNearestMineCenter(): Pair<Int, Int> {
        if (playerMines.isEmpty()) return INITIAL_MINE_CENTER_X to INITIAL_MINE_CENTER_Z
        var radius = 0
        while (radius < ZONE_BUILD_LIMIT) {
            val candidates = ringCandidates(radius)
                .map { (gx, gz) ->
                    (INITIAL_MINE_CENTER_X + (gx * mineStride)) to (INITIAL_MINE_CENTER_Z + (gz * mineStride))
                }
                .sortedBy { (x, z) ->
                    val dx = x - INITIAL_MINE_CENTER_X
                    val dz = z - INITIAL_MINE_CENTER_Z
                    (dx * dx) + (dz * dz)
                }
            for ((centerX, centerZ) in candidates) {
                if (isMineCenterAvailable(null, centerX, centerZ)) return centerX to centerZ
            }
            radius++
        }
        return INITIAL_MINE_CENTER_X to INITIAL_MINE_CENTER_Z
    }

    private fun isMineCenterAvailable(ownerId: UUID?, centerX: Int, centerZ: Int): Boolean {
        val candidate = createMineLayout(centerX, centerZ).mine
        return playerMines.values.none { existing ->
            existing.ownerId != ownerId && existing.mine.expand(MINE_GAP_BLOCKS).intersects(candidate)
        }
    }

    private fun ringCandidates(radius: Int): List<Pair<Int, Int>> {
        if (radius == 0) return listOf(0 to 0)
        val points = mutableListOf<Pair<Int, Int>>()
        for (x in -radius..radius) {
            points += x to -radius
            points += x to radius
        }
        for (z in (-radius + 1) until radius) {
            points += -radius to z
            points += radius to z
        }
        return points
    }

    private fun getMineAt(loc: Location): PlayerMine? =
        playerMines.values.firstOrNull { it.mine.contains(loc) }

    private fun getMineAreaAt(loc: Location): PlayerMine? =
        playerMines.values.firstOrNull { it.mineArea.contains(loc) }

    private fun getMineAreaAtIgnoringY(loc: Location): PlayerMine? =
        playerMines.values.firstOrNull { it.mineArea.containsXZ(loc) }

    private fun teleportToMineTop(player: Player, centerX: Int, centerZ: Int, w: World) {
        player.teleport(Location(w, centerX + 0.5, (MINE_MAX_Y + 1).toDouble(), centerZ + 0.5, player.location.yaw, player.location.pitch))
    }

    private fun teleportPlayerToMineTopAtCurrentXZ(player: Player, mine: Cuboid, w: World) {
        val x = player.location.x.coerceIn(mine.minX().toDouble(), mine.maxX() + 0.999999)
        val z = player.location.z.coerceIn(mine.minZ().toDouble(), mine.maxZ() + 0.999999)
        player.teleport(Location(w, x, (MINE_MAX_Y + 1).toDouble(), z, player.location.yaw, player.location.pitch))
    }

    private fun createMineLayout(centerX: Int, centerZ: Int): MineLayout {
        return MineLayout(
            centerX = centerX,
            centerZ = centerZ,
            mine = Cuboid(
                centerX - HALF_BOX_SIZE,
                MINE_MIN_Y,
                centerZ - HALF_BOX_SIZE,
                centerX + HALF_BOX_SIZE,
                MINE_MAX_Y,
                centerZ + HALF_BOX_SIZE
            ),
            mineArea = Cuboid(
                centerX - HALF_BOX_SIZE,
                MINE_MIN_Y,
                centerZ - HALF_BOX_SIZE,
                centerX + HALF_BOX_SIZE,
                MINE_MAX_Y + 100,
                centerZ + HALF_BOX_SIZE
            )
        )
    }

    private fun fillMineWeighted(world: World, cuboid: Cuboid, valuableWeightMultiplier: Double) {
        val batch = 6000
        val iter = cuboid.positions().iterator()

        object : BukkitRunnable() {
            override fun run() {
                var count = 0
                while (iter.hasNext() && count < batch) {
                    val (x, y, z) = iter.next()
                    val chosen = selectMineMaterial(valuableWeightMultiplier)
                    val block = world.getBlockAt(x, y, z)
                    if (chosen != Material.DEEPSLATE && y != 64) {
                        mineOre[locKey(block.location)] = chosen
                        block.type = Material.DEEPSLATE
                    } else {
                        block.type = chosen
                    }
                    count++
                }
                if (!iter.hasNext()) {
                    revealStoredSurfaceBlocks(world, cuboid)
                    cancel()
                }
            }
        }.runTaskTimer(TestPlugin.instance, 0L, 1L)
    }

    private fun clearMineBedrockFloor(world: World, cuboid: Cuboid) {
        val floorY = cuboid.minY() - 1
        for (x in cuboid.minX()..cuboid.maxX()) {
            for (z in cuboid.minZ()..cuboid.maxZ()) {
                val block = world.getBlockAt(x, floorY, z)
                if (block.type == Material.BEDROCK) {
                    block.type = Material.AIR
                }
            }
        }
    }

    private fun revealStoredSurfaceBlocks(world: World, cuboid: Cuboid) {
        for ((x, y, z) in cuboid.positions()) {
            val block = world.getBlockAt(x, y, z)
            val stored = mineOre[locKey(block.location)] ?: continue
            if (isSurfaceExposed(block)) {
                block.type = stored
            }
        }
    }

    private fun isSurfaceExposed(block: Block): Boolean {
        val offsets = arrayOf(
            intArrayOf(1, 0, 0),
            intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0),
            intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1),
            intArrayOf(0, 0, -1)
        )
        for (offset in offsets) {
            val adjacent = block.world.getBlockAt(
                block.x + offset[0],
                block.y + offset[1],
                block.z + offset[2]
            )
            if (adjacent.type == Material.AIR) return true
        }
        return false
    }

    private fun locKey(loc: Location): String = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"

    data class MineLayout(
        val centerX: Int,
        val centerZ: Int,
        val mine: Cuboid,
        val mineArea: Cuboid
    )

    data class PlayerMine(
        val ownerId: UUID,
        val centerX: Int,
        val centerZ: Int,
        val mine: Cuboid,
        val mineArea: Cuboid,
        var minedBlocksSinceReset: Int = 0,
        var resetQueued: Boolean = false
    )

    data class Cuboid(val x1: Int, val y1: Int, val z1: Int, val x2: Int, val y2: Int, val z2: Int) {
        fun minX(): Int = minOf(x1, x2)
        fun maxX(): Int = maxOf(x1, x2)
        fun minY(): Int = minOf(y1, y2)
        fun maxY(): Int = maxOf(y1, y2)
        fun minZ(): Int = minOf(z1, z2)
        fun maxZ(): Int = maxOf(z1, z2)

        fun expand(xz: Int): Cuboid = Cuboid(minX() - xz, minY(), minZ() - xz, maxX() + xz, maxY(), maxZ() + xz)

        fun intersects(other: Cuboid): Boolean {
            return maxX() >= other.minX() && minX() <= other.maxX() &&
                maxY() >= other.minY() && minY() <= other.maxY() &&
                maxZ() >= other.minZ() && minZ() <= other.maxZ()
        }

        fun contains(loc: Location): Boolean {
            val x = loc.blockX
            val y = loc.blockY
            val z = loc.blockZ
            return x in minX()..maxX() &&
                y in minY()..maxY() &&
                z in minZ()..maxZ()
        }

        fun containsXZ(loc: Location): Boolean {
            val x = loc.blockX
            val z = loc.blockZ
            return x in minX()..maxX() && z in minZ()..maxZ()
        }

        fun size(): Int = volume()

        fun volume(): Int = (maxX() - minX() + 1) *
            (maxY() - minY() + 1) *
            (maxZ() - minZ() + 1)

        fun positions(): Sequence<Triple<Int, Int, Int>> = sequence {
            for (x in minX()..maxX()) {
                for (y in minY()..maxY()) {
                    for (z in minZ()..maxZ()) {
                        yield(Triple(x, y, z))
                    }
                }
            }
        }

        fun forEach(world: World, action: (Block) -> Unit) {
            for ((x, y, z) in positions()) {
                action(world.getBlockAt(x, y, z))
            }
        }
    }
}
