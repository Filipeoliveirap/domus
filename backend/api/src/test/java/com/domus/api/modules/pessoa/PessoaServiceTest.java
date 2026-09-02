package com.domus.api.modules.pessoa;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.DTO.PessoaRequestDTO;
import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import com.domus.api.modules.usuario.UsuarioService;
import com.domus.api.shared.dominio.Endereco;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Data de batismo só faz sentido para vínculo MEMBRO; perder o vínculo MEMBRO cancela inscrições em eventos exclusivos. */
class PessoaServiceTest {

    PessoaRepository pessoaRepository;
    IgrejaRepository igrejaRepository;
    UsuarioService usuarioService;
    InscricaoService inscricaoService;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    ReindexacaoMovimentacaoService reindexacaoMovimentacaoService;
    FotoService fotoService;
    com.domus.api.modules.evento.EventoRepository eventoRepository;
    com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository;
    com.domus.api.modules.celula.CelulaMembroRepository celulaMembroRepository;
    com.domus.api.modules.ministerio.MinisterioMembroRepository ministerioMembroRepository;
    com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinteRepository movimentacaoContribuinteRepository;
    com.domus.api.modules.visitante.VisitanteRepository visitanteRepository;
    PessoaService service;

    UUID igrejaId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        pessoaRepository = mock(PessoaRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioService = mock(UsuarioService.class);
        inscricaoService = mock(InscricaoService.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        reindexacaoMovimentacaoService = mock(ReindexacaoMovimentacaoService.class);
        fotoService = mock(FotoService.class);
        eventoRepository = mock(com.domus.api.modules.evento.EventoRepository.class);
        inscricaoRepository = mock(com.domus.api.modules.evento.inscricao.InscricaoRepository.class);
        celulaMembroRepository = mock(com.domus.api.modules.celula.CelulaMembroRepository.class);
        ministerioMembroRepository = mock(com.domus.api.modules.ministerio.MinisterioMembroRepository.class);
        movimentacaoContribuinteRepository = mock(com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinteRepository.class);
        visitanteRepository = mock(com.domus.api.modules.visitante.VisitanteRepository.class);
        service = new PessoaService(pessoaRepository, igrejaRepository, usuarioService,
                inscricaoService, cacheEvictor, outboxRegistrador, reindexacaoMovimentacaoService,
                fotoService, eventoRepository,
                mock(com.domus.api.modules.evento.EventoResponsavelRepository.class),
                inscricaoRepository, celulaMembroRepository,
                ministerioMembroRepository, movimentacaoContribuinteRepository, visitanteRepository);

        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pessoaRepository.findByIgrejaIdAndTelefoneIsNotNull(igrejaId))
                .thenReturn(java.util.List.of());
    }

    private PessoaRequestDTO dto(Vinculo vinculo, LocalDate dataBatismo) {
        return dto(vinculo, dataBatismo, null);
    }

    private PessoaRequestDTO dto(Vinculo vinculo, LocalDate dataBatismo, UUID fotoId) {
        return new PessoaRequestDTO("Maria", null, null, null, null,
                vinculo, null, null, null, null, dataBatismo, fotoId);
    }

