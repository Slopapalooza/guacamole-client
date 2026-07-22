#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""Build the Connection Bar extension: an mstsc-style hovering bar.

v5 (2026-07-22): Windows-style "Identify" overlays - opening the panel,
committing an arrangement, or reverting to Auto flashes each window's
screen number as a large overlay in that window (broadcast over the ext
side channel).

v4 (2026-07-22): bar 30% wider, icon spacing doubled; panel drag locked
to horizontal (vertical arrangement disabled in the multi-monitor client changes after
cut-off rendering with vertical offsets).

v3 (2026-07-21): buttons hard-reset (Guacamole's stock button CSS was
inflating them and left-aligning the icons); bar regrouped for balance
(pin / +screen / arrange on the left, fullscreen / close on the right,
name centered); arrangement panel reworked - every screen is draggable
including screen 1, boxes are clearly numbered, and screens slide out of
the way live as a dragged screen crosses their midpoint (no overlap).

Bar layout (top-center of every session window):

    [pin] [+screen] [arrange]      connection name      [fullscreen] [X]

  - "+ screen"  : primary window only; reuses the client controller's own
                  addMonitor()/showAddMonitor() logic (multi-monitor build).
  - arrange     : primary only, shown with 2+ monitors. Windows-style
                  display panel; drag numbered boxes to reorder screens
                  left/right (live reflow) and offset them vertically.
                  Uses the guacManageMonitor arrangement APIs.
                  NOTE: whichever screen ends up leftmost becomes the
                  remote session's PRIMARY monitor (taskbar may move) -
                  inherent to how the layout reaches the RDP server.
                  Monitors cannot stack in one column (guacd packs them
                  side by side); "Auto" returns to follow-the-windows.
  - fullscreen  : toggles fullscreen for this window.
  - close       : primary = disconnect; secondary = window.close().
  - Minimize deliberately absent: no browser API can minimize a window.

Output: connection-bar.jar next to this script. Deploy by copying into
guac-home/extensions/ and restarting the guacamole container.
"""
import json, os, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "connection-bar.jar")

NAMESPACE = "connection-bar"

MANIFEST = {
    "guacamoleVersion": "*",
    "name": "Connection Bar",
    "namespace": NAMESPACE,
    "js": ["connection-bar.js"],
    "css": ["connection-bar.css"],
    "html": ["connection-bar.html"],
}

# HTML patch: inject the bar as the last child of .client-view-content,
# which exists in both client.html and secondaryMonitor.html.
HTML_PATCH = """<meta name="after-children" content=".client-view-content">
<connection-bar></connection-bar>
"""

JS = r"""/*
 * Connection Bar - mstsc-style hovering bar for Guacamole sessions.
 * Injected into the client and secondaryMonitor templates by the HTML patch
 * in this extension. Runs before Angular bootstraps, so the directive is a
 * first-class member of the 'client' module.
 */
