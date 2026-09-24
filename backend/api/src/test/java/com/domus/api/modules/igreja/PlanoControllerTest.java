package com.domus.api.modules.igreja;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanoControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanoController()).build();
    }

    @Test
    void deveRetornarListaDePlanosComPrecosELimites() throws Exception {
        mockMvc.perform(get("/api/planos")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("BASICO"))
            .andExpect(jsonPath("$[0].valorMensal").value(79.0))
            .andExpect(jsonPath("$[0].limitePessoas").value(60))
            .andExpect(jsonPath("$[1].id").value("PRO"))
            .andExpect(jsonPath("$[1].valorMensal").value(179.0));
    }
}
