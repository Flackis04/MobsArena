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

        val headerScale = 1.8
        val listScale = 1.0

        // Titles
        spawnTextDisplay(offset(baseLoc, 0.0, 2.0, 0.0), 5.0, Color.fromARGB(0, 0, 0, 0), "&7Wall of Fame")
        spawnTextDisplay(offset(baseLoc, 0.0, 1.35, 0.0), 2.0, Color.fromARGB(0, 0, 0, 0), "<#fc1919>⏱ ʀᴇsᴇᴛs ᴇᴠᴇʀʏ 1 ᴍɪɴ")

        // Headers
        val headers = listOf(
            "&9⛏ ʙʟᴏᴄᴋs ᴍɪɴᴇᴅ",
            "<#FFD700>🪙 ᴄᴏɪɴs",
            "&d⭐ ʀᴀɴᴋ",
            "&a✦ ʟᴇᴠᴇʟ",
            "&3🕒 ᴘʟᴀʏᴛɪᴍᴇ"
        )

        val columnStep = 3.5
        val startOffset = -((headers.size - 1) * columnStep) / 2.0
        val yOffset = 0.0

        headers.forEachIndexed { index, text ->
            val loc = offset(baseLoc, startOffset + index * columnStep, yOffset, 0.0)
            spawnTextDisplay(loc, headerScale, Color.fromARGB(0, 0, 0, 0), text)
        }

        // List top players
        listTopPlayers("playtime", offset(baseLoc, startOffset + 4 * columnStep, yOffset, 0.0), listScale)
        listTopPlayers("blocksMined", offset(baseLoc, startOffset + 0 * columnStep, yOffset, 0.0), listScale)
        listTopPlayers("rank", offset(baseLoc, startOffset + 2 * columnStep, yOffset, 0.0), listScale)
        listTopPlayers("balance", offset(baseLoc, startOffset + 1 * columnStep, yOffset, 0.0), listScale)
        listTopPlayers("level", offset(baseLoc, startOffset + 3 * columnStep, yOffset, 0.0), listScale)

    }

    private fun listTopPlayers(stat: String, loc: Location, scale: Double) {
        val sorted = if (stat == "rank") {
            DataStore.all().sortedWith(
                compareByDescending<Pair<java.util.UUID, PlayerData>> { it.second.rebirth }
                    .thenByDescending { it.second.rank }
            )
        } else {
            DataStore.all().sortedByDescending { (_, data) ->
                when (stat) {
                    "kills" -> data.kills
                    "deaths" -> data.deaths
                    "blocksMined" -> data.blocksMined
                    "balance" -> data.balance
                    "level" -> data.level.toLong()
                    "playtime" -> data.playtimeSeconds
                    else -> 0
                }
            }
        }

        var rank = 0
        for ((uuid, data) in sorted) {
            if (rank >= 10) break

            val name = Bukkit.getOfflinePlayer(uuid).name ?: continue
            if (name == "<none>" || name == "Player" || dupers.contains(name)) continue

            rank++
            loc.subtract(0.0, 0.4, 0.0)

            val value = when (stat) {
                "playtime" -> TextUtil.formatPlaytime(data.playtimeSeconds)
                "rank" -> formatDisplayedRank(data)
                "level" -> data.level.toString()
                else -> TextUtil.formatNum(
                    when (stat) {
                        "kills" -> data.kills
                        "deaths" -> data.deaths
                        "blocksMined" -> data.blocksMined
                        "balance" -> data.balance
                        else -> 0
                    }
                )
            }

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
                "$rankPrefix &7$name - &b$value"
            )
        }
    }

    private fun formatDisplayedRank(data: PlayerData): String {
        if (data.rebirth <= 0) return data.rank.toString()
        val rebirthLetter = ('A'.code + ((data.rebirth - 1) % 26)).toChar()
        return "$rebirthLetter${data.rank}"
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
