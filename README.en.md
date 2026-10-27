# Kwrg IRC

<p align="center">
  <img src="./assets/readme/hero.svg" width="100%" alt="Kwrg IRC — a SkyBlock toolkit with a built-in IRC client. Fabric · Minecraft 26.1.2 · Java 25">
</p>

<p align="center">
  <a href="./README.md"><img alt="中文" src="https://img.shields.io/badge/%E4%B8%AD%E6%96%87-26384A?style=flat-square"></a>
  <a href="./README.en.md"><img alt="English" src="https://img.shields.io/badge/English-4FC3F7?style=flat-square"></a>
  &nbsp;
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.1.2-F2A65A?style=flat-square">
  <img alt="Fabric" src="https://img.shields.io/badge/Loader-Fabric-F2A65A?style=flat-square">
  <img alt="Java" src="https://img.shields.io/badge/Java-25-4FC3F7?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-78909C?style=flat-square">
</p>

**Kwrg is a Fabric client mod.** It puts the everyday SkyBlock chores — marking spots, running routes, keeping an eye on people, checking status — and a self-hosted IRC client into one hand-drawn interface.

None of it is a retexture of vanilla screens. The radial menu, the three-level panels, the dynamic island, the beams and route lines in the world: the mod draws all of it itself.

---

<p align="center">
  <img src="./assets/readme/hero.png" width="100%" alt="Kwrg key visual">
</p>

---

## At a glance

| What you want | What Kwrg gives you | Modules |
| --- | --- | --- |
| Talk to friends in-game | IRC with channels, nicknames, emoji, capes, glow | `channel` `customCape` `glowColor` |
| Mark spots, run routes | Waypoint sets, a route editor, a guided path | `waypoint` `route` |
| Keep track of people | Target list, party auto-tracking, on-screen radar | `playerFinder` |
| Fix the interface | Dynamic-island HUD, tab and scoreboard reskin, fonts, theme | `dynamicIsland` `dynamicTab` `dynamicScoreboard` `font` `guiStyle` |
| Look different | Nickname and Hypixel rank spoofing (client-side) | `Nick` |
| Change your outfit | Custom capes, model scale, hidden armor | `customCape` `modelScale` `hideArmor` |

---

## What it is

Kwrg cares about two things.

**One: putting chat inside the game.** The mod talks to an IRC service over a custom JSON protocol — TCP, newline-delimited. It is not only messages: channels, emoji shortcodes, capes, glow, hidden armor, model scale, typing state and important-player toasts all travel the same link. These states are decided by the client and broadcast; the server just relays them.

**Two: treating the interface as a real rendering problem.** The ClickGUI sits on owo-lib, but the anti-aliased geometry, the opacity pipeline, the rounded corners and shadows, and the hot-swapped font are all written here. The dynamic island, the tab overlay and the scoreboard are not textures — they take over drawing inside vanilla's own render flow.

> Fabric Loader ≥ 0.19.3 · Minecraft 26.1.2 · Java 25 · 36 modules · 34 mixin hooks · ~28,500 lines of Java

<p align="center">
  <img src="./assets/readme/mascot.png" width="220" alt="The Kwrg mascot">
</p>

---

<p align="center">
  <img src="./assets/readme/section-irc.svg" width="100%" alt="IRC — JSON over TCP, 5 root commands, 12 managers">
</p>

### In one line

The chat box is more than a chat box. After `/chat irc`, **every line you type becomes an IRC message**; any other `/chat` leaves the mode.

```
/chat irc        → subsequent chat input goes to IRC
/ic <message>    → send just this one
/icall <message> → force the public hall
/ci              → flip between saved channel and public hall
```

<p align="center">
  <img src="./assets/readme/shot-irc.png" width="100%" alt="IRC chat window">
</p>

### Commands

