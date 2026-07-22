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
import com.domus.api.modules.usuario.UsuarioService;
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

/**
 * M5: data de batismo só faz sentido para vínculo MEMBRO (ver design doc). M6: perder o
 * vínculo MEMBRO cancela as inscrições em eventos exclusivos, reusando o InscricaoService.
 */
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
        service = new PessoaService(pessoaRepository, igrejaRepository, usuarioService,
                inscricaoService, cacheEvictor, outboxRegistrador, reindexacaoMovimentacaoService,
                fotoService, eventoRepository);

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

    /**
     * Trocar a foto (id antigo → id novo) precisa remover a foto ANTIGA — senão o bucket
     * acumula arquivo órfão para sempre, já que nada mais aponta para ele.
     */
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

    /**
     * Sem troca de foto (mesmo id, ou os dois nulos), NADA deve ser removido — a foto atual
     * continua sendo a mesma referenciada pela pessoa.
     */
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
}
