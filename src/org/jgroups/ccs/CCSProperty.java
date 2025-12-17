package org.jgroups.ccs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.jgroups.logging.Log;

/**
 * Property used to control run time behavior.
 * Initialized from system properties.
 * Format: [key=]value&...&[key=]value[&LEVEL]
 * 
 * // FIXME: make it faster by creating a map in constructor.
 *
 * @author onoprien
 */
public class CCSProperty {
    
    static private final String SEP = ";";
    static private final String MAP = "=";
    
    private final String name;
    private final String value;
    
    private final Level level;
    private final Map<String,Object> data = new LinkedHashMap<>();
    
    public CCSProperty(String name) {
        this(name, (System.getProperty(name) == null || System.getProperty(name).isBlank()) ? null : System.getProperty(name));
    }
    
    public CCSProperty(String name, String val) {
        this.name = name;
        this.value = val == null || val.isBlank() ? null : val.trim();
        if (value == null) {
            level = Level.OFF;
        } else {
            Level lev = null;
            for (String s : value.split(SEP)) {
                if (!s.isBlank()) {
                    String[] ss = s.split(MAP);
                    switch (ss.length) {
                        case 1 -> {
                            String k = ss[0].trim();
                            if (!k.isEmpty()) {
                                try {
                                    lev = Level.parse(k);
                                } catch (IllegalArgumentException x) {
                                    data.put(k, "");
                                }
                            }
                        }
                        case 2 -> {
                            String k = ss[0].trim();
                            String v = ss[1].trim();
                            if (lev == null && "level".equalsIgnoreCase(k)) {
                                try {
                                    lev = Level.parse(v);
                                } catch (IllegalArgumentException x) {
                                    data.put(k, v);
                                }
                            } else {
                                data.put(k, v);
                            }
                        }
                        default -> {}
                    }
                }
            }
            level = lev == null ? Level.INFO : lev;
        }
    }
        
    /**
     * Returns the maximum logging level among those associated with the specified properties.
     * @param properties Properties to check
     * @return Max logging level.
     */
    static public Level getMaxLevel(CCSProperty... properties) {
        Level out = null;
        for (CCSProperty p : properties) {
            Level level = p.getLevel();
            if (level != null) {
                if (out == null || out.intValue() < level.intValue()) {
                    out = level;
                }
            }
        }
        return out;
    }
    
    /**
     * Returns {@code true} if any value has been specified for this property.
     * @return True if set.
     */
    public boolean isSet() {
        return value != null;
    }
    
    /**
     * Returns String value associated with the specified key.
     * @param key Key.
     * @return Value as a String, or {@code null} if there is no such key.
     */
    public String get(String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
    
    /**
     * Returns double value associated with the specified key.
     * @param key Key.
     * @return Value as a double, or {@code Double.NaN} if there is no such key, or the value cannot be converted to double.
     */
    public double getDouble(String key) {
        Object v = data.get(key);
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException x) {
                return Double.NaN;
            }
        } else {
            return Double.NaN;
        }
    }
    
    /**
     * Returns the double value not associated with any key.
     * @return Double value, or {@code Double.NaN} if there is no value that is not
     *         associated with any key and that can be converted to double.
     */
    public double getDouble() {
        if (value == null) return Double.NaN;
        for (Map.Entry<String,Object> e : data.entrySet()) {
            if (e.getValue().toString().isEmpty()) {
                try {
                    return Double.parseDouble(e.getKey());
                } catch (NumberFormatException x) {
                }
            }
        }
        return Double.NaN;
    }
    
    /**
     * Returns {@code int} value associated with the specified key.
     * @param key Key.
     * @return Value as an {@code int}, or {@code Integer.MIN_VALUE} if there is no such key,
     *         or the value cannot be converted to int.
     */
    public int getInt(String key) {
        Object v = data.get(key);
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException x) {
                return Integer.MIN_VALUE;
            }
        } else {
            return Integer.MIN_VALUE;
        }
    }
    
    /**
     * Returns the {@code int} value not associated with any key.
     * @return int value, or {@code Integer.MIN_VALUE} if there is no value that is not
     *         associated with any key and that can be converted to double.
     */
    public int getInt() {
        if (value == null) return Integer.MIN_VALUE;
        for (Map.Entry<String,Object> e : data.entrySet()) {
            if (e.getValue().toString().isEmpty()) {
                try {
                    return Integer.parseInt(e.getKey());
                } catch (NumberFormatException x) {
                }
            }
        }
        return Integer.MIN_VALUE;
    }
    
    /**
     * Returns {@code boolean} value associated with the specified key.
     * @param key Key.
     * @return True if {@code key} is mapped to anything other than "false" (ignore case), or is present without a key.
     */
    public boolean getBoolean(String key) {
        Object v = data.get(key);
        return v != null && !"false".equalsIgnoreCase(v.toString());
    }
    
    /**
     * Returns the default logging level associated with this property.
     * @return Specified logging level, or {@code INFO} if it has not been explicitly specified.
     */
    public Level getLevel() {
        return level;
    }
    
    /**
     * Returns logging level associated with this property.
     * @param key Key.
     * @return Specified logging level, or the default level if no level is specified for the given key.
     */
    public Level getLevel(String key) {
        Object v = data.get(key);
        return (v instanceof Level lev) ? lev : level;
    }

    @Override
    public String toString() {
        return name +" = "+ value;
    }
    
    public boolean isLogEnabled(Log log) {
        return log.isEnabled(level);
    }
    
    static public void main(String... s) {
        CCSProperty p = new CCSProperty("", "441");
        System.out.println(p.getBoolean("FINE")); // false
        System.out.println(p.getLevel()); // INFO
        System.out.println(p.getDouble()); // 441.
        System.out.println(p.getDouble("x")); // NaN
        System.out.println(p.getInt()); // 441
        p = new CCSProperty("", "x=4&FINE & 441");
        System.out.println(p.getBoolean("FINE")); // true
        System.out.println(p.getLevel()); // FINE
        System.out.println(p.getDouble()); // 441.
        System.out.println(p.getDouble("x")); // 4
        System.out.println(p.getInt()); // 441
        p = new CCSProperty("", "suppress");
        System.out.println(p.getBoolean("suppress")); // true
        
    }
}
