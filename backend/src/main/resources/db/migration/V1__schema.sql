-- Architecture visualizer schema (PostgreSQL). One baseline script: the database as of feature 004.

CREATE TABLE spec_card (
    id               uuid          PRIMARY KEY,
    title            varchar(200)  NOT NULL,
    api_version      varchar(50),
    spec_format      varchar(20)   NOT NULL CHECK (spec_format IN ('SWAGGER_2_0', 'OPENAPI_3_0', 'OPENAPI_3_1')),
    source_type      varchar(10)   NOT NULL CHECK (source_type IN ('PASTE', 'URL')),
    source_url       varchar(2048),
    raw_content      text          NOT NULL,
    content_hash     varchar(64)   NOT NULL,
    endpoints        jsonb         NOT NULL,
    last_sync_at     timestamptz   NOT NULL,
    last_sync_status varchar(10)   NOT NULL CHECK (last_sync_status IN ('OK', 'FAILED')),
    last_sync_errors jsonb         NOT NULL,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    CONSTRAINT ck_spec_card_url CHECK (source_type <> 'URL' OR source_url IS NOT NULL)
);

CREATE TABLE microservice (
    id              uuid          PRIMARY KEY,
    name            varchar(120)  NOT NULL,
    name_normalized varchar(120)  NOT NULL,
    description     varchar(2000),
    spec_card_id    uuid,
    team            varchar(120),
    repository_url  varchar(2048),
    docs_url        varchar(2048),
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    CONSTRAINT uq_microservice_name UNIQUE (name_normalized),
    CONSTRAINT uq_microservice_card UNIQUE (spec_card_id),
    CONSTRAINT fk_microservice_card FOREIGN KEY (spec_card_id) REFERENCES spec_card (id) ON DELETE SET NULL
);

CREATE TABLE flow (
    id              uuid         PRIMARY KEY,
    name            varchar(120) NOT NULL,
    name_normalized varchar(120) NOT NULL,
    color           varchar(10)  NOT NULL
        CHECK (color IN ('BLUE', 'PURPLE', 'ORANGE', 'PINK', 'AMBER', 'BROWN', 'CYAN', 'SLATE')),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT uq_flow_name UNIQUE (name_normalized)
);

-- source_id/target_id become NULL when a microservice is deleted: the connection is kept as dangling
CREATE TABLE connection (
    id                     uuid          PRIMARY KEY,
    name                   varchar(120)  NOT NULL,
    name_normalized        varchar(120)  NOT NULL,
    kind                   varchar(10)   CHECK (kind IN ('REQUEST', 'EVENT')),
    description            varchar(2000),
    source_id              uuid,
    target_id              uuid,
    source_name            varchar(120)  NOT NULL,
    target_name            varchar(120)  NOT NULL,
    source_name_normalized varchar(120)  NOT NULL,
    target_name_normalized varchar(120)  NOT NULL,
    flow_id                uuid,
    endpoint               varchar(600),
    step                   integer       CHECK (step IS NULL OR step >= 1),
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    CONSTRAINT ck_connection_no_self CHECK (source_name_normalized <> target_name_normalized),
    CONSTRAINT uq_connection_key UNIQUE (source_name_normalized, target_name_normalized, name_normalized),
    CONSTRAINT fk_connection_source FOREIGN KEY (source_id) REFERENCES microservice (id) ON DELETE SET NULL,
    CONSTRAINT fk_connection_target FOREIGN KEY (target_id) REFERENCES microservice (id) ON DELETE SET NULL,
    CONSTRAINT fk_connection_flow FOREIGN KEY (flow_id) REFERENCES flow (id) ON DELETE SET NULL
);

CREATE INDEX ix_connection_source ON connection (source_id);
CREATE INDEX ix_connection_target ON connection (target_id);

CREATE TABLE note (
    id         uuid          PRIMARY KEY,
    text       varchar(2000) NOT NULL,
    kind       varchar(10)   NOT NULL CHECK (kind IN ('NOTE', 'DECISION', 'QUESTION')),
    x          double precision NOT NULL,
    y          double precision NOT NULL,
    created_at timestamptz   NOT NULL,
    updated_at timestamptz   NOT NULL
);

CREATE TABLE saved_scenario (
    id              uuid          PRIMARY KEY,
    name            varchar(120)  NOT NULL,
    name_normalized varchar(120)  NOT NULL,
    description     varchar(2000),
    content         jsonb         NOT NULL,
    baseline_ref    jsonb         NOT NULL,
    version         integer       NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    CONSTRAINT uq_saved_scenario_name UNIQUE (name_normalized)
);

-- single-row tables
CREATE TABLE baseline_layout (
    id              smallint    PRIMARY KEY CHECK (id = 1),
    positions       jsonb       NOT NULL,
    collapsed_cards jsonb       NOT NULL DEFAULT '[]'::jsonb,
    version         integer     NOT NULL,
    updated_at      timestamptz NOT NULL
);
INSERT INTO baseline_layout (id, positions, collapsed_cards, version, updated_at)
VALUES (1, '{}'::jsonb, '[]'::jsonb, 0, CURRENT_TIMESTAMP);

CREATE TABLE workspace_state (
    id       smallint PRIMARY KEY CHECK (id = 1),
    revision bigint   NOT NULL
);
INSERT INTO workspace_state (id, revision) VALUES (1, 0);
