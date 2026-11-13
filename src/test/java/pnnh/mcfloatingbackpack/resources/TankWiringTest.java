package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 048: the wiring no pure test can see because the classes need Minecraft, and what was not touched. */
class TankWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    // ---- the data ----

    @Test
    void theLiquidsAreAnOptionalStoredFieldAndTheLastThingOnTheWire() throws IOException {
        String contents = read(SRC.resolve("inventory/BackpackContents.java"));
        assertTrue(contents.contains("FluidStore fluids,\n        int dataRevision"), "the new component sits before the revision");
        assertTrue(contents.contains(".optionalFieldOf(\"fluids\", FluidStore.EMPTY)"), "an older backpack has no field and reads as empty");
        int rows = contents.indexOf("ExtensionRowsCodecs.STREAM_CODEC.encode(buf, contents.rows());");
        int fluids = contents.indexOf("FluidStoreCodecs.STREAM_CODEC.encode(buf, contents.fluids());");
        assertTrue(rows > 0 && fluids > rows, "after the rows, last on the wire");
        assertTrue(contents.contains("FluidStoreCodecs.STREAM_CODEC.decode(buf)"));
    }

    @Test
    void theStoredFormIsAnObjectWithAKeyForEachLiquid() throws IOException {
        String codecs = read(SRC.resolve("tank/FluidStoreCodecs.java"));
        for (String kind : new String[] {"WATER", "OIL", "LAVA", "MILK", "HONEY"}) {
            assertTrue(codecs.contains("FluidKind." + kind + ".id()"), kind);
        }
        assertTrue(codecs.contains("\"oil_fluid\""));
        assertTrue(codecs.contains("MAX_FLUID_ID = 256"));
        assertTrue(codecs.contains("count > kinds.length"), "the wire refuses more liquids than there are");
        assertTrue(codecs.contains("ordinal >= kinds.length"), "and an unknown liquid");
        assertTrue(codecs.contains("FluidStore.of(amounts, oilFluid)") && codecs.contains("FluidStore.of(amounts, FLUID_ID.decode(buf))"),
                "both ways clean what they read through the record's invariants");
    }

    @Test
    void theInventoryCarriesTheLiquidsThroughLoadAndSave() throws IOException {
        String inventory = read(SRC.resolve("inventory/BackpackInventory.java"));
        assertTrue(inventory.contains("inventory.setFluids(fitted.fluids());"));
        assertTrue(inventory.contains("reconciledRows(), fluids, baseCapacity)"));
        String recipe = read(SRC.resolve("inventory/recipe/BackpackUpgradeRecipe.java"));
        assertTrue(recipe.contains("current.rows(), current.fluids(), capacity)"), "upgrading a backpack keeps its liquids");
    }

    @Test
    void theSnapshotsStillGoThroughTheSameCodecSoTheLiquidsComeAlong() throws IOException {
        assertTrue(read(SRC.resolve("snapshot/SnapshotOps.java")).contains("BackpackContents.CODEC"));
    }

    @Test
    void theDataRevisionIsFiveSince054AndTheProtocolIs20() throws IOException {
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 5;")); // 051 raised it from 2 (048 introduced 2)
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"20\")"));
        assertTrue(network.contains("registrar.playToClient(\n                BackpackFluidsPayload.TYPE,"), "a server-to-client packet");
        assertTrue(network.contains("BackpackFluidsPayload::handleClient"));
    }

    @Test
    void theClientIsToldTheLiquidsWhenTheSessionOpensAndForgetsThemWhenItClears() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        int pickup = network.indexOf("sendPickupBlacklist(player, inventory);\n        // 048");
        int fluids = network.indexOf("sendFluids(player, inventory);", pickup);
        assertTrue(pickup > 0 && fluids > pickup, "sent with the tags, the trash and the pickup blacklist");
        assertTrue(network.contains("new BackpackFluidsPayload(inventory.fluids())"));
        String view = read(SRC.resolve("client/panel/ClientBackpackView.java"));
        assertTrue(view.contains("fluids = pnnh.mcfloatingbackpack.tank.FluidStore.EMPTY;"));
        assertTrue(view.contains("public void setFluids("));
    }

    // ---- the rules ----

    @Test
    void theServerAsksTheTankRuleAfterTheToolAreaRuleAndBeforeItChangesAnything() throws IOException {
        String click = read(SRC.resolve("network/BackpackClickPayload.java"));
        int toolRule = click.indexOf("ToolAreaRules.canPlace(");
        int tankRule = click.indexOf("TankItem.check(inventory, area, held, payload.slotIndex())");
        int change = click.indexOf("BackpackMenuBridge.clickWithMenuCarried(");
        assertTrue(toolRule > 0 && tankRule > toolRule && change > tankRule, "tool area rule, tank rule, then the change");
        String branch = click.substring(tankRule, change);
        assertTrue(branch.contains("displayClientMessage(TankItem.refusal(verdict, held), true)"), "the player is told why");
        assertTrue(branch.contains("sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex())"), "a refusal is rolled back");
        assertTrue(branch.contains("return;"));
    }

    @Test
    void theClientPredictsWithTheSameFunction() throws IOException {
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        String limit = input.substring(input.indexOf("private static boolean refusedByLimit"));
        assertTrue(limit.contains("TankItem.check(area, held, slotIndex, view.toolStacks(area), view.backpackTier(), view.fluids())"));
        assertTrue(limit.contains("TankItem.refusal(tankVerdict, held)"));
        String item = read(SRC.resolve("item/TankItem.java"));
        assertTrue(item.contains("TankPlacement.check(area, held, slot, areaSlots, TankItem::tankOf, backpack, stored)"));
        assertTrue(item.contains("check(area, held, slot, inventory.toolStacks(area), inventory.tier(), inventory.fluids())"));
    }

    @Test
    void everyRefusalHasAMessageInBothLanguages() throws IOException {
        String item = read(SRC.resolve("item/TankItem.java"));
        for (String key : new String[] {"tank.tier", "tank.duplicate", "tank.too_small"}) {
            assertTrue(item.contains("\"message.mcfloatingbackpack." + key + "\""), key);
        }
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            String text = read(Path.of("src/main/resources/assets/mcfloatingbackpack/lang/" + lang + ".json"));
            for (String key : new String[] {"tank.tier", "tank.duplicate", "tank.too_small"}) {
                assertTrue(text.contains("\"message.mcfloatingbackpack." + key + "\""), lang + " " + key);
            }
        }
    }

    @Test
    void theOnlyWayIntoAToolAreaIsTheClickPacket() throws IOException {
        // the other batch requests write the main area only (setStackInSlot with one index); none of them names a tool area
        for (String name : new String[] {"BackpackQuickTransferPayload", "BackpackCursorGesturePayload", "BackpackTakeFromContainerPayload",
                "BackpackTrashPayload", "BackpackTrashSendPayload", "MergePanelCursorPayload"}) {
            String source = read(SRC.resolve("network/" + name + ".java"));
            assertFalse(source.contains("setStackInSlot(area,") || source.contains("setStackInSlot(PanelArea."), name);
        }
    }

    // ---- the panel ----

    @Test
    void theLevelBarIsDrawnOnlyInTheBottomAreaAndOnlyForATank() throws IOException {
        String renderer = read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
        assertTrue(renderer.contains("if (area == PanelArea.BOTTOM) {\n                        drawTankBar(graphics, view, stack, sx, sy);"));
        String bar = renderer.substring(renderer.indexOf("private static void drawTankBar"), renderer.indexOf("private static Component tankTooltipLine"));
        assertTrue(bar.contains("TankItem.tankOf(stack)") && bar.contains("if (tank == null)"));
        assertTrue(bar.contains("TankBar.fillPixels(") && bar.contains("if (fill <= 0)"), "nothing for an empty tank");
        assertTrue(bar.contains("translate(0.0F, 0.0F, 200.0F)"), "on the layer of the durability bar");
        assertTrue(bar.contains("tank.kind().color()"));
    }

    @Test
    void theTooltipOfATankInTheBottomAreaSaysWhatItHolds() throws IOException {
        String renderer = read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
        assertTrue(renderer.contains("hit.area() == PanelArea.BOTTOM ? TankItem.tankOf(stack) : null"));
        assertTrue(renderer.contains("Screen.getTooltipFromItem(minecraft, stack)"));
        assertTrue(renderer.contains("\"tooltip.mcfloatingbackpack.tank.amount\"") && renderer.contains("\"tooltip.mcfloatingbackpack.tank.empty\""));
    }

    // ---- 048 US6: liquids with no tank are hidden content ----

    @Test
    void theYellowDotIsDrawnForHiddenItemsOrHiddenLiquids() throws IOException {
        String renderer = read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
        assertTrue(renderer.contains("boolean anyHidden = !hidden.isEmpty() || !hiddenLiquids.isEmpty();"));
        assertTrue(renderer.contains("if (anyHidden) {\n            drawHiddenDot(graphics, state);"));
        assertTrue(renderer.contains("boolean overDot = anyHidden && TitleDotLayout.hits("));
        assertTrue(renderer.contains("drawHiddenHint(graphics, minecraft, hidden, hiddenLiquids, mouseX, mouseY)"));
        assertTrue(renderer.contains("HiddenTextKeys.title(hasItems, hasLiquids)") && renderer.contains("HiddenTextKeys.hints(hasItems, hasLiquids)"));
        assertTrue(renderer.contains("\".hidden.liquid.line\""), "a line for each hidden liquid");
        String view = read(SRC.resolve("client/panel/ClientBackpackView.java"));
        assertTrue(view.contains("HiddenLiquids.lines(fluids, kind -> tankTier(kind) != null)"), "a tank counts only if the bottom area has it");
    }

    @Test
    void slashHiddenListsTheLiquidsAfterTheCategories() throws IOException {
        String context = read(SRC.resolve("client/panel/search/SearchContext.java"));
        int categories = context.indexOf("lines.add(new HiddenSummaryRows.Line(line.category(), name, line.cells()));");
        int liquids = context.indexOf("ClientBackpackView.get().hiddenLiquids()", categories);
        assertTrue(categories > 0 && liquids > categories);
        assertTrue(context.contains("new HiddenSummaryRows.Line(\"liquid_\" + liquid.kind().id(), name, 1, liquid.amountMb())"));
        String search = read(SRC.resolve("client/panel/search/SearchRenderer.java"));
        assertTrue(search.contains("row.isLiquid()") && search.contains("\".hidden.liquid.line\""));
        assertTrue(search.contains("HiddenTextKeys.title(hasItems, hasLiquids)"));
    }

    @Test
    void theHiddenLiquidWordsExistInBothLanguagesAndNothingNewWasAdded() throws IOException {
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            String text = read(Path.of("src/main/resources/assets/mcfloatingbackpack/lang/" + lang + ".json"));
            for (String key : new String[] {"hidden.liquid.name", "hidden.liquid.title", "hidden.liquid.line", "hidden.liquid.hint", "hidden.mixed.title"}) {
                assertTrue(text.contains("\"panel.mcfloatingbackpack." + key + "\""), lang + " " + key);
            }
        }
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertEquals(1, network.split("BackpackFluidsPayload.TYPE,", -1).length - 1, "the fluids packet is the only one 048 added");
        assertFalse(read(SRC.resolve("tank/HiddenLiquids.java")).contains("import net.minecraft"));
    }

    @Test
    void theTankPackageNeedsNoMinecraftAndNothingWasAddedToTheBuild() throws IOException {
        for (String name : new String[] {"FluidKind", "TankCatalog", "FluidStore", "FluidStoreOps", "TankPlacement", "TankBar"}) {
            String source = read(SRC.resolve("tank/" + name + ".java"));
            assertFalse(source.contains("import net.minecraft"), name);
            assertFalse(source.contains("import net.neoforged"), name);
        }
        assertFalse(read(Path.of("src/main/templates/META-INF/neoforge.mods.toml")).contains("tank"));
    }
}
