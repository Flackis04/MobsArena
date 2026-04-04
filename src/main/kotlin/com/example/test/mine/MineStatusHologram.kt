package com.example.test

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

object MineStatusHologram {
    private const val HOLOGRAM_TAG = "mine_status_hologram"
    private const val REFRESH_INTERVAL_TICKS = 40L
    private const val BASE_HEIGHT_OFFSET = 10.5
    private const val TITLE_SCALE = 5.4
    private const val LINE_SCALE = 4.3

    private var updateTask: BukkitTask? = null
    private val lineDisplays = mutableMapOf<String, TextDisplay>()

    fun init() {
        clearAllDisplays()
        lineDisplays.clear()
        refresh()

        updateTask?.cancel()
        updateTask = Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            refresh()
        }, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS)
    }

    private fun refresh() {
        val validLineIds = mutableSetOf<String>()
        for (mine in MineManager.getPlayerMines()) {
            val baseLocation = MineManager.getPlayerMineCenterLocation(mine.ownerId, BASE_HEIGHT_OFFSET) ?: continue
            val ownerName = Bukkit.getOfflinePlayer(mine.ownerId).name ?: "Unknown"
            val ownerData = DataStore.get(mine.ownerId)
            val ownerRank = formatDisplayedRank(ownerData)
            val mineRichness = String.format("%.2f", MineManager.getMineRichnessMultiplier(mine.ownerId))
            val totalBlocksMined = TextUtil.formatNum(ownerData.blocksMined)
            val titleId = "${mine.ownerId}:title"
            val ownerId = "${mine.ownerId}:owner"
            val blocksId = "${mine.ownerId}:blocks"
            val weightId = "${mine.ownerId}:weight"
            val progressId = "${mine.ownerId}:progress"
            validLineIds += titleId
            validLineIds += ownerId
            validLineIds += blocksId
            validLineIds += weightId
            validLineIds += progressId

            val title = ensureDisplay(baseLocation.clone().add(0.0, 5.0, 0.0), titleId, TITLE_SCALE)
            val owner = ensureDisplay(baseLocation.clone().add(0.0, 3.2, 0.0), ownerId, LINE_SCALE)
            val blocks = ensureDisplay(baseLocation.clone().add(0.0, 1.6, 0.0), blocksId, LINE_SCALE)
            val weight = ensureDisplay(baseLocation.clone().add(0.0, 0.0, 0.0), weightId, LINE_SCALE)
            val progress = ensureDisplay(baseLocation.clone().add(0.0, -1.6, 0.0), progressId, LINE_SCALE)

            styleDisplay(title, baseLocation.clone().add(0.0, 5.0, 0.0), TITLE_SCALE)
            styleDisplay(owner, baseLocation.clone().add(0.0, 3.2, 0.0), LINE_SCALE)
            styleDisplay(blocks, baseLocation.clone().add(0.0, 1.6, 0.0), LINE_SCALE)
            styleDisplay(weight, baseLocation.clone().add(0.0, 0.0, 0.0), LINE_SCALE)
            styleDisplay(progress, baseLocation.clone().add(0.0, -1.6, 0.0), LINE_SCALE)

            title.text(TextUtil.toComponent("<#6EF8FF>⛏ &fMine Status <#6EF8FF>⛏"))
            owner.text(TextUtil.toComponent("<#A89064>Mine Owner: <#6EF8FF>$ownerName <#6C6458>| <#A89064>Rank: <#6EF8FF>$ownerRank"))
            blocks.text(TextUtil.toComponent("<#A89064>Total Blocks Mined: <#6EF8FF>$totalBlocksMined"))
            weight.text(TextUtil.toComponent("<#A89064>Mine Richness: <#6EF8FF>${mineRichness}x"))
            progress.text(
                TextUtil.toComponent(
                    "<#A89064>Mined: <#6EF8FF>${MineManager.getClearedPercentText(mine.ownerId)} <#6C6458>| <#A89064>Reset Threshold: <#FFFFFF>4.0%"
                )
            )
        }
        removeStaleDisplays(validLineIds)
    }

    private fun ensureDisplay(location: org.bukkit.Location, lineId: String, scale: Double): TextDisplay {
        val existing = lineDisplays[lineId]
        if (existing != null && existing.isValid) return existing

        return spawnDisplay(location, lineId, scale).also {
            lineDisplays[lineId] = it
        }
    }

    private fun spawnDisplay(location: org.bukkit.Location, lineId: String, scale: Double): TextDisplay {
        return location.world!!.spawn(location, TextDisplay::class.java).apply {
            addScoreboardTag(HOLOGRAM_TAG)
            addScoreboardTag("$HOLOGRAM_TAG:$lineId")
            styleDisplay(this, location, scale)
        }
    }

    private fun styleDisplay(display: TextDisplay, location: org.bukkit.Location, scale: Double) {
        display.teleport(location)
        display.billboard = Display.Billboard.CENTER
        display.isSeeThrough = false
        display.isShadowed = false
        display.setDefaultBackground(false)
        display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
        display.lineWidth = 2000
        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            Quaternionf(),
            Vector3f(scale.toFloat(), scale.toFloat(), scale.toFloat()),
            Quaternionf()
        )
    }

    private fun removeStaleDisplays(validLineIds: Set<String>) {
        val iterator = lineDisplays.entries.iterator()
        while (iterator.hasNext()) {
            val (lineId, display) = iterator.next()
            if (lineId in validLineIds && display.isValid) continue
            if (display.isValid) {
                display.remove()
            }
            iterator.remove()
        }
    }

    private fun clearAllDisplays() {
        val world = Bukkit.getWorld("mine") ?: return
        world.getEntitiesByClass(TextDisplay::class.java)
            .filter { it.scoreboardTags.contains(HOLOGRAM_TAG) }
            .forEach { it.remove() }
    }
}