| Command | What it does |
| --- | --- |
| `/ci` · `/ci all` · `/ci channel [name]` | Switch channel / back to public / join a channel |
| `/ic <message>` · `/icall <message>` | Send once / force public hall |
| `/irc coords` | Send your position, island and server |
| `/irc rank <tag>` · `/irc nameStyle` · `/irc messageStyle` | Tag and text styling |
| `/irc play <player> <item>` | Play an item or totem animation for someone |
| `/irc important [player]` | Watchlist: big on-screen title when they speak |
| `/irc online` | Online list |
| `/irc config load \| create \| save` | Config profiles |
| `/irc share` | Send config, styles or a channel invite |
| `/irc routeEdit` | Toggle Alt + left-click waypoint insertion |
| `/irc gui` · `/irc hud` | Open the interface / HUD editor |

### Details that are not obvious

**The protocol is "chat by default, type when needed".** There are 23 message types. Chat is the only one with no `type` field — a receiver that finds no `type` treats it as chat; everything else declares itself.

**Heartbeat and latency measurement are the same packet.** One `ping` every 15 seconds; the reply is not fully deserialized, and the RTT feeds both the HUD and `/irc debug ping`. Up to three reconnects, and a successful reconnect **replays the whole state** once: join → hidden armor → model scale → cape → channel → online query → heartbeat → watchlist.

**Capes are a tiny content-addressed CDN.** Pick a cape, hash it with SHA-256, ask the server whether it already has that image; only upload the base64 if it does not, then broadcast. Peers download by hash when they miss. Animation parameters are not stored as metadata — they are encoded into the cache filename: `<hash>@frames#fps.png`.

**Visual powers live in the render layer, never in the data.** Nickname and rank spoofing happens at the font layer (it first eats the old prefix painted on the player, then redraws). Hidden armor works by making `getItemBySlot` return empty. Glow goes through `isCurrentlyGlowing` and `getTeamColor`. The server sees none of it, and a disconnect restores everything.

**Coordinates in chat can become a path.** When a message contains `x: 1 y: 2 z: 3` (or a bare triple), a `[show waypoints]` button is appended. Clicking it spawns a beam plus a guide line from an async A\* run; walk up to it for three seconds and it clears itself.

**1024 emoji shortcodes.** Type `:skull:`, `:joy:`, `:fire:` — replaced with Unicode before sending, in both IRC and public chat.

---

<p align="center">
  <img src="./assets/readme/section-toolkit.svg" width="100%" alt="Toolkit — waypoints, routes, navigator">
</p>

### Waypoints and routes

Two different things, managed by one two-page screen:

- **Waypoint sets** — a bag of coordinates, filterable by island. The default set is protected and capped at 64 points.
- **Routes** — an ordered node chain, with an optional line joining them. A Kuudra pearl route (57 nodes) ships with the mod and is unpacked on first launch.

Writes are atomic: a temp file is replaced into place, and the previous content on disk is compared first — **if the file was modified externally, the save is refused** instead of silently overwriting your hand edits.

```
/route show                 list nodes
/route add <name> [x y z]   append (position defaults to where you stand)
/route ins <name> <a> <b>   insert between adjacent indices
/route del <index>          remove
```

With `/irc routeEdit` on, **Alt + left-click a block** to drop a node right there.

### Pathfinding is not a straight line

Chat coordinates, waypoints and routes all share one weighted A\*:

- Cost is `f = g + h × weight`, weight tunable from 0.8 to 2.2 (the `navigatorWeight` module).
- One search keeps seven best-effort tiers; when the queue empties it returns the candidate that got furthest instead of failing outright.
- Teleport edges are cast along 98 minimal directions, up to 61 blocks, costing `distance × 1.6 + 12`; only considered while grounded, and capped at 128 casts per search.
- Walked paths land in a favor table (4096 entries) that discounts the same area next time.
- Smoothing runs only on **non-teleport** segments — interpolating a teleport would draw a 60-block jump as a curve.

Direct connections are tested before searching: if both ends are standable and the line is clear, you get a two-point path and no search at all.

<p align="center">
  <img src="./assets/readme/shot-route.png" width="100%" alt="Routes and guided path">
</p>

