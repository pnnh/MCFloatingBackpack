package pnnh.mcfloatingbackpack.inventory.recipe;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;

/**
 * 026: a shaped crafting recipe that puts a fresh, still unregistered identity (an internal id and no number) on every result
 * it assembles. Shift-clicking a crafting result runs the "crafted" callback for the real stack and again for a copy made
 * before it, and the crafting grid assembles previews that nobody ever takes; stamping the identity here, when the result is
 * assembled, lets the callback register by internal id (and do nothing the second time) instead of registering whatever stack
 * it is handed. Everything else is exactly {@link ShapedRecipe}.
 */
public class BackpackCraftingRecipe extends ShapedRecipe {
    private final String groupName;
    private final CraftingBookCategory bookCategory;
    private final ShapedRecipePattern recipePattern;
    private final ItemStack resultStack;
    private final boolean notify;

    public BackpackCraftingRecipe(
            String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.groupName = group;
        this.bookCategory = category;
        this.recipePattern = pattern;
        this.resultStack = result;
        this.notify = showNotification;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack stack = super.assemble(input, registries);
        stack.set(ModDataComponents.BACKPACK_IDENTITY.get(), new BackpackIdentity(UUID.randomUUID()));
        return stack;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BACKPACK_CRAFTING.get();
    }

    public static final MapCodec<BackpackCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.groupName),
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(recipe -> recipe.bookCategory),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.recipePattern),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.resultStack),
                    Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(recipe -> recipe.notify))
            .apply(instance, BackpackCraftingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackCraftingRecipe> STREAM_CODEC =
            StreamCodec.of(BackpackCraftingRecipe::toNetwork, BackpackCraftingRecipe::fromNetwork);

    private static BackpackCraftingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
        String group = buffer.readUtf();
        CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
        ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
        ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
        boolean showNotification = buffer.readBoolean();
        return new BackpackCraftingRecipe(group, category, pattern, result, showNotification);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, BackpackCraftingRecipe recipe) {
        buffer.writeUtf(recipe.groupName);
        buffer.writeEnum(recipe.bookCategory);
        ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.recipePattern);
        ItemStack.STREAM_CODEC.encode(buffer, recipe.resultStack);
        buffer.writeBoolean(recipe.notify);
    }
}
