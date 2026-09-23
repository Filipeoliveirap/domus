package com.domus.api.modules.postagem;

import com.domus.api.modules.postagem.dto.PostagemResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostagemController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostagemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostagemService postagemService;

    @MockBean
    private UsuarioAutenticado usuarioAutenticado;

    @Test
    @DisplayName("listar_mural_retorna_lista_com_sucesso")
    void listarMuralRetornaListaComSucesso() throws Exception {
        UUID igrejaId = UUID.randomUUID();
        when(usuarioAutenticado.getIgrejaId()).thenReturn(igrejaId);
        when(postagemService.listarMural(igrejaId)).thenReturn(List.of());

        mockMvc.perform(get("/postagens/mural")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
