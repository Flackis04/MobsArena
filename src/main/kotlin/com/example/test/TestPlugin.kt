package com.example.test

import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.plugin.java.JavaPlugin

class TestPlugin : JavaPlugin() {
    lateinit var blackMarketGui: BlackMarketGui
        private set

    override fun onEnable() {
        instance = this
        Bukkit.createWorld(WorldCreator("mine"))

        // Initialize all managers
        DataStore.init(dataFolder)
        LevelManager.init()
        TierManager.init()
        ItemManager.init()
        BossbarManager.init()
        ActivityTracker.init()
        MineManager.init()
        StatsManager.init()
        DataIntegrityManager.init()
        CombatManager.init()
        AutoMinerManager.init()
        HeadHunterManager.init()
        RareOresEventManager.init()

        val minerGui = OreIndexGui()
        val shopGui = BlackMarketGui()
        blackMarketGui = shopGui
        val battlepassGui = BattlepassGui()
        val rankUpGui = RankUpGui()
        val animationGui = AnimationGui()
        val autoMinerGui = AutoMinerGui()
        val storeGui = StoreGui()
        shopGui.init()

        val plugin = this

        // Register event listeners
        server.pluginManager.registerEvents(PlayerListener(), plugin)
        server.pluginManager.registerEvents(CombatManager, plugin)
        server.pluginManager.registerEvents(ActivityTracker, plugin)
        server.pluginManager.registerEvents(ChatListener(), plugin)
        server.pluginManager.registerEvents(PickaxeListener(), plugin)
        server.pluginManager.registerEvents(ExperienceListener(), plugin)
        server.pluginManager.registerEvents(DropListener(), plugin)
        server.pluginManager.registerEvents(minerGui, plugin)
        server.pluginManager.registerEvents(shopGui, plugin)
        server.pluginManager.registerEvents(battlepassGui, plugin)
        server.pluginManager.registerEvents(rankUpGui, plugin)
        server.pluginManager.registerEvents(animationGui, plugin)
        server.pluginManager.registerEvents(autoMinerGui, plugin)
        server.pluginManager.registerEvents(storeGui, plugin)
        server.pluginManager.registerEvents(StorageGuiListener(), plugin)
        server.pluginManager.registerEvents(DynamiteListener(), plugin)
        server.pluginManager.registerEvents(ScrollManager, plugin)

        // Commands
        getCommand("baltop")?.setExecutor(BaltopCommand())
        getCommand("stats")?.setExecutor(StatsCommand())
        getCommand("setdata")?.setExecutor(SetDataCommand())
        getCommand("setmastery")?.setExecutor(SetMasteryCommand())
        getCommand("resetvaluablemasteries")?.setExecutor(ResetValuableMasteriesCommand())
        getCommand("help")?.setExecutor(MobsHelpCommand())
        getCommand("spawn")?.setExecutor(SpawnCommand())
        getCommand("mine")?.setExecutor(MineCommand())
        getCommand("kit")?.setExecutor(KitCommand())
        getCommand("headhunter")?.setExecutor(HeadHunterCommand())
        getCommand("miner")?.setExecutor(MinerCommand(minerGui))
        getCommand("autominer")?.setExecutor(AutoMinerCommand(autoMinerGui))
        getCommand("blackmarket")?.setExecutor(ShopCommand(shopGui))
        getCommand("upgrades")?.setExecutor(PermUpgradesCommand()) // Opens openPermUpgradeGui(player)
        getCommand("battlepass")?.setExecutor(BattlepassCommand(battlepassGui))
        getCommand("newplayer")?.setExecutor(NewPlayerCommand())
        getCommand("noobprotection")?.setExecutor(NoobProtectionCommand())
        getCommand("reset")?.setExecutor(ResetCommand())
        getCommand("givecoins")?.setExecutor(GiveCoinsCommand())
        getCommand("givetokens")?.setExecutor(GiveTokensCommand())
        getCommand("givedynamite")?.setExecutor(GiveDynamiteCommand())
        getCommand("rankup")?.setExecutor(RankUpCommand(rankUpGui))
        getCommand("store")?.setExecutor(StoreCommand(storeGui))
        getCommand("togglefly")?.setExecutor(FlyCommand())
        getCommand("animations")?.setExecutor(AnimationCommand(animationGui))
        getCommand("rebirth")?.setExecutor(RebirthCommand(rankUpGui))
        getCommand("givediscordlinkrewards")?.setExecutor(DiscordLinkRewardsCommand())
        getCommand("givescroll")?.setExecutor(GiveScrollCommand())


        getCommand("event")?.setExecutor(EventCommand())
        getCommand("startevent")?.setExecutor(StartEventCommand())
        getCommand("placeheadsonwall")?.setExecutor(HeadDisplayCommands())
        getCommand("removeheadsonwall")?.setExecutor(HeadDisplayCommands())
        getCommand("minereset")?.setExecutor(MineResetCommand())
        getCommand("onminerpurchase")?.setExecutor(OnMinerPurchaseCommand())
        getCommand("onexcavatorpurchase")?.setExecutor(OnExcavatorPurchaseCommand())
        getCommand("onnukerpurchase")?.setExecutor(OnNukerPurchaseCommand())
        getCommand("movelb")?.setExecutor(MoveLBCommand())
        getCommand("tabdebug")?.setExecutor(TabDebugCommand())

        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            MobsArenaPlaceholderExpansion(this).register()
        }

        // Auto-save every minute
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { DataStore.save() }, 1200L, 1200L)

        // Broadcast the store reminder every 2 minutes.
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                Bukkit.broadcast(
                    TextUtil.toComponent("&dWant to support the server and gain cool features at the same time? &a/store")
                )
            },
            2400L,
            2400L
        )
    }

    override fun onDisable() {
        AnimationManager.clearAll()
        BossbarManager.shutdown()
        RareOresEventManager.shutdown()
        DataStore.save()
    }

    companion object {
        lateinit var instance: TestPlugin
            private set
    }
}
