package client.paint.core.hud;

import java.util.ArrayList;
import java.util.List;

/** Small on-screen notification queue: bounded, expiring, newest first. */
public final class NotificationCenter {
    public record Note(String text, long createdMs) {}

    private final List<Note> notes = new ArrayList<>();
    public int maxVisible = 4;
    public long ttlMs = 4000;

    public void push(String text, long nowMs) {
        if (text == null || text.isBlank()) return;
        if (!notes.isEmpty() && notes.get(0).text().equals(text) && nowMs - notes.get(0).createdMs() < 500) return; // de-dupe bursts
        notes.add(0, new Note(text, nowMs));
        while (notes.size() > 16) notes.remove(notes.size() - 1);
    }

    /** Active notes, newest first, at most {@link #maxVisible}. Expired ones are dropped. */
    public List<Note> active(long nowMs) {
        notes.removeIf(n -> nowMs - n.createdMs() > ttlMs);
        return new ArrayList<>(notes.subList(0, Math.min(maxVisible, notes.size())));
    }
}
