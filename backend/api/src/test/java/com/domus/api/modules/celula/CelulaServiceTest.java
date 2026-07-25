package com.domus.api.modules.celula;

import com.domus.api.modules.celula.DTOs.*;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        service = new CelulaService(celulaRepository, membroRepository, igrejaRepository,
                usuarioRepository, pessoaRepository, visitanteRepository);

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
        return new CelulaRequest(nome, null, null);
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
                new CelulaRequest("Célula Sião", DiaSemana.SEXTA, "19:00"), igrejaId, null);
        assertThat(response.diaSemana()).isEqualTo(DiaSemana.SEXTA);
        assertThat(response.horario()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void listarCelulasRetornaListaComResumo() {
        Celula c = celula();
        when(celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId)).thenReturn(List.of(c));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        List<CelulaResponse> response = service.listar(igrejaId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).nome()).isEqualTo("Célula Bethânia");
    }

    @Test
    void excluirCelulaVaziaEhHardDelete() {
        dadoQueExiste();
        when(membroRepository.existsByCelulaId(celulaId)).thenReturn(false);

        service.excluir(celulaId, igrejaId);

        verify(celulaRepository).deleteById(celulaId);
    }

    @Test
    void excluirCelulaComMembrosEhSoftDelete() {
        dadoQueExiste();
        when(membroRepository.existsByCelulaId(celulaId)).thenReturn(true);

        service.excluir(celulaId, igrejaId);

        verify(celulaRepository).delete(any(Celula.class));
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

        assertThatThrownBy(() -> service.atualizarPapel(celulaId, membro.getId(), igrejaId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("visitante");
    }

    @Test
    void celulaDeOutraIgrejaRetornaNaoEncontrado() {
        when(celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhe(celulaId, igrejaId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