---

<p align="center">
  <img src="./assets/readme/section-interface.svg" width="100%" alt="Interface — HUD, themes, custom font">
</p>

### A hand-drawn GUI

Press **right Shift** (default) and a radial menu opens: five categories on a ring, 500 ms easing. Pick one and its button slides aside while the second-level panel grows out of that button's center; right-click a module and a third-level settings panel grows out of the row.

<p align="center">
  <img src="./assets/readme/shot-hud.png" width="100%" alt="HUD and interface">
</p>

### The theme is not a table of constants

The whole palette has a single input: the panel background. Everything else is derived —

- card background is the panel color one step brighter, outlines derived from it;
- "raised surfaces" (badges, list rows) are not hardcoded; the channel deltas of the calibration color against the default background are **inverted onto the current one**, so a light theme shifts the right way automatically;
- whether text should be dark or light is not decided by a threshold but by measuring both candidates against the panel color and the derived card color and **taking the worse contrast, then comparing**;
- changing the color lerps twenty-odd tones over 180 ms, so a theme change is a transition rather than a flash.

### Anti-aliasing is hand-rolled

No textures involved. Rounded corners, borders, circles, arcs and lines all go through one geometry pipeline:

- a 1×1 quad is scaled by the pose into an arbitrarily **sub-pixel** rectangle;
- a rounded panel is 3 rectangles plus 4 quarter-discs;
- a 1 px border is feathered with **two rings**: an inner gradient from transparent to solid, and an outer solid core;
- a soft shadow is a solid rounded core plus four edge gradients plus four radial ring gradients.

Opacity is not multiplied per draw call either: it returns a graphics proxy that rewrites the alpha channel at the last moment, so a whole component subtree can fade without touching any drawing code.

### Fonts are hot-swapped

Scan `kwrg/font/` for ttf/otf, reflect into the font manager, build a TrueType provider on the spot and swap the font set — **without reloading resource packs or restarting the game**. A failed swap keeps the previous font.

The HUD uses a different one: `kwrg:island` is bundled (size 8.0 / oversample 4.0) and used only by the dynamic island and the scoreboard.

### HUD elements are anchored, not positioned

Final position = `anchor → margin → offset → clamped to the screen`.

Dragging in the editor writes **only the offset**. That is why "reset" simply zeroes it, and can animate the element back from wherever it was. Snapping considers the center line, left/right and top/bottom, taking the nearest target within 6 px; hold Ctrl to disable it.

Three elements: the dynamic island (off by default, 20% opacity — FPS, IRC latency, server latency, TPS, RAM, GPU, CPU), the notification stack, and the dynamic scoreboard.

**The tab panel is measured, not guessed.** Vanilla's is measured by redirecting `fill`; if Skyblocker is present its visible widgets are read reflectively and unioned; if SkyHanni is present the bounds are estimated from row and column counts. All three paths converge on one boundary, and the dynamic island draws the background, clip and offset — continuing to render after the key is released until the animation finishes.

### Notifications

Four types, each with its own glyph and accent. Dwell time is derived from length: `2500 + characters × 60` ms, clamped to 2.5–8 s. Hovering pauses the countdown; leaving resumes it.

---

## Structure

<p align="center">
  <img src="./assets/readme/diagram-structure.svg" width="100%" alt="Structure: vanilla surfaces rewritten by 34 injection points, 36 modules driving the interface, IRC and local files in parallel">
</p>

Top to bottom: the vanilla surfaces that get rewritten → 34 mixin hooks → 36 modules → the client surfaces. The bottom two run in parallel: the IRC link (TCP, newline JSON) and the local `kwrg/` directory.

---

## Install

Requires **Minecraft 26.1.2**, **Fabric Loader ≥ 0.19.3**, **Java 25**.

1. Install Fabric Loader.
2. Drop the jar into `mods/`.
3. Launch the game.

The Hypixel Mod API is bundled — you do not install it separately.

On first launch a `kwrg/` directory appears in your game folder, holding config, routes, waypoint sets, fonts and the cape cache.

