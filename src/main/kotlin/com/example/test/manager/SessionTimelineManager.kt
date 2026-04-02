package com.example.test

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object SessionTimelineManager : Listener {
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val timelineDir: File by lazy { File(TestPlugin.instance.dataFolder, "session-timelines") }
    private val newPlayersDir: File by lazy { File(timelineDir, "new-players") }
    private val returningPlayersDir: File by lazy { File(timelineDir, "returning-players") }
    private val sessions = mutableMapOf<UUID, PlayerSessionTimeline>()
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val sessionFileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())

    fun init() {
        newPlayersDir.mkdirs()
        returningPlayersDir.mkdirs()
        Bukkit.getOnlinePlayers().forEach { beginSession(it) }
    }

    fun record(player: Player, action: String) {
        val session = sessions[player.uniqueId] ?: beginSession(player)
        flushPendingMining(session)
        session.entries += "${formatNow()} | $action"
    }

    fun recordMining(player: Player, materials: Collection<Material>, location: Location) {
        if (materials.isEmpty()) return
        val session = sessions[player.uniqueId] ?: beginSession(player)
        val pending = session.pendingMining
        if (pending == null) {
            val newPending = PendingMiningAction(
                startedAt = formatNow(),
                worldName = location.world?.name ?: "unknown",
                materials = linkedMapOf()
            )
            session.pendingMining = newPending
            materials.forEach { material ->
                newPending.materials.merge(material, 1, Int::plus)
            }
            return
        }

        materials.forEach { material ->
            pending.materials.merge(material, 1, Int::plus)
        }
    }

    private fun beginSession(player: Player): PlayerSessionTimeline {
        val session = PlayerSessionTimeline(
            playerId = player.uniqueId,
            playerName = player.name,
            startedAt = Instant.now(),
            isFirstJoinSession = !player.hasPlayedBefore(),
            entries = mutableListOf()
        )
        sessions[player.uniqueId] = session
        session.entries += "${formatNow()} | Joined at ${formatLocation(player.location)}"
        return session
    }

    private fun endSession(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: beginSession(player)
        flushPendingMining(session)
        session.entries += "${formatNow()} | Logged off at ${formatLocation(player.location)}"
        saveSession(session, player.location)
    }

    private fun saveSession(session: PlayerSessionTimeline, logoffLocation: Location) {
        val playerDir = resolvePlayerTimelineDir(session.isFirstJoinSession, session.playerId)
        val nextSessionIndex = nextSessionIndex(playerDir)
        val fileName = "${nextSessionIndex}_${sessionFileFormatter.format(session.startedAt)}.yml"
        val file = File(playerDir, fileName)
        val finishedTutorial = TutorialManager.hasFinishedTutorial(session.playerId)
        val yaml = YamlConfiguration()
        yaml.set("playerId", session.playerId.toString())
        yaml.set("playerName", session.playerName)
        yaml.set("finishedTutorial", finishedTutorial)
        yaml.set("startedAt", timestampFormatter.format(session.startedAt))
        yaml.set("endedAt", formatNow())
        yaml.set("logoffSpot.world", logoffLocation.world?.name ?: "unknown")
        yaml.set("logoffSpot.x", logoffLocation.x)
        yaml.set("logoffSpot.y", logoffLocation.y)
        yaml.set("logoffSpot.z", logoffLocation.z)
        yaml.set("logoffSpot.yaw", logoffLocation.yaw)
        yaml.set("logoffSpot.pitch", logoffLocation.pitch)
        yaml.set("timeline", session.entries)
        yaml.save(file)
    }

    private fun latestSessionFile(playerId: UUID): File? {
        return listOf(newPlayersDir, returningPlayersDir)
            .mapNotNull { findPlayerTimelineDir(it, playerId) }
            .flatMap { playerDir ->
                playerDir.listFiles { file -> file.isFile && file.extension == "yml" }?.asList().orEmpty()
            }
            .maxWithOrNull(compareBy<File> { extractSessionIndex(it) }.thenBy { it.name })
    }

    private fun playerTimelineRoot(isFirstJoinSession: Boolean): File =
        if (isFirstJoinSession) newPlayersDir else returningPlayersDir

    private fun resolvePlayerTimelineDir(isFirstJoinSession: Boolean, playerId: UUID): File {
        val root = playerTimelineRoot(isFirstJoinSession)
        val existingDir = findPlayerTimelineDir(root, playerId)
        if (existingDir != null) {
            if (existingDir.name == playerId.toString()) {
                val migratedDir = File(root, "${nextPlayerDirectoryIndex(root)}_${playerId}")
                if (existingDir.renameTo(migratedDir)) {
                    return migratedDir.apply { mkdirs() }
                }
            }
            return existingDir.apply { mkdirs() }
        }

        return File(root, "${nextPlayerDirectoryIndex(root)}_${playerId}").apply { mkdirs() }
    }

    private fun findPlayerTimelineDir(root: File, playerId: UUID): File? =
        root.listFiles { file -> file.isDirectory }
            ?.firstOrNull { it.name == playerId.toString() || it.name.endsWith("_$playerId") }

    private fun nextPlayerDirectoryIndex(root: File): Int =
        root.listFiles { file -> file.isDirectory }
            ?.maxOfOrNull { extractPlayerDirectoryIndex(it) }
            ?.plus(1)
            ?: 0

    private fun extractPlayerDirectoryIndex(dir: File): Int =
        dir.name.substringBefore('_').toIntOrNull() ?: -1

    private fun nextSessionIndex(playerDir: File): Int =
        playerDir.listFiles { file -> file.isFile && file.extension == "yml" }
            ?.maxOfOrNull { extractSessionIndex(it) }
            ?.plus(1)
            ?: 0

    private fun extractSessionIndex(file: File): Int =
        file.nameWithoutExtension.substringBefore('_').toIntOrNull() ?: -1

    private fun flushPendingMining(session: PlayerSessionTimeline) {
        val pending = session.pendingMining ?: return
        session.pendingMining = null
        if (pending.materials.isEmpty()) return

        val materialSummary = pending.materials.entries
            .sortedWith(compareByDescending<Map.Entry<Material, Int>> { it.value }.thenBy { prettifyMaterial(it.key) })
            .joinToString(", ") { "${it.value}x ${prettifyMaterial(it.key)}" }
        val locationLabel = if (pending.worldName == "mine") "mine" else pending.worldName
        session.entries += "${pending.startedAt} | Broke following blocks at $locationLabel: $materialSummary"
    }

    private fun formatLocation(location: Location): String =
        "${location.world?.name ?: "unknown"} @ ${"%.1f".format(location.x)}, ${"%.1f".format(location.y)}, ${"%.1f".format(location.z)}"

    private fun formatNow(): String = timestampFormatter.format(Instant.now())

    private fun prettifyMaterial(material: Material): String {
        val valuableIndex = MineManager.valuableDrops.indexOf(material)
        if (valuableIndex != -1 && valuableIndex < MineManager.valuableNames.size) {
            return MineManager.valuableNames[valuableIndex]
        }
        return material.name
            .lowercase()
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        beginSession(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        endSession(event.player)
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val message = legacySerializer.serialize(event.message())
        Bukkit.getScheduler().runTask(TestPlugin.instance, Runnable {
            val session = sessions[event.player.uniqueId] ?: return@Runnable
            flushPendingMining(session)
            session.entries += "${formatNow()} | Chat: $message"
        })
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        record(event.player, "Command: ${event.message}")
    }

    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        if (event.player.world.name == "mine" && MineManager.containsMine(event.block.location)) return
        record(event.player, "Broke ${event.block.type} at ${formatLocation(event.block.location)}")
    }

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        record(event.player, "Placed ${event.block.type} at ${formatLocation(event.block.location)}")
    }

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        record(event.player, "Teleported to ${formatLocation(event.to)}")
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        record(event.player, "Changed world to ${event.player.world.name}")
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        record(event.entity, "Died at ${formatLocation(event.entity.location)}")
    }

    class SessionTimelineCommand : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
            val target = when {
                args.isNotEmpty() -> Bukkit.getOfflinePlayer(args[0])
                sender is Player -> sender
                else -> {
                    sender.sendMessage(TextUtil.colorize("&cUsage: /sessiontimeline <player>"))
                    return true
                }
            }

            if (args.isNotEmpty() && !sender.hasPermission("command.dev")) {
                sendPermissionMessage(sender)
                return true
            }

            val file = latestSessionFile(target.uniqueId)
            if (file == null || !file.exists()) {
                sender.sendMessage(TextUtil.colorize("&cNo saved session timeline found for &f${target.name ?: "that player"}&c."))
                return true
            }

            val yaml = YamlConfiguration.loadConfiguration(file)
            val playerName = yaml.getString("playerName") ?: target.name ?: "Unknown"
            val finishedTutorial = yaml.getBoolean("finishedTutorial", false)
            val startedAt = yaml.getString("startedAt") ?: "Unknown"
            val endedAt = yaml.getString("endedAt") ?: "Unknown"
            val world = yaml.getString("logoffSpot.world") ?: "unknown"
            val x = yaml.getDouble("logoffSpot.x")
            val y = yaml.getDouble("logoffSpot.y")
            val z = yaml.getDouble("logoffSpot.z")
            val timeline = yaml.getStringList("timeline")

            sender.sendMessage(TextUtil.colorize("&8&m----------------------------------"))
            sender.sendMessage(TextUtil.colorize("&b&lSession Timeline &7for &f$playerName"))
            sender.sendMessage(TextUtil.colorize("&7Finished Tutorial: &f${if (finishedTutorial) "Yes" else "No"}"))
            sender.sendMessage(TextUtil.colorize("&7Started: &f$startedAt"))
            sender.sendMessage(TextUtil.colorize("&7Ended: &f$endedAt"))
            sender.sendMessage(TextUtil.colorize("&7Logoff Spot: &f$world @ ${"%.1f".format(x)}, ${"%.1f".format(y)}, ${"%.1f".format(z)}"))
            sender.sendMessage(TextUtil.colorize("&8&m----------------------------------"))
            timeline.takeLast(40).forEach { sender.sendMessage(TextUtil.colorize("&7$it")) }
            if (timeline.size > 40) {
                sender.sendMessage(TextUtil.colorize("&8... showing last 40 of ${timeline.size} entries"))
            }
            return true
        }
    }

    private data class PlayerSessionTimeline(
        val playerId: UUID,
        val playerName: String,
        val startedAt: Instant,
        val isFirstJoinSession: Boolean,
        val entries: MutableList<String>,
        var pendingMining: PendingMiningAction? = null
    )

    private data class PendingMiningAction(
        val startedAt: String,
        val worldName: String,
        val materials: LinkedHashMap<Material, Int>
    )
}
