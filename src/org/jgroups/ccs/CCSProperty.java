package org.jgroups.ccs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    
    static private final Map<String, CCSProperty> knownProperties = new ConcurrentHashMap<>();
    
    static private final String SEP = ";";
    static private final String MAP = ":";
    
    private final String name;
    
    private volatile Level levelValue; // Unnamed level, or ALL
    private volatile int intValue; // unnamed int, or Integer.MIN_VALUE
    private volatile double doubleValue; // unnamed double, or Double.NaN
    private volatile boolean booleanValue; // unnamed boolean, or false
    private volatile Map<String,String> data; // null if property not set
    
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    
// -- Life cycle : ---------------------------------------------------------
    
    CCSProperty(String name) {
        this(name, (System.getProperty(name) == null || System.getProperty(name).isBlank()) ? null : System.getProperty(name));
    }
    
    CCSProperty(String name, String value) {
        this.name = name;
        set(value);
    }
    
    static public CCSProperty make(String name) {
        CCSProperty p = new CCSProperty(name);
        if (knownProperties.putIfAbsent(name, p) == null) {
            return p;
        } else {
            throw new IllegalArgumentException("Duplicate JGroups CCSProperty name: "+ name);
        }
    }
    
    static public CCSProperty get(String name) {
        return knownProperties.get(name);
    }
    
    static public List<CCSProperty> getRegisteredProperties() {
        ArrayList<CCSProperty> out = new ArrayList<>(knownProperties.values());
        out.sort(Comparator.comparing(CCSProperty::getName));
        return out;
    }
    
    
