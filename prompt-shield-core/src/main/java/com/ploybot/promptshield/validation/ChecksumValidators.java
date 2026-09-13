package com.ploybot.promptshield.validation;

import java.math.BigInteger;

/**
 * Checksum validators for Spanish identity documents and IBANs. Used to discard
 * regex matches whose control digit/letter does not verify, drastically reducing
 * false positives (e.g. an 8-digit reference number followed by a letter).
 *
 * <p>Separators commonly used when writing these values by hand (dots and hyphens
 * in DNI/NIE, spaces in IBANs) are ignored.
 */
public final class ChecksumValidators {

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);
    private static final int IBAN_MIN_LENGTH = 15;
    private static final int IBAN_MAX_LENGTH = 34;

    private ChecksumValidators() {
    }

    public static boolean isValidDni(String value) {
        if (value == null) {
            return false;
        }
        String compact = strip(value, ".-");
        if (compact.length() != 9) {
            return false;
        }
        String digits = compact.substring(0, 8);
        if (!allDigits(digits)) {
            return false;
        }
        char letter = Character.toUpperCase(compact.charAt(8));
        return DNI_LETTERS.charAt(Integer.parseInt(digits) % 23) == letter;
    }

    public static boolean isValidNie(String value) {
        if (value == null) {
            return false;
        }
        String compact = strip(value, "-");
        if (compact.length() != 9) {
            return false;
        }
        int prefixDigit = switch (Character.toUpperCase(compact.charAt(0))) {
            case 'X' -> 0;
            case 'Y' -> 1;
            case 'Z' -> 2;
            default -> -1;
        };
        if (prefixDigit < 0) {
            return false;
        }
        String digits = compact.substring(1, 8);
        if (!allDigits(digits)) {
            return false;
        }
        char letter = Character.toUpperCase(compact.charAt(8));
        int number = Integer.parseInt(prefixDigit + digits);
        return DNI_LETTERS.charAt(number % 23) == letter;
    }

    /**
     * Standard IBAN mod-97 check (ISO 13616). Works for any country, including the
     * Spanish {@code ES + 22 digits} format.
     */
    public static boolean isValidIban(String value) {
        if (value == null) {
            return false;
        }
        String compact = strip(value, " ");
        if (compact.length() < IBAN_MIN_LENGTH || compact.length() > IBAN_MAX_LENGTH
                || !isAsciiLetter(compact.charAt(0)) || !isAsciiLetter(compact.charAt(1))
                || !isAsciiDigit(compact.charAt(2)) || !isAsciiDigit(compact.charAt(3))) {
            return false;
        }
        String rearranged = compact.substring(4) + compact.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            if (isAsciiDigit(c)) {
                numeric.append(c);
            } else if (isAsciiLetter(c)) {
                numeric.append(Character.toUpperCase(c) - 'A' + 10);
            } else {
                return false;
            }
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    private static String strip(String value, String separators) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (separators.indexOf(c) < 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean allDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isAsciiDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }
}