angular.module('client').directive('connectionBar', ['$injector',
        function connectionBar($injector) {

    // Required services
    var guacFullscreen    = $injector.get('guacFullscreen');
    var guacManageMonitor = $injector.get('guacManageMonitor');

    // Monochrome line-art icons (stroke follows button text color)
    var SVG_ATTRS = ' viewBox="0 0 24 24" fill="none" stroke="currentColor"'
            + ' stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"';

    var SVG_PIN =
          '<svg' + SVG_ATTRS + '>'
        +     '<path d="M9 3h6v6l2 3H7l2-3V3z"/>'
        +     '<path d="M12 12v8"/>'
        + '</svg>';

    var SVG_MONITOR =
          '<svg' + SVG_ATTRS + '>'
        +     '<rect x="3" y="4" width="18" height="12" rx="1.5"/>'
        +     '<path d="M9 20h6M12 16v4"/>'
        + '</svg>';

    var SVG_ARRANGE =
          '<svg' + SVG_ATTRS + '>'
        +     '<rect x="2" y="5" width="11" height="9"/>'
        +     '<rect x="15" y="8" width="7" height="6"/>'
        +     '<path d="M5 18h14"/>'
        + '</svg>';

    var SVG_FULLSCREEN =
          '<svg' + SVG_ATTRS + '>'
        +     '<rect x="2" y="2" width="20" height="20"/>'
        +     '<path d="M10.2 10.2 6 6M6 9.2V6h3.2"/>'
        +     '<path d="M13.8 10.2 18 6M14.8 6H18v3.2"/>'
        +     '<path d="M10.2 13.8 6 18M6 14.8V18h3.2"/>'
        +     '<path d="M13.8 13.8 18 18M14.8 18H18v-3.2"/>'
        + '</svg>';

    var SVG_CLOSE =
          '<svg' + SVG_ATTRS + ' stroke-width="2">'
        +     '<path d="M6 6l12 12M18 6L6 18"/>'
        + '</svg>';

    var directive = {
        restrict : 'E',
        replace  : true,
        scope    : false,
        template :
              '<div class="connection-bar" ng-class="{pinned: cbBar.pinned, \'arrange-open\': cbBar.arrangeOpen}">'
            +     '<div class="cb-bar">'
            +         '<div class="cb-bar-side cb-bar-side-left">'
            +             '<button class="cb-bar-pin" ng-class="{active: cbBar.pinned}" '
            +                     'ng-click="cbBar.pinned = !cbBar.pinned" '
            +                     'title="Pin the connection bar">' + SVG_PIN + '</button>'
            +             '<button class="cb-bar-add" ng-if="cbBar.canAddMonitor()" '
            +                     'ng-class="{disabled: disableAddMonitor}" '
            +                     'ng-click="addMonitor()" title="Add a screen">'
            +                     '<span class="cb-plus">+</span>' + SVG_MONITOR + '</button>'
            +             '<button class="cb-bar-arrange" ng-if="cbBar.canArrange()" '
            +                     'ng-class="{active: cbBar.arrangeOpen}" '
            +                     'ng-click="cbBar.toggleArrange()" '
            +                     'title="Arrange screens">' + SVG_ARRANGE + '</button>'
            +         '</div>'
            +         '<span class="cb-bar-name">{{ cbBar.name }}</span>'
            +         '<div class="cb-bar-side cb-bar-side-right">'
            +             '<button class="cb-bar-fullscreen" ng-click="cbBar.toggleFullscreen()" '
            +                     'title="Toggle fullscreen">' + SVG_FULLSCREEN + '</button>'
            +             '<button class="cb-bar-close" ng-click="cbBar.close()" '
            +                     'title="Disconnect / close this window">' + SVG_CLOSE + '</button>'
            +         '</div>'
            +     '</div>'
            +     '<div class="cb-arrange-panel" ng-if="cbBar.arrangeOpen">'
            +         '<div class="cb-arrange-canvas"></div>'
            +         '<div class="cb-arrange-footer">'
            +             '<span>Drag screens left or right to reorder &mdash; the leftmost becomes the session\'s primary</span>'
            +             '<button ng-if="cbBar.isManual()" ng-click="cbBar.resetArrange()" '
            +                     'title="Follow the browser window positions again">Auto</button>'
            +         '</div>'
            +     '</div>'
            + '</div>'
    };

    directive.link = function linkConnectionBar(scope, element) {

        var bar = scope.cbBar = {
            pinned      : true,
            arrangeOpen : false,
            name        : document.title
        };

        // A monitor id other than 0 means this window is a secondary monitor
        var isSecondary = !!guacManageMonitor.monitorId
                && guacManageMonitor.monitorId != 0;

        // True while a box in the arrangement panel is being dragged, so
        // layout updates from guacd do not re-render mid-drag
        var dragActive = false;

        // Gap between packed boxes in the panel, in pixels
        var BOX_GAP = 3;

        // Side channel for sharing the connection name between the windows of
        // this connection. Scoped by the same channel id as the multi-monitor
        // broadcast channel; absent on builds without getChannelId().
        var channelId = guacManageMonitor.getChannelId
                && guacManageMonitor.getChannelId();
        var extChannel = (window.BroadcastChannel && channelId)
                ? new BroadcastChannel('guac_monitors_' + channelId + '_ext')
                : null;

        if (!isSecondary) {

            // Primary: the bar shows the focused client's name, announced to
            // secondary windows whenever it changes or one of them asks
            scope.$watch('focusedClient.name', function nameChanged(name) {
                bar.name = name || document.title;
                if (extChannel)
                    extChannel.postMessage({ name: bar.name });
            });

            if (extChannel)
                extChannel.onmessage = function extMessage(e) {
                    if (e.data.hello)
                        extChannel.postMessage({ name: bar.name });
                };

        }

        else if (extChannel) {

            // Secondary: ask the primary window for the connection name
            extChannel.onmessage = function extMessage(e) {
                if (e.data.name) {
                    bar.name = e.data.name;
                    scope.$applyAsync();
                }
                if (e.data.identify) {
                    var own = e.data.identify[String(guacManageMonitor.monitorId)];
                    if (own != null)
                        showIdentify(own + 1);
                }
            };
            extChannel.postMessage({ hello: true });

        }

        /**
         * Whether the "+ screen" button applies: primary window only, and
         * only when the connection supports secondary monitors. Reuses the
         * client controller's own showAddMonitor() logic (scope: false).
         *
         * @returns {boolean}
         *     true if the add-screen button should be shown.
         */
        bar.canAddMonitor = function canAddMonitor() {
            return !isSecondary && !!scope.showAddMonitor
                    && scope.showAddMonitor();
        };

        /**
         * Whether the arrangement panel applies: primary window, arrangement
         * APIs present (v4+ build), and more than one monitor open.
         *
         * @returns {boolean}
         *     true if the arrange button should be shown.
         */
        bar.canArrange = function canArrange() {
            return !isSecondary && !!guacManageMonitor.getMonitorsInfos
                    && guacManageMonitor.getMonitorCount() > 1;
        };

        /**
         * Whether a manual arrangement is active.
         *
         * @returns {boolean}
         *     true if a manual arrangement overrides window positions.
         */
        bar.isManual = function isManual() {
            return !!guacManageMonitor.isManualLayout
                    && guacManageMonitor.isManualLayout();
        };

        bar.toggleArrange = function toggleArrange() {
            bar.arrangeOpen = !bar.arrangeOpen;
            if (bar.arrangeOpen) {
                window.setTimeout(renderPanel, 50);
                broadcastIdentify();
            }
        };

        bar.resetArrange = function resetArrange() {
            guacManageMonitor.clearManualLayout();
            window.setTimeout(renderPanel, 50);
            window.setTimeout(broadcastIdentify, 800);
        };

        bar.toggleFullscreen = function toggleFullscreen() {
            guacFullscreen.setFullscreenMode(
                    !guacFullscreen.isInFullscreenMode());
        };

        /**
         * Closes this window: secondary monitors simply close (the primary
         * is notified through the existing beforeunload handler); the
         * primary disconnects via the same path as the menu action.
         */
        bar.close = function close() {

            if (isSecondary) {
                window.close();
                return;
            }

            if (scope.disconnect)
                scope.disconnect();

        };

        /**
         * Flashes a large identifying number in this window, mirroring the
         * "Identify" behavior of Windows display settings.
         *
         * @param {Number} num
         *     The screen number to display.
         */
        function showIdentify(num) {

            var overlay = document.createElement('div');
            overlay.className = 'cb-identify';
            overlay.textContent = String(num);
            document.body.appendChild(overlay);

            window.setTimeout(function() { overlay.style.opacity = '0'; }, 2200);
            window.setTimeout(function() { overlay.remove(); }, 2800);

        }

        /**
         * Shows the identify overlay in every window of this connection:
         * flashes this window's number and broadcasts the id-to-slot map so
         * secondary windows flash theirs.
         */
        function broadcastIdentify() {

            if (!guacManageMonitor.getMonitorsInfos)
                return;

            var map = guacManageMonitor.getMonitorsInfos().map;

            if (extChannel)
                extChannel.postMessage({ identify: map });

            var own = map[String(guacManageMonitor.monitorId)];
            if (own != null)
                showIdentify(own + 1);

        }

        /**
         * Draws the arrangement panel: one numbered box per monitor, scaled
         * to fit, positioned by the current layout. Every box is draggable,
         * including screen 1. Numbers are left-to-right slot order, which is
         * also how the remote session numbers its displays.
         */
        function renderPanel() {

            var canvas = element[0].querySelector('.cb-arrange-canvas');
            if (!canvas || dragActive)
                return;

            canvas.innerHTML = '';
            canvas.classList.remove('live');

            var infos = guacManageMonitor.getMonitorsInfos();
            var primaryTop = (infos.details[0] && infos.details[0].top) || 0;

            // Geometry per monitor, ordered by current position
            var entries = Object.keys(infos.map)
                .sort(function(a, b) { return infos.map[a] - infos.map[b]; })
                .map(function(id) {
                    var r = infos.rendered[id] || {};
                    var d = infos.details[id] || {};
                    var top = (r.top != null) ? r.top
                            : (d.top != null) ? d.top - primaryTop : 0;
                    return {
                        id  : id,
                        w   : r.width  || d.width  || 800,
                        h   : r.height || d.height || 600,
                        top : top
                    };
                });

            // Pack side by side (mirrors how guacd lays monitors out)
            var left = 0;
            entries.forEach(function(e) { e.left = left; left += e.w; });

            var tops = entries.map(function(e) { return e.top; });
            var minTop = Math.min.apply(null, tops);
            var maxBottom = Math.max.apply(null, entries.map(
                    function(e) { return e.top + e.h; }));

            var scale = Math.min(300 / left, 110 / (maxBottom - minTop));

            entries.forEach(function(e, index) {

                var box = document.createElement('div');
                box.className = 'cb-arrange-box'
                        + (e.id === '0' ? ' primary' : '');
                box.dataset.id = e.id;

                var label = document.createElement('span');
                label.className = 'cb-arrange-num';
                label.textContent = String(index + 1);
                box.appendChild(label);

                box.style.width  = Math.max(26, e.w * scale - BOX_GAP) + 'px';
                box.style.height = Math.max(20, e.h * scale - BOX_GAP) + 'px';
                box.style.left = (10 + e.left * scale) + 'px';
                box.style.top  = (10 + (e.top - minTop) * scale) + 'px';
                canvas.appendChild(box);

                attachDrag(box, canvas, scale);

            });

            // Enable slide transitions only after the initial layout has
            // painted, so boxes don't animate in from the corner
            window.requestAnimationFrame(function() {
                canvas.classList.add('live');
            });

        }

        /**
         * Returns all boxes in the canvas ordered by their current
         * horizontal centers.
         */
        function orderedBoxes(canvas) {
            return Array.prototype.slice.call(
                    canvas.querySelectorAll('.cb-arrange-box'))
                .sort(function(a, b) {
                    return (parseFloat(a.style.left) + a.offsetWidth / 2)
                         - (parseFloat(b.style.left) + b.offsetWidth / 2);
                });
        }

        /**
         * Live reflow while dragging: every box except the dragged one is
         * repacked side by side in current center order, so screens slide
         * out of the way as the dragged screen crosses their midpoint. The
         * dragged screen's slot is held open for it.
         */
        function reflow(canvas, draggedBox) {

            var left = 10;
            orderedBoxes(canvas).forEach(function(b) {
                if (b !== draggedBox)
                    b.style.left = left + 'px';
                left += b.offsetWidth + BOX_GAP;
            });

        }

        /**
         * Makes an arrangement box draggable with pointer events, with live
         * reflow of the other boxes. On drop, the arrangement is committed.
         */
        function attachDrag(box, canvas, scale) {

            box.addEventListener('pointerdown', function dragStart(down) {

                down.preventDefault();
                dragActive = true;
                box.setPointerCapture(down.pointerId);
                box.classList.add('dragging');

                var startX = down.clientX, startY = down.clientY;
                var origL = parseFloat(box.style.left);
                var origT = parseFloat(box.style.top);

                var move = function dragMove(mv) {
                    // Horizontal only: vertical arrangement is locked for
                    // now (vertical offsets caused cut-off rendering)
                    box.style.left = (origL + mv.clientX - startX) + 'px';
                    reflow(canvas, box);
                };

                var up = function dragEnd() {
                    box.removeEventListener('pointermove', move);
                    box.removeEventListener('pointerup', up);
                    box.classList.remove('dragging');
                    dragActive = false;
                    commitArrangement(canvas, scale);
                };

                box.addEventListener('pointermove', move);
                box.addEventListener('pointerup', up);

            });

        }

        /**
         * Reads the dropped box positions and applies them: horizontal
         * centers determine left-to-right slot order (any screen may take
         * any slot, including the leftmost), vertical deltas from screen
         * 1's box (snapped level within 12px) become top offsets.
         */
        function commitArrangement(canvas, scale) {

            var boxes = orderedBoxes(canvas);

            var primary = null;
            boxes.forEach(function(b) {
                if (b.dataset.id === '0') primary = b;
            });
            if (!primary)
                return;

            var primaryTop = parseFloat(primary.style.top);

            var order = [], newTops = {};
            boxes.forEach(function(b) {
                order.push(b.dataset.id);
                if (b.dataset.id !== '0')
                    newTops[b.dataset.id] = 0;
            });

            guacManageMonitor.applyArrangement(order, newTops);
            scope.$applyAsync();

            // Re-identify once the new layout has settled
            window.setTimeout(broadcastIdentify, 800);

        }

        // Re-render the panel whenever guacd confirms a new layout (and
        // close it if the monitor count drops to one)
        scope.$watch(function watchLayout() {
            if (!bar.arrangeOpen)
                return '';
            var infos = guacManageMonitor.getMonitorsInfos();
            return JSON.stringify([infos.map, infos.rendered]);
        }, function layoutChanged() {
            if (!bar.arrangeOpen)
                return;
            if (guacManageMonitor.getMonitorCount() <= 1) {
                bar.arrangeOpen = false;
                return;
            }
            window.setTimeout(renderPanel, 0);
        });

        scope.$on('$destroy', function cleanUp() {
            if (extChannel)
                extChannel.close();
        });

    };

    return directive;

}]);
"""

CSS = """/* Connection Bar - top-center hovering bar, mstsc style.
 * Pinned by default; unpinned, a 5px sliver stays visible and hovering it
 * slides the bar in. NOTE: extension CSS is aggregated into the app-root
 * app.css - any url() here would need the app/ext/connection-bar/
 * prefix. All icons are inline SVG, so no resources are required. */