Open the interface with **right Shift** or `/irc gui`.

### Build from source

```bash
./gradlew build
# artifacts land in build/libs/
```

---

## Modules

<details>
<summary><b>All 36 modules</b> (click to expand)</summary>

| Category | Module | What it does |
| --- | --- | --- |
| STYLE | `dynamicIsland` | Dynamic-island HUD: FPS, latency, TPS, RAM, GPU, CPU |
| STYLE | `dynamicTab` | Tab panel reskin, optional follow of the island |
| STYLE | `dynamicScoreboard` | Scoreboard reskin, optional hidden scores |
| STYLE | `rank` | IRC tag |
| STYLE | `nameStyle` | Name styling (rainbow, gradients, color codes) |
| STYLE | `messageStyle` | Message body styling |
| STYLE | `chat` | Sound on incoming message (7 choices, baby cat by default) |
| STYLE | `font` | GUI font switching |
| STYLE | `guiStyle` | GUI accent color and opacity |
| STYLE | `GuiScale` | GUI scale 50%–200% |
| STYLE | `hudEditor` | HUD layout editor |
| FUNCTIONS | `channel` | IRC channel listening and send target |
| FUNCTIONS | `customCape` | Custom cape: file, canvas size, animation fps |
| FUNCTIONS | `glowColor` | Glow for online IRC players, with color |
| FUNCTIONS | `hideArmor` | Hide armor per slot |
| FUNCTIONS | `modelScale` | Your own model scale |
| FUNCTIONS | `Nick` | Nickname and rank spoofing |
| FUNCTIONS | `chatSuffix` | Prefix/suffix flair on public chat |
| FUNCTIONS | `importantPlayer` | Watchlist: big title when they speak |
| FUNCTIONS | `playerFinder` | Target list, party auto-tracking, on-screen radar |
| FUNCTIONS | `online` | IRC online list with avatars |
| FUNCTIONS | `waypoint` | Waypoint sets |
| FUNCTIONS | `route` | Routes |
| CONFIG | `load` | Config profiles: new, switch, delete, clipboard import/export |
| CONFIG | `share` | Send config, styles or a channel invite to a player |
| DEBUG | `connect` `disconnect` | Connect / disconnect IRC |
| DEBUG | `ping` | Measure IRC latency |
| DEBUG | `disableBlur` | GUI blur toggle and quality |
| DEBUG | `FPSLimit` | GUI frame limiter |
| DEBUG | `navigatorWeight` | A\* heuristic weight 0.8–2.2 |
| DEBUG | `DynamicIslandAAValve` | Island anti-aliasing factor |
| DEBUG | `clearIndexCache` | Clear the item index cache |
| DEBUG | `reloadRepo` | Reload the item repository |
| DEBUG | `clearCapeCache` | Clear the cape cache |
| PLAY | `play` | Play an item or totem animation for a player |

</details>

---

## FAQ

**Some features overlap with other mods — is that a problem?**  
The overlap is surface-level. Kwrg's tab and scoreboard reskin **takes over vanilla drawing** rather than layering on top, so it coexists with Skyblocker or SkyHanni: it measures their layout first, then draws in that area.

**Can editing a config file break things?**  
Config files go through a migrator: old fields are migrated, and when unknown fields appear the original file is backed up to `kwrg/backUp/` before anything is touched.

**What if another tool edited my waypoint file?**  
Content on disk is compared before writing. If it changed externally, the save is refused with a message rather than quietly overwriting your hand edits.

---

## Credits

<p align="center">
  <img src="./assets/readme/mod-icon.png" width="88" alt="Kwrg icon">
</p>

<p align="center">
  <b>cow76192</b> · <b>2vnailong88</b><br>
  <sub>Mod metadata lists: Grwk, Kwrg</sub>
</p>

<p align="center">
  MIT License · <a href="./LICENSE">LICENSE</a>
</p>

<p align="center">
  <sub>w kwrg!</sub>
</p>