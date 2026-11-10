package pnnh.mcfloatingbackpack.snapshot.net;

/** 031: one snapshot of a backpack, in a version list (newest first; its number is its place in the list, from 1). Pure data. */
public record VersionRow(long time, int slots, String ownerName, int capacity) {
}
