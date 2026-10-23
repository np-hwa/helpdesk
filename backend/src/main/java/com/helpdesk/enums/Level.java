package com.helpdesk.enums;

/** Thang đo cho Impact và Urgency. Trọng số tuyến tính 1-2-3 (xem mục 10.1 README). */
public enum Level {
    LOW(1), MEDIUM(2), HIGH(3);

    private final int weight;

    Level(int weight) { this.weight = weight; }

    public int getWeight() { return weight; }
}
