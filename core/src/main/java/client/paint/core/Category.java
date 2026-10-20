package client.paint.core;

/** Category panels, in the left-to-right order used by Reference 1. */
public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    BASE_HUNT("Base Hunt"),
    DONUT("Donut"),
    MISC("Misc");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
