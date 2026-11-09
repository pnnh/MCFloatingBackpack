package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.authlib.GameProfile;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Filter;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Page;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;

/**
 * 030: {@code /mcfloatingbackpack snapshot ...} (alias {@code /mcfb}): look at the backpack snapshots, get a new backpack out of one, and
 * delete them. Everyone can list and look at <em>their own</em> backpacks' snapshots; getting a new backpack out of a snapshot
 * (in survival that copies items), deleting, trimming and looking at other players' snapshots need permission level 2 (the
 * server console has it). A player who names somebody else's backpack gets "no match", so the command does not say whether
 * it exists.
 *
 * <p>{@code restore} never changes an existing backpack: it hands out a <em>new</em> backpack without an identity, which gets
 * its own the first time it is used. Every message carries an English fallback, so a server console, which has no language
 * file of this mod, still reads it. Runs on the server's main thread; the files are written first (flushAndWait).
 */
public final class SnapshotCommands {
    private static final int PAGE_SIZE = 10;
    private static final String KEY = "command.mcfloatingbackpack.";

    private SnapshotCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> snapshot = Commands.literal("snapshot")
                // 031: the window; anyone may open it (what it shows and allows is the same as these commands)
                .then(Commands.literal("gui").executes(SnapshotCommands::gui))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ListKind.MINE, null, -1, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx, ListKind.MINE, null, -1, IntegerArgumentType.getInteger(ctx, "page"))))
                        .then(Commands.literal("all")
                                .requires(SnapshotCommands::isOp)
                                .executes(ctx -> list(ctx, ListKind.ALL, null, -1, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> list(ctx, ListKind.ALL, null, -1, IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(Commands.literal("owner")
                                .requires(SnapshotCommands::isOp)
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> listOwner(ctx, 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> listOwner(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
                        .then(Commands.literal("inactive")
                                .requires(SnapshotCommands::isOp)
                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                        .executes(ctx -> list(ctx, ListKind.ALL, null, IntegerArgumentType.getInteger(ctx, "days"), 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> list(
                                                        ctx,
                                                        ListKind.ALL,
                                                        null,
                                                        IntegerArgumentType.getInteger(ctx, "days"),
                                                        IntegerArgumentType.getInteger(ctx, "page")))))))
                .then(Commands.literal("info")
                        .then(Commands.argument("backpack", StringArgumentType.word()).executes(SnapshotCommands::info)))
                .then(Commands.literal("restore")
                        .requires(SnapshotCommands::isOp)
                        .then(Commands.argument("backpack", StringArgumentType.word())
                                .executes(ctx -> restore(ctx, 1, null))
                                .then(Commands.argument("version", IntegerArgumentType.integer(1))
                                        .executes(ctx -> restore(ctx, IntegerArgumentType.getInteger(ctx, "version"), null))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> restore(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "version"),
                                                        EntityArgument.getPlayer(ctx, "target")))))))
                .then(Commands.literal("delete")
                        .requires(SnapshotCommands::isOp)
                        .then(Commands.argument("backpack", StringArgumentType.word())
                                .executes(ctx -> deleteAll(ctx, false))
                                .then(Commands.literal("confirm").executes(ctx -> deleteAll(ctx, true)))
                                .then(Commands.argument("version", IntegerArgumentType.integer(1))
                                        .executes(SnapshotCommands::deleteOne))))
                .then(Commands.literal("trim")
                        .requires(SnapshotCommands::isOp)
                        // 0 means "however recently it was used": every backpack's snapshots
                        .then(Commands.argument("days", IntegerArgumentType.integer(0))
                                .executes(ctx -> trim(ctx, false))
                                .then(Commands.literal("confirm").executes(ctx -> trim(ctx, true)))));
        LiteralCommandNode<CommandSourceStack> root =
                dispatcher.register(Commands.literal("mcfloatingbackpack").then(snapshot));
        dispatcher.register(Commands.literal("mcfb").redirect(root));
    }

    private static int gui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return fail(ctx, msg("gui.console", "The snapshot window needs a player: from the console use 'list all'."));
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new pnnh.mcfloatingbackpack.snapshot.net.SnapshotOpenPayload());
        return 1;
    }

    private enum ListKind {
        MINE,
        ALL
    }

    private static boolean isOp(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    // ---------------------------------------------------------------- messages

    /** A text component only takes strings, numbers, booleans and components as arguments: anything else (a UUID...) is turned into a string. */
    private static Component msg(String key, String fallback, Object... args) {
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            safe[i] = arg instanceof String || arg instanceof Number || arg instanceof Boolean || arg instanceof Component
                    ? arg
                    : String.valueOf(arg);
        }
        return Component.translatableWithFallback(KEY + key, fallback, safe);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendFailure(message);
        return 0;
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static String tierName(int capacity) {
        BackpackTier tier = BackpackTier.byCapacity(capacity);
        return tier == null ? "?" : tier.name().toLowerCase(Locale.ROOT);
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static Component entry(Group group, long now) {
        SnapshotMeta newest = group.newest();
        return msg(
                "list.entry",
                "%s  %s  %s  %s snapshot(s)  owner %s  last %s ago",
                BackpackIds.of(group.uuid()),
                shortUuid(group.uuid()),
                tierName(newest.capacity()),
                group.versions(),
                newest.ownerName(),
                AgeText.ago(now - newest.time()));
    }

    // ---------------------------------------------------------------- the files, as this source may see them

    private static Optional<SnapshotService> service(CommandContext<CommandSourceStack> ctx) {
        Optional<SnapshotService> service = SnapshotService.get();
        if (service.isEmpty()) {
            fail(ctx, msg("unavailable", "Backpack snapshots are not running."));
        }
        return service;
    }

    /** Writes what is waiting, then reads the files: what this source may see (everyone's for an op, one's own otherwise). */
    private static List<Group> visible(CommandContext<CommandSourceStack> ctx, SnapshotService service) {
        return SnapshotOps.visibleGroups(service, requester(ctx.getSource()));
    }

    private static SnapshotOps.Requester requester(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return new SnapshotOps.Requester(isOp(source), player == null ? null : player.getUUID());
    }

    /** The one backpack the argument names (among those this source may see), or null after saying why not. */
    private static Group chooseOne(CommandContext<CommandSourceStack> ctx, SnapshotService service) {
        String text = StringArgumentType.getString(ctx, "backpack");
        BackpackSelector.Parsed parsed = BackpackSelector.parse(text);
        if (parsed.kind() == BackpackSelector.Kind.INVALID) {
            fail(ctx, msg(
                    "select.invalid",
                    "'%s' is not a backpack id (4 characters), a UUID or the start of a UUID (at least %s characters).",
                    text,
                    BackpackSelector.MIN_PREFIX));
            return null;
        }
        List<Group> groups = visible(ctx, service);
        List<UUID> matches = BackpackSelector.resolve(groups.stream().map(Group::uuid).toList(), parsed);
        if (matches.isEmpty()) {
            fail(ctx, msg("select.none", "No backpack with a snapshot matches '%s'.", text));
            return null;
        }
        Function<UUID, Group> byUuid = uuid -> groups.stream().filter(g -> g.uuid().equals(uuid)).findFirst().orElseThrow();
        if (matches.size() > 1) {
            long now = System.currentTimeMillis();
            fail(ctx, msg("select.ambiguous", "%s backpacks match '%s'; name one by the start of its UUID:", matches.size(), text));
            for (Group group : SnapshotListing.newestFirst(matches.stream().map(byUuid).toList())) {
                ctx.getSource().sendSystemMessage(entry(group, now));
            }
            return null;
        }
        return byUuid.apply(matches.get(0));
    }

    // ---------------------------------------------------------------- looking

    private static int listOwner(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        List<GameProfile> profiles = new ArrayList<>(GameProfileArgument.getGameProfiles(ctx, "player"));
        if (profiles.size() != 1) {
            return fail(ctx, msg("list.oneplayer", "Name exactly one player."));
        }
        return list(ctx, ListKind.ALL, profiles.get(0).getId(), -1, page);
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ListKind kind, UUID owner, int inactiveDays, int pageNumber) {
        Optional<SnapshotService> service = service(ctx);
        if (service.isEmpty()) {
            return 0;
        }
        UUID filterOwner = owner;
        if (kind == ListKind.MINE) {
            ServerPlayer self = ctx.getSource().getPlayer();
            if (self == null) {
                return fail(ctx, msg("list.console", "The console has no backpacks of its own: use 'list all'."));
            }
            filterOwner = self.getUUID();
        }
        long now = System.currentTimeMillis();
        List<Group> sorted = SnapshotListing.newestFirst(
                SnapshotListing.select(visible(ctx, service.get()), new Filter(filterOwner, inactiveDays), now));
        Page page = SnapshotListing.page(sorted, pageNumber, PAGE_SIZE);
        if (!page.inRange()) {
            return fail(ctx, msg("list.badpage", "There is no page %s (there are %s).", pageNumber, page.pages()));
        }
        if (page.total() == 0) {
            return ok(ctx, msg("list.empty", "No backpack snapshots match."));
        }
        int from = (pageNumber - 1) * PAGE_SIZE + 1;
        ctx.getSource().sendSuccess(
                () -> msg(
                        "list.header",
                        "Backpacks %s-%s of %s (page %s/%s):",
                        from,
                        from + page.items().size() - 1,
                        page.total(),
                        page.page(),
                        page.pages()),
                false);
        for (Group group : page.items()) {
            ctx.getSource().sendSuccess(() -> entry(group, now), false);
        }
        return page.total();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Optional<SnapshotService> service = service(ctx);
        if (service.isEmpty()) {
            return 0;
        }
        Group group = chooseOne(ctx, service.get());
        if (group == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        List<SnapshotMeta> versions = service.get().store().versions(group.uuid());
        ctx.getSource().sendSuccess(() -> msg("info.head", "Backpack %s  (%s)", BackpackIds.of(group.uuid()), group.uuid().toString()), false);
        ctx.getSource().sendSuccess(
                () -> msg(
                        "info.body",
                        "Tier %s (%s slots), last used by %s, %s snapshot(s):",
                        tierName(group.newest().capacity()),
                        group.newest().capacity(),
                        group.newest().ownerName(),
                        versions.size()),
                false);
        for (int i = 0; i < versions.size(); i++) {
            int number = i + 1;
            SnapshotMeta version = versions.get(i);
            ctx.getSource().sendSuccess(
                    () -> msg("info.version", "  #%s  %s ago  %s non-empty slots", number, AgeText.ago(now - version.time()), version.slots()),
                    false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- changing

    private static int restore(CommandContext<CommandSourceStack> ctx, int version, ServerPlayer named) {
        ServerPlayer target = named != null ? named : ctx.getSource().getPlayer();
        if (target == null) {
            return fail(ctx, msg("restore.console", "Name a player: the console has no inventory."));
        }
        Optional<SnapshotService> service = service(ctx);
        if (service.isEmpty()) {
            return 0;
        }
        Group group = chooseOne(ctx, service.get());
        if (group == null) {
            return 0;
        }
        String id = BackpackIds.of(group.uuid());
        List<SnapshotMeta> versions = service.get().store().versions(group.uuid());
        if (version > versions.size()) {
            return fail(ctx, msg("restore.badversion", "Backpack %s has %s snapshot(s); there is no snapshot #%s.", id, versions.size(), version));
        }
        SnapshotMeta chosen = versions.get(version - 1);
        ResultCode result = SnapshotOps.restore(ctx.getSource().getServer(), service.get(), chosen, target);
        if (result == ResultCode.NO_TIER) {
            return fail(ctx, msg("restore.notier", "Backpack %s has a size that matches no tier (%s slots).", id, chosen.capacity()));
        }
        if (result != ResultCode.RESTORED) {
            return fail(ctx, msg("restore.unreadable", "The snapshot could not be read (see the server log)."));
        }
        return ok(ctx, msg(
                "restore.ok",
                "Gave %s a new backpack with snapshot #%s of %s. The original backpack is unchanged.",
                target.getGameProfile().getName(),
                version,
                id));
    }

    private static int deleteOne(CommandContext<CommandSourceStack> ctx) {
        Optional<SnapshotService> service = service(ctx);
        if (service.isEmpty()) {
            return 0;
        }
        Group group = chooseOne(ctx, service.get());
        if (group == null) {
            return 0;
        }
        int version = IntegerArgumentType.getInteger(ctx, "version");
        String id = BackpackIds.of(group.uuid());
        List<SnapshotMeta> versions = service.get().store().versions(group.uuid());
        if (version > versions.size()) {
            return fail(ctx, msg("restore.badversion", "Backpack %s has %s snapshot(s); there is no snapshot #%s.", id, versions.size(), version));
        }
        boolean deleted = SnapshotOps.deleteOne(service.get(), group.uuid(), versions.get(version - 1).time());
        if (!deleted) {
            return fail(ctx, msg("delete.failed", "Could not delete the snapshot (see the server log)."));
        }
        return ok(ctx, msg("delete.one", "Deleted snapshot #%s of backpack %s.", version, id));
    }

    private static int deleteAll(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        Optional<SnapshotService> service = service(ctx);
        if (service.isEmpty()) {
            return 0;
        }
        Group group = chooseOne(ctx, service.get());
        if (group == null) {
            return 0;
        }
        String id = BackpackIds.of(group.uuid());
        if (!confirmed) {
            return ok(ctx, msg(
                    "delete.preview",
                    "Backpack %s has %s snapshot(s). Nothing was deleted; add 'confirm' to delete them all.",
                    id,
                    group.versions()));
        }
        int deleted = SnapshotOps.deleteAll(service.get(), group.uuid());
        return ok(ctx, msg("delete.all", "Deleted %s snapshot(s) of backpack %s.", deleted, id));
    }

    private static int trim(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        Optional<SnapshotService> service = service(ctx);
        if (service.isEmpty()) {
            return 0;
        }
        int days = IntegerArgumentType.getInteger(ctx, "days");
        List<Group> candidates = SnapshotOps.trimCandidates(service.get(), days);
        if (candidates.isEmpty()) {
            return ok(ctx, msg("trim.none", "No backpack's newest snapshot is older than %s days.", days));
        }
        int files = candidates.stream().mapToInt(Group::versions).sum();
        if (!confirmed) {
            return ok(ctx, msg(
                    "trim.preview",
                    "%s of %s backpacks (%s snapshots) have not been used for more than %s days. Nothing was deleted; add 'confirm' to delete them.",
                    candidates.size(),
                    SnapshotOps.backpackCount(service.get()),
                    files,
                    days));
        }
        int deleted = SnapshotOps.trimDelete(service.get(), candidates);
        return ok(ctx, msg(
                "trim.done",
                "Deleted %s snapshot(s) of %s backpack(s). The backpacks themselves are untouched; a snapshot is written again when one is next used.",
                deleted,
                candidates.size()));
    }
}
