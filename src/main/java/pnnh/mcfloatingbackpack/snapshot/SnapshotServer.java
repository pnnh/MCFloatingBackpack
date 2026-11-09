package pnnh.mcfloatingbackpack.snapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Page;
import pnnh.mcfloatingbackpack.snapshot.SnapshotOps.PreviewResult;
import pnnh.mcfloatingbackpack.snapshot.SnapshotOps.Requester;
import pnnh.mcfloatingbackpack.snapshot.net.ActionKind;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;
import pnnh.mcfloatingbackpack.snapshot.net.Row;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotActionPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotResultPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.VersionRow;

/**
 * 031: what the server does when the snapshot window asks. The first thing every request does is work out whether the requester
 * has permission level 2 ({@code player.hasPermissions(2)}, the same test the commands use); nothing the client sends says who it
 * is. A request outside the limits of {@link SnapshotRequestRules} changes nothing and is not answered; a request for something
 * the requester may not see is answered as if it did not exist. Runs on the server's main thread.
 */
public final class SnapshotServer {
    private SnapshotServer() {
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void refuse(ServerPlayer player, String what) {
        MCFloatingBackpack.LOGGER.debug("Refused a bad snapshot {} request from {}", what, player.getGameProfile().getName());
    }

    // ---------------------------------------------------------------- the list

    public static void onList(ServerPlayer player, SnapshotListRequestPayload request) {
        Scope scope = Scope.fromByte(request.scope());
        if (scope == null
                || !SnapshotRequestRules.validName(request.owner())
                || !SnapshotRequestRules.validFilterDays(request.inactiveDays())
                || !SnapshotRequestRules.validPage(request.page())
                || !SnapshotRequestRules.validPageSize(request.pageSize())) {
            refuse(player, "list");
            return;
        }
        Requester who = Requester.of(player);
        Optional<SnapshotService> service = SnapshotService.get();
        if (service.isEmpty()) {
            send(player, new SnapshotListPayload(who.op(), Scope.MINE.code(), 1, 1, 0, List.of()));
            send(player, SnapshotResultPayload.of(ResultCode.UNAVAILABLE));
            return;
        }
        List<Group> visible = SnapshotOps.visibleGroups(service.get(), who);
        long now = System.currentTimeMillis();
        Page page = SnapshotOps.page(visible, who, scope, request.owner(), request.inactiveDays(), request.page(), request.pageSize(), now);
        if (!page.inRange()) {
            // a page that is gone (the list got shorter): the last one
            page = SnapshotOps.page(visible, who, scope, request.owner(), request.inactiveDays(), page.pages(), request.pageSize(), now);
        }
        List<Row> rows = page.items().stream().map(SnapshotServer::row).toList();
        send(player, new SnapshotListPayload(
                who.op(), SnapshotAccess.effectiveScope(who.op(), scope).code(), page.page(), page.pages(), page.total(), rows));
    }

    private static Row row(Group group) {
        SnapshotMeta newest = group.newest();
        return new Row(group.uuid(), newest.owner(), newest.ownerName(), newest.capacity(), newest.time(), newest.slots(), group.versions());
    }

    // ---------------------------------------------------------------- versions and preview

    public static void onVersions(ServerPlayer player, SnapshotVersionsRequestPayload request) {
        Optional<SnapshotService> service = SnapshotService.get();
        List<VersionRow> versions = List.of();
        if (service.isPresent()) {
            versions = SnapshotOps.versions(service.get(), Requester.of(player), request.backpack()).stream()
                    .limit(SnapshotRequestRules.MAX_VERSIONS)
                    .map(meta -> new VersionRow(meta.time(), meta.slots(), meta.ownerName(), meta.capacity()))
                    .toList();
        }
        send(player, new SnapshotVersionsPayload(request.backpack(), versions));
    }

    public static void onPreview(ServerPlayer player, SnapshotPreviewRequestPayload request) {
        if (request.time() <= 0) {
            refuse(player, "preview");
            return;
        }
        Optional<SnapshotService> service = SnapshotService.get();
        if (service.isEmpty()) {
            send(player, SnapshotPreviewPayload.failed(request.backpack(), request.time(), ResultCode.UNAVAILABLE));
            return;
        }
        PreviewResult result = SnapshotOps.preview(player.server, service.get(), Requester.of(player), request.backpack(), request.time());
        if (!result.ok()) {
            send(player, SnapshotPreviewPayload.failed(request.backpack(), request.time(), result.failure()));
            return;
        }
        send(player, new SnapshotPreviewPayload(request.backpack(), request.time(), null, result.capacity(), result.slots(), result.items()));
    }

    // ---------------------------------------------------------------- actions (permission level 2)

    public static void onAction(ServerPlayer player, SnapshotActionPayload request) {
        ActionKind kind = ActionKind.fromByte(request.kind());
        if (kind == null
                || !SnapshotRequestRules.validName(request.target())
                || !SnapshotRequestRules.validTrimDays(request.days())
                || request.time() < 0) {
            refuse(player, "action");
            return;
        }
        Requester who = Requester.of(player);
        if (!who.op()) {
            send(player, SnapshotResultPayload.of(ResultCode.NO_PERMISSION));
            return;
        }
        Optional<SnapshotService> service = SnapshotService.get();
        if (service.isEmpty()) {
            send(player, SnapshotResultPayload.of(ResultCode.UNAVAILABLE));
            return;
        }
        switch (kind) {
            case RESTORE -> restore(player, service.get(), who, request);
            case DELETE_ONE -> deleteOne(player, service.get(), who, request);
            case DELETE_ALL -> deleteAll(player, service.get(), who, request);
            case TRIM_PREVIEW -> {
                List<Group> candidates = SnapshotOps.trimCandidates(service.get(), request.days());
                send(player, new SnapshotResultPayload(
                        ResultCode.TRIM_PREVIEWED, "", "", candidates.size(), candidates.stream().mapToInt(Group::versions).sum()));
            }
            case TRIM_CONFIRM -> {
                // worked out again: what the preview counted is not trusted
                List<Group> candidates = SnapshotOps.trimCandidates(service.get(), request.days());
                int files = SnapshotOps.trimDelete(service.get(), candidates);
                send(player, new SnapshotResultPayload(ResultCode.TRIM_DONE, "", "", files, candidates.size()));
            }
        }
    }

    private static void restore(ServerPlayer player, SnapshotService service, Requester who, SnapshotActionPayload request) {
        Optional<Group> group = SnapshotOps.find(SnapshotOps.visibleGroups(service, who), request.backpack());
        if (group.isEmpty()) {
            send(player, SnapshotResultPayload.of(ResultCode.NOT_FOUND));
            return;
        }
        SnapshotMeta chosen = service.store().versions(request.backpack()).stream()
                .filter(meta -> meta.time() == request.time())
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            send(player, SnapshotResultPayload.of(ResultCode.BAD_VERSION));
            return;
        }
        ServerPlayer target = request.target().isEmpty() ? player : player.server.getPlayerList().getPlayerByName(request.target());
        if (target == null) {
            send(player, SnapshotResultPayload.of(ResultCode.TARGET_OFFLINE));
            return;
        }
        ResultCode result = SnapshotOps.restore(player.server, service, chosen, target);
        if (result != ResultCode.RESTORED) {
            send(player, SnapshotResultPayload.of(result));
            return;
        }
        send(player, new SnapshotResultPayload(
                ResultCode.RESTORED, BackpackIds.of(request.backpack()), target.getGameProfile().getName(), 0, 0));
    }

