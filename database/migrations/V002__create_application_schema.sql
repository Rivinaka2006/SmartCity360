CREATE TABLE public.audit_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_user_id uuid,
    action character varying(80) NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id uuid,
    details_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT audit_logs_details_json_check CHECK (jsonb_typeof(details_json) = 'object'::text),
    CONSTRAINT audit_logs_pkey PRIMARY KEY (id)
);

CREATE TABLE public.building_power_states (
    zone_id uuid NOT NULL,
    power_state character varying(8) DEFAULT 'ON'::character varying NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT building_power_states_power_state_check CHECK (power_state::text = ANY (ARRAY['ON'::character varying, 'OFF'::character varying]::text[])),
    CONSTRAINT building_power_states_pkey PRIMARY KEY (zone_id)
);

CREATE TABLE public.complaint_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    complaint_id uuid NOT NULL,
    actor_user_id uuid,
    action character varying(16) NOT NULL,
    from_level character varying(16),
    to_level character varying(16),
    note text,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT complaint_events_action_check CHECK (action::text = ANY (ARRAY['SUBMITTED'::character varying, 'ASSIGNED'::character varying, 'ESCALATED'::character varying, 'COMMENTED'::character varying, 'RESOLVED'::character varying, 'REOPENED'::character varying, 'REJECTED'::character varying]::text[])),
    CONSTRAINT complaint_events_from_level_check CHECK (from_level::text = ANY (ARRAY['LOCAL_STAFF'::character varying, 'SUPERVISOR'::character varying, 'CITY_MANAGER'::character varying]::text[])),
    CONSTRAINT complaint_events_to_level_check CHECK (to_level::text = ANY (ARRAY['LOCAL_STAFF'::character varying, 'SUPERVISOR'::character varying, 'CITY_MANAGER'::character varying]::text[])),
    CONSTRAINT complaint_events_pkey PRIMARY KEY (id)
);

CREATE TABLE public.complaints (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    reference_no character varying(32) NOT NULL,
    resident_id uuid NOT NULL,
    zone_id uuid NOT NULL,
    title character varying(180) NOT NULL,
    description text NOT NULL,
    severity character varying(12) NOT NULL,
    status character varying(16) DEFAULT 'OPEN'::character varying NOT NULL,
    assigned_to_user_id uuid,
    current_handler_level character varying(16),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    resolved_at timestamp with time zone,
    CONSTRAINT complaints_current_handler_level_check CHECK (current_handler_level::text = ANY (ARRAY['LOCAL_STAFF'::character varying, 'SUPERVISOR'::character varying, 'CITY_MANAGER'::character varying]::text[])),
    CONSTRAINT complaints_severity_check CHECK (severity::text = ANY (ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying]::text[])),
    CONSTRAINT complaints_status_check CHECK (status::text = ANY (ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'REJECTED'::character varying]::text[])),
    CONSTRAINT complaints_pkey PRIMARY KEY (id),
    CONSTRAINT complaints_reference_no_key UNIQUE (reference_no)
);

CREATE TABLE public.energy_meters (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    building_zone_id uuid NOT NULL,
    meter_code character varying(50) NOT NULL,
    status character varying(12) DEFAULT 'ACTIVE'::character varying NOT NULL,
    registered_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone,
    CONSTRAINT energy_meters_status_check CHECK (status::text = ANY (ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying]::text[])),
    CONSTRAINT energy_meters_meter_code_key UNIQUE (meter_code),
    CONSTRAINT energy_meters_pkey PRIMARY KEY (id)
);

CREATE TABLE public.energy_readings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    meter_id uuid NOT NULL,
    period_start_at timestamp with time zone NOT NULL,
    period_end_at timestamp with time zone NOT NULL,
    consumption_kwh numeric(14,4) NOT NULL,
    voltage_v numeric(10,2),
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT energy_readings_check CHECK (period_end_at > period_start_at),
    CONSTRAINT energy_readings_consumption_kwh_check CHECK (consumption_kwh >= 0::numeric),
    CONSTRAINT energy_readings_voltage_v_check CHECK (voltage_v >= 0::numeric),
    CONSTRAINT energy_readings_meter_id_period_start_at_period_end_at_key UNIQUE (meter_id, period_start_at, period_end_at),
    CONSTRAINT energy_readings_pkey PRIMARY KEY (id)
);

