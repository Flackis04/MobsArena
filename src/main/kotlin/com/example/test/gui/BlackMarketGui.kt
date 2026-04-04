package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

class BlackMarketGui : Listener {
    data class ShopTemplate(
        val item: ItemStack,
        val displayName: String,
        val amountRange: IntRange,
        val stockRange: IntRange,
        val priceRange: LongRange,
        val tokenPriceRange: LongRange = 0L..0L,
        val appearanceChancePercent: Double
    )

    data class MarketOffer(
        val item: ItemStack,
        val displayName: String,
        val amount: Int,
        val price: Long,
        val tokenPrice: Long,
        var stock: Int,
        val appearanceChancePercent: Double
    )

    data class PlayerMarketState(
        val offers: MutableMap<Int, MarketOffer>,
        var nextRestockAt: ZonedDateTime
    )

    companion object {
        private const val RESTOCK_CHECK_INTERVAL_TICKS = 20L
        private const val RESTOCK_INTERVAL_MINUTES = 5L
        private const val BLACK_MARKET_NORMAL_SCROLL_WEIGHT = 75.0
        private const val BLACK_MARKET_RARE_SCROLL_WEIGHT = 20.0
        private const val BLACK_MARKET_MYTHIC_SCROLL_WEIGHT = 4.0
        private const val BLACK_MARKET_GOD_SCROLL_WEIGHT = 0.9
        private const val BLACK_MARKET_SECRET_SCROLL_WEIGHT = 0.1
        private val OFFER_SLOTS = linkedMapOf(
            19 to 0,
            20 to 1,
            21 to 2,
            22 to 3,
            23 to 4
        )
        private const val SCROLL_SLOT = 24
        private const val LIGHTNING_ROD_SLOT = 25
        private val PVP_OFFER_SLOTS = linkedMapOf(
            28 to 0,
            29 to 1,
            30 to 2,
            31 to 3,
            32 to 4,
            33 to 5,
            34 to 6,
            37 to 7,
            38 to 8,
            39 to 9,
            40 to 10,
            41 to 11
        )
    }

    private val templates = listOf(
        ShopTemplate(ItemManager.dynamite.clone(), "Dynamite", 2..8, 3..8, 240L..400L, 0L..0L, 85.0),
        ShopTemplate(ItemManager.procBooster.clone(), "Power Up", 1..1, 1..2, 20_000L..40_000L, 0L..0L, 60.0),
        ShopTemplate(ItemManager.chargedDynamite.clone(), "Charged Dynamite", 2..5, 3..8, 70_000L..87_000L, 0L..0L, 42.0),
        ShopTemplate(ItemManager.lightningRodDeployable.clone(), "Storm Rod", 1..1, 1..1, 1_000_000_000L..2_500_000_000L, 0L..0L, 28.0),
        ShopTemplate(ItemManager.nuke.clone(), "Nuke", 1..3, 3..8, 4_000_000L..5_000_000L, 0L..0L, 18.0)
    )
    private val pvpTemplates = listOf(
        ShopTemplate(ItemManager.makeBlackMarketGodChestplate(), "God Netherite Chestplate", 1..1, 1..1, 0L..0L, 60_000L..110_000L, 1.8),
        ShopTemplate(ItemManager.makeBlackMarketInfernoSword(), "Inferno Blade", 1..1, 1..1, 0L..0L, 45_000L..85_000L, 2.8),
        ShopTemplate(ItemManager.makeBlackMarketPhantomBoots(), "Phantom Treads", 1..1, 1..1, 0L..0L, 30_000L..58_000L, 4.0),
        ShopTemplate(ItemManager.makeBlackMarketWarAxe(), "Executioner Axe", 1..1, 1..1, 0L..0L, 34_000L..60_000L, 3.1),
        ShopTemplate(ItemStack(Material.COBWEB), "Cobweb Bundle", 8..24, 1..3, 0L..0L, 700L..1_800L, 28.0),
        ShopTemplate(ItemManager.makeMace(8), "War Mace", 1..1, 1..1, 0L..0L, 14_000L..28_000L, 6.5),
        ShopTemplate(ItemStack(Material.WIND_CHARGE), "Wind Charge Bundle", 10..24, 1..3, 0L..0L, 1_100L..2_600L, 24.0),
        ShopTemplate(ItemStack(Material.ENCHANTED_GOLDEN_APPLE), "Gap Bundle", 2..8, 1..2, 0L..0L, 3_500L..9_000L, 18.0),
        ShopTemplate(ItemStack(Material.GOLDEN_APPLE), "Golden Apple Cache", 8..20, 1..3, 0L..0L, 1_200L..2_800L, 26.0),
        ShopTemplate(ItemStack(Material.ENDER_PEARL), "Pearl Bundle", 6..16, 1..3, 0L..0L, 1_600L..3_400L, 20.0),
        ShopTemplate(ItemStack(Material.OBSIDIAN), "Obsidian Stack", 20..48, 1..2, 0L..0L, 1_800L..4_400L, 17.0),
        ShopTemplate(makeBlackMarketBulwarkShield(), "Bulwark Shield", 1..1, 1..1, 0L..0L, 10_000L..20_000L, 7.5)
    )

