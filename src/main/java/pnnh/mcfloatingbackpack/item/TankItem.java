package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import pnnh.mcfloatingbackpack.tank.FluidKind;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.tank.TankCatalog;
import pnnh.mcfloatingbackpack.tank.TankPlacement;

/**
 * 048: a liquid tank. An ordinary item that does nothing in hand or in the world and carries no data: what it holds belongs to the
 * backpack ({@code FluidStore}), so taking it out leaves the liquid in the backpack. It only counts while it sits in the floating
 * panel's bottom area, one for each liquid, and only in a backpack of its tier or higher (see {@code TankPlacement}).
 */
public class TankItem extends Item {
    private final FluidKind kind;
    private final BackpackTier tier;

    public TankItem(FluidKind kind, BackpackTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.tier = tier;
    }

    public FluidKind kind() {
        return kind;
    }

    public BackpackTier tier() {
        return tier;
    }

    /** What a tank of this item is, for the placement rules. */
    public TankCatalog.Tank tank() {
        return new TankCatalog.Tank(kind, tier);
    }

    /** The tank a stack is, or {@code null} for any other stack. */
    public static TankCatalog.Tank tankOf(ItemStack stack) {
        return stack.getItem() instanceof TankItem item ? item.tank() : null;
    }

    /**
     * Whether {@code held} may be put into slot {@code slot} of {@code area} of a backpack with these tool stacks, tier and stored liquid;
     * the server and the client's prediction both ask this. A stack that is not a tank is always fine.
     */
    public static TankPlacement.Verdict check(
            PanelArea area, ItemStack held, int slot, java.util.List<ItemStack> areaSlots, BackpackTier backpack, pnnh.mcfloatingbackpack.tank.FluidStore fluids) {
        TankCatalog.Tank tank = tankOf(held);
        int stored = tank == null ? 0 : fluids.amount(tank.kind());
        return TankPlacement.check(area, held, slot, areaSlots, TankItem::tankOf, backpack, stored);
    }

    /** The server-side check for a backpack inventory. */
    public static TankPlacement.Verdict check(BackpackInventory inventory, PanelArea area, ItemStack held, int slot) {
        return check(area, held, slot, inventory.toolStacks(area), inventory.tier(), inventory.fluids());
    }

    /** What to tell the player when a tank was refused; {@code null} for {@code OK}. */
    public static Component refusal(TankPlacement.Verdict verdict, ItemStack held) {
        return switch (verdict) {
            case OK -> null;
            case TIER_TOO_HIGH -> {
                TankCatalog.Tank tank = tankOf(held);
                String tier = tank == null ? "leather" : tank.tier().name().toLowerCase(java.util.Locale.ROOT);
                yield Component.translatable("message.mcfloatingbackpack.tank.tier", Component.translatable("tier.mcfloatingbackpack." + tier));
            }
            case DUPLICATE_KIND -> Component.translatable("message.mcfloatingbackpack.tank.duplicate");
            case STORE_EXCEEDS_CAPACITY -> Component.translatable("message.mcfloatingbackpack.tank.too_small");
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mcfloatingbackpack.tank.capacity", TankCatalog.capacityMb(tier))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.mcfloatingbackpack.tank.min_tier", Component.translatable("tier.mcfloatingbackpack." + tier.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
    }
}
