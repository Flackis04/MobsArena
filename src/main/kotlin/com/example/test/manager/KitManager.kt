package com.example.test
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object KitManager {
    const val KIT_SET_MULTIPLIER_PER_TIER = 0.05
    private const val PVP_ACTIVATION_SECONDS = 5
    private val mineModeSnapshots = mutableMapOf<UUID, InventorySnapshot>()
    private val pvpModeSnapshots = mutableMapOf<UUID, InventorySnapshot>()
    private val currentModes = mutableMapOf<UUID, LoadoutMode>()
    private val pendingPvpActivationTasks = mutableMapOf<UUID, BukkitTask>()

    fun giveStarterSpawnLoadout(player: Player) {
        removePvpArmor(player)
        ensurePickaxe(player)
        giveStorage(player)
    }

    fun shouldReceiveStarterLoot(player: Player): Boolean {
        val data = DataStore.get(player.uniqueId)
        return data.newPlayer == null || !hasPickaxe(player) || !hasStorage(player)
    }

    fun giveTierKit(player: Player, tier: Int) {
        equipHead(player, tier)
        equipArmor(player, tier)
        dyeArmor(player, tier)
        player.inventory.addItem(ItemStack(Material.WIND_CHARGE, 24))
        player.inventory.addItem(ItemStack(Material.IRON_SWORD))
        player.inventory.addItem(ItemStack(Material.GOLDEN_CARROT, 16))
        givePickaxe(player)
    }

    fun equipHead(player: Player, tier: Int) {
        val tierData = TierManager.getTier(tier)
        val color = tierData?.color ?: "&f"
        val name = tierData?.name ?: "Unknown"
        val head = TierManager.makeTierHead(tier)
        head.editMeta { meta ->
            meta.displayName(TextUtil.toComponent("${color}&lT$tier $name Helmet"))
            meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, tier, true)
            meta.addEnchant(org.bukkit.enchantments.Enchantment.VANISHING_CURSE, 1, true)
            meta.addItemFlags(
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE
            )
            meta.lore(listOf(kitSetLoreLine(tier)))
        }
        player.inventory.helmet = head
    }

    fun equipArmor(player: Player, tier: Int) {
        val tierData = TierManager.getTier(tier)
        val name = tierData?.name ?: "Unknown"
        val color = tierData?.color ?: "&f"
        val rgb = tierData?.rgb?.split(",")?.mapNotNull { it.toIntOrNull() } ?: listOf(255, 255, 255)
        val bootYellow = tierData?.bootColor == "yellow"

        val chest = ItemManager.makeLeatherArmor(
            Material.LEATHER_CHESTPLATE,
            "${color}&lT$tier $name Chestplate",
            tier,
            Triple(rgb[0], rgb[1], rgb[2]),
            false
        )
        val legs = ItemManager.makeLeatherArmor(
            Material.LEATHER_LEGGINGS,
            "${color}&lT$tier $name Leggings",
            tier,
            Triple(rgb[0], rgb[1], rgb[2]),
            false
        )
        val boots = ItemManager.makeLeatherArmor(
            Material.LEATHER_BOOTS,
            "${color}&lT$tier $name Boots",
            tier,
            Triple(rgb[0], rgb[1], rgb[2]),
            bootYellow
        )
        addRebirthLore(chest)
        addRebirthLore(legs)
        addRebirthLore(boots)

        player.inventory.chestplate = chest
        player.inventory.leggings = legs
        player.inventory.boots = boots
    }

    fun dyeArmor(player: Player, tier: Int) {
        // Already applied in makeLeatherArmor via color values.
    }

    fun givePickaxe(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val pickaxe = ItemManager.makePickaxe(data, player.level)
        player.inventory.addItem(pickaxe)
    }

    fun ensurePickaxe(player: Player) {
        if (hasPickaxe(player)) return
        givePickaxe(player)
    }

    fun giveDynamite(player: Player, amount: Int) {
        val dynamite = ItemManager.makeDynamite()
        dynamite.amount = amount
        player.inventory.addItem(dynamite)
    }

    fun giveChargedDynamite(player: Player, amount: Int) {
        val dynamite = ItemManager.makeChargedDynamite()
        dynamite.amount = amount
        player.inventory.addItem(dynamite)
    }

    fun refreshPickaxe(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val refreshed = ItemManager.makePickaxe(data, player.level, ItemManager.isProcBooster(player.inventory.itemInOffHand))

        for (slot in player.inventory.contents.indices) {
            val item = player.inventory.getItem(slot) ?: continue
            if (ItemManager.isPickaxe(item)) {
                player.inventory.setItem(slot, refreshed.clone())
            }
        }
    }

    fun refreshPvpSword(player: Player) {
        if (currentModes[player.uniqueId] != LoadoutMode.PVP && !isDangerZone(player.location)) return
        val data = DataStore.get(player.uniqueId)
        val swordTier = (data.rebirth + data.swordLevel).coerceAtLeast(1)
        player.inventory.setItem(0, ItemManager.makeSword(swordTier))
    }

    fun giveStorage(player: Player) {
        if (hasStorage(player)) return
        val storage = ItemManager.storage.clone()
        if (player.inventory.getItem(1) == null || player.inventory.getItem(1)?.type == Material.AIR) {
            player.inventory.setItem(1, storage)
        } else {
            player.inventory.addItem(storage)
        }
    }

    fun hasPickaxe(player: Player): Boolean =
        player.inventory.contents.any { ItemManager.isPickaxe(it) } || ItemManager.isPickaxe(player.inventory.itemInOffHand)

    fun hasStorage(player: Player): Boolean =
        player.inventory.contents.any { ItemManager.isStorage(it) } || ItemManager.isStorage(player.inventory.itemInOffHand)

    fun isWearingRankArmor(player: Player): Boolean {
        val data = DataStore.get(player.uniqueId)
        return hasFullTierArmorSet(player, data.rank)
    }

    fun ensureRankArmorState(player: Player) {
        if (isDangerZone(player.location)) {
            equipPvpLoadout(player)
        }
    }

    fun removePvpArmor(player: Player) {
        player.inventory.helmet = null
        player.inventory.chestplate = null
        player.inventory.leggings = null
        player.inventory.boots = null
    }

    fun syncLoadoutMode(player: Player, location: Location = player.location, force: Boolean = false) {
        val targetMode = if (isMineMode(location)) LoadoutMode.MINE else LoadoutMode.PVP
        val currentMode = currentModes[player.uniqueId]
        if (force && currentMode == null && targetMode == LoadoutMode.MINE) {
            currentModes[player.uniqueId] = LoadoutMode.MINE
            restoreFlightForMineMode(player)
            removePvpArmor(player)
            return
        }
        if (targetMode == LoadoutMode.MINE) {
            cancelPendingPvpActivation(player.uniqueId)
            if (!force && currentMode == LoadoutMode.MINE) return
            switchToMineMode(player)
            currentModes[player.uniqueId] = LoadoutMode.MINE
            return
        }

        if (!force && currentMode == LoadoutMode.PVP) return
        if (force) {
            cancelPendingPvpActivation(player.uniqueId)
            switchToPvpMode(player)
            currentModes[player.uniqueId] = LoadoutMode.PVP
            sendDangerZoneTitle(player)
            return
        }

        if (pendingPvpActivationTasks.containsKey(player.uniqueId)) return
        startPvpActivationCountdown(player)
    }

    fun prepareForLogout(player: Player) {
        cancelPendingPvpActivation(player.uniqueId)
        persistCurrentLoadout(player)
        val currentMode = currentModes[player.uniqueId]
        if (currentMode == LoadoutMode.PVP) {
            restoreMineSnapshotOrDefault(player)
        }
        currentModes.remove(player.uniqueId)
        mineModeSnapshots.remove(player.uniqueId)
    }

    fun prepareForProgressReset(player: Player) {
        cancelPendingPvpActivation(player.uniqueId)
        persistCurrentLoadout(player)
        currentModes[player.uniqueId] = LoadoutMode.MINE
        if (!player.hasPermission("command.dev")) {
            if (player.isFlying) {
                player.isFlying = false
            }
            player.allowFlight = false
        }
    }

    fun handlePvpDeath(player: Player) {
        cancelPendingPvpActivation(player.uniqueId)
        pvpModeSnapshots.remove(player.uniqueId)
        mineModeSnapshots.remove(player.uniqueId)
        currentModes.remove(player.uniqueId)
    }

    fun persistCurrentLoadout(player: Player) {
        when (currentModes[player.uniqueId]) {
            LoadoutMode.PVP -> pvpModeSnapshots[player.uniqueId] = captureInventory(player)
            LoadoutMode.MINE -> mineModeSnapshots[player.uniqueId] = captureInventory(player)
            null -> {
                if (isDangerZone(player.location)) {
                    pvpModeSnapshots[player.uniqueId] = captureInventory(player)
                } else {
                    mineModeSnapshots[player.uniqueId] = captureInventory(player)
                }
            }
        }
    }

    private fun switchToMineMode(player: Player) {
        pvpModeSnapshots[player.uniqueId] = captureInventory(player)
        restoreMineSnapshotOrDefault(player)
        restoreFlightForMineMode(player)
    }

    private fun switchToPvpMode(player: Player) {
        cancelPendingPvpActivation(player.uniqueId)
        mineModeSnapshots[player.uniqueId] = captureInventory(player)
        val snapshot = pvpModeSnapshots[player.uniqueId]
        if (snapshot != null) {
            restoreInventory(player, snapshot)
        } else {
            player.inventory.clear()
            player.inventory.setArmorContents(arrayOfNulls(4))
            player.inventory.setItemInOffHand(null)
        }
        stripMineTools(player)
        equipPvpLoadout(player)
        disableFlightForPvpMode(player)
    }

    private fun restoreMineSnapshotOrDefault(player: Player) {
        val snapshot = mineModeSnapshots[player.uniqueId]
        if (snapshot != null) {
            restoreInventory(player, snapshot)
        } else {
            player.inventory.clear()
            player.inventory.setArmorContents(arrayOfNulls(4))
            player.inventory.setItemInOffHand(null)
            giveStarterSpawnLoadout(player)
        }
        removePvpArmor(player)
    }

    private fun startPvpActivationCountdown(player: Player) {
        var secondsRemaining = PVP_ACTIVATION_SECONDS
        ActionBarManager.sendActionBarFor(player, 1.05, "&cDanger Zone in: &f$secondsRemaining")
        val task = org.bukkit.Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            if (!player.isOnline) {
                cancelPendingPvpActivation(player.uniqueId)
                return@Runnable
            }
            if (isMineMode(player.location)) {
                cancelPendingPvpActivation(player.uniqueId)
                return@Runnable
            }

            secondsRemaining--
            if (secondsRemaining <= 0) {
                cancelPendingPvpActivation(player.uniqueId)
                switchToPvpMode(player)
                currentModes[player.uniqueId] = LoadoutMode.PVP
                sendDangerZoneTitle(player)
                return@Runnable
            }

            ActionBarManager.sendActionBarFor(player, 1.05, "&cDanger Zone in: &f$secondsRemaining")
        }, 20L, 20L)
        pendingPvpActivationTasks[player.uniqueId] = task
    }

    private fun cancelPendingPvpActivation(playerId: UUID) {
        pendingPvpActivationTasks.remove(playerId)?.cancel()
    }

    private fun sendDangerZoneTitle(player: Player) {
        TextUtil.showTitle(player, "&cDanger Zone", "&7PvP mode enabled", 0, 40, 10)
    }

    private fun disableFlightForPvpMode(player: Player) {
        if (player.hasPermission("command.dev")) {
            player.allowFlight = true
            return
        }
        if (player.isFlying) {
            player.isFlying = false
        }
        player.allowFlight = false
    }

    private fun restoreFlightForMineMode(player: Player) {
        if (player.hasPermission("command.dev")) {
            player.allowFlight = true
            return
        }
        if (CombatManager.isInCombat(player)) {
            player.allowFlight = false
            if (player.isFlying) {
                player.isFlying = false
            }
            return
        }

        val data = DataStore.get(player.uniqueId)
        player.allowFlight = data.flightUnlocked && data.flight
        if (!player.allowFlight && player.isFlying) {
            player.isFlying = false
        }
    }

    private fun equipPvpLoadout(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val armorTier = (data.rebirth + 1).coerceAtMost(LevelManager.MAX_REBIRTH_LEVEL + 1).coerceAtLeast(1)
        val swordTier = (data.rebirth + data.swordLevel).coerceAtLeast(1)
        equipHead(player, armorTier)
        equipArmor(player, armorTier)
        player.inventory.setItem(0, ItemManager.makeSword(swordTier))
        player.inventory.setItem(8, ItemStack(Material.GOLDEN_CARROT, 16))
    }

    private fun captureInventory(player: Player): InventorySnapshot {
        val inventory = player.inventory
        return InventorySnapshot(
            contents = inventory.contents.map { it?.clone() }.toTypedArray(),
            armor = inventory.armorContents.map { it?.clone() }.toTypedArray(),
            offHand = inventory.itemInOffHand?.clone()
        )
    }

    private fun restoreInventory(player: Player, snapshot: InventorySnapshot) {
        val inventory = player.inventory
        inventory.contents = snapshot.contents.map { it?.clone() }.toTypedArray()
        inventory.armorContents = snapshot.armor.map { it?.clone() }.toTypedArray()
        inventory.setItemInOffHand(snapshot.offHand?.clone())
    }

    private fun stripMineTools(player: Player) {
        val inventory = player.inventory
        for (slot in inventory.contents.indices) {
            val item = inventory.getItem(slot) ?: continue
            if (ItemManager.isPickaxe(item) || ItemManager.isStorage(item)) {
                inventory.setItem(slot, null)
            }
        }
        val offHand = inventory.itemInOffHand
        if (ItemManager.isPickaxe(offHand) || ItemManager.isStorage(offHand)) {
            inventory.setItemInOffHand(null)
        }
    }

    fun isMineMode(location: Location): Boolean =
        location.y > 95.0 || MineManager.containsMineAreaXZ(location)

    fun isDangerZone(location: Location): Boolean = !isMineMode(location)

    fun getEffectiveSellMultiplier(player: Player): Double {
        val data = DataStore.get(player.uniqueId)
        return data.multiplier
    }

    private fun addRebirthLore(item: ItemStack) {
        item.editMeta { meta ->
            val tier = extractTierFromDisplayName(item) ?: 1
            meta.lore(listOf(kitSetLoreLine(tier)))
        }
    }

    private fun hasFullTierArmorSet(player: Player, tier: Int): Boolean {
        val inventory = player.inventory
        return isTierArmorPiece(inventory.helmet, tier) &&
            isTierArmorPiece(inventory.chestplate, tier) &&
            isTierArmorPiece(inventory.leggings, tier) &&
            isTierArmorPiece(inventory.boots, tier)
    }

    fun isTierArmorPiece(item: ItemStack?, tier: Int): Boolean {
        if (item == null) return false
        val displayName = TextUtil.toLegacyString(item.itemMeta?.displayName()) ?: return false
        return displayName.contains("T$tier ")
    }

    fun isRankArmorPiece(item: ItemStack?): Boolean {
        if (item == null) return false
        val displayName = TextUtil.toLegacyString(item.itemMeta?.displayName()) ?: return false
        return Regex("(T\\d+|Rebirth \\d+) .*?(Helmet|Chestplate|Leggings|Boots)").containsMatchIn(displayName)
    }

    fun getRankMultiplier(tier: Int): Double = (tier - 1).coerceAtLeast(0) * KIT_SET_MULTIPLIER_PER_TIER

    private fun kitSetLoreLine(tier: Int) =
        TextUtil.toComponent("&7Visual rank armor only.")
            .decoration(TextDecoration.ITALIC, false)

    private fun extractTierFromDisplayName(item: ItemStack): Int? {
        val displayName = TextUtil.toLegacyString(item.itemMeta?.displayName()) ?: return null
        val match = Regex("T(\\d+)").find(displayName) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private enum class LoadoutMode {
        MINE,
        PVP
    }

    private data class InventorySnapshot(
        val contents: Array<ItemStack?>,
        val armor: Array<ItemStack?>,
        val offHand: ItemStack?
    )
}