    private val marketStates = mutableMapOf<UUID, PlayerMarketState>()
    private val openGuis = mutableMapOf<UUID, Gui>()
    private var refreshTask: BukkitTask? = null

    fun init() {
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable { refreshRestocksForOnlinePlayers() },
            RESTOCK_CHECK_INTERVAL_TICKS,
            RESTOCK_CHECK_INTERVAL_TICKS
        )
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable { refreshOpenBlackmarketViews() },
            20L,
            20L
        )
    }

    fun openMain(player: Player) {
        getOrCreateMarketState(player.uniqueId)
        val gui = createMainGui(player)
        openGuis[player.uniqueId] = gui
        gui.setCloseGuiAction {
            openGuis.remove(player.uniqueId)
            GuiClickDebounce.clear(player)
        }
        gui.open(player)
    }

    fun getTimeUntilRestock(playerId: UUID): Duration {
        val marketState = getOrCreateMarketState(playerId)
        return Duration.between(ZonedDateTime.now(), marketState.nextRestockAt).coerceAtLeast(Duration.ZERO)
    }

    private fun createMainGui(player: Player): Gui {
        val marketState = getOrCreateMarketState(player.uniqueId)
        val gui = Gui.gui()
            .title(TextUtil.toComponent("&8Black Market"))
            .rows(6)
            .disableAllInteractions()
            .create()

        renderFrame(gui, player, marketState)
        val presentSlots = marketState.offers.keys
        for ((slot, _) in OFFER_SLOTS) {
            val offer = marketState.offers[slot]
            if (offer != null) {
                addShopItem(player, gui, slot, offer)
            } else {
                addUnavailableItem(gui, slot)
            }
        }
        for ((slot, _) in PVP_OFFER_SLOTS) {
            val offer = marketState.offers[slot]
            if (offer != null) {
                addShopItem(player, gui, slot, offer)
            } else {
                addUnavailableItem(gui, slot, "PvP Contraband")
            }
        }
        addSpecialOffer(player, gui, marketState, SCROLL_SLOT, "Mystery Scroll")
        addSpecialOffer(player, gui, marketState, LIGHTNING_ROD_SLOT, "Storm Rod")
        return gui
    }

    private fun createMarketState(): PlayerMarketState {
        val offers = mutableMapOf<Int, MarketOffer>()
        for ((slot, templateIndex) in OFFER_SLOTS) {
            val template = templates[templateIndex]
            if (!rollAppearance(template.appearanceChancePercent)) {
                continue
            }
            val amount = template.amountRange.random()
            val stock = template.stockRange.random()
            val price = template.priceRange.random()
            val tokenPrice = template.tokenPriceRange.random()
            offers[slot] = MarketOffer(
                item = template.item.clone(),
                displayName = template.displayName,
                amount = amount,
                price = price,
                tokenPrice = tokenPrice,
                stock = stock,
                appearanceChancePercent = template.appearanceChancePercent
            )
        }
        for ((slot, templateIndex) in PVP_OFFER_SLOTS) {
            val template = pvpTemplates[templateIndex]
            if (!rollAppearance(template.appearanceChancePercent)) {
                continue
            }
            val amount = template.amountRange.random()
            val stock = template.stockRange.random()
            val price = template.priceRange.random()
            val tokenPrice = template.tokenPriceRange.random()
            offers[slot] = MarketOffer(
                item = template.item.clone(),
                displayName = template.displayName,
                amount = amount,
                price = price,
                tokenPrice = tokenPrice,
                stock = stock,
                appearanceChancePercent = template.appearanceChancePercent
            )
        }

        if (rollAppearance(22.0)) {
            val scrollRarity = rollBlackMarketScrollRarity()
            offers[SCROLL_SLOT] = MarketOffer(
                item = ItemManager.makeUpgradeScroll(scrollRarity),
                displayName = "${scrollRarity.displayName} Upgrade Scroll",
                amount = 1,
                price = getScrollPrice(scrollRarity, 1),
                tokenPrice = 0L,
                stock = 1,
                appearanceChancePercent = 22.0
            )
        }
        if (rollAppearance(5.0)) {
            offers[LIGHTNING_ROD_SLOT] = MarketOffer(
                item = ItemManager.lightningRodDeployable.clone(),
                displayName = "Storm Rod",
                amount = 1,
                price = 10_000_000_000L,
                tokenPrice = 0L,
                stock = 1,
                appearanceChancePercent = 5.0
            )
        }

        return PlayerMarketState(
            offers = offers,
            nextRestockAt = nextRestockMark()
        )
    }

    private fun rollAppearance(chancePercent: Double): Boolean =
        kotlin.random.Random.nextDouble(100.0) < chancePercent

    private fun rollBlackMarketScrollRarity(): ScrollRarity {
        val totalWeight =
            BLACK_MARKET_NORMAL_SCROLL_WEIGHT +
                BLACK_MARKET_RARE_SCROLL_WEIGHT +
                BLACK_MARKET_MYTHIC_SCROLL_WEIGHT +
                BLACK_MARKET_GOD_SCROLL_WEIGHT +
                BLACK_MARKET_SECRET_SCROLL_WEIGHT
        val roll = kotlin.random.Random.nextDouble() * totalWeight

        return when {
            roll < BLACK_MARKET_NORMAL_SCROLL_WEIGHT -> ScrollRarity.NORMAL
            roll < BLACK_MARKET_NORMAL_SCROLL_WEIGHT + BLACK_MARKET_RARE_SCROLL_WEIGHT -> ScrollRarity.RARE
            roll < BLACK_MARKET_NORMAL_SCROLL_WEIGHT + BLACK_MARKET_RARE_SCROLL_WEIGHT + BLACK_MARKET_MYTHIC_SCROLL_WEIGHT -> ScrollRarity.MYTHIC
            roll < BLACK_MARKET_NORMAL_SCROLL_WEIGHT + BLACK_MARKET_RARE_SCROLL_WEIGHT + BLACK_MARKET_MYTHIC_SCROLL_WEIGHT + BLACK_MARKET_GOD_SCROLL_WEIGHT -> ScrollRarity.GOD
            else -> ScrollRarity.SECRET
        }
    }

    private fun getScrollPrice(rarity: ScrollRarity, amount: Int): Long {
        val unitPrice = when (rarity) {
            ScrollRarity.NORMAL -> 80_000L
            ScrollRarity.RARE -> 250_000L
            ScrollRarity.MYTHIC -> 5_000_000L
            ScrollRarity.GOD -> 40_000_000L
            ScrollRarity.SECRET -> 250_000_000L
        }
        return unitPrice * amount.coerceAtLeast(1)
    }

    private fun getOrCreateMarketState(playerId: UUID): PlayerMarketState {
        checkRestock(playerId)
        return marketStates.getOrPut(playerId) { createMarketState() }
    }

    private fun checkRestock(playerId: UUID): Boolean {
        val state = marketStates[playerId] ?: return false
        val now = ZonedDateTime.now()
        if (!now.isBefore(state.nextRestockAt)) {
            marketStates[playerId] = createMarketState()
            return true
        }
        return false
    }

    private fun refreshRestocksForOnlinePlayers() {
        var didRestockAny = false
        Bukkit.getOnlinePlayers().forEach { player ->
            if (checkRestock(player.uniqueId)) {
                didRestockAny = true
            }
        }
        if (didRestockAny) {
            Bukkit.broadcast(TextUtil.toComponent("&7The &cʙʟᴀᴄᴋᴍᴀʀᴋᴇᴛ &7has Restocked!"))
        }
    }

    private fun renderFrame(gui: Gui, player: Player, marketState: PlayerMarketState) {
        val filler = createFrameItem()
        for (slot in 0 until gui.inventory.size) {
            gui.setItem(slot, GuiItem(filler))
        }
    }

    private fun addShopItem(player: Player, gui: Gui, slot: Int, offer: MarketOffer) {
        val data = DataStore.get(player.uniqueId)
        val canAffordCoins = data.balance >= offer.price
        val canAffordTokens = data.tokens >= offer.tokenPrice
        val canAfford = canAffordCoins && canAffordTokens
        val affordColor = if (canAfford) "&a" else "&c"
        val stack = offer.item.clone().apply { amount = offer.amount }
        stack.editMeta { meta ->
            val lore = mutableListOf<Component>()
            lore += TextUtil.toComponent("&7${getOfferFlavor(offer.displayName)}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("&7Chance: &e${formatChance(offer.appearanceChancePercent)}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("&7Bundle: &f${offer.amount}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("&7Stock: &f${offer.stock}").decoration(TextDecoration.ITALIC, false)
            if (offer.price > 0L) {
                lore += TextUtil.toComponent("${if (canAffordCoins) "&a" else "&c"}Coins: &b${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false)
            }
            if (offer.tokenPrice > 0L) {
                lore += TextUtil.toComponent("${if (canAffordTokens) "&a" else "&c"}Tokens: &b${TextUtil.formatNum(offer.tokenPrice)}").decoration(TextDecoration.ITALIC, false)
            }
            val enchantLines = getEnchantLore(stack)
            if (enchantLines.isNotEmpty()) {
                lore += TextUtil.toComponent("").decoration(TextDecoration.ITALIC, false)
                lore += TextUtil.toComponent("&7Enchants:").decoration(TextDecoration.ITALIC, false)
                lore += enchantLines.map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) }
            }
            lore += TextUtil.toComponent("").decoration(TextDecoration.ITALIC, false)
            lore += if (offer.stock > 0) {
                TextUtil.toComponent("&aClick to buy now").decoration(TextDecoration.ITALIC, false)
            } else {
                TextUtil.toComponent("&cSold out for this cycle").decoration(TextDecoration.ITALIC, false)
            }
            meta.lore(lore)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        gui.setItem(slot, GuiItem(stack) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            purchase(player, offer)
        })
    }

    private fun addSpecialOffer(player: Player, gui: Gui, marketState: PlayerMarketState, slot: Int, label: String) {
        val offer = marketState.offers[slot]
        if (offer != null) {
            addShopItem(player, gui, slot, offer)
        } else {
            addUnavailableItem(gui, slot, label)
        }
    }

    private fun addUnavailableItem(gui: Gui, slot: Int, label: String = "Contraband") {
        val stack = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        stack.editMeta { meta ->
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Nothing rolled into this slot.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Check the next restock window.").decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        gui.setItem(slot, GuiItem(stack))
    }

    private fun formatChance(chancePercent: Double): String =
        if (chancePercent % 1.0 == 0.0) "${chancePercent.toInt()}%" else String.format("%.1f%%", chancePercent)

    private fun purchase(player: Player, offer: MarketOffer) {
        if (offer.stock <= 0) {
            player.sendMessage(TextUtil.colorize("&cThat item is sold out. Wait for the next restock."))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            openMain(player)
            return
        }

        if (player.inventory.firstEmpty() == -1) {
            player.sendMessage(TextUtil.colorize("&cYour inventory is full!"))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
            return
        }

        val data = DataStore.get(player.uniqueId)
        if (data.balance >= offer.price && data.tokens >= offer.tokenPrice) {
            data.balance -= offer.price
            data.tokens -= offer.tokenPrice
            offer.stock -= 1
            player.inventory.addItem(offer.item.clone().apply { amount = offer.amount })
            SessionTimelineManager.record(
                player,
                "Purchased x${offer.amount} ${offer.displayName} for ${formatOfferPrice(offer)}"
            )
            player.sendMessage(
                TextUtil.colorize(
                    "&aPurchased &7x${offer.amount} &a${offer.displayName} &afor ${formatOfferPriceColored(offer)}&a!"
                )
            )
            player.playSound(player.location, "entity.experience_orb.pickup", 1f, 1f)
            ScoreboardManager.updateBoard(player)
            openMain(player)
        } else {
            player.sendMessage(TextUtil.colorize("&cYou can't afford this item!"))
            player.playSound(player.location, "block.note_block.bass", 1f, 1f)
        }
    }

    private fun getEnchantLore(item: ItemStack): List<String> =
        item.itemMeta.enchants.entries
            .sortedBy { it.key.key.key }
            .map { (enchant, level) -> "&b${formatEnchantName(enchant.key.key)} &7${toRoman(level)}" }

    private fun formatEnchantName(key: String): String =
        key.split('_').joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }

    private fun toRoman(value: Int): String {
        val numerals = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var remaining = value.coerceAtLeast(1)
        val builder = StringBuilder()
        for ((amount, numeral) in numerals) {
            while (remaining >= amount) {
                builder.append(numeral)
                remaining -= amount
            }
        }
        return builder.toString()
    }

    private fun formatOfferPrice(offer: MarketOffer): String =
        buildList {
            if (offer.price > 0L) add("${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}")
            if (offer.tokenPrice > 0L) add("${TextUtil.formatNum(offer.tokenPrice)} tokens")
        }.joinToString(" and ")

    private fun formatOfferPriceColored(offer: MarketOffer): String =
        buildList {
            if (offer.price > 0L) add("&b${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}")
            if (offer.tokenPrice > 0L) add("&b${TextUtil.formatNum(offer.tokenPrice)} tokens")
        }.joinToString(" &7and ")

    private fun createFrameItem(): ItemStack =
        ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }

    private fun makeBlackMarketBulwarkShield(): ItemStack =
        ItemStack(Material.SHIELD).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&e&lBulwark Shield").decoration(TextDecoration.ITALIC, false))
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true)
                meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true)
                meta.addEnchant(org.bukkit.enchantments.Enchantment.VANISHING_CURSE, 1, true)
                meta.isUnbreakable = true
                meta.lore(
                    listOf(
                        TextUtil.toComponent("&7Black market PvP contraband.").decoration(TextDecoration.ITALIC, false),
                        TextUtil.toComponent("&7Defensive utility for danger-zone duels.").decoration(TextDecoration.ITALIC, false)
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }

    private fun createTitleCard(): ItemStack =
        ItemStack(Material.NETHER_STAR).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&cBlack Market").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf(
                        "&7Rotating contraband for players who spend big.",
                        "&7When a slot misses its roll, it stays dark until restock."
                    ).map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) }
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            }
        }

    private fun getOfferFlavor(label: String): String = when {
        label.contains("Storm Rod", ignoreCase = true) -> "Rare deployable power."
        label.contains("Scroll", ignoreCase = true) -> "Rare upgrade pull."
        label.contains("Nuke", ignoreCase = true) -> "Huge mine burst."
        label.contains("Charged Dynamite", ignoreCase = true) -> "Heavy blast charge."
        label.contains("Dynamite", ignoreCase = true) -> "Fast mine burst."
        label.contains("Power Up", ignoreCase = true) -> "Short proc boost."
        label.contains("Inferno Blade", ignoreCase = true) -> "Rare PvP sword with fire and burst."
        label.contains("God Netherite Chestplate", ignoreCase = true) -> "Ultra-rare tank armor for danger-zone fights."
        label.contains("Phantom Treads", ignoreCase = true) -> "Rare movement armor for escapes."
        label.contains("Executioner Axe", ignoreCase = true) -> "Rare PvP axe for brutal burst."
        label.contains("Bulwark Shield", ignoreCase = true) -> "Rare shield for safer trades."
        label.contains("Pearl", ignoreCase = true) -> "Escape or chase utility."
        label.contains("Golden Apple", ignoreCase = true) -> "Sustain for drawn-out fights."
        label.contains("Gap", ignoreCase = true) -> "Premium healing for clutch fights."
        label.contains("Obsidian", ignoreCase = true) -> "Fast cover for zone fights."
        label.contains("Cobweb", ignoreCase = true) -> "Trap and isolate targets."
        label.contains("Wind Charge", ignoreCase = true) -> "Mobility and knockback utility."
        label.contains("Mace", ignoreCase = true) -> "Burst weapon for risky engages."
        label.contains("Upgrade Snowball", ignoreCase = true) -> "Tier-up your mine."
        else -> "Rare contraband."
    }

    private fun nextRestockMark(): ZonedDateTime {
        val now = ZonedDateTime.now()
        val nextMinute = (((now.minute / RESTOCK_INTERVAL_MINUTES) + 1) * RESTOCK_INTERVAL_MINUTES).toInt()
        return if (nextMinute < 60) {
            now.withMinute(nextMinute).withSecond(0).withNano(0)
        } else {
            now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        }
    }

    private fun refreshOpenBlackmarketViews() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val gui = openGuis[player.uniqueId] ?: return@forEach
            checkRestock(player.uniqueId)
            if (player.openInventory.topInventory.holder != gui) return@forEach
            val updated = createMainGui(player)
            openGuis[player.uniqueId] = updated
            updated.setCloseGuiAction { openGuis.remove(player.uniqueId) }
            updated.open(player)
        }
    }
}
