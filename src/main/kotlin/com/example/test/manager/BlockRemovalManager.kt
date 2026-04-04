package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object BlockRemovalManager {

    data class RemovalResult(
        val totalBlocksMined: Int,
        val materialsMined: List<Material>
    )

    fun handleBlockRemoval(
        player: Player?,
        block: Block,
        blockQuantity: Double
    ): RemovalResult {

        var cumBlocksMined = 1
        val player = player ?: return RemovalResult(0, emptyList())
        val resolvedBlockQuantity = resolveBlockQuantity(blockQuantity)
        if (resolvedBlockQuantity <= 0) return RemovalResult(cumBlocksMined, emptyList())
        val blocks = MineManager.getBlocks(block, player, 2)
        val closestBlocks = MineManager.getClosestBlocks(block, blocks, resolvedBlockQuantity)
            .filter { MineManager.containsMine(it.location) }
            .filter { MineManager.canBreakMineBlock(player, it.location) }
        val data = DataStore.get(player.uniqueId)
        val configuredDelay = AnimationManager.clampExtraBlockDelayTicks(data.animationExtraBlockDelayTicks)
        val delayStepTicks = if (data.excavatorActive) {
            (configuredDelay - 1L).coerceAtLeast(AnimationManager.MIN_EXTRA_BLOCK_DELAY_TICKS)
        } else {
            configuredDelay
        }
        val materialsMined = mutableListOf<Material>()

        for ((index, b1) in closestBlocks.withIndex()) {
            val startDelayTicks = (index + 1L) * delayStepTicks
            materialsMined += processExtraBlock(player, b1, startDelayTicks)
            cumBlocksMined++
        } // else, reveal block on blocks furthest away
        return RemovalResult(cumBlocksMined, materialsMined)
    }

    private fun resolveBlockQuantity(blockQuantity: Double): Int {
        if (blockQuantity <= 0.0) return 0
        val wholeBlocks = kotlin.math.floor(blockQuantity).toInt()
        val fractionalChance = blockQuantity - wholeBlocks
        return if (Random.nextDouble() < fractionalChance) wholeBlocks + 1 else wholeBlocks
    }

    private fun processExtraBlock(player: Player, block: Block, startDelayTicks: Long): Material {
        val stored = MineManager.getStoredOre(block.location)
        val actualType = stored ?: block.type
        if (stored != null && block.type != stored) block.type = stored
        val blockData = block.blockData.clone()
        val data = DataStore.get(player.uniqueId)
        val fortuneLevel = UpgradeToggleManager.getEffectiveLevel(data, "fortune", data.fortuneLevel)
        val fortuneMaxLevel = data.fortuneMaxLevel
        val fortuneScrollBonus = 0.0
        val drops = if (actualType in MineManager.valuables) {
            MineManager.getConfiguredDrops(actualType, fortuneLevel, data, fortuneMaxLevel, fortuneScrollBonus)
        } else {
            block
                .getDrops(ItemStack(player.inventory.itemInMainHand.type), player)
                .map { drop ->
                    drop.clone().apply {
                        amount = rollScaledAmount(amount, getFortuneMultiplier(actualType, fortuneLevel, data, fortuneMaxLevel, fortuneScrollBonus))
                    }
                }
                .toList()
        }
        //player.sendMessage(drops.toString())
        if (MineManager.mineableBlocks.contains(actualType)) {
            val onBreak = {
                playMiningFeedback(player, actualType, block.location, blockData, data.oreBoostActive, data.excavatorActive)
                announceRareFind(player, actualType)
                deliverDrops(player, drops, block.location)
                ExperienceManager.giveValuableExperience(player, actualType)
                if (actualType in MineManager.valuables) {
                    TutorialManager.handleValuableBreak(player)
                }
                if (actualType in MineManager.mineableBlocks) {
                    ScrollManager.tryAwardMiningScroll(player)
                }
            }
            if (actualType == Material.TRIAL_SPAWNER) {
                block.type = Material.AIR
                MineManager.removeStoredOre(block.location)
                MineManager.revealExposedBlocks(block)
                onBreak()
            } else {
                AnimationManager.breakBlock(
                    player,
                    block.location,
                    actualType,
                    blockData,
                    startDelayTicks = startDelayTicks,
                    onStart = onBreak
                )
            }
        } else {
            playMiningFeedback(player, actualType, block.location, blockData, data.oreBoostActive, data.excavatorActive)
            deliverDrops(player, drops, block.location)
            ExperienceManager.giveValuableExperience(player, actualType)
            if (actualType in MineManager.valuables) {
                TutorialManager.handleValuableBreak(player)
            }
            ScrollManager.tryAwardMiningScroll(player)
        }
        return actualType
    }

    fun deliverDrops(player: Player?, drops: List<ItemStack>, originLoc: org.bukkit.Location) {
        if (player == null) {
            for (drop in drops) {
                if (drop.type == Material.AIR) continue
                originLoc.world?.dropItemNaturally(originLoc, drop)
            }
            return
        }

        val data = DataStore.get(player.uniqueId)
        val hasStorage = data.backpackEnabled &&
            (player.inventory.contents.any { ItemManager.isStorage(it) } || ItemManager.isStorage(player.inventory.itemInOffHand))

        for (drop in drops) {
            if (drop.type == Material.AIR) continue
            if (MineManager.nonDroppableBlocks.contains(drop.type)) continue

            val valuableIndex = MineManager.valuableDrops.indexOf(drop.type)
            if (valuableIndex != -1) {
                val clanAdjustedAmount = rollScaledAmount(drop.amount, ClanManager.getPlayerFortuneMultiplier(player.uniqueId))
                val comboAdjustedAmount = rollScaledAmount(clanAdjustedAmount, RetentionUpgradeManager.getComboMultiplier(player))
                val totalGenerated = comboAdjustedAmount
                if (totalGenerated > 0) {
                    val payoutStack = MineManager.createValuableItem(drop.type, totalGenerated)
                    if (!hasStorage) {
                        player.inventory.addItem(payoutStack)
                    } else {
                        val storedAmount = StorageManager.addDrop(player, payoutStack)
                        if (storedAmount < totalGenerated) {
                            val overflow = payoutStack.clone().apply { amount = totalGenerated - storedAmount }
                            val leftover = player.inventory.addItem(overflow)
                            if (leftover.isNotEmpty()) {
                                leftover.values.forEach { originLoc.world?.dropItemNaturally(originLoc, it) }
                            }
                        }
                    }
                }
                RetentionUpgradeManager.tryApplyJackpot(player, originLoc)

                MasteryManager.recordValuableCollection(player, drop.type, totalGenerated)
                if (totalGenerated > 0) {
                    player.playSound(
                        player.location,
                        SoundManager.ITEM_PICKUP_SOUND,
                        SoundManager.ITEM_PICKUP_VOLUME,
                        SoundManager.getValuablePickupPitch(valuableIndex, oreBoostActive = data.oreBoostActive, excavatorActive = data.excavatorActive)
                    )
                }
                continue
            }

            val leftover = player.inventory.addItem(drop)
            if (leftover.isNotEmpty()) {
                leftover.values.forEach { originLoc.world?.dropItemNaturally(originLoc, it) }
            }
        }
    }

    fun getFortuneMultiplier(blockType: Material, fortuneLevel: Int, maxLevel: Int = LevelManager.fortuneMaxLevel, scrollBonus: Double = 0.0): Double {
        if (blockType !in MineManager.valuables) return 1.0
        return UpgradeFormulas.getFortuneMultiplier(fortuneLevel, maxLevel, scrollBonus)
    }

    fun getFortuneMultiplier(
        blockType: Material,
        fortuneLevel: Int,
        playerData: PlayerData,
        maxLevel: Int = LevelManager.fortuneMaxLevel,
        scrollBonus: Double = 0.0
    ): Double = getFortuneMultiplier(blockType, fortuneLevel, maxLevel, scrollBonus) * PotionsManager.getFortuneMultiplier(playerData)

    fun rollFortuneAmount(blockType: Material, fortuneLevel: Int, maxLevel: Int = LevelManager.fortuneMaxLevel, scrollBonus: Double = 0.0): Int {
        val multiplier = getFortuneMultiplier(blockType, fortuneLevel, maxLevel, scrollBonus).coerceAtLeast(1.0)
        val guaranteedAmount = kotlin.math.floor(multiplier).toInt()
        val fractionalChance = multiplier - guaranteedAmount
        val bonusAmount = if (Random.nextDouble() < fractionalChance) 1 else 0
        return guaranteedAmount + bonusAmount
    }

    fun rollScaledAmount(baseAmount: Int, multiplier: Double): Int {
        if (baseAmount <= 0) return 0
        val scaledAmount = baseAmount * multiplier.coerceAtLeast(1.0)
        val guaranteedAmount = kotlin.math.floor(scaledAmount).toInt()
        val fractionalChance = scaledAmount - guaranteedAmount
        val bonusAmount = if (Random.nextDouble() < fractionalChance) 1 else 0
        return guaranteedAmount + bonusAmount
    }

    fun announceRareFind(player: Player?, blockType: Material) {
    }

    fun playMiningFeedback(
        player: Player,
        blockType: Material,
        location: org.bukkit.Location,
        blockData: BlockData,
        oreBoostActive: Boolean,
        excavatorActive: Boolean
    ) {
        val effectLocation = location.clone().add(0.5, 0.5, 0.5)
        player.playSound(
            effectLocation,
            SoundManager.getMineBreakSound(blockType),
            SoundManager.getMineBreakVolume(blockType),
            SoundManager.getBreakPitch(excavatorActive)
        )

        player.spawnParticle(Particle.BLOCK, effectLocation, 12, 0.18, 0.18, 0.18, 0.02, blockData)
        if (blockType in MineManager.valuables) {
            val valuableIndex = MineManager.valuables.indexOf(blockType)
            player.spawnParticle(Particle.ENCHANT, effectLocation, 6, 0.28, 0.28, 0.28, 0.01)
            player.playSound(
                effectLocation,
                SoundManager.EXPERIENCE_PICKUP_SOUND,
                0.22f,
                SoundManager.getRewardChimePitch(valuableIndex, oreBoostActive)
            )
        }
    }

    fun getRolls(): Pair<List<Int>, List<Int>> {
        val rolls = (1..5).map { Random.nextInt(0, LevelManager.multiBreakMaxLevel + 1) }
        val rolls2 = (1..13).map { Random.nextInt(0, (LevelManager.excavatorEfficiencyMaxLevel * 20) + 1) }
        return rolls to rolls2
    }
}
