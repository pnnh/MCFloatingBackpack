package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 059: the language key of the refusal notice and the server config's comments. Plain text checks. */
class ServerCompatResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final String KEY = "message.mcfloatingbackpack.compat.server_off";

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String value(String json) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(KEY) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        assertTrue(m.find(), KEY);
        return m.group(1);
    }

    @Test
    void bothLanguagesHaveTheKeyWithOneArgument() throws IOException {
        String en = value(read(LANG.resolve("en_us.json")));
        String zh = value(read(LANG.resolve("zh_cn.json")));
        assertEquals(1, Pattern.compile("%1\\$s").matcher(en).results().count());
        assertEquals(1, Pattern.compile("%1\\$s").matcher(zh).results().count());
        assertTrue(en.contains("server setting"), en);
        assertTrue(zh.contains("服务器"), zh);
    }

    @Test
    void theConfigCommentSaysWhatEachLevelDoesOnTheServer() throws IOException {
        String config = read(SRC.resolve("compat/ThirdPartyConfig.java"));
        for (String part : new String[] {
            "ON THE SERVER", "Vanilla (minecraft) cannot be configured", "FULL    - the mod-specific adaptations run on the server",
            "GENERIC - they do not run", "OFF     - the server also refuses every item exchange", "the stricter of the two counts",
            "Changes take effect without reopening the world", "Example: modLevels = [", "integratedterminals=GENERIC"}) {
            assertTrue(config.contains(part), part);
        }
        assertTrue(config.contains(".defineListAllowEmpty(\"modLevels\", List.of(),"), "empty by default");
        assertTrue(config.indexOf("builder.push(\"compat\")") < config.indexOf("modLevels = builder.comment("), "in the [compat] section");
    }
}
