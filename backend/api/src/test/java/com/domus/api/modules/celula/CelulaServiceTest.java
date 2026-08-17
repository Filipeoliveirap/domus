package com.domus.api.modules.celula;

import com.domus.api.modules.celula.DTOs.*;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ConflitoNegocioException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CelulaServiceTest {

    CelulaRepository celulaRepository;
    CelulaMembroRepository membroRepository;
    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    PessoaRepository pessoaRepository;
    VisitanteRepository visitanteRepository;
    FotoService fotoService;
    OutboxRegistrador outboxRegistrador;
    CelulaService service;

    UUID igrejaId = UUID.randomUUID();
    UUID celulaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        celulaRepository = mock(CelulaRepository.class);
        membroRepository = mock(CelulaMembroRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        visitanteRepository = mock(VisitanteRepository.class);
        fotoService = mock(FotoService.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        service = new CelulaService(celulaRepository, membroRepository, igrejaRepository,
                usuarioRepository, pessoaRepository, visitanteRepository, fotoService, outboxRegistrador);

        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja()));
        when(celulaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Celula celula() {
        return Celula.builder().id(celulaId).igreja(igreja()).nome("Célula Bethânia").build();
    }

    private CelulaRequest request(String nome) {
        return new CelulaRequest(nome, null, null, null);
    }

    private void dadoQueExiste() {
        when(celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId))
                .thenReturn(Optional.of(celula()));
    }

    @Test
    void criarCelulaSalvaComSucesso() {
        CelulaResponse response = service.criar(request("Célula Bethânia"), igrejaId, null);
        assertThat(response.nome()).isEqualTo("Célula Bethânia");
        verify(celulaRepository).save(any());
    }

    @Test
    void criarCelulaComDiaEHorario() {
        CelulaResponse response = service.criar(
                new CelulaRequest("Célula Sião", DiaSemana.SEXTA, "19:00", null), igrejaId, null);
        assertThat(response.diaSemana()).isEqualTo(DiaSemana.SEXTA);
        assertThat(response.horario()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void listarCelulasRetornaListaComResumo() {
        Celula c = celula();
        when(celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId)).thenReturn(List.of(c));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        List<CelulaResponse> response = service.listar(igrejaId, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).nome()).isEqualTo("Célula Bethânia");
    }

    @Test
    void excluirSempreArquiva() {
        dadoQueExiste();

        service.excluir(celulaId, igrejaId);

        verify(celulaRepository).delete(any(Celula.class));
        verify(celulaRepository, never()).deleteById(any());
        verify(celulaRepository, never()).hardDeleteById(any());
    }

    @Test
    void excluirDefinitivoApagaDeVerdadeQuandoVazia() {
        dadoQueExiste();
        when(membroRepository.existsByCelulaId(celulaId)).thenReturn(false);

        service.excluirDefinitivo(celulaId, igrejaId);

        verify(celulaRepository).hardDeleteById(celulaId);
    }

    @Test
    void excluirDefinitivoRecusaQuandoTemMembro() {
        dadoQueExiste();
        when(membroRepository.existsByCelulaId(celulaId)).thenReturn(true);

        assertThatThrownBy(() -> service.excluirDefinitivo(celulaId, igrejaId))
                .isInstanceOf(ConflitoNegocioException.class);

        verify(celulaRepository, never()).hardDeleteById(any());
    }

    @Test
    void atualizarPermiteAdmin() {
        dadoQueExiste();

        service.atualizar(celulaId, request("Novo nome"), igrejaId, null, null, true);

        verify(celulaRepository).save(any());
    }

    @Test
    void atualizarPermiteLiderDaCelula() {
        dadoQueExiste();
        UUID pessoaId = UUID.randomUUID();
        when(membroRepository.existsByCelulaIdAndPessoaIdAndPapel(celulaId, pessoaId, PapelCelula.LIDER))
                .thenReturn(true);

        service.atualizar(celulaId, request("Novo nome"), igrejaId, null, pessoaId, false);

        verify(celulaRepository).save(any());
    }

    @Test
    void atualizarRecusaQuemNaoEAdminNemLider() {
        dadoQueExiste();
        UUID pessoaId = UUID.randomUUID();
        when(membroRepository.existsByCelulaIdAndPessoaIdAndPapel(celulaId, pessoaId, PapelCelula.LIDER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.atualizar(celulaId, request("Novo nome"), igrejaId, null, pessoaId, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(celulaRepository, never()).save(any());
    }

    @Test
    void detalheRetornaDadosEMembros() {
        dadoQueExiste();
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        CelulaDetalheResponse response = service.detalhe(celulaId, igrejaId, null);

        assertThat(response.nome()).isEqualTo("Célula Bethânia");
        assertThat(response.souLiderDestaCelula()).isFalse();
    }

    @Test
    void adicionarPessoaMoveSeJaEstaEmOutraCelula() {
        UUID pessoaId = UUID.randomUUID();
        Celula outra = Celula.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Outra").build();

        dadoQueExiste();
        Pessoa pessoa = Pessoa.builder().id(pessoaId).nome("João").igreja(igreja()).build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa));
        CelulaMembro existente = CelulaMembro.builder()
                .id(UUID.randomUUID()).celula(outra).pessoa(pessoa).igreja(igreja()).build();
        when(membroRepository.findByPessoaId(pessoaId)).thenReturn(Optional.of(existente));

        service.adicionarMembro(celulaId,
                new AdicionarMembroCelulaRequest(pessoaId, null),
                igrejaId, null, true, null);

        assertThat(existente.getCelula().getId()).isEqualTo(celulaId);
        verify(membroRepository).save(existente);
    }

    @Test
    void adicionarPessoaSemPessoaIdNemVisitanteIdLancaErro() {
        dadoQueExiste();

        assertThatThrownBy(() -> service.adicionarMembro(celulaId,
                new AdicionarMembroCelulaRequest(null, null),
                igrejaId, null, true, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pessoaId ou visitanteId");
    }

    @Test
    void adicionarVisitanteGravaEntrouEmCelulaEm() {
        UUID visitanteId = UUID.randomUUID();
        dadoQueExiste();
        Visitante v = Visitante.builder().id(visitanteId).igreja(igreja()).nome("Ana").build();
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)).thenReturn(Optional.of(v));
        when(membroRepository.findByVisitanteId(visitanteId)).thenReturn(Optional.empty());
        when(visitanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adicionarMembro(celulaId,
                new AdicionarMembroCelulaRequest(null, visitanteId),
                igrejaId, null, true, null);

        verify(visitanteRepository).save(v);
    }

    @Test
    void atualizarPapelVisitanteLancaErro() {
        dadoQueExiste();
        CelulaMembro membro = CelulaMembro.builder()
                .id(UUID.randomUUID()).celula(celula()).visitante(
                        Visitante.builder().id(UUID.randomUUID()).igreja(igreja()).nome("A").build())
                .igreja(igreja()).build();
        when(membroRepository.findById(membro.getId())).thenReturn(Optional.of(membro));

        assertThatThrownBy(() -> service.atualizarPapel(celulaId, membro.getId(),
                new AtualizarPapelCelulaRequest(PapelCelula.LIDER), igrejaId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("visitante");
    }

    @Test
    void atualizarPapelPromoveParaLiderComValorExplicito() {
        dadoQueExiste();
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(pessoaId).nome("João").igreja(igreja()).build();
        CelulaMembro membro = CelulaMembro.builder()
                .id(UUID.randomUUID()).celula(celula()).pessoa(pessoa).papel(PapelCelula.MEMBRO)
                .igreja(igreja()).build();
        when(membroRepository.findById(membro.getId())).thenReturn(Optional.of(membro));

        service.atualizarPapel(celulaId, membro.getId(),
                new AtualizarPapelCelulaRequest(PapelCelula.LIDER), igrejaId, true);

        assertThat(membro.getPapel()).isEqualTo(PapelCelula.LIDER);
    }

    @Test
    void atualizarPapelRecusaQuemNaoEAdmin() {
        dadoQueExiste();

        assertThatThrownBy(() -> service.atualizarPapel(celulaId, UUID.randomUUID(),
                new AtualizarPapelCelulaRequest(PapelCelula.LIDER), igrejaId, false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void celulaDeOutraIgrejaRetornaNaoEncontrado() {
        when(celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhe(celulaId, igrejaId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listarArquivadasRetornaSoAsArquivadas() {
        Celula arquivada = celula();
        when(celulaRepository.findArquivadasPorIgreja(igrejaId)).thenReturn(List.of(arquivada));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        List<CelulaResponse> response = service.listarArquivadas(igrejaId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).nome()).isEqualTo("Célula Bethânia");
        verify(celulaRepository, never()).findByIgrejaIdOrderByNomeAsc(any());
    }

    @Test
    void restaurarTiraDoArquivoEReindexaNaBusca() {
        service.restaurar(celulaId, igrejaId);

        verify(celulaRepository).restaurarPorId(celulaId);
        verify(outboxRegistrador).registrar(
                com.domus.api.modules.outbox.TipoEntidadeOutbox.CELULA,
                com.domus.api.modules.outbox.TipoEventoOutbox.CRIADO,
                celulaId, igrejaId);
    }

    @Test
    void listarMarcaTemVinculoQuandoTemMembro() {
        Celula c = celula();
        when(celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId)).thenReturn(List.of(c));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId))
                .thenReturn(List.of(CelulaMembro.builder().build()));

        List<CelulaResponse> response = service.listar(igrejaId, null);

        assertThat(response.get(0).temVinculo()).isTrue();
    }

    @Test
    void listarMarcaSemVinculoQuandoVazia() {
        Celula c = celula();
        when(celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId)).thenReturn(List.of(c));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        List<CelulaResponse> response = service.listar(igrejaId, null);

        assertThat(response.get(0).temVinculo()).isFalse();
    }
}
