package com.domus.api.modules.contapagar;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.anexo.Anexo;
import com.domus.api.modules.anexo.AnexoService;
import com.domus.api.modules.anexo.dto.AnexoUploadResponse;
import com.domus.api.modules.contapagar.dto.*;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraService;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinte;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ConflitoNegocioException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.domus.api.modules.financeiro.categoria.TipoCategoria.SAIDA;

/** Service de contas a pagar — regras de negócio, transações e link com movimentacao_financeira. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContaAPagarService {

    private final ContaAPagarRepository repository;
    private final PagamentoContaRepository pagamentoRepository;
    private final CategoriaFinanceiraService categoriaFinanceiraService;
    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnexoService anexoService;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final MovimentacaoAutomaticaService movimentacaoAutomaticaService;
    private final OutboxRegistrador outboxRegistrador;
    private final CacheEvictor cacheEvictor;

    // ---- leitura ----

    @Transactional(readOnly = true)
    public Page<ContaResponse> listar(UUID igrejaId, StatusConta status, LocalDate vencimentoAte,
                                      String q, Pageable pageable) {
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        Page<ContaAPagar> page = repository.listar(igrejaId, status, vencimentoAte, termo, pageable);
        return page.map(ContaResponse::from);
    }

    @Transactional(readOnly = true)
    public ContaResponse buscar(UUID id, UUID igrejaId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));
        return ContaResponse.from(conta, carregarPagamentos(conta.getPagamentos()));
    }

    // ---- criação ----

    @Transactional
    public ContaResponse criar(ContaRequest req, UUID igrejaId, UUID usuarioId) {
        log.info("Criando conta a pagar. descricao={}, valor={}, vencimento={}, recorrente={}, criado_por={}, igreja_id={}",
                req.descricao(), req.valor(), req.vencimento(),
                req.recorrencia() != null, usuarioId, igrejaId);

        CategoriaFinanceira categoria = categoriaFinanceiraService.buscarEntidade(req.categoriaId(), igrejaId);
        validarCategoriaSaida(categoria);

        Usuario usuario = usuarioRepository.getReferenceById(usuarioId);

        ContaAPagar conta = ContaAPagar.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .categoria(categoria)
                .valor(req.valor())
                .vencimento(req.vencimento())
                .descricao(req.descricao())
                .competencia(req.competencia())
                .linhaDigitavel(req.linhaDigitavel())
                .documentoNumero(req.documentoNumero())
                .cnpjBeneficiario(req.cnpjBeneficiario())
                .observacoes(req.observacoes())
                .status(StatusConta.EM_ABERTO)
                .valorPago(BigDecimal.ZERO)
                .divergeDaSerie(false)
                .criadoPorUsuario(usuario)
                .criadoPorTexto(usuario.getPessoa().getNome())
                .build();

        resolverBeneficiario(req.beneficiario(), conta);

        if (req.anexoId() != null) {
            conta.setAnexo(anexoService.buscarParaVincular(req.anexoId(), igrejaId));
        }

        if (req.recorrencia() != null) {
            configurarRecorrencia(conta, req.recorrencia());
        }

        repository.save(conta);

        if (req.recorrencia() != null) {
            // Geradora aponta pra si mesma.
            conta.setSerie(conta);
            repository.save(conta);
        }

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.CRIADO,
                conta.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("contas", igrejaId);

        log.info("Conta a pagar criada. id={}, descricao={}, valor={}, vencimento={}, recorrente={}, igreja_id={}",
                conta.getId(), conta.getDescricao(), conta.getValor(), conta.getVencimento(),
                conta.getRecorrenciaFrequencia() != null, igrejaId);

        return ContaResponse.from(conta);
    }

    // ---- edição completa (PUT-style) ----

    @Transactional
    public ContaResponse editar(UUID id, ContaRequest req, EscopoEdicaoSerie escopo,
                                UUID igrejaId, UUID usuarioId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));

        Usuario usuario = usuarioRepository.getReferenceById(usuarioId);
        CategoriaFinanceira categoria = categoriaFinanceiraService.buscarEntidade(req.categoriaId(), igrejaId);
        validarCategoriaSaida(categoria);

        log.info("Editando conta. id={}, escopo={}, novo_valor={}, atualizado_por={}, igreja_id={}",
                id, escopo, req.valor(), usuarioId, igrejaId);

        if (escopo == EscopoEdicaoSerie.ESTA) {
            if (conta.getSerie() != null) {
                conta.setSerie(null);
                conta.setDivergeDaSerie(true);
            }
            aplicarEdicaoCompleta(conta, req, categoria, usuario);
            repository.save(conta);

        } else if (escopo == EscopoEdicaoSerie.ESTA_E_SEGUINTES) {
            List<ContaAPagar> afetadas = repository.findBySerieIdOrderByVencimentoAsc(conta.getSerie().getId())
                    .stream()
                    .filter(c -> !c.getId().equals(id) && !c.getVencimento().isBefore(conta.getVencimento()))
                    .toList();

            aplicarEdicaoCompleta(conta, req, categoria, usuario);
            repository.save(conta);

            for (ContaAPagar c : afetadas) {
                aplicarEdicaoCompleta(c, req, categoria, usuario);
                repository.save(c);
            }

        } else if (escopo == EscopoEdicaoSerie.SERIE) {
            if (conta.getRecorrenciaFrequencia() == null) {
                throw new ConflitoNegocioException("NAO_E_SERIE",
                        "Esta conta não é uma série recorrente.");
            }
            aplicarEdicaoCompleta(conta, req, categoria, usuario);
            if (req.recorrencia() != null) {
                configurarRecorrencia(conta, req.recorrencia());
            }
            repository.save(conta);
        }

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.ATUALIZADO,
                conta.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("contas", igrejaId);

        return ContaResponse.from(conta);
    }

    // ---- edição parcial (PATCH) ----

    @Transactional
    public ContaResponse editar(UUID id, ContaPatchRequest req, EscopoEdicaoSerie escopo,
                                UUID igrejaId, UUID usuarioId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));

        Usuario usuario = usuarioRepository.getReferenceById(usuarioId);

        log.info("Editando conta (parcial). id={}, escopo={}, atualizado_por={}, igreja_id={}",
                id, escopo, usuarioId, igrejaId);

        if (escopo == EscopoEdicaoSerie.ESTA) {
            if (conta.getSerie() != null) {
                conta.setSerie(null);
                conta.setDivergeDaSerie(true);
            }
            aplicarEdicaoParcial(conta, req, usuario);
            repository.save(conta);

        } else if (escopo == EscopoEdicaoSerie.ESTA_E_SEGUINTES) {
            List<ContaAPagar> serie = repository.findBySerieIdOrderByVencimentoAsc(conta.getSerie().getId());
            aplicarEdicaoParcial(conta, req, usuario);
            repository.save(conta);
            for (ContaAPagar c : serie) {
                if (!c.getId().equals(id) && !c.getVencimento().isBefore(conta.getVencimento())) {
                    aplicarEdicaoParcial(c, req, usuario);
                    repository.save(c);
                }
            }

        } else if (escopo == EscopoEdicaoSerie.SERIE) {
            if (conta.getRecorrenciaFrequencia() == null) {
                throw new ConflitoNegocioException("NAO_E_SERIE",
                        "Esta conta não é uma série recorrente.");
            }
            aplicarEdicaoParcial(conta, req, usuario);
            if (req.recorrencia() != null) {
                configurarRecorrencia(conta, req.recorrencia());
            }
            repository.save(conta);
        }

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.ATUALIZADO,
                conta.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("contas", igrejaId);

        return ContaResponse.from(conta);
    }

    // ---- edição completa (PUT) ----

    // ---- exclusão ----

    @Transactional
    public void excluir(UUID id, EscopoEdicaoSerie escopo, UUID igrejaId, UUID usuarioId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));

        log.info("Excluindo conta. id={}, escopo={}, igreja_id={}", id, escopo, igrejaId);

        List<ContaAPagar> contasParaExcluir = new ArrayList<>();

        if (escopo == EscopoEdicaoSerie.ESTA) {
            if (conta.getSerie() != null) {
                conta.setSerie(null);
                conta.setDivergeDaSerie(true);
                repository.save(conta);
            } else {
                contasParaExcluir.add(conta);
            }

        } else if (escopo == EscopoEdicaoSerie.ESTA_E_SEGUINTES) {
            List<ContaAPagar> serie = repository.findBySerieIdOrderByVencimentoAsc(conta.getSerie().getId());
            for (ContaAPagar c : serie) {
                if (!c.getVencimento().isBefore(conta.getVencimento())) {
                    contasParaExcluir.add(c);
                }
            }

        } else if (escopo == EscopoEdicaoSerie.SERIE) {
            if (conta.getRecorrenciaFrequencia() == null) {
                throw new ConflitoNegocioException("NAO_E_SERIE",
                        "Esta conta não é uma série recorrente.");
            }
            List<ContaAPagar> serie = repository.findBySerieIdOrderByVencimentoAsc(conta.getId());
            contasParaExcluir.addAll(serie);
        }

        // Registra estorno no financeiro para cada conta com pagamentos.
        for (ContaAPagar c : contasParaExcluir) {
            List<PagamentoConta> naoEstornados = c.getPagamentos().stream()
                    .filter(p -> !p.getEstornado()).toList();
            if (!naoEstornados.isEmpty()) {
                String nomeCat = c.getCategoria() != null ? c.getCategoria().getNome() : "Contas a pagar";
                movimentacaoAutomaticaService.registrarExclusaoConta(
                        igrejaId, nomeCat,
                        naoEstornados.stream().map(PagamentoConta::getValorLiquido).toList(),
                        naoEstornados.stream().map(p -> p.getConta().getBeneficiarioPessoa() != null
                                ? p.getConta().getBeneficiarioPessoa().getId() : null).toList(),
                        naoEstornados.stream().map(p -> p.getConta().getBeneficiarioTexto()).toList(),
                        c.getDescricao()
                );
            }
            repository.delete(c);
        }

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.REMOVIDO,
                id, igrejaId);
        cacheEvictor.evictPorIgreja("contas", igrejaId);
    }

    // ---- pagamento ----

    @Transactional
    public ContaResponse pagar(UUID id, PagamentoRequest req, UUID igrejaId, UUID usuarioId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));

        if (conta.getStatus() == StatusConta.PAGA) {
            throw new ConflitoNegocioException("CONTA_JA_PAGA",
                    "Esta conta já está com status PAGA.");
        }

        BigDecimal valorPago = req.valorPago();
        BigDecimal saldoDevedor = conta.getValor().subtract(conta.getValorPago());
        if (valorPago.compareTo(saldoDevedor) > 0) {
            throw new BusinessException("VALOR_EXCEDE_SALDO",
                    "Valor do pagamento excede o saldo devedor: " + saldoDevedor);
        }

        Anexo anexo = null;
        if (req.anexoId() != null) {
            anexo = anexoService.buscarParaVincular(req.anexoId(), igrejaId);
        }

        // Criar movimentação SAÍDA no financeiro.
        String descMov = "Conta a pagar: " + conta.getDescricao()
                + (conta.getBeneficiarioPessoa() != null
                    ? " (" + conta.getBeneficiarioPessoa().getNome() + ")"
                    : conta.getBeneficiarioTexto() != null ? " (" + conta.getBeneficiarioTexto() + ")" : "");

        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .categoria(conta.getCategoria())
                .tipo(TipoMovimentacao.SAIDA)
                .valor(req.valorLiquido())
                .dataMovimentacao(req.data())
                .descricao(descMov)
                .criadoPorTexto("Conta a pagar")
                .build();

        // Beneficiário como contribuinte da movimentação.
        if (conta.getBeneficiarioPessoa() != null) {
            mov.getContribuintes().add(MovimentacaoContribuinte.builder()
                    .movimentacao(mov)
                    .pessoa(conta.getBeneficiarioPessoa())
                    .valor(req.valorLiquido())
                    .build());
        } else if (conta.getBeneficiarioTexto() != null) {
            mov.getContribuintes().add(MovimentacaoContribuinte.builder()
                    .movimentacao(mov)
                    .nomeExterno(conta.getBeneficiarioTexto())
                    .valor(req.valorLiquido())
                    .build());
        }

        movimentacaoRepository.save(mov);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.CRIADO,
                mov.getId(), igrejaId);

        // Registrar pagamento.
        PagamentoConta pagamento = PagamentoConta.builder()
                .conta(conta)
                .movimentacao(mov)
                .valorPago(req.valorPago())
                .jurosAcrescimos(req.jurosAcrescimos())
                .desconto(req.desconto())
                .pagoEm(req.data())
                .forma(req.forma())
                .anexo(anexo)
                .estornado(false)
                .criadoPorUsuario(usuarioRepository.getReferenceById(usuarioId))
                .build();
        conta.getPagamentos().add(pagamento);
        pagamentoRepository.save(pagamento);

        // Atualizar conta.
        BigDecimal novoPago = conta.getValorPago().add(valorPago);
        conta.setValorPago(novoPago);
        if (novoPago.compareTo(conta.getValor()) >= 0) {
            conta.setStatus(StatusConta.PAGA);
            conta.setPagoEm(req.data());
        }

        repository.save(conta);
        cacheEvictor.evictPorIgreja("contas", igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);

        log.info("Pagamento registrado. conta_id={}, valor={}, novo_valor_pago={}, status={}, igreja_id={}",
                id, valorPago, novoPago, conta.getStatus(), igrejaId);

        return ContaResponse.from(conta, carregarPagamentos(conta.getPagamentos()));
    }

    // ---- estorno ----

    @Transactional
    public PagamentoResponse estornar(UUID pagamentoId, UUID igrejaId, UUID usuarioId) {
        PagamentoConta pagamento = pagamentoRepository.findByIdAndIgrejaId(pagamentoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

        if (pagamento.getEstornado()) {
            throw new ConflitoNegocioException("PAGAMENTO_JA_ESTORNADO",
                    "Este pagamento já foi estornado.");
        }

        ContaAPagar conta = pagamento.getConta();
        if (conta.getStatus() == StatusConta.PAGA) {
            // Reabre a conta.
            conta.setStatus(StatusConta.EM_ABERTO);
            conta.setPagoEm(null);
        }

        // Atualizar valor pago acumulado.
        BigDecimal acumulado = conta.getPagamentos().stream()
                .filter(p -> !p.getId().equals(pagamentoId) && !p.getEstornado())
                .map(PagamentoConta::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        conta.setValorPago(acumulado);
        repository.save(conta);

        // Estornar movimentação vinculada.
        pagamento.setEstornado(true);
        pagamento.setEstornadoEm(java.time.LocalDateTime.now());
        pagamento.setEstornadoPorUsuario(usuarioRepository.getReferenceById(usuarioId));
        pagamentoRepository.save(pagamento);

        log.info("Pagamento estornado. pagamento_id={}, conta_id={}, novo_valor_pago={}, igreja_id={}",
                pagamentoId, conta.getId(), acumulado, igrejaId);

        cacheEvictor.evictPorIgreja("contas", igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);

        return PagamentoResponse.from(pagamento);
    }

    // ---- dar baixa restante ----
    @Transactional
    public ContaResponse darBaixaRestante(UUID id, UUID igrejaId, UUID usuarioId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));

        if (conta.getStatus() == StatusConta.PAGA) {
            throw new ConflitoNegocioException("CONTA_JA_PAGA",
                    "Esta conta já está com status PAGA.");
        }

        conta.setStatus(StatusConta.PAGA);
        conta.setPagoEm(LocalDate.now());

        String obs = conta.getObservacoes();
        if (obs == null || obs.isBlank()) {
            conta.setObservacoes("[Baixa por abatimento]");
        } else if (!obs.contains("[Baixa por abatimento]")) {
            conta.setObservacoes(obs + " [Baixa por abatimento]");
        }

        repository.save(conta);
        log.info("Dar baixa no restante. conta_id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("contas", igrejaId);

        return ContaResponse.from(conta, carregarPagamentos(conta.getPagamentos()));
    }

    // ---- recorrência (job) ----

    /** Gera ocorrências futuras para todas as séries ativas.
     *  Chamado pelo {@link ContaAPagarMaterializacaoJob}. */
    @Transactional
    public void materializarRecorrencias() {
        List<ContaAPagar> geradoras = repository.buscarGeradorasAtivas();
        int total = 0;

        for (ContaAPagar geradora : geradoras) {
            total += materializarSerie(geradora);
        }
        log.info("Materialização de contas recorrentes concluída. series={}, ocorrencias_criadas={}",
                geradoras.size(), total);
    }

    private int materializarSerie(ContaAPagar geradora) {
        LocalDate limite = LocalDate.now().plusMonths(3);

        List<LocalDate> proximosVencimentos = RecorrenciaCalculator.proximasDatas(
                geradora.getRecorrenciaFrequencia(),
                geradora.getVencimento(),
                limite,
                geradora.getRecorrenciaVezes(),
                geradora.getRecorrenciaAte(),
                geradora.getRecorrenciaDiaAncora()
        );

        int criadas = 0;
        for (LocalDate vencimento : proximosVencimentos) {
            if (repository.existsBySerieIdAndVencimentoAndDeletedAtIsNull(geradora.getId(), vencimento)) {
                continue;
            }
            ContaAPagar ocorrencia = clonar(geradora, vencimento);
            repository.save(ocorrencia);
            criadas++;
        }
        return criadas;
    }

    private ContaAPagar clonar(ContaAPagar modelo, LocalDate novoVencimento) {
        return ContaAPagar.builder()
                .igreja(modelo.getIgreja())
                .categoria(modelo.getCategoria())
                .beneficiarioPessoa(modelo.getBeneficiarioPessoa())
                .beneficiarioTexto(modelo.getBeneficiarioTexto())
                .descricao(modelo.getDescricao())
                .competencia(null)
                .linhaDigitavel(null)
                .documentoNumero(null)
                .cnpjBeneficiario(modelo.getCnpjBeneficiario())
                .valor(modelo.getValor())
                .vencimento(novoVencimento)
                .status(StatusConta.EM_ABERTO)
                .valorPago(BigDecimal.ZERO)
                .serie(modelo)
                .divergeDaSerie(false)
                .recorrenciaFrequencia(null)
                .recorrenciaAte(null)
                .recorrenciaVezes(null)
                .recorrenciaDiaAncora(null)
                .observacoes(modelo.getObservacoes())
                .criadoPorTexto(modelo.getCriadoPorTexto())
                .build();
    }

    // ---- projeção ----

    @Transactional(readOnly = true)
    public List<ProjecaoItem> projetarSerie(UUID contaId, UUID igrejaId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(contaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));

        if (conta.getRecorrenciaFrequencia() == null) {
            throw new ConflitoNegocioException("NAO_E_SERIE",
                    "Esta conta não é uma série recorrente.");
        }

        List<ProjecaoItem> itens = new ArrayList<>(repository
                .findBySerieIdOrderByVencimentoAsc(conta.getSerie().getId())
                .stream()
                .map(ProjecaoItem::from)
                .collect(Collectors.toList()));

        LocalDate limite = LocalDate.now().plusMonths(3);
        List<LocalDate> proximasDatas = RecorrenciaCalculator.proximasDatas(
                conta.getRecorrenciaFrequencia(),
                conta.getVencimento(),
                limite,
                conta.getRecorrenciaVezes(),
                conta.getRecorrenciaAte(),
                conta.getRecorrenciaDiaAncora()
        );

        for (LocalDate d : proximasDatas) {
            if (!itens.stream().anyMatch(i -> i.vencimento().equals(d))) {
                itens.add(ProjecaoItem.prevista(d, conta.getValor()));
            }
        }

        return itens.stream()
                .sorted((a, b) -> a.vencimento().compareTo(b.vencimento()))
                .toList();
    }

    // ---- resumo KPIs ----

    @Transactional(readOnly = true)
    public ResumoResponse resumo(UUID igrejaId, Integer mes, Integer ano) {
        if (mes == null) mes = LocalDate.now().getMonthValue();
        if (ano == null) ano = LocalDate.now().getYear();

        YearMonth ym = YearMonth.of(ano, mes);
        BigDecimal venceHoje = repository.sumVenceHoje(igrejaId);
        BigDecimal aVencer = repository.sumAVencerNoMes(igrejaId, ym.atDay(1), ym.atEndOfMonth());
        BigDecimal atrasadas = repository.sumAtrasadas(igrejaId);
        BigDecimal pagas = repository.sumPagasNoMes(igrejaId, ym.atDay(1), ym.atEndOfMonth());

        return new ResumoResponse(
                venceHoje != null ? venceHoje : BigDecimal.ZERO,
                aVencer != null ? aVencer : BigDecimal.ZERO,
                atrasadas != null ? atrasadas : BigDecimal.ZERO,
                pagas != null ? pagas : BigDecimal.ZERO
        );
    }

    // ---- LGPD ----

    @Transactional
    public void desvincularBeneficiario(UUID pessoaId) {
        repository.desvincularBeneficiario(pessoaId);
        log.info("Beneficiário desvinculado de contas a pagar. pessoa_id={}", pessoaId);
    }

    // ---- anexos ----

    /**
     * Vincula um novo anexo à conta (upload + link).
     */
    @Transactional
    public AnexoUploadResponse anexarAnexo(UUID contaId, MultipartFile arquivo, UUID igrejaId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(contaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));
        AnexoUploadResponse uploaded = anexoService.upload(arquivo, igrejaId);
        conta.setAnexo(anexoService.buscarParaVincular(uploaded.id(), igrejaId));
        repository.save(conta);
        return uploaded;
    }

    /**
     * Devolve o conteúdo binário de um anexo da conta.
     */
    @Transactional(readOnly = true)
    public byte[] lerAnexo(UUID anexoId, UUID contaId, UUID igrejaId) {
        // Valida que a conta existe e pertence à igreja.
        repository.findByIdAndIgrejaId(contaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));
        return anexoService.ler(anexoId, igrejaId);
    }

    /**
     * Devolve os metadados de um anexo para o response headers.
     */
    @Transactional(readOnly = true)
    public AnexoUploadResponse buscarAnexoMeta(UUID anexoId, UUID contaId, UUID igrejaId) {
        repository.findByIdAndIgrejaId(contaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));
        Anexo anexo = anexoService.buscarParaVincular(anexoId, igrejaId);
        return AnexoUploadResponse.from(anexo);
    }

    /**
     * Remove o anexo da conta e do storage.
     */
    @Transactional
    public void removerAnexo(UUID anexoId, UUID contaId, UUID igrejaId) {
        ContaAPagar conta = repository.findByIdAndIgrejaId(contaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada."));
        if (conta.getAnexo() == null || !conta.getAnexo().getId().equals(anexoId)) {
            throw new ResourceNotFoundException("Anexo não encontrado nesta conta.");
        }
        conta.setAnexo(null);
        repository.save(conta);
        anexoService.remover(anexoId);
    }

    // ---- helpers ----

    private void validarCategoriaSaida(CategoriaFinanceira categoria) {
        if (categoria.getTipo() == com.domus.api.modules.financeiro.categoria.TipoCategoria.ENTRADA) {
            throw new BusinessException("CATEGORIA_INVALIDA",
                    "Categoria para conta a pagar deve ser de saída ou ambos.");
        }
    }

    private void resolverBeneficiario(BeneficiarioDTO benef, ContaAPagar conta) {
        if (benef == null) {
            throw new BusinessException("BENEFICIARIO_OBRIGATORIO",
                    "Beneficiário é obrigatório.");
        }
        if (benef.pessoaId() != null) {
            UUID igrejaId = conta.getIgreja().getId();
            Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(benef.pessoaId(), igrejaId)
                    .orElseThrow(() -> new BusinessException("PESSOA_NAO_ENCONTRADA",
                            "Pessoa do beneficiário não encontrada."));
            conta.setBeneficiarioPessoa(pessoa);
            conta.setBeneficiarioTexto(null);
        } else if (benef.texto() != null && !benef.texto().isBlank()) {
            conta.setBeneficiarioTexto(benef.texto());
            conta.setBeneficiarioPessoa(null);
        } else {
            throw new BusinessException("BENEFICIARIO_INVALIDO",
                    "Beneficiário: preencha pessoa ou texto.");
        }
    }

    private void configurarRecorrencia(ContaAPagar conta, RecorrenciaDTO rec) {
        conta.setRecorrenciaFrequencia(rec.frequencia());
        conta.setRecorrenciaAte(rec.ate());
        conta.setRecorrenciaVezes(rec.vezes());
        conta.setRecorrenciaDiaAncora(rec.diaAncora());
    }

    private void aplicarEdicaoCompleta(ContaAPagar conta, ContaRequest req,
                                CategoriaFinanceira categoria, Usuario usuario) {
        conta.setCategoria(categoria);
        conta.setValor(req.valor());
        conta.setVencimento(req.vencimento());
        conta.setDescricao(req.descricao());
        conta.setCompetencia(req.competencia());
        conta.setLinhaDigitavel(req.linhaDigitavel());
        conta.setDocumentoNumero(req.documentoNumero());
        conta.setCnpjBeneficiario(req.cnpjBeneficiario());
        conta.setObservacoes(req.observacoes());

        resolverBeneficiario(req.beneficiario(), conta);

        if (req.anexoId() != null) {
            conta.setAnexo(anexoService.buscarParaVincular(req.anexoId(), conta.getIgreja().getId()));
        }
    }

    private void aplicarEdicaoParcial(ContaAPagar conta, ContaPatchRequest req, Usuario usuario) {
        if (req.categoriaId() != null) {
            CategoriaFinanceira categoria = categoriaFinanceiraService.buscarEntidade(req.categoriaId(),
                    conta.getIgreja().getId());
            validarCategoriaSaida(categoria);
            conta.setCategoria(categoria);
        }
        if (req.valor() != null) conta.setValor(req.valor());
        if (req.vencimento() != null) conta.setVencimento(req.vencimento());
        if (req.descricao() != null) conta.setDescricao(req.descricao());
        if (req.competencia() != null) conta.setCompetencia(req.competencia());
        if (req.linhaDigitavel() != null) conta.setLinhaDigitavel(req.linhaDigitavel());
        if (req.documentoNumero() != null) conta.setDocumentoNumero(req.documentoNumero());
        if (req.cnpjBeneficiario() != null) conta.setCnpjBeneficiario(req.cnpjBeneficiario());
        if (req.observacoes() != null) conta.setObservacoes(req.observacoes());
        if (req.beneficiario() != null) {
            resolverBeneficiario(req.beneficiario(), conta);
        }
        if (req.anexoId() != null) {
            conta.setAnexo(anexoService.buscarParaVincular(req.anexoId(), conta.getIgreja().getId()));
        }
    }

    private List<PagamentoResponse> carregarPagamentos(List<PagamentoConta> pagamentos) {
        return pagamentos.stream()
                .map(PagamentoResponse::from)
                .sorted((a, b) -> b.data().compareTo(a.data()))
                .collect(Collectors.toList());
    }
}
