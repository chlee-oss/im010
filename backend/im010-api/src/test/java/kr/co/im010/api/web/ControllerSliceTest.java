package kr.co.im010.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import kr.co.im010.api.forward.ForwardController;
import kr.co.im010.api.forward.ForwardService;
import kr.co.im010.api.plan.Network;
import kr.co.im010.api.plan.PlanController;
import kr.co.im010.api.plan.PlanService;

@WebMvcTest(controllers = {PlanController.class, ForwardController.class})
class ControllerSliceTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlanService planService;

    @MockitoBean
    ForwardService forwardService;

    @Test
    void goRedirectsWith302AndNoStore() throws Exception {
        when(forwardService.forwardPlan(eq(7L), eq("S2"))).thenReturn(URI.create("https://partner.test/plan"));

        mvc.perform(get("/go/7").param("from", "S2"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://partner.test/plan"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownPlanForwardIs404() throws Exception {
        when(forwardService.forwardPlan(eq(999L), any())).thenThrow(new NotFoundException("plan 999"));

        mvc.perform(get("/go/999")).andExpect(status().isNotFound());
    }

    @Test
    void cheapestWithBadNetworkIs400() throws Exception {
        mvc.perform(get("/api/plans/cheapest").param("network", "SK").param("dataGb", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void cheapestWithNoMatchIs204() throws Exception {
        when(planService.findCheapest(eq(Network.SKT), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/plans/cheapest").param("network", "skt")).andExpect(status().isNoContent());
    }

    @Test
    void nonNumericPlanIdIs400() throws Exception {
        mvc.perform(get("/api/plans/abc")).andExpect(status().isBadRequest());
    }
}
