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
        val appearanceChancePercent: Double
    )

    data class MarketOffer(
        val item: ItemStack,
        val displayName: String,
        val amount: Int,
        val price: Long,
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
    }

    private val templates = listOf(
        ShopTemplate(ItemManager.dynamite.clone(), "Dynamite", 2..8, 3..8, 240L..400L, 85.0),
        ShopTemplate(ItemManager.procBooster.clone(), "Power Up", 1..1, 1..2, 20_000L..40_000L, 60.0),
        ShopTemplate(ItemManager.chargedDynamite.clone(), "Charged Dynamite", 2..5, 3..8, 70_000L..87_000L, 42.0),
        ShopTemplate(ItemManager.lightningRodDeployable.clone(), "Storm Rod", 1..1, 1..1, 1_000_000_000L..2_500_000_000L, 28.0),
        ShopTemplate(ItemManager.nuke.clone(), "Nuke", 1..3, 3..8, 4_000_000L..5_000_000L, 18.0)
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
        addRestockInfo(gui, marketState)
        val presentSlots = marketState.offers.keys
        for ((slot, _) in OFFER_SLOTS) {
            val offer = marketState.offers[slot]
            if (offer != null) {
                addShopItem(player, gui, slot, offer)
            } else {
                addUnavailableItem(gui, slot)
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
            offers[slot] = MarketOffer(
                item = template.item.clone(),
                displayName = template.displayName,
                amount = amount,
                price = price,
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

        gui.setItem(49, GuiItem(createWalletCard(player)))
    }

    private fun addRestockInfo(gui: Gui, marketState: PlayerMarketState) {
        val remaining = Duration.between(ZonedDateTime.now(), marketState.nextRestockAt).coerceAtLeast(Duration.ZERO)
        val secondsLeft = remaining.seconds
        val minutes = secondsLeft / 60L
        val seconds = secondsLeft % 60L

        val info = ItemStack(Material.CLOCK)
        info.editMeta { meta ->
            meta.displayName(TextUtil.toComponent("&eRestock Timer").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Fresh contraband every cycle.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Restocks every &e5 minutes&7.").decoration(TextDecoration.ITALIC, false),
                    TextUtil.toComponent("&7Next drop in: &e${String.format("%02d:%02d", minutes, seconds)}").decoration(TextDecoration.ITALIC, false)
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        gui.setItem(4, GuiItem(info))
    }

    private fun addShopItem(player: Player, gui: Gui, slot: Int, offer: MarketOffer) {
        val affordColor = if (DataStore.get(player.uniqueId).balance >= offer.price) "&a" else "&c"
        val stack = offer.item.clone().apply { amount = offer.amount }
        stack.editMeta { meta ->
            val lore = mutableListOf<Component>()
            lore += TextUtil.toComponent("&7${getOfferFlavor(offer.displayName)}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("&7Chance: &e${formatChance(offer.appearanceChancePercent)}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("&7Bundle: &f${offer.amount}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("&7Stock: &f${offer.stock}").decoration(TextDecoration.ITALIC, false)
            lore += TextUtil.toComponent("${affordColor}Price: &b${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}").decoration(TextDecoration.ITALIC, false)
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
            meta.displayName(TextUtil.toComponent("&8$label").decoration(TextDecoration.ITALIC, false))
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
        if (data.balance >= offer.price) {
            data.balance -= offer.price
            offer.stock -= 1
            player.inventory.addItem(offer.item.clone().apply { amount = offer.amount })
            SessionTimelineManager.record(
                player,
                "Purchased x${offer.amount} ${offer.displayName} for ${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}"
            )
            player.sendMessage(
                TextUtil.colorize(
                    "&aPurchased &7x${offer.amount} &a${offer.displayName} &afor &b${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}&a!"
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

    private fun createFrameItem(): ItemStack =
        ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
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

    private fun createWalletCard(player: Player): ItemStack =
        ItemStack(Material.GOLD_INGOT).apply {
            editMeta { meta ->
                meta.displayName(TextUtil.toComponent("&6Coins").decoration(TextDecoration.ITALIC, false))
                meta.lore(
                    listOf("&f${TextUtil.formatNum(DataStore.get(player.uniqueId).balance)} ${ItemManager.COIN_NAME_PLURAL}")
                        .map { TextUtil.toComponent(it).decoration(TextDecoration.ITALIC, false) }
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