    @Test
    void cadastrarRecusaDataDeBatismoParaCongregante() {
        assertThatThrownBy(() -> service.cadastrarMembro(
                dto(Vinculo.CONGREGANTE, LocalDate.now().minusYears(1)), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MEMBRO");
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void cadastrarAceitaDataDeBatismoParaMembro() {
        service.cadastrarMembro(dto(Vinculo.MEMBRO, LocalDate.now().minusYears(1)), igrejaId);
        verify(pessoaRepository).save(any());
    }

    @Test
    void atualizarRecusaDataDeBatismoParaCongregante() {
        Pessoa existente = Pessoa.builder().id(pessoaId).nome("Maria").vinculo(Vinculo.MEMBRO).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizarMembro(pessoaId,
                dto(Vinculo.CONGREGANTE, LocalDate.now().minusYears(1)), igrejaId))
                .isInstanceOf(BusinessException.class);
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void atualizarCancelaInscricoesExclusivasQuandoPerdeVinculoMembro() {
        Pessoa existente = Pessoa.builder().id(pessoaId).nome("Maria").vinculo(Vinculo.MEMBRO).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));

        service.atualizarMembro(pessoaId, dto(Vinculo.CONGREGANTE, null), igrejaId);

        verify(inscricaoService).cancelarInscricoesEmEventosExclusivos(pessoaId);
    }

    @Test
    void atualizarNaoCancelaInscricoesQuandoContinuaMembro() {
        Pessoa existente = Pessoa.builder().id(pessoaId).nome("Maria").vinculo(Vinculo.MEMBRO).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));

        service.atualizarMembro(pessoaId, dto(Vinculo.MEMBRO, LocalDate.now().minusYears(1)), igrejaId);

