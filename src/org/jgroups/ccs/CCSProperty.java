package org.jgroups.ccs;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.jgroups.logging.Log;

/**
 * Property used to control run time behavior.
 * Initialized from system properties.
 * Format: [key:]value;...;[key:]value[;LEVEL]
 *
 * @author onoprien
 */
public class CCSProperty {
    
    static private final String SEP = ";";
    static private final String MAP = ":";
    
    private final String name;
    private final String value;
    
    private final Level levelValue;
    private final int intValue;
    private final double doubleValue;
    private final boolean booleanValue;
    private final Map<String,String> data;
    private final Set<String> keys; // all keys except those mapped to "false"
    
    public CCSProperty(String name) {
        this(name, (System.getProperty(name) == null || System.getProperty(name).isBlank()) ? null : System.getProperty(name));
    }
    
    public CCSProperty(String name, String val) {
        this.name = name;
        this.value = val == null || val.isBlank() ? null : val.trim();
        if (value == null) {
            data = null;
            levelValue = Level.ALL;
            intValue = Integer.MIN_VALUE;
            doubleValue = Double.NaN;
            booleanValue = false;
            keys = Collections.emptySet();
        } else {
            data = new LinkedHashMap<>();
            Level levelV = null;
            int intV = Integer.MIN_VALUE;
            double doubleV = Double.NaN;
            boolean booleanV = false;
            for (String s : value.split(SEP)) {
                if (!s.isBlank()) {
                    String[] ss = s.split(MAP);
                    switch (ss.length) {
                        case 1 -> {
                            String k = ss[0].trim();
                            if (!k.isEmpty()) {
                                try {
                                    int v = Integer.parseInt(k);
                                    if (intV == Integer.MIN_VALUE) {
                                        intV = v;
                                    } else {
                                        throw new RuntimeException("Illegal property: "+ val +". More than one unnamed integer value.");
                                    }
                                } catch (NumberFormatException x) {
                                    try {
                                        Level v = Level.parse(k);
                                        if (levelV == null) {
                                            levelV = v;
                                        } else {
                                            throw new RuntimeException("Illegal property: "+ val +". More than one unnamed log level.");
                                        }
                                    } catch (IllegalArgumentException xx) {
                                        try {
                                            double v = Double.parseDouble(k);
                                            if (Double.isNaN(doubleV)) {
                                                doubleV = v;
                                            } else {
                                                throw new RuntimeException("Illegal property: " + val + ". More than one unnamed double value.");
                                            }
                                        } catch (NumberFormatException xxx) {
                                            if ("true".equalsIgnoreCase(k)) {
                                                booleanV = true;
                                            } else {
                                                data.put(k, "");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        case 2 -> {
                            String k = ss[0].trim();
                            String v = ss[1].trim();
                            if (levelV == null && "level".equalsIgnoreCase(k)) {
                                try {
                                    levelV = Level.parse(v);
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
            levelValue = levelV == null ? Level.INFO : levelV;
            intValue = intV;
            doubleValue = Double.isNaN(doubleV) ? (intV == Integer.MIN_VALUE ? Double.NaN : intV) : doubleV;
            booleanValue = booleanV;
            keys = new HashSet<>();
            data.forEach((k,v) -> {
                if (!"false".equalsIgnoreCase(k)) {
                    keys.add(k);
                }
            });
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
        return data == null ? null : data.get(key);
    }
    
    /**
     * Returns double value associated with the specified key.
     * @param key Key.
     * @return Value as a double, or {@code Double.NaN} if there is no such key, or the value cannot be converted to double.
     */
    public double getDouble(String key) {
        try {
            return Double.parseDouble(data.get(key));
        } catch (RuntimeException x) {
            return Double.NaN;
        }
    }
    
    /**
     * Returns the double value not associated with any key.
     * @return Double value, or {@code Double.NaN} if there is no value that is not
     *         associated with any key and that can be converted to double.
     */
    public double getDouble() {
        return doubleValue;
    }
    
    /**
     * Returns {@code int} value associated with the specified key.
     * @param key Key.
     * @return Value as an {@code int}, or {@code Integer.MIN_VALUE} if there is no such key,
     *         or the value cannot be converted to int.
     */
    public int getInt(String key) {
        try {
            return Integer.parseInt(data.get(key));
        } catch (RuntimeException x) {
            return Integer.MIN_VALUE;
        }
    }
    
    /**
     * Returns the {@code int} value not associated with any key.
     * @return int value, or {@code Integer.MIN_VALUE} if there is no value that is not
     *         associated with any key and that can be converted to double.
     */
    public int getInt() {
        return intValue;
    }
    
    /**
     * Returns {@code boolean} value associated with the specified key.
     * @param key Key.
     * @return True if {@code key} is mapped to anything other than "false" (ignore case), or is present without a key.
     */
    public boolean getBoolean(String key) {
        return keys.contains(key);
    }
    
    /**
     * Returns {@code boolean} value not associated with any key. 
     * @return {@code true} if the definition string contains "true" (ignore case) value without a key.
     */
    public boolean getBoolean() {
        return booleanValue;
    }
    
    /**
     * Returns the default logging level associated with this property.
     * @return Specified logging level, or {@code INFO} if it has not been explicitly specified.
     */
    public Level getLevel() {
        return levelValue;
    }
    
    /**
     * Returns logging level associated with this property.
     * @param key Key.
     * @return Specified logging level, or the default level if no level is specified for the given key.
     */
    public Level getLevel(String key) {
        if (data == null) return Level.ALL;
        String v = data.get(key);
        if (v == null) {
            return levelValue;
        } else {
            try {
                return Level.parse(v);
            } catch (IllegalArgumentException x) {
                return levelValue;
            }
        }
    }

    @Override
    public String toString() {
        return name +" = "+ value;
    }
    
    public boolean isLogEnabled(Log log) {
        return log.isEnabled(levelValue);
    }
    
    static public void main(String... s) {
        CCSProperty p;
        System.out.println(p = new CCSProperty("", "441;FINE"));
        System.out.println(" p.getLevel() "+ p.getLevel() +" p.getInt() "+ p.getInt() +" p.getDouble() "+ p.getDouble() +" p.getInt(\"x\") "+ p.getInt("x")); // FINE
        System.out.println(p = new CCSProperty("", "441;FINE;x:66;level:INFO;.7"));
        System.out.println(" p.getLevel() "+ p.getLevel() +" p.getInt() "+ p.getInt() +" p.getDouble() "+ p.getDouble() +" p.getInt(\"x\") "+ p.getInt("x") +" p.getLevel(\"level\") "+ p.getLevel("level")); // FINE
        
    }
}
