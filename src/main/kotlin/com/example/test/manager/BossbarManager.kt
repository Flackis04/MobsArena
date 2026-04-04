package com.example.test

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.io.File
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
    var durationSeconds = 40

    var requirementBlocks = 0L
    var requirementPlayers = 70

    private var bar: BossBar? = null
    private val tutorialBars = mutableMapOf<java.util.UUID, BossBar>()
    private var activeBarTask: BukkitTask? = null
    private var eventEndTask: BukkitTask? = null
    private var activeEventEndsAtMillis = 0L
    private lateinit var eventStateFile: File
    private lateinit var eventStateConfig: YamlConfiguration

    fun init(dataFolder: File) {
        eventStateFile = File(dataFolder, "event-state.yml")
        eventStateConfig = YamlConfiguration.loadConfiguration(eventStateFile)
        initEvent()
        initBossBar()
        val restored = restoreSavedActiveEvent()
        if (!restored) {
            checkEventConditions()
        }
    }

    fun shutdown() {
        saveState()
        activeBarTask?.cancel()
        activeBarTask = null
        eventEndTask?.cancel()
        eventEndTask = null
        bar?.removeAll()
        tutorialBars.values.forEach(BossBar::removeAll)
        tutorialBars.clear()
        bar = null
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
    }

    fun addPlayer(player: Player) {
        refreshPlayer(player)
        reevaluateWaitingTrigger()
    }

    fun removePlayer(player: Player) {
        bar?.removePlayer(player)
        tutorialBars.remove(player.uniqueId)?.removeAll()
        reevaluateWaitingTrigger()
    }

    fun refreshPlayer(player: Player) {
        if (TutorialManager.isTutorialMode(player)) {
            bar?.removePlayer(player)
            val tutorialState = TutorialManager.getBossbar(player)
            if (tutorialState == null) {
                tutorialBars.remove(player.uniqueId)?.removeAll()
                return
            }
            val tutorialBar = tutorialBars.getOrPut(player.uniqueId) {
                Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID)
            }
            tutorialBar.setTitle(TextUtil.colorize(tutorialState.title))
            tutorialBar.progress = tutorialState.progress
            tutorialBar.color = tutorialState.color
            tutorialBar.style = tutorialState.style
            if (!tutorialBar.players.contains(player)) {
                tutorialBar.addPlayer(player)
            }
            reevaluateWaitingTrigger()
            return
        }

        tutorialBars.remove(player.uniqueId)?.removeAll()
        if (!(bar?.players?.contains(player) ?: false)) {
            bar?.addPlayer(player)
        }
        if (isActive) {
            bar?.setTitle(TextUtil.colorize(getEventTitle(true)))
        } else if (isPlayerCountEvent) {
            updatePlayerCountEvent()
        } else if (isBlocksMinedEvent) {
            updateBlocksMinedEvent()
        }
        reevaluateWaitingTrigger()
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
        Bukkit.getOnlinePlayers().forEach(::refreshPlayer)
        bar?.progress = 0.0
    }

    fun checkEventConditions() {
        if (getEligibleOnlinePlayerCount() >= requirementPlayers) {
            startPlayerCountEvent()
            return
        }
        startBlocksMinedEvent()
    }

    private fun startPlayerCountEvent() {
        isBlocksMinedEvent = false
        isPlayerCountEvent = true
        bar?.color = BarColor.BLUE
        bar?.setTitle(TextUtil.colorize("&aPlayers Online Event"))
        updatePlayerCountEvent()
    }

    private fun startBlocksMinedEvent() {
        isPlayerCountEvent = false
        isBlocksMinedEvent = true
        bar?.color = BarColor.GREEN
        bar?.setTitle(TextUtil.colorize(getEventTitle(false)))
        updateBlocksMinedEvent()
    }

    fun updatePlayerCountEvent() {
        if (!isPlayerCountEvent || isActive) return
        val eligiblePlayers = getEligibleOnlinePlayerCount()
        updateEventBar(
            eligiblePlayers.toLong(),
            requirementPlayers.toLong(),
            "&bPlayers Online: ${TextUtil.formatNum(eligiblePlayers)} / ${TextUtil.formatNum(requirementPlayers)}"
        )
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
        eventEndTask?.cancel()
        eventEndTask = null
        bar?.setTitle(TextUtil.colorize(getEventTitle(true)))
        bar?.color = BarColor.PINK
        startActiveBossBarCountdown()
        saveState()

        val timeText = if (durationSeconds < 60) {
            "$durationSeconds second${if (durationSeconds == 1) "" else "s"}"
        } else {
            val minutes = (durationSeconds / 60).coerceAtLeast(1)
            "$minutes minute${if (minutes == 1) "" else "s"}"
        }

        getEventEligiblePlayers().forEach { it.playSound(it.location, "block.note_block.pling", 1f, 0.9f) }
        playRarityActivationSound()
        if (type == "RareOres") {
            if (!RareOresEventManager.startFromEventManager()) {
                Bukkit.broadcast(TextUtil.toComponent("&cRare Ores Event could not start."))
                resetEvent()
            }
            return
        }
        scheduleEventEnd(durationSeconds * 20L)
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
        eventEndTask?.cancel()
        eventEndTask = null
        activeEventEndsAtMillis = 0L
        bar?.progress = 0.0
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
        initEvent()
        checkEventConditions()
        saveState()
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
            else ->  "$rarityPrefix &bRare Ores Event (${weightMultiplier}x mine richness)"
        }
    }

    private fun getRarityPrefix(): String = when (rarity) {
        "normal" -> "&b&lNORMAL"
        "rare" -> "&e&lRARE"
        "mythic" -> "&0&l&kl&5&lMYTHIC&0&l&kl"
        "god" -> "&e&l&kl&f&lGOD&e&l&kl"
        else -> "&b&l✦ &d&lSECRET &b&l✦"
    }

    private fun getEventRarity() {
        val roll = Random.nextDouble(100.0)
        rarity = "normal"
        if (roll >= 75.0) rarity = "rare"
        if (roll >= 95.0) rarity = "mythic"
        if (roll >= 99.0) rarity = "god"
        if (roll >= 99.9) rarity = "secret"
    }

    private fun playRarityActivationSound() {
        val sound = when (rarity) {
            "mythic" -> "entity.ender_dragon.ambient"
            "god" -> "item.trident.thunder"
            "secret" -> "block.beacon.activate"
            else -> return
        }
        getEventEligiblePlayers().forEach { it.playSound(it.location, sound, 1f, 1f) }
    }

    private fun getEligibleOnlinePlayerCount(): Int =
        Bukkit.getOnlinePlayers().count { !TutorialManager.isTutorialMode(it) }

    private fun getEventEligiblePlayers(): List<Player> =
        Bukkit.getOnlinePlayers().filter { !TutorialManager.isTutorialMode(it) }

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
            "god" -> {
                multiplier = 3.0
                weightMultiplier = 5.0
            }
            else -> {
                multiplier = 4.0
                weightMultiplier = 6.5
            }
        }
    }

    private fun getEventRequirement(): Long {
        return when (rarity) {
            "normal" -> Random.nextInt(25, 50) * 10L * max(MineManager.getActivePlayersInMine().size, 1)
            "rare" -> Random.nextInt(50, 75) * 10L * MineManager.getActivePlayersInMine().size
            "mythic" -> Random.nextInt(75, 100) * 10L * MineManager.getActivePlayersInMine().size
            "god" -> Random.nextInt(100, 250) * 10L * MineManager.getActivePlayersInMine().size
            else -> Random.nextInt(250, 400) * 10L * MineManager.getActivePlayersInMine().size
        }
    }

    private fun getEventType(): String {
        return when (Random.nextInt(1, 3)) {
            1 -> "GoldRush"
            else -> "RareOres"
        }
    }

    private fun getEventDuration(): Int = when (type) {
        "RareOres" -> 40
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
        saveState()
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
            "secret" -> "secret"
            else -> return ""
        }

        rarity = forcedRarity
        getEventMultiplier()
        requirementBlocks = getEventRequirement()
        requirementPlayers = 70
        bar?.progress = 0.0
        bar?.color = BarColor.GREEN
        bar?.setTitle(TextUtil.colorize(getEventTitle(false)))
        saveState()
        return forcedRarity
    }

    fun cancelCurrentEvent() {
        activeBarTask?.cancel()
        activeBarTask = null
        eventEndTask?.cancel()
        eventEndTask = null
        activeEventEndsAtMillis = 0L
        blocksMinedGlobally = 0
        isActive = false
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
        bar?.progress = 0.0
        saveState()
    }

    private fun restoreSavedActiveEvent(): Boolean {
        if (!eventStateConfig.getBoolean("active", false)) return false

        val savedType = eventStateConfig.getString("type") ?: return false
        val savedRarity = eventStateConfig.getString("rarity") ?: return false
        val remainingMillis = eventStateConfig.getLong("remainingMillis", 0L).coerceAtLeast(0L)
        if (remainingMillis <= 0L) {
            clearSavedState()
            return false
        }

        type = savedType
        rarity = savedRarity
        getEventMultiplier()
        durationSeconds = ((remainingMillis + 999L) / 1000L).toInt().coerceAtLeast(1)
        isActive = true
        isBlocksMinedEvent = false
        isPlayerCountEvent = false
        eventEndTask?.cancel()
        eventEndTask = null
        bar?.color = BarColor.PINK
        bar?.setTitle(TextUtil.colorize(getEventTitle(true)))
        startActiveBossBarCountdown()

        if (type == "RareOres") {
            val restored = RareOresEventManager.restoreActiveEvent(
                remainingTicks = ((remainingMillis + 49L) / 50L).coerceAtLeast(1L),
                weightMultiplier = weightMultiplier
            )
            if (!restored) {
                resetEvent()
                return false
            }
        } else {
            scheduleEventEnd(((remainingMillis + 49L) / 50L).coerceAtLeast(1L))
        }
        return true
    }

    private fun scheduleEventEnd(delayTicks: Long) {
        eventEndTask?.cancel()
        eventEndTask = Bukkit.getScheduler().runTaskLater(
            TestPlugin.instance,
            Runnable { resetEvent() },
            delayTicks.coerceAtLeast(1L)
        )
    }

    private fun reevaluateWaitingTrigger() {
        if (isActive) return
        if (getEligibleOnlinePlayerCount() >= requirementPlayers) {
            if (!isPlayerCountEvent) {
                startPlayerCountEvent()
            } else {
                updatePlayerCountEvent()
            }
            return
        }

        if (!isBlocksMinedEvent) {
            startBlocksMinedEvent()
        } else {
            updateBlocksMinedEvent()
        }
    }

    private fun saveState() {
        if (!::eventStateFile.isInitialized) return

        if (!isActive || activeEventEndsAtMillis <= 0L) {
            clearSavedState()
            return
        }

        eventStateConfig.set("active", true)
        eventStateConfig.set("type", type)
        eventStateConfig.set("rarity", rarity)
        eventStateConfig.set("remainingMillis", getActiveEventTimeRemainingMillis())
        eventStateConfig.save(eventStateFile)
    }

    private fun clearSavedState() {
        if (!::eventStateFile.isInitialized) return
        eventStateConfig.set("active", false)
        eventStateConfig.set("type", null)
        eventStateConfig.set("rarity", null)
        eventStateConfig.set("remainingMillis", 0L)
        eventStateConfig.save(eventStateFile)
    }
}
