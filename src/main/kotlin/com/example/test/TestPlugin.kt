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
        ClanManager.init(dataFolder)
        LevelManager.init()
        TierManager.init()
        ItemManager.init()
        RareOresEventManager.init()
        BossbarManager.init(dataFolder)
        DiscordLinkReminderManager.init()
        ActivityTracker.init()
        ScoreboardManager.init()
        MineManager.init()
        StatsManager.init()
        DataIntegrityManager.init()
        CombatManager.init()
        AutoMinerManager.init()
        LightningRodManager.init()
        DangerZoneCubeManager.init()
        VindicatorManager.init()
        HeadHunterManager.init()
        SessionTimelineManager.init()

        val minerGui = OreIndexGui()
        val shopGui = BlackMarketGui()
        blackMarketGui = shopGui
        val milestonesGui = MilestonesGui()
        val battlepassGui = BattlepassGui()
        val rankUpGui = RankUpGui()
        val animationGui = AnimationGui()
        val optionsGui = OptionsGui()
        val prefsGui = PrefsGui(optionsGui, animationGui)
        val leaderboardsGui = LeaderboardsGui()
        val autoMinerGui = AutoMinerGui()
        val potionShopGui = PotionShopGui()
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
        server.pluginManager.registerEvents(milestonesGui, plugin)
        server.pluginManager.registerEvents(battlepassGui, plugin)
        server.pluginManager.registerEvents(rankUpGui, plugin)
        server.pluginManager.registerEvents(animationGui, plugin)
        server.pluginManager.registerEvents(optionsGui, plugin)
        server.pluginManager.registerEvents(prefsGui, plugin)
        server.pluginManager.registerEvents(leaderboardsGui, plugin)
        server.pluginManager.registerEvents(autoMinerGui, plugin)
        server.pluginManager.registerEvents(potionShopGui, plugin)
        server.pluginManager.registerEvents(storeGui, plugin)
        server.pluginManager.registerEvents(StorageGuiListener(), plugin)
        server.pluginManager.registerEvents(DynamiteListener(), plugin)
        server.pluginManager.registerEvents(ScrollManager, plugin)
        server.pluginManager.registerEvents(SessionTimelineManager, plugin)
        server.pluginManager.registerEvents(LightningRodManager, plugin)
        server.pluginManager.registerEvents(VindicatorManager, plugin)

        // Commands
        getCommand("baltop")?.setExecutor(BaltopCommand())
        getCommand("stats")?.setExecutor(StatsCommand())
        getCommand("setdata")?.setExecutor(SetDataCommand())
        getCommand("setmastery")?.setExecutor(SetMasteryCommand())
        getCommand("maxstats")?.setExecutor(MaxStatsCommand())
        getCommand("resetvaluablemasteries")?.setExecutor(ResetValuableMasteriesCommand())
        getCommand("help")?.setExecutor(MobsHelpCommand())
        getCommand("spawn")?.setExecutor(SpawnCommand())
        getCommand("mine")?.setExecutor(MineCommand())
        getCommand("clan")?.setExecutor(ClanCommand())
        getCommand("giveclanpoints")?.setExecutor(GiveClanPointsCommand())
        getCommand("givelightningrod")?.setExecutor(GiveLightningRodCommand())
        getCommand("kit")?.setExecutor(KitCommand())
        getCommand("headhunter")?.setExecutor(HeadHunterCommand())
        getCommand("miner")?.setExecutor(MinerCommand(minerGui))
        getCommand("oreindex")?.setExecutor(MinerCommand(minerGui))
        getCommand("autominer")?.setExecutor(AutoMinerCommand(autoMinerGui))
        getCommand("potionshop")?.setExecutor(PotionShopCommand(potionShopGui))
        getCommand("blackmarket")?.setExecutor(ShopCommand(shopGui))
        getCommand("pay")?.setExecutor(PayCommand())
        getCommand("upgrades")?.setExecutor(PermUpgradesCommand()) // Opens openPermUpgradeGui(player)
        getCommand("prefs")?.setExecutor(PrefsCommand(prefsGui))
        getCommand("leaderboards")?.setExecutor(LeaderboardsCommand(leaderboardsGui))
        getCommand("milestones")?.setExecutor(MilestonesCommand(milestonesGui))
        getCommand("battlepass")?.setExecutor(BattlepassCommand(battlepassGui))
        getCommand("sessiontimeline")?.setExecutor(SessionTimelineManager.SessionTimelineCommand())
        getCommand("newplayer")?.setExecutor(NewPlayerCommand())
        getCommand("reset")?.setExecutor(ResetCommand())
        getCommand("givecoins")?.setExecutor(GiveCoinsCommand())
        getCommand("givedynamite")?.setExecutor(GiveDynamiteCommand())
        getCommand("rankup")?.setExecutor(RankUpCommand(rankUpGui))
        getCommand("store")?.setExecutor(StoreCommand(storeGui))
        getCommand("ascend")?.setExecutor(AscendCommand(rankUpGui))
        getCommand("togglefly")?.setExecutor(FlyCommand())
        getCommand("rebirth")?.setExecutor(RebirthCommand(rankUpGui))
        getCommand("linkdiscord")?.setExecutor(DiscordLinkCommand())
        getCommand("unlinkdiscord")?.setExecutor(UnlinkDiscordCommand())
        getCommand("givescroll")?.setExecutor(GiveScrollCommand())
        getCommand("keyall")?.setExecutor(KeyAllCommand())


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
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            DataStore.save()
            ClanManager.save()
        }, 1200L, 1200L)

        // Broadcast the store reminder every 2 minutes.
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                Bukkit.broadcast(
                    TextUtil.toComponent("&dWant to support the server and gain cool features at the same time? &a/store")
                )
            },
            1800L,
            2400L
        )
    }

    override fun onDisable() {
        AnimationManager.clearAll()
        BossbarManager.shutdown()
        DangerZoneCubeManager.shutdown()
        LightningRodManager.shutdown()
        VindicatorManager.shutdown()
        RareOresEventManager.shutdown()
        DataStore.save()
        ClanManager.save()
    }

    companion object {
        lateinit var instance: TestPlugin
            private set
    }
}
