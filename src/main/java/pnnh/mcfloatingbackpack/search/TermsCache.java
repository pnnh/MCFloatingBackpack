package pnnh.mcfloatingbackpack.search;

import java.util.LinkedHashMap;
import java.util.Map;

/** 043: a small least-recently-used cache. Not thread-safe: the owner synchronizes. No Minecraft types. */
public final class TermsCache<K, V> {
    private final LinkedHashMap<K, V> map;

    public TermsCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    /** The cached value, or null; a hit counts as a use. */
    public V get(K key) {
        return map.get(key);
    }

    public void put(K key, V value) {
        map.put(key, value);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }
}
