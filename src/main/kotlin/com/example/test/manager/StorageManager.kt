package com.example.test

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

object StorageManager {
    val slotsPerPage = MineManager.valuableDrops.size
    const val maxPages = 1
    val capacity = slotsPerPage

    fun getContents(player: Player): MutableList<ItemStack> {
        val stored = getActiveStorage(player)
        val normalized = MutableList(capacity) { ItemStack(Material.AIR) }

        for (item in stored) {
            if (item.type == Material.AIR || item.amount <= 0) continue
            val slot = MineManager.valuableDrops.indexOf(item.type)
            if (slot == -1) continue

            val existing = normalized[slot]
            if (existing.type == Material.AIR) {
                normalized[slot] = ItemStack(item.type, item.amount)
            } else {
                existing.amount += item.amount
            }
        }

        return normalized
    }

    fun setContents(player: Player, contents: List<ItemStack>) {
        val normalizedContents = contents
            .take(capacity)
            .map { item ->
                if (item.type == Material.AIR || item.amount <= 0) {
                    ItemStack(Material.AIR)
                } else {
                    item.clone()
                }
            }
            .toMutableList()

        val data = DataStore.get(player.uniqueId)
        if (data.hasEnabledPvp) {
            data.deathStorageContents = normalizedContents
        } else {
            data.storageContents = normalizedContents
        }
    }

    fun addDrop(player: Player, itemStack: ItemStack): Int {
        val slot = MineManager.valuableDrops.indexOf(itemStack.type)
        if (slot == -1 || itemStack.amount <= 0) return 0

        val contents = getContents(player)
        val existing = contents[slot]
        if (existing.type == Material.AIR) {
            contents[slot] = ItemStack(itemStack.type, itemStack.amount)
        } else {
            existing.amount += itemStack.amount
        }

        setContents(player, contents)
        return itemStack.amount
    }

    fun addDropToBackpack(ownerId: UUID, itemStack: ItemStack): Int {
        val slot = MineManager.valuableDrops.indexOf(itemStack.type)
        if (slot == -1 || itemStack.amount <= 0) return 0

        val data = DataStore.get(ownerId)
        val contents = getNormalizedContents(data.storageContents)
        val existing = contents[slot]
        if (existing.type == Material.AIR) {
            contents[slot] = ItemStack(itemStack.type, itemStack.amount)
        } else {
            existing.amount += itemStack.amount
        }
        data.storageContents = contents
        return itemStack.amount
    }

    fun addDropToDeathpack(player: Player, itemStack: ItemStack): Int {
        val slot = MineManager.valuableDrops.indexOf(itemStack.type)
        if (slot == -1 || itemStack.amount <= 0) return 0

        val contents = getNormalizedContents(DataStore.get(player.uniqueId).deathStorageContents)
        val existing = contents[slot]
        if (existing.type == Material.AIR) {
            contents[slot] = ItemStack(itemStack.type, itemStack.amount)
        } else {
            existing.amount += itemStack.amount
        }

        DataStore.get(player.uniqueId).deathStorageContents = contents
        return itemStack.amount
    }

    fun getDeathpackContents(player: Player): MutableList<ItemStack> =
        getNormalizedContents(DataStore.get(player.uniqueId).deathStorageContents)

    fun clearDeathpack(player: Player) {
        DataStore.get(player.uniqueId).deathStorageContents = mutableListOf()
    }

    fun sellAll(player: Player): SellResult {
        val contents = getContents(player)
        var totalItems = 0L
        var totalValue = 0L

        for (index in contents.indices) {
            val item = contents[index]
            if (item.type == Material.AIR || item.amount <= 0) continue

            val baseValue = getSellValue(player, item.type) ?: continue

            totalItems += item.amount.toLong()
            totalValue += (baseValue * item.amount * resolveSellMultiplier(player)).toLong()
            contents[index] = ItemStack(Material.AIR)
        }

        DataStore.get(player.uniqueId).hasSold = true
        DataStore.get(player.uniqueId).balance += totalValue
        setContents(player, contents)
        TutorialManager.handleBackpackSold(player)
        SessionTimelineManager.record(
            player,
            "Sold all backpack contents: ${TextUtil.formatNum(totalItems)} items for ${TextUtil.formatNum(totalValue)} ${ItemManager.COIN_NAME_PLURAL}"
        )

        return SellResult(totalItems, totalValue)
    }

