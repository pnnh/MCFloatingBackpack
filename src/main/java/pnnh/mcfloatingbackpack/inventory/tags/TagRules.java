package pnnh.mcfloatingbackpack.inventory.tags;

/**
 * 018: which tags are allowed. A tag is 1 to 16 visible ASCII characters with no space and no capital letter, so
 * comparing tags is plain string equality and a later filter feature never has to guess at case. Pure logic with no
 * Minecraft types: the client's text box, the server's request check and the tests all use it.
 */
public final class TagRules {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 16;
    /** The most tags one item category can carry. */
    public static final int MAX_TAGS_PER_ITEM = 10;

    private TagRules() {
    }

    public static boolean acceptsChar(char c) {
        return c >= '!' && c <= '~' && !(c >= 'A' && c <= 'Z');
    }

    public static boolean isValid(String tag) {
        if (tag == null || tag.length() < MIN_LENGTH || tag.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < tag.length(); i++) {
            if (!acceptsChar(tag.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Keeps the accepted characters (in order) and cuts to {@link #MAX_LENGTH}; used for pasted text. */
    public static String filter(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < MAX_LENGTH; i++) {
            char c = text.charAt(i);
            if (acceptsChar(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
