package pnnh.mcfloatingbackpack.client.jukebox;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 053: the sound of one track of the mobile jukebox. It is in the player's ear: relative to the listener and not attenuated, so it needs no
 * position that follows the player. Its category is records, so the volume follows the game's "Jukebox/Note Blocks" slider. It never loops
 * (the player starts the next track itself) and is not heard by anybody else (it is only ever created on the player's own client).
 */
@OnlyIn(Dist.CLIENT)
public final class MobileJukeboxSound extends AbstractSoundInstance {
    public MobileJukeboxSound(SoundEvent soundEvent) {
        super(soundEvent, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = false;
    }
}
