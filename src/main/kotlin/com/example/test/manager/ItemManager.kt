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
    private data class PickaxeUpgradeStyle(
        val displayName: String,
        val color: String
    )

    private val pickaxeUpgradeStyles = linkedMapOf(
        "multiBreak" to PickaxeUpgradeStyle("Multi-Break", "<#6EE7FF>"),
        "fortune" to PickaxeUpgradeStyle("Fortune", "<#FF7AE0>"),
        "oreBoost" to PickaxeUpgradeStyle("Ore Boost", "<#FFD54A>"),
        "excavator" to PickaxeUpgradeStyle("Excavator", "<#9FA6B2>"),
        "lightning" to PickaxeUpgradeStyle("Lightning", "<#FFF06A>"),
        "virtualJackhammer" to PickaxeUpgradeStyle("Jackhammer", "<#FF9A3D>"),
        "excavatorEfficiency" to PickaxeUpgradeStyle("Excavator Efficiency", "<#F2F2F2>"),
        "xpGain" to PickaxeUpgradeStyle("XP Gain", "<#7DFF8A>"),
        "oreFrequency" to PickaxeUpgradeStyle("Mine Richness", "<#43E08A>"),
        "scrollFinder" to PickaxeUpgradeStyle("Scroll Finder", "<#D98CFF>"),
        "sellMultiplier" to PickaxeUpgradeStyle("Sell Multiplier", "<#FFCC66>"),
        "tokenFinder" to PickaxeUpgradeStyle("Token Finder", "<#5DEBFF>"),
        "keyFinder" to PickaxeUpgradeStyle("Key Finder", "<#FFFFFF>"),
        "jackpot" to PickaxeUpgradeStyle("Jackpot", "<#FF66C9>"),
        "combo" to PickaxeUpgradeStyle("Combo Meter", "<#FFB347>"),
        "procPower" to PickaxeUpgradeStyle("Proc Power", "<#C7F0FF>")
    )

    enum class JackpotXpBottleTier(
        val utilityId: String,
        val displayName: String,
        val levelsGranted: Int,
        val color: NamedTextColor
    ) {
        CRUDE("jackpot_xp_bottle_crude", "Crude XP Flask", 5, NamedTextColor.GREEN),
        CHARGED("jackpot_xp_bottle_charged", "Charged XP Flask", 20, NamedTextColor.AQUA),
        CELESTIAL("jackpot_xp_bottle_celestial", "Celestial XP Flask", 50, NamedTextColor.GOLD)
    }

    lateinit var coin: ItemStack
    lateinit var tokenShard: ItemStack
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
    const val TOKEN_NAME = "<#55FFFF>Tokens"
    const val TOKEN_NAME_PLURAL = "<#55FFFF>Tokens"
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

        tokenShard = ItemStack(Material.PRISMARINE_SHARD)
        tokenShard.editMeta { meta ->
            meta.displayName(TextUtil.toComponent(TOKEN_NAME))
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
    fun isTokenShard(item: ItemStack?): Boolean = item?.isSimilar(tokenShard) == true
    fun isPickaxeUpgradeKey(key: String): Boolean = key in pickaxeUpgradeStyles
    fun getPickaxeUpgradeDisplayName(key: String): String = pickaxeUpgradeStyles[key]?.displayName ?: key
    fun getColoredPickaxeUpgradeName(key: String): String =
        pickaxeUpgradeStyles[key]?.let { "${it.color}${it.displayName}" } ?: "&f$key"

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
    fun getBuffPotionType(item: ItemStack?): PotionsManager.BuffType? {
        val utilityId = getUtilityItemType(item) ?: return null
        return PotionsManager.BuffType.entries.firstOrNull { "buff_potion_${it.name.lowercase()}" == utilityId }
    }
    fun isJackpotXpBottle(item: ItemStack?): Boolean = getJackpotXpBottleTier(item) != null
    fun getJackpotXpBottleTier(item: ItemStack?): JackpotXpBottleTier? {
        val utilityId = getUtilityItemType(item) ?: return null
        return JackpotXpBottleTier.entries.firstOrNull { it.utilityId == utilityId }
    }

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
                    TextUtil.toComponent("&7Rolls one random rankup upgrade").decoration(TextDecoration.ITALIC, false),
                )
            )
            meta.persistentDataContainer.set(scrollRarityKey, PersistentDataType.STRING, rarity.id)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        return item
    }

    fun makePickaxe(data: PlayerData, playerLevel: Int, procBoosterActive: Boolean = false): ItemStack {
        val rebirth = data.rebirth
        val fortuneLevel = data.fortuneLevel
        val multiBreakLevel = data.multiBreakLevel
        val oreBoostLevel = data.oreBoostLevel
        val excavatorLevel = data.excavatorLevel
        val lightningLevel = data.lightningLevel
        val virtualJackhammerLevel = data.virtualJackhammerLevel
        val excavatorEfficiencyLevel = data.excavatorEfficiencyLevel
        val xpGainLevel = data.xpGainLevel
        val mineRichnessLevel = data.oreFrequencyLevel
        val scrollFinderLevel = data.scrollFinderLevel
        val sellMultiplierLevel = data.sellMultiplierLevel
        val tokenFinderLevel = data.tokenFinderLevel
        val keyFinderLevel = data.keyFinderLevel
        val jackpotLevel = data.jackpotLevel
        val comboLevel = data.comboLevel
        val procPowerLevel = data.procPowerLevel
        val multiBreakChance = UpgradeFormulas.getMultiBreakBlockQuantity(multiBreakLevel, procBoosterActive, data.multiBreakMaxLevel)
        val oreBoostChance = UpgradeFormulas.getOreBoostChance(oreBoostLevel, data.oreBoostMaxLevel, 0.0) * 100
        val excavatorChance = UpgradeFormulas.getExcavatorChance(excavatorLevel, data.excavatorMaxLevel, 0.0) * 100
        val lightningChance = UpgradeFormulas.getLightningChance(lightningLevel, data.lightningMaxLevel, 0.0) * 100
        val jackhammerChance = UpgradeFormulas.getVirtualJackhammerChance(virtualJackhammerLevel, data.virtualJackhammerMaxLevel, 0.0) * 100
        val efficiencyBoost = UpgradeFormulas.getExcavatorEfficiency(excavatorEfficiencyLevel, data.excavatorEfficiencyMaxLevel, 0.0)
        val xpBoost = ExperienceManager.getExperienceMultiplier(xpGainLevel, data.xpGainMaxLevel)
        val mineRichness = MineManager.getOreFrequencyMultiplier(mineRichnessLevel, data.oreFrequencyMaxLevel, 0.0)
        val sellBoost = UpgradeFormulas.getSellMultiplier(sellMultiplierLevel, data.sellMultiplierMaxLevel)
        val tokenChance = UpgradeFormulas.getTokenFinderChance(tokenFinderLevel, data.tokenFinderMaxLevel) * 100
        val keyChance = UpgradeFormulas.getKeyFinderChance(keyFinderLevel, data.keyFinderMaxLevel) * 100
        val jackpotChance = UpgradeFormulas.getJackpotChance(jackpotLevel, data.jackpotMaxLevel) * 100
        val comboCap = UpgradeFormulas.getComboMaxStreak(comboLevel, data.comboMaxLevel)
        val procPowerChance = UpgradeFormulas.getProcPowerChanceBonus(procPowerLevel, data.procPowerMaxLevel) * 100
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
                    TextUtil.toComponent("&bShift-Right-Click &7to upgrade and push for bigger procs."),
                    TextUtil.toComponent(""),
                    TextUtil.toComponent("&7This pick powers your full mining loop: faster clears, richer ores, keys, jackpots, and token spikes."),
                    TextUtil.toComponent(""),
                    TextUtil.toComponent("&fLevel Path"),
                    TextUtil.toComponent("&8• &fEfficiency &7Lv &b$efficiencyLevel &8| &7+1 every 8 player levels"),
                    TextUtil.toComponent(""),
                    TextUtil.toComponent("&fProc Suite"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("multiBreak")} &7Lv &f$multiBreakLevel &8| &b${"%.2f".format(multiBreakChance)} blocks"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("oreBoost")} &7Lv &f$oreBoostLevel &8| &e${"%.2f".format(oreBoostChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("excavator")} &7Lv &f$excavatorLevel &8| &f${"%.2f".format(excavatorChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("lightning")} &7Lv &f$lightningLevel &8| &e${"%.2f".format(lightningChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("virtualJackhammer")} &7Lv &f$virtualJackhammerLevel &8| &6${"%.2f".format(jackhammerChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("excavatorEfficiency")} &7Lv &f$excavatorEfficiencyLevel &8| &f${"%.2f".format(efficiencyBoost)}x strength"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("procPower")} &7Lv &f$procPowerLevel &8| &b+${"%.2f".format(procPowerChance)}% proc chance"),
                    TextUtil.toComponent(""),
                    TextUtil.toComponent("&fEconomy Suite"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("fortune")} &7Lv &f$fortuneLevel"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("oreFrequency")} &7Lv &f$mineRichnessLevel &8| &2${"%.2f".format(mineRichness)}x richness"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("xpGain")} &7Lv &f$xpGainLevel &8| &a${"%.2f".format(xpBoost)}x XP"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("sellMultiplier")} &7Lv &f$sellMultiplierLevel &8| &6${"%.2f".format(sellBoost)}x sell"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("tokenFinder")} &7Lv &f$tokenFinderLevel &8| &b${"%.2f".format(tokenChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("keyFinder")} &7Lv &f$keyFinderLevel &8| &f${"%.3f".format(keyChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("jackpot")} &7Lv &f$jackpotLevel &8| &d${"%.3f".format(jackpotChance)}% proc"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("combo")} &7Lv &f$comboLevel &8| &6${comboCap} streak cap"),
                    TextUtil.toComponent("&8• ${getColoredPickaxeUpgradeName("scrollFinder")} &7Lv &f$scrollFinderLevel")
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

    fun makeJackpotXpBottle(tier: JackpotXpBottleTier): ItemStack {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        item.editMeta { meta ->
            meta.displayName(storageStyleName(tier.displayName, tier.color))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Right-click to crack this jackpot XP flask.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Grants &b+${tier.levelsGranted} levels &7instantly.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Jackpot contraband drop.").decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            meta.persistentDataContainer.set(utilityItemTypeKey, PersistentDataType.STRING, tier.utilityId)
        }
        return item
    }

    fun makeBuffPotion(buffType: PotionsManager.BuffType): ItemStack {
        val material = when (buffType) {
            PotionsManager.BuffType.MINE_RICHNESS -> Material.EMERALD
            PotionsManager.BuffType.XP -> Material.EXPERIENCE_BOTTLE
            PotionsManager.BuffType.PROC -> Material.NETHER_STAR
            PotionsManager.BuffType.FORTUNE -> Material.DIAMOND
        }
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(storageStyleName(buffType.displayName, when (buffType) {
                    PotionsManager.BuffType.MINE_RICHNESS -> NamedTextColor.GREEN
                    PotionsManager.BuffType.XP -> NamedTextColor.AQUA
                    PotionsManager.BuffType.PROC -> NamedTextColor.LIGHT_PURPLE
                    PotionsManager.BuffType.FORTUNE -> NamedTextColor.GOLD
                }))
                val effectLine = when (buffType) {
                    PotionsManager.BuffType.MINE_RICHNESS -> "&7Mine richness: &f${String.format("%.2f", buffType.multiplier)}x"
                    PotionsManager.BuffType.XP -> "&7XP gain: &f${String.format("%.2f", buffType.multiplier)}x"
                    PotionsManager.BuffType.PROC -> "&7Proc chance: &f+${String.format("%.0f", (buffType.multiplier - 1.0) * 100)}%"
                    PotionsManager.BuffType.FORTUNE -> "&7Fortune payout: &f${String.format("%.2f", buffType.multiplier)}x"
                }
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Right-click to activate").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Duration: &f${buffType.durationMinutes} minutes").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent(effectLine).decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
                meta.persistentDataContainer.set(utilityItemTypeKey, PersistentDataType.STRING, "buff_potion_${buffType.name.lowercase()}")
            }
        }
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

    fun makeAxe(level: Int): ItemStack {
        val (material, sharpCap) = when {
            level <= 6 -> Material.WOODEN_AXE to 4
            level <= 12 -> Material.STONE_AXE to 4
            level <= 18 -> Material.IRON_AXE to 5
            level <= 26 -> Material.DIAMOND_AXE to 6
            else -> Material.NETHERITE_AXE to 8
        }
        val base = when (material) {
            Material.WOODEN_AXE -> 1
            Material.STONE_AXE -> 7
            Material.IRON_AXE -> 13
            Material.DIAMOND_AXE -> 19
            else -> 27
        }
        val sharp = (level - base).coerceAtLeast(0).coerceAtMost(sharpCap)
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
            if (sharp > 0) meta.addEnchant(Enchantment.SHARPNESS, sharp, true)
            if (level >= 12) meta.addEnchant(Enchantment.SMITE, ((level - 8) / 6).coerceAtMost(5), true)
            meta.isUnbreakable = true
        }
        return item
    }

    fun makeBlackMarketGodChestplate(): ItemStack {
        return ItemStack(Material.NETHERITE_CHESTPLATE).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&6&lGod Netherite Chestplate").decoration(TextDecoration.ITALIC, false))
                meta.addEnchant(Enchantment.PROTECTION, 8, true)
                meta.addEnchant(Enchantment.BLAST_PROTECTION, 5, true)
                meta.addEnchant(Enchantment.THORNS, 3, true)
                meta.addEnchant(Enchantment.UNBREAKING, 5, true)
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
                meta.isUnbreakable = true
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Black market PvP contraband.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Heavy defense for danger-zone fights.").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }

    fun makeBlackMarketInfernoSword(): ItemStack {
        return ItemStack(Material.NETHERITE_SWORD).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&c&lInferno Blade").decoration(TextDecoration.ITALIC, false))
                meta.addEnchant(Enchantment.SHARPNESS, 7, true)
                meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true)
                meta.addEnchant(Enchantment.LOOTING, 4, true)
                meta.addEnchant(Enchantment.UNBREAKING, 5, true)
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
                meta.isUnbreakable = true
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Black market PvP contraband.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Burns through players in the danger zone.").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }

    fun makeBlackMarketPhantomBoots(): ItemStack {
        return ItemStack(Material.NETHERITE_BOOTS).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&b&lPhantom Treads").decoration(TextDecoration.ITALIC, false))
                meta.addEnchant(Enchantment.PROTECTION, 6, true)
                meta.addEnchant(Enchantment.FEATHER_FALLING, 8, true)
                meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true)
                meta.addEnchant(Enchantment.SOUL_SPEED, 3, true)
                meta.addEnchant(Enchantment.UNBREAKING, 5, true)
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
                meta.isUnbreakable = true
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Black market PvP contraband.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Mobility boots for ambushes and escapes.").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
    }

    fun makeBlackMarketWarAxe(): ItemStack {
        return ItemStack(Material.NETHERITE_AXE).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&4&lExecutioner Axe").decoration(TextDecoration.ITALIC, false))
                meta.addEnchant(Enchantment.SHARPNESS, 6, true)
                meta.addEnchant(Enchantment.SMITE, 5, true)
                meta.addEnchant(Enchantment.UNBREAKING, 5, true)
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true)
                meta.isUnbreakable = true
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Black market PvP contraband.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Brutal burst damage for close-range picks.").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }
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

enum class KeyRarity(
    val displayName: String,
    val material: Material,
    val color: String,
    val crateId: String
) {
    COMMON("Common", Material.WHITE_DYE, "&f", "common"),
    UNCOMMON("Uncommon", Material.LIME_DYE, "&a", "uncommon"),
    RARE("Rare", Material.ORANGE_DYE, "&6", "rare"),
    EPIC("Epic", Material.PURPLE_DYE, "&5", "epic"),
    LEGENDARY("Legendary", Material.YELLOW_DYE, "&e", "legendary")
}