    private static void deleteOne(ServerPlayer player, SnapshotService service, Requester who, SnapshotActionPayload request) {
        if (SnapshotOps.find(SnapshotOps.visibleGroups(service, who), request.backpack()).isEmpty()) {
            send(player, SnapshotResultPayload.of(ResultCode.NOT_FOUND));
            return;
        }
        List<SnapshotMeta> versions = service.store().versions(request.backpack());
        int number = 0;
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).time() == request.time()) {
                number = i + 1;
            }
        }
        if (number == 0) {
            send(player, SnapshotResultPayload.of(ResultCode.BAD_VERSION));
            return;
        }
        if (!SnapshotOps.deleteOne(service, request.backpack(), request.time())) {
            send(player, SnapshotResultPayload.of(ResultCode.FAILED));
            return;
        }
        send(player, new SnapshotResultPayload(ResultCode.DELETED_ONE, BackpackIds.of(request.backpack()), "", number, 0));
    }

    private static void deleteAll(ServerPlayer player, SnapshotService service, Requester who, SnapshotActionPayload request) {
        UUID backpack = request.backpack();
        if (SnapshotOps.find(SnapshotOps.visibleGroups(service, who), backpack).isEmpty()) {
            send(player, SnapshotResultPayload.of(ResultCode.NOT_FOUND));
            return;
        }
        int deleted = SnapshotOps.deleteAll(service, backpack);
        send(player, new SnapshotResultPayload(ResultCode.DELETED_ALL, BackpackIds.of(backpack), "", deleted, 0));
    }
}
