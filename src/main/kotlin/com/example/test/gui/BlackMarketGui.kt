package com.example.test

import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
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
        val priceRange: LongRange
    )

    data class MarketOffer(
        val item: ItemStack,
        val displayName: String,
        val amount: Int,
        val price: Long,
        var stock: Int
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
            20 to 0,
            24 to 1
        )
        private const val SCROLL_SLOT = 22
    }

    private val templates = listOf(
        ShopTemplate(ItemManager.dynamite.clone(), "Dynamite", 2..5, 3..8, 240L..400L),
        ShopTemplate(ItemManager.procBooster.clone(), "Power Up", 1..1, 1..2, 20_000L..30_000L)
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
            .rows(5)
            .disableAllInteractions()
            .create()

        addRestockInfo(gui, marketState)
        for ((slot, offer) in marketState.offers) {
            addShopItem(player, gui, slot, offer)
        }
        return gui
    }

    private fun createMarketState(): PlayerMarketState {
        val offers = mutableMapOf<Int, MarketOffer>()
        for ((slot, templateIndex) in OFFER_SLOTS) {
            val template = templates[templateIndex]
            val amount = template.amountRange.random()
            val stock = template.stockRange.random()
            val price = template.priceRange.random()
            offers[slot] = MarketOffer(
                item = template.item.clone(),
                displayName = template.displayName,
                amount = amount,
                price = price,
                stock = stock
            )
        }

        val scrollRarity = rollBlackMarketScrollRarity()
        offers[SCROLL_SLOT] = MarketOffer(
            item = ItemManager.makeUpgradeScroll(scrollRarity),
            displayName = "${scrollRarity.displayName} Upgrade Scroll",
            amount = 1,
            price = getScrollPrice(scrollRarity, 1),
            stock = 1
        )

        return PlayerMarketState(
            offers = offers,
            nextRestockAt = nextRestockMark()
        )
    }

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
            ScrollRarity.NORMAL -> 7_500L
            ScrollRarity.RARE -> 20_000L
            ScrollRarity.MYTHIC -> 75_000L
            ScrollRarity.GOD -> 250_000L
            ScrollRarity.SECRET -> 2_500_000L
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

    private fun addRestockInfo(gui: Gui, marketState: PlayerMarketState) {
        val remaining = Duration.between(ZonedDateTime.now(), marketState.nextRestockAt).coerceAtLeast(Duration.ZERO)
        val secondsLeft = remaining.seconds
        val minutes = secondsLeft / 60L
        val seconds = secondsLeft % 60L

        val info = ItemStack(Material.CLOCK)
        info.editMeta { meta ->
            meta.displayName(TextUtil.toComponent("&8&lRestock Timer"))
            meta.lore(
                listOf(
                    TextUtil.toComponent("&7Fresh contraband every cycle."),
                    TextUtil.toComponent("&7Restocks every &e5 minutes&7."),
                    TextUtil.toComponent("&7Next restock in: &e${String.format("%02d:%02d", minutes, seconds)}")
                )
            )
        }
        gui.setItem(4, GuiItem(info))
    }

    private fun addShopItem(player: Player, gui: Gui, slot: Int, offer: MarketOffer) {
        val affordColor = if (DataStore.get(player.uniqueId).balance >= offer.price) "&a" else "&c"
        val stack = offer.item.clone().apply { amount = offer.amount }
        stack.editMeta { meta ->
            val lore = (meta.lore() ?: emptyList()).toMutableList()
            if (lore.isNotEmpty()) lore += TextUtil.toComponent("")
            lore += TextUtil.toComponent("&7Bundle: &e${offer.amount}")
            lore += TextUtil.toComponent("&7Stock left: &f${offer.stock}")
            lore += TextUtil.toComponent("${affordColor}Price: &b${TextUtil.formatNum(offer.price)} ${ItemManager.COIN_NAME_PLURAL}")
            lore += if (offer.stock > 0) {
                TextUtil.toComponent("&aClick to purchase")
            } else {
                TextUtil.toComponent("&cSold out")
            }
            meta.lore(lore)
        }
        gui.setItem(slot, GuiItem(stack) {
            if (!GuiClickDebounce.tryAcquire(player)) return@GuiItem
            purchase(player, offer)
        })
    }

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
