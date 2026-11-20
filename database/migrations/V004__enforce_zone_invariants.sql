DO $validation$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.zones child
        JOIN public.zones parent ON parent.id = child.parent_zone_id
        WHERE (child.zone_type = 'DISTRICT' AND parent.zone_type <> 'CITY')
           OR (child.zone_type = 'STREET' AND parent.zone_type <> 'DISTRICT')
           OR (child.zone_type = 'BUILDING' AND parent.zone_type <> 'STREET')
    ) THEN
        RAISE EXCEPTION 'Existing zones violate CITY -> DISTRICT -> STREET -> BUILDING hierarchy';
    END IF;

    IF EXISTS (
        WITH RECURSIVE ancestry AS (
            SELECT id AS origin_id, parent_zone_id, ARRAY[id] AS path, false AS cycle
            FROM public.zones
            UNION ALL
            SELECT a.origin_id, z.parent_zone_id, a.path || z.id, z.id = ANY(a.path)
            FROM ancestry a
            JOIN public.zones z ON z.id = a.parent_zone_id
            WHERE NOT a.cycle
        )
        SELECT 1 FROM ancestry WHERE cycle
    ) THEN
        RAISE EXCEPTION 'Existing zone hierarchy contains an ancestor cycle';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.energy_meters m
        JOIN public.zones z ON z.id = m.building_zone_id
        WHERE z.zone_type <> 'BUILDING'
    ) THEN
        RAISE EXCEPTION 'Existing energy meter references a non-BUILDING zone';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.streetlights s
        JOIN public.zones z ON z.id = s.street_zone_id
        WHERE z.zone_type <> 'STREET'
    ) THEN
        RAISE EXCEPTION 'Existing streetlight references a non-STREET zone';
    END IF;
END
$validation$;

CREATE FUNCTION public.smartcity360_enforce_zone_invariants()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
DECLARE
    referenced_type character varying(12);
    expected_parent_type character varying(12);
    expected_child_type character varying(12);
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('smartcity360.zone-graph', 0));

    IF TG_TABLE_NAME = 'energy_meters' THEN
        SELECT zone_type INTO referenced_type FROM public.zones WHERE id = NEW.building_zone_id;
        IF referenced_type IS DISTINCT FROM 'BUILDING' THEN
            RAISE EXCEPTION 'Energy meter building_zone_id % must reference a BUILDING zone', NEW.building_zone_id
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'streetlights' THEN
        SELECT zone_type INTO referenced_type FROM public.zones WHERE id = NEW.street_zone_id;
        IF referenced_type IS DISTINCT FROM 'STREET' THEN
            RAISE EXCEPTION 'Streetlight street_zone_id % must reference a STREET zone', NEW.street_zone_id
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    expected_parent_type := CASE NEW.zone_type
        WHEN 'CITY' THEN NULL
        WHEN 'DISTRICT' THEN 'CITY'
        WHEN 'STREET' THEN 'DISTRICT'
        WHEN 'BUILDING' THEN 'STREET'
    END;

    IF NEW.zone_type = 'CITY' THEN
        IF NEW.parent_zone_id IS NOT NULL THEN
            RAISE EXCEPTION 'CITY zone % cannot have a parent', NEW.id USING ERRCODE = '23514';
        END IF;
    ELSE
        SELECT zone_type INTO referenced_type FROM public.zones WHERE id = NEW.parent_zone_id;
        IF referenced_type IS DISTINCT FROM expected_parent_type THEN
            RAISE EXCEPTION '% zone % must have a % parent; zone % is %',
                NEW.zone_type, NEW.id, expected_parent_type, NEW.parent_zone_id,
                COALESCE(referenced_type, 'missing') USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.parent_zone_id IS NOT NULL AND EXISTS (
        WITH RECURSIVE ancestors(id, parent_zone_id) AS (
            SELECT z.id, z.parent_zone_id FROM public.zones z WHERE z.id = NEW.parent_zone_id
            UNION ALL
            SELECT z.id, z.parent_zone_id
            FROM public.zones z JOIN ancestors a ON z.id = a.parent_zone_id
            WHERE a.id <> NEW.id
        )
        SELECT 1 FROM ancestors WHERE id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Zone % cannot be reparented beneath its own descendant %', NEW.id, NEW.parent_zone_id
            USING ERRCODE = '23514';
    END IF;

    expected_child_type := CASE NEW.zone_type
        WHEN 'CITY' THEN 'DISTRICT'
        WHEN 'DISTRICT' THEN 'STREET'
        WHEN 'STREET' THEN 'BUILDING'
        WHEN 'BUILDING' THEN NULL
    END;

    IF EXISTS (
        SELECT 1 FROM public.zones child
        WHERE child.parent_zone_id = NEW.id
          AND (expected_child_type IS NULL OR child.zone_type <> expected_child_type)
    ) THEN
        RAISE EXCEPTION 'Zone % cannot become % because its existing children require parent type %',
            NEW.id, NEW.zone_type, COALESCE(expected_child_type, 'none') USING ERRCODE = '23514';
    END IF;

    IF NEW.zone_type <> 'BUILDING' AND EXISTS (
        SELECT 1 FROM public.energy_meters WHERE building_zone_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Zone % must remain BUILDING while referenced by energy meters', NEW.id
            USING ERRCODE = '23514';
    END IF;

    IF NEW.zone_type <> 'STREET' AND EXISTS (
        SELECT 1 FROM public.streetlights WHERE street_zone_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Zone % must remain STREET while referenced by streetlights', NEW.id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END
$function$;

CREATE TRIGGER trg_zones_enforce_invariants
BEFORE INSERT OR UPDATE OF id, parent_zone_id, zone_type ON public.zones
FOR EACH ROW EXECUTE FUNCTION public.smartcity360_enforce_zone_invariants();

CREATE TRIGGER trg_energy_meters_enforce_building_zone
BEFORE INSERT OR UPDATE OF building_zone_id ON public.energy_meters
FOR EACH ROW EXECUTE FUNCTION public.smartcity360_enforce_zone_invariants();

CREATE TRIGGER trg_streetlights_enforce_street_zone
BEFORE INSERT OR UPDATE OF street_zone_id ON public.streetlights
FOR EACH ROW EXECUTE FUNCTION public.smartcity360_enforce_zone_invariants();
