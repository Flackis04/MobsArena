package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Statistic
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.io.File

object StatsManager {
    data class LeaderboardEntry(val name: String, val value: String)
    data class LeaderboardStanding(
        val entries: List<LeaderboardEntry>,
        val viewerPlace: Int?,
        val viewerValue: String?
    )

    private const val WALL_TAG = "wall_of_fame"

    private val dupers = setOf("ErmGD", "tur5685", "XGamePierce","X_Phoenix_is_Him")
    private lateinit var wallFile: File
    private lateinit var wallConfig: YamlConfiguration

    object WallOfFameManager {
        var lastLoc: Location? = null
    }

    fun refreshWallOfFameIfPlaced() {
        WallOfFameManager.lastLoc?.let { refreshWallOfFame(it) }
    }

    fun init() {
        wallFile = File(TestPlugin.instance.dataFolder, "wall_of_fame.yml")
        wallConfig = YamlConfiguration.loadConfiguration(wallFile)
        loadWallOfFameLocation()

        Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            Bukkit.getOnlinePlayers().forEach { p ->
                val data = DataStore.get(p.uniqueId)
                data.playtimeSeconds = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L
            }

            refreshWallOfFameIfPlaced()

        }, 20L * 60L, 20L * 60L)

        refreshWallOfFameIfPlaced()
    }

    fun setWallOfFameLocation(location: Location) {
        WallOfFameManager.lastLoc = location.clone()
        saveWallOfFameLocation()
    }

    fun refreshWallOfFame(baseLoc: Location) {
        clearDisplays()

        val headerScale = 2.75
        val listScale = 1.5

        // Titles
        spawnTextDisplay(offset(baseLoc, 0.0, 2.0, 0.0), 7.25, Color.fromARGB(0, 0, 0, 0), "&7Wall of Fame")
        spawnTextDisplay(offset(baseLoc, 0.0, 1.35, 0.0), 3.0, Color.fromARGB(0, 0, 0, 0), "<#fc1919>⏱ ʀᴇsᴇᴛs ᴇᴠᴇʀʏ 1 ᴍɪɴ")
        val categories = listOf(
            "&6💰 ᴄᴏɪɴs" to "balance",
            "&d♛ ʀᴀɴᴋ" to "rank"
        )
        val columnStep = 6.0
        val startOffset = -((categories.size - 1) * columnStep) / 2.0
        categories.forEachIndexed { index, (header, stat) ->
            val loc = offset(baseLoc, startOffset + index * columnStep, 0.0, 0.0)
            spawnTextDisplay(loc, headerScale, Color.fromARGB(0, 0, 0, 0), header)
            listTopPlayers(stat, loc.clone(), listScale)
        }
        spawnTextDisplay(offset(baseLoc, 0.0, -5.8, 0.0), 2.0, Color.fromARGB(0, 0, 0, 0), "&7/leaderboards for more")

    }

    private fun listTopPlayers(stat: String, loc: Location, scale: Double) {
        val standing = getPlayerLeaderboard(stat, null)
        standing.entries.forEachIndexed { index, entry ->
            loc.subtract(0.0, 0.47, 0.0)
            val rank = index + 1
            val rankPrefix = when (rank) {
                1 -> "<#FFD700>1."
                2 -> "<#C0C0C0>2."
                3 -> "<#CD7F32>3."
                else -> "&8$rank."
            }

            spawnTextDisplay(
                loc.clone(),
                scale,
                Color.fromARGB(0, 0, 0, 0),
                "$rankPrefix &7${entry.name} - &b${entry.value}"
            )
        }
    }

    private fun listTopClans(loc: Location, scale: Double) {
        val standing = getClanLeaderboard(null)
        standing.entries.forEachIndexed { index, entry ->
            loc.subtract(0.0, 0.47, 0.0)
            val rank = index + 1
            val rankPrefix = when (rank) {
                1 -> "<#FFD700>1."
                2 -> "<#C0C0C0>2."
                3 -> "<#CD7F32>3."
                else -> "&8$rank."
            }

            spawnTextDisplay(
                loc.clone(),
                scale,
                Color.fromARGB(0, 0, 0, 0),
                "$rankPrefix &7${entry.name} - &b${entry.value}"
            )
        }
    }

    fun getPlayerLeaderboard(stat: String, viewer: org.bukkit.OfflinePlayer?): LeaderboardStanding {
        val entries = getSortedPlayerEntries(stat)
        val topEntries = entries.take(10).map { LeaderboardEntry(it.name, formatPlayerStatValue(stat, it.data)) }
        val viewerEntry = viewer?.uniqueId?.let { viewerId ->
            entries.indexOfFirst { it.uuid == viewerId }.takeIf { it >= 0 }?.let { index ->
                val entry = entries[index]
                index + 1 to formatPlayerStatValue(stat, entry.data)
            }
        }
        return LeaderboardStanding(topEntries, viewerEntry?.first, viewerEntry?.second)
    }

    fun getClanLeaderboard(viewer: org.bukkit.OfflinePlayer?): LeaderboardStanding {
        val entries = ClanManager.getTopClans().mapIndexed { index, clan ->
            Triple(index + 1, clan.name, "L${ClanManager.getClanLevel(clan)}")
        }
        val topEntries = entries.take(10).map { LeaderboardEntry(it.second, it.third) }
        val viewerClanName = viewer?.uniqueId?.let { ClanManager.getClanFor(it)?.name }
        val viewerEntry = if (viewerClanName != null) {
            entries.firstOrNull { it.second.equals(viewerClanName, ignoreCase = true) }
        } else {
            null
        }
        return LeaderboardStanding(topEntries, viewerEntry?.first, viewerEntry?.third)
    }

    private data class RankedPlayerEntry(
        val uuid: java.util.UUID,
        val name: String,
        val data: PlayerData
    )

    private fun getSortedPlayerEntries(stat: String): List<RankedPlayerEntry> {
        val validEntries = DataStore.all().mapNotNull { (uuid, data) ->
            val name = Bukkit.getOfflinePlayer(uuid).name ?: return@mapNotNull null
            if (name == "<none>" || name == "Player" || dupers.contains(name)) return@mapNotNull null
            RankedPlayerEntry(uuid, name, data)
        }
        return if (stat == "rank") {
            validEntries.sortedWith(
                compareByDescending<RankedPlayerEntry> { it.data.ascension }
                    .thenByDescending { it.data.rebirth }
                    .thenByDescending { it.data.rank }
            )
        } else {
            validEntries.sortedByDescending { getNumericPlayerStatValue(stat, it.data) }
        }
    }

    private fun getNumericPlayerStatValue(stat: String, data: PlayerData): Long =
        when (stat) {
            "kills" -> data.kills
            "deaths" -> data.deaths
            "blocksMined" -> data.blocksMined
            "balance" -> data.balance
            "tokens" -> data.tokens
            "level" -> data.level.toLong()
            "playtime" -> data.playtimeSeconds
            else -> 0
        }

    private fun formatPlayerStatValue(stat: String, data: PlayerData): String =
        when (stat) {
            "playtime" -> TextUtil.formatPlaytime(data.playtimeSeconds)
            "rank" -> formatDisplayedRank(data)
            "level" -> data.level.toString()
            else -> TextUtil.formatNum(getNumericPlayerStatValue(stat, data))
        }

    private fun spawnTextDisplay(loc: Location, scale: Double, color: Color, text: String) {
        val display = loc.world?.spawn(loc, TextDisplay::class.java) ?: return

        display.addScoreboardTag(WALL_TAG)
        display.billboard = Display.Billboard.FIXED
        display.setRotation(loc.yaw, 0f)

        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            org.joml.Quaternionf(),
            Vector3f(scale.toFloat(), scale.toFloat(), scale.toFloat()),
            org.joml.Quaternionf()
        )

        display.backgroundColor = color
        display.text(TextUtil.toComponent(text))
    }

    private fun clearDisplays() {
        Bukkit.getWorlds().forEach { world ->
            world.getEntitiesByClass(TextDisplay::class.java)
                .filter { it.scoreboardTags.contains(WALL_TAG) }
                .forEach { it.remove() }
        }
    }

    private fun offset(baseLoc: Location, rightOffset: Double, yOffset: Double, forwardOffset: Double): Location {
        val yawRadians = Math.toRadians(baseLoc.yaw.toDouble())
        val forward = Vector(-kotlin.math.sin(yawRadians), 0.0, kotlin.math.cos(yawRadians))
        val right = Vector(-forward.z, 0.0, forward.x)
        val offset = right.multiply(rightOffset).add(forward.multiply(forwardOffset))

        return baseLoc.clone().add(offset.x, yOffset, offset.z).apply {
            yaw = baseLoc.yaw
            pitch = 0f
        }
    }

    private fun loadWallOfFameLocation() {
        val worldName = wallConfig.getString("wall.world") ?: return
        val world = Bukkit.getWorld(worldName) ?: return
        val x = wallConfig.getDouble("wall.x")
        val y = wallConfig.getDouble("wall.y")
        val z = wallConfig.getDouble("wall.z")
        val yaw = wallConfig.getDouble("wall.yaw").toFloat()
        val pitch = wallConfig.getDouble("wall.pitch").toFloat()

        WallOfFameManager.lastLoc = Location(world, x, y, z, yaw, pitch)
    }

    private fun saveWallOfFameLocation() {
        val location = WallOfFameManager.lastLoc ?: return

        wallConfig.set("wall.world", location.world?.name)
        wallConfig.set("wall.x", location.x)
        wallConfig.set("wall.y", location.y)
        wallConfig.set("wall.z", location.z)
        wallConfig.set("wall.yaw", location.yaw.toDouble())
        wallConfig.set("wall.pitch", location.pitch.toDouble())
        wallConfig.save(wallFile)
    }
}