        verify(inscricaoService, never()).cancelarInscricoesEmEventosExclusivos(any());
    }

    /** Trocar a foto precisa remover a antiga, senão o bucket acumula arquivo órfão pra sempre. */
    @Test
    void atualizarRemoveFotoAntigaQuandoFotoMuda() {
        UUID fotoAntigaId = UUID.randomUUID();
        UUID fotoNovaId = UUID.randomUUID();

        Foto fotoAntiga = new Foto();
        fotoAntiga.setId(fotoAntigaId);
        Foto fotoNova = new Foto();
        fotoNova.setId(fotoNovaId);

        Pessoa existente = Pessoa.builder().id(pessoaId).nome("Maria")
                .vinculo(Vinculo.MEMBRO).foto(fotoAntiga).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));
        when(fotoService.buscarParaVincular(fotoNovaId, igrejaId)).thenReturn(fotoNova);

        service.atualizarMembro(pessoaId,
                dto(Vinculo.MEMBRO, LocalDate.now().minusYears(1), fotoNovaId), igrejaId);

        verify(fotoService).remover(fotoAntigaId);
    }

    /** Sem troca de foto (mesmo id, ou os dois nulos), nada deve ser removido. */
    @Test
    void atualizarNaoRemoveFotoQuandoNaoMuda() {
        UUID fotoId = UUID.randomUUID();
        Foto foto = new Foto();
        foto.setId(fotoId);

        Pessoa existente = Pessoa.builder().id(pessoaId).nome("Maria")
                .vinculo(Vinculo.MEMBRO).foto(foto).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));
        when(fotoService.buscarParaVincular(fotoId, igrejaId)).thenReturn(foto);

        service.atualizarMembro(pessoaId,
                dto(Vinculo.MEMBRO, LocalDate.now().minusYears(1), fotoId), igrejaId);

        verify(fotoService, never()).remover(any());
    }

    @org.junit.jupiter.api.Test
    void atualizarMinhaFoto_trocaSoAFoto_mantemRestoIntacto() {
        Foto fotoAntiga = new Foto();
        fotoAntiga.setId(UUID.randomUUID());
        Pessoa existente = Pessoa.builder()
                .id(pessoaId).nome("Ana").email("ana@ex.com")
                .vinculo(Vinculo.CONGREGANTE).foto(fotoAntiga)
                .build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));

        Foto fotoNova = new Foto();
        fotoNova.setId(UUID.randomUUID());
        when(fotoService.buscarParaVincular(fotoNova.getId(), igrejaId)).thenReturn(fotoNova);

        PessoaResponse resposta = service.atualizarMinhaFoto(pessoaId, fotoNova.getId(), igrejaId);

        assertThat(resposta.fotoId()).isEqualTo(fotoNova.getId());
        assertThat(resposta.nome()).isEqualTo("Ana");
        verify(fotoService).remover(fotoAntiga.getId());
    }

    @org.junit.jupiter.api.Test
    void atualizarMinhaFoto_pessoaDeOutraIgreja_lancaNaoEncontrado() {
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarMinhaFoto(pessoaId, UUID.randomUUID(), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }

    private Pessoa pessoaComDadosSensiveis() {
        return Pessoa.builder()
                .id(pessoaId).nome("Maria").vinculo(Vinculo.MEMBRO)
                .telefone("11999998888")
                .dataNascimento(LocalDate.of(1990, 5, 20))
                .observacoes("Passou por acompanhamento pastoral em 2025.")
                .endereco(Endereco.builder().cep("01001-000").logradouro("Praça da Sé")
                        .cidade("São Paulo").uf("SP").build())
                .build();
    }

    @Test
    void buscarPorId_semDadosSensiveis_escondeEnderecoEObservacoes() {
        when(pessoaRepository.findByIdAndIgrejaIdIncluindoArquivadas(pessoaId, igrejaId))
                .thenReturn(Optional.of(pessoaComDadosSensiveis()));

        PessoaResponse resposta = service.buscarPorId(pessoaId, igrejaId, false);

        assertThat(resposta.endereco()).isNull();
        assertThat(resposta.observacoes()).isNull();
        assertThat(resposta.telefone()).isEqualTo("11999998888");
        assertThat(resposta.dataNascimento()).isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    void buscarPorId_comDadosSensiveis_mostraTudo() {
        when(pessoaRepository.findByIdAndIgrejaIdIncluindoArquivadas(pessoaId, igrejaId))
                .thenReturn(Optional.of(pessoaComDadosSensiveis()));

        PessoaResponse resposta = service.buscarPorId(pessoaId, igrejaId, true);

        assertThat(resposta.endereco()).isNotNull();
        assertThat(resposta.endereco().cidade()).isEqualTo("São Paulo");
        assertThat(resposta.observacoes()).isEqualTo("Passou por acompanhamento pastoral em 2025.");
    }

    @Test
    void definirEmailInicialGravaQuandoPessoaAindaNaoTemEmail() {
        Pessoa semEmail = Pessoa.builder()
                .id(pessoaId).igreja(new Igreja() {{ setId(igrejaId); }}).nome("Sem Email")
                .vinculo(Vinculo.MEMBRO).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(semEmail));
        when(pessoaRepository.existsByEmail("novo@email.com")).thenReturn(false);
        when(pessoaRepository.existsByEmailIncluindoArquivados("novo@email.com")).thenReturn(false);

        PessoaResponse resposta = service.definirEmailInicial(pessoaId, "novo@email.com", igrejaId);

        assertThat(resposta.email()).isEqualTo("novo@email.com");
        assertThat(semEmail.getEmail()).isEqualTo("novo@email.com");
    }

    @Test
    void definirEmailInicialRecusaQuandoPessoaJaTemEmail() {
        Pessoa comEmail = Pessoa.builder()
                .id(pessoaId).igreja(new Igreja() {{ setId(igrejaId); }}).nome("Com Email")
                .email("ja-tenho@email.com").vinculo(Vinculo.MEMBRO).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(comEmail));

        assertThatThrownBy(() -> service.definirEmailInicial(pessoaId, "outro@email.com", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "EMAIL_JA_DEFINIDO");

        assertThat(comEmail.getEmail()).isEqualTo("ja-tenho@email.com");
        verify(pessoaRepository, never()).save(any());
    }

    @Test
    void definirEmailInicialRecusaEmailJaUsadoPorOutraPessoa() {
        Pessoa semEmail = Pessoa.builder()
                .id(pessoaId).igreja(new Igreja() {{ setId(igrejaId); }}).nome("Sem Email")
                .vinculo(Vinculo.MEMBRO).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(semEmail));
        when(pessoaRepository.existsByEmail("duplicado@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.definirEmailInicial(pessoaId, "duplicado@email.com", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "EMAIL_DUPLICADO");

        verify(pessoaRepository, never()).save(any());
    }
}
