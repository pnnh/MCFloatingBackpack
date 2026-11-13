# MCFloatingBackpack

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.251+-orange.svg)](https://neoforged.net/)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3_or_later-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://openjdk.org/projects/jdk/21/)

**English** | [简体中文](README_CN.md)

A floating backpack mod for **Minecraft 1.21.1 + NeoForge**. Like the bag windows of an MMO, a draggable, resizable panel floats above the game screen and stays there when you open a chest, the inventory or another mod's backpack screen, so you can move items between them directly.

> **Status**: early development (version 0.1.0), not released yet. The features are fully usable, but interfaces, save formats and the network protocol may still change before a release. **Back up your world before updating the mod.**

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 or newer |
| Java | 21 |
| Must be installed on | **both the client and the server** (the server stores the contents) |

## Features

- **Floating panel**: toggle it with `B` (rebindable in Controls), or by right-clicking with the backpack in hand. The panel can be dragged and resized; its position and size are remembered. Sophisticated Backpacks and Traveler's Backpack also default to `B` for their own backpack screens; if you use them together, rebind whichever key you like in Controls.
- **Lives with other screens**: it stays visible and clickable over the vanilla inventory, chests and other container screens; when no other screen is open it sits on a transparent screen of its own.
- **Five tiers**: leather 36 slots, iron 54, gold 72, diamond 90, netherite 108. The leather backpack is crafted from leather, string, a chest and iron ingots; higher tiers are upgraded at the **smithing table** with the matching material block and trim template. Upgrading keeps all contents.
- **Three tool areas**: a column on each side of the main area and a row below it hold "tools":
  - left: **auto pickup** (picked-up items go straight into the backpack, with a blacklist page);
  - right: **trash can** (a protected trash panel; deleted items can be taken back);
  - bottom: **tag manager** (tag items to sort them and to dump them in one go).
- **Category extensions and universal extensions**: put an extension into a tool area and the main area gains a row of 9 slots.
  - 19 **category extensions** (food, tool, weapon, armor, ore, crop, wood, potion, redstone, disc, and building, natural, plant, decor, mob drops, storage, transport, enchanting, meat & fish): their row only takes that kind of item, and each has its own tint and separator lines; what fits is decided by the data tags `mcfloatingbackpack:extension/<category>`, which data packs and other mods can add to;
  - 5 **universal extensions** (leather, iron, gold, diamond, netherite, each with its own recipe): their row takes any item, but only in a backpack of the same tier, only in the left or right area, one counted per area, so at most 2 extra rows;
  - when an extension is taken away, a row that holds items is hidden (a small yellow dot appears in the title bar) and comes back when you put it back. Nothing is lost.
- **Sort**: the sort button in the title bar (left of the magnifier) tidies the main area: stacks of the same kind are merged, items are put in registry order (`minecraft` first, then the other mods by name), and anything that fits a category extension row goes into that row first (a universal row only takes what the main cells have no room for). The tool areas and the hidden rows are not touched. It does nothing while the cursor holds an item.
- **Lantern light** (052): put a lantern, soul lantern, sea lantern, glowstone, shroomlight, froglight (any of the three), end rod or jack o'lantern into the **bottom tool area**, and the user of the backpack the `B` key would open gets a light that follows them. **This needs you to install [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) yourself (the 4.x version for 1.21.1, a client mod)**: like Traveler's Backpack, this mod does no lighting of its own, and without it nothing shines (you are told once, the first time you put a light into the bottom area). Each light has a fixed brightness **equal to what the original game gives the same block** (lantern, glowstone, sea lantern, shroomlight, jack o'lantern and froglights 15, end rod 14, soul lantern 10; a torch is 14), so a soul lantern is dimmer than a lantern; the bottom area takes only one light (replacing it in the same slot is fine); the left and right areas take one but it does nothing; torches, candles and campfires are not lights; there is no colour. Other players only see your light while the backpack is **worn** (chest slot, Curios, Accessories). No new stored data. **A backpack that already has a lantern in its bottom area starts to shine after the update for players who have LambDynamicLights.** A data pack can add other lights to the item tag `mcfloatingbackpack:tool_lights`; put `lambdynlights=OFF` in the config's `compat.modLevels` to switch this support off.
- **Mobile jukebox** (053): a new tool for the **right tool area** of the floating backpack panel (at most one in the area). With an empty cursor, left click it and the "Player" page unfolds to the right of the panel (at the same place as the trash can page; only one of the two is open at a time; the right button still picks it up). It is a minimal music player that plays the discs in the **visible "disc extension" rows** of the backpack the `B` key would open: read in the order of the rows and, within a row, left to right, **disc rows with other extension rows between them are read in full**, hidden disc rows do not count, and discs of other mods work as long as they use the vanilla jukebox playable component. Buttons: previous, play / stop, rewind (the current track again from its start), next, and two switches: repeat one and shuffle (every track once in each round); the default is to go round the list (after the last track comes the first). **There is no pause** (the game has none for a single sound, and this mod does not reach into the sound engine): playing again after a stop starts the current track from its start. The sound is in your ear, its volume follows the "Jukebox/Note Blocks" slider, the background music gives way when a track starts, and the "now playing" line is shown; closing the panel folds the page up but the music goes on, and taking the jukebox out of the right area, using another backpack or leaving the world stops it. **Other players do not hear it.** Recipe: `IDI / NJN / IEI` (iron ingots, diamond, 2 note blocks, jukebox, emerald). No new network packet; the stored data gained a kind of item (data revision 4), so back up before updating.
- **Mobile crafting table** (054): a new tool for the **right tool area** of the floating backpack panel (at most one in the area; placed in the left or bottom area it does nothing). With an empty cursor, left click it to open the **vanilla crafting table screen** (3×3 grid, recipe book; JEI / EMI recipe transfer and other mods' recipes work as usual; the floating panel stays on top). **Click it again to close it**; take it out of the right area or switch backpack and the screen closes within a second, with whatever is in the grid handed back by vanilla. You do not need to stand next to a crafting table, and nothing new is stored. Recipe: `IDI / ATB / ICI` (iron ingot, diamond, iron pickaxe, iron axe, crafting table, chest). It adds one network packet (the client tells the server which tool was clicked), so the **protocol number goes 19 → 20** and both sides must be updated; the backpack storage data gains a new kind of item (data revision 5), so back up before updating.
- **Liquid tanks** (048, the first step): 19 tanks in four tiers (leather, iron, gold, netherite; no leather lava tank) for five liquids (water, crude oil, lava, milk, honey), holding 8 / 16 / 64 / 256 buckets. A tank only counts in the bottom area, one for each liquid whatever its tier; a tank of a tier above the backpack's cannot be put into any tool area (a leather backpack takes leather tanks, an iron one iron and below, and so on; the main area takes anything). What a backpack holds belongs to the backpack, so taking a tank out leaves the liquid in it. The panel shows a level bar on a tank and its content in the tooltip. Liquid a backpack holds with no tank for it counts as hidden content: the title bar's yellow dot appears, its hint lists the liquids, and `/hidden` lists them too (`/hidden liquid` finds all of them). **For now nothing fills a tank**: the pipes that take liquid in and let it out are the next features (049, 050). The stored data gained a field (data revision 2), so back up before updating.
- **Pipes** (049): a water pipe, an oil pipe and a lava pipe (no tiers). Held in the main hand, **right click** takes a bucket (1000 mB) of its liquid from a source block (the way an empty bucket does, water surface included) into the matching tank of the backpack the `B` key would open; **left click** a block pours a bucket out of that tank, where a bucket would put it, and never mines the block. The tank must be in that backpack's bottom area (any tier). You get a chat line when a tank gets full or empty, when you try with no room or less than a bucket, and when there is no backpack or no tank; the same line is not repeated within a second. Holding the left button pours at most every few ticks. Crude oil means any fluid in `#c:crude_oil` (PneumaticCraft: Repressurized's oil is one); the tank stays locked to the first oil put in until it is empty. **Sneak + right click** (on anything, any of the five pipes) fills an empty container from the tank: it uses up an empty bucket (an empty glass bottle for honey) from the backpack's main area first, then your inventory, and gives you the full one (water, lava, milk bucket, honey bottle, or the bucket of the oil the tank holds; if that oil has no bucket you are told and nothing is used). The **milk pipe** (adult cow, mooshroom or goat: right click, 1000 mB) and the **honey pipe** (a beehive or bee nest with full honey: right click, 250 mB, the bees get angry as with a bottle unless there is campfire smoke) take their liquid from the animal or the hive; they have no block to pour out, so left click only tells you so.
- **Item tooltips**: hover a slot that holds an item, in the main area or any tool area, to see the same tooltip the inventory shows (`F3+H` advanced tooltips included); nothing is shown while the cursor holds an item.
- **Handy controls**: `Ctrl` (`Command` on macOS) + click or drag moves items quickly between the backpack and the inventory; `Shift` selects slots; `Alt` (`Option` on macOS) + click swaps with the main-hand item; drag-distribute and double-click gather work like vanilla.
- **Wearable**: sneak + right-click puts the backpack into the chest slot; with **Curios** or **Accessories** installed it can also be worn in their back slot. The panel works with a worn backpack as usual.
- **Backpack snapshots**: the contents of every backpack are snapshotted outside the world, so you can restore it after a mistake, from a screen (bind "Backpack snapshots" in Controls) or with the `/snapshot` command.
- **Many backpacks**: each backpack has its own 4-character number, shown in the panel's title bar.
- **Search**: click the magnifier at the right of the title bar and type; the main area shows only the matching items, packed together, with a blue frame on each match and every other cell of the area greyed out and not clickable (cells of the three tool areas that match are framed too, nothing is hidden there). The box is the whole title bar except the tier icon, which you can still drag the panel by. `Esc` or the `x` in the box clears it and shows everything again; clicking a result lets go of the keyboard but keeps the results. Everything you can do with a slot works on a result as usual (select, `Ctrl`-click, `Alt`-click, `Delete`...). Searches the item's name in your language (also a renamed item's new name) and its registry name, any case; other mods can add more text to find an item by (other languages, pinyin...) through the mod's public API, see [docs/api/search-terms.md](docs/api/search-terms.md). Syntax: a space means "and" (`oak log`), `|` means "or" (`iron|gold`), a leading `-` excludes (`-planks`), quotes keep a phrase together (`"oak log"`). Prefixes: `@mod` finds the items of a mod, by its id or its name (`@minecraft`, `@mekanism ingot`, `@"sophisticated backpacks"`); `#tag` finds items with a tag, the item tags (`#logs` matches the path of any tag, `#c:ingots` or `#minecraft:logs` must start the full id), the `c:` tags and your own backpack tags (`#trash`) together; they combine with the rest (`@minecraft #logs`, `#logs|#planks`, `-#planks`) and are ordinary characters inside quotes or inside a word. A query that starts with `/hidden` lists which categories have items hidden because their extension was taken out (and in how many slots; the hidden items themselves are not shown, only the summary), and the words after it filter those categories (`/hidden fish`); any other `/xxx` is an unknown command. At the smallest size (3 columns) the panel has no close button to leave room; `B` or `Esc` closes it.

## Compatibility

- The panel also shows on, and exchanges items with, the screens of Sophisticated Backpacks and Traveler's Backpack; it stays out of JEI's way; Curios and Accessories are optional. **None of these mods is required.**
- The client config `config/mcfloatingbackpack-client.toml` can lower or switch off the compatibility per mod; the server config is `serverconfig/mcfloatingbackpack-server.toml` in the world folder.
- Both configs have a diagnostics log level (`OFF`/`ERROR`/`WARN`/`INFO`/`DEBUG`). When something goes wrong (say, a backpack disappears), raise it and `latest.log` will have more to go on.

## Multiplayer

- The client and the server **must use the same network protocol version**, or the client cannot join.
- Even with the same protocol, **the client and the server should run the same mod version**: some rules apply only on the server, and with different versions what the panel shows may differ from what the server actually does. The mod compares the two when you log in: if the **MAJOR.MINOR** differ (a different PATCH is fine) you get a warning in your own chat telling you to update or downgrade (back up your world first), and the server logs it. A client too old to have this check gets a plain-text notice from the server instead.
- To check whether the mod versions on your client and server match, you can additionally install [MCompatIssues](https://github.com/pnnh/MCompatIssues) (optional; it is not a dependency of this mod).
- The server stores and validates the contents; a client cannot create items.

## Updating and downgrading

Back up your world before updating the mod. **Downgrading is not supported**: an older version does not know the data a newer version stored (for example the hidden rows, or extension items it does not have) and drops it when it loads your player data; that loss cannot be undone. A backpack remembers the data revision it was last saved with, and an older version that has this check will refuse to open a backpack saved by a newer one (a message tells you why; opening it again within 60 seconds means you accept the risk), but this is a warning, not a guarantee that nothing is lost, and versions before the check cannot warn at all. If you must downgrade, restore a backup made before you updated.

To keep an older version from loading what a newer one saved, a world records the highest data revision of the mod that has touched it (`mcfloatingbackpack/world_marker.properties` in the world folder). **If a world was saved by a newer data revision than the mod you are running, the mod refuses to load that world**: a dedicated server stops with an error in the console, and in single player the game closes as if it had crashed (your launcher will say so) with a crash report in the `crash-reports` folder that explains why. Nothing in the world is read or changed by this mod. To accept the risk anyway (back up the world first), either add the JVM argument `-Dmcfloatingbackpack.acceptDowngrade=true` for one run, or delete the marker file (or lower its `data_revision`) to accept it for good. Updating the mod instead needs no action. Versions from before this check cannot refuse.

## Releases

Releases are published on [GitHub Releases](https://github.com/pnnh/MCFloatingBackpack/releases) from semantic-version tags (`vMAJOR.MINOR.PATCH`) by a GitHub Actions workflow; every asset carries a GitHub artifact attestation that you can verify:

```bash
gh attestation verify mcfloatingbackpack-<version>.jar --repo pnnh/MCFloatingBackpack
```

(No release has been published yet. The versioning rules and the release steps are in [`docs/plan/release-process.md`](docs/plan/release-process.md), in Chinese.)

## Building from source

```bash
./gradlew build            # the jar is in build/libs/
./gradlew test             # unit tests (they do not load Minecraft)
./gradlew runClient        # start a development client
```

The development client includes JEI, Curios, Accessories, Sophisticated Backpacks, Traveler's Backpack and other mods for testing alongside (development runs only; they do not go into the released jar):

```bash
./gradlew runClient -PcleanRuntime            # keep only JEI
./gradlew runClient -PcleanRuntime -PnoJei    # leave out JEI too
```

Java 21 is required. After running Gradle, `./gradlew --stop` shuts the background daemon down.

## Project documents

- [`docs/MCFloatingBackpack.md`](docs/MCFloatingBackpack.md): goals, feature descriptions and an overview of every feature (in Chinese).
- [`docs/plan/`](docs/plan): planning and design documents.
- [`specs/`](specs): the specification, plan, tasks and delivery notes of each feature, made with [Spec Kit](https://github.com/github/spec-kit) (numbered from 001; 035 onwards is the "category extensions" series).
- Project documents under `docs/` and `specs/` are written in Chinese, the author's working language; see [`AGENTS.md`](AGENTS.md).

## Contributing

This is a personal project and may not accept outside contributions.

## License

GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later), see [LICENSE](LICENSE).
