package com.example.test

import com.destroystokyo.paper.profile.ProfileProperty
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

object TierManager {
    data class Tier(
        val name: String,
        val color: String,
        val rgb: String,
        val egg: String,
        val bootColor: String? = null
    )

    val tiers = mutableMapOf<Int, Tier>()
    private val tierHeads = mutableMapOf<Int, String>()

    fun init() {
        tiers.clear()
        tierHeads.clear()

        tiers[1] = Tier("Axolotl", "<#ffb6c1>", "255,182,193", "axolotl spawn egg")
        tiers[2] = Tier("Squid", "<#1E90FF>", "30,144,255", "squid spawn egg")
        tiers[3] = Tier("Glow Squid", "<#38ACAC>", "56,172,172", "glow squid spawn egg")
        tiers[4] = Tier("Dolphin", "<#C0D0E2>", "192,208,226", "dolphin spawn egg")
        tiers[5] = Tier("Turtle", "<#46BD4A>", "70,189,74", "turtle spawn egg")
        tiers[6] = Tier("Bee", "<#FFC32B>", "255,195,43", "bee spawn egg")
        tiers[7] = Tier("Parrot", "<#FF0000>", "255,0,0", "parrot spawn egg")
        tiers[8] = Tier("Chicken", "<#FFFFFF>", "255,255,255", "chicken spawn egg", "yellow")
        tiers[9] = Tier("Sheep", "<#C8C8C8>", "200,200,200", "sheep spawn egg")
        tiers[10] = Tier("Cow", "<#8E5A3B>", "142,90,59", "cow spawn egg")
        tiers[11] = Tier("Pig", "<#e48484>", "228,132,132", "pig spawn egg")
        tiers[12] = Tier("Fox", "<#e48c44>", "228,140,68", "fox spawn egg")
        tiers[13] = Tier("Sniffer", "<#174435>", "23,68,53", "sniffer spawn egg")
        tiers[14] = Tier("Polar Bear", "<#FFFFFF>", "255,255,255", "polar bear spawn egg")
        tiers[15] = Tier("Slime", "<#75c464>", "117,196,100", "slime spawn egg")
        tiers[16] = Tier("Spider", "<#615548>", "97,85,72", "spider spawn egg")
        tiers[17] = Tier("Zombie", "<#4c7c34>", "76,124,52", "zombie spawn egg")
        tiers[18] = Tier("Husk", "<#D9C49A>", "217,196,154", "husk spawn egg")
        tiers[19] = Tier("Drowned", "<#3A6A78>", "58,106,120", "drowned spawn egg")
        tiers[20] = Tier("Skeleton", "<#bcbcbc>", "188,188,188", "skeleton spawn egg")
        tiers[21] = Tier("Stray", "<#435859>", "67,88,89", "stray spawn egg")
        tiers[22] = Tier("Creeper", "<#00FF00>", "50,255,50", "creeper spawn egg")
        tiers[23] = Tier("Phantom", "<#1E1E3F>", "30,30,63", "phantom spawn egg")
        tiers[24] = Tier("Ravager", "<#5C5C5C>", "92,92,92", "ravager spawn egg")
        tiers[25] = Tier("Guardian", "<#517768>", "81,119,104", "guardian spawn egg")
        tiers[26] = Tier("Piglin", "<#a3593b>", "163,89,59", "zombified piglin spawn egg")
        tiers[27] = Tier("Ghast", "<#FFFFFF>", "255,255,255", "ghast spawn egg")
        tiers[28] = Tier("Magma Cube", "<#320200>", "50,2,0", "magma cube spawn egg")
        tiers[29] = Tier("Blaze", "<#FFB84D>", "255,184,77", "blaze spawn egg")
        tiers[30] = Tier("Wither Skeleton", "<#1A1A1A>", "26,26,26", "wither skeleton spawn egg")
        tiers[31] = Tier("Shulker", "<#8e668f>", "142,102,143", "shulker spawn egg")
        tiers[32] = Tier("Enderman", "<#FF00FF>", "11,11,11", "enderman spawn egg")
        tiers[33] = Tier("Iron Golem", "<#A59C94>", "165,156,148", "iron golem spawn egg")
        tiers[34] = Tier("Elder Guardian", "<#404671>", "64,70,113", "elder guardian spawn egg")
        tiers[35] = Tier("Warden", "<#031923>", "3,25,35", "warden spawn egg")
        tiers[36] = Tier("Wither", "<#3e5e84>", "62,94,132", "wither spawn egg")
        tiers[37] = Tier("Ender Dragon", "<#4B0082>", "75,0,130", "ender dragon spawn egg")
        tiers[38] = Tier("Hero Brine", "<#ffffff>", "0,14,15", "stick")

        tierHeads[1] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjFjM2FhMGQ1MzkyMDhiNDc5NzJiZjhlNzJmMDUwNWNkY2ZiOGQ3Nzk2YjJmY2Y4NTkxMWNlOTRmZDAxOTNkMCJ9fX0="
        tierHeads[2] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMDE0MzNiZTI0MjM2NmFmMTI2ZGE0MzRiODczNWRmMWViNWIzY2IyY2VkZTM5MTQ1OTc0ZTljNDgzNjA3YmFjIn19fQ=="
        tierHeads[3] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDVjOTk5ZGQxMmRkMWM4NjZmZGQwZWU5NGEzOTczNTMzNDI4Y2Q3MmQ5Mjk2YzYyNzI0ZjQyOTM2NWRhOGVlYiJ9fX0="
        tierHeads[4] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU5Njg4Yjk1MGQ4ODBiNTViN2FhMmNmY2Q3NmU1YTBmYTk0YWFjNmQxNmY3OGU4MzNmNzQ0M2VhMjlmZWQzIn19fQ=="
        tierHeads[5] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzYxY2QzZTVmN2E5YmI1OGEwZWQyNGRmOTRlMjc1MTNlYTYxYzdhNDFmMzNlMDE4MGFkOWM4NWY1MzI3ZjdjNSJ9fX0="
        tierHeads[6] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzZkZWQ4MzU1YTU2MjU4OTNlODA0MTg3N2FmNjU2ODNjMmZkMGFhMDljM2QzYzFjOTRmNWY4MGEwZjhjMjJjYiJ9fX0="
        tierHeads[7] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWQxYTE2OGJjNzJjYjMxNGY3Yzg2ZmVlZjlkOWJjNzYxMjM2NTI0NGNlNjdmMGExMDRmY2UwNDIwMzQzMGMxZCJ9fX0="
        tierHeads[8] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2FkM2RkMDA4M2ZhYTY5YTA2MmY5YWQ4MTQxOGY1YTU5NjE4MGJmMTU5MmU0YjhkMTMwM2IyMzBiNjRiYzc5ZSJ9fX0="
        tierHeads[9] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzBmNTAzOTRjNmQ3ZGJjMDNlYTU5ZmRmNTA0MDIwZGM1ZDY1NDhmOWQzYmM5ZGNhYzg5NmJiNWNhMDg1ODdhIn19fQ=="
        tierHeads[10] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjY2N2MwZTEwN2JlNzlkNzY3OWJmZTg5YmJjNTdjNmJmMTk4ZWNiNTI5YTMyOTVmY2ZkZmQyZjI0NDA4ZGNhMyJ9fX0="
        tierHeads[11] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWIxNzYwZTM3NzhmODA4NzA0NmI4NmJlYzZhMGE4M2E1Njc2MjVmMzBmMGQ2YmNlODY2ZDRiZWQ5NWRiYTZjMSJ9fX0="
        tierHeads[12] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmQ0ZWZhN2UzMTUyYmY5ZTNhNTViOWM4ODNhZmEzNzAzNTE1NGM3NGRhNDdhYzg5NmRhNDhkYmFiZWI5MzRhZSJ9fX0="
        tierHeads[13] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdhZDkyMGE2NmUzOGNjMzQyNmE1YmZmMDg0NjY3ZTg3NzIxMTY5MTVlMjk4MDk4NTY3YzEzOWYyMjJlMmM0MiJ9fX0="
        tierHeads[14] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGIyZjQ4NDU0ZDBjMDYxMDI0NTkxNWQyN2U5ODEyNzI5MGRmZWE5NzQxZWI1OTE5OGUzNzFmNDQ5OTlmOWNiNCJ9fX0="
        tierHeads[15] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjBjYzM1OTdmMjVkNjJiN2Y1NzQ4Y2VjMjJlMmZiZWQyMzYwNDBmMWMyNzA0N2FmZWExZjUwZjc2OGE4In19fQ=="
        tierHeads[16] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzg3YTk2YThjMjNiODNiMzJhNzNkZjA1MWY2Yjg0YzJlZjI0ZDI1YmE0MTkwZGJlNzRmMTExMzg2MjliNWFlZiJ9fX0="
        tierHeads[17] = "minecraft:zombie_head"
        tierHeads[18] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDY3NGM2M2M4ZGI1ZjRjYTYyOGQ2OWEzYjFmOGEzNmUyOWQ4ZmQ3NzVlMWE2YmRiNmNhYmI0YmU0ZGIxMjEifX19"
        tierHeads[19] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzNmN2NjZjYxZGJjM2Y5ZmU5YTYzMzNjZGUwYzBlMTQzOTllYjJlZWE3MWQzNGNmMjIzYjNhY2UyMjA1MSJ9fX0="
        tierHeads[20] = "minecraft:skeleton_skull"
        tierHeads[21] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU3YmYwN2IzODg0N2FjMzA5ZDMzZTMyYTZiZjNiZDYwODc0MGNiZDljZjUwMmRjMWQ2NzBjM2VhMjZmOWRmNiJ9fX0="
        tierHeads[22] = "minecraft:creeper_head"
        tierHeads[23] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWNiZmIzZTAzNzRiYzMwMzI0NDIzZTY0NDU2ZTJiYmFmMjliODMzODdhNDk1N2I3Y2U0MGM4MmMxM2MxNmYxZSJ9fX0="
        tierHeads[24] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTQ3ZmQ3NzUxZWM4MDcwMTViNWEzYjIwMDZkZDBmMzA3ZmNlZTM0ZjNhZWQ3MmJhMThkY2UxZDA4N2Q3MGViZiJ9fX0="
        tierHeads[25] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjE4NGZkZTliODY4ODBmNWIxZWNhNjgyYWE0NDJjN2QwNDdmYTM3Y2U0NDk2M2RiNmY2NGU3MzQ3NWRjOTU0ZSJ9fX0="
        tierHeads[26] = "minecraft:piglin_head"
        tierHeads[27] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzY4OGU2MTY0MmEwYjY4NjQzZjRiYTM2OTJmZTIwNjYyMmI0ZDlhN2QzOTY1YmEwYmUxMzI5YzIxMzJkIn19fQ=="
        tierHeads[28] = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTFjOTdhMDZlZmRlMDRkMDAyODdiZjIwNDE2NDA0YWIyMTAzZTEwZjA4NjIzMDg3ZTFiMGMxMjY0YTFjMGYwYyJ9fX0="
        tierHeads[29] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGY4NjYzMjBiNTVlOWYzM2I0YjI3OWZmNzkyMmZmYWE4MGMzZDM1ODE2OWNjYWJjZmI4ZDVhYTRiNjQ1OTRlNiJ9fX0="
        tierHeads[30] = "minecraft:wither_skeleton_skull"
        tierHeads[31] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGU4YzkzMzZlZDEzYjQ2OGYyNjdmZjgxMzJjMzdiNGEzMGM1MDNlNDViNDJkNjlmNWMzYmJhOWJiMjkyOTM5MiJ9fX0="
        tierHeads[32] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWVkY2I0OTA4MjlmNTUyMmNmMzNjMzNmM2ZiZGViN2YxNzhhZWRhYWRlMjcwYTA5NThjOGE3OTI3MzhlNSJ9fX0="
        tierHeads[33] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmUyMGQ5OTJmMDg3N2MxMzI1OWZiYzYwYWVkNzY1NjgwNTE0YmZmZmY5NmQ0ZWI3YjcxOTE5NWE2OTY2NmM1NyJ9fX0="
        tierHeads[34] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzIzZDU4ODU5YTFhYTIyYmYwZWY5NjZiYjI5NDVjYzM5NjJiYjRiZTVmZWQ0ZTM2OWU3ZjExMjg1NzM1MWYyMiJ9fX0="
        tierHeads[35] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjk3OWFjZWUxYTkzZWM3MWE0ODZkN2I5NTVhNDY5M2UzMTNjNTA4OTlkZWJiNTMwMWIwNzJjNDBkZDUzZWVkMCJ9fX0="
        tierHeads[36] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjFiZmM0YzNlYWU4Zjg1YTMyZGIwYzllYWZmZWI2ODZiZDViMWU1MGFkNzJiYWM4MTM2ZDk4OWJhMzQ4M2Q2NyJ9fX0="
        tierHeads[37] = "minecraft:dragon_head"
        tierHeads[38] = "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjZjODViZjExZjA5YTE0MzdkMDQwNGNlZjE2YzhiNWU0YWMxYmVjMmIzMDU5YWY1NjBkMGFlOGE5YzRhMjNhNSJ9fX0="
    }

    fun getTier(tier: Int): Tier? = tiers[tier]

    fun makeTierHead(tier: Int): ItemStack {
        val headValue = tierHeads[tier]
        if (headValue != null && headValue.startsWith("minecraft:")) {
            val material = when (headValue) {
                "minecraft:zombie_head" -> Material.ZOMBIE_HEAD
                "minecraft:skeleton_skull" -> Material.SKELETON_SKULL
                "minecraft:creeper_head" -> Material.CREEPER_HEAD
                "minecraft:piglin_head" -> Material.PIGLIN_HEAD
                "minecraft:wither_skeleton_skull" -> Material.WITHER_SKELETON_SKULL
                "minecraft:dragon_head" -> Material.DRAGON_HEAD
                else -> Material.PLAYER_HEAD
            }
            return ItemStack(material)
        }

        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta
        if (!headValue.isNullOrBlank()) {
            val profile = Bukkit.createProfile(UUID.randomUUID())
            profile.setProperty(ProfileProperty("textures", headValue))
            meta.playerProfile = profile
        }
        item.itemMeta = meta
        return item
    }
}
