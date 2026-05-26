// Runestone: Bootstrap loader for RPG Maker MV/MZ
// Detects WebGL and WebAudio support, then loads the game
(function() {
    var RunestoneBootstrap = {
        webglSupported: false,
        webaudioSupported: false,

        detect: function() {
            // WebGL detection
            try {
                var canvas = document.createElement('canvas');
                var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
                this.webglSupported = !!gl;
                if (gl) {
                    var debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
                    console.log('[Runestone] WebGL supported' +
                        (debugInfo ? ' (' + gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) + ')' : ''));
                }
            } catch(e) {
                console.log('[Runestone] WebGL not available:', e.message);
            }

            // WebAudio detection
            try {
                var ctx = new (window.AudioContext || window.webkitAudioContext)();
                this.webaudioSupported = !!ctx;
                ctx.close();
                console.log('[Runestone] WebAudio supported');
            } catch(e) {
                console.log('[Runestone] WebAudio not available:', e.message);
            }

            return {
                webgl: this.webglSupported,
                webaudio: this.webaudioSupported
            };
        },

        boot: function(useWebgl, useWebaudio) {
            var caps = this.detect();
            var actualWebgl = useWebgl !== false && caps.webgl;
            var actualWebaudio = useWebaudio !== false && caps.webaudio;

            // Notify native side about capabilities
            if (window.RunestoneBridge) {
                window.RunestoneBridge.boot(actualWebgl, actualWebaudio);
            }

            console.log('[Runestone] Booting with WebGL=' + actualWebgl + ' WebAudio=' + actualWebaudio);

            // If the game hasn't loaded yet, it will check for WebGL support
            // via Modernizr or similar. We override the check here.
            if (typeof Modernizr !== 'undefined') {
                Modernizr.webgl = actualWebgl;
            }
        }
    };

    window.RunestoneBootstrap = RunestoneBootstrap;

    // Auto-detect and boot when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            RunestoneBootstrap.boot(true, true);
        });
    } else {
        RunestoneBootstrap.boot(true, true);
    }

    console.log('[Runestone] Bootstrap loaded');
})();
