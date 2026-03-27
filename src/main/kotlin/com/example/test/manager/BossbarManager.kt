package com.example.test

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object BossbarManager {
    var blocksMinedGlobally = 0L
    var isActive = false
    var isBlocksMinedEvent = false
    var isPlayerCountEvent = false

    var rarity = "normal"
    var multiplier = 1.5
    var weightMultiplier = 1.25
    var type = "GoldRush"
    var durationSeconds = 60

    var requirementBlocks = 0L
    var requirementPlayers = 70

    private var bar: BossBar? = null
    private var activeBarTask: BukkitTask? = null
    private var activeEventEndsAtMillis = 0L

    fun init() {
        initEvent()
        initBossBar()
        checkEventConditions()
    }

    fun shutdown() {
        activeBarTask?.cancel()
        activeBarTask = null
        bar?.removeAll()
        bar = null
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
    }

    fun addPlayer(player: Player) {
        bar?.addPlayer(player)
        updatePlayerCountEvent()
    }

    fun removePlayer(player: Player) {
        bar?.removePlayer(player)
        updatePlayerCountEvent()
    }

    private fun initEvent() {
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false

        getEventRarity()
        getEventMultiplier()
        requirementBlocks = getEventRequirement()
        requirementPlayers = 70
        type = getEventType()
        durationSeconds = getEventDuration()
    }

    private fun initBossBar() {
        bar?.removeAll()
        bar = Bukkit.createBossBar(TextUtil.colorize(getEventTitle(false)), BarColor.GREEN, BarStyle.SOLID)
        Bukkit.getOnlinePlayers().forEach { bar?.addPlayer(it) }
        bar?.progress = 0.0
    }

    fun checkEventConditions() {
        if (Bukkit.getOnlinePlayers().size >= requirementPlayers) {
            startPlayerCountEvent()
            return
        }
        startBlocksMinedEvent()
    }

    private fun startPlayerCountEvent() {
        isPlayerCountEvent = true
        bar?.color = BarColor.BLUE
        bar?.setTitle(TextUtil.colorize("&aPlayers Online Event"))
        Bukkit.broadcast(TextUtil.toComponent("&aNext event starts at $requirementPlayers online players!"))
    }

    private fun startBlocksMinedEvent() {
        isBlocksMinedEvent = true
        bar?.color = BarColor.GREEN
        bar?.setTitle(TextUtil.colorize(getEventTitle(false)))
        Bukkit.broadcast(TextUtil.toComponent("&aNext event starts at ${TextUtil.formatNum(requirementBlocks)} mined blocks!"))
    }

    fun updatePlayerCountEvent() {
        if (!isPlayerCountEvent || isActive) return
        updateEventBar(Bukkit.getOnlinePlayers().size.toLong(), requirementPlayers.toLong(), "&bPlayers Online: ${TextUtil.formatNum(Bukkit.getOnlinePlayers().size)} / ${TextUtil.formatNum(requirementPlayers)}")
    }

    fun updateBlocksMinedEvent() {
        if (!isBlocksMinedEvent || isActive) return
        updateEventBar(blocksMinedGlobally, requirementBlocks, getEventTitle(false))
    }

    private fun updateEventBar(now: Long, requirement: Long, title: String) {
        val progress = min(1.0, now.toDouble() / requirement.toDouble())
        bar?.progress = progress
        bar?.setTitle(TextUtil.colorize(title))
        if (progress >= 1.0) activateEvent()
    }

    private fun activateEvent() {
        if (type == "RareOres" && !RareOresEventManager.canStart()) {
            Bukkit.broadcast(TextUtil.toComponent("&cRare Ores Event could not start."))
            resetEvent()
            return
        }
        isActive = true
        bar?.setTitle(TextUtil.colorize(getEventTitle(true)))
        bar?.color = BarColor.PINK
        startActiveBossBarCountdown()

        val timeText = if (durationSeconds < 60) {
            "$durationSeconds second${if (durationSeconds == 1) "" else "s"}"
        } else {
            val minutes = (durationSeconds / 60).coerceAtLeast(1)
            "$minutes minute${if (minutes == 1) "" else "s"}"
        }

        Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, "block.note_block.pling", 1f, 0.9f) }
        playRarityActivationSound()
        if (type == "RareOres") {
            if (!RareOresEventManager.startFromEventManager()) {
                Bukkit.broadcast(TextUtil.toComponent("&cRare Ores Event could not start."))
                resetEvent()
            }
            return
        }
        Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable { resetEvent() }, durationSeconds * 20L)
    }

    private fun startActiveBossBarCountdown() {
        activeBarTask?.cancel()
        activeEventEndsAtMillis = System.currentTimeMillis() + (durationSeconds * 1000L)
        bar?.progress = 1.0
        activeBarTask = Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            val totalMillis = (durationSeconds * 1000L).coerceAtLeast(1L)
            val remainingMillis = (activeEventEndsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            bar?.progress = remainingMillis.toDouble() / totalMillis.toDouble()
        }, 0L, 1L)
    }

    fun resetEvent() {
        activeBarTask?.cancel()
        activeBarTask = null
        activeEventEndsAtMillis = 0L
        bar?.progress = 0.0
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
        initEvent()
        checkEventConditions()
    }

    fun getActiveEventTimeRemainingMillis(): Long =
        (activeEventEndsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    fun hasActiveSellMultiplier(): Boolean = isActive && type == "GoldRush"

    fun getEventTitle(isActive: Boolean): String {
        val rarityPrefix = getRarityPrefix()
        if (!isActive)
            return "&dNext Event in &a${TextUtil.formatNum(blocksMinedGlobally)}/${TextUtil.formatNum(requirementBlocks)} &dBlocks Mined"
        return when (type) {
            "GoldRush" -> "$rarityPrefix &eGold Rush Event &7(&d${multiplier}x sell)"
            else ->  "$rarityPrefix &bRare Ores Event (${weightMultiplier}x valuable frequency)"
        }
    }

    private fun getRarityPrefix(): String = when (rarity) {
        "normal" -> "&b&lNORMAL"
        "rare" -> "&e&lRARE"
        "mythic" -> "&0&l&kl&5&lMYTHIC&0&l&kl"
        else -> "&e&l&kl&f&lGOD&e&l&kl"
    }

    private fun getEventRarity() {
        val roll = Random.nextInt(0, 101)
        rarity = "normal"
        if (roll > 75) rarity = "rare"
        if (roll > 95) rarity = "mythic"
        if (roll > 99) rarity = "god"
    }

    private fun playRarityActivationSound() {
        val sound = when (rarity) {
            "mythic" -> "entity.ender_dragon.ambient"
            "god" -> "item.trident.thunder"
            else -> return
        }
        Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, sound, 1f, 1f) }
    }

    private fun getEventMultiplier() {
        when (rarity) {
            "normal" -> {
                multiplier = 1.5
                weightMultiplier = 2.5
            }
            "rare" -> {
                multiplier = 2.0
                weightMultiplier = 3.0
            }
            "mythic" -> {
                multiplier = 2.5
                weightMultiplier = 4.0
            }
            else -> {
                multiplier = 3.0
                weightMultiplier = 5.0
            }
        }
    }

    private fun getEventRequirement(): Long {
        return when (rarity) {
            "normal" -> Random.nextInt(25, 50) * 10L * max(MineManager.getActivePlayersInMine().size, 1)
            "rare" -> Random.nextInt(50, 75) * 10L * MineManager.getActivePlayersInMine().size
            "mythic" -> Random.nextInt(75, 100) * 10L * MineManager.getActivePlayersInMine().size
            else -> Random.nextInt(100, 250) * 10L * MineManager.getActivePlayersInMine().size
        }
    }

    private fun getEventType(): String {
        return when (Random.nextInt(1, 3)) {
            1 -> "GoldRush"
            else -> "RareOres"
        }
    }

    private fun getEventDuration(): Int = when (type) {
        "RareOres" -> 60
        else -> 20
    }

    fun forceEventType(typeName: String? = null): String {
        val forcedType = when (typeName?.lowercase()) {
            null, "", "random", "reroll" -> getEventType()
            "goldrush", "gold" -> "GoldRush"
            "rareores", "rareore", "rarer", "richmine" -> "RareOres"
            else -> return ""
        }

        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = true
        isPlayerCountEvent = false
        activeBarTask?.cancel()
        activeBarTask = null
        activeEventEndsAtMillis = 0L
        type = forcedType
        durationSeconds = getEventDuration()
        requirementBlocks = getEventRequirement()
        bar?.progress = 0.0
        bar?.color = BarColor.GREEN
        bar?.setTitle(TextUtil.colorize(getEventTitle(false)))
        return forcedType
    }

    fun forceEventRarity(rarityName: String? = null): String {
        val forcedRarity = when (rarityName?.lowercase()) {
            null, "", "random", "reroll" -> {
                getEventRarity()
                rarity
            }
            "normal" -> "normal"
            "rare" -> "rare"
            "mythic" -> "mythic"
            "god" -> "god"
            else -> return ""
        }

        rarity = forcedRarity
        getEventMultiplier()
        requirementBlocks = getEventRequirement()
        requirementPlayers = 70
        bar?.progress = 0.0
        bar?.color = BarColor.GREEN
        bar?.setTitle(TextUtil.colorize(getEventTitle(false)))
        return forcedRarity
    }

    fun cancelCurrentEvent() {
        activeBarTask?.cancel()
        activeBarTask = null
        activeEventEndsAtMillis = 0L
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
        bar?.progress = 0.0
    }
}
