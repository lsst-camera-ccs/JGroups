package org.jgroups.ccs;

import java.util.logging.Level;

/**
 *
 * @author onoprien
 */
public class CCSProperty {
    
    private final String value;
    
    public CCSProperty(String name) {
        String s = System.getProperty(name);
        value = (s == null || s.isBlank()) ? null : s.trim();
    }
    
    public CCSProperty(String name, String value) {
        this.value = value.trim();
    }
    
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
    
    public boolean isSet() {
        return value != null;
    }
    
    public String get(String key) {
        if (value == null) return null;
        for (String s : value.split("&")) {
            if (!s.isBlank()) {
                String[] ss = s.split("=");
                if (ss.length == 2 && ss[0].trim().equals(key)) {
                        return ss[1].trim();
                }
            }
        }
        return null;
    }
    
    public double getDouble(String key) {
        String s = get(key);
        if (s == null) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException x) {
            return Double.NaN;
        }
    }
    
    public double getDouble() {
        if (value == null) return Double.NaN;
        for (String s : value.split("&")) {
            if (!(s.isBlank() || s.contains("="))) {
                try {
                    return Double.parseDouble(s.trim());
                } catch (NumberFormatException x) {
                }
            }
        }
        return Double.NaN;
    }
    
    public int getInt(String key) {
        String s = get(key);
        if (s == null) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException x) {
            return Integer.MIN_VALUE;
        }
    }
    
    public int getInt() {
        if (value == null) return Integer.MIN_VALUE;
        for (String s : value.split("&")) {
            if (!(s.isBlank() || s.contains("="))) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException x) {
                }
            }
        }
        return Integer.MIN_VALUE;
    }
    
    public boolean getBoolean(String key) {
        if (value == null) return false;
        for (String s : value.split("&")) {
            if (!s.isBlank()) {
                String[] ss = s.split("=");
                if (ss[0].trim().equals(key)) {
                    if (ss.length == 1) {
                        return true;
                    } else if (ss.length == 2) {
                        return !"false".equalsIgnoreCase(ss[1]);
                    }
                }
            }
        }
        return false;
    }
    
    public Level getLevel() {
        if (value == null) return null;
        for (String s : value.split("&")) {
            if (!s.isBlank()) {
                String[] ss = s.split("=");
                String name = switch (ss.length) {
                    case 1 -> ss[0].trim();
                    case 2 -> "level".equals(ss[0].trim()) ? ss[1].trim() : null;
                    default -> null;
                };
                if (name != null && name.matches("\\D+")) {
                    try {
                        return Level.parse(name);
                    } catch (IllegalArgumentException x) {
                    }
                }
            }
        }
        return Level.INFO;
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
        
    }
}
