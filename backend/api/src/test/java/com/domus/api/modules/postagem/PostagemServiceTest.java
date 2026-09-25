package com.domus.api.modules.postagem;

import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.postagem.dto.CriarPostagemRequest;
import com.domus.api.modules.postagem.dto.PostagemResponse;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
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
    private CurtidaComentarioRepository curtidaComentarioRepository;
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
    @Mock
    private FamiliaIgrejaService familiaIgrejaService;

    @InjectMocks
    private PostagemService postagemService;

    @Test
    @DisplayName("membro_comunidade_nao_pode_criar_postagem_oficial")
    void membroComunidadeNaoPodeCriarPostagemOficial() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        CriarPostagemRequest request = new CriarPostagemRequest(
                TipoPostagem.MURAL_AVISO, true, "Título", "Conteúdo", null, null, false, true
        );

        assertThatThrownBy(() -> postagemService.criarPostagem(igrejaId, pessoaId, "MEMBRO", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apenas administradores e líderes");
    }

    @Test
    @DisplayName("admin_pode_criar_postagem_oficial")
    void adminPodeCriarPostagemOficial() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        CriarPostagemRequest request = new CriarPostagemRequest(
                TipoPostagem.MURAL_AVISO, true, "Título", "Conteúdo", null, null, false, false
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
                .restritoPropriaIgreja(false)
                .build();

        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa));
        when(postagemRepository.save(any(Postagem.class))).thenReturn(postSalvo);

        PostagemResponse response = postagemService.criarPostagem(igrejaId, pessoaId, "ADMIN_IGREJA", request);

        assertThat(response).isNotNull();
        assertThat(response.oficial()).isTrue();
        assertThat(response.conteudo()).isEqualTo("Conteúdo");
        assertThat(response.restritoPropriaIgreja()).isFalse();
    }

    @Test
    @DisplayName("deve_lancar_excecao_quando_postagem_sem_conteudo_e_sem_foto")
    void deveLancarExcecaoQuandoPostagemSemConteudoESemFoto() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        CriarPostagemRequest request = new CriarPostagemRequest(
                TipoPostagem.DEVOCIONAL, false, null, "   ", null, null, false, true
        );

        assertThatThrownBy(() -> postagemService.criarPostagem(igrejaId, pessoaId, "MEMBRO", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A postagem deve conter texto ou uma foto.");
    }

    @Test
    @DisplayName("pode_criar_postagem_somente_com_foto")
    void podeCriarPostagemSomenteComFoto() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID fotoId = UUID.randomUUID();

        CriarPostagemRequest request = new CriarPostagemRequest(
                TipoPostagem.DEVOCIONAL, false, null, "", fotoId, null, false, true
        );

        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setNome("Membro Teste");

        Postagem postSalvo = Postagem.builder()
                .id(UUID.randomUUID())
                .igreja(igreja)
                .autorPessoa(pessoa)
                .tipo(TipoPostagem.DEVOCIONAL)
                .oficial(false)
                .conteudo("")
                .restritoPropriaIgreja(true)
                .build();

        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa));
        when(postagemRepository.save(any(Postagem.class))).thenReturn(postSalvo);

        PostagemResponse response = postagemService.criarPostagem(igrejaId, pessoaId, "MEMBRO", request);

        assertThat(response).isNotNull();
        assertThat(response.conteudo()).isEmpty();
        assertThat(response.restritoPropriaIgreja()).isTrue();
    }

    @Test
    @DisplayName("usuario_de_outra_igreja_da_rede_nao_pode_editar_postagem_alheia")
    void usuarioDeOutraIgrejaDaRedeNaoPodeEditarPostagemAlheia() {
        UUID minhaIgrejaId = UUID.randomUUID();
        UUID igrejaAutorId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID postagemId = UUID.randomUUID();

        Igreja igrejaAutor = new Igreja();
        igrejaAutor.setId(igrejaAutorId);
        Pessoa autor = new Pessoa();
        autor.setId(UUID.randomUUID());

        Postagem post = Postagem.builder()
                .id(postagemId)
                .igreja(igrejaAutor)
                .autorPessoa(autor)
                .tipo(TipoPostagem.DEVOCIONAL)
                .oficial(false)
                .conteudo("Post compartilhado")
                .restritoPropriaIgreja(false)
                .build();

        when(familiaIgrejaService.idsDaFamiliaCompleta(minhaIgrejaId)).thenReturn(Set.of(minhaIgrejaId, igrejaAutorId));
        when(postagemRepository.findByIdAndFamilia(any(), any(), any())).thenReturn(Optional.of(post));

        CriarPostagemRequest editRequest = new CriarPostagemRequest(
                TipoPostagem.DEVOCIONAL, false, null, "Editado", null, null, false, false
        );

        assertThatThrownBy(() -> postagemService.atualizarPostagem(minhaIgrejaId, pessoaId, "ADMIN_IGREJA", postagemId, editRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Você não tem permissão para editar esta postagem.");
    }
}
