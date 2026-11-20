# Energy Stream Academic Demo Operator Preparation

This procedure is for an explicitly selected local/development Payara domain and
database only. It is not a production provisioning procedure. Nothing in the
application creates the fixture, writes Payara configuration, restarts Payara,
or cleans up demo data automatically.

The integrated endpoint is disabled unless
`smartcity360.energy.stream.demo.integrated.enabled=true`. It accepts a reading
only for the active `ACADEMIC-VIVA-METER` registered against an active
`BUILDING` zone whose code is `ACADEMIC-VIVA-BUILDING`.

## One-time preparation

1. Confirm the selected Payara domain and PostgreSQL database are the intended
   development environment. Check that the following meter code, building code,
   HMAC alias, and AES alias do not already belong to another fixture:

   - Meter: `ACADEMIC-VIVA-METER`
   - Building: `ACADEMIC-VIVA-BUILDING`
   - HMAC alias: `smartcity360.energy.hmac.academic.viva.v1`
   - AES alias: `smartcity360.energy.aes.academic.viva.v1`

2. Record the exact current existence and values of these Payara domain
   properties before changing either one:

   - `smartcity360.energy.aes.current-key-id`
   - `smartcity360.energy.stream.demo.integrated.enabled`

   Preserve an absent property as absent. Do not change
   `smartcity360.energy.stream.demo.enabled`; that property controls the
   separate Phase 51 demonstration.

3. Create two demo-owned aliases using the repository's guarded provisioning
   script. Run from the repository root and use its interactive confirmation:

   ```powershell
   .\database\scripts\Provision-EnergyKeyAlias.ps1 -AliasName 'smartcity360.energy.hmac.academic.viva.v1' -Purpose MeterHmac -Apply
   .\database\scripts\Provision-EnergyKeyAlias.ps1 -AliasName 'smartcity360.energy.aes.academic.viva.v1' -Purpose AesGcm -Apply
   ```

   The generated key values are not displayed or written to the repository.
   Do not reuse either alias for another purpose.

4. Create a dedicated four-level `CITY` → `DISTRICT` → `STREET` → `BUILDING`
   zone hierarchy in the development database. Use newly generated UUIDs and
   these unique codes; set the building code exactly as shown:

   ```sql
   BEGIN;
   INSERT INTO public.zones (id, zone_type, code, name)
   VALUES ('<CITY_UUID>', 'CITY', 'ACADEMIC-VIVA-CITY', 'Academic VIVA Demo City');
   INSERT INTO public.zones (id, parent_zone_id, zone_type, code, name)
   VALUES ('<DISTRICT_UUID>', '<CITY_UUID>', 'DISTRICT', 'ACADEMIC-VIVA-DISTRICT', 'Academic VIVA Demo District');
   INSERT INTO public.zones (id, parent_zone_id, zone_type, code, name)
   VALUES ('<STREET_UUID>', '<DISTRICT_UUID>', 'STREET', 'ACADEMIC-VIVA-STREET', 'Academic VIVA Demo Street');
   INSERT INTO public.zones (id, parent_zone_id, zone_type, code, name)
   VALUES ('<BUILDING_UUID>', '<STREET_UUID>', 'BUILDING', 'ACADEMIC-VIVA-BUILDING', 'Academic VIVA Demo Building');
   INSERT INTO public.energy_meters
       (id, building_zone_id, meter_code, credential_key_id, credential_key_version)
   VALUES
       ('<METER_UUID>', '<BUILDING_UUID>', 'ACADEMIC-VIVA-METER',
        'smartcity360.energy.hmac.academic.viva.v1', 1);
   COMMIT;
   ```

   Replace every placeholder with a distinct newly generated UUID. Stop if any
   insert conflicts; do not modify an existing meter or zone to make it fit.
   The run recorder will attach processing history to this meter.

5. Set the AES current-key property to the new AES alias and enable the
   integrated demo in this development domain only:

   ```powershell
   asadmin set-config-property --propertyName=smartcity360.energy.aes.current-key-id --propertyValue=smartcity360.energy.aes.academic.viva.v1 --source=domain
   asadmin set-config-property --propertyName=smartcity360.energy.stream.demo.integrated.enabled --propertyValue=true --source=domain
   ```

   The meter row's `credential_key_id` matches the HMAC alias. Do not put key
   material in SQL, source code, frontend configuration, or command-line
   arguments.

6. Manually restart the selected Payara domain once after provisioning the
   aliases and configuration. This is required for password aliases to be
   reliably available to the running server, as in the existing Energy Streams
   integration harness. Do not restart or redeploy automatically from the
   application.

## Explicit cleanup after the VIVA

Do not run cleanup after individual readings; persisted history is part of the
demonstration. After the VIVA, verify the recorded original configuration from
step 2, then:

1. Restore the original
   `smartcity360.energy.stream.demo.integrated.enabled` value, or delete that
   property if it was originally absent.
2. Restore the original
   `smartcity360.energy.aes.current-key-id` value, or delete that property if
   it was originally absent. Restore before deleting the demo AES alias.

   Use `set-config-property` with each recorded original value. For a property
   that was absent, use:

   ```powershell
   asadmin delete-config-property --propertyName=smartcity360.energy.stream.demo.integrated.enabled --source=domain
   asadmin delete-config-property --propertyName=smartcity360.energy.aes.current-key-id --source=domain
   ```

3. Using the exact `<METER_UUID>` and zone UUIDs created above, verify that the
   meter has the expected code and is attached to the dedicated building. Record
   the run count before cleanup:

   ```sql
   SELECT count(*) FROM public.energy_stream_runs WHERE meter_id = '<METER_UUID>';
   ```

   Then remove only this fixture's history and rows, in dependency order:

   ```sql
   BEGIN;
   DELETE FROM public.energy_stream_runs WHERE meter_id = '<METER_UUID>';
   DELETE FROM public.energy_meters
   WHERE id = '<METER_UUID>'
     AND meter_code = 'ACADEMIC-VIVA-METER'
     AND building_zone_id = '<BUILDING_UUID>';
   DELETE FROM public.zones WHERE id = '<BUILDING_UUID>' AND code = 'ACADEMIC-VIVA-BUILDING';
   DELETE FROM public.zones WHERE id = '<STREET_UUID>' AND code = 'ACADEMIC-VIVA-STREET';
   DELETE FROM public.zones WHERE id = '<DISTRICT_UUID>' AND code = 'ACADEMIC-VIVA-DISTRICT';
   DELETE FROM public.zones WHERE id = '<CITY_UUID>' AND code = 'ACADEMIC-VIVA-CITY';
   ```

   Run the statements interactively. Confirm that the history delete count equals
   the recorded run count and each meter/zone delete affected exactly one row
   before issuing `COMMIT;`. Issue `ROLLBACK;` instead if any count is
   unexpected. If a row does not match these invocation-owned identifiers and
   codes, stop rather than broadening the deletion.
4. Manually restart Payara once after restoring configuration. Delete the two
   password aliases with `asadmin delete-password-alias` only if they were
   created for this fixture, are not referenced by restored configuration, and
   were not present before setup.

   ```powershell
   asadmin delete-password-alias 'smartcity360.energy.hmac.academic.viva.v1'
   asadmin delete-password-alias 'smartcity360.energy.aes.academic.viva.v1'
   ```

5. Verify the integrated-demo property is absent or restored to its original
   value and that the demo meter, its zones, and its processing history are gone.

This preparation changes no schema and does not use or alter operational
city-meter records.