.connection-bar {
    position: fixed;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    z-index: 20;
    padding-bottom: 12px; /* grace area so the bar doesn't vanish mid-reach */
    text-align: center;
    font-family: inherit;
}

.connection-bar .cb-bar {
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    align-items: center;
    gap: 1.2em;
    min-width: 26em;
    padding: 0.25em 1em;
    background: rgba(0, 0, 0, 0.78);
    color: #fff;
    font-size: 0.9em;
    /* Light outline so the bar edges stay visible over black remote
     * desktops (no top border - the bar hangs from the screen edge) */
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-top: none;
    border-radius: 0 0 0.6em 0.6em;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
    transform: translateY(calc(-100% + 5px));
    transition: transform 0.15s ease;
    white-space: nowrap;
}

.connection-bar:hover .cb-bar,
.connection-bar.pinned .cb-bar,
.connection-bar.arrange-open .cb-bar {
    transform: none;
}

.connection-bar .cb-bar-side {
    display: flex;
    align-items: center;
    gap: 0.5em;
}

.connection-bar .cb-bar-side-left  { justify-self: start; }
.connection-bar .cb-bar-side-right { justify-self: end; }

.connection-bar .cb-bar-name {
    grid-column: 2;
    justify-self: center;
    font-weight: bold;
    max-width: 22em;
    overflow: hidden;
    text-overflow: ellipsis;
}

