package client.paint.core;

/** Ban-risk tag shown next to each module (matches the SAFE/LOW/MED/HIGH badges in Reference 1). */
public enum Risk {
    SAFE("Client-side only. Servers cannot see it."),
    LOW("Hard for a server to notice."),
    MED("Caught by good anti-cheats."),
    HIGH("Instant ban on most servers. Fine on anarchy servers.");

    public final String description;

    Risk(String description) {
        this.description = description;
    }
}
