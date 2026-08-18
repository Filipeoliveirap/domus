package com.domus.api.modules.pessoa;

import com.domus.api.modules.ministerio.MinisterioService;
import com.domus.api.modules.pessoa.DTO.PessoaRequestDTO;
import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** E-mail é imutável em PUT /pessoas/me mesmo via ADMIN_IGREJA — controller força o e-mail já persistido antes de repassar pro service. */
@ExtendWith(MockitoExtension.class)
class PessoaControllerAtualizarMeTest {

    @Mock
    private PessoaService pessoaService;

    @Mock
    private UsuarioAutenticado usuarioAutenticado;

    @Mock
    private MinisterioService ministerioService;

    @Test
    void atualizarMe_admin_ignoraEmailDoPayloadEMantemOJaPersistido() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        when(usuarioAutenticado.getIgrejaId()).thenReturn(igrejaId);
        when(usuarioAutenticado.getPessoaId()).thenReturn(pessoaId);
        when(usuarioAutenticado.getRole()).thenReturn("ADMIN_IGREJA");

        PessoaResponse pessoaAtual = new PessoaResponse(
                pessoaId, "Fulano", "email.real@igreja.com", "11999999999",
                null, null, Vinculo.MEMBRO, null, null, null, null, null, null, null, null, false
        );
        when(pessoaService.buscarPorId(eq(pessoaId), eq(igrejaId), eq(true))).thenReturn(pessoaAtual);

        PessoaResponse respostaEsperada = pessoaAtual;
        when(pessoaService.atualizarMembro(eq(pessoaId), any(PessoaRequestDTO.class), eq(igrejaId)))
                .thenReturn(respostaEsperada);

        PessoaController controller = new PessoaController(pessoaService, usuarioAutenticado, ministerioService);

        PessoaRequestDTO payloadComEmailMalicioso = new PessoaRequestDTO(
                "Fulano", "email.trocado@fora.com", "11999999999", null, null,
                Vinculo.MEMBRO, null, null, null, null, null, null
        );

        controller.atualizarMe(payloadComEmailMalicioso);

        ArgumentCaptor<PessoaRequestDTO> captor = ArgumentCaptor.forClass(PessoaRequestDTO.class);
        org.mockito.Mockito.verify(pessoaService).atualizarMembro(eq(pessoaId), captor.capture(), eq(igrejaId));

        assertEquals("email.real@igreja.com", captor.getValue().email(),
                "email enviado pro service tem que ser o já persistido, nunca o do payload");
    }
}
