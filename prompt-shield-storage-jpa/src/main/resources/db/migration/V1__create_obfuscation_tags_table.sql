CREATE TABLE IF NOT EXISTS obfuscation_tags (
    hash VARCHAR(64) PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    original_value TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_obfuscation_tags_created_at ON obfuscation_tags(created_at);
CREATE INDEX IF NOT EXISTS idx_obfuscation_tags_type ON obfuscation_tags(type);
