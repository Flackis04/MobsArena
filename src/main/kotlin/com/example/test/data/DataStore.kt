package com.example.test

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*

object DataStore {
    private val manuallyPersistedFields = setOf(
        "rank",
        "rebirth",
        "newPlayer",
        "hasLinkedDiscord",
        "discordMultiplierBonus",
        "animationExtraBlockDelayTicks",
        "valuableCollected",
        "masteryActivations",
        "masteryLevels",
        "storageContents",
        "autoMinerStorageContents",
        "trustedMinePlayers",
        "hasClaimedBattlepass",
        "hasClaimedPlaytimeRewards",
        "battlepassClaimedRewards",
        "battlepassClaimedQuests",
        "killStreak",
        "victims",
        "oreBoostActive",
        "excavatorActive"
    )
    private val data = mutableMapOf<UUID, PlayerData>()
    private lateinit var file: File
    private lateinit var config: YamlConfiguration

    fun init(dataFolder: File) {
        file = File(dataFolder, "data.yml")
        if (!file.exists()) file.parentFile.mkdirs()
        config = YamlConfiguration.loadConfiguration(file)
        load()
    }

    fun get(uuid: UUID): PlayerData = data.getOrPut(uuid) { PlayerData() }

    fun all(): Collection<Pair<UUID, PlayerData>> = data.map { it.key to it.value }

    fun save() {
        for ((uuid, d) in data) {
            val key = uuid.toString()
            saveSimpleFields(key, d)
            config.set("$key.rank", d.rank)
            config.set("$key.rebirth", d.rebirth)
            config.set("$key.newPlayer", d.newPlayer)
            config.set("$key.hasLinkedDiscord", d.hasLinkedDiscord)
            config.set("$key.discordMultiplierBonus", d.discordMultiplierBonus)
            config.set("$key.animationExtraBlockDelayTicks", d.animationExtraBlockDelayTicks)
            config.set("$key.valuableCollected", d.valuableCollected)
            config.set("$key.masteryActivations", d.masteryActivations)
            config.set("$key.masteryLevels", d.masteryLevels)
            config.set(
                "$key.storageContents",
                d.storageContents.mapNotNull { item ->
                    if (item.type == org.bukkit.Material.AIR || item.amount <= 0) {
                        null
                    } else {
                        mapOf(
                            "type" to item.type.name,
                            "amount" to item.amount
                        )
                    }
                }
            )
            config.set(
                "$key.autoMinerStorageContents",
                d.autoMinerStorageContents.mapNotNull { item ->
                    if (item.type == org.bukkit.Material.AIR || item.amount <= 0) {
                        null
                    } else {
                        mapOf(
                            "type" to item.type.name,
                            "amount" to item.amount
                        )
                    }
                }
            )
            config.set("$key.trustedMinePlayers", d.trustedMinePlayers)

            config.set("$key.hasClaimedBattlepass", d.hasClaimedBattlepass.toList())
            config.set("$key.hasClaimedPlaytimeRewards", d.hasClaimedPlaytimeRewards.toList())
            config.set("$key.battlepassClaimedRewards", d.battlepassClaimedRewards.toList())
            config.set("$key.battlepassClaimedQuests", d.battlepassClaimedQuests.toList())
            config.set("$key.animationsEnabled", d.animationsEnabled)
            config.set("$key.animationLinearMode", d.animationLinearMode)
            config.set("$key.animationExtraBlockDelayTicks", d.animationExtraBlockDelayTicks)
            config.set("$key.animationDurationTicks", d.animationDurationTicks)
        }
        config.save(file)
    }

