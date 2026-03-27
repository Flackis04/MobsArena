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

    private var updateTask: BukkitTask? = null
    private val lineDisplays = mutableMapOf<String, TextDisplay>()

    fun init() {
        clearAllDisplays()
        lineDisplays.clear()
        refresh()

        updateTask?.cancel()
        updateTask = Bukkit.getScheduler().runTaskTimer(TestPlugin.instance, Runnable {
            refresh()
        }, 20L, 20L)
    }

    private fun refresh() {
        val validLineIds = mutableSetOf<String>()
        for (mine in MineManager.getPlayerMines()) {
            val baseLocation = MineManager.getPlayerMineCenterLocation(mine.ownerId, 5.5) ?: continue
            val ownerName = Bukkit.getOfflinePlayer(mine.ownerId).name ?: "Unknown"

            val titleId = "${mine.ownerId}:title"
            val statusId = "${mine.ownerId}:status"
            val progressId = "${mine.ownerId}:progress"
            validLineIds += titleId
            validLineIds += statusId
            validLineIds += progressId

            val title = ensureDisplay(baseLocation.clone().add(0.0, 2.2, 0.0), titleId, 2.7)
            val status = ensureDisplay(baseLocation.clone().add(0.0, 1.2, 0.0), statusId, 2.15)
            val progress = ensureDisplay(baseLocation.clone().add(0.0, 0.2, 0.0), progressId, 2.15)

            styleDisplay(title, baseLocation.clone().add(0.0, 2.2, 0.0), 2.7)
            styleDisplay(status, baseLocation.clone().add(0.0, 1.2, 0.0), 2.15)
            styleDisplay(progress, baseLocation.clone().add(0.0, 0.2, 0.0), 2.15)

            title.text(TextUtil.toComponent("<#6EF8FF>-> &fMine Status <#6EF8FF>->"))
            status.text(TextUtil.toComponent("<#A89064>Mine Owner: <#6EF8FF>$ownerName"))
            progress.text(
                TextUtil.toComponent(
                    "<#A89064>Mined: <#6EF8FF>${MineManager.getClearedPercentText()} <#6C6458>| <#A89064>Reset In <#FFFFFF>${MineManager.getTimeUntilResetText()}"
                )
            )
        }
        removeStaleDisplays(validLineIds)
    }

    private fun ensureDisplay(location: org.bukkit.Location, lineId: String, scale: Double): TextDisplay {
        cleanupDuplicateDisplays(lineId)
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
        display.lineWidth = 1000
        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            Quaternionf(),
            Vector3f(scale.toFloat(), scale.toFloat(), scale.toFloat()),
            Quaternionf()
        )
    }

    private fun cleanupDuplicateDisplays(lineId: String) {
        val world = Bukkit.getWorld("mine") ?: return
        val cachedDisplay = lineDisplays[lineId]
        world.getEntitiesByClass(TextDisplay::class.java)
            .filter { it.scoreboardTags.contains("$HOLOGRAM_TAG:$lineId") }
            .forEach { display ->
                if (cachedDisplay != null && display.uniqueId == cachedDisplay.uniqueId) return@forEach
                if (cachedDisplay == null && lineDisplays[lineId] == null && display.isValid) {
                    lineDisplays[lineId] = display
                    return@forEach
                }
                display.remove()
            }
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