CREATE TABLE public.energy_stream_runs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    meter_id uuid NOT NULL,
    initiated_by uuid,
    decorator_chain jsonb NOT NULL,
    input_bytes integer NOT NULL,
    output_bytes integer,
    status character varying(12) NOT NULL,
    duration_ms integer,
    error_code character varying(64),
    executed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT energy_stream_runs_decorator_chain_check CHECK (jsonb_typeof(decorator_chain) = 'array'::text),
    CONSTRAINT energy_stream_runs_duration_ms_check CHECK (duration_ms >= 0),
    CONSTRAINT energy_stream_runs_input_bytes_check CHECK (input_bytes >= 0),
    CONSTRAINT energy_stream_runs_output_bytes_check CHECK (output_bytes >= 0),
    CONSTRAINT energy_stream_runs_status_check CHECK (status::text = ANY (ARRAY['SUCCESS'::character varying, 'FAILED'::character varying]::text[])),
    CONSTRAINT energy_stream_runs_pkey PRIMARY KEY (id)
);

CREATE TABLE public.lighting_profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    profile_code character varying(50) NOT NULL,
    manufacturer character varying(80) NOT NULL,
    model character varying(100) NOT NULL,
    wattage numeric(8,2) NOT NULL,
    default_brightness_pct smallint NOT NULL,
    controller_type character varying(40) NOT NULL,
    firmware_family character varying(50),
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    profile_version integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT lighting_profiles_config_json_check CHECK (jsonb_typeof(config_json) = 'object'::text),
    CONSTRAINT lighting_profiles_default_brightness_pct_check CHECK (default_brightness_pct >= 0 AND default_brightness_pct <= 100),
    CONSTRAINT lighting_profiles_profile_version_check CHECK (profile_version > 0),
    CONSTRAINT lighting_profiles_wattage_check CHECK (wattage > 0::numeric),
    CONSTRAINT lighting_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT lighting_profiles_profile_code_key UNIQUE (profile_code)
);

CREATE TABLE public.policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    policy_code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    domain character varying(12) NOT NULL,
    description text,
    status character varying(12) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT policies_domain_check CHECK (domain::text = ANY (ARRAY['NOISE'::character varying, 'PARKING'::character varying, 'GENERAL'::character varying]::text[])),
    CONSTRAINT policies_status_check CHECK (status::text = ANY (ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying]::text[])),
    CONSTRAINT policies_pkey PRIMARY KEY (id),
    CONSTRAINT policies_policy_code_key UNIQUE (policy_code)
);

CREATE TABLE public.policy_evaluations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    policy_version_id uuid NOT NULL,
    evaluated_by uuid,
    context_json jsonb NOT NULL,
    status character varying(12) NOT NULL,
    result boolean,
    error_message text,
    duration_ms integer,
    evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT policy_evaluations_check CHECK (status::text = 'SUCCESS'::text AND result IS NOT NULL OR status::text = 'ERROR'::text AND result IS NULL),
    CONSTRAINT policy_evaluations_context_json_check CHECK (jsonb_typeof(context_json) = 'object'::text),
    CONSTRAINT policy_evaluations_duration_ms_check CHECK (duration_ms >= 0),
    CONSTRAINT policy_evaluations_status_check CHECK (status::text = ANY (ARRAY['SUCCESS'::character varying, 'ERROR'::character varying]::text[])),
    CONSTRAINT policy_evaluations_pkey PRIMARY KEY (id)
);

CREATE TABLE public.policy_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    policy_id uuid NOT NULL,
    version_no integer NOT NULL,
    dsl_version character varying(12) DEFAULT '1'::character varying NOT NULL,
    dsl_expression text NOT NULL,
    is_valid boolean DEFAULT false NOT NULL,
    validation_error text,
    is_active boolean DEFAULT false NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT policy_versions_check CHECK (NOT is_active OR is_valid),
    CONSTRAINT policy_versions_dsl_expression_check CHECK (length(TRIM(BOTH FROM dsl_expression)) > 0),
    CONSTRAINT policy_versions_version_no_check CHECK (version_no > 0),
    CONSTRAINT policy_versions_pkey PRIMARY KEY (id),
    CONSTRAINT policy_versions_policy_id_version_no_key UNIQUE (policy_id, version_no)
);

