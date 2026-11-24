package pnnh.mcfloatingbackpack.inventory.recipe;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackEmptiness;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.extension.Row;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 065: the combined backpack's crafting recipe: an ordinary shaped recipe, except that a floating backpack among the ingredients must be
 * <em>empty</em>. The backpack is used up, and one with anything in it would take its contents with it. The combined backpack holds no data,
 * so there is nothing to carry over to the result (and no identity to stamp, unlike {@link BackpackCraftingRecipe}).
 */
public class CombinedCraftingRecipe extends ShapedRecipe {
    private final String groupName;
    private final CraftingBookCategory bookCategory;
    private final ShapedRecipePattern recipePattern;
    private final ItemStack resultStack;
    private final boolean notify;

    public CombinedCraftingRecipe(
            String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.groupName = group;
        this.bookCategory = category;
        this.recipePattern = pattern;
        this.resultStack = result;
        this.notify = showNotification;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (FloatingBackpackItem.isBackpack(stack) && !isEmptyBackpack(stack)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the backpack holds nothing (see {@link BackpackEmptiness}); a backpack with no contents component is empty. */
    public static boolean isEmptyBackpack(ItemStack backpack) {
        BackpackContents contents = backpack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        if (contents == null) {
            return true;
        }
        List<List<ItemStack>> rowCells = new ArrayList<>();
        for (Row<ItemStack> row : contents.rows().visible()) {
            rowCells.add(row.cells());
        }
        for (Row<ItemStack> row : contents.rows().hidden()) {
            rowCells.add(row.cells());
        }
        return BackpackEmptiness.isEmpty(
                new BackpackEmptiness.Parts<>(
                        contents.items(),
                        contents.cursor(),
                        contents.tools().left(),
                        contents.tools().right(),
                        contents.tools().bottom(),
                        contents.trash().groups(),
                        rowCells,
                        contents.fluids().isEmpty()),
                ItemStack::isEmpty);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.COMBINED_CRAFTING.get();
    }

    public static final MapCodec<CombinedCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.groupName),
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(recipe -> recipe.bookCategory),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.recipePattern),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.resultStack),
                    Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(recipe -> recipe.notify))
            .apply(instance, CombinedCraftingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombinedCraftingRecipe> STREAM_CODEC =
            StreamCodec.of(CombinedCraftingRecipe::toNetwork, CombinedCraftingRecipe::fromNetwork);

    private static CombinedCraftingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
        String group = buffer.readUtf();
        CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
        ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
        ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
        boolean showNotification = buffer.readBoolean();
        return new CombinedCraftingRecipe(group, category, pattern, result, showNotification);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, CombinedCraftingRecipe recipe) {
        buffer.writeUtf(recipe.groupName);
        buffer.writeEnum(recipe.bookCategory);
        ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.recipePattern);
        ItemStack.STREAM_CODEC.encode(buffer, recipe.resultStack);
        buffer.writeBoolean(recipe.notify);
    }
}
