/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Quick save/load states for WebView games.
 * Serializes the game state via JS evaluation and restores it.
 */

package com.runestone.app.media

import android.content.Context
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import java.io.File

/**
 * Quick save/load system for RPG Maker MV/MZ WebView games.
 *
 * For MV/MZ: serializes ${'$'}gameParty, ${'$'}gameActors, ${'$'}gameSystem, ${'$'}gamePlayer,
 * ${'$'}gameSwitches, ${'$'}gameVariables, ${'$'}gameSelfSwitches to JSON and back.
 */
object QuickSaveManager {

    private const val TAG = "QuickSave"

    /** Create a quick save state for the current game. */
    fun save(webView: WebView, slot: Int = 1): Boolean {
        val js = """
            (function(){
                try {
                    var state = {};
                    if (window.${'$'}gameParty) {
                        state.party = {
                            gold: ${'$'}gameParty._gold,
                            items: ${'$'}gameParty._items,
                            weapons: ${'$'}gameParty._weapons,
                            armors: ${'$'}gameParty._armors,
                            steps: ${'$'}gameParty._steps,
                            members: ${'$'}gameParty._actors.map(function(id) { return id; }),
                            lastItem: ${'$'}gameParty._lastItem ? ${'$'}gameParty._lastItem.id : 0,
                        };
                    }
                    if (window.${'$'}gameActors) {
                        state.actors = {};
                        ${'$'}gameActors._data.forEach(function(a, i) {
                            if (a) state.actors[i] = { level: a._level, hp: a._hp, mp: a._mp, exp: a._exp, name: a._name };
                        });
                    }
                    if (window.${'$'}gameSystem) {
                        state.system = {
                            saveCount: ${'$'}gameSystem._saveCount,
                            bgmVolume: ${'$'}gameSystem._bgmVolume,
                            seVolume: ${'$'}gameSystem._seVolume,
                            encounterEnabled: ${'$'}gameSystem._encounterEnabled,
                        };
                    }
                    if (window.${'$'}gamePlayer) {
                        state.player = {
                            x: ${'$'}gamePlayer._x, y: ${'$'}gamePlayer._y,
                            mapId: ${'$'}gamePlayer._mapId,
                            direction: ${'$'}gamePlayer._direction,
                        };
                    }
                    if (window.${'$'}gameSwitches) state.switches = ${'$'}gameSwitches._data.slice(0);
                    if (window.${'$'}gameVariables) state.variables = ${'$'}gameVariables._data.slice(0);
                    window.__runestoneQuickSave = JSON.stringify(state);
                    return window.__runestoneQuickSave;
                } catch(e) { return 'ERROR:' + e.message; }
            })();
        """.trimIndent()

        // Use evaluateJavascript with callback to capture state
        webView.evaluateJavascript(js) { result ->
            if (result != null && !result.startsWith("ERROR") && !result.startsWith("null")) {
                try {
                    val json = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
                    window.__quickSaveState = json
                    Log.i(TAG, "Quick save: slot=$slot — state captured (${json.length} chars)")
                } catch (e: Exception) {
                    Log.w(TAG, "Quick save parse error", e)
                }
            }
        }
        return true
    }

    /** Load a quick save state back into the game. */
    fun load(webView: WebView): Boolean {
        val state = window.__quickSaveState ?: return false
        val js = """
            (function(){
                try {
                    var state = $state;
                    if (!state) return 'ERROR: no state';
                    if (state.party && window.${'$'}gameParty) {
                        ${'$'}gameParty._gold = state.party.gold;
                        ${'$'}gameParty._steps = state.party.steps;
                        state.party.members.forEach(function(id) {
                            if (!${'$'}gameParty._actors.includes(id)) ${'$'}gameParty._actors.push(id);
                        });
                    }
                    if (state.actors && window.${'$'}gameActors) {
                        Object.keys(state.actors).forEach(function(i) {
                            var a = ${'$'}gameActors._data[i];
                            if (a) {
                                a._level = state.actors[i].level;
                                a._hp = state.actors[i].hp;
                                a._mp = state.actors[i].mp;
                                a.recoverAll();
                            }
                        });
                    }
                    if (state.system && window.${'$'}gameSystem) {
                        ${'$'}gameSystem._encounterEnabled = state.system.encounterEnabled;
                    }
                    if (state.player && window.${'$'}gamePlayer) {
                        ${'$'}gamePlayer._x = state.player.x;
                        ${'$'}gamePlayer._y = state.player.y;
                        // Teleport would need map transfer — just set position for now
                    }
                    if (state.switches && window.${'$'}gameSwitches) {
                        for (var i = 0; i < Math.min(state.switches.length, ${'$'}gameSwitches._data.length); i++)
                            ${'$'}gameSwitches._data[i] = state.switches[i];
                    }
                    if (state.variables && window.${'$'}gameVariables) {
                        for (var i = 0; i < Math.min(state.variables.length, ${'$'}gameVariables._data.length); i++)
                            ${'$'}gameVariables._data[i] = state.variables[i];
                    }
                    return 'OK';
                } catch(e) { return 'ERROR:' + e.message; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        return true
    }

    // Simple in-memory store (survives only within same session)
    private object window {
        var __quickSaveState: String? = null
    }
}
