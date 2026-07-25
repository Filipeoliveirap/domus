package com.domus.api.modules.celula;

import com.domus.api.modules.celula.DTOs.*;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CelulaService {

    private final CelulaRepository celulaRepository;
    private final CelulaMembroRepository membroRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final VisitanteRepository visitanteRepository;

    @Transactional(readOnly = true)
    public List<CelulaResponse> listar(UUID igrejaId) {
        return celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .map(c -> CelulaResponse.comResumo(c, membrosAtivosDe(c.getId())))
                .toList();
    }

    private List<CelulaMembro> membrosAtivosDe(UUID celulaId) {
        return membroRepository.findByCelulaIdOrderByPapelAsc(celulaId);
    }

    @Transactional
    public CelulaResponse criar(CelulaRequest data, UUID igrejaId, UUID usuarioId) {
        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, null);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;

        Celula celula = Celula.builder()
                .igreja(igreja).nome(nome)
                .diaSemana(data.diaSemana())
                .horario(data.horario() != null && !data.horario().isBlank()
                        ? LocalTime.parse(data.horario()) : null)
                .criadoPor(usuario).atualizadoPor(usuario)
                .build();

        return CelulaResponse.from(celulaRepository.save(celula));
    }

    @Transactional
    public CelulaResponse atualizar(UUID id, CelulaRequest data, UUID igrejaId, UUID usuarioId) {
        Celula celula = buscarDaIgrejaOuFalhar(id, igrejaId);

        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, id);

        celula.setNome(nome);
        celula.setDiaSemana(data.diaSemana());
        celula.setHorario(data.horario() != null && !data.horario().isBlank()
                ? LocalTime.parse(data.horario()) : null);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(celula::setAtualizadoPor);
        }

        return CelulaResponse.from(celulaRepository.save(celula));
    }

    @Transactional
    public void excluir(UUID id, UUID igrejaId) {
        Celula celula = buscarDaIgrejaOuFalhar(id, igrejaId);
        if (membroRepository.existsByCelulaId(id)) {
            celulaRepository.delete(celula);
        } else {
            membroRepository.findByCelulaIdOrderByPapelAsc(id).forEach(membroRepository::delete);
            celulaRepository.deleteById(id);
        }
    }

    @Transactional(readOnly = true)
    public CelulaDetalheResponse detalhe(UUID celulaId, UUID igrejaId, UUID pessoaLogadaId) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        boolean souLider = pessoaLogadaId != null && ehLiderDaCelula(celulaId, pessoaLogadaId);

        List<CelulaMembro> todos = membroRepository.findByCelulaIdOrderByPapelAsc(celulaId);
        List<MembroCelulaResponse> membros = todos.stream()
                .map(MembroCelulaResponse::from).toList();

        return CelulaDetalheResponse.from(celula, membros, souLider);
    }

    public boolean ehLiderDaCelula(UUID celulaId, UUID pessoaId) {
        return membroRepository.existsByCelulaIdAndPessoaIdAndPapel(celulaId, pessoaId, PapelCelula.LIDER);
    }

    private void exigirAdminOuLider(UUID celulaId, UUID atorPessoaId, boolean isAdmin) {
        if (isAdmin) return;
        if (atorPessoaId == null || !ehLiderDaCelula(celulaId, atorPessoaId)) {
            throw new AccessDeniedException(
                    "Só o líder desta célula ou um administrador pode fazer isso.");
        }
    }

    @Transactional
    public void adicionarMembro(UUID celulaId, AdicionarMembroCelulaRequest data,
                                 UUID igrejaId, UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        exigirAdminOuLider(celulaId, atorPessoaId, isAdmin);

        if (data.pessoaId() != null) {
            adicionarPessoa(celula, data.pessoaId(), igrejaId, usuarioId);
        } else if (data.visitanteId() != null) {
            adicionarVisitante(celula, data.visitanteId(), igrejaId, usuarioId);

            Visitante v = visitanteRepository.findByIdAndIgrejaId(data.visitanteId(), igrejaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));
            if (v.getEntrouEmCelulaEm() == null) {
                v.setEntrouEmCelulaEm(LocalDateTime.now());
                visitanteRepository.save(v);
            }
        } else {
            throw new BusinessException("MEMBRO_INVALIDO",
                    "Informe pessoaId ou visitanteId.");
        }
    }

    private void adicionarPessoa(Celula celula, UUID pessoaId, UUID igrejaId, UUID usuarioId) {
        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        CelulaMembro existente = membroRepository.findByPessoaId(pessoaId).orElse(null);
        if (existente != null) {
            existente.setCelula(celula);
            membroRepository.save(existente);
            return;
        }

        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;
        membroRepository.save(CelulaMembro.builder()
                .igreja(celula.getIgreja()).celula(celula).pessoa(pessoa)
                .criadoPor(usuario).atualizadoPor(usuario).build());
    }

    private void adicionarVisitante(Celula celula, UUID visitanteId, UUID igrejaId, UUID usuarioId) {
        Visitante visitante = visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));

        CelulaMembro existente = membroRepository.findByVisitanteId(visitanteId).orElse(null);
        if (existente != null) {
            existente.setCelula(celula);
            membroRepository.save(existente);
            return;
        }

        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;
        membroRepository.save(CelulaMembro.builder()
                .igreja(celula.getIgreja()).celula(celula).visitante(visitante)
                .criadoPor(usuario).atualizadoPor(usuario).build());
    }

    @Transactional
    public void removerMembro(UUID celulaId, UUID membroId, UUID igrejaId,
                               UUID atorPessoaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        exigirAdminOuLider(celulaId, atorPessoaId, isAdmin);

        CelulaMembro membro = membroRepository.findById(membroId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        membroRepository.delete(membro);
    }

    @Transactional
    public void atualizarPapel(UUID celulaId, UUID membroId, UUID igrejaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        if (!isAdmin) {
            throw new AccessDeniedException(
                    "Só um administrador pode promover ou rebaixar líder de célula.");
        }

        CelulaMembro membro = membroRepository.findById(membroId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        if (membro.getVisitante() != null) {
            throw new BusinessException("VISITANTE_NAO_PODE_SER_LIDER",
                    "Um visitante não pode ser promovido a líder de célula.");
        }
        membro.setPapel(membro.getPapel() == PapelCelula.LIDER ? PapelCelula.MEMBRO : PapelCelula.LIDER);
        membroRepository.save(membro);
    }

    @Transactional
    public Pessoa converterVisitante(UUID celulaId, UUID visitanteId,
                                      ConverterVisitanteRequest data, UUID igrejaId,
                                      UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        exigirAdminOuLider(celulaId, atorPessoaId, isAdmin);

        Visitante v = visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));

        Pessoa pessoa = Pessoa.builder()
                .igreja(celula.getIgreja()).nome(v.getNome()).telefone(v.getTelefone())
                .endereco(v.getEndereco()).sexo(v.getSexo()).estadoCivil(v.getEstadoCivil())
                .dataNascimento(v.getDataNascimento()).observacoes(v.getObservacoes())
                .vinculo(data.vinculo())
                .build();
        pessoa = pessoaRepository.save(pessoa);

        CelulaMembro membro = membroRepository.findByVisitanteId(visitanteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Visitante não está vinculado a esta célula."));
        membro.setVisitante(null);
        membro.setPessoa(pessoa);
        membroRepository.save(membro);

        v.setConvertidoPessoaId(pessoa.getId());
        visitanteRepository.save(v);

        return pessoa;
    }

    private Celula buscarDaIgrejaOuFalhar(UUID id, UUID igrejaId) {
        return celulaRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Célula não encontrada."));
    }

    private void validarNaoDuplicado(String nome, UUID igrejaId, UUID ignorarId) {
        String normalizado = TextoUtil.normalizarParaComparacao(nome);
        boolean duplicado = celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .filter(c -> ignorarId == null || !c.getId().equals(ignorarId))
                .anyMatch(c -> TextoUtil.normalizarParaComparacao(c.getNome()).equals(normalizado));
        if (duplicado) {
            throw new BusinessException("CELULA_DUPLICADA",
                    "Já existe uma célula com esse nome.");
        }
    }
}
