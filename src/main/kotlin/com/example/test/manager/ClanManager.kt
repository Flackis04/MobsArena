package com.example.test

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

object ClanManager {
    private const val MIN_NAME_LENGTH = 3
    private const val MAX_NAME_LENGTH = 12
    private const val BASE_MEMBER_CAP = 5
    private const val MEMBERS_PER_SIZE_LEVEL = 3
    private const val MEMBERS_PER_CLAN_LEVEL = 1
    private const val BLOCKS_PER_CLAN_POINT = 40L
    private const val FORTUNE_BONUS_PER_LEVEL = 0.05
    private const val CLAN_LEVEL_FORTUNE_BONUS = 0.01
    private const val CLAN_LEVEL_MINE_WEIGHT_BONUS = 0.02
    private val clanLevelCosts = listOf(
        100L,
        175L,
        300L,
        475L,
        700L,
        1_000L,
        1_375L,
        1_850L,
        2_450L,
        3_200L
    )

    private lateinit var file: File
    private lateinit var config: YamlConfiguration
    private val clans = linkedMapOf<String, Clan>()
    private val memberIndex = mutableMapOf<UUID, String>()
    private val pendingInvites = mutableMapOf<UUID, String>()

    private val upgradeDefinitions = listOf(
        UpgradeDefinition("size", "Clan Size", listOf(25_000L, 75_000L, 200_000L, 500_000L, 1_250_000L)),
        UpgradeDefinition("fortune", "Clan Fortune Aura", listOf(80_000L, 240_000L, 675_000L, 1_800_000L, 4_500_000L))
    )

    fun init(dataFolder: File) {
        file = File(dataFolder, "clans.yml")
        config = YamlConfiguration.loadConfiguration(file)
        load()
    }

    fun save() {
        config.set("clans", null)
        clans.forEach { (clanId, clan) ->
            val basePath = "clans.$clanId"
            config.set("$basePath.name", clan.name)
            config.set("$basePath.ownerId", clan.ownerId.toString())
            config.set("$basePath.members", clan.members.map(UUID::toString))
            config.set("$basePath.upgrades", clan.upgrades)
            config.set("$basePath.points", clan.points)
            config.set("$basePath.totalPointsEarned", clan.totalPointsEarned)
            config.set("$basePath.level", clan.level)
            config.set("$basePath.blocksContributed", clan.blocksContributed)
            config.set("$basePath.blocksTowardsNextPoint", clan.blocksTowardsNextPoint)
        }
        config.save(file)
    }

    fun createClan(player: Player, name: String): String {
        val normalizedName = name.trim()
        val clanId = normalizedName.lowercase()
        if (!normalizedName.matches(Regex("[A-Za-z0-9_]{${MIN_NAME_LENGTH},${MAX_NAME_LENGTH}}"))) {
            return "&cClan names must be $MIN_NAME_LENGTH-$MAX_NAME_LENGTH characters using letters, numbers, or underscores."
        }
        if (getClanFor(player.uniqueId) != null) return "&cYou are already in a clan."
        if (clans.containsKey(clanId)) return "&cThat clan name is already taken."

        val clan = Clan(
            id = clanId,
            name = normalizedName,
            ownerId = player.uniqueId,
            members = linkedSetOf(player.uniqueId),
            upgrades = mutableMapOf(),
            points = 0L,
            totalPointsEarned = 0L,
            level = 0,
            blocksContributed = 0L,
            blocksTowardsNextPoint = 0L
        )
        clans[clanId] = clan
        rebuildMemberIndex()
        save()
        return "&aCreated clan &f${clan.name}&a."
    }

    fun invitePlayer(sender: Player, target: Player): String {
        val clan = getClanFor(sender.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId != sender.uniqueId) return "&cOnly the clan owner can invite players."
        if (getClanFor(target.uniqueId) != null) return "&c${target.name} is already in a clan."
        if (clan.members.size >= getMemberCap(clan)) return "&cYour clan is full. Upgrade Clan Size or level the clan."

        pendingInvites[target.uniqueId] = clan.id
        target.sendMessage(TextUtil.colorize("&a${sender.name} invited you to join clan &f${clan.name}&a. Use &f/clan accept&a."))
        return "&aInvited &f${target.name} &ato clan &f${clan.name}&a."
    }

    fun acceptInvite(player: Player): String {
        if (getClanFor(player.uniqueId) != null) return "&cYou are already in a clan."
        val clanId = pendingInvites.remove(player.uniqueId) ?: return "&cYou do not have a pending clan invite."
        val clan = clans[clanId] ?: return "&cThat clan no longer exists."
        if (clan.members.size >= getMemberCap(clan)) return "&cThat clan is full."

        clan.members += player.uniqueId
        rebuildMemberIndex()
        save()
        Bukkit.getPlayer(clan.ownerId)?.sendMessage(TextUtil.colorize("&a${player.name} joined clan &f${clan.name}&a."))
        return "&aJoined clan &f${clan.name}&a."
    }