    private fun load() {
        for (key in config.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val d = PlayerData()
            loadSimpleFields(key, d)
            d.rank = config.getInt("$key.rank", config.getInt("$key.kitLevel", d.rank))
            d.rebirth = config.getInt("$key.rebirth", config.getInt("$key.prestige", d.rebirth))
            d.flightUnlocked = config.getBoolean("$key.flightUnlocked", d.flightUnlocked)
            d.hasLinkedDiscord = config.getBoolean("$key.hasLinkedDiscord", d.flightUnlocked)
            d.discordMultiplierBonus = config.getDouble(
                "$key.discordMultiplierBonus",
                if (d.hasLinkedDiscord) 0.25 else d.discordMultiplierBonus
            )
            d.newPlayer = parseNullableBoolean(config.getString("$key.newPlayer"))
            d.animationExtraBlockDelayTicks = config.getLong(
                "$key.animationExtraBlockDelayTicks",
                config.getLong("$key.animationStartDelayTicks", d.animationExtraBlockDelayTicks)
            )
            d.valuableCollected = loadValuableCollected(key)
            d.masteryActivations = loadLongMap(key, "masteryActivations")
            d.masteryLevels = loadIntMap(key, "masteryLevels")
            d.upgradeScrollBonuses = loadDoubleMap(key, "upgradeScrollBonuses")
            LevelManager.migrateLegacyScrollBonuses(d)
            d.storageContents = loadStorageContents(key)
            d.autoMinerStorageContents = loadStorageContents(key, "autoMinerStorageContents")
            d.trustedMinePlayers = config.getStringList("$key.trustedMinePlayers").toMutableList()
            d.hasClaimedBattlepass = config.getIntegerList("$key.hasClaimedBattlepass").toMutableSet()
            d.hasClaimedPlaytimeRewards = config.getIntegerList("$key.hasClaimedPlaytimeRewards").toMutableSet()
            d.battlepassClaimedRewards = config.getIntegerList("$key.battlepassClaimedRewards").toMutableSet()
            d.battlepassClaimedQuests = config.getStringList("$key.battlepassClaimedQuests").toMutableSet()

            data[uuid] = d
        }
    }

    private fun saveSimpleFields(key: String, data: PlayerData) {
        for (field in persistedSimpleFields()) {
            config.set("$key.${field.name}", field.read(data))
        }
    }

    private fun loadSimpleFields(key: String, data: PlayerData) {
        for (field in persistedSimpleFields()) {
            val path = "$key.${field.name}"
            if (!config.contains(path)) continue
            field.write(data, config.get(path))
        }
    }

    private fun persistedSimpleFields(): List<Field> =
        PlayerData::class.java.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                !field.isSynthetic &&
                field.name !in manuallyPersistedFields &&
                field.isSupportedPersistentType()
        }
    
    private fun loadStorageContents(key: String, path: String = "storageContents"): MutableList<ItemStack> {
        val raw = config.getList("$key.$path") ?: return mutableListOf()
        val parsed = mutableListOf<ItemStack>()

        for (entry in raw) {
            when (entry) {
                is ItemStack -> parsed += entry.clone()
                is Map<*, *> -> {
                    val typeName = entry["type"]?.toString() ?: continue
                    val material = runCatching { org.bukkit.Material.valueOf(typeName) }.getOrNull() ?: continue
                    val amount = (entry["amount"] as? Number)?.toInt() ?: continue
                    if (amount <= 0) continue
                    parsed += ItemStack(material, amount)
                }
            }
        }

        return parsed
    }

    private fun loadValuableCollected(key: String): MutableMap<String, Long> {
        val section = config.getConfigurationSection("$key.valuableCollected") ?: return mutableMapOf()
        val totals = mutableMapOf<String, Long>()

        for (materialName in section.getKeys(false)) {
            totals[materialName] = section.getLong(materialName)
        }

        return totals
    }

    private fun loadLongMap(key: String, path: String): MutableMap<String, Long> {
        val section = config.getConfigurationSection("$key.$path") ?: return mutableMapOf()
        val values = mutableMapOf<String, Long>()

        for (entryKey in section.getKeys(false)) {
            values[entryKey] = section.getLong(entryKey)
        }

        return values
    }

    private fun loadIntMap(key: String, path: String): MutableMap<String, Int> {
        val section = config.getConfigurationSection("$key.$path") ?: return mutableMapOf()
        val values = mutableMapOf<String, Int>()

        for (entryKey in section.getKeys(false)) {
            values[entryKey] = section.getInt(entryKey)
        }

        return values
    }

    private fun loadDoubleMap(key: String, path: String): MutableMap<String, Double> {
        val section = config.getConfigurationSection("$key.$path") ?: return mutableMapOf()
        val values = mutableMapOf<String, Double>()

        for (entryKey in section.getKeys(false)) {
            values[entryKey] = section.getDouble(entryKey)
        }

        return values
    }
}

