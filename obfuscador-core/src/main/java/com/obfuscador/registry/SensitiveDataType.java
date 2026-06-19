package com.obfuscador.registry;

public enum SensitiveDataType {

    DNI("DNI", "\\d{8}[A-Za-z]"),
    NIE("NIE", "[XYZxyz]\\d{7}[A-Za-z]"),
    EMAIL("EMAIL", "[\\w.+-]+@[\\w.-]+\\.\\w{2,}"),
    TELEFONO("TELEFONO", "\\d{9}"),
    CODIGO_POSTAL("CODIGO_POSTAL", "\\d{5}"),
    N_CUENTA("N_CUENTA", "ES\\d{22}");

    private final String typeName;
    private final String pattern;

    SensitiveDataType(String typeName, String pattern) {
        this.typeName = typeName;
        this.pattern = pattern;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getPattern() {
        return pattern;
    }
}