    fun leaveClan(player: Player): String {
        val clan = getClanFor(player.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId == player.uniqueId) {
            return "&cYou are the clan owner. Use &f/clan disband &cif you want to remove the clan."
        }
        clan.members.remove(player.uniqueId)
        rebuildMemberIndex()
        save()
        Bukkit.getPlayer(clan.ownerId)?.sendMessage(TextUtil.colorize("&e${player.name} left clan &f${clan.name}&e."))
        return "&eYou left clan &f${clan.name}&e."
    }

    fun kickMember(sender: Player, targetName: String): String {
        val clan = getClanFor(sender.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId != sender.uniqueId) return "&cOnly the clan owner can kick players."
        val target = clan.members
            .firstOrNull { memberId ->
                val offline = Bukkit.getOfflinePlayer(memberId)
                offline.name.equals(targetName, ignoreCase = true)
            } ?: return "&cThat player is not in your clan."
        if (target == sender.uniqueId) return "&cUse &f/clan disband &cor &f/clan leave &cfor yourself."

        clan.members.remove(target)
        pendingInvites.remove(target)
        rebuildMemberIndex()
        save()
        Bukkit.getPlayer(target)?.sendMessage(TextUtil.colorize("&cYou were kicked from clan &f${clan.name}&c."))
        return "&aRemoved &f${Bukkit.getOfflinePlayer(target).name ?: "Unknown"} &afrom clan &f${clan.name}&a."
    }

