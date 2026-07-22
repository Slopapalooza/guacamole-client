# Apache Guacamole Client — multi-monitor fork

Fork of [apache/guacamole-client](https://github.com/apache/guacamole-client).
The default branch, **`multimon-1.6.0`**, is the 1.6.0 release plus
multi-monitor RDP support, a drag-to-arrange display panel, and a set of
fixes, developed and tested against a real deployment. The companion proxy
fork is
[guacamole-server](https://github.com/Slopapalooza/guacamole-server) —
both are required.

Upstream's original build documentation is in the plain [`README`](README)
file.

## What this fork adds

### Multi-monitor support (GUACAMOLE-288)

Backport of upstream draft PR
[apache/guacamole-client#1061](https://github.com/apache/guacamole-client/pull/1061)
(author: Corentin Soriano) onto the 1.6.0 release tag, with three trivial
conflict resolutions and a fix for the `start` instruction handler parsing
its x coordinate from the layer-index parameter. Each monitor is a separate
browser window (`Ctrl+Alt+Shift → Multi-screen → Add an additional screen`);
the primary window owns the only WebSocket, and secondary windows replay
instructions over a `BroadcastChannel`.

### Fixes and rework on top of the backport

Found by code review and pilot testing:

- **Popup-blocked windows no longer leak a monitor slot** — previously each
  blocked `window.open` permanently consumed a slot until page reload.
- **Per-connection BroadcastChannel** (`guac_monitors_<uuid>`, id inherited
  by secondary windows via the route, now
  `/secondaryMonitor/:id/:channel`) — two connections open in the same
  browser previously cross-fed each other's input and display streams.
- The monitor-size clamp in `Display.resize()` applies only to the default
  layer; buffers and sublayers keep their requested dimensions.
- Non-display instructions (audio, file, pipe, argv, …) are not replayed
  into secondary windows — fixes duplicate audio playback, cuts replay
  overhead.
- Vertical offset math no longer takes `Math.abs` of the primary window's
  screen position (broke layouts with a monitor above the OS primary).
- `guacManageMonitor.init()` is idempotent — no more leaked
  `BroadcastChannel`s and poll intervals on route re-entry.
- Monitors close only after 8 consecutive absences from layout updates,
  tolerating transient layouts emitted while a monitor is being added.
- **Monitors are ordered by actual window screen position** (left, then
  top) instead of opening order — rearrange monitors by dragging the
  browser windows. Any window may occupy any slot, including the leftmost;
  slot 0 carries the RDP primary-monitor flag.
- **Manual arrangement APIs** (`applyArrangement` / `clearManualLayout` /
  `getMonitorsInfos` / `getChannelId`) supporting an extension-provided
  arrangement panel; manual mode overrides window-position ordering until
  cleared.
- **Secondary windows render a full-canvas replica** of the combined
  display, shifted into view with CSS, instead of cropping at draw time —
  `copy` instructions whose source lies in another monitor's slice always
  have valid source data (fixes black-rectangle artifacts).
- Vertical arrangement is locked (top offsets always 0) pending upstream's
  vertical-reordering work.
- Resize-request debouncing with remote-echo suppression, and validation of
  the `multimon-layout` parameter before use (both from
  [ciroiriarte's fix set](https://github.com/corentin-soriano/guacamole-client/pull/3),
  cherry-picked with original authorship).

### Connection-bar extension

[`extensions/connection-bar/`](extensions/connection-bar/) builds a
Guacamole extension jar (self-contained Python script, not part of the Maven
build) providing an mstsc-style hovering bar in every session window:

- pin toggle (pinned by default), connection name centered and synced to
  secondary windows;
- add-screen button (primary window, respects the connection's monitor
  limit);
- **drag-to-arrange display panel** — numbered boxes with live reflow, any
  screen draggable including screen 1, Windows-style "Identify" number
  overlays flashed in each window, and an Auto button returning to
  follow-the-window-positions mode;
- fullscreen toggle and close/disconnect;
- no minimize button — browsers expose no API to minimize a window.

### Upstream fixes cherry-picked from `main`

Post-1.6.0 fixes from upstream, applied with `git cherry-pick -x`: crash on
zero-size cursor instructions (#1199), the full `Keyboard.js` chain — macOS
Caps Lock (GUACAMOLE-1823), AltGr handling (#1203 and its precursor), Caps
Lock synchronization (#1164), Cmd/Meta click modifiers (#1155), UTF-8
surrogate pairs (#999) — mouse events lost after browser blur/focus (#1169),
mouse-state correctness (#1202, #1162), clipboard synchronization before
paste (#1132), `BlobWriter` upload race (#1194), stream-index reuse delay
(#1147), and cache-busting for JavaScript resources (#1152).

## Deployment notes

- Requires the companion
  [guacamole-server fork](https://github.com/Slopapalooza/guacamole-server);
  connections must be RDP with `resize-method=display-update` and
  `secondary-monitors` ≥ 1.
- The aggregated extension JS/CSS (`app.js`/`app.css`) is cache-busted only
  per WAR build — after an extension-only update, serve those two paths with
  `Cache-Control: no-cache` from your reverse proxy (or hard-refresh).
- This branch tracks a moving upstream draft; expect it to be superseded
  when GUACAMOLE-288 merges.

## License

[Apache License 2.0](LICENSE), same as upstream. The multi-monitor backport
is derived from the upstream draft PR by Corentin Soriano; cherry-picked
commits retain their original authorship.