CREATE TABLE public.roles (
    code character varying(30) NOT NULL,
    display_name character varying(80) NOT NULL,
    CONSTRAINT roles_pkey PRIMARY KEY (code)
);

CREATE TABLE public.streetlights (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_tag character varying(50) NOT NULL,
    profile_id uuid NOT NULL,
    street_zone_id uuid NOT NULL,
    latitude numeric(9,6),
    longitude numeric(9,6),
    address_label character varying(255),
    operational_state character varying(16) DEFAULT 'OFF'::character varying NOT NULL,
    brightness_override_pct smallint,
    installed_on date,
    last_seen_at timestamp with time zone,
    registered_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT streetlights_brightness_override_pct_check CHECK (brightness_override_pct >= 0 AND brightness_override_pct <= 100),
    CONSTRAINT streetlights_check CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT streetlights_latitude_check CHECK (latitude >= '-90'::integer::numeric AND latitude <= 90::numeric),
    CONSTRAINT streetlights_longitude_check CHECK (longitude >= '-180'::integer::numeric AND longitude <= 180::numeric),
    CONSTRAINT streetlights_operational_state_check CHECK (operational_state::text = ANY (ARRAY['ON'::character varying, 'OFF'::character varying, 'FAULT'::character varying, 'MAINTENANCE'::character varying]::text[])),
    CONSTRAINT streetlights_asset_tag_key UNIQUE (asset_tag),
    CONSTRAINT streetlights_pkey PRIMARY KEY (id)
);

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_code character varying(30) NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_code)
);

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    full_name character varying(120) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash text NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT users_status_check CHECK (status::text = ANY (ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DISABLED'::character varying]::text[])),
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

CREATE TABLE public.zone_commands (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    target_zone_id uuid NOT NULL,
    issued_by uuid NOT NULL,
    command character varying(12) NOT NULL,
    execution_status character varying(12) NOT NULL,
    affected_buildings integer DEFAULT 0 NOT NULL,
    message text,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT zone_commands_affected_buildings_check CHECK (affected_buildings >= 0),
    CONSTRAINT zone_commands_command_check CHECK (command::text = ANY (ARRAY['SHUTDOWN'::character varying, 'RESTORE'::character varying]::text[])),
    CONSTRAINT zone_commands_execution_status_check CHECK (execution_status::text = ANY (ARRAY['SUCCEEDED'::character varying, 'FAILED'::character varying]::text[])),
    CONSTRAINT zone_commands_pkey PRIMARY KEY (id)
);

CREATE TABLE public.zones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    parent_zone_id uuid,
    zone_type character varying(12) NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(150) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT zones_check CHECK (zone_type::text = 'CITY'::text AND parent_zone_id IS NULL OR zone_type::text <> 'CITY'::text AND parent_zone_id IS NOT NULL),
    CONSTRAINT zones_check1 CHECK (parent_zone_id IS DISTINCT FROM id),
    CONSTRAINT zones_status_check CHECK (status::text = ANY (ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying]::text[])),
    CONSTRAINT zones_zone_type_check CHECK (zone_type::text = ANY (ARRAY['CITY'::character varying, 'DISTRICT'::character varying, 'STREET'::character varying, 'BUILDING'::character varying]::text[])),
    CONSTRAINT zones_code_key UNIQUE (code),
    CONSTRAINT zones_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_audit_logs_actor_time ON public.audit_logs USING btree (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_entity_time ON public.audit_logs USING btree (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_complaint_events_complaint_time ON public.complaint_events USING btree (complaint_id, occurred_at DESC);
CREATE INDEX ix_complaints_assignee_status ON public.complaints USING btree (assigned_to_user_id, status);
CREATE INDEX ix_complaints_resident_time ON public.complaints USING btree (resident_id, created_at DESC);
CREATE INDEX ix_complaints_zone_status ON public.complaints USING btree (zone_id, status);
CREATE INDEX ix_energy_meters_building ON public.energy_meters USING btree (building_zone_id);
CREATE INDEX ix_energy_readings_meter_end ON public.energy_readings USING btree (meter_id, period_end_at DESC);
CREATE INDEX ix_energy_stream_runs_meter_time ON public.energy_stream_runs USING btree (meter_id, executed_at DESC);
CREATE INDEX ix_policy_evaluations_version_time ON public.policy_evaluations USING btree (policy_version_id, evaluated_at DESC);
CREATE INDEX ix_streetlights_profile ON public.streetlights USING btree (profile_id);
CREATE INDEX ix_streetlights_street ON public.streetlights USING btree (street_zone_id);
CREATE INDEX ix_user_roles_role ON public.user_roles USING btree (role_code);
CREATE INDEX ix_zone_commands_zone_time ON public.zone_commands USING btree (target_zone_id, issued_at DESC);
CREATE INDEX ix_zones_parent ON public.zones USING btree (parent_zone_id);
CREATE UNIQUE INDEX ux_policy_one_active_version ON public.policy_versions USING btree (policy_id) WHERE is_active;
CREATE UNIQUE INDEX ux_users_email_ci ON public.users USING btree (lower(email::text));

ALTER TABLE ONLY public.audit_logs ADD CONSTRAINT audit_logs_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.building_power_states ADD CONSTRAINT building_power_states_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.building_power_states ADD CONSTRAINT building_power_states_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zones(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.complaint_events ADD CONSTRAINT complaint_events_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.complaint_events ADD CONSTRAINT complaint_events_complaint_id_fkey FOREIGN KEY (complaint_id) REFERENCES public.complaints(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.complaints ADD CONSTRAINT complaints_assigned_to_user_id_fkey FOREIGN KEY (assigned_to_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.complaints ADD CONSTRAINT complaints_resident_id_fkey FOREIGN KEY (resident_id) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.complaints ADD CONSTRAINT complaints_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zones(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.energy_meters ADD CONSTRAINT energy_meters_building_zone_id_fkey FOREIGN KEY (building_zone_id) REFERENCES public.zones(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.energy_readings ADD CONSTRAINT energy_readings_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES public.energy_meters(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.energy_stream_runs ADD CONSTRAINT energy_stream_runs_initiated_by_fkey FOREIGN KEY (initiated_by) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.energy_stream_runs ADD CONSTRAINT energy_stream_runs_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES public.energy_meters(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.policies ADD CONSTRAINT policies_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.policy_evaluations ADD CONSTRAINT policy_evaluations_evaluated_by_fkey FOREIGN KEY (evaluated_by) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.policy_evaluations ADD CONSTRAINT policy_evaluations_policy_version_id_fkey FOREIGN KEY (policy_version_id) REFERENCES public.policy_versions(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.policy_versions ADD CONSTRAINT policy_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.policy_versions ADD CONSTRAINT policy_versions_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES public.policies(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.streetlights ADD CONSTRAINT streetlights_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.lighting_profiles(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.streetlights ADD CONSTRAINT streetlights_street_zone_id_fkey FOREIGN KEY (street_zone_id) REFERENCES public.zones(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.user_roles ADD CONSTRAINT user_roles_role_code_fkey FOREIGN KEY (role_code) REFERENCES public.roles(code) ON DELETE RESTRICT;
ALTER TABLE ONLY public.user_roles ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.zone_commands ADD CONSTRAINT zone_commands_issued_by_fkey FOREIGN KEY (issued_by) REFERENCES public.users(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.zone_commands ADD CONSTRAINT zone_commands_target_zone_id_fkey FOREIGN KEY (target_zone_id) REFERENCES public.zones(id) ON DELETE RESTRICT;
ALTER TABLE ONLY public.zones ADD CONSTRAINT zones_parent_zone_id_fkey FOREIGN KEY (parent_zone_id) REFERENCES public.zones(id) ON DELETE RESTRICT;
