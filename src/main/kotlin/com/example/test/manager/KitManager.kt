package com.example.test
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object KitManager {
    const val KIT_SET_MULTIPLIER_PER_TIER = 0.05

    fun giveStarterSpawnLoadout(player: Player) {
        val data = DataStore.get(player.uniqueId)
        equipHead(player, data.rank)
        equipArmor(player, data.rank)
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
        val multiBreakChance = UpgradeFormulas.getMultiBreakBlockQuantity(data.multiBreakLevel, false, data.multiBreakMaxLevel)
        val oreBoostChance = UpgradeFormulas.getOreBoostChance(data.oreBoostLevel, data.oreBoostMaxLevel, 0.0) * 100
        val excavatorChance = UpgradeFormulas.getExcavatorChance(data.excavatorLevel, data.excavatorMaxLevel, 0.0) * 100
        val pickaxe = ItemManager.makePickaxe(
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
        val multiBreakChance = UpgradeFormulas.getMultiBreakBlockQuantity(
            data.multiBreakLevel,
            ItemManager.isProcBooster(player.inventory.itemInOffHand),
            data.multiBreakMaxLevel
        )
        val oreBoostChance = UpgradeFormulas.getOreBoostChance(data.oreBoostLevel, data.oreBoostMaxLevel, 0.0) * 100
        val excavatorChance = UpgradeFormulas.getExcavatorChance(data.excavatorLevel, data.excavatorMaxLevel, 0.0) * 100
        val refreshed = ItemManager.makePickaxe(
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

        for (slot in player.inventory.contents.indices) {
            val item = player.inventory.getItem(slot) ?: continue
            if (ItemManager.isPickaxe(item)) {
                player.inventory.setItem(slot, refreshed.clone())
            }
        }
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
        val data = DataStore.get(player.uniqueId)
        equipHead(player, data.rank)
        equipArmor(player, data.rank)
    }

    fun removeRankArmor(player: Player) {
        player.inventory.helmet = null
        player.inventory.chestplate = null
        player.inventory.leggings = null
        player.inventory.boots = null
    }

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
        return Regex("T\\d+ .*?(Helmet|Chestplate|Leggings|Boots)").containsMatchIn(displayName)
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
}
