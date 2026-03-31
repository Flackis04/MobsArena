package com.example.test

import com.example.test.BlockRemovalManager.getFortuneMultiplier
import com.example.test.UpgradeFormulas.getMultiBreakBlockQuantity
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

class PickaxeListener : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (player.world.name != "mine") return
        if (!MineManager.containsMine(event.block.location) && !player.hasPermission("command.dev")) {
            event.isCancelled = true
            return
        }
        if (!player.hasPermission("command.dev") && !MineManager.canBreakMineBlock(player, event.block.location)) {
            event.isCancelled = true
            TextUtil.showTitle(player, "&cTrying to steal?", "&7Miner Rank required", 0, 40, 10)
            return
        }

        val block = event.block

        if (!hasCustomPickaxe(player)) return
        event.isCancelled = true

        val data = DataStore.get(player.uniqueId)
        activateMiningProcs(player, data)

        val context = createBreakContext(player, block, data)
        triggerMineStrikeProcs(player, data, context.originLoc.blockY)
        breakPrimaryBlock(context)

        val extraBlockResult = BlockRemovalManager.handleBlockRemoval(
            player = player,
            block = block,
            blockQuantity = getMultiBreakBlockQuantity(
                data.multiBreakLevel,
                ItemManager.isProcBooster(player.inventory.itemInOffHand),
                data.multiBreakMaxLevel,
                0.0,
                data.excavatorActive,
                data.excavatorEfficiencyLevel
            )
        )

        SessionTimelineManager.recordMining(
            player,
            listOf(context.actualType) + extraBlockResult.materialsMined,
            context.originLoc
        )

        updateMiningProgress(player, data, extraBlockResult.totalBlocksMined, context.actualType)
    }

    private fun activateMiningProcs(player: Player, data: PlayerData) {
        tryActivateTimedProc(
            player = player,
            key = "oreBoost",
            isActive = data.oreBoostActive,
            activationChance = MineManager.applyProcMultiplier(
                UpgradeFormulas.getOreBoostChance(data.oreBoostLevel, data.oreBoostMaxLevel, 0.0) +
                    MasteryManager.getActivationChanceBonus(data, "oreBoost"),
                player
            ),
            activationMessage = "&dOre Boost activated for 5 seconds!",
            durationTicks = 20L * 5L,
            activate = { data.oreBoostActive = true },
            deactivate = { data.oreBoostActive = false }
        )

        tryActivateTimedProc(
            player = player,
            key = "excavator",
            isActive = data.excavatorActive,
            activationChance = MineManager.applyProcMultiplier(
                UpgradeFormulas.getExcavatorChance(data.excavatorLevel, data.excavatorMaxLevel, 0.0) +
                    MasteryManager.getActivationChanceBonus(data, "excavator"),
                player
            ),
            activationMessage = "&dExcavator activated for 15 seconds!",
            durationTicks = 20L * 15L,
            activate = { data.excavatorActive = true },
            deactivate = { data.excavatorActive = false }
        )

    }

    private fun tryActivateTimedProc(
        player: Player,
        key: String,
        isActive: Boolean,
        activationChance: Double,
        activationMessage: String,
        durationTicks: Long,
        activate: () -> Unit,
        deactivate: () -> Unit
    ) {
        if (isActive || Random.nextDouble() > activationChance) return

        activate()
        MasteryManager.recordActivation(player, key)
        player.sendMessage(TextUtil.colorize(activationMessage))
        org.bukkit.Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            deactivate()
        }, durationTicks)
    }

    private fun createBreakContext(player: Player, block: Block, data: PlayerData): BreakContext {
        val originLoc = block.location
        val actualType = revealStoredOreIfNeeded(block)
        val blockData = block.blockData.clone()
        val drops = resolveDrops(player, block, actualType, data.fortuneLevel)
        return BreakContext(
            player = player,
            block = block,
            originLoc = originLoc,
            actualType = actualType,
            blockData = blockData,
            drops = drops
        )
    }

    private fun revealStoredOreIfNeeded(block: Block): Material {
        val stored = MineManager.getStoredOre(block.location)
        val actualType = stored ?: block.type
        if (stored != null && block.type != stored) {
            block.type = stored
        }
        return actualType
    }

    private fun resolveDrops(player: Player, block: Block, actualType: Material, fortuneLevel: Int): List<ItemStack> {
        if (actualType in MineManager.valuables) {
            val data = DataStore.get(player.uniqueId)
            return MineManager.getConfiguredDrops(actualType, fortuneLevel, data.fortuneMaxLevel, 0.0)
        }

        return block.getDrops(ItemStack(player.inventory.itemInMainHand.type), player).map { drop ->
            val data = DataStore.get(player.uniqueId)
            drop.clone().apply {
                amount = BlockRemovalManager.rollScaledAmount(amount, getFortuneMultiplier(actualType, fortuneLevel, data.fortuneMaxLevel, 0.0))
            }
        }
    }

    private fun breakPrimaryBlock(context: BreakContext) {
        val onBreak = {
            BlockRemovalManager.playMiningFeedback(
                player = context.player,
                blockType = context.actualType,
                location = context.originLoc,
                blockData = context.blockData,
                oreBoostActive = DataStore.get(context.player.uniqueId).oreBoostActive,
                excavatorActive = DataStore.get(context.player.uniqueId).excavatorActive
            )
            BlockRemovalManager.announceRareFind(context.player, context.actualType)
            BlockRemovalManager.deliverDrops(context.player, context.drops, context.originLoc)
            ExperienceManager.giveValuableExperience(context.player, context.actualType)
            if (context.actualType in MineManager.valuables) {
                TutorialManager.handleValuableBreak(context.player)
            }
            if (context.actualType in MineManager.mineableBlocks) {
                ScrollManager.tryAwardMiningScroll(context.player)
            }
        }
        if (context.actualType == Material.TRIAL_SPAWNER) {
            context.block.type = Material.AIR
            MineManager.removeStoredOre(context.originLoc)
            MineManager.revealExposedBlocks(context.block)
            onBreak()
            return
        }

        AnimationManager.breakBlock(
            player = context.player,
            loc = context.originLoc,
            material = context.actualType,
            blockData = context.blockData,
            onStart = onBreak
        )
    }

    private fun updateMiningProgress(player: Player, data: PlayerData, blocksMined: Int, actualType: Material) {
        if (actualType in MineManager.mineableBlocks) {
            MineManager.recordBlocksMined()
        }

        data.blocksMined += blocksMined
        if (!TutorialManager.isTutorialMode(player)) {
            BossbarManager.blocksMinedGlobally += blocksMined
            if (BossbarManager.isBlocksMinedEvent && !BossbarManager.isActive) {
                BossbarManager.updateBlocksMinedEvent()
            }
        }
        ScoreboardManager.updateBoard(player)
    }

    private fun hasCustomPickaxe(player: Player): Boolean {
        val item = player.inventory.itemInMainHand
        if (item.type != Material.DIAMOND_PICKAXE) return false

        val data = DataStore.get(player.uniqueId)
        val multiBreakChance = UpgradeFormulas.getMultiBreakBlockQuantity(
            data.multiBreakLevel,
            ItemManager.isProcBooster(player.inventory.itemInOffHand),
            data.multiBreakMaxLevel
        )
        val oreBoostChance = UpgradeFormulas.getOreBoostChance(data.oreBoostLevel, data.oreBoostMaxLevel, 0.0) * 100
        val excavatorChance = UpgradeFormulas.getExcavatorChance(data.excavatorLevel, data.excavatorMaxLevel, 0.0) * 100
        val expected = ItemManager.makePickaxe(
            data.rebirth,
            player.level,
            data.fortuneLevel,
            data.multiBreakLevel,
            multiBreakChance,
            data.oreBoostLevel,
            oreBoostChance,
            data.excavatorLevel,
            excavatorChance
        )
        return item.itemMeta?.displayName() == expected.itemMeta?.displayName()
    }

    private fun triggerMineStrikeProcs(player: Player, data: PlayerData, yLevel: Int) {
        val lightningChance = MineManager.applyProcMultiplier(
            UpgradeFormulas.getLightningChance(
                data.lightningLevel,
                data.lightningMaxLevel,
                ScrollManager.getBonus(data, UpgradeScrollType.LIGHTNING)
            ) + MasteryManager.getActivationChanceBonus(data, "lightning"),
            player
        )

        if (data.rebirth >= 1 && Random.nextDouble() <= lightningChance) {
            if (MineManager.triggerLightningUpgrade(player)) {
                MasteryManager.recordActivation(player, "lightning")
            }
        }

        if (data.rebirth >= 1 && MineManager.isPlayersOwnMine(player, player.location)) {
            if (LightningRodManager.tryTriggerTripleLightning(player, lightningChance)) {
                MasteryManager.recordActivation(player, "lightning")
            }
        }

        val virtualJackhammerChance = MineManager.applyProcMultiplier(
            UpgradeFormulas.getVirtualJackhammerChance(
                data.virtualJackhammerLevel,
                data.virtualJackhammerMaxLevel,
                ScrollManager.getBonus(data, UpgradeScrollType.VIRTUAL_JACKHAMMER)
            ) + MasteryManager.getActivationChanceBonus(data, "virtualJackhammer"),
            player
        )

        if (data.rebirth >= 1 && Random.nextDouble() <= virtualJackhammerChance) {
            if (MineManager.triggerVirtualJackhammer(player, yLevel)) {
                MasteryManager.recordActivation(player, "virtualJackhammer")
            }
        }
    }

    private data class BreakContext(
        val player: Player,
        val block: Block,
        val originLoc: org.bukkit.Location,
        val actualType: Material,
        val blockData: BlockData,
        val drops: List<ItemStack>
    )
}
