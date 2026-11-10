package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.UUID;

/** 031: one backpack in a list page: its newest snapshot's facts and how many snapshots it has. Pure data. */
public record Row(UUID uuid, UUID owner, String ownerName, int capacity, long newestTime, int slots, int versions) {
}
