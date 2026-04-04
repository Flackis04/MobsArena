package com.example.test

import dev.triumphteam.gui.guis.Gui
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

object AutoMinerManager {
    private const val TICK_INTERVAL_TICKS = 20L
    private const val BACKPACK_ITEMS_PER_LEVEL = 10_000L
    private const val MAX_BACKPACK_CAPACITY = 5_000_000L
    private val openBackpackViewers = mutableSetOf<UUID>()

    data class CollectResult(
        val totalItems: Long,
        val totalValue: Long,
        val payoutLucky: Boolean,
        val payoutMultiplier: Int
    )

    fun init() {
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable {
                tickLootGeneration()
                refreshOpenViews()
            },
            TICK_INTERVAL_TICKS,
            TICK_INTERVAL_TICKS
        )
    }

    fun getContents(player: Player): MutableList<ItemStack> {
        val stored = DataStore.get(player.uniqueId).autoMinerStorageContents
        val normalized = MutableList(StorageManager.capacity) { ItemStack(Material.AIR) }

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
        DataStore.get(player.uniqueId).autoMinerStorageContents = contents
            .take(StorageManager.capacity)
            .map { item ->
                if (item.type == Material.AIR || item.amount <= 0) ItemStack(Material.AIR) else item.clone()
            }
            .toMutableList()
    }

    fun addDrop(player: Player, itemStack: ItemStack): Int {
        val slot = MineManager.valuableDrops.indexOf(itemStack.type)
        if (slot == -1 || itemStack.amount <= 0) return 0

        val remainingCapacity = (getBackpackCapacity(player) - getStoredItemCount(player)).coerceAtLeast(0L)
        if (remainingCapacity <= 0L) return 0
        val acceptedAmount = itemStack.amount.coerceAtMost(remainingCapacity.toInt())

        val contents = getContents(player)
        val existing = contents[slot]
        if (existing.type == Material.AIR) {
            contents[slot] = ItemStack(itemStack.type, acceptedAmount)
        } else {
            existing.amount += acceptedAmount
        }
        setContents(player, contents)
        return acceptedAmount
    }

    fun getBackpackCapacity(player: Player): Long = getBackpackCapacity(DataStore.get(player.uniqueId))

    fun getBackpackCapacity(data: PlayerData): Long =
        (data.autoMinerBackpackLevel.coerceAtLeast(1).toLong() * BACKPACK_ITEMS_PER_LEVEL)
            .coerceAtMost(MAX_BACKPACK_CAPACITY)

    fun getStoredItemCount(player: Player): Long = getContents(player).sumOf { it.amount.toLong() }

    fun getLuckMultiplier(level: Int): Double = UpgradeFormulas.getAutoMinerLuckMultiplier(level)

    fun getOfflineYieldRate(level: Int): Double = UpgradeFormulas.getAutoMinerOfflineYieldRate(level)

    fun getProcessingAttempts(level: Int): Int = UpgradeFormulas.getAutoMinerProcessingAttempts(level)

    fun collectAll(player: Player): CollectResult {
        val contents = getContents(player)
        var totalItems = 0L
        val data = DataStore.get(player.uniqueId)
        val payoutLucky = Random.nextDouble() <= UpgradeFormulas.getAutoMinerPayoutChance(data.autoMinerPayoutLevel)
        val payoutMultiplier = if (payoutLucky) 2 else 1

        for (item in contents) {
            if (item.type == Material.AIR || item.amount <= 0) continue
            val payoutAmount = item.amount * payoutMultiplier
            totalItems += payoutAmount.toLong()
            StorageManager.addDrop(player, item.clone().apply { amount = payoutAmount })
        }

        if (totalItems > 0L) {
            setContents(player, MutableList(StorageManager.capacity) { ItemStack(Material.AIR) })
        }

        return CollectResult(totalItems, 0L, payoutLucky, payoutMultiplier)
    }

    fun openBackpack(player: Player) {
        openBackpackViewers += player.uniqueId
    }

    fun closeBackpack(player: Player) {
        openBackpackViewers -= player.uniqueId
    }

    private fun tickLootGeneration() {
        DataStore.all().forEach { (uuid, data) ->
            if (data.rebirth < 1) return@forEach

            val onlinePlayer = Bukkit.getPlayer(uuid)
            val attempts = getProcessingAttempts(data.autoMinerEfficiencyLevel)
            val offlineRate = getOfflineYieldRate(data.autoMinerEnergyDrinkLevel)

            repeat(attempts) {
                if (onlinePlayer == null && Math.random() > offlineRate) return@repeat
                generateValuable(uuid, onlinePlayer, data)
            }
        }
    }

    private fun generateValuable(uuid: UUID, player: Player?, data: PlayerData): Boolean {
        val remainingCapacity = (getBackpackCapacity(data) - getContents(uuid).sumOf { it.amount.toLong() }).coerceAtLeast(0L)
        if (remainingCapacity <= 0L) return false
        val minedDrop = MineManager.mineValuableForAutoMiner(
            ownerId = uuid,
            luckMultiplier = getLuckMultiplier(data.autoMinerLuckLevel),
            fortuneLevel = data.autoMinerFortuneLevel
        ) ?: return false
        val cappedAmount = minedDrop.amount.coerceAtMost(remainingCapacity.toInt())
        if (cappedAmount <= 0) return false

        val index = MineManager.valuableDrops.indexOf(minedDrop.type)
        if (index == -1) return false

        val contents = getContents(uuid)
        val existing = contents[index]
        if (existing.type == Material.AIR) {
            contents[index] = ItemStack(minedDrop.type, cappedAmount)
        } else {
            existing.amount += cappedAmount
        }
        setContents(uuid, contents)
        return true
    }

    private fun getContents(uuid: UUID): MutableList<ItemStack> {
        val stored = DataStore.get(uuid).autoMinerStorageContents
        val normalized = MutableList(StorageManager.capacity) { ItemStack(Material.AIR) }

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

    private fun setContents(uuid: UUID, contents: List<ItemStack>) {
        DataStore.get(uuid).autoMinerStorageContents = contents
            .take(StorageManager.capacity)
            .map { item ->
                if (item.type == Material.AIR || item.amount <= 0) ItemStack(Material.AIR) else item.clone()
            }
            .toMutableList()
    }

    private fun refreshOpenViews() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.uniqueId !in openBackpackViewers) return@forEach
            val holder = player.openInventory.topInventory.holder
            if (holder !is Gui) return@forEach
            AutoMinerBackpackGui.refreshIfOpen(player)
        }
    }
}
