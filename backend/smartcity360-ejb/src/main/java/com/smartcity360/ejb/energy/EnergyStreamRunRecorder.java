package com.smartcity360.ejb.energy;

import com.smartcity360.ejb.persistence.model.*;
import jakarta.ejb.*;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Stateless
public class EnergyStreamRunRecorder {
    @PersistenceContext(unitName = "smartcityPU")
    EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public UUID record(EnergyMeterEntity meter, String chain, int input, Integer output, EnergyStreamRunEntity.Status status, Integer duration, String error) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                        INSERT INTO energy_stream_runs
                          (id,meter_id,initiated_by,decorator_chain,input_bytes,output_bytes,status,duration_ms,error_code,executed_at)
                        VALUES
                          (CAST(?1 AS uuid),CAST(?2 AS uuid),CAST(?3 AS uuid),CAST(?4 AS jsonb),?5,
                           CAST(?6 AS integer),?7,CAST(?8 AS integer),CAST(?9 AS varchar),?10)
                        """).setParameter(1, id.toString()).setParameter(2, meter.getId().toString()).setParameter(3, null)
                .setParameter(4, chain).setParameter(5, input).setParameter(6, output).setParameter(7, status.name())
                .setParameter(8, duration).setParameter(9, error).setParameter(10, OffsetDateTime.now()).executeUpdate();
        return id;
    }
}
