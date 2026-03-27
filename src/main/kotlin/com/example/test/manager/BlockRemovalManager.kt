package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object BlockRemovalManager {

    fun handleBlockRemoval(
        player: Player?,
        block: Block,
        blockQuantity: Double
    ): Int {

        var cumBlocksMined = 1
        val player = player ?: return 0
        val resolvedBlockQuantity = resolveBlockQuantity(blockQuantity)
        if (resolvedBlockQuantity <= 0) return cumBlocksMined
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

        for ((index, b1) in closestBlocks.withIndex()) {
            val startDelayTicks = (index + 1L) * delayStepTicks
            processExtraBlock(player, b1, startDelayTicks)
            cumBlocksMined++
        } // else, reveal block on blocks furthest away
        return cumBlocksMined
    }

    private fun resolveBlockQuantity(blockQuantity: Double): Int {
        if (blockQuantity <= 0.0) return 0
        val wholeBlocks = kotlin.math.floor(blockQuantity).toInt()
        val fractionalChance = blockQuantity - wholeBlocks
        return if (Random.nextDouble() < fractionalChance) wholeBlocks + 1 else wholeBlocks
    }

    private fun processExtraBlock(player: Player, block: Block, startDelayTicks: Long) {
        val stored = MineManager.getStoredOre(block.location)
        val actualType = stored ?: block.type
        if (stored != null && block.type != stored) block.type = stored
        val blockData = block.blockData.clone()
        val data = DataStore.get(player.uniqueId)
        val fortuneLevel = data.fortuneLevel
        val fortuneMaxLevel = data.fortuneMaxLevel
        val fortuneScrollBonus = 0.0
        val drops = if (actualType in MineManager.valuables) {
            MineManager.getConfiguredDrops(actualType, fortuneLevel, fortuneMaxLevel, fortuneScrollBonus)
        } else {
            block
                .getDrops(ItemStack(player.inventory.itemInMainHand.type), player)
                .map { drop ->
                    drop.clone().apply {
                        amount = rollScaledAmount(amount, getFortuneMultiplier(actualType, fortuneLevel, fortuneMaxLevel, fortuneScrollBonus))
                    }
                }
                .toList()
        }
        //player.sendMessage(drops.toString())
        if (MineManager.mineableBlocks.contains(actualType)) {
            AnimationManager.breakBlock(
                player,
                block.location,
                actualType,
                blockData,
                startDelayTicks = startDelayTicks,
                onStart = {
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
            )
        } else {
            deliverDrops(player, drops, block.location)
            ExperienceManager.giveValuableExperience(player, actualType)
            if (actualType in MineManager.valuables) {
                TutorialManager.handleValuableBreak(player)
            }
            ScrollManager.tryAwardMiningScroll(player)
        }
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

        for (drop in drops) {
            if (drop.type == Material.AIR) continue
            if (MineManager.nonDroppableBlocks.contains(drop.type)) continue

            val valuableIndex = MineManager.valuableDrops.indexOf(drop.type)
            if (valuableIndex != -1) {
                val amount = drop.amount
                val itemStack = MineManager.createValuableItem(drop.type, amount)
                val hasStorage = player.inventory.contents.any { ItemManager.isStorage(it) }
                val data = DataStore.get(player.uniqueId)

                if (data.oreBoostActive) {
                    repeat(2) {
                        if (!hasStorage) {
                            player.inventory.addItem(itemStack.clone())
                        } else {
                            StorageManager.addDrop(player, itemStack.clone())
                        }
                    }
                    MasteryManager.recordValuableCollection(player, drop.type, amount * 2)
                    player.playSound(
                        player.location,
                        SoundManager.ITEM_PICKUP_SOUND,
                        SoundManager.ITEM_PICKUP_VOLUME,
                        SoundManager.getValuablePickupPitch(valuableIndex, oreBoostActive = true, excavatorActive = data.excavatorActive)
                    )
                } else {
                    if (!hasStorage) {
                        player.inventory.addItem(itemStack.clone())
                    } else {
                        StorageManager.addDrop(player, itemStack.clone())
                    }
                    MasteryManager.recordValuableCollection(player, drop.type, amount)
                    player.playSound(
                        player.location,
                        SoundManager.ITEM_PICKUP_SOUND,
                        SoundManager.ITEM_PICKUP_VOLUME,
                        SoundManager.getValuablePickupPitch(valuableIndex, oreBoostActive = false, excavatorActive = data.excavatorActive)
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
        if (player == null) return
        val rareStartIndex = MineManager.valuables.indexOf(Material.MAGMA_BLOCK)
        val blockIndex = MineManager.valuables.indexOf(blockType)
        if (blockIndex == -1 || blockIndex < rareStartIndex) return

        val displayName = MineManager.valuableNames[blockIndex]
        Bukkit.broadcast(TextUtil.toComponent("&b${player.name} &7found &l$displayName&7!"))    }

    fun getRolls(): Pair<List<Int>, List<Int>> {
        val rolls = (1..5).map { Random.nextInt(0, LevelManager.multiBreakMaxLevel + 1) }
        val rolls2 = (1..13).map { Random.nextInt(0, (LevelManager.excavatorEfficiencyMaxLevel * 20) + 1) }
        return rolls to rolls2
    }
}