    fun sellType(player: Player, material: Material): SellResult {
        val contents = getContents(player)
        val slot = MineManager.valuableDrops.indexOf(material)
        if (slot == -1) return SellResult(0, 0)

        val item = contents[slot]
        if (item.type == Material.AIR || item.amount <= 0) return SellResult(0, 0)

        val baseValue = getSellValue(player, item.type) ?: return SellResult(0, 0)
        val totalItems = item.amount.toLong()
        val totalValue = (baseValue * item.amount * resolveSellMultiplier(player)).toLong()

        contents[slot] = ItemStack(Material.AIR)
        DataStore.get(player.uniqueId).hasSold = true
        DataStore.get(player.uniqueId).balance += totalValue
        setContents(player, contents)
        TutorialManager.handleBackpackSold(player)
        SessionTimelineManager.record(
            player,
            "Sold ${TextUtil.formatNum(totalItems)}x ${material.name.lowercase().replace('_', ' ')} for ${TextUtil.formatNum(totalValue)} ${ItemManager.COIN_NAME_PLURAL}"
        )

        return SellResult(totalItems, totalValue)
    }

    fun sellAllPreview(player: Player): SellResult {
        val contents = getContents(player)
        var totalItems = 0L
        var totalValue = 0L

        for (item in contents) {
            if (item.type == Material.AIR || item.amount <= 0) continue
            val baseValue = getSellValue(player, item.type) ?: continue
            totalItems += item.amount.toLong()
            totalValue += (baseValue * item.amount * resolveSellMultiplier(player)).toLong()
        }

        totalValue = applyDeathpackValueMultiplier(player, totalValue)

        return SellResult(totalItems, totalValue)
    }

    fun getFilledSlotCount(player: Player): Int = getContents(player).count { it.type != Material.AIR }

    fun getStoredItemCount(player: Player): Long = getContents(player).sumOf { item ->
        if (item.type == Material.AIR || item.amount <= 0) 0L else item.amount.toLong()
    }

    fun getBackpackCapacity(player: Player): Long = getBackpackCapacity(DataStore.get(player.uniqueId))

    fun getBackpackCapacity(data: PlayerData): Long =
        Long.MAX_VALUE

    fun getBackpackCapacityForLevel(level: Int): Long =
        Long.MAX_VALUE

    fun getBaseSellValue(material: Material): Long? {
        val index = MineManager.valuableDrops.indexOf(material)
        return if (index == -1) null else MineManager.valuableSellValues[index]
    }

    fun getSellValue(player: Player, material: Material): Long? {
        val baseValue = getBaseSellValue(material) ?: return null
        return MasteryManager.getValuableSellValue(DataStore.get(player.uniqueId), material).takeIf { it > 0L } ?: baseValue
    }

    fun resolveSellMultiplier(player: Player): Double {
        val multiplier = KitManager.getEffectiveSellMultiplier(player)
        if (TutorialManager.isTutorialMode(player)) return multiplier
        return if (BossbarManager.hasActiveSellMultiplier()) multiplier * BossbarManager.multiplier else multiplier
    }

    fun getDeathpackValueMultiplier(player: Player): Double {
        if (!DataStore.get(player.uniqueId).hasEnabledPvp) return 1.0
        val baseValue = getBaseStorageSellValue(player)
        return 1.0 + (baseValue.toDouble() / 400000)
    }

    fun getBaseStorageSellValue(player: Player): Long {
        val contents = getContents(player)
        var totalValue = 0L

        for (item in contents) {
            if (item.type == Material.AIR || item.amount <= 0) continue
            val baseValue = getSellValue(player, item.type) ?: continue
            totalValue += (baseValue * item.amount * resolveSellMultiplier(player)).toLong()
        }

        return totalValue
    }

    data class SellResult(
        val totalItems: Long,
        val totalValue: Long
    )

    private fun getActiveStorage(player: Player): MutableList<ItemStack> {
        val data = DataStore.get(player.uniqueId)
        return if (data.hasEnabledPvp) data.deathStorageContents else data.storageContents
    }

    private fun applyDeathpackValueMultiplier(player: Player, value: Long): Long {
        if (!DataStore.get(player.uniqueId).hasEnabledPvp || value <= 0L) return value
        return (value * getDeathpackValueMultiplier(player)).toLong()
    }

    private fun getNormalizedContents(stored: MutableList<ItemStack>): MutableList<ItemStack> {
        val normalized = MutableList(capacity) { ItemStack(Material.AIR) }

        for (item in stored) {
            if (item.type == Material.AIR || item.amount <= 0) continue
            val slot = MineManager.valuableDrops.indexOf(item.type)
            if (slot == -1) continue

            val existing = normalized[slot]
            if (existing.type == Material.AIR) {
                normalized[slot] = ItemStack(item.type, item.amount)
            } else {
                existing.amount += item.amount
            }
        }

        return normalized
    }
}
