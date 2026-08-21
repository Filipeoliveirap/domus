package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificacaoServiceTest {

    NotificacaoRepository notificacaoRepository;
    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    NotificacaoService service;

    @BeforeEach
    void setup() {
        notificacaoRepository = mock(NotificacaoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new NotificacaoService(notificacaoRepository, igrejaRepository, usuarioRepository);
    }

    @Test
    void criarGravaNotificacaoComOsCamposCertos() {
        UUID igrejaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Igreja igreja = Igreja.builder().id(igrejaId).build();
        Usuario usuario = Usuario.builder().id(usuarioId).build();
        when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
        when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);

        service.criar(TipoNotificacao.ACESSO_CONCEDIDO, igrejaId, usuarioId, "Você recebeu acesso.", "/inicio");

        verify(notificacaoRepository).save(argThat(n ->
                n.getTipo() == TipoNotificacao.ACESSO_CONCEDIDO
                        && n.getTexto().equals("Você recebeu acesso.")
                        && n.getLink().equals("/inicio")
                        && !n.isLida()
                        && n.getIgreja() == igreja
                        && n.getUsuarioDestinatario() == usuario));
    }

    @Test
    void contarNaoLidasDelegaPraRepository() {
        UUID usuarioId = UUID.randomUUID();
        when(notificacaoRepository.countByUsuarioDestinatarioIdAndLidaFalse(usuarioId)).thenReturn(3L);

        assertThat(service.contarNaoLidas(usuarioId)).isEqualTo(3L);
    }

    @Test
    void listarMapeiaPaginaParaPagedResponse() {
        UUID usuarioId = UUID.randomUUID();
        Notificacao n = Notificacao.builder()
                .id(UUID.randomUUID()).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste").lida(false).build();
        when(notificacaoRepository.findByUsuarioDestinatarioId(eq(usuarioId), any()))
                .thenReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 10), 1));

        PagedResponse<com.domus.api.modules.notificacao.DTO.NotificacaoResponse> resposta =
                service.listar(usuarioId, PageRequest.of(0, 10));

        assertThat(resposta.getContent()).hasSize(1);
        assertThat(resposta.getContent().get(0).texto()).isEqualTo("Teste");
    }

    @Test
    void marcarComoLida_soMarcaSeForDoUsuarioCerto() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Notificacao n = Notificacao.builder().id(id).lida(false).build();
        when(notificacaoRepository.findByIdAndUsuarioDestinatarioId(id, usuarioId)).thenReturn(Optional.of(n));

        service.marcarComoLida(id, usuarioId);

        assertThat(n.isLida()).isTrue();
        verify(notificacaoRepository).save(n);
    }

    @Test
    void marcarComoLida_naoFazNadaSeNaoForDoUsuario() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(notificacaoRepository.findByIdAndUsuarioDestinatarioId(id, usuarioId)).thenReturn(Optional.empty());

        service.marcarComoLida(id, usuarioId);

        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void marcarTodasComoLidasMarcaTodasAsNaoLidasDoUsuario() {
        UUID usuarioId = UUID.randomUUID();
        Notificacao n1 = Notificacao.builder().id(UUID.randomUUID()).lida(false).build();
        Notificacao n2 = Notificacao.builder().id(UUID.randomUUID()).lida(false).build();
        when(notificacaoRepository.findByUsuarioDestinatarioIdAndLidaFalse(usuarioId)).thenReturn(List.of(n1, n2));

        service.marcarTodasComoLidas(usuarioId);

        assertThat(n1.isLida()).isTrue();
        assertThat(n2.isLida()).isTrue();
        verify(notificacaoRepository).saveAll(List.of(n1, n2));
    }
}
