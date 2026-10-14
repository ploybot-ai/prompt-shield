package com.ploybot.promptshield.registry;

import com.ploybot.promptshield.validation.ChecksumValidators;

import java.util.function.Predicate;

/**
 * Built-in detectable types. Declaration order is detection priority: when two
 * matches overlap, the type declared first wins.
 */
public enum SensitiveDataType {

    ANTHROPIC_API_KEY("ANTHROPIC_API_KEY", "\\bsk-ant-[A-Za-z0-9_-]{20,}", true),
    // Project, service-account, admin and legacy keys. A real key always contains a
    // digit, which keeps words such as "sk-some-long-hyphenated-name" out.
    OPENAI_API_KEY("OPENAI_API_KEY", "\\bsk-(?:proj-|svcacct-|admin-)?(?=[A-Za-z_-]*\\d)[A-Za-z0-9_-]{20,}", true),
    STRIPE_KEY("STRIPE_KEY", "(?:sk|pk|rk)_(?:live|test)_[0-9a-zA-Z]{24,}", true),
    GOOGLE_AI_KEY("GOOGLE_AI_KEY", "AIza[A-Za-z0-9_-]{35}", true),
    HUGGINGFACE_TOKEN("HUGGINGFACE_TOKEN", "hf_[A-Za-z0-9]{34}", true),
    AWS_ACCESS_KEY("AWS_ACCESS_KEY", "AKIA[0-9A-Z]{16}", true),
    AZURE_STORAGE_KEY("AZURE_STORAGE_KEY", "[A-Za-z0-9+/]{88}==", true),
    DIGITALOCEAN_TOKEN("DIGITALOCEAN_TOKEN", "dop_v1_[a-f0-9]{64}", true),
    GITHUB_TOKEN("GITHUB_TOKEN", "gh[psoa]_[A-Za-z0-9]{36}", true),
    GITLAB_TOKEN("GITLAB_TOKEN", "glpat-[A-Za-z0-9_-]{20,}", true),
    NPM_TOKEN("NPM_TOKEN", "npm_[A-Za-z0-9]{36}", true),
    PYPI_TOKEN("PYPI_TOKEN", "pypi-[A-Za-z0-9_-]{60,}", true),
    SLACK_TOKEN("SLACK_TOKEN", "xox[bpsa]-[0-9]{10,13}-[a-zA-Z0-9-]{20,}", true),
    TWILIO_API_KEY("TWILIO_API_KEY", "SK[0-9a-fA-F]{32}", true),
    SENDGRID_KEY("SENDGRID_KEY", "SG\\.[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}", true),
    MAILGUN_API_KEY("MAILGUN_API_KEY", "key-[0-9a-zA-Z]{32}", true),
    // 12345678Z, 12345678-Z, 12.345.678-Z
    DNI("DNI", "\\b(?:\\d{8}|\\d{2}\\.\\d{3}\\.\\d{3})-?[A-Za-z]\\b", false, ChecksumValidators::isValidDni),
    // X1234567L, X-1234567-L
    NIE("NIE", "\\b[XYZxyz]-?\\d{7}-?[A-Za-z]\\b", false, ChecksumValidators::isValidNie),
    // The lookbehind makes only the start of a run try a match: without it a long run of
    // word characters with no "@" is rescanned from every position (quadratic time)
    EMAIL("EMAIL", "(?<![\\w.+-])[\\w.+-]{1,64}@[\\w.-]+\\.\\w{2,}", false),
    // Spanish IBAN, compact or in groups of four: ES91 2100 0418 4502 0005 1332
    N_CUENTA("N_CUENTA", "\\bES\\d{2}(?: ?\\d{4}){5}\\b", false, ChecksumValidators::isValidIban),
    // Any other IBAN, compact or grouped
    IBAN("IBAN", "\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b", false, ChecksumValidators::isValidIban),
    // 612345678, 612 34 56 78, 612-345-678, +34 612 345 678, 0034612345678 and any
    // other bare 9-digit number
    TELEFONO("TELEFONO", "(?<![\\w+])(?:(?:\\+|00)34[ -]?)?[6-9](?:[ -]?\\d){8}(?!\\w)|\\b\\d{9}\\b", false),
    CODIGO_POSTAL("CODIGO_POSTAL", "\\b\\d{5}\\b", false);

    private final String typeName;
    private final String pattern;
    private final boolean serviceKey;
    private final Predicate<String> validator;

    SensitiveDataType(String typeName, String pattern, boolean serviceKey) {
        this(typeName, pattern, serviceKey, null);
    }

    SensitiveDataType(String typeName, String pattern, boolean serviceKey, Predicate<String> validator) {
        this.typeName = typeName;
        this.pattern = pattern;
        this.serviceKey = serviceKey;
        this.validator = validator;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isServiceKey() {
        return serviceKey;
    }

    /**
     * Optional checksum validator applied on top of the regex match, or {@code null}
     * if the type has no verifiable control digit.
     */
    public Predicate<String> getValidator() {
        return validator;
    }
}
