/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Cheat engine with built-in cheats for RPG Maker MV/MZ (JavaScript)
 * and RPG Maker XP/VX/VX Ace (Ruby via mkxp-z).
 */

package com.runestone.app.cheats

import android.util.Log
import android.webkit.WebView

/**
 * Sealed hierarchy of cheats applicable to RPG Maker games.
 */
sealed class Cheat {
    /** Add items to inventory. Each item has an id and optional quantity. */
    data class AddItems(val items: List<ItemDef>) : Cheat()

    /** Level up all party members by [amount] levels. -1 = max level. */
    data class LevelUp(val amount: Int = 99) : Cheat()

    /** Full HP + MP restore for entire party. */
    data object HealParty : Cheat()

    /** Set gold to exact amount. */
    data class SetGold(val amount: Int) : Cheat()

    /** Set a specific stat on all party members. */
    data class SetStat(val stat: String, val value: Int) : Cheat()

    /** Toggle walk-through-walls (debug mode). */
    data object WalkThroughWalls : Cheat()

    /** Toggle random encounters on/off. */
    data object ToggleEncounter : Cheat()

    /** Add all items in the game database. */
    data object AllItems : Cheat()

    /** Set all party members to max HP/MP. */
    data object MaxStats : Cheat()

    /** Instantly win the next battle. */
    data object OneHitKill : Cheat()

    /** Run a custom script. */
    data class CustomScript(val script: String, val language: ScriptLang) : Cheat()
}

data class ItemDef(val id: Int, val quantity: Int = 99)

enum class ScriptLang { JAVASCRIPT, RUBY }

// ─────────────────────────────────────────────────────────────────

/**
 * Injects cheats into RPG Maker MV/MZ games via JavaScript.
 *
 * All MV/MZ games expose global game objects:
 *   ${'$'}gameParty   — party management (items, gold, members)
 *   ${'$'}gameActors  — actor database
 *   ${'$'}gameSystem  — system state (encounters, switches, variables)
 *   ${'$'}gamePlayer  — player position
 *   ${'$'}gameTemp    — temporary battle flags
 *   ${'$'}dataItems   — item database
 *   ${'$'}dataWeapons, ${'$'}dataArmors — equipment database
 */
object MvCheatEngine {

    private const val TAG = "MvCheatEngine"

    /** Inject a single cheat into a running MV/MZ WebView game. */
    fun inject(webView: WebView, cheat: Cheat): Boolean {
        val js = when (cheat) {
            is Cheat.SetGold -> mvSetGold(cheat.amount)
            is Cheat.HealParty -> mvHealParty
            is Cheat.LevelUp -> mvLevelUp(cheat.amount)
            is Cheat.AddItems -> mvAddItems(cheat.items)
            is Cheat.SetStat -> mvSetStat(cheat.stat, cheat.value)
            is Cheat.WalkThroughWalls -> mvWalkThroughWalls
            is Cheat.ToggleEncounter -> mvToggleEncounter
            is Cheat.AllItems -> mvAllItems
            is Cheat.MaxStats -> mvMaxStats
            is Cheat.OneHitKill -> mvOneHitKill
            is Cheat.CustomScript -> if (cheat.language == ScriptLang.JAVASCRIPT) cheat.script
                else return false
        }
        return try {
            webView.evaluateJavascript("(function(){try{$js}catch(e){console.error('Runestone cheat:',e);}})();", null)
            Log.i(TAG, "Injected: ${cheat.javaClass.simpleName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Injection failed", e)
            false
        }
    }

    /** Inject multiple cheats at once. */
    fun injectAll(webView: WebView, cheats: List<Cheat>): Int {
        return cheats.count { inject(webView, it) }
    }

    // ── cheat JS generators ─────────────────────────────────────

    private val mvSetGold: (Int) -> String = { amount ->
        """
        if (window.${'$'}gameParty) {
            ${'$'}gameParty._gold = $amount;
            ${'$'}gameParty.gainGold(0);
        }
        """.trimIndent()
    }

    private val mvHealParty = """
        if (window.${'$'}gameParty) {
            ${'$'}gameParty.members().forEach(function(a) {
                a.recoverAll();
            });
        }
    """.trimIndent()

    private val mvLevelUp: (Int) -> String = { amount ->
        """
        if (window.${'$'}gameParty && window.${'$'}gameActors) {
            ${'$'}gameParty.members().forEach(function(a) {
                var targetLvl = ${if (amount < 0) 99 else "a._level + $amount"};
                while (a._level < targetLvl) {
                    a.changeExp(a.expForLevel(a._level + 1) - a.currentExp(), false);
                }
                a.recoverAll();
            });
        }
        """.trimIndent()
    }

    private val mvAddItems: (List<ItemDef>) -> String = { items ->
        val itemCalls = items.joinToString(";") { item ->
            "${'$'}gameParty.gainItem(${$"dataItems"}[${item.id}], ${item.quantity});"
        }
        """
        if (window.${'$'}gameParty && window.${'$'}dataItems) {
            $itemCalls
        }
        """.trimIndent()
    }

    private val mvSetStat: (String, Int) -> String = { stat, value ->
        """
        if (window.${'$'}gameParty) {
            ${'$'}gameParty.members().forEach(function(a) {
                a.addParam('$stat', $value);
            });
        }
        """.trimIndent()
    }

