package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;

class CategoryStyleTest {
    @Test
    void everyCategoryHasAStyle() {
        for (String id : ExtensionCategories.IDS) {
            assertTrue(CategoryStyle.known(id), id);
            assertEquals(id + "_extension", CategoryStyle.ghostItemId(id));
        }
    }

    @Test
    void allTheEdgeColoursAreDifferent() {
        Set<Integer> seen = new HashSet<>();
        for (String id : ExtensionCategories.IDS) {
            assertTrue(seen.add(CategoryStyle.edge(id)), id);
        }
    }

    @Test
    void theEdgeIsOpaqueAndTheFillIsTheSameColourTranslucent() {
        for (String id : ExtensionCategories.IDS) {
            assertEquals(0xFF, CategoryStyle.edge(id) >>> 24, id);
            int fill = CategoryStyle.fill(id);
            assertEquals(CategoryStyle.edge(id) & 0xFFFFFF, fill & 0xFFFFFF, id);
            int alpha = fill >>> 24;
            assertTrue(alpha > 0x10 && alpha < 0xC0, id + " alpha " + alpha);
        }
    }

    @Test
    void noEdgeColourIsTheColourOfTheSlotItself() {
        for (String id : ExtensionCategories.IDS) {
            assertNotEquals(0xFF8B8B8B, CategoryStyle.edge(id), id);
        }
    }

    @Test
    void anUnknownCategoryIsNeutralAndHasNoGhost() {
        assertFalse(CategoryStyle.known("zzz"));
        assertEquals(0xFF808080, CategoryStyle.edge("zzz"));
        assertNull(CategoryStyle.ghostItemId("zzz"));
        assertNull(CategoryStyle.ghostItemId(null));
    }

    @Test
    void theUniversalCategoryHasAStyleOfItsOwnAndNoGhost() {
        assertTrue(CategoryStyle.known("general"));
        assertNull(CategoryStyle.ghostItemId("general"));
        for (String id : ExtensionCategories.IDS) {
            assertNotEquals(CategoryStyle.edge(id), CategoryStyle.edge("general"), id);
        }
        assertNotEquals(0xFF8B8B8B, CategoryStyle.edge("general"));
        assertEquals(0xFF, CategoryStyle.edge("general") >>> 24);
        assertEquals(CategoryStyle.edge("general") & 0xFFFFFF, CategoryStyle.fill("general") & 0xFFFFFF);
        // a near white at the alpha of the ten could not be seen on the slot's grey: the universal fill is the stronger of the eleven
        int generalAlpha = CategoryStyle.fill("general") >>> 24;
        for (String id : ExtensionCategories.IDS) {
            assertTrue(generalAlpha > (CategoryStyle.fill(id) >>> 24), id);
        }
        assertTrue(generalAlpha < 0xC0);
    }

    /** CIE76 distance in Lab between two 0xRRGGBB colours. */
    private static double deltaE(int a, int b) {
        double[] x = lab(a);
        double[] y = lab(b);
        return Math.sqrt(Math.pow(x[0] - y[0], 2) + Math.pow(x[1] - y[1], 2) + Math.pow(x[2] - y[2], 2));
    }

    private static double[] lab(int rgb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double u = ((rgb >> (16 - 8 * i)) & 0xFF) / 255.0;
            c[i] = u > 0.04045 ? Math.pow((u + 0.055) / 1.055, 2.4) : u / 12.92;
        }
        double x = (0.4124 * c[0] + 0.3576 * c[1] + 0.1805 * c[2]) / 0.95047;
        double y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
        double z = (0.0193 * c[0] + 0.1192 * c[1] + 0.9505 * c[2]) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }

    /** 051: with nineteen categories and the universal row the colours must still be told apart, and told from the slot's own grey. */
    @Test
    void noTwoCategoriesLookAlikeAndNoneLooksLikeTheSlot() {
        java.util.List<String> all = ExtensionCategories.ALL;
        for (int i = 0; i < all.size(); i++) {
            assertTrue(deltaE(CategoryStyle.edge(all.get(i)) & 0xFFFFFF, 0x8B8B8B) >= 20, all.get(i) + " is too close to the slot grey");
            for (int j = i + 1; j < all.size(); j++) {
                double d = deltaE(CategoryStyle.edge(all.get(i)) & 0xFFFFFF, CategoryStyle.edge(all.get(j)) & 0xFFFFFF);
                assertTrue(d >= 15, all.get(i) + " and " + all.get(j) + " differ by only " + d);
            }
        }
    }
}
