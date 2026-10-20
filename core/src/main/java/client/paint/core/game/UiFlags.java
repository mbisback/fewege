package client.paint.core.game;

/** Client-side display switches that modules can set and the adapter reads (Screenshot Mode). */
public final class UiFlags {
    public volatile boolean hideHud, hideChat;
    public void reset() { hideHud = false; hideChat = false; }
}
