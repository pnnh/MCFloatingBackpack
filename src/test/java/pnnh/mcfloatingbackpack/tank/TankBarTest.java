package pnnh.mcfloatingbackpack.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 048: the pixels of the level bar. */
class TankBarTest {
    @Test
    void emptyDrawsNothing() {
        assertEquals(0, TankBar.fillPixels(0, 16_000, TankBar.HEIGHT));
        assertEquals(0, TankBar.fillPixels(-5, 16_000, TankBar.HEIGHT));
    }

    @Test
    void aLittleStillShowsOnePixel() {
        assertEquals(1, TankBar.fillPixels(1, 256_000, TankBar.HEIGHT));
    }

    @Test
    void halfFullIsHalfTheBar() {
        assertEquals(7, TankBar.fillPixels(8_000, 16_000, TankBar.HEIGHT));
    }

    @Test
    void fullAndOverFullFillTheBar() {
        assertEquals(TankBar.HEIGHT, TankBar.fillPixels(16_000, 16_000, TankBar.HEIGHT));
        assertEquals(TankBar.HEIGHT, TankBar.fillPixels(20_000, 16_000, TankBar.HEIGHT));
    }

    @Test
    void noCapacityDrawsNothing() {
        assertEquals(0, TankBar.fillPixels(100, 0, TankBar.HEIGHT));
    }
}