/* Hard reset: Guacamole's stock button styling (padding, min-width, gray
 * background) otherwise inflates these buttons and left-aligns the icons */
.connection-bar .cb-bar button,
.connection-bar .cb-arrange-footer button {
    all: unset;
    box-sizing: border-box;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.15em;
    padding: 0.25em 0.4em;
    border-radius: 0.25em;
    color: #fff;
    line-height: 1;
    cursor: pointer;
}

.connection-bar .cb-bar button svg {
    width: 1.2em;
    height: 1.2em;
    display: block;
}

.connection-bar .cb-bar button .cb-plus {
    font-weight: bold;
    line-height: 1;
}

.connection-bar .cb-bar button:hover,
.connection-bar .cb-arrange-footer button:hover {
    background: rgba(255, 255, 255, 0.2);
}

.connection-bar .cb-bar button.active {
    background: rgba(255, 255, 255, 0.25);
}

.connection-bar .cb-bar .cb-bar-close:hover {
    background: #c0392b;
}

.connection-bar .cb-bar button.disabled {
    pointer-events: none;
    opacity: 0.4;
}

/* Arrangement panel */
.connection-bar .cb-arrange-panel {
    display: inline-block;
    margin-top: 4px;
    padding: 8px;
    background: rgba(0, 0, 0, 0.85);
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: 0.5em;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
    color: #fff;
}

