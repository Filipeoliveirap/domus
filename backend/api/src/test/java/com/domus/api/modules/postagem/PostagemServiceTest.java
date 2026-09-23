package com.domus.api.modules.postagem;

import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.postagem.dto.CriarPostagemRequest;
import com.domus.api.modules.postagem.dto.PostagemResponse;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostagemServiceTest {

    @Mock
    private PostagemRepository postagemRepository;
    @Mock
    private CurtidaPostagemRepository curtidaRepository;
    @Mock
    private ComentarioPostagemRepository comentarioRepository;
    @Mock
    private IgrejaRepository igrejaRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private FotoRepository fotoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private PostagemService postagemService;

    @Test
    @DisplayName("membro_comunidade_nao_pode_criar_postagem_oficial")
    void membroComunidadeNaoPodeCriarPostagemOficial() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        CriarPostagemRequest request = new CriarPostagemRequest(
                TipoPostagem.MURAL_AVISO, true, "Título", "Conteúdo", null, null, false
        );

        assertThatThrownBy(() -> postagemService.criarPostagem(igrejaId, pessoaId, "MEMBRO", request))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Apenas administradores e líderes");
    }

    @Test
    @DisplayName("admin_pode_criar_postagem_oficial")
    void adminPodeCriarPostagemOficial() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        CriarPostagemRequest request = new CriarPostagemRequest(
                TipoPostagem.MURAL_AVISO, true, "Título", "Conteúdo", null, null, false
        );

        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setNome("Admin User");

        Postagem postSalvo = Postagem.builder()
                .id(UUID.randomUUID())
                .igreja(igreja)
                .autorPessoa(pessoa)
                .tipo(TipoPostagem.MURAL_AVISO)
                .oficial(true)
                .titulo("Título")
                .conteudo("Conteúdo")
                .build();

        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa));
        when(postagemRepository.save(any(Postagem.class))).thenReturn(postSalvo);

        PostagemResponse response = postagemService.criarPostagem(igrejaId, pessoaId, "ADMIN_IGREJA", request);

        assertThat(response).isNotNull();
        assertThat(response.oficial()).isTrue();
        assertThat(response.conteudo()).isEqualTo("Conteúdo");
    }
}
