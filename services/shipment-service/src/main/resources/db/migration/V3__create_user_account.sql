CREATE TABLE user_account (
    id            UUID PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    site          VARCHAR(100),
    active        BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT user_account_site_only_for_scanner CHECK ((role = 'SCANNER') = (site IS NOT NULL))
);

-- Identifiant unique sans distinction de casse.
CREATE UNIQUE INDEX user_account_username_lower ON user_account (lower(username));
