package com.helpdesk.enums;

public enum Priority {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** Score = weight(impact) + weight(urgency) thuộc [2,6]. Dùng làm phương án dự phòng khi bảng priority_matrix thiếu dòng. */
    public static Priority fromScore(int score) {
        return switch (score) {
            case 2, 3 -> LOW;
            case 4 -> MEDIUM;
            case 5 -> HIGH;
            case 6 -> CRITICAL;
            default -> throw new IllegalArgumentException("Score ngoài [2,6]: " + score);
        };
    }

    public static Priority of(Level impact, Level urgency) {
        return fromScore(impact.getWeight() + urgency.getWeight());
    }
}
