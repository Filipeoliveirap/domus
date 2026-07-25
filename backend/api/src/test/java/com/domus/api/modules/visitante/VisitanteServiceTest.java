package com.domus.api.modules.visitante;

import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.DTOs.VisitanteRequest;
import com.domus.api.modules.visitante.DTOs.VisitanteResponse;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VisitanteServiceTest {

    VisitanteRepository visitanteRepository;
    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    CelulaRepository celulaRepository;
    CelulaMembroRepository celulaMembroRepository;
    VisitanteService service;

    UUID igrejaId = UUID.randomUUID();
    UUID visitanteId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        visitanteRepository = mock(VisitanteRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        celulaRepository = mock(CelulaRepository.class);
        celulaMembroRepository = mock(CelulaMembroRepository.class);
        service = new VisitanteService(visitanteRepository, igrejaRepository, usuarioRepository,
                celulaRepository, celulaMembroRepository);

        when(igrejaRepository.findById(igrejaId))
                .thenReturn(Optional.of(igreja()));
        when(visitanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Visitante visitante() {
        return Visitante.builder()
                .id(visitanteId).igreja(igreja()).nome("Ana Silva")
                .build();
    }

    private VisitanteRequest requestBase() {
        return new VisitanteRequest("Ana Silva", null, null, null, null, null, false, null, null);
    }

    private void dadoQueExiste() {
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId))
                .thenReturn(Optional.of(visitante()));
    }

    @Test
    void criarVisitanteSalvaComSucesso() {
        VisitanteRequest req = requestBase();

        VisitanteResponse response = service.criar(req, igrejaId, null);

        assertThat(response.nome()).isEqualTo("Ana Silva");
        verify(visitanteRepository).save(any(Visitante.class));
    }

    @Test
    void criarVisitanteRecusaNomeEmBranco() {
        VisitanteRequest req = new VisitanteRequest("   ", null, null, null, null, null, false, null, null);

        assertThatThrownBy(() -> service.criar(req, igrejaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nome");
        verify(visitanteRepository, never()).save(any());
    }

    @Test
    void criarVisitanteComTemFilhosTrueEQuantidadeNull() {
        VisitanteRequest req = new VisitanteRequest("Maria", null, null, null, null, null, true, null, null);

        assertThatThrownBy(() -> service.criar(req, igrejaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("filhos");
    }

    @Test
    void criarVisitanteComTemFilhosTrueEQuantidadeNegativa() {
        VisitanteRequest req = new VisitanteRequest("Maria", null, null, null, null, null, true, -1, null);

        assertThatThrownBy(() -> service.criar(req, igrejaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("filhos");
    }

    @Test
    void criarVisitanteComTemFilhosTrueEQuantidadeZeroPermite() {
        VisitanteRequest req = new VisitanteRequest("Maria", null, null, null, null, null, true, 0, null);

        VisitanteResponse response = service.criar(req, igrejaId, null);

        assertThat(response.quantidadeFilhos()).isEqualTo(0);
    }

    @Test
    void criarVisitanteComTemFilhosFalseIgnoraQuantidade() {
        VisitanteRequest req = new VisitanteRequest("Maria", null, null, null, null, null, false, 5, null);

        VisitanteResponse response = service.criar(req, igrejaId, null);

        assertThat(response.quantidadeFilhos()).isNull();
    }

    @Test
    void criarVisitanteComEnderecoMapeiaCorretamente() {
        VisitanteRequest req = new VisitanteRequest("João", null,
                new com.domus.api.modules.pessoa.DTO.EnderecoDTO(
                        "12345-678", "Rua A", "100", "Apto 2", "Centro", "São Paulo", "SP"),
                null, null, null, false, null, null);

        VisitanteResponse response = service.criar(req, igrejaId, null);

        assertThat(response.endereco()).isNotNull();
        assertThat(response.endereco().cep()).isEqualTo("12345-678");
        assertThat(response.endereco().cidade()).isEqualTo("São Paulo");
    }

    @Test
    void listarVisitantesBuscaPorNomeETelefone() {
        Visitante v = visitante();
        Page<Visitante> pagina = new PageImpl<>(List.of(v));
        when(visitanteRepository.buscarPorIgreja(eq(igrejaId), eq("ana"), isNull(), isNull(), isNull(), any()))
                .thenReturn(pagina);

        PagedResponse<VisitanteResponse> response = service.listar(igrejaId, "ana", null, null, null,
                PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).nome()).isEqualTo("Ana Silva");
    }

    @Test
    void listarVisitantesFiltraPorContatoRealizadoTrue() {
        Visitante v = visitante();
        v.setContatoRealizado(true);
        Page<Visitante> pagina = new PageImpl<>(List.of(v));
        when(visitanteRepository.buscarPorIgreja(eq(igrejaId), isNull(), eq(true), isNull(), isNull(), any()))
                .thenReturn(pagina);

        PagedResponse<VisitanteResponse> response = service.listar(igrejaId, null, true, null, null,
                PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).contatoRealizado()).isTrue();
    }

    @Test
    void listarVisitantesSemFiltroRetornaTodos() {
        Page<Visitante> pagina = new PageImpl<>(List.of(visitante()));
        when(visitanteRepository.buscarPorIgreja(eq(igrejaId), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(pagina);

        PagedResponse<VisitanteResponse> response = service.listar(igrejaId, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
    }

    @Test
    void buscarPorIdRetornaVisitanteQuandoExiste() {
        dadoQueExiste();

        VisitanteResponse response = service.buscarPorId(visitanteId, igrejaId);

        assertThat(response.nome()).isEqualTo("Ana Silva");
    }

    @Test
    void buscarPorIdLancaNaoEncontradoParaOutraIgreja() {
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(visitanteId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toggleContatoDefineComoTrue() {
        dadoQueExiste();

        VisitanteResponse response = service.toggleContato(visitanteId, true, igrejaId);

        assertThat(response.contatoRealizado()).isTrue();
    }

    @Test
    void toggleContatoRetornaNaoEncontradoParaOutraIgreja() {
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleContato(visitanteId, true, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toggleVisitaAlternaDeTrueParaFalse() {
        Visitante v = visitante();
        v.setVisitaRealizada(true);
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)).thenReturn(Optional.of(v));

        VisitanteResponse response = service.toggleVisita(visitanteId, false, igrejaId);

        assertThat(response.visitaRealizada()).isFalse();
    }

    @Test
    void deleteVisitanteEhHardDelete() {
        dadoQueExiste();

        service.excluir(visitanteId, igrejaId);

        verify(visitanteRepository).delete(any(Visitante.class));
    }

    @Test
    void deleteVisitanteRetornaNaoEncontradoParaOutraIgreja() {
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(visitanteId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(visitanteRepository, never()).delete(any());
    }

    @Test
    void atualizarVisitantePreservaDadosNaoEnviados() {
        Visitante existente = Visitante.builder()
                .id(visitanteId).igreja(igreja()).nome("Antigo")
                .contatoRealizado(true).visitaRealizada(true).acompanhamentoFeito(false)
                .build();
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId))
                .thenReturn(Optional.of(existente));

        VisitanteRequest req = new VisitanteRequest("Novo Nome", null, null, null, null, null, false, null, null);

        VisitanteResponse response = service.atualizar(visitanteId, req, igrejaId, null);

        assertThat(response.nome()).isEqualTo("Novo Nome");
        assertThat(response.contatoRealizado()).isTrue();
        assertThat(response.acompanhamentoFeito()).isFalse();
    }
}
