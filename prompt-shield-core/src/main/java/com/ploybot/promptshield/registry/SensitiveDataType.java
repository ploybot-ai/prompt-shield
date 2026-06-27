package com.ploybot.promptshield.registry;

public enum SensitiveDataType {

    ANTHROPIC_API_KEY("ANTHROPIC_API_KEY", "sk-ant-[A-Za-z0-9_-]{20,}", true),
    OPENAI_API_KEY("OPENAI_API_KEY", "sk-(?:proj-)[A-Za-z0-9_-]{20,}", true),
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
    DNI("DNI", "\\d{8}[A-Za-z]", false),
    NIE("NIE", "[XYZxyz]\\d{7}[A-Za-z]", false),
    EMAIL("EMAIL", "[\\w.+-]+@[\\w.-]+\\.\\w{2,}", false),
    N_CUENTA("N_CUENTA", "ES\\d{22}", false),
    TELEFONO("TELEFONO", "\\b\\d{9}\\b", false),
    CODIGO_POSTAL("CODIGO_POSTAL", "\\b\\d{5}\\b", false);

    private final String typeName;
    private final String pattern;
    private final boolean serviceKey;

    SensitiveDataType(String typeName, String pattern, boolean serviceKey) {
        this.typeName = typeName;
        this.pattern = pattern;
        this.serviceKey = serviceKey;
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
}
