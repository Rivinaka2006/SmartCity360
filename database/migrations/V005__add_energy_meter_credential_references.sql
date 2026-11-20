ALTER TABLE public.energy_meters
    ADD COLUMN credential_key_id character varying(128),
    ADD COLUMN credential_key_version integer,
    ADD CONSTRAINT energy_meters_credential_reference_pair_check CHECK (
        (credential_key_id IS NULL AND credential_key_version IS NULL)
        OR
        (credential_key_id IS NOT NULL AND credential_key_version IS NOT NULL)
    ),
    ADD CONSTRAINT energy_meters_credential_key_version_check CHECK (
        credential_key_version IS NULL OR credential_key_version > 0
    );

CREATE UNIQUE INDEX ux_energy_meters_credential_key_id
    ON public.energy_meters USING btree (credential_key_id)
    WHERE credential_key_id IS NOT NULL;
