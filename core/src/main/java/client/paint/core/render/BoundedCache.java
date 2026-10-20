package client.paint.core.render;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Size-bounded LRU cache for render data (label layouts, per-entity results). Bounded so it cannot
 * grow without limit; {@link #clear()} must be called on world unload / dimension change / module disable.
 */
public final class BoundedCache<K, V> {
    private final LinkedHashMap<K, V> map;

    public BoundedCache(int maxEntries) {
        final int cap = Math.max(1, maxEntries);
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > cap; }
        };
    }

    public V get(K k) { return map.get(k); }
    public void put(K k, V v) { map.put(k, v); }
    public void remove(K k) { map.remove(k); }
    public int size() { return map.size(); }
    public void clear() { map.clear(); }
}
