package pnnh.mcfloatingbackpack.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 020 / constitution VII: checks of the dependency rules, plain Java so the test class path needs nothing but JUnit.
 * Each check returns its violations (empty = passes).
 *
 * <ul>
 *   <li>{@link #checkModsToml}: every dependency except {@code neoforge} and {@code minecraft} is {@code optional}.
 *   <li>{@link #checkImports}: an import outside the allowed prefixes only appears under {@code compat/<modid>/}.
 *   <li>{@link #checkBuildGradle}: third-party mod coordinates are only declared {@code compileOnly} or {@code localRuntime}.
 * </ul>
 */
final class DependencyRules {
    record SourceFile(String path, String text) {
    }

    private static final Set<String> REQUIRED_DEPENDENCIES = Set.of("neoforge", "minecraft");

    /** Imports anywhere in the mod may start with these: the JDK, Minecraft, NeoForge and what they ship with, and the mod itself. */
    private static final List<String> ALLOWED_IMPORT_PREFIXES = List.of(
            "java.", "javax.", "com.mojang.", "net.minecraft.", "net.neoforged.", "io.netty.", "com.google.",
            "org.slf4j.", "org.lwjgl.", "org.apache.", "it.unimi.", "pnnh.mcfloatingbackpack.");

    private static final Pattern COMPAT_MOD_DIR = Pattern.compile("(^|/)compat/[^/]+/[^/]+$");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_.]+)");
    private static final Pattern DEPENDENCY_DECLARATION =
            Pattern.compile("^\\s*(\\w+)\\s+[\"']([^\"':]+):([^\"':]+):([^\"']+)[\"']");
    private static final Set<String> THIRD_PARTY_GROUPS = Set.of("curse.maven", "maven.modrinth", "mezz.jei");
    private static final Set<String> ALLOWED_CONFIGURATIONS = Set.of("compileOnly", "localRuntime");

    private DependencyRules() {
    }

    static List<String> checkModsToml(String toml) {
        List<String> violations = new ArrayList<>();
        String modId = null;
        String type = null;
        boolean inDependency = false;
        for (String line : (toml + "\n[[end]]").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                if (inDependency) {
                    judge(modId, type, violations);
                }
                inDependency = trimmed.startsWith("[[dependencies.");
                modId = null;
                type = null;
            } else if (inDependency) {
                String value = valueOf(trimmed, "modId");
                if (value != null) {
                    modId = value;
                }
                value = valueOf(trimmed, "type");
                if (value != null) {
                    type = value;
                }
            }
        }
        return violations;
    }

    private static void judge(String modId, String type, List<String> violations) {
        if (modId == null) {
            violations.add("a [[dependencies]] block has no modId");
        } else if (!REQUIRED_DEPENDENCIES.contains(modId) && !"optional".equals(type)) {
            violations.add("dependency '" + modId + "' must be type=\"optional\" but is " + (type == null ? "missing a type" : "\"" + type + "\""));
        }
    }

    private static String valueOf(String line, String key) {
        Matcher matcher = Pattern.compile("^" + key + "\\s*=\\s*\"([^\"]*)\"").matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    static List<String> checkImports(List<SourceFile> files) {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : files) {
            String path = file.path().replace('\\', '/');
            boolean inCompatModDir = COMPAT_MOD_DIR.matcher(path).find();
            for (String line : file.text().split("\n")) {
                Matcher matcher = IMPORT.matcher(line);
                if (!matcher.find()) {
                    continue;
                }
                String imported = matcher.group(1);
                boolean allowed = ALLOWED_IMPORT_PREFIXES.stream().anyMatch(imported::startsWith);
                if (!allowed && !inCompatModDir) {
                    violations.add(path + ": imports '" + imported + "' outside compat/<modid>/");
                }
            }
        }
        return violations;
    }

    static List<String> checkBuildGradle(String gradle) {
        List<String> violations = new ArrayList<>();
        int number = 0;
        for (String line : gradle.split("\n")) {
            number++;
            Matcher matcher = DEPENDENCY_DECLARATION.matcher(line);
            if (matcher.find()
                    && THIRD_PARTY_GROUPS.contains(matcher.group(2))
                    && !ALLOWED_CONFIGURATIONS.contains(matcher.group(1))) {
                violations.add("build.gradle:" + number + ": " + matcher.group(2) + ":" + matcher.group(3)
                        + " is declared " + matcher.group(1) + ", only compileOnly or localRuntime is allowed");
            }
        }
        return violations;
    }
}
