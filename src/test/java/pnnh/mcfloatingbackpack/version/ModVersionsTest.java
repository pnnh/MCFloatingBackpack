package pnnh.mcfloatingbackpack.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.version.ModVersions.Result;

class ModVersionsTest {
    private static Result compare(String client, String server) {
        return ModVersions.compare(client, server);
    }

    @Test
    void sameVersionMatches() {
        assertEquals(Result.MATCH, compare("0.1.0", "0.1.0"));
        assertEquals(Result.MATCH, compare("1.4.2", "1.4.2"));
    }

    @Test
    void thePatchTakesNoPart() {
        assertEquals(Result.MATCH, compare("0.2.1", "0.2.0"));
        assertEquals(Result.MATCH, compare("0.2.0", "0.2.9"));
        assertEquals(Result.MATCH, compare("1.0.0", "1.0.37"));
    }

    @Test
    void aDifferentMinorIsOlderOrNewer() {
        assertEquals(Result.CLIENT_OLDER, compare("0.1.0", "0.2.0"));
        assertEquals(Result.CLIENT_NEWER, compare("0.3.0", "0.2.0"));
        assertEquals(Result.CLIENT_OLDER, compare("0.2.9", "0.3.0"));
    }

    @Test
    void aDifferentMajorWinsOverTheMinor() {
        assertEquals(Result.CLIENT_NEWER, compare("1.0.0", "0.9.9"));
        assertEquals(Result.CLIENT_OLDER, compare("0.9.9", "1.0.0"));
        assertEquals(Result.CLIENT_OLDER, compare("1.9.0", "2.0.0"));
    }

    @Test
    void aPrereleaseRanksBelowTheReleaseOfTheSameVersion() {
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-rc1", "0.2.0"));
        assertEquals(Result.CLIENT_NEWER, compare("0.2.0", "0.2.0-rc1"));
        // the server's 0.2.1 has no pre-release, so it ranks above the client's pre-release even though the patch is not compared
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-rc1", "0.2.1"));
    }

    @Test
    void theSamePrereleaseMatchesWhateverThePatch() {
        assertEquals(Result.MATCH, compare("0.2.0-rc1", "0.2.1-rc1"));
        assertEquals(Result.MATCH, compare("0.2.0-rc1", "0.2.0-rc1"));
    }

    @Test
    void prereleaseIdentifiersAreOrderedLikeSemanticVersioning() {
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-rc1", "0.2.0-rc2"));
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-rc.2", "0.2.0-rc.10")); // numbers by value
        assertEquals(Result.CLIENT_NEWER, compare("0.2.0-rc.10", "0.2.0-rc.2"));
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-alpha", "0.2.0-beta")); // letters by text
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-1", "0.2.0-alpha")); // a number ranks below letters
        assertEquals(Result.CLIENT_OLDER, compare("0.2.0-rc", "0.2.0-rc.1")); // the shorter ranks lower
    }

    @Test
    void buildMetadataTakesNoPart() {
        assertEquals(Result.MATCH, compare("0.2.0+abc", "0.2.0"));
        assertEquals(Result.MATCH, compare("0.2.0+abc", "0.2.0+def"));
        assertEquals(Result.CLIENT_OLDER, compare("0.1.0+x", "0.2.0+y"));
        assertEquals(Result.MATCH, compare("0.2.0-rc1+a", "0.2.0-rc1+b"));
    }

    @Test
    void aLeadingVAndAMissingPatchAreAccepted() {
        assertEquals(Result.MATCH, compare("v0.2.0", "0.2.0"));
        assertEquals(Result.MATCH, compare("0.2", "0.2.5"));
        assertEquals(Result.CLIENT_OLDER, compare("0.1", "0.2.0"));
    }

    @Test
    void whatIsNotASemanticVersionCannotBeCompared() {
        for (String bad : new String[] {null, "", "   ", "banana", "1.x.0", "0.2.0.0", "0", "1", "-1.0.0", "0.-2.0", "0.2.0-", "0.2.0-a..b", "0.2.0+", "0.2.0-rc_1", "0.2.0 beta"}) {
            assertEquals(Result.UNCOMPARABLE, compare(bad, "0.2.0"), "client " + bad);
            assertEquals(Result.UNCOMPARABLE, compare("0.2.0", bad), "server " + bad);
        }
        assertEquals(Result.UNCOMPARABLE, compare(null, null));
        assertEquals(Result.UNCOMPARABLE, compare("banana", "banana"));
    }

    @Test
    void aHugeNumberDoesNotOverflow() {
        assertEquals(Result.UNCOMPARABLE, compare("99999999999999999999.0.0", "0.2.0"));
        assertEquals(Result.CLIENT_NEWER, compare("999999999999999999.0.0", "0.2.0"));
    }

    private static Result mirror(Result r) {
        return switch (r) {
            case CLIENT_OLDER -> Result.CLIENT_NEWER;
            case CLIENT_NEWER -> Result.CLIENT_OLDER;
            default -> r;
        };
    }

    @Test
    void swappingTheTwoMirrorsTheResult() {
        List<String> versions = List.of(
                "0.1.0", "0.1.9", "0.2.0", "0.2.1", "0.2.0-rc1", "0.2.0-rc2", "0.2.0-rc.10", "0.2.0+abc", "1.0.0", "v1.0.0",
                "0.3", "banana", "", "0.2.0-alpha");
        for (String a : versions) {
            for (String b : versions) {
                assertEquals(mirror(compare(a, b)), compare(b, a), a + " vs " + b);
            }
        }
    }

    @Test
    void parsingDropsTheBuildMetadataAndKeepsThePrerelease() {
        Optional<ModVersions.Parsed> parsed = ModVersions.parse("1.2.3-rc.1+build.5");
        assertTrue(parsed.isPresent());
        assertEquals(new BigInteger("1"), parsed.get().major());
        assertEquals(new BigInteger("2"), parsed.get().minor());
        assertEquals(new BigInteger("3"), parsed.get().patch());
        assertEquals(List.of("rc", "1"), parsed.get().prerelease());
        assertEquals(BigInteger.ZERO, ModVersions.parse("1.2").get().patch());
    }
}
