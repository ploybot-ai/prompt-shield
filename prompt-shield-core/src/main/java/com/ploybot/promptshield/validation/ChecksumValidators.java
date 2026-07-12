package com.ploybot.promptshield.validation;

import java.math.BigInteger;

/**
 * Checksum validators for Spanish identity documents and IBANs. Used to discard
 * regex matches whose control digit/letter does not verify, drastically reducing
 * false positives (e.g. an 8-digit reference number followed by a letter).
 */
public final class ChecksumValidators {

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private ChecksumValidators() {
    }

    public static boolean isValidDni(String value) {
        if (value == null || value.length() != 9) {
            return false;
        }
        String digits = value.substring(0, 8);
        if (!digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        char letter = Character.toUpperCase(value.charAt(8));
        return DNI_LETTERS.charAt(Integer.parseInt(digits) % 23) == letter;
    }

    public static boolean isValidNie(String value) {
        if (value == null || value.length() != 9) {
            return false;
        }
        char prefix = Character.toUpperCase(value.charAt(0));
        int prefixDigit = switch (prefix) {
            case 'X' -> 0;
            case 'Y' -> 1;
            case 'Z' -> 2;
            default -> -1;
        };
        if (prefixDigit < 0) {
            return false;
        }
        String digits = value.substring(1, 8);
        if (!digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        char letter = Character.toUpperCase(value.charAt(8));
        int number = Integer.parseInt(prefixDigit + digits);
        return DNI_LETTERS.charAt(number % 23) == letter;
    }

    /**
     * Standard IBAN mod-97 check (ISO 13616). Works for any country, including the
     * Spanish {@code ES + 22 digits} format.
     */
    public static boolean isValidIban(String value) {
        if (value == null || value.length() < 5) {
            return false;
        }
        String rearranged = value.substring(4) + value.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else if (Character.isLetter(c)) {
                numeric.append(Character.toUpperCase(c) - 'A' + 10);
            } else {
                return false;
            }
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
