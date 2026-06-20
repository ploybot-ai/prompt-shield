package com.ploybot.promptshield.ner;

import java.util.Objects;

public class DetectedEntity {

    private final String type;
    private final String value;
    private final int start;
    private final int end;
    private final double confidence;

    public DetectedEntity(String type, String value, int start, int end, double confidence) {
        this.type = type;
        this.value = value;
        this.start = start;
        this.end = end;
        this.confidence = confidence;
    }

    public DetectedEntity(String type, String value, int start, int end) {
        this(type, value, start, end, 1.0);
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetectedEntity that = (DetectedEntity) o;
        return start == that.start && end == that.end && Objects.equals(type, that.type) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, start, end);
    }

    @Override
    public String toString() {
        return "DetectedEntity{" +
                "type='" + type + '\'' +
                "value='" + value + '\'' +
                "start=" + start +
                "end=" + end +
                "confidence=" + confidence +
                '}';
    }
}
