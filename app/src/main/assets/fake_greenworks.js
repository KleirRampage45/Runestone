// Runestone: Fake greenworks.js for RPG Maker MV/MZ games
// Some games call greenworks.init() to check Steam integration.
// This fake returns success without requiring Steam.

(function() {
    var greenworks = {};

    greenworks.init = function() {
        console.log('[Runestone] greenworks.init() faked: success');
        return true;
    };

    greenworks.initAPI = function() {
        console.log('[Runestone] greenworks.initAPI() faked: success');
        return true;
    };

    greenworks.getSteamId = function() {
        return { steamId: '00000000000000000' };
    };

    greenworks.isGameOverlayEnabled = function() {
        return false;
    };

    if (typeof window !== 'undefined') {
        window.greenworks = greenworks;
    }

    console.log('[Runestone] Fake greenworks.js loaded');
})();
