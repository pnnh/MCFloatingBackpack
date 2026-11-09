package pnnh.mcfloatingbackpack.inventory.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.Test;

/** 027: what each side knows of the other's cursor, so a value is sent once and never echoed. */
class CursorMirrorTest {
    private static CursorMirror<StringBuilder> mirror() {
        return new CursorMirror<>(
                (a, b) -> Objects.equals(a.toString(), b.toString()), v -> new StringBuilder(v), new StringBuilder());
    }

    private static StringBuilder sb(String s) {
        return new StringBuilder(s);
    }

    @Test
    void startsKnowingAnEmptyCursor() {
        CursorMirror<StringBuilder> m = mirror();
        assertEquals("", m.known().toString());
        assertFalse(m.shouldSend(sb("")));
        assertTrue(m.shouldSend(sb("stone")));
    }

    @Test
    void whatWasSentIsNotSentAgain() {
        CursorMirror<StringBuilder> m = mirror();
        m.sent(sb("stone"));
        assertFalse(m.shouldSend(sb("stone")));
        assertTrue(m.shouldSend(sb("dirt")));
        assertTrue(m.shouldSend(sb("")));
    }

    @Test
    void whatWasReceivedIsNotEchoed() {
        CursorMirror<StringBuilder> m = mirror();
        m.received(sb("iron"));
        assertFalse(m.shouldSend(sb("iron")));
        assertTrue(m.shouldSend(sb("")));
    }

    @Test
    void resetGoesBackToEmpty() {
        CursorMirror<StringBuilder> m = mirror();
        m.sent(sb("stone"));
        m.reset();
        assertEquals("", m.known().toString());
        assertTrue(m.shouldSend(sb("stone")));
        assertFalse(m.shouldSend(sb("")));
    }

    @Test
    void itKeepsACopyNotTheOriginal() {
        CursorMirror<StringBuilder> m = mirror();
        StringBuilder value = sb("stone");
        m.sent(value);
        value.append("!");
        assertEquals("stone", m.known().toString());
        assertTrue(m.shouldSend(value));
        StringBuilder other = sb("gold");
        m.received(other);
        other.append("!");
        assertEquals("gold", m.known().toString());
    }

    @Test
    void aFullRoundTripSendsEachValueOnce() {
        CursorMirror<StringBuilder> client = mirror(); // what the server knows of the client's cursor
        CursorMirror<StringBuilder> server = mirror(); // what the client knows of the server's cursor
        int sent = 0;

        // the client's cursor becomes "stone": reported once
        StringBuilder clientCursor = sb("stone");
        if (client.shouldSend(clientCursor)) {
            client.sent(clientCursor);
            server.received(clientCursor);
            sent++;
        }
        // the server's menu cursor is now "stone" too: nothing to push back
        StringBuilder serverCursor = sb("stone");
        assertFalse(server.shouldSend(serverCursor));

        // the server handles a panel click and the cursor becomes "iron": pushed once
        serverCursor = sb("iron");
        if (server.shouldSend(serverCursor)) {
            server.sent(serverCursor);
            client.received(serverCursor);
            sent++;
        }
        clientCursor = sb("iron");
        // the client sees the new cursor and must not report it back
        assertFalse(client.shouldSend(clientCursor));
        assertEquals(2, sent);
    }
}