// -- Setters : ------------------------------------------------------------
    
    public final CCSProperty set(String value) {
        synchronized (this) {
            if (value == null || value.isBlank()) {
                data = null;
                levelValue = Level.ALL;
                intValue = Integer.MIN_VALUE;
                doubleValue = Double.NaN;
                booleanValue = false;
            } else {
                if (value.contains("=")) {
                    throw new IllegalArgumentException("Propertu should not contain \"=\" symbol.");
                }
                Map<String, String> dataV = new TreeMap<>();
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
                                if (!k.isEmpty() && !"false".equalsIgnoreCase(k)) {
                                    try {
                                        int v = Integer.parseInt(k);
                                        if (intV == Integer.MIN_VALUE) {
                                            intV = v;
                                        } else {
                                            throw new IllegalArgumentException("Illegal property: " + value + ". More than one unnamed integer value.");
                                        }
                                    } catch (NumberFormatException x) {
                                        try {
                                            Level v = Level.parse(k);
                                            if (levelV == null) {
                                                levelV = v;
                                            } else {
                                                throw new IllegalArgumentException("Illegal property: " + value + ". More than one unnamed log level.");
                                            }
                                        } catch (IllegalArgumentException xx) {
                                            try {
                                                double v = Double.parseDouble(k);
                                                if (Double.isNaN(doubleV)) {
                                                    doubleV = v;
                                                } else {
                                                    throw new RuntimeException("Illegal property: " + value + ". More than one unnamed double value.");
                                                }
                                            } catch (NumberFormatException xxx) {
                                                if ("true".equalsIgnoreCase(k)) {
                                                    booleanV = true;
                                                } else {
                                                    dataV.put(k, "");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            case 2 -> {
                                String k = ss[0].trim();
                                String v = ss[1].trim();
                                if (!"false".equalsIgnoreCase(v)) {
                                    switch (k) {
                                        case "boolean" -> {
                                            if ("true".equalsIgnoreCase(v)) {
                                                booleanV = true;
                                            } else {
                                                throw new IllegalArgumentException(v + " cannot be converted to boolean.");
                                            }
                                        }
                                        case "int" -> {
                                            if (intV == Integer.MIN_VALUE) {
                                                intV = Integer.parseInt(v);
                                            } else {
                                                throw new IllegalArgumentException("Illegal property: " + value + ". More than one unnamed integer value.");
                                            }
                                        }
                                        case "double" -> {
                                            if (doubleV == Double.NaN) {
                                                doubleV = Double.parseDouble(v);
                                            } else {
                                                throw new IllegalArgumentException("Illegal property: " + value + ". More than one unnamed double value.");
                                            }
                                        }
                                        case "level" -> {
                                            if (levelV == Level.ALL) {
                                                levelV = Level.parse(v);
                                            } else {
                                                throw new IllegalArgumentException("Illegal property: " + value + ". More than one unnamed double value.");
                                            }
                                        }
                                        default -> {
                                            dataV.put(k, v);
                                        }
                                    }
                                }
                            }
                            default -> {
                                throw new IllegalArgumentException("Illegal property: " + value + ".");
                            }
                        }
                    }
                }
                levelValue = levelV == null ? Level.ALL : levelV;
                intValue = intV;
                doubleValue = Double.isNaN(doubleV) ? (intV == Integer.MIN_VALUE ? Double.NaN : intV) : doubleV;
                booleanValue = booleanV;
                data = dataV;
            }
        }
        notifyListeners();
        return this;
    }
    
    public final CCSProperty modify(String key, String value) {
        synchronized (this) {
            if (key == null || key.isBlank()) key = "";
            if (value == null || value.isBlank()) value = "";
            if (data == null) data = new TreeMap<>();
            if (key.isEmpty()) { // unnamed value
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("No key, no value.");
                } else {
                    try {
                        intValue = Integer.parseInt(value);
                        doubleValue = intValue;
                    } catch (NumberFormatException x) {
                        try {
                            levelValue = Level.parse(value);
                        } catch (IllegalArgumentException xx) {
                            try {
                                doubleValue = Double.parseDouble(value);
                            } catch (NumberFormatException xxx) {
                                if ("true".equalsIgnoreCase(value)) {
                                    booleanValue = true;
                                } else if ("false".equalsIgnoreCase(value)) {
                                    booleanValue = false;
                                } else {
                                    throw new IllegalArgumentException("No key, value cannot be converted to Level, int, double, boolean.");
                                }
                            }
                        }
                    }
                }
            } else { // non-empty key
                if (value.isEmpty()) {
                    switch (key) {
                        case "int" ->
                            intValue = Integer.MIN_VALUE;
                        case "double" ->
                            doubleValue = Double.NaN;
                        case "boolean" ->
                            booleanValue = false;
                        case "level" ->
                            levelValue = Level.ALL;
                        default -> {
                            try {
                                modify("", key); // try using key as value
                            } catch (IllegalArgumentException x) {
                                Map<String, String> dataV = new TreeMap<>(data);
                                dataV.put(key, value);
                                data = dataV;
                            }
                        }
                    }
                } else { // both key and value present
                    if ("false".equalsIgnoreCase(value)) {
                        switch (key) {
                            case "int", "double", "level" ->
                                throw new IllegalArgumentException(value + " cannot be converted to Level, int, double.");
                            case "boolean" ->
                                booleanValue = false;
                            default -> {
                                Map<String, String> dataV = new TreeMap<>(data);
                                dataV.remove(key);
                                data = dataV;
                            }
                        }
                    } else {
                        switch (key) {
                            case "boolean" -> {
                                if ("true".equalsIgnoreCase(value)) {
                                    booleanValue = true;
                                } else {
                                    throw new IllegalArgumentException(value + " cannot be converted to boolean.");
                                }
                            }
                            case "int" -> {
                                try {
                                    intValue = Integer.parseInt(value);
                                } catch (NumberFormatException x) {
                                    throw new RuntimeException(value + " is not an integer value.");
                                }
                            }
                            case "double" -> {
                                try {
                                    doubleValue = Double.parseDouble(value);
                                } catch (NumberFormatException x) {
                                    throw new RuntimeException(value + " is not a double value.");
                                }
                            }
                            case "level" -> {
                                try {
                                    levelValue = Level.parse(value);
                                } catch (IllegalArgumentException x) {
                                    throw new RuntimeException(value + " is not a valid log level.");
                                }
                            }
                            default -> {
                                Map<String, String> dataV = new TreeMap<>(data);
                                dataV.put(key, value);
                                data = dataV;
                            }
                        }
                    }
                }
            }
        }
        notifyListeners();
        return this;
    }
    
    
// -- Getters : ----------------------------------------------------------------
    
    /**
     * Returns property name (aka system property it is initialized from).
     * @return Property name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns {@code true} if any value has been specified for this property.
     * @return True if set.
     */
    public boolean isSet() {
        return data != null;
    }
    
    /**
     * Returns String value associated with the specified key.
     * @param key Key.
     * @return Value as a String, or {@code null} if there is no such key.
     */
    public String getString(String key) {
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
        return data == null ? false : data.keySet().contains(key);
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
     * @return Specified logging level, or the default (unnamed) level if no level is specified for the given key.
     */
    public Level getLevel(String key) {
        if (data == null) return Level.ALL;
        String v = data.get(key);
        if (v == null || v.isEmpty()) {
            return levelValue;
        }
        for (char c : v.toCharArray()) {
            if (Character.isDigit(c)) {
                return levelValue;
            }
        }
        try {
            return Level.parse(v);
        } catch (IllegalArgumentException x) {
            return levelValue;
        }
    }
    
    public boolean isLogEnabled(Log log) {
        return log.isEnabled(levelValue);
    }

    @Override
    public String toString() {
        if (isSet()) {
            List<String> ss = new ArrayList<>();
            if (levelValue != Level.ALL) ss.add(levelValue.toString());
            if (intValue != Integer.MIN_VALUE) ss.add(Integer.toString(intValue));
            if (!Double.isNaN(doubleValue) && !(Math.round(doubleValue) == intValue)) ss.add(Double.toString(doubleValue));
            if (booleanValue) ss.add("true");
            data.forEach((key, value) -> ss.add(value.isEmpty() ? key : key + MAP + value));
            return name +" = "+ String.join(SEP, ss);
        } else {
            return name +" = not set";
        }
    }
    
    
// -- Utilities : --------------------------------------------------------------

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
    
    
// -- Handling listenerts : ----------------------------------------------------
    
    public interface Listener {
        void changed(CCSProperty property);
    }
    
    public void addListener(Listener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
    
    public void removeAllListeners() {
        listeners.clear();
    }
    
    public void notifyListeners() {
        listeners.forEach(lis -> lis.changed(this));
    }
    
    
// -- Testing : - FIXME: Unit tests are currently disabled ---------------------
    
    static public void main(String... s) {
        
        CCSProperty p = new CCSProperty("p", "441;FINE");
        if (!p.getBoolean() && !p.getBoolean("x") && Math.round(p.getDouble()) == 441 && Double.isNaN(p.getDouble("x")) && p.getInt() == 441 && 
                p.getInt("x") == Integer.MIN_VALUE && p.getLevel() == Level.FINE && p.getLevel("x") == Level.FINE) {
            System.out.println("Success "+ p.toString());
        } else {
            System.out.println("Failure "+ p.toString() +" : getBoolean() = "+ p.getBoolean() +", getBoolean(x) = "+ p.getBoolean("x") +
                    ", getDouble() = "+ p.getDouble() +", getDouble(x) = "+ p.getDouble("x") +
                    ", p.getInt() = "+ p.getInt() +", p.getInt(x) = "+ p.getInt("x") +", p.getLevel() = "+ p.getLevel() +", p.getLevel(x) = "+ p.getLevel("x"));
        }
        
        try {
            p = new CCSProperty("p", "441;FINE;x:66;level:INFO;.7");
            System.out.println("Failure 441;FINE;x:66;level:INFO;.7 duplicate double.");
        } catch (IllegalArgumentException x) {
            System.out.println("Success.");
        }
        
        p.set(".99; boolean:true; x; INFO; y : test");
        if (p.getBoolean() && p.getBoolean("x") && Math.round(p.getDouble()) == 1 && Double.isNaN(p.getDouble("x")) && p.getInt() == Integer.MIN_VALUE && 
                p.getInt("x") == Integer.MIN_VALUE && p.getLevel() == Level.INFO && p.getLevel("x") == Level.INFO) {
            System.out.println("Success "+ p.toString());
        } else {
            System.out.println("Failure "+ p.toString() +" : getBoolean() = "+ p.getBoolean() +", getBoolean(x) = "+ p.getBoolean("x") +
                    ", getDouble() = "+ p.getDouble() +", getDouble(x) = "+ p.getDouble("x") +
                    ", p.getInt() = "+ p.getInt() +", p.getInt(x) = "+ p.getInt("x") +", p.getLevel() = "+ p.getLevel() +", p.getLevel(x) = "+ p.getLevel("x"));
        }
        
        p.modify("", "false");
        p.modify("x", "-1");
        p.modify("level", "FINE");
        if (!p.getBoolean() && p.getBoolean("x") && Math.round(p.getDouble()) == 1 && Math.round(p.getDouble("x")) == -1 && p.getInt() == Integer.MIN_VALUE && 
                p.getInt("x") == -1 && p.getLevel() == Level.FINE && p.getLevel("x") == Level.FINE) {
            System.out.println("Success "+ p.toString());
        } else {
            System.out.println("Failure "+ p.toString() +" : getBoolean() = "+ p.getBoolean() +", getBoolean(x) = "+ p.getBoolean("x") +
                    ", getDouble() = "+ p.getDouble() +", getDouble(x) = "+ p.getDouble("x") +
                    ", p.getInt() = "+ p.getInt() +", p.getInt(x) = "+ p.getInt("x") +", p.getLevel() = "+ p.getLevel() +", p.getLevel(x) = "+ p.getLevel("x"));
        }
        
        p.set(null);
        if (!p.getBoolean() && !p.getBoolean("x") && Double.isNaN(p.getDouble()) && Double.isNaN(p.getDouble("x")) && p.getInt() == Integer.MIN_VALUE && 
                p.getInt("x") == Integer.MIN_VALUE && p.getLevel() == Level.ALL && p.getLevel("x") == Level.ALL) {
            System.out.println("Success "+ p.toString());
        } else {
            System.out.println("Failure "+ p.toString() +" : getBoolean() = "+ p.getBoolean() +", getBoolean(x) = "+ p.getBoolean("x") +
                    ", getDouble() = "+ p.getDouble() +", getDouble(x) = "+ p.getDouble("x") +
                    ", p.getInt() = "+ p.getInt() +", p.getInt(x) = "+ p.getInt("x") +", p.getLevel() = "+ p.getLevel() +", p.getLevel(x) = "+ p.getLevel("x"));
        }
        
        p = new CCSProperty("p");
        if (!p.getBoolean() && !p.getBoolean("x") && Double.isNaN(p.getDouble()) && Double.isNaN(p.getDouble("x")) && p.getInt() == Integer.MIN_VALUE && 
                p.getInt("x") == Integer.MIN_VALUE && p.getLevel() == Level.ALL && p.getLevel("x") == Level.ALL) {
            System.out.println("Success "+ p.toString());
        } else {
            System.out.println("Failure "+ p.toString() +" : getBoolean() = "+ p.getBoolean() +", getBoolean(x) = "+ p.getBoolean("x") +
                    ", getDouble() = "+ p.getDouble() +", getDouble(x) = "+ p.getDouble("x") +
                    ", p.getInt() = "+ p.getInt() +", p.getInt(x) = "+ p.getInt("x") +", p.getLevel() = "+ p.getLevel() +", p.getLevel(x) = "+ p.getLevel("x"));
        }
    }
}
