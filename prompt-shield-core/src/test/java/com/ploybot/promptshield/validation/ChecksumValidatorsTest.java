package com.ploybot.promptshield.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumValidatorsTest {

    @Test
    void validDni() {
        assertTrue(ChecksumValidators.isValidDni("12345678Z"));
        assertTrue(ChecksumValidators.isValidDni("87654321X"));
        assertTrue(ChecksumValidators.isValidDni("12345678z"));
    }

    @Test
    void invalidDni() {
        assertFalse(ChecksumValidators.isValidDni("12345678A"));
        assertFalse(ChecksumValidators.isValidDni("87654321A"));
        assertFalse(ChecksumValidators.isValidDni("1234567Z"));
        assertFalse(ChecksumValidators.isValidDni(null));
    }

    @Test
    void validNie() {
        assertTrue(ChecksumValidators.isValidNie("X1234567L"));
        assertTrue(ChecksumValidators.isValidNie("Y1234567X"));
        assertTrue(ChecksumValidators.isValidNie("Z1234567R"));
    }

    @Test
    void invalidNie() {
        assertFalse(ChecksumValidators.isValidNie("X1234567A"));
        assertFalse(ChecksumValidators.isValidNie("A1234567L"));
        assertFalse(ChecksumValidators.isValidNie(null));
    }

    @Test
    void validIban() {
        // Well-known valid test IBAN (Spanish format)
        assertTrue(ChecksumValidators.isValidIban("ES9121000418450200051332"));
    }

    @Test
    void invalidIban() {
        assertFalse(ChecksumValidators.isValidIban("ES9121000418450200051333"));
        assertFalse(ChecksumValidators.isValidIban("ES00"));
        assertFalse(ChecksumValidators.isValidIban(null));
    }
}
