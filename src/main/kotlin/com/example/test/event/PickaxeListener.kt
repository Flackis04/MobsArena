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
import kotlin.math.ceil
import kotlin.random.Random

class PickaxeListener : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (player.world.name != "mine") return
        if (DangerZoneCubeManager.tryBreakCube(player, event.block)) {
            event.isCancelled = true
            return
        }
        if (!MineManager.containsMine(event.block.location) && !player.hasPermission("command.dev")) {
            event.isCancelled = true
            return
        }
        if (!player.hasPermission("command.dev") && !MineManager.canBreakMineBlock(player, event.block.location)) {
            event.isCancelled = true
            if (!MineManager.isProtectedMineBorder(event.block.location)) {
                TextUtil.showTitle(player, "&cTrying to steal?", "&7Miner Rank required", 0, 40, 10)
            }
            return
        }

        val block = event.block

        if (!hasCustomPickaxe(player)) return
        event.isCancelled = true

        val data = DataStore.get(player.uniqueId)
        activateMiningProcs(player, data)
        RetentionUpgradeManager.recordMineSwing(player)

        val context = createBreakContext(player, block, data)
        triggerMineStrikeProcs(player, data, context.originLoc.blockY)
        breakPrimaryBlock(context)

        val extraBlockResult = BlockRemovalManager.handleBlockRemoval(
            player = player,
            block = block,
            blockQuantity = if (!UpgradeToggleManager.isEnabled(data, "multiBreak")) {
                0.0
            } else {
                getMultiBreakBlockQuantity(
                    UpgradeToggleManager.getEffectiveLevel(data, "multiBreak", data.multiBreakLevel),
                    ItemManager.isProcBooster(player.inventory.itemInOffHand),
                    data.multiBreakMaxLevel,
                    0.0,
                    data.excavatorActive,
                    if (UpgradeToggleManager.isEnabled(data, "excavatorEfficiency")) {
                        ceil(data.excavatorEfficiencyLevel.toDouble()).toInt()
                    } else {
                        1
                    }
                )
            }
        )

        val minedMaterials = listOf(context.actualType) + extraBlockResult.materialsMined

        SessionTimelineManager.recordMining(
            player,
            minedMaterials,
            context.originLoc
        )
        val tokensEarned = RetentionUpgradeManager.tryAwardTokens(player, minedMaterials)
        val keysFound = RetentionUpgradeManager.tryAwardKeys(player, minedMaterials)

        updateMiningProgress(player, data, extraBlockResult.totalBlocksMined, context.actualType, tokensEarned, keysFound)
    }

    private fun activateMiningProcs(player: Player, data: PlayerData) {
        val procPowerBonus = UpgradeToggleManager.getProcPowerBonus(data)
        tryActivateTimedProc(
            player = player,
            key = "oreBoost",
            isActive = data.oreBoostActive,
            activationChance = if (!UpgradeToggleManager.isEnabled(data, "oreBoost")) {
                0.0
            } else {
                MineManager.applyProcMultiplier(
                    UpgradeFormulas.getOreBoostChance(data.oreBoostLevel, data.oreBoostMaxLevel, 0.0) +
                        procPowerBonus +
                        MasteryManager.getActivationChanceBonus(data, "oreBoost"),
                    player
                )
            },
            activationMessage = "&dOre Boost activated for 5 seconds!",
            durationTicks = 20L * 5L,
            activate = { data.oreBoostActive = true },
            deactivate = { data.oreBoostActive = false }
        )

        tryActivateTimedProc(
            player = player,
            key = "excavator",
            isActive = data.excavatorActive,
            activationChance = if (!UpgradeToggleManager.isEnabled(data, "excavator")) {
                0.0
            } else {
                MineManager.applyProcMultiplier(
                    UpgradeFormulas.getExcavatorChance(data.excavatorLevel, data.excavatorMaxLevel, 0.0) +
                        procPowerBonus +
                        MasteryManager.getActivationChanceBonus(data, "excavator"),
                    player
                )
            },
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
        RetentionUpgradeManager.queueActivationMessage(player.uniqueId, activationMessage)
        org.bukkit.Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            deactivate()
        }, durationTicks)
    }

    private fun createBreakContext(player: Player, block: Block, data: PlayerData): BreakContext {
        val originLoc = block.location
        val actualType = revealStoredOreIfNeeded(block)
        val blockData = block.blockData.clone()
        val drops = resolveDrops(
            player,
            block,
            actualType,
            UpgradeToggleManager.getEffectiveLevel(data, "fortune", data.fortuneLevel)
        )
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
        val data = DataStore.get(player.uniqueId)
        if (actualType in MineManager.valuables) {
            return MineManager.getConfiguredDrops(actualType, fortuneLevel, data, data.fortuneMaxLevel, 0.0)
        }

        return block.getDrops(ItemStack(player.inventory.itemInMainHand.type), player).map { drop ->
            drop.clone().apply {
                amount = BlockRemovalManager.rollScaledAmount(amount, BlockRemovalManager.getFortuneMultiplier(actualType, fortuneLevel, data, data.fortuneMaxLevel, 0.0))
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

    private fun updateMiningProgress(
        player: Player,
        data: PlayerData,
        blocksMined: Int,
        actualType: Material,
        tokensEarned: Long,
        keysFound: List<KeyRarity>
    ) {
        if (actualType in MineManager.mineableBlocks) {
            val mineOwnerId = MineManager.getMineOwnerAt(player.location) ?: player.uniqueId
            MineManager.recordBlocksMined(mineOwnerId)
        }

        data.blocksMined += blocksMined
        ClanManager.recordBlocksMined(player.uniqueId, blocksMined)
        val actionBarParts = mutableListOf<String>()
        actionBarParts += RetentionUpgradeManager.consumeActivationMessages(player.uniqueId)
        if (tokensEarned > 0L) {
            actionBarParts += "&b+${TextUtil.formatNum(tokensEarned)} tokens"
        }
        if (keysFound.isNotEmpty()) {
            actionBarParts += keysFound.groupingBy { it }.eachCount()
                .entries
                .sortedBy { it.key.ordinal }
                .joinToString(" &8| ") { (rarity, amount) ->
                    "${rarity.color}+${TextUtil.formatNum(amount.toLong())} ${rarity.displayName.lowercase()} key"
                }
        }
        if (actionBarParts.isNotEmpty()) {
            ActionBarManager.sendActionBarFor(player, 1.2, actionBarParts.joinToString(" &8| "))
        }
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
        val expected = ItemManager.makePickaxe(data, player.level, ItemManager.isProcBooster(player.inventory.itemInOffHand))
        return item.itemMeta?.displayName() == expected.itemMeta?.displayName()
    }

    private fun triggerMineStrikeProcs(player: Player, data: PlayerData, yLevel: Int) {
        val procPowerBonus = UpgradeToggleManager.getProcPowerBonus(data)
        val lightningChance = if (!UpgradeToggleManager.isEnabled(data, "lightning")) {
            0.0
        } else {
            MineManager.applyProcMultiplier(
                UpgradeFormulas.getLightningChance(
                    data.lightningLevel,
                    data.lightningMaxLevel,
                    ScrollManager.getBonus(data, UpgradeScrollType.LIGHTNING)
                ) + procPowerBonus +
                    MasteryManager.getActivationChanceBonus(data, "lightning"),
                player
            )
        }

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

        val virtualJackhammerChance = if (!UpgradeToggleManager.isEnabled(data, "virtualJackhammer")) {
            0.0
        } else {
            MineManager.applyProcMultiplier(
                UpgradeFormulas.getVirtualJackhammerChance(
                    data.virtualJackhammerLevel,
                    data.virtualJackhammerMaxLevel,
                    ScrollManager.getBonus(data, UpgradeScrollType.VIRTUAL_JACKHAMMER)
                ) + procPowerBonus +
                    MasteryManager.getActivationChanceBonus(data, "virtualJackhammer"),
                player
            )
        }

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