.connection-bar .cb-arrange-canvas {
    position: relative;
    width: 320px;
    height: 130px;
    background: rgba(255, 255, 255, 0.06);
    border-radius: 0.3em;
    overflow: hidden;
}

.connection-bar .cb-arrange-box {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(47, 111, 237, 0.25);
    border: 1.5px solid #9ec1ff;
    border-radius: 3px;
    cursor: grab;
    user-select: none;
    touch-action: none;
    overflow: visible;
}

/* Screens slide aside during a drag */
.connection-bar .cb-arrange-canvas.live .cb-arrange-box {
    transition: left 0.12s ease, top 0.12s ease;
}

.connection-bar .cb-arrange-box.primary {
    background: rgba(255, 255, 255, 0.15);
    border-color: #fff;
}

.connection-bar .cb-arrange-box.dragging {
    opacity: 0.85;
    z-index: 2;
    cursor: grabbing;
    transition: none !important;
}

.connection-bar .cb-arrange-num {
    font-weight: bold;
    font-size: 1.1em;
    color: #fff;
    text-shadow: 0 0 3px #000, 0 0 6px #000;
    pointer-events: none;
}

.connection-bar .cb-arrange-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 1em;
    margin-top: 6px;
    font-size: 0.85em;
    opacity: 0.9;
}

.connection-bar .cb-arrange-footer button {
    border: 1px solid rgba(255, 255, 255, 0.5);
    padding: 0.1em 0.6em;
}

/* "Identify" overlay - large screen number flashed in each window while
 * arranging, mirroring Windows display settings */
.cb-identify {
    position: fixed;
    top: 45%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 30;
    font-size: 110px;
    font-weight: bold;
    color: #fff;
    text-shadow: 0 0 6px #000, 0 0 18px #000;
    opacity: 0.92;
    pointer-events: none;
    transition: opacity 0.5s ease;
}
"""

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as jar:
    jar.writestr("guac-manifest.json", json.dumps(MANIFEST, indent=4))
    jar.writestr("connection-bar.js", JS)
    jar.writestr("connection-bar.css", CSS)
    jar.writestr("connection-bar.html", HTML_PATCH)

print(f"built {OUT} ({os.path.getsize(OUT):,} bytes)")
for name in zipfile.ZipFile(OUT).namelist():
    print("  ", name)