    fun promoteMember(sender: Player, targetName: String): String {
        val clan = getClanFor(sender.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId != sender.uniqueId) return "&cOnly the clan owner can promote a new owner."
        val target = clan.members.firstOrNull { memberId ->
            val offline = Bukkit.getOfflinePlayer(memberId)
            offline.name.equals(targetName, ignoreCase = true)
        } ?: return "&cThat player is not in your clan."
        if (target == sender.uniqueId) return "&cYou are already the clan owner."

        clan.ownerId = target
        save()

        val targetNameFormatted = Bukkit.getOfflinePlayer(target).name ?: "Unknown"
        Bukkit.getPlayer(target)?.sendMessage(TextUtil.colorize("&aYou are now the owner of clan &f${clan.name}&a."))
        sender.sendMessage(TextUtil.colorize("&eYou transferred clan ownership to &f$targetNameFormatted&e."))
        clan.members
            .asSequence()
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.uniqueId != sender.uniqueId && it.uniqueId != target }
            .forEach { it.sendMessage(TextUtil.colorize("&e${targetNameFormatted} is now the owner of clan &f${clan.name}&e.")) }
        return "&aPromoted &f$targetNameFormatted &ato clan owner."
    }

    fun disbandClan(player: Player): String {
        val clan = getClanFor(player.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId != player.uniqueId) return "&cOnly the clan owner can disband the clan."

        clans.remove(clan.id)
        pendingInvites.entries.removeIf { it.value == clan.id }
        rebuildMemberIndex()
        save()
        clan.members
            .asSequence()
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.uniqueId != player.uniqueId }
            .forEach { it.sendMessage(TextUtil.colorize("&cClan &f${clan.name} &cwas disbanded.")) }
        return "&cDisbanded clan &f${clan.name}&c."
    }

    fun levelUpClan(player: Player, buyMax: Boolean): String {
        val clan = getClanFor(player.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId != player.uniqueId) return "&cOnly the clan owner can level the clan."

        var purchased = 0
        var spent = 0L
        while (true) {
            val nextCost = getNextLevelCost(clan) ?: break
            if (clan.points < nextCost) break
            clan.points -= nextCost
            clan.level += 1
            purchased += 1
            spent += nextCost
            if (!buyMax) break
        }

        if (purchased == 0) {
            return if (getNextLevelCost(clan) == null) {
                "&cYour clan is already max level."
            } else {
                "&cYour clan doesn't have enough points to level up."
            }
        }

        save()
        clan.members.mapNotNull(Bukkit::getPlayer).forEach { member ->
            member.sendMessage(TextUtil.colorize("&d${clan.name} reached clan level &f${clan.level}&d!"))
        }
        return "&aLeveled clan &f${clan.name} &ato &fL${clan.level}&a for &b${TextUtil.formatNum(spent)} clan points&a."
    }

    fun upgradeClan(player: Player, upgradeKey: String, buyMax: Boolean): String {
        val clan = getClanFor(player.uniqueId) ?: return "&cYou are not in a clan."
        if (clan.ownerId != player.uniqueId) return "&cOnly the clan owner can buy clan upgrades."
        val definition = upgradeDefinitions.firstOrNull { it.key.equals(upgradeKey, ignoreCase = true) }
            ?: return "&cUnknown clan upgrade. Use &f/clan &cto open the clan menu."

        var purchased = 0
        var spent = 0L
        while (true) {
            val currentLevel = clan.upgrades[definition.key] ?: 0
            if (currentLevel >= definition.costs.size) break
            val cost = definition.costs[currentLevel]
            if (clan.points < cost) break
            clan.points -= cost
            clan.upgrades[definition.key] = currentLevel + 1
            purchased += 1
            spent += cost
            if (!buyMax) break
        }

        if (purchased == 0) return "&cYour clan doesn't have enough points or the upgrade is maxed."
        save()
        return "&aUpgraded &f${definition.displayName} &ato level &f${clan.upgrades[definition.key]}&a for &b${TextUtil.formatNum(spent)} clan points&a."
    }

    fun addPointsToClan(playerId: UUID, amount: Long): String {
        if (amount <= 0L) return "&cPoints must be positive."
        val clan = getClanFor(playerId) ?: return "&cThat player is not in a clan."
        clan.points += amount
        clan.totalPointsEarned += amount
        save()
        return "&aAdded &b${TextUtil.formatNum(amount)} clan points &ato &f${clan.name}&a."
    }

    fun recordBlocksMined(playerId: UUID, blocksMined: Int): Long {
        if (blocksMined <= 0) return 0L
        val clan = getClanFor(playerId) ?: return 0L
        clan.blocksContributed += blocksMined.toLong()
        clan.blocksTowardsNextPoint += blocksMined.toLong()

        val earnedPoints = clan.blocksTowardsNextPoint / BLOCKS_PER_CLAN_POINT
        clan.blocksTowardsNextPoint %= BLOCKS_PER_CLAN_POINT
        if (earnedPoints > 0L) {
            clan.points += earnedPoints
            clan.totalPointsEarned += earnedPoints
        }
        return earnedPoints
    }

    fun getClanFor(playerId: UUID): Clan? = memberIndex[playerId]?.let(clans::get)

    fun getPendingInviteClan(playerId: UUID): Clan? = pendingInvites[playerId]?.let(clans::get)

    fun getUpgradeLevel(clan: Clan, upgradeKey: String): Int = clan.upgrades[upgradeKey] ?: 0

    fun getUpgradeCost(clan: Clan, upgradeKey: String): Long? {
        val definition = upgradeDefinitions.firstOrNull { it.key == upgradeKey } ?: return null
        return definition.costs.getOrNull(getUpgradeLevel(clan, upgradeKey))
    }

    fun getClanLevel(clan: Clan): Int = clan.level

    fun getNextLevelCost(clan: Clan): Long? = clanLevelCosts.getOrNull(clan.level)

    fun getPointsToNextLevel(clan: Clan): Long {
        val nextCost = getNextLevelCost(clan) ?: return 0L
        return (nextCost - clan.points).coerceAtLeast(0L)
    }

    fun getLevelProgressPercent(clan: Clan): Double {
        val nextCost = getNextLevelCost(clan) ?: return 1.0
        return (clan.points.toDouble() / nextCost.toDouble()).coerceIn(0.0, 1.0)
    }

    fun getPlayerFortuneMultiplier(playerId: UUID): Double {
        val clan = getClanFor(playerId) ?: return 1.0
        return 1.0 +
            ((clan.upgrades["fortune"] ?: 0) * FORTUNE_BONUS_PER_LEVEL) +
            (clan.level * CLAN_LEVEL_FORTUNE_BONUS)
    }

    fun getMineWeightMultiplier(playerId: UUID): Double {
        val clan = getClanFor(playerId) ?: return 1.0
        return 1.0 + (clan.level * CLAN_LEVEL_MINE_WEIGHT_BONUS)
    }

    fun getLevelFortuneBonus(clan: Clan): Double = clan.level * CLAN_LEVEL_FORTUNE_BONUS

    fun getLevelMineWeightBonus(clan: Clan): Double = clan.level * CLAN_LEVEL_MINE_WEIGHT_BONUS

    fun getMemberCap(clan: Clan): Int =
        BASE_MEMBER_CAP +
            ((clan.upgrades["size"] ?: 0) * MEMBERS_PER_SIZE_LEVEL) +
            (clan.level * MEMBERS_PER_CLAN_LEVEL)

    fun getTopClans(limit: Int = 10): List<Clan> =
        clans.values
            .sortedWith(compareByDescending<Clan> { it.level }.thenByDescending { it.totalPointsEarned })
            .take(limit)

    fun buildClanInfo(playerId: UUID): List<String> {
        val clan = getClanFor(playerId) ?: return listOf("&7You are not in a clan.", "&7Use &f/clan create <name>&7 to start one.")
        val ownerName = Bukkit.getOfflinePlayer(clan.ownerId).name ?: "Unknown"
        val memberNames = clan.members.map { Bukkit.getOfflinePlayer(it).name ?: "Unknown" }.sorted()
        val nextLevelCost = getNextLevelCost(clan)
        return listOf(
            "&d&lClan: &f${clan.name}",
            "&7Owner: &f$ownerName",
            "&7Members: &f${memberNames.size}/${getMemberCap(clan)}",
            "&7Online: &f${clan.members.count { Bukkit.getPlayer(it)?.isOnline == true }}",
            "&7Clan Level: &f${clan.level}",
            "&7Clan Points: &f${TextUtil.formatNum(clan.points)}",
            "&7Lifetime Points Earned: &f${TextUtil.formatNum(clan.totalPointsEarned)}",
            if (nextLevelCost != null) {
                "&7Next Level Cost: &f${TextUtil.formatNum(nextLevelCost)} &8(${TextUtil.formatNum(getPointsToNextLevel(clan))} more)"
            } else {
                "&7Next Level Cost: &aMaxed"
            },
            "&7Points From Mining: &f1 &7per ${TextUtil.formatNum(BLOCKS_PER_CLAN_POINT)} blocks",
            "&7Clan Size: &f${clan.upgrades["size"] ?: 0}",
            "&7Fortune Aura: &f${clan.upgrades["fortune"] ?: 0}",
            "&7Level Bonus: &f+${String.format("%.0f", getLevelFortuneBonus(clan) * 100)}% fortune &7and &f+${String.format("%.0f", getLevelMineWeightBonus(clan) * 100)}% mine weight",
            "&7Members: &f${memberNames.joinToString(", ")}"
        )
    }

    fun buildHelp(): List<String> = listOf(
        "&d/clan &8- &7Open the clan menu.",
        "&d/clan create <name> &8- &7Create a clan.",
        "&d/clan invite <player> &8- &7Invite a player to your clan.",
        "&d/clan accept &8- &7Accept your pending clan invite.",
        "&d/clan leave &8- &7Leave your clan.",
        "&d/clan kick <player> &8- &7Kick a member from your clan.",
        "&d/clan promote <player> &8- &7Transfer clan ownership.",
        "&d/clan disband &8- &7Disband your clan.",
        "&d/clan levelup [max] &8- &7Spend clan points on clan levels.",
        "&d/clan upgrade <size|fortune> [max] &8- &7Spend clan points on upgrades."
    )

    private fun load() {
        clans.clear()
        val section = config.getConfigurationSection("clans") ?: run {
            rebuildMemberIndex()
            return
        }

        for (clanId in section.getKeys(false)) {
            val basePath = "clans.$clanId"
            val ownerId = runCatching { UUID.fromString(config.getString("$basePath.ownerId")) }.getOrNull() ?: continue
            val members = config.getStringList("$basePath.members")
                .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                .toCollection(linkedSetOf())
            if (members.isEmpty()) members += ownerId
            val upgrades = config.getConfigurationSection("$basePath.upgrades")
                ?.getKeys(false)
                ?.associateWith { key -> config.getInt("$basePath.upgrades.$key") }
                ?.toMutableMap()
                ?: mutableMapOf()
            val totalPointsEarned = config.getLong("$basePath.totalPointsEarned", config.getLong("$basePath.points"))
            clans[clanId] = Clan(
                id = clanId,
                name = config.getString("$basePath.name", clanId) ?: clanId,
                ownerId = ownerId,
                members = members,
                upgrades = upgrades,
                points = config.getLong("$basePath.points"),
                totalPointsEarned = totalPointsEarned,
                level = config.getInt("$basePath.level", inferLegacyLevel(totalPointsEarned)),
                blocksContributed = config.getLong("$basePath.blocksContributed"),
                blocksTowardsNextPoint = config.getLong("$basePath.blocksTowardsNextPoint")
            )
        }
        rebuildMemberIndex()
    }

    private fun inferLegacyLevel(totalPointsEarned: Long): Int {
        var remaining = totalPointsEarned
        var level = 0
        for (cost in clanLevelCosts) {
            if (remaining < cost) break
            remaining -= cost
            level += 1
        }
        return level
    }

    private fun rebuildMemberIndex() {
        memberIndex.clear()
        clans.forEach { (clanId, clan) ->
            clan.members.forEach { memberIndex[it] = clanId }
        }
    }

    data class Clan(
        val id: String,
        val name: String,
        var ownerId: UUID,
        val members: LinkedHashSet<UUID>,
        val upgrades: MutableMap<String, Int>,
        var points: Long,
        var totalPointsEarned: Long,
        var level: Int,
        var blocksContributed: Long,
        var blocksTowardsNextPoint: Long
    )

    private data class UpgradeDefinition(
        val key: String,
        val displayName: String,
        val costs: List<Long>
    )
}
