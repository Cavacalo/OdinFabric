package com.odtheking.odin.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.WorldLoadEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.renderPos
import com.odtheking.odin.utils.render.drawLine
import com.odtheking.odin.utils.render.drawStyledBox
import com.odtheking.odin.utils.renderBoundingBox
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

object Highlight : Module(
    name = "Highlight",
    description = "Allows you to highlight selected entities."
) {
    private val highlightStar by BooleanSetting("Highlight Starred Mobs", true, desc = "Highlights starred dungeon mobs.")
    private val color by ColorSetting("Highlight color", Colors.WHITE, true, desc = "The color of the highlight.")
    private val renderStyle by SelectorSetting("Render Style", "Outline", listOf("Filled", "Outline", "Filled Outline"), desc = "Style of the box.")
    private val hideNonNames by BooleanSetting("Hide non-starred names", true, desc = "Hides names of entities that are not starred.")

    private val teammateClassGlow by BooleanSetting("Teammate Class Glow", true, desc = "Highlights dungeon teammates based on their class color.")

    // New setting: Depth Check
    private val depthCheck by BooleanSetting("Depth Check", true, desc = "Highlights entities through walls when off")

    // Tracer setting for starred mobs
    private val tracer by BooleanSetting("Tracer", false, desc = "Draws a line to starred mobs.")

    private val dungeonMobSpawns = hashSetOf("Lurker", "Dreadlord", "Souleater", "Zombie", "Skeleton", "Skeletor", "Sniper", "Super Archer", "Spider", "Fels", "Withermancer", "Lost Adventurer", "Angry Archaeologist", "Frozen Adventurer")
    // https://regex101.com/r/QQf502/1
    private val starredRegex = Regex("^.*✯ .*\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?(?:[kM])?❤$")

    private val entities = mutableSetOf<Entity>()

    init {
        on<TickEvent.End> {
            if (!highlightStar || !DungeonUtils.inDungeons || DungeonUtils.inBoss) return@on

            val entitiesToRemove = mutableListOf<Entity>()
            mc.level?.entitiesForRendering()?.forEach { e ->
                val entity = e ?: return@forEach
                if (!entity.isAlive || entity !is ArmorStand) return@forEach

                val entityName = entity.name?.string ?: return@forEach
                if (!dungeonMobSpawns.any { it in entityName }) return@forEach

                val isStarred = starredRegex.matches(entityName)

                if (hideNonNames && entity.isInvisible && !isStarred) {
                    entitiesToRemove.add(entity)
                    return@forEach
                }

                if (!isStarred) return@forEach

                mc.level?.getEntities(entity, entity.boundingBox.move(0.0, -1.0, 0.0)) { isValidEntity(it) }
                    ?.firstOrNull()?.let { entities.add(it) }
            }
            entitiesToRemove.forEach { it.remove(Entity.RemovalReason.DISCARDED) }
            entities.removeIf { entity -> !entity.isAlive }
        }

        on<RenderEvent.Last> {
            if (!highlightStar || !DungeonUtils.inDungeons || DungeonUtils.inBoss) return@on

            entities.forEach { entity ->
                if (!entity.isAlive) return@forEach
                context.drawStyledBox(entity.renderBoundingBox, color, renderStyle, depthCheck)

                if (tracer) {
                    val pos = entity.position()
                    mc.player?.let {
                        // drawLine(start/end, color, depth)
                        context.drawLine(listOf(it.eyePosition, pos.add(0.0, 1.0, 0.0)), color = Colors.MINECRAFT_YELLOW, depthCheck)
                    }
                }
            }
        }

        on<WorldLoadEvent> {
            entities.clear()
        }
    }

    private fun isValidEntity(entity: Entity): Boolean =
        when (entity) {
            is ArmorStand -> false
            is WitherBoss -> false
            is Player -> entity.uuid.version() == 2 && entity != mc.player
            else -> true
        }

    @JvmStatic
    fun getTeammateColor(entity: Entity): Int? {
        if (!enabled || !teammateClassGlow || !DungeonUtils.inDungeons || entity !is Player) return null
        return DungeonUtils.dungeonTeammates.find { it.name == entity.name?.string }?.clazz?.color?.rgba
    }
}
