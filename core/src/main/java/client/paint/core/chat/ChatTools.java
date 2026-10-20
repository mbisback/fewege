package client.paint.core.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chat timestamps and keyword filtering (pure logic; the in-game hook just calls these). */
public final class ChatTools {
    private ChatTools() {}

    /** "[HH:mm:ss] " (24h) or "[h:mm:ss a] " in the given zone. */
    public static String timestamp(long epochMs, ZoneId zone, boolean use24h) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern(use24h ? "HH:mm:ss" : "h:mm:ss a", Locale.ENGLISH);
        return "[" + f.format(Instant.ofEpochMilli(epochMs).atZone(zone)) + "] ";
    }

    public static final class Filter {
        private final List<String> blocked = new ArrayList<>();

        /** Blank terms are ignored. Matching is case-insensitive substring. */
        public boolean add(String term) {
            if (term == null || term.isBlank()) return false;
            blocked.add(term.strip().toLowerCase(Locale.ROOT));
            return true;
        }

        public void clear() { blocked.clear(); }

        /** @return true if the message should be hidden. */
        public boolean shouldHide(String message) {
            if (message == null || blocked.isEmpty()) return false;
            String m = message.toLowerCase(Locale.ROOT);
            for (String b : blocked) if (m.contains(b)) return true;
            return false;
        }
    }
}
