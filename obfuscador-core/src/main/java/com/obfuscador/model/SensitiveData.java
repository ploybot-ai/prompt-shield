package com.obfuscador.model;

import java.util.Objects;

public class SensitiveData {

    private final String type;
    private final String value;

    public SensitiveData(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensitiveData that = (SensitiveData) o;
        return Objects.equals(type, that.type) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "SensitiveData{" +
                "type='" + type + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
