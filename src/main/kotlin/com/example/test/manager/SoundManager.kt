package com.example.test

import kotlin.random.Random

object SoundManager {
    const val ITEM_PICKUP_SOUND = "entity.item.pickup"
    const val EXPERIENCE_PICKUP_SOUND = "entity.experience_orb.pickup"

    const val DEFAULT_VOLUME = 1f
    const val ITEM_PICKUP_VOLUME = 0.5f

    const val EXCAVATOR_BREAK_PITCH_MULTIPLIER = 0.1f
    const val ORE_BOOST_PICKUP_PITCH_MULTIPLIER = 1.5f

    const val VALUABLE_BREAK_MIN_PITCH = 0.45f
    const val VALUABLE_BREAK_MAX_PITCH = 0.7f
    const val FIRST_VALUABLE_BREAK_PITCH = 1.2f

    fun getValuablePickupPitch(valuableIndex: Int, oreBoostActive: Boolean, excavatorActive: Boolean): Float {
        val pitchBase = (1 + valuableIndex / 9.0).toFloat()
        val oreBoostMultiplier = if (oreBoostActive) ORE_BOOST_PICKUP_PITCH_MULTIPLIER else 1f
        return pitchBase * oreBoostMultiplier
    }

    fun getBreakPitch(excavatorActive: Boolean): Float {
        val multiplier = if (excavatorActive) EXCAVATOR_BREAK_PITCH_MULTIPLIER else 1f

        val minPitch = VALUABLE_BREAK_MIN_PITCH * multiplier
        val maxPitch = VALUABLE_BREAK_MAX_PITCH * multiplier

        return Random.nextDouble(minPitch.toDouble(), maxPitch.toDouble()).toFloat()
    }
}