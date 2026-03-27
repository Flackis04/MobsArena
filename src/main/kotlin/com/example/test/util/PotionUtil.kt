package com.example.test

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object PotionUtil {
    fun applyNightVision(player: Player) {
        val effect = PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 0, false, false, false)
        player.addPotionEffect(effect)
    }
}
