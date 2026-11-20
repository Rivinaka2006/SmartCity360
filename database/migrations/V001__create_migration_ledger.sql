CREATE TABLE public.smartcity360_schema_migrations (
    version integer NOT NULL,
    description character varying(200) NOT NULL,
    checksum character(64) NOT NULL,
    installed_at timestamp with time zone DEFAULT now() NOT NULL,
    installed_by character varying(128) DEFAULT current_user NOT NULL,
    baselined boolean DEFAULT false NOT NULL,
    execution_ms integer NOT NULL,
    CONSTRAINT smartcity360_schema_migrations_pkey PRIMARY KEY (version),
    CONSTRAINT smartcity360_schema_migrations_checksum_check
        CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT smartcity360_schema_migrations_execution_ms_check
        CHECK (execution_ms >= 0)
);
