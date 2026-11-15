package android.os;

import java.util.HashMap;
import java.util.Map;

public class Bundle {
    private final Map<String, Object> map = new HashMap<>();

    public Bundle() {}

    public void putBoolean(String key, boolean value) {
        map.put(key, value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return defaultValue;
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }
}
