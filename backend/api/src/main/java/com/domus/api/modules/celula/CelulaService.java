package com.domus.api.modules.celula;

import com.domus.api.modules.celula.DTOs.*;
import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
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
import java.util.Objects;
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
    private final FotoService fotoService;
    private final OutboxRegistrador outboxRegistrador;
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;

    @Transactional(readOnly = true)
    public List<CelulaResponse> listar(UUID igrejaId, UUID pessoaLogadaId) {
        return celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .map(c -> CelulaResponse.comResumo(c, membrosAtivosDe(c.getId()), pessoaLogadaId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CelulaResponse> listarArquivadas(UUID igrejaId) {
        return celulaRepository.findArquivadasPorIgreja(igrejaId).stream()
                .map(c -> CelulaResponse.comResumo(c, membrosAtivosDe(c.getId()), null))
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        int linhas = celulaRepository.restaurarPorId(id, igrejaId);
        if (linhas == 0) {
            throw new ResourceNotFoundException("Célula não encontrada.");
        }
        outboxRegistrador.registrar(TipoEntidadeOutbox.CELULA, TipoEventoOutbox.ATUALIZADO, id, igrejaId);
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
        Foto foto = fotoService.buscarParaVincular(data.fotoId(), igrejaId);

        Celula celula = Celula.builder()
                .igreja(igreja).nome(nome)
                .diaSemana(data.diaSemana())
                .horario(data.horario() != null && !data.horario().isBlank()
                        ? LocalTime.parse(data.horario()) : null)
                .foto(foto)
                .criadoPor(usuario).atualizadoPor(usuario)
                .build();

        Celula salva = celulaRepository.save(celula);
        outboxRegistrador.registrar(TipoEntidadeOutbox.CELULA, TipoEventoOutbox.CRIADO, salva.getId(), igrejaId);
        return CelulaResponse.from(salva);
    }

    @Transactional
    public CelulaResponse atualizar(UUID id, CelulaRequest data, UUID igrejaId,
                                     UUID usuarioId, UUID atorPessoaId, boolean isAdmin) {
        Celula celula = buscarDaIgrejaOuFalhar(id, igrejaId);
        exigirAdminOuLider(id, atorPessoaId, isAdmin);

        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, id);

        DiaSemana diaSemanaAntigo = celula.getDiaSemana();
        LocalTime horarioAntigo = celula.getHorario();

        celula.setNome(nome);
        celula.setDiaSemana(data.diaSemana());
        celula.setHorario(data.horario() != null && !data.horario().isBlank()
                ? LocalTime.parse(data.horario()) : null);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(celula::setAtualizadoPor);
        }

        Foto fotoAntiga = celula.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(data.fotoId(), igrejaId);
        celula.setFoto(fotoNova);

        CelulaResponse response = CelulaResponse.from(celulaRepository.save(celula));
        outboxRegistrador.registrar(TipoEntidadeOutbox.CELULA, TipoEventoOutbox.ATUALIZADO, celula.getId(), igrejaId);

        boolean diaOuHorarioMudou = !Objects.equals(diaSemanaAntigo, celula.getDiaSemana())
                || !Objects.equals(horarioAntigo, celula.getHorario());
        if (diaOuHorarioMudou) {
            notificarCelulaAlterada(celula, igrejaId, atorPessoaId);
        }

        if (!Objects.equals(fotoAntiga != null ? fotoAntiga.getId() : null,
                fotoNova != null ? fotoNova.getId() : null) && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }

        return response;
    }

    /** Só a foto — salva assim que o recorte é confirmado, sem esperar o resto do "Salvar". */
    @Transactional
    public void atualizarFoto(UUID id, UUID igrejaId, UUID usuarioId, UUID atorPessoaId, boolean isAdmin, UUID fotoId) {
        Celula celula = buscarDaIgrejaOuFalhar(id, igrejaId);
        exigirAdminOuLider(id, atorPessoaId, isAdmin);

        Foto fotoAntiga = celula.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(fotoId, igrejaId);
        celula.setFoto(fotoNova);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(celula::setAtualizadoPor);
        }
        celulaRepository.save(celula);

        if (!Objects.equals(fotoAntiga != null ? fotoAntiga.getId() : null,
                fotoNova != null ? fotoNova.getId() : null) && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }
    }

    /** Notifica todo mundo que está na célula (exceto quem fez a mudança) quando dia/horário muda. */
    private void notificarCelulaAlterada(Celula celula, UUID igrejaId, UUID pessoaIdAtor) {
        List<CelulaMembro> membros = membroRepository.findByCelulaIdAndPessoaIdIsNotNull(celula.getId());
        for (CelulaMembro membro : membros) {
            UUID pessoaIdMembro = membro.getPessoa().getId();
            if (pessoaIdMembro.equals(pessoaIdAtor)) continue;
            usuarioRepository.findByPessoaId(pessoaIdMembro).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.CELULA_ALTERADA,
                            igrejaId, usuario.getId(),
                            "O dia ou horário da " + TextoUtil.prefixarSemDuplicar("célula", celula.getNome()) + " mudou.",
                            "/celulas/" + celula.getId()));
        }
    }

    @Transactional
    public void excluir(UUID id, UUID igrejaId) {
        Celula celula = buscarDaIgrejaOuFalhar(id, igrejaId);
        celulaRepository.delete(celula);
        outboxRegistrador.registrar(TipoEntidadeOutbox.CELULA, TipoEventoOutbox.REMOVIDO, id, igrejaId);
    }

    @Transactional
    public void excluirDefinitivo(UUID id, UUID igrejaId) {
        // findByIdAndIgrejaIdIncluindoArquivadas (não buscarDaIgrejaOuFalhar) porque esse
        // endpoint precisa achar também uma célula já arquivada — é o caminho principal
        // chamado a partir da tela de Arquivados.
        celulaRepository.findByIdAndIgrejaIdIncluindoArquivadas(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Célula não encontrada."));

        // Diferente de Evento/Categoria, vínculo de célula não é histórico — ter membro não bloqueia a exclusão.
        List<CelulaMembro> membros = membroRepository.findByCelulaIdOrderByPapelAsc(id);
        List<UUID> visitantesAfetados = membros.stream()
                .filter(m -> m.getVisitante() != null)
                .map(m -> m.getVisitante().getId())
                .toList();
        membroRepository.deleteAll(membros);
        // hardDeleteById é SQL nativo e não dispara auto-flush; sem flush() acharia membro que já devia ter sumido.
        membroRepository.flush();

        celulaRepository.hardDeleteById(id);
        outboxRegistrador.registrar(TipoEntidadeOutbox.CELULA, TipoEventoOutbox.REMOVIDO, id, igrejaId);
        // VisitanteDocument.celulaId precisa voltar a null pra quem estava nessa célula.
        visitantesAfetados.forEach(visitanteId -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.VISITANTE, TipoEventoOutbox.ATUALIZADO, visitanteId, igrejaId));
    }

    @Transactional(readOnly = true)
    public CelulaDetalheResponse detalhe(UUID celulaId, UUID igrejaId, UUID pessoaLogadaId) {
        // Precisa enxergar arquivada também — dá pra abrir o detalhe de uma célula
        // arquivada a partir da tela de Arquivados, igual uma célula ativa.
        Celula celula = celulaRepository.findByIdAndIgrejaIdIncluindoArquivadas(celulaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Célula não encontrada."));
        boolean souLider = pessoaLogadaId != null && ehLiderDaCelula(celulaId, pessoaLogadaId);

        List<CelulaMembro> todos = membroRepository.findByCelulaIdOrderByPapelAsc(celulaId);
        List<MembroCelulaResponse> membros = todos.stream()
                .map(MembroCelulaResponse::from).toList();

        return CelulaDetalheResponse.from(celula, membros, souLider);
    }

    public boolean ehLiderDaCelula(UUID celulaId, UUID pessoaId) {
        return membroRepository.existsByCelulaIdAndPessoaIdAndPapel(celulaId, pessoaId, PapelCelula.LIDER.name());
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
            Pessoa pessoa = adicionarPessoa(celula, data.pessoaId(), igrejaId, usuarioId);
            notificarEntradaNaCelula(celula, igrejaId, pessoa.getNome(), pessoa.getId(), atorPessoaId);
            notificarEntranteNaCelula(celula, igrejaId, pessoa.getId(), atorPessoaId,
                    com.domus.api.modules.notificacao.TipoNotificacao.ADICIONADO_CELULA,
                    "Você foi adicionado à " + TextoUtil.prefixarSemDuplicar("célula", celula.getNome()) + ".");
        } else if (data.visitanteId() != null) {
            adicionarVisitante(celula, data.visitanteId(), igrejaId, usuarioId);

            Visitante v = visitanteRepository.findByIdAndIgrejaId(data.visitanteId(), igrejaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));
            if (v.getEntrouEmCelulaEm() == null) {
                v.setEntrouEmCelulaEm(LocalDateTime.now());
                visitanteRepository.save(v);
            }
            notificarEntradaNaCelula(celula, igrejaId, v.getNome(), null, atorPessoaId);
        } else {
            throw new BusinessException("MEMBRO_INVALIDO",
                    "Informe pessoaId ou visitanteId.");
        }
    }

    /** Notifica todo mundo que já está na célula, exceto quem acabou de entrar e quem fez a ação. */
    private void notificarEntradaNaCelula(Celula celula, UUID igrejaId, String nomeEntrante,
                                           UUID pessoaIdEntranteOuNull, UUID pessoaIdAtor) {
        List<CelulaMembro> membros = membroRepository.findByCelulaIdAndPessoaIdIsNotNull(celula.getId());
        for (CelulaMembro membro : membros) {
            UUID pessoaIdMembro = membro.getPessoa().getId();
            if (pessoaIdMembro.equals(pessoaIdEntranteOuNull) || pessoaIdMembro.equals(pessoaIdAtor)) continue;
            usuarioRepository.findByPessoaId(pessoaIdMembro).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.ENTRADA_CELULA,
                            igrejaId, usuario.getId(),
                            nomeEntrante + " entrou na " + TextoUtil.prefixarSemDuplicar("célula", celula.getNome()) + ".",
                            "/celulas/" + celula.getId()));
        }
    }

    /** Notifica a própria pessoa afetada (adicionada/removida) — nunca quando ela mesma agiu. */
    private void notificarEntranteNaCelula(Celula celula, UUID igrejaId, UUID pessoaId, UUID pessoaIdAtor,
                                            com.domus.api.modules.notificacao.TipoNotificacao tipo, String texto) {
        if (pessoaId.equals(pessoaIdAtor)) return;
        usuarioRepository.findByPessoaId(pessoaId)
                .ifPresent(usuario -> notificacaoService.criar(
                        tipo, igrejaId, usuario.getId(), texto, "/celulas/" + celula.getId()));
    }

    private Pessoa adicionarPessoa(Celula celula, UUID pessoaId, UUID igrejaId, UUID usuarioId) {
        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        CelulaMembro existente = membroRepository.findByPessoaId(pessoaId).orElse(null);
        if (existente != null) {
            existente.setCelula(celula);
            membroRepository.save(existente);
            return pessoa;
        }

        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;
        membroRepository.save(CelulaMembro.builder()
                .igreja(celula.getIgreja()).celula(celula).pessoa(pessoa)
                .criadoPor(usuario).atualizadoPor(usuario).build());
        return pessoa;
    }

    private void adicionarVisitante(Celula celula, UUID visitanteId, UUID igrejaId, UUID usuarioId) {
        Visitante visitante = visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));

        CelulaMembro existente = membroRepository.findByVisitanteId(visitanteId).orElse(null);
        if (existente != null) {
            existente.setCelula(celula);
            membroRepository.save(existente);
        } else {
            Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;
            membroRepository.save(CelulaMembro.builder()
                    .igreja(celula.getIgreja()).celula(celula).visitante(visitante)
                    .criadoPor(usuario).atualizadoPor(usuario).build());
        }

        // VisitanteDocument.celulaId muda — busca precisa reindexar.
        outboxRegistrador.registrar(TipoEntidadeOutbox.VISITANTE, TipoEventoOutbox.ATUALIZADO, visitanteId, igrejaId);
    }

    @Transactional
    public void removerMembro(UUID celulaId, UUID membroId, UUID igrejaId,
                               UUID atorPessoaId, boolean isAdmin) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        exigirAdminOuLider(celulaId, atorPessoaId, isAdmin);

        CelulaMembro membro = membroRepository.findById(membroId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        UUID visitanteId = membro.getVisitante() != null ? membro.getVisitante().getId() : null;
        // Visitante não tem login — só pessoa cadastrada pode ter usuário pra notificar.
        UUID pessoaId = membro.getPessoa() != null ? membro.getPessoa().getId() : null;
        membroRepository.delete(membro);

        if (pessoaId != null) {
            notificarEntranteNaCelula(celula, igrejaId, pessoaId, atorPessoaId,
                    com.domus.api.modules.notificacao.TipoNotificacao.REMOVIDO_CELULA,
                    "Você foi removido da " + TextoUtil.prefixarSemDuplicar("célula", celula.getNome()) + ".");
        }

        // VisitanteDocument.celulaId volta a null — busca precisa reindexar.
        if (visitanteId != null) {
            outboxRegistrador.registrar(TipoEntidadeOutbox.VISITANTE, TipoEventoOutbox.ATUALIZADO, visitanteId, igrejaId);
        }
    }

    @Transactional
    public void atualizarPapel(UUID celulaId, UUID membroId, AtualizarPapelCelulaRequest data,
                                UUID igrejaId, UUID atorPessoaId, boolean isAdmin, UUID usuarioIdAtor) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        exigirAdminOuLider(celulaId, atorPessoaId, isAdmin);

        CelulaMembro membro = membroRepository.findById(membroId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        if (membro.getVisitante() != null) {
            throw new BusinessException("VISITANTE_NAO_PODE_SER_LIDER",
                    "Um visitante não pode ser promovido a líder de célula.");
        }
        boolean vaiVirarLider = data.papel() == PapelCelula.LIDER && membro.getPapel() != PapelCelula.LIDER;
        membro.setPapel(data.papel());
        membroRepository.save(membro);

        if (vaiVirarLider) {
            usuarioRepository.findByPessoaId(membro.getPessoa().getId())
                    .filter(usuario -> !usuario.getId().equals(usuarioIdAtor))
                    .ifPresent(usuario ->
                            notificacaoService.criar(
                                    com.domus.api.modules.notificacao.TipoNotificacao.PROMOVIDO_LIDER_CELULA,
                                    igrejaId, usuario.getId(),
                                    "Você foi promovido a líder da " + TextoUtil.prefixarSemDuplicar("célula", celula.getNome()) + ".",
                                    "/celulas/" + celula.getId()));
        }
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

        outboxRegistrador.registrar(TipoEntidadeOutbox.PESSOA, TipoEventoOutbox.CRIADO, pessoa.getId(), igrejaId);
        outboxRegistrador.registrar(TipoEntidadeOutbox.VISITANTE, TipoEventoOutbox.ATUALIZADO, v.getId(), igrejaId);

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
