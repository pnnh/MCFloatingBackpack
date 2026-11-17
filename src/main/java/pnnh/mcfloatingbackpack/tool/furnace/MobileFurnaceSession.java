package pnnh.mcfloatingbackpack.tool.furnace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.EventHooks;

/**
 * 055: one opening of a mobile furnace: its working state (the engine's {@link FurnaceCore}), the clock it has been worked out up to, and the
 * backpack it belongs to. The state is worked out to the game time when it is opened (the catch-up for the time it was shut), then up to now every
 * time the menu is looked at ({@link #advanceTo}; a second look in the same tick works out nothing), and it is written back to the item whenever
 * the player changes what is in the slots, when it is opened and when it is closed ({@link #save}). The time that passes in between needs no
 * writing: it is worked out again from the state and its stamp, with the same engine.
 */
public final class MobileFurnaceSession {
    private final ServerPlayer player;
    private final UUID identity;
    private final McFurnaceRules rules;
    private final FurnaceCore<ItemStack> core;
    private final FurnaceStateContainer container;
    private long workedOutTo;
    private boolean saving;

    /** Opens the furnace whose state is {@code state}, worked out up to {@code now}. */
    public MobileFurnaceSession(ServerPlayer player, UUID identity, FurnaceState state, long now) {
        this.player = player;
        this.identity = identity;
        this.rules = new McFurnaceRules(player.serverLevel());
        this.core = state.toCore();
        long elapsed = state.stamp() == FurnaceState.NO_STAMP ? 0 : Math.max(0, now - state.stamp());
        FurnaceEngine.advance(core, elapsed, rules);
        this.workedOutTo = now;
        this.container = new FurnaceStateContainer(core, rules, this::save);
    }

    public UUID identity() {
        return identity;
    }

    public FurnaceStateContainer container() {
        return container;
    }

    public McFurnaceRules rules() {
        return rules;
    }

    /** Works the furnace out up to the game time {@code now}; a tick that was already worked out changes nothing. */
    public void advanceTo(long now) {
        if (now > workedOutTo) {
            FurnaceEngine.advance(core, now - workedOutTo, rules);
        }
        workedOutTo = Math.max(workedOutTo, now);
    }

    /** Writes the state to the item, with the clock it has been worked out up to. Reentrant calls (a save that changes a slot) are ignored. */
    public void save() {
        if (saving) {
            return;
        }
        saving = true;
        try {
            MobileFurnaceStore.write(player, identity, FurnaceState.of(core, workedOutTo));
        } finally {
            saving = false;
        }
    }

    /** The four numbers of a vanilla furnace's menu, as vanilla (and NeoForge) give them. */
    public ContainerData data() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    // NeoForge: data slots are synced as shorts, so a long burn keeps the ratio of litTime to litDuration instead
                    case 0 -> core.litDuration > Short.MAX_VALUE
                            ? net.minecraft.util.Mth.floor(((double) core.litTime / core.litDuration) * Short.MAX_VALUE)
                            : core.litTime;
                    case 1 -> Math.min(core.litDuration, Short.MAX_VALUE);
                    case 2 -> core.cookProgress;
                    case 3 -> core.cookTotal;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> core.litTime = value;
                    case 1 -> core.litDuration = value;
                    case 2 -> core.cookProgress = value;
                    case 3 -> core.cookTotal = value;
                    default -> {
                    }
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
    }

    /**
     * The player takes the output: pays the experience of everything smelted since last time, the way a vanilla furnace does
     * ({@code awardUsedRecipesAndPopExperience}), then starts counting again and writes that down (or the next take would pay it twice).
     */
    public void payExperience(ServerPlayer taker) {
        if (core.recipesUsed.isEmpty()) {
            return;
        }
        ServerLevel level = taker.serverLevel();
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : core.recipesUsed.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(id);
            if (holder.isPresent() && holder.get().value() instanceof AbstractCookingRecipe cooking) {
                recipes.add(holder.get());
                int orbs = FurnaceExperience.orbs(entry.getValue(), cooking.getExperience(), Math.random());
                ExperienceOrb.award(level, taker.position(), orbs);
            }
        }
        taker.awardRecipes(recipes);
        List<ItemStack> contents = List.of(core.input, core.fuel, core.output);
        for (RecipeHolder<?> recipe : recipes) {
            taker.triggerRecipeCrafted(recipe, contents);
        }
        core.recipesUsed.clear();
        save();
    }
}
