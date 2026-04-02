package com.example.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.persistence.PersistentDataType

object ItemManager {
    lateinit var coin: ItemStack
    lateinit var soulFragment: ItemStack
    lateinit var procBooster: ItemStack
    lateinit var storage: ItemStack
    lateinit var pickaxe: ItemStack
    lateinit var dynamite: ItemStack
    lateinit var chargedDynamite: ItemStack
    lateinit var nuke: ItemStack
    lateinit var lightningRodDeployable: ItemStack

    const val COIN_NAME = "<#FFFF00>Coins"
    const val COIN_NAME_PLURAL = "<#FFFF00>Coins"
    const val SOUL_FRAGMENT_NAME = "<#131313>&lS<#1F1F1F>&lo<#2B2B2B>&lu<#373737>&ll <#4E4E4E>&lF<#5A5A5A>&lr<#4E4E4E>&la<#424242>&lg<#373737>&lm<#2B2B2B>&le<#1F1F1F>&ln<#131313>&lt"
    private val banknoteAmountKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("TestPlugin")!!, "banknote_amount")
    }
    private val scrollRarityKey by lazy { NamespacedKey(TestPlugin.instance, "scroll_rarity") }
    private val utilityItemTypeKey by lazy { NamespacedKey(TestPlugin.instance, "utility_item_type") }

    fun init() {
        coin = ItemStack(Material.SUNFLOWER)
        coin.editMeta { meta ->
            meta.displayName(TextUtil.toComponent(COIN_NAME))
            meta.lore(listOf(TextUtil.toComponent("&7Right-Click to deposit")))
            meta.addEnchant(Enchantment.MENDING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
        }

        soulFragment = ItemStack(Material.WITHER_ROSE)
        soulFragment.editMeta { meta ->
            meta.displayName(TextUtil.toComponent(SOUL_FRAGMENT_NAME))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Gained from every fifth kill in a killstreak"),
                    TextUtil.toComponent("&7Save these for later...")
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
        }

        procBooster = ItemStack(Material.HEART_OF_THE_SEA)
        procBooster.editMeta { meta ->
            meta.displayName(storageStyleName("powerup", NamedTextColor.AQUA))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Offhand: &b+2.5&7 Multi-Break cap")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
        }

        storage = makeStorage()
        dynamite = makeDynamite()
        chargedDynamite = makeChargedDynamite()
        nuke = makeNuke()
        lightningRodDeployable = makeLightningRodDeployable()
    }

    fun isCoin(item: ItemStack?): Boolean = item?.isSimilar(coin) == true

    fun makeBanknote(amount: Long): ItemStack {
        return ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&6&lBanknote").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Value: &b${TextUtil.formatNum(amount)} ${COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Right-click to redeem").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.persistentDataContainer.set(banknoteAmountKey, PersistentDataType.LONG, amount)
            }
        }
    }

    fun isBanknote(item: ItemStack?): Boolean =
        item?.itemMeta?.persistentDataContainer?.has(banknoteAmountKey, PersistentDataType.LONG) == true

    fun getBanknoteAmount(item: ItemStack?): Long? =
        item?.itemMeta?.persistentDataContainer?.get(banknoteAmountKey, PersistentDataType.LONG)

    fun isBackpack(item: ItemStack?): Boolean = item?.isSimilar(storage) == true

    fun isStorage(item: ItemStack?): Boolean = isBackpack(item)

    fun isDynamite(item: ItemStack?): Boolean = getUtilityItemType(item) == "dynamite"

    fun isChargedDynamite(item: ItemStack?): Boolean = getUtilityItemType(item) == "charged_dynamite"

    fun isNuke(item: ItemStack?): Boolean = getUtilityItemType(item) == "nuke"

    fun isLightningRodDeployable(item: ItemStack?): Boolean = getUtilityItemType(item) == "lightning_rod_deployable"

    fun isPickaxe(item: ItemStack?): Boolean {
        if (item?.type != Material.DIAMOND_PICKAXE) return false

        val displayName = TextUtil.toLegacyString(item.itemMeta?.displayName()) ?: return false
        val expectedName = TextUtil.toLegacyString(
            TextUtil.toComponent("<#3F3F3F>[<#55FFFF>Pickaxe<#3F3F3F>]")
        )

        return displayName == expectedName
    }

    fun isProcBooster(item: ItemStack?): Boolean = item?.isSimilar(procBooster) == true

    fun isUpgradeScroll(item: ItemStack?): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.has(scrollRarityKey, PersistentDataType.STRING)
    }

    fun getScrollRarity(item: ItemStack?): ScrollRarity? {
        val rarityId = item?.itemMeta?.persistentDataContainer?.get(scrollRarityKey, PersistentDataType.STRING) ?: return null
        return ScrollRarity.entries.firstOrNull { it.id == rarityId }
    }

    fun makeUpgradeScroll(rarity: ScrollRarity): ItemStack {
        val item = ItemStack(rarity.material)
        item.editMeta { meta ->
            meta.displayName(TextUtil.toComponent("${rarity.color}&l${rarity.displayName} Upgrade Scroll").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    TextUtil.toComponent(""),
                    TextUtil.toComponent(
                        when (rarity) {
                            ScrollRarity.NORMAL -> "&720% chance to add &b+1 &7max level"
                            ScrollRarity.RARE -> "&750% chance to add &b+1 &7max level"
                            ScrollRarity.MYTHIC -> "&7Adds &b+1 &7max level"
                            ScrollRarity.GOD -> "&7Adds &b+2 &7max levels"
                            ScrollRarity.SECRET -> "&7Adds &b+5 &7max levels"
                        }
                    ).decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Rolls one random permanent upgrade").decoration(TextDecoration.ITALIC, false),
                )
            )
            meta.persistentDataContainer.set(scrollRarityKey, PersistentDataType.STRING, rarity.id)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        return item
    }

    fun makePickaxe(
        rebirth: Int,
        playerLevel: Int,
        fortuneLevel: Int,
        multiBreakLevel: Int,
        multiBreakChance: Double,
        oreBoostLevel: Int,
        oreBoostChance: Double,
        excavatorLevel: Int,
        excavatorChance: Double
    ): ItemStack {
        val efficiencyLevel = getPickaxeEfficiencyLevel(playerLevel, rebirth)
        val item = ItemStack(Material.DIAMOND_PICKAXE)
        item.editMeta { meta ->
            meta.displayName(
                TextUtil.toComponent("<#3F3F3F>[<#55FFFF>Pickaxe<#3F3F3F>]")
                    .decoration(TextDecoration.ITALIC, false)
            )
            meta.addEnchant(Enchantment.EFFICIENCY, efficiencyLevel, true)
            if (fortuneLevel > 1) {
                meta.addEnchant(Enchantment.FORTUNE, fortuneLevel - 1, true)
            }
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
            meta.isUnbreakable = true
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE)
            meta.lore(
                listOf(
                    TextUtil.toComponent("&8Shift-Right-Click to Upgrade"),
                    TextUtil.toComponent(""),
                    TextUtil.toComponent("<#55FFFF>| <#A8A8A8>Every 8 levels <#55FFFF>+1 Efficiency"),
                    TextUtil.toComponent(""),
                    TextUtil.toComponent("<#55FFFF>| <#55FFFF>Enchants"),
                    TextUtil.toComponent("<#55FFFF>| <#A8A8A8>Efficiency <#55FFFF>$efficiencyLevel"),
                    TextUtil.toComponent("<#55FFFF>| <#A8A8A8>Fortune <#55FFFF>$fortuneLevel"),
                    TextUtil.toComponent("<#55FFFF>| <#A8A8A8>Multi-Break <#55FFFF>$multiBreakLevel"),
                    TextUtil.toComponent("<#55FFFF>| <#A8A8A8>Ore Boost <#55FFFF>$oreBoostLevel"),
                    TextUtil.toComponent("<#55FFFF>| <#A8A8A8>Excavator <#55FFFF>$excavatorLevel")
                ).map { it.decoration(TextDecoration.ITALIC, false) }
            )
        }
        return item
    }

    fun getPickaxeEfficiencyLevel(playerLevel: Int, rebirth: Int): Int = 12 + (playerLevel.coerceAtLeast(0) / 8) + rebirth

    fun makeStorage(): ItemStack {
        val storage = ItemStack(Material.CHEST)
        storage.editMeta { meta ->
            meta.displayName(
                storageStyleName("backpack", NamedTextColor.GOLD)
            )
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Right-click to open")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
        }
        return storage
    }

    fun makeDynamite(): ItemStack {
        val item = ItemStack(Material.RED_CANDLE)
        item.editMeta { meta ->
            meta.displayName(storageStyleName("dynamite", NamedTextColor.RED))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Right-click to launch")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            meta.persistentDataContainer.set(utilityItemTypeKey, PersistentDataType.STRING, "dynamite")
        }
        return item
    }

    fun makeChargedDynamite(): ItemStack {
        val item = ItemStack(Material.BLUE_CANDLE)
        item.editMeta { meta ->
            meta.displayName(storageStyleName("charged dynamite", NamedTextColor.BLUE))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Right-click to launch").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Slightly larger blast than normal dynamite").decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            meta.persistentDataContainer.set(utilityItemTypeKey, PersistentDataType.STRING, "charged_dynamite")
        }
        return item
    }

    fun makeNuke(): ItemStack {
        val item = ItemStack(Material.BLACK_CANDLE)
        item.editMeta { meta ->
            meta.displayName(storageStyleName("nuke", NamedTextColor.YELLOW))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Right-click to launch").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Massive blast radius").decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            meta.persistentDataContainer.set(utilityItemTypeKey, PersistentDataType.STRING, "nuke")
        }
        return item
    }

    fun makeLightningRodDeployable(): ItemStack {
        val item = ItemStack(Material.LIGHTNING_ROD)
        item.editMeta { meta ->
            meta.displayName(storageStyleName("storm rod", NamedTextColor.YELLOW))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Right-click in your mine to deploy").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Stacks up to &e100 &7times on your mine").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7The more you stack, the more oxidized it looks").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7While active it can trigger &e3x lightning &7at once").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&73x Lightning chance: &e1.0x -> 2.5x &7your Lightning proc chance").decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            meta.persistentDataContainer.set(utilityItemTypeKey, PersistentDataType.STRING, "lightning_rod_deployable")
        }
        return item
    }

    private fun getUtilityItemType(item: ItemStack?): String? =
        item?.itemMeta?.persistentDataContainer?.get(utilityItemTypeKey, PersistentDataType.STRING)

    fun makeMace(level: Int): ItemStack {
        val item = ItemStack(Material.MACE)
        item.editMeta { meta ->
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
            if (level > 1) {
                meta.addEnchant(Enchantment.DENSITY, level - 1, true)
            }
            if (level >= 4) meta.addEnchant(Enchantment.BREACH, 1, true)
            if (level >= 7) {
                meta.addEnchant(Enchantment.BREACH, 2, true)
                meta.addEnchant(Enchantment.WIND_BURST, 1, true)
            }
            if (level >= 10) meta.addEnchant(Enchantment.BREACH, 3, true)
            if (level >= 13) {
                meta.addEnchant(Enchantment.BREACH, 4, true)
                meta.addEnchant(Enchantment.WIND_BURST, 2, true)
            }
            meta.isUnbreakable = true
        }
        return item
    }

    fun makeSword(level: Int): ItemStack {
        val (material, sharpCap) = when {
            level <= 6 -> Material.WOODEN_SWORD to 5
            level <= 12 -> Material.STONE_SWORD to 5
            level <= 18 -> Material.IRON_SWORD to 5
            level <= 26 -> Material.DIAMOND_SWORD to 7
            else -> Material.NETHERITE_SWORD to 10
        }
        val base = when (material) {
            Material.WOODEN_SWORD -> 1
            Material.STONE_SWORD -> 7
            Material.IRON_SWORD -> 13
            Material.DIAMOND_SWORD -> 19
            else -> 27
        }
        val sharp = (level - base).coerceAtLeast(0).coerceAtMost(sharpCap)
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
            if (sharp > 0) meta.addEnchant(Enchantment.SHARPNESS, sharp, true)
            meta.isUnbreakable = true
        }
        return item
    }

    fun makeLeatherArmor(material: Material, name: String, protection: Int, rgb: Triple<Int, Int, Int>, bootYellow: Boolean): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            val leatherMeta = meta as LeatherArmorMeta
            leatherMeta.displayName(TextUtil.toComponent(name))
            leatherMeta.addEnchant(Enchantment.PROTECTION, protection, true)
            leatherMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
            leatherMeta.isUnbreakable = true
            leatherMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE)
            if (material == Material.LEATHER_BOOTS && bootYellow) {
                leatherMeta.setColor(org.bukkit.Color.YELLOW)
            } else {
                leatherMeta.setColor(org.bukkit.Color.fromRGB(rgb.first, rgb.second, rgb.third))
            }
        }
        return item
    }

    fun makeFood(level: Int): List<ItemStack> {
        return when (level) {
            1 -> listOf(ItemStack(Material.BREAD, 3))
            2 -> listOf(ItemStack(Material.COOKED_BEEF, 3))
            3 -> listOf(ItemStack(Material.GOLDEN_CARROT, 3))
            4 -> listOf(ItemStack(Material.GOLDEN_CARROT, 16))
            5 -> listOf(ItemStack(Material.GOLDEN_APPLE, 4))
            6 -> listOf(ItemStack(Material.GOLDEN_APPLE, 16))
            else -> listOf(ItemStack(Material.GOLDEN_APPLE, 24), ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1))
        }
    }

    private fun storageStyleName(name: String, color: NamedTextColor): Component {
        return Component.text(name.lowercase().map { toSmallCapsChar(it) }.joinToString(""))
            .color(color)
            .decoration(TextDecoration.ITALIC, false)
    }

    private fun toSmallCapsChar(char: Char): String = when (char) {
        'a' -> "ᴀ"
        'b' -> "ʙ"
        'c' -> "ᴄ"
        'd' -> "ᴅ"
        'e' -> "ᴇ"
        'f' -> "ꜰ"
        'g' -> "ɢ"
        'h' -> "ʜ"
        'i' -> "ɪ"
        'j' -> "ᴊ"
        'k' -> "ᴋ"
        'l' -> "ʟ"
        'm' -> "ᴍ"
        'n' -> "ɴ"
        'o' -> "ᴏ"
        'p' -> "ᴘ"
        'q' -> "ǫ"
        'r' -> "ʀ"
        's' -> "ꜱ"
        't' -> "ᴛ"
        'u' -> "ᴜ"
        'v' -> "ᴠ"
        'w' -> "ᴡ"
        'x' -> "x"
        'y' -> "ʏ"
        'z' -> "ᴢ"
        ' ' -> " "
        else -> char.toString()
    }
}
