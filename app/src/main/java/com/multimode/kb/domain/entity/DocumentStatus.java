package com.multimode.kb.domain.entity;

/**
 * Domain-level document status enum.
 */
public enum DocumentStatus {
    PENDING("等待处理"),
    INGESTING("入库中"),
    INGESTED("已入库"),
    ERROR("处理失败");

    private final String displayName;

    DocumentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DocumentStatus fromString(String value) {
        if (value == null) return PENDING;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
