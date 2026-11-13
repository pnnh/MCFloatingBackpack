package pnnh.mcfloatingbackpack.client.jukebox;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelState;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.jukebox.JukeboxPlayer;
import pnnh.mcfloatingbackpack.jukebox.PlaylistReader;
import pnnh.mcfloatingbackpack.jukebox.PlaylistReader.Track;

/**
 * 053: the mobile jukebox's player on the client: it reads the playlist, drives the {@link JukeboxPlayer} and plays the tracks. It reads the
 * <em>stack</em> of the backpack the B key opens (its {@code BACKPACK_CONTENTS}, which the client has in full), not the panel's view, because
 * the view is emptied when the panel is closed and the music goes on then. A track is over when its length in the song's data has run out
 * (counted only while the game is not paused, as the vanilla jukebox counts) or when its sound was heard and has stopped; the sound
 * engine's {@code isActive} alone cannot say, for with the volume at zero the engine does not make a channel at all. There is no pause (the
 * sound engine has none for a single sound, and reaching for its inside is not done): stopping and playing again starts the track again.
 * Only the player's own client plays it. Client thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class JukeboxPlayback {
    /** The category of the rows whose discs make the playlist. */
    private static final String DISC = "disc";
    /** The slack added to a track's length, as the vanilla jukebox adds it. */
    private static final int LENGTH_SLACK_TICKS = 20;

    private static final JukeboxPlayer PLAYER = new JukeboxPlayer(new Random());

    private static MobileJukeboxSound sound;
    private static int ticks;
    private static int totalTicks;
    private static boolean seenActive;
    /** Whose backpack the music is of: if the backpack the B key opens is another one, the music stops. */
    private static String scope;
    /** The playlist as it was read at the last tick (or action). */
    private static List<Track<ItemStack>> tracks = List.of();
    private static Component title;

    private JukeboxPlayback() {
    }

    /** What the page shows. */
    public record Status(boolean hasDiscs, boolean playing, int number, int count, Component title, boolean loopOne, boolean shuffle) {
    }

    public static Status status() {
        List<String> keys = PlaylistReader.keys(tracks);
        int at = PLAYER.currentIndex(keys);
        return new Status(!tracks.isEmpty(), PLAYER.playing(), at < 0 ? 0 : at + 1, tracks.size(), title, PLAYER.loopOne(), PLAYER.shuffle());
    }

    // ---- what the buttons do ----

    public static void play() {
        refresh(Minecraft.getInstance());
        apply(PLAYER.play(PlaylistReader.keys(tracks)));
    }

    public static void stop() {
        apply(PLAYER.stop());
    }

    public static void rewind() {
        refresh(Minecraft.getInstance());
        apply(PLAYER.rewind(PlaylistReader.keys(tracks)));
    }

    public static void next() {
        refresh(Minecraft.getInstance());
        apply(PLAYER.next(PlaylistReader.keys(tracks)));
    }

    public static void previous() {
        refresh(Minecraft.getInstance());
        apply(PLAYER.previous(PlaylistReader.keys(tracks)));
    }

    public static void toggleLoopOne() {
        PLAYER.setLoopOne(!PLAYER.loopOne());
    }

    public static void toggleShuffle() {
        PLAYER.setShuffle(!PLAYER.shuffle());
    }

    // ---- every client tick, and leaving the world ----

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        if (!PLAYER.playing() && !JukeboxPanelState.get().isOpen()) {
            return;
        }
        String now = refresh(minecraft);
        if (!PLAYER.playing()) {
            return;
        }
        if (now == null || !now.equals(scope)) {
            // the mobile jukebox left the right area, or the backpack the B key opens is another one
            apply(PLAYER.stop());
            return;
        }
        if (!minecraft.isPaused()) {
            ticks++;
        }
        boolean active = sound != null && minecraft.getSoundManager().isActive(sound);
        if (active) {
            seenActive = true;
        }
        if (ticks >= totalTicks || seenActive && !active) {
            apply(PLAYER.finished(PlaylistReader.keys(tracks)));
        }
    }

    /** Leaving the world, or no player: stop and forget everything, the switches too. */
    public static void reset() {
        stopSound();
        PLAYER.reset();
        tracks = List.of();
        scope = null;
        title = null;
    }

    // ---- inside ----

    /**
     * Reads the playlist from the backpack the B key opens.
     *
     * @return what the music would belong to now, or {@code null} when that backpack has no mobile jukebox in its right area (the playlist
     *         is then empty)
     */
    private static String refresh(Minecraft minecraft) {
        Player player = minecraft.player;
        BackpackRef ref = player == null ? null : BackpackRef.resolve(player);
        BackpackContents contents = ref == null ? null : ref.getStack(player).get(ModDataComponents.BACKPACK_CONTENTS.get());
        if (contents == null || !hasMobileJukebox(contents)) {
            tracks = List.of();
            return null;
        }
        HolderLookup.Provider registries = minecraft.level.registryAccess();
        tracks = PlaylistReader.read(contents.rows().visible(), DISC, stack -> keyOf(registries, stack));
        BackpackIdentity identity = ref.getStack(player).get(ModDataComponents.BACKPACK_IDENTITY.get());
        return identity != null ? identity.uuid().toString() : ref.source() + ":" + ref.invIndex();
    }

    private static boolean hasMobileJukebox(BackpackContents contents) {
        for (ItemStack tool : contents.tools().right()) {
            if (tool.is(ModItems.MOBILE_JUKEBOX.get())) {
                return true;
            }
        }
        return false;
    }

    /** What song a stack is: the key of its song, or its item's id if the song has none; {@code null} for an empty cell or a stack that is no disc. */
    private static String keyOf(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Optional<Holder<JukeboxSong>> song = JukeboxSong.fromStack(registries, stack);
        if (song.isEmpty()) {
            return null;
        }
        return song.get().unwrapKey().map(key -> key.location().toString())
                .orElseGet(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static void apply(JukeboxPlayer.Command command) {
        switch (command.kind()) {
            case START -> start(command.index());
            case STOP -> stopSound();
            case NONE -> {
            }
        }
    }

    private static void start(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || index < 0 || index >= tracks.size()) {
            stopSound();
            return;
        }
        Optional<Holder<JukeboxSong>> found = JukeboxSong.fromStack(minecraft.level.registryAccess(), tracks.get(index).item());
        if (found.isEmpty()) {
            stopSound();
            PLAYER.stop();
            return;
        }
        JukeboxSong song = found.get().value();
        stopSound();
        // the way a vanilla jukebox begins a record: the background music gives way, and the title is shown
        minecraft.getMusicManager().stopPlaying();
        sound = new MobileJukeboxSound(song.soundEvent().value());
        minecraft.getSoundManager().play(sound);
        minecraft.gui.setNowPlaying(song.description());
        title = song.description();
        ticks = 0;
        totalTicks = (int) (song.lengthInSeconds() * 20.0F) + LENGTH_SLACK_TICKS;
        seenActive = false;
        scope = currentScope(minecraft);
    }

    private static String currentScope(Minecraft minecraft) {
        Player player = minecraft.player;
        BackpackRef ref = player == null ? null : BackpackRef.resolve(player);
        if (ref == null) {
            return null;
        }
        BackpackIdentity identity = ref.getStack(player).get(ModDataComponents.BACKPACK_IDENTITY.get());
        return identity != null ? identity.uuid().toString() : ref.source() + ":" + ref.invIndex();
    }

    private static void stopSound() {
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
    }
}