    private val mvWalkThroughWalls = """
        if (window.${'$'}gamePlayer) {
            ${'$'}gamePlayer._through = !${'$'}gamePlayer._through;
        }
    """.trimIndent()

    private val mvToggleEncounter = """
        if (window.${'$'}gameSystem) {
            ${'$'}gameSystem._encounterEnabled = !${'$'}gameSystem._encounterEnabled;
        }
    """.trimIndent()

    private val mvAllItems = """
        if (window.${'$'}gameParty && window.${'$'}dataItems) {
            for (var i = 1; i < ${'$'}dataItems.length; i++) {
                if (${'$'}dataItems[i] && ${'$'}dataItems[i].id) {
                    ${'$'}gameParty.gainItem(${'$'}dataItems[i], 99);
                }
            }
        }
    """.trimIndent()

    private val mvMaxStats = """
        if (window.${'$'}gameParty) {
            ${'$'}gameParty.members().forEach(function(a) {
                a.setHp(a.mhp);
                a.setMp(a.mmp);
                a._level = 99;
                a.recoverAll();
            });
        }
    """.trimIndent()

    private val mvOneHitKill = """
        if (window.${'$'}gameTemp) {
            ${'$'}gameTemp._oneHitKill = !(${'$'}gameTemp._oneHitKill);
            // Hook into Game_Action to apply OHK damage
            var _origEvalDamage = Game_Action.prototype.evalDamageFormula;
            Game_Action.prototype.evalDamageFormula = function(target) {
                if (${'$'}gameTemp._oneHitKill) {
                    target.setHp(0);
                    return;
                }
                _origEvalDamage.call(this, target);
            };
        }
    """.trimIndent()
}

// ─────────────────────────────────────────────────────────────────

/**
 * Injects cheats into RPG Maker XP/VX/VX Ace games via Ruby.
 *
 * These games run on mkxp-z which supports Ruby evaluation.
 * The cheats are sent as Ruby script strings via Intent extras
 * or a named pipe to the mkxp-z process.
 *
 * NOTE: Ruby injection requires mkxp-z build with custom patches
 * to expose a "runestone_eval" interface. This is a stub until
 * those patches are applied.
 */
object RgssCheatEngine {

    private const val TAG = "RgssCheatEngine"

    /**
     * Generate a Ruby script string for the given cheat.
     * The resulting string should be sent to mkxp-z's eval interface.
     */
    fun toRuby(cheat: Cheat): String? {
        return when (cheat) {
            is Cheat.SetGold -> rgssSetGold(cheat.amount)
            is Cheat.HealParty -> rgssHealParty
            is Cheat.LevelUp -> rgssLevelUp(cheat.amount)
            is Cheat.AddItems -> rgssAddItems(cheat.items)
            is Cheat.WalkThroughWalls -> rgssWalkThroughWalls
            is Cheat.ToggleEncounter -> rgssToggleEncounter
            is Cheat.SetStat -> rgssSetStat(cheat.stat, cheat.value)
            is Cheat.MaxStats -> rgssMaxStats
            is Cheat.AllItems -> null // RGSS doesn't have a global item DB array
            is Cheat.OneHitKill -> null // Not implemented for RGSS yet
            is Cheat.CustomScript -> if (cheat.language == ScriptLang.RUBY) cheat.script else null
        }
    }

    /** Generate a complete Ruby script that runs multiple cheats. */
    fun toRubyBatch(cheats: List<Cheat>): String {
        return cheats.mapNotNull { toRuby(it) }.joinToString("\n") {
            "begin\n$it\nrescue Exception => e\n  # ignore\nend"
        }
    }

    // ── cheat Ruby generators ───────────────────────────────────

    private val rgssSetGold: (Int) -> String = { amount ->
        "${'$'}game_party.gold = $amount"
    }

    private val rgssHealParty = """
        ${'$'}game_party.actors.each { |a| a.recover_all }
    """.trimIndent()

    private val rgssLevelUp: (Int) -> String = { amount ->
        if (amount < 0)
            "${'$'}game_party.actors.each { |a| a.level = 99; a.recover_all }"
        else
            "${'$'}game_party.actors.each { |a| ${amount}.times { a.level_up }; a.recover_all }"
    }

    private val rgssAddItems: (List<ItemDef>) -> String = { items ->
        items.joinToString(";") { item ->
            if (item.id > 0)
                "${'$'}game_party.gain_item(${$"data_items"}[${item.id}], ${item.quantity})"
            else
                "# unknown item id=${item.id}"
        }
    }

    private val rgssWalkThroughWalls = """
        ${'$'}game_player.through = !${'$'}game_player.through
    """.trimIndent()

    private val rgssToggleEncounter = """
        ${'$'}game_system.encounter_disabled = !${'$'}game_system.encounter_disabled
    """.trimIndent()

    private val rgssSetStat: (String, Int) -> String = { stat, value ->
        "${'$'}game_party.actors.each { |a| a.add_param('$stat', $value) }"
    }

    private val rgssMaxStats = """
        ${'$'}game_party.actors.each { |a|
            a.hp = a.maxhp
            a.mp = a.maxmp
            a.level = 99
            a.recover_all
        }
    """.trimIndent()
}
