package com.multimode.kb.domain.entity;

/**
 * Directory tracking status.
 */
public enum DirectoryStatus {
    IDLE("空闲"),
    SCANNING("扫描中"),
    INGESTING("入库中"),
    ERROR("错误");

    private final String displayName;

    DirectoryStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DirectoryStatus fromString(String value) {
        if (value == null) return IDLE;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return IDLE;
        }
    }
}