private fun Field.isSupportedPersistentType(): Boolean {
    val type = type
    return type == Int::class.javaPrimitiveType ||
        type == Long::class.javaPrimitiveType ||
        type == Double::class.javaPrimitiveType ||
        type == Float::class.javaPrimitiveType ||
        type == Boolean::class.javaPrimitiveType ||
        type == String::class.java
}

private fun Field.read(instance: Any): Any? {
    isAccessible = true
    return get(instance)
}

private fun Field.write(instance: Any, value: Any?) {
    isAccessible = true
    when (type) {
        Int::class.javaPrimitiveType -> setInt(instance, (value as Number).toInt())
        Long::class.javaPrimitiveType -> setLong(instance, (value as Number).toLong())
        Double::class.javaPrimitiveType -> setDouble(instance, (value as Number).toDouble())
        Float::class.javaPrimitiveType -> setFloat(instance, (value as Number).toFloat())
        Boolean::class.javaPrimitiveType -> setBoolean(instance, value as Boolean)
        String::class.java -> set(instance, value?.toString())
    }
}

private fun parseNullableBoolean(value: String?): Boolean? =
    when (value?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

class PlayerData {
    var multiplier = 1.0

    var excavatorEfficiencyLevel = 1
    var excavatorEfficiencyMaxLevel = LevelManager.excavatorEfficiencyMaxLevel
    var rank = 1
    var multiBreakLevel = 1
    var multiBreakMaxLevel = LevelManager.multiBreakMaxLevel
    var fortuneLevel = 1
    var fortuneMaxLevel = LevelManager.fortuneMaxLevel
    var oreBoostLevel = 1
    var oreBoostMaxLevel = LevelManager.oreBoostMaxLevel
    var excavatorLevel = 1
    var excavatorMaxLevel = LevelManager.excavatorMaxLevel
    var lightningLevel = 1
    var lightningMaxLevel = LevelManager.lightningMaxLevel
    var virtualJackhammerLevel = 1
    var virtualJackhammerMaxLevel = LevelManager.virtualJackhammerMaxLevel
    var xpGainLevel = 1
    var xpGainMaxLevel = LevelManager.xpGainMaxLevel
    var oreFrequencyLevel = 1
    var oreFrequencyMaxLevel = LevelManager.oreFrequencyMaxLevel
    var scrollFinderLevel = 1
    var scrollFinderMaxLevel = LevelManager.scrollFinderMaxLevel
    var backpackLevel = 1
    var backpackMaxLevel = LevelManager.backpackMaxLevel
    var sellMultiplierLevel = 1
    var sellMultiplierMaxLevel = LevelManager.sellMultiplierMaxLevel
    var tokenFinderLevel = 1
    var tokenFinderMaxLevel = LevelManager.tokenFinderMaxLevel
    var keyFinderLevel = 1
    var keyFinderMaxLevel = LevelManager.keyFinderMaxLevel
    var jackpotLevel = 1
    var jackpotMaxLevel = LevelManager.jackpotMaxLevel
    var comboLevel = 1
    var comboMaxLevel = LevelManager.comboMaxLevel
    var procPowerLevel = 1
    var procPowerMaxLevel = LevelManager.procPowerMaxLevel
    var autoMinerFortuneLevel = 1
    var autoMinerFortuneMaxLevel = LevelManager.autoMinerFortuneMaxLevel
    var autoMinerEfficiencyLevel = 1
    var autoMinerEfficiencyMaxLevel = LevelManager.autoMinerEfficiencyMaxLevel
    var autoMinerEnergyDrinkLevel = 1
    var autoMinerEnergyDrinkMaxLevel = LevelManager.autoMinerEnergyDrinkMaxLevel
    var autoMinerBackpackLevel = 1
    var autoMinerBackpackMaxLevel = LevelManager.autoMinerBackpackMaxLevel
    var autoMinerLuckLevel = 1
    var autoMinerLuckMaxLevel = LevelManager.autoMinerLuckMaxLevel
    var autoMinerPayoutLevel = 1
    var autoMinerPayoutMaxLevel = LevelManager.autoMinerPayoutMaxLevel
    var swordLevel = 1
    var swordMaxLevel = LevelManager.swordMaxLevel

    var balance = 0L
    var tokens = 0L
    var kills = 0L
    var deaths = 0L
    var level = 0
    var blocksMined = 0L
    var playtimeSeconds = 0L
    var playtimeSecondsAtLastRebirth = 0L
    var paymentUnlockPlaytimeSeconds = 0L
    var mineCenterX = Int.MIN_VALUE
    var mineCenterZ = Int.MIN_VALUE
    var multiBreakScrollBonus = 0.0

    var rebirth = 0
    var ascension = 0
    var experienceBuffer = 0.0
    var valuableCollected: MutableMap<String, Long> = mutableMapOf()
    var masteryActivations: MutableMap<String, Long> = mutableMapOf()
    var masteryLevels: MutableMap<String, Int> = mutableMapOf()
    var upgradeScrollBonuses: MutableMap<String, Double> = mutableMapOf()
    var flightUnlocked = false
    var hasLinkedDiscord = false
    var hasDonorRank = false
    var discordMultiplierBonus = 0.0
    var donorRankMultiplier = 0.0
    var mineWeightBonusMultiplier = 1.0
    var extraExperienceMultiplier = 1.0
    var minePotionMultiplier = 1.0
    var minePotionExpiresAt = 0L
    var xpPotionMultiplier = 1.0
    var xpPotionExpiresAt = 0L
    var procPotionMultiplier = 1.0
    var procPotionExpiresAt = 0L
    var fortunePotionMultiplier = 1.0
    var fortunePotionExpiresAt = 0L
    var hasEnabledPvp = false

    var newPlayer: Boolean? = null
    var hasTouched = false
    var hasBroken = false
    var hasClosed = false
    var hasSold = false
    var valuableBlocksBroken = 0
    var hasSeenBackpackSellHint = false
    var hasSeenUpgradeHint = false
    var tutorialActive = false
    var tutorialPendingUpgradeClose = false
    var flight = false
    var hasReceivedJoinLoadout = false
    var hasSeenMineHelp = false
    var storageContents: MutableList<ItemStack> = mutableListOf()
    var autoMinerStorageContents: MutableList<ItemStack> = mutableListOf()
    var trustedMinePlayers: MutableList<String> = mutableListOf()
    var lightningRodPlaced = false
    var lightningRodCount = 0
    var animationsEnabled = true
    var animationLinearMode = true
    var animationExtraBlockDelayTicks = 0L
    var animationDurationTicks = 8

    var hasClaimedBattlepass: MutableSet<Int> = mutableSetOf()
    var hasClaimedPlaytimeRewards: MutableSet<Int> = mutableSetOf()
    var battlepassClaimedRewards: MutableSet<Int> = mutableSetOf()
    var battlepassClaimedQuests: MutableSet<String> = mutableSetOf()
    var battlepassPoints = 0L
    var battlepassSwordKills = 0
    var battlepassAxeKills = 0
    var battlepassMaceKills = 0
    var battlepassTotalKills = 0

    var killStreak = 0
    var victims: MutableList<String> = mutableListOf()

    var oreBoostActive = false
    var excavatorActive = false

    var multiBreakEnabled = true
    var oreBoostEnabled = true
    var fortuneEnabled = true
    var excavatorEnabled = true
    var lightningEnabled = true
    var virtualJackhammerEnabled = true
    var excavatorEfficiencyEnabled = true
    var xpGainEnabled = true
    var oreFrequencyEnabled = true
    var scrollFinderEnabled = true
    var backpackEnabled = true
    var sellMultiplierEnabled = true
    var tokenFinderEnabled = true
    var keyFinderEnabled = true
    var jackpotEnabled = true
    var comboEnabled = true
    var procPowerEnabled = true

    fun getCollectedAmount(material: org.bukkit.Material): Long = valuableCollected[material.name] ?: 0L

    fun addCollectedAmount(material: org.bukkit.Material, amount: Int) {
        if (amount <= 0) return
        valuableCollected[material.name] = getCollectedAmount(material) + amount
    }
}
