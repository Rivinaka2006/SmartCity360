package com.smartcity360.ejb.complaints.domain.chain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComplaintChainTest {
    @Test void standardChainRoutesEverySeverityWithDelegationTrace(){
        ComplaintHandler chain=new ComplaintChainFactory().standardChain();
        assertDecision(chain.route(ComplaintRoutingContext.initial(ComplaintSeverity.LOW)),HandlerLevel.LOCAL_STAFF,List.of(HandlerLevel.LOCAL_STAFF));
        assertDecision(chain.route(ComplaintRoutingContext.initial(ComplaintSeverity.MEDIUM)),HandlerLevel.LOCAL_STAFF,List.of(HandlerLevel.LOCAL_STAFF));
        assertDecision(chain.route(ComplaintRoutingContext.initial(ComplaintSeverity.HIGH)),HandlerLevel.SUPERVISOR,List.of(HandlerLevel.LOCAL_STAFF,HandlerLevel.SUPERVISOR));
        assertDecision(chain.route(ComplaintRoutingContext.initial(ComplaintSeverity.CRITICAL)),HandlerLevel.CITY_MANAGER,List.of(HandlerLevel.LOCAL_STAFF,HandlerLevel.SUPERVISOR,HandlerLevel.CITY_MANAGER));
    }
    @Test void manualEscalationAdvancesThroughRemainingHandlers(){
        ComplaintHandler chain=new ComplaintChainFactory().standardChain();
        assertDecision(chain.route(ComplaintRoutingContext.escalating(ComplaintSeverity.LOW,HandlerLevel.SUPERVISOR)),HandlerLevel.SUPERVISOR,List.of(HandlerLevel.LOCAL_STAFF,HandlerLevel.SUPERVISOR));
        assertDecision(chain.route(ComplaintRoutingContext.escalating(ComplaintSeverity.HIGH,HandlerLevel.CITY_MANAGER)),HandlerLevel.CITY_MANAGER,List.of(HandlerLevel.LOCAL_STAFF,HandlerLevel.SUPERVISOR,HandlerLevel.CITY_MANAGER));
    }
    @Test void incompleteDynamicChainReturnsExplicitUnhandledDecision(){
        ComplaintHandler onlyLocal=new LocalStaffHandler();
        RoutingDecision decision=onlyLocal.route(ComplaintRoutingContext.initial(ComplaintSeverity.CRITICAL));
        assertFalse(decision.handled());assertNull(decision.selectedLevel());assertEquals(List.of(HandlerLevel.LOCAL_STAFF),decision.traversedLevels());
    }
    @Test void factoryBuildsPolicySelectedOrderAndRejectsDuplicateHandlers(){
        ComplaintHandler chain=new ComplaintChainFactory().build(List.of(HandlerLevel.SUPERVISOR,HandlerLevel.CITY_MANAGER));
        RoutingDecision decision=chain.route(ComplaintRoutingContext.initial(ComplaintSeverity.CRITICAL));
        assertDecision(decision,HandlerLevel.CITY_MANAGER,List.of(HandlerLevel.SUPERVISOR,HandlerLevel.CITY_MANAGER));
        assertThrows(IllegalArgumentException.class,()->new ComplaintChainFactory().build(List.of(HandlerLevel.LOCAL_STAFF,HandlerLevel.LOCAL_STAFF)));
        assertThrows(IllegalArgumentException.class,()->new ComplaintChainFactory().build(List.of()));
    }
    @Test void linkingRejectsDirectAndIndirectCycles(){
        BaseComplaintHandler local=new LocalStaffHandler();BaseComplaintHandler supervisor=new SupervisorHandler();BaseComplaintHandler manager=new CityManagerHandler();
        local.setNext(supervisor);supervisor.setNext(manager);
        assertThrows(IllegalArgumentException.class,()->manager.setNext(local));
        assertThrows(IllegalArgumentException.class,()->local.setNext(local));
    }
    private static void assertDecision(RoutingDecision actual,HandlerLevel selected,List<HandlerLevel> trace){assertTrue(actual.handled());assertEquals(selected,actual.selectedLevel());assertEquals(trace,actual.traversedLevels());}
}
