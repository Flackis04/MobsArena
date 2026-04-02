package com.example.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.*

object ScoreboardManager {
    private val boards = mutableMapOf<UUID, Scoreboard>()
    private val pendingUpdates = mutableSetOf<UUID>()
    private var flushTaskStarted = false
    private const val NAME_TEAM_PREFIX = "nt_"
    private const val UPDATE_FLUSH_INTERVAL_TICKS = 5L

    fun init() {
        if (flushTaskStarted) return
        flushTaskStarted = true
        Bukkit.getScheduler().runTaskTimer(
            TestPlugin.instance,
            Runnable { flushPendingUpdates() },
            1L,
            UPDATE_FLUSH_INTERVAL_TICKS
        )
    }

    fun updateBoard(player: Player) {
        pendingUpdates += player.uniqueId
    }

    private fun flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) return
        val playerIds = pendingUpdates.toList()
        pendingUpdates.clear()

        playerIds.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            renderBoard(player)
        }
    }

    private fun renderBoard(player: Player) {
        val data = DataStore.get(player.uniqueId)
        val blocks = TextUtil.formatNum(data.blocksMined)
        val balance = TextUtil.formatNum(data.balance)
        val tokens = TextUtil.formatNum(data.tokens)
        val playtime = TextUtil.formatPlaytime(data.playtimeSeconds)
        val multiplier = String.format("%.2f", KitManager.getEffectiveSellMultiplier(player))
        player.playerListName(TextUtil.toComponent(getTabName(player, data)))
        player.playerListOrder = getTabOrder(player, data)
        player.sendPlayerListHeaderAndFooter(getTabHeader(), getTabFooter())

        val scoreboard = boards.getOrPut(player.uniqueId) { Bukkit.getScoreboardManager().newScoreboard }
        var objective = scoreboard.getObjective("mobsarena")
        if (objective == null) {
            val title = TextUtil.toComponent("<#FF5A5A>Mobs Arena").decorate(TextDecoration.BOLD)
            objective = scoreboard.registerNewObjective("mobsarena", Criteria.DUMMY, title)
            objective.displaySlot = DisplaySlot.SIDEBAR
        } else {
            objective.displayName(
                TextUtil.toComponent("<#FF5A5A>Mobs Arena").decorate(TextDecoration.BOLD)
            );        }

        player.scoreboard = scoreboard

        val lines = listOf(
            "",
            "${ItemManager.COIN_NAME_PLURAL}: &b$balance",
            "&3Tokens: &b$tokens",
            "&2Multiplier: &b${multiplier}x",
            "&dRank: &b${data.rank}",
            "&aLevel: &b${player.level}",
            "&9Blocks Mined: &b$blocks",
            "&3Playtime: &b$playtime",
            "",
            "&7ᴍᴏʙꜱᴀʀᴇɴᴀ.ᴍɪɴᴇʜᴜᴛ.ɢɢ"
        )

        setLines(scoreboard, objective, lines)
        syncNameTeams(scoreboard)
        syncPlayerNameForViewers(player)
    }

    fun refreshTabListForAll() {
        Bukkit.getOnlinePlayers().forEach(::updateBoard)
    }

    fun debugPrimaryGroup(player: Player): String = getPrimaryGroup(player)

    fun debugTabOrder(player: Player): Int = getTabOrder(player, DataStore.get(player.uniqueId))

    fun debugNameTeamId(player: Player): String = getNameTeamId(player.uniqueId)

    fun debugRenderedNamePrefix(player: Player): String =
        buildNameDecoration(player, DataStore.get(player.uniqueId)).prefix

    fun debugRenderedNameSuffix(player: Player): String =
        buildNameDecoration(player, DataStore.get(player.uniqueId)).suffix

    fun debugViewerTeamState(viewer: Player, target: Player): String {
        val team = viewer.scoreboard.getEntryTeam(target.name) ?: return "missing"
        val expectedTeamId = getNameTeamId(target.uniqueId)
        val status = if (team.name == expectedTeamId) "matched" else "different"
        return "$status:${team.name}"
    }

    private fun setLines(scoreboard: Scoreboard, objective: Objective, lines: List<String>) {
        val entries = listOf(
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
        )

        for (i in entries.indices) {
            val entry = entries[i]
            scoreboard.getTeam("line_$i")?.unregister()
            scoreboard.resetScores(entry)
        }

        for (i in lines.indices) {
            val line = TextUtil.colorize(lines[i])
            val entry = entries[i]
            val teamName = "line_$i"
            val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
            if (!team.entries.contains(entry)) team.addEntry(entry)
            team.prefix(TextUtil.toComponent(line))
            objective.getScore(entry).score = lines.size - i
        }
    }

    private fun getTabName(player: Player, data: PlayerData): String {
        val nametag = buildNameDecoration(player, data)
        return "${nametag.prefix}${player.name}${nametag.suffix}"
    }

    private fun getTabOrder(player: Player, data: PlayerData): Int {
        val groupOrder = when (getPrimaryGroup(player).lowercase()) {
            "owner" -> 11
            "headmanager" -> 10
            "manager" -> 9
            "admin" -> 8
            "mod" -> 7
            "builder" -> 6
            "nuker" -> 5
            "helper" -> 4
            "exploit-hunter" -> 3
            "miner" -> 2
            "default" -> 1
            else -> 0
        }
        val progressionOrder = (
            (data.ascension.coerceAtLeast(0) * 10_000) +
                (data.rebirth.coerceAtLeast(0) * 100) +
                data.rank.coerceAtLeast(0)
            ).coerceAtMost(99_999)
        val nameOrder = player.name.lowercase(Locale.ROOT)
            .fold(0) { acc, char -> ((acc * 37) + char.code) % 1000 }
        return (groupOrder * 100_000_000) + (progressionOrder * 1000) + nameOrder
    }

    private fun getTabHeader(): Component {
        return TextUtil.toComponent("&c&lMobsArena\n&7Players Online: &f${Bukkit.getOnlinePlayers().size}")
    }

    private fun getTabFooter(): Component = TextUtil.toComponent("")

    private fun syncNameTeams(scoreboard: Scoreboard) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val onlineTeamNames = onlinePlayers.mapTo(mutableSetOf()) { getNameTeamId(it.uniqueId) }

        scoreboard.teams
            .filter { it.name.startsWith(NAME_TEAM_PREFIX) && it.name !in onlineTeamNames }
            .forEach(Team::unregister)

        onlinePlayers.forEach { target ->
            applyNameTeam(scoreboard, target)
        }
    }

    private fun syncPlayerNameForViewers(target: Player) {
        Bukkit.getOnlinePlayers().forEach { viewer ->
            val scoreboard = boards[viewer.uniqueId] ?: return@forEach
            applyNameTeam(scoreboard, target)
        }
    }

    private fun applyNameTeam(scoreboard: Scoreboard, target: Player) {
        val team = scoreboard.getTeam(getNameTeamId(target.uniqueId))
            ?: scoreboard.registerNewTeam(getNameTeamId(target.uniqueId))
        val data = DataStore.get(target.uniqueId)
        val nametag = buildNameDecoration(target, data)

        scoreboard.teams
            .filter { it !== team && it.hasEntry(target.name) }
            .forEach { it.removeEntry(target.name) }

        if (!team.hasEntry(target.name)) {
            team.addEntry(target.name)
        }

        team.prefix(TextUtil.toComponent(nametag.prefix))
        team.suffix(TextUtil.toComponent(nametag.suffix))
    }

    private fun getNameTeamId(uuid: UUID): String =
        NAME_TEAM_PREFIX + uuid.toString().replace("-", "").take(13)

    private fun buildNameDecoration(player: Player, data: PlayerData): NameDecoration {
        val luckPermsPrefix = getLuckPermsPrefix(player)
        val luckPermsSuffix = getLuckPermsSuffix(player)
        return NameDecoration(
            prefix = "$luckPermsPrefix${formatStyledRank(data)} &f",
            suffix = luckPermsSuffix
        )
    }

    private fun getLuckPermsPrefix(player: Player): String {
        return runCatching {
            val metaData = LuckPermsProvider.get().getPlayerAdapter(Player::class.java).getMetaData(player)
            val rawPrefix = metaData.prefix ?: return ""
            val cleanedPrefix = rawPrefix.replace(Regex("^prefix\\.\\d+\\."), "")
            if (cleanedPrefix.isBlank()) "" else "$cleanedPrefix "
        }.getOrDefault("")
    }

    private fun getLuckPermsSuffix(player: Player): String {
        return runCatching {
            val metaData = LuckPermsProvider.get().getPlayerAdapter(Player::class.java).getMetaData(player)
            val rawSuffix = metaData.suffix ?: return ""
            val cleanedSuffix = rawSuffix.replace(Regex("^suffix\\.\\d+\\."), "")
            if (cleanedSuffix.isBlank()) "" else " $cleanedSuffix"
        }.getOrDefault("")
    }

    private fun getPrimaryGroup(player: Player): String {
        return runCatching {
            LuckPermsProvider.get().getPlayerAdapter(Player::class.java).getUser(player).primaryGroup
        }.getOrDefault("default")
    }

    private data class NameDecoration(
        val prefix: String,
        val suffix: String
    )
}
