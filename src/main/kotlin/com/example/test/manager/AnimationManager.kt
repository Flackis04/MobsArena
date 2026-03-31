package com.example.test

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.sin

object AnimationManager {
    enum class AnimationMode {
        LINEAR,
        CLASSIC
    }

    private val activeAnimations = mutableMapOf<String, ActiveAnimation>()
    private const val FRAME_COUNT = 10
    const val MIN_BLOCK_BREAK_DURATION_TICKS = FRAME_COUNT
    const val MAX_BLOCK_BREAK_DURATION_TICKS = 64
    const val MIN_EXTRA_BLOCK_DELAY_TICKS = 0L
    const val MAX_EXTRA_BLOCK_DELAY_TICKS = 20L
    private const val DEFAULT_BLOCK_BREAK_DURATION_TICKS = 8
    private const val DEFAULT_START_DELAY_TICKS = 0L

    fun playBlockBreakAnimation(
        loc: Location,
        material: Material,
        blockData: BlockData,
        durationTicks: Int = DEFAULT_BLOCK_BREAK_DURATION_TICKS,
        startDelayTicks: Long = DEFAULT_START_DELAY_TICKS,
        animationMode: AnimationMode = AnimationMode.LINEAR,
        onStart: (() -> Unit)? = null
    ) {
        val block = loc.block
        val world = loc.world ?: return
        val key = locKey(loc)
        clearAnimation(key)
        val normalizedDuration = durationTicks.coerceAtLeast(FRAME_COUNT - 1)
        val frameStepTicks = kotlin.math.ceil(
            normalizedDuration.toDouble() / (FRAME_COUNT - 1).toDouble()
        ).toLong().coerceAtLeast(1L)
        val activeAnimation = ActiveAnimation(null, null)

        fun spawnInitialFrame() {
            if (activeAnimation.display?.isValid == true) return
            activeAnimation.display = world.spawn(loc.clone(), BlockDisplay::class.java) {
                val initialFrame = frameAt(0, animationMode)
                it.block = blockData.clone()
                it.transformation = transform(initialFrame)
                it.interpolationDuration = 0
                it.interpolationDelay = 0
            }
            block.type = Material.AIR
            MineManager.removeStoredOre(block.location)
            MineManager.revealExposedBlocks(block)
            onStart?.invoke()
        }

        val task = object : BukkitRunnable() {
            var frameIndex = if (startDelayTicks <= 0L) 1 else 0

            override fun run() {
                val animation = activeAnimations[key]
                if (animation == null) {
                    clearAnimation(key)
                    cancel()
                    return
                }

                if (frameIndex == 0 && animation.display == null) {
                    spawnInitialFrame()
                }

                val display = animation.display
                if (display == null || !display.isValid) {
                    clearAnimation(key)
                    cancel()
                    return
                }

                if (frameIndex >= FRAME_COUNT) {
                    clearAnimation(key)
                    cancel()
                    return
                }

                val frame = frameAt(frameIndex, animationMode)
                display.interpolationDuration = if (frameIndex == 0) 0 else frameStepTicks.toInt()
                display.interpolationDelay = 0
                display.transformation = transform(frame)
                frameIndex++
            }
        }

        if (startDelayTicks <= 0L) {
            spawnInitialFrame()
        }

        val scheduledTask = task.runTaskTimer(
            TestPlugin.instance,
            if (startDelayTicks <= 0L) frameStepTicks else startDelayTicks,
            frameStepTicks
        )
        activeAnimation.task = scheduledTask
        activeAnimations[key] = activeAnimation

        // Hard cleanup in case a repeating task never completes under lag.
        org.bukkit.Bukkit.getScheduler().runTaskLater(TestPlugin.instance, Runnable {
            clearAnimation(key)
        }, startDelayTicks + (frameStepTicks * FRAME_COUNT) + 8L)
    }

    fun breakBlock(
        player: Player?,
        loc: Location,
        material: Material,
        blockData: BlockData,
        durationTicks: Int = DEFAULT_BLOCK_BREAK_DURATION_TICKS,
        startDelayTicks: Long = DEFAULT_START_DELAY_TICKS,
        onStart: (() -> Unit)? = null
    ) {
        val data = player?.let { DataStore.get(it.uniqueId) }
        if (data != null && !data.animationsEnabled) {
            breakBlockImmediately(loc, onStart)
            return
        }

        val resolvedDuration = data?.animationDurationTicks ?: durationTicks
        val resolvedMode = if (data?.animationLinearMode != false) AnimationMode.LINEAR else AnimationMode.CLASSIC
        playBlockBreakAnimation(
            loc = loc,
            material = material,
            blockData = blockData,
            durationTicks = clampDurationTicks(resolvedDuration),
            startDelayTicks = startDelayTicks,
            animationMode = resolvedMode,
            onStart = onStart
        )
    }

    fun clearAll() {
        activeAnimations.keys.toList().forEach(::clearAnimation)
    }

    private fun clearAnimation(key: String) {
        val animation = activeAnimations.remove(key) ?: return
        animation.task?.cancel()
        val display = animation.display
        if (display?.isValid == true) {
            display.remove()
        }
    }

    private fun breakBlockImmediately(loc: Location, onStart: (() -> Unit)?) {
        val block = loc.block
        block.type = Material.AIR
        MineManager.removeStoredOre(block.location)
        MineManager.revealExposedBlocks(block)
        onStart?.invoke()
    }

    fun clampDurationTicks(value: Int): Int =
        value.coerceIn(MIN_BLOCK_BREAK_DURATION_TICKS, MAX_BLOCK_BREAK_DURATION_TICKS)

    fun clampExtraBlockDelayTicks(value: Long): Long =
        value.coerceIn(MIN_EXTRA_BLOCK_DELAY_TICKS, MAX_EXTRA_BLOCK_DELAY_TICKS)

    private fun transform(frame: FrameState): Transformation {
        return Transformation(
            Vector3f(frame.translate, frame.verticalLift, frame.translate),
            Quaternionf().rotateY(frame.rotation),
            Vector3f(frame.scale, frame.scale, frame.scale),
            Quaternionf()
        )
    }

    private fun frameAt(frameIndex: Int, animationMode: AnimationMode): FrameState {
        val t = frameIndex.toFloat() / (FRAME_COUNT - 1).toFloat()
        val eased = when (animationMode) {
            AnimationMode.LINEAR -> t
            AnimationMode.CLASSIC -> t * t * t
        }
        val scale = (1f - (eased * 0.92f)).coerceAtLeast(0.0011f)
        val translate = 0.5f * eased
        val verticalLift = (sin(t * PI).toFloat() * 0.08f) - (eased * 0.015f)
        val rotation = when (animationMode) {
            AnimationMode.LINEAR -> eased * 0.35f
            AnimationMode.CLASSIC -> eased * 0.8f
        }
        return FrameState(scale, translate, verticalLift, rotation)
    }

    private fun locKey(loc: Location): String = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"

    private data class ActiveAnimation(var display: BlockDisplay?, var task: BukkitTask?)
    private data class FrameState(val scale: Float, val translate: Float, val verticalLift: Float, val rotation: Float)
}
