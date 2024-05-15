package us.timinc.mc.cobblemon.capturexp

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.util.isInBattle
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import us.timinc.mc.cobblemon.capturexp.config.CaptureXPConfig
import us.timinc.mc.cobblemon.capturexp.config.ConfigBuilder

@Mod(CaptureXP.MOD_ID)
object CaptureXP {
    const val MOD_ID = "capture_xp"
    private var captureXPConfig: CaptureXPConfig = ConfigBuilder.load(CaptureXPConfig::class.java, MOD_ID)

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.FORGE)
    object Registration {
        @SubscribeEvent
        fun onInit(e: ServerStartedEvent) {
            CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
                if (event.player.isInBattle()) handleCaptureInBattle(event) else handleCaptureOutOfBattle(event)
            }
        }
    }

    private fun handleCaptureInBattle(event: PokemonCapturedEvent) {
        val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(event.player) ?: return
        val caughtBattleMonActor = battle.actors.find { it.uuid == event.pokemon.uuid } ?: return
        val caughtBattleMon = caughtBattleMonActor.pokemonList.find { it.uuid == event.pokemon.uuid } ?: return

        caughtBattleMonActor.getSide().getOppositeSide().actors.forEach { opponentActor ->
            opponentActor.pokemonList.filter {
                it.health > 0 && (caughtBattleMon.facedOpponents.contains(it) || it.effectedPokemon.heldItem()
                    .`is`(CobblemonItemTags.EXPERIENCE_SHARE))
            }.forEach { opponentMon ->
                val xpShareOnly = !caughtBattleMon.facedOpponents.contains(opponentMon)
                val xpShareOnlyModifier =
                    (if (xpShareOnly) Cobblemon.config.experienceShareMultiplier else 1).toDouble()
                val experience = Cobblemon.experienceCalculator.calculate(
                    opponentMon, caughtBattleMon, captureXPConfig.multiplier * xpShareOnlyModifier
                )
                if (experience > 0) {
                    opponentActor.awardExperience(opponentMon, experience)
                }
            }
        }
    }

    private fun handleCaptureOutOfBattle(event: PokemonCapturedEvent) {
        val playerParty = Cobblemon.storage.getParty(event.player)
        val source = SidemodExperienceSource(MOD_ID)
        val first = playerParty.firstOrNull { it != event.pokemon && it.currentHealth > 0 } ?: return
        val targetMons = playerParty.filter {
            it != event.pokemon && it.currentHealth > 0 && (it.uuid == first.uuid || it.heldItem()
                .`is`(CobblemonItemTags.EXPERIENCE_SHARE))
        }
        targetMons.forEach { targetMon ->
            val xpShareOnly = targetMon.uuid != first.uuid
            val xpShareOnlyModifier = (if (xpShareOnly) Cobblemon.config.experienceShareMultiplier else 1).toDouble()
            val experience = Cobblemon.experienceCalculator.calculate(
                BattlePokemon.safeCopyOf(targetMon),
                BattlePokemon.safeCopyOf(event.pokemon),
                captureXPConfig.multiplier * xpShareOnlyModifier
            )
            targetMon.addExperienceWithPlayer(event.player, source, experience)
        }
    }
}