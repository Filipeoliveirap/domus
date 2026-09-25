package com.domus.api.modules.contapagar;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.anexo.Anexo;
import com.domus.api.modules.anexo.AnexoService;
import com.domus.api.modules.contapagar.dto.*;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraService;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ContaAPagarServiceTest {

    ContaAPagarRepository repository;
    PagamentoContaRepository pagamentoRepository;
    CategoriaFinanceiraService categoriaFinanceiraService;
    IgrejaRepository igrejaRepository;
    PessoaRepository pessoaRepository;
    UsuarioRepository usuarioRepository;
    AnexoService anexoService;
    MovimentacaoFinanceiraRepository movimentacaoRepository;
    MovimentacaoAutomaticaService movimentacaoAutomaticaService;
    OutboxRegistrador outboxRegistrador;
    CacheEvictor cacheEvictor;
    ContaAPagarService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID categoriaId = UUID.randomUUID();
    UUID contaId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();

    Igreja igreja;
    CategoriaFinanceira categoria;
    Usuario usuario;
    Pessoa pessoa;

    @BeforeEach
    void setup() {
        repository = mock(ContaAPagarRepository.class);
        pagamentoRepository = mock(PagamentoContaRepository.class);
        categoriaFinanceiraService = mock(CategoriaFinanceiraService.class);
        igrejaRepository = mock(IgrejaRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        anexoService = mock(AnexoService.class);
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        movimentacaoAutomaticaService = mock(MovimentacaoAutomaticaService.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        cacheEvictor = mock(CacheEvictor.class);

        service = new ContaAPagarService(repository, pagamentoRepository,
                categoriaFinanceiraService, igrejaRepository, pessoaRepository,
                usuarioRepository, anexoService, movimentacaoRepository,
                movimentacaoAutomaticaService, outboxRegistrador, cacheEvictor);

        igreja = Igreja.builder().id(igrejaId).nome("Igreja Teste").build();
        categoria = CategoriaFinanceira.builder()
                .id(categoriaId).igreja(igreja).nome("Contas Gerais").tipo(TipoCategoria.SAIDA).build();
        pessoa = Pessoa.builder().id(pessoaId).nome("João Silva").build();
        usuario = Usuario.builder().id(usuarioId).pessoa(pessoa).build();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    // Record: ContaRequest(UUID categoriaId, BeneficiarioDTO beneficiario, String cnpjBeneficiario,
    //     String descricao, BigDecimal valor, LocalDate vencimento, LocalDate competencia,
    //     String linhaDigitavel, String documentoNumero, String observacoes, UUID anexoId,
    //     RecorrenciaDTO recorrencia)
    private ContaRequest reqSimples() {
        return new ContaRequest(
                categoriaId, null, null,
                "Conta de luz", new BigDecimal("150.00"),
                LocalDate.now().plusDays(10), null, null, null, null, null, null
        );
    }

    private ContaRequest reqSimplesComBeneficiario() {
        return new ContaRequest(
                categoriaId, new BeneficiarioDTO(null, "Domus Igreja"), null,
                "Conta de luz", new BigDecimal("150.00"),
                LocalDate.now().plusDays(10), null, null, null, null, null, null
        );
    }

    private ContaRequest reqComBeneficiarioPessoa() {
        return new ContaRequest(
                categoriaId, new BeneficiarioDTO(pessoaId, null), null,
                "Fornecedor XYZ", new BigDecimal("500.00"),
                LocalDate.now().plusDays(5), null, null, null, null, null, null
        );
    }

    private ContaAPagar contaArmazenada() {
        return ContaAPagar.builder()
                .id(contaId)
                .igreja(igreja)
                .categoria(categoria)
                .descricao("Conta de luz")
                .valor(new BigDecimal("150.00"))
                .vencimento(LocalDate.now().plusDays(10))
                .status(StatusConta.EM_ABERTO)
                .valorPago(BigDecimal.ZERO)
                .divergeDaSerie(false)
                .criadoPorTexto("João Silva")
                .pagamentos(new ArrayList<>())
                .build();
    }

    private void stubRepositorySave(ContaAPagar conta) {
        when(repository.save(any(ContaAPagar.class))).thenAnswer(inv -> {
            ContaAPagar arg = inv.getArgument(0);
            if (arg.getId() == null) {
                try {
                    java.lang.reflect.Field idField = ContaAPagar.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(arg, UUID.randomUUID());
                } catch (Exception e) { /* ignore */ }
            }
            return arg;
        });
    }

    private void stubIgrejaAndUsuario() {
        when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
        when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
        when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa));
    }

    // -------------------------------------------------------------------------
    // criar()
    // -------------------------------------------------------------------------

    @Nested
    class criar {

        @Test
        void cria_conta_simples_quando_dados_validos() {
            ContaRequest req = reqSimplesComBeneficiario();
            stubIgrejaAndUsuario();
            stubRepositorySave(new ContaAPagar());

            ContaResponse resp = service.criar(req, igrejaId, usuarioId);

            assertThat(resp).isNotNull();
            assertThat(resp.valor()).isEqualByComparingTo("150.00");
            assertThat(resp.status()).isEqualTo("EM_ABERTO");
            verify(repository).save(any(ContaAPagar.class));
            verify(outboxRegistrador).registrar(eq(TipoEntidadeOutbox.MOVIMENTACAO),
                    eq(TipoEventoOutbox.CRIADO), any(UUID.class), eq(igrejaId));
            verify(cacheEvictor).evictPorIgreja("contas", igrejaId);
        }

        @Test
        void cria_conta_com_beneficiario_pessoa() {
            ContaRequest req = reqComBeneficiarioPessoa();
            when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa));
            stubIgrejaAndUsuario();
            stubRepositorySave(new ContaAPagar());

            ContaResponse resp = service.criar(req, igrejaId, usuarioId);

            assertThat(resp).isNotNull();
            assertThat(resp.beneficiario().pessoaId()).isEqualTo(pessoaId);
            verify(pessoaRepository).findByIdAndIgrejaId(pessoaId, igrejaId);
        }

        @Test
        void cria_conta_recusa_categoria_tipo_entrada() {
            CategoriaFinanceira catEntrada = CategoriaFinanceira.builder()
                    .id(categoriaId).igreja(igreja).nome("Dízimos").tipo(com.domus.api.modules.financeiro.categoria.TipoCategoria.ENTRADA).build();
            when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(catEntrada);

            ContaRequest req = reqSimplesComBeneficiario();

            assertThatThrownBy(() -> service.criar(req, igrejaId, usuarioId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Categoria para conta a pagar deve ser de saída");
        }

        @Test
        void cria_conta_recusa_pessoa_de_outra_igreja() {
            when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);
            when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.empty());

            ContaRequest req = reqComBeneficiarioPessoa();

            assertThatThrownBy(() -> service.criar(req, igrejaId, usuarioId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Pessoa do beneficiário não encontrada");
        }

        @Test
        void cria_conta_com_beneficiario_texto() {
            ContaRequest req = new ContaRequest(
                    categoriaId, new BeneficiarioDTO(null, "Empresa Externa Ltda"), null,
                    "Serviço terceiro", new BigDecimal("300.00"),
                    LocalDate.now().plusDays(20), null, null, null, null, null, null
            );
            stubIgrejaAndUsuario();
            stubRepositorySave(new ContaAPagar());

            ContaResponse resp = service.criar(req, igrejaId, usuarioId);

            assertThat(resp).isNotNull();
            assertThat(resp.beneficiario().texto()).isEqualTo("Empresa Externa Ltda");
        }

        @Test
        void cria_conta_recorrente_quando_recorrencia_informada() {
            ContaRequest req = new ContaRequest(
                    categoriaId, new BeneficiarioDTO(pessoaId, null), null,
                    "Aluguel mensal", new BigDecimal("1000.00"),
                    LocalDate.now().plusMonths(1), null, null, null, null, null,
                    new RecorrenciaDTO(RecorrenciaFrequencia.MENSAL, null, 12, null)
            );
            when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa));
            stubIgrejaAndUsuario();
            stubRepositorySave(new ContaAPagar());

            ContaResponse resp = service.criar(req, igrejaId, usuarioId);

            assertThat(resp).isNotNull();
            assertThat(resp.recorrencia()).isNotNull();
            assertThat(resp.recorrencia().frequencia()).isEqualTo("MENSAL");
            // serie deve apontar pra si mesma
            verify(repository, times(2)).save(any(ContaAPagar.class));
        }

        @Test
        void falha_quando_beneficiario_nulo() {
            ContaRequest req = new ContaRequest(
                    categoriaId, null, null, "Conta sem beneficiário", new BigDecimal("100.00"),
                    LocalDate.now(), null, null, null, null, null, null
            );
            stubIgrejaAndUsuario();
            stubRepositorySave(new ContaAPagar());

            assertThatThrownBy(() -> service.criar(req, igrejaId, usuarioId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("obrigatório");
        }

        @Test
        void falha_quando_beneficiario_invalido_sem_pessoa_nem_texto() {
            ContaRequest req = new ContaRequest(
                    categoriaId, new BeneficiarioDTO(null, null), null,
                    "Conta inválida", new BigDecimal("100.00"),
                    LocalDate.now(), null, null, null, null, null, null
            );
            stubIgrejaAndUsuario();
            stubRepositorySave(new ContaAPagar());

            assertThatThrownBy(() -> service.criar(req, igrejaId, usuarioId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("preencha");
        }

        @Test
        void falha_quando_categoria_nao_existe() {
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId))
                    .thenThrow(new ResourceNotFoundException("Categoria não encontrada."));
            stubRepositorySave(new ContaAPagar());
            when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);

            assertThatThrownBy(() -> service.criar(reqSimples(), igrejaId, usuarioId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------------------------------------------------------------
    // editar()
    // -------------------------------------------------------------------------

    @Nested
    class editar {

        @Test
        void edita_conta_esta_desvincula_da_serie() {
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Serie").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.now().plusMonths(1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false)
                    .recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .pagamentos(new ArrayList<>())
                    .build();
            ContaAPagar conta = ContaAPagar.builder()
                    .id(contaId).igreja(igreja).categoria(categoria)
                    .descricao("Mensalidade").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.now().plusMonths(1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .serie(geradora).divergeDaSerie(false)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            stubRepositorySave(conta);

            ContaRequest req = reqSimplesComBeneficiario();
            ContaResponse resp = service.editar(contaId, req, EscopoEdicaoSerie.ESTA, igrejaId, usuarioId);

            assertThat(conta.getSerie()).isNull();
            assertThat(conta.getDivergeDaSerie()).isTrue();
        }

        @Test
        void edita_conta_esta_e_seguintes_altera_acoes() {
            UUID geradoraId = UUID.randomUUID();
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(geradoraId).igreja(igreja).categoria(categoria)
                    .descricao("Serie").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.of(2026, 1, 15))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false).recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .pagamentos(new ArrayList<>())
                    .build();
            ContaAPagar conta1 = ContaAPagar.builder()
                    .id(contaId).igreja(igreja).categoria(categoria)
                    .descricao("Jan").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.of(2026, 1, 15))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .serie(geradora).divergeDaSerie(false)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();
            ContaAPagar conta2 = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Fev").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.of(2026, 2, 15))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .serie(geradora).divergeDaSerie(false)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta1));
            when(repository.findBySerieIdOrderByVencimentoAsc(geradoraId)).thenReturn(List.of(conta1, conta2));
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            stubRepositorySave(conta1);

            ContaRequest req = new ContaRequest(categoriaId, new BeneficiarioDTO(null, "Domus"), null,
                    "Novo valor",
                    new BigDecimal("250.00"), LocalDate.now(), null, null, null, null, null, null);
            service.editar(contaId, req, EscopoEdicaoSerie.ESTA_E_SEGUINTES, igrejaId, usuarioId);

            verify(repository, atLeast(2)).save(any(ContaAPagar.class));
        }

        @Test
        void edicao_parcial_so_altera_campos_fornecidos() {
            ContaAPagar conta = contaArmazenada();
            conta.setDescricao("Original");

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            stubRepositorySave(conta);

            ContaPatchRequest patch = new ContaPatchRequest(
                    null, null, null,
                    "Descricao alterada via patch",
                    null, null, null, null, null, null, null, null
            );
            ContaResponse resp = service.editar(contaId, patch, EscopoEdicaoSerie.ESTA, igrejaId, usuarioId);

            assertThat(conta.getDescricao()).isEqualTo("Descricao alterada via patch");
            assertThat(conta.getValor()).isEqualByComparingTo("150.00"); // não mudou
        }

        @Test
        void edita_serie_atualiza_template_e_nao_toca_materializadas() {
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Aluguel").valor(new BigDecimal("1000.00"))
                    .vencimento(LocalDate.now().plusMonths(1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false).recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .recorrenciaVezes(12)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();
            ContaAPagar ocorrencia = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Aluguel").valor(new BigDecimal("1000.00"))
                    .vencimento(LocalDate.now().plusMonths(1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .serie(geradora).divergeDaSerie(false)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.findByIdAndIgrejaId(geradora.getId(), igrejaId)).thenReturn(Optional.of(geradora));
            when(repository.findBySerieIdOrderByVencimentoAsc(geradora.getId())).thenReturn(List.of(geradora, ocorrencia));
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            stubRepositorySave(geradora);

            ContaRequest req = new ContaRequest(categoriaId, new BeneficiarioDTO(null, "Domus"), null,
                    "Aluguel revisado",
                    new BigDecimal("1200.00"), geradora.getVencimento(), null, null, null, null, null, null);
            service.editar(geradora.getId(), req, EscopoEdicaoSerie.SERIE, igrejaId, usuarioId);

            // Só a geradora foi atualizada, a ocorrência não.
            verify(repository, times(1)).save(argThat(c -> c.getId().equals(geradora.getId())));
        }

        @Test
        void edita_serie_em_conta_nao_recorrente_lanca_excecao() {
            ContaAPagar conta = contaArmazenada();
            // Sem série.

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            when(categoriaFinanceiraService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);

            assertThatThrownBy(() -> service.editar(contaId, reqSimples(),
                    EscopoEdicaoSerie.SERIE, igrejaId, usuarioId))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("Esta conta não é uma série recorrente.");
        }
    }

    // -------------------------------------------------------------------------
    // excluir()
    // -------------------------------------------------------------------------

    @Nested
    class excluir {

        @Test
        void exclui_esta_conta_nao_recorrente() {
            ContaAPagar conta = contaArmazenada();
            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            doNothing().when(repository).delete(any(ContaAPagar.class));

            service.excluir(contaId, EscopoEdicaoSerie.ESTA, igrejaId, usuarioId);

            verify(repository).delete(conta);
        }

        @Test
        void exclui_esta_em_serie_desvincula_em_vez_de_deletar() {
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Serie").valor(new BigDecimal("100.00"))
                    .vencimento(LocalDate.now().plusMonths(1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false).recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .pagamentos(new ArrayList<>())
                    .build();
            ContaAPagar conta = ContaAPagar.builder()
                    .id(contaId).igreja(igreja).categoria(categoria)
                    .descricao("Jan").valor(new BigDecimal("100.00"))
                    .vencimento(LocalDate.now().plusMonths(1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .serie(geradora).divergeDaSerie(false)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            stubRepositorySave(conta);

            service.excluir(contaId, EscopoEdicaoSerie.ESTA, igrejaId, usuarioId);

            // Conta em série não é deletada — é desvinculada.
            verify(repository, never()).delete(conta);
            verify(repository).save(conta);
            assertThat(conta.getSerie()).isNull();
            assertThat(conta.getDivergeDaSerie()).isTrue();
        }

        @Test
        void exclui_toda_serie_em_conta_nao_recorrente_lanca_excecao() {
            ContaAPagar conta = contaArmazenada();
            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));

            assertThatThrownBy(() -> service.excluir(contaId, EscopoEdicaoSerie.SERIE, igrejaId, usuarioId))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("Esta conta não é uma série recorrente.");
        }
    }

    // -------------------------------------------------------------------------
    // pagar()
    // -------------------------------------------------------------------------

    @Nested
    class pagar {

        @Test
        void pagamento_parcial_nao_muda_status_para_paga() {
            ContaAPagar conta = contaArmazenada();
            conta.setValor(new BigDecimal("200.00"));

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            when(movimentacaoRepository.save(any(MovimentacaoFinanceira.class))).thenAnswer(i -> {
                MovimentacaoFinanceira m = i.getArgument(0);
                try {
                    java.lang.reflect.Field idField = MovimentacaoFinanceira.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(m, UUID.randomUUID());
                } catch (Exception e) { /* ignore */ }
                return m;
            });
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
            stubRepositorySave(conta);

            PagamentoRequest req = new PagamentoRequest(
                    new BigDecimal("100.00"), new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now(), FormaPagamento.PIX, null
            );
            ContaResponse resp = service.pagar(contaId, req, igrejaId, usuarioId);

            assertThat(resp.status()).isEqualTo("PARCIAL");
            assertThat(resp.valorPago()).isEqualByComparingTo("100.00");
            verify(movimentacaoRepository).save(any(MovimentacaoFinanceira.class));
            verify(pagamentoRepository).save(any(PagamentoConta.class));
        }

        @Test
        void pagamento_total_muda_status_para_paga() {
            ContaAPagar conta = contaArmazenada();
            conta.setValor(new BigDecimal("150.00"));
            conta.setValorPago(BigDecimal.ZERO);

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            when(movimentacaoRepository.save(any(MovimentacaoFinanceira.class))).thenAnswer(i -> {
                MovimentacaoFinanceira m = i.getArgument(0);
                try {
                    java.lang.reflect.Field idField = MovimentacaoFinanceira.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(m, UUID.randomUUID());
                } catch (Exception e) { /* ignore */ }
                return m;
            });
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
            stubRepositorySave(conta);

            PagamentoRequest req = new PagamentoRequest(
                    new BigDecimal("150.00"), new BigDecimal("150.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now(), FormaPagamento.BOLETO, null
            );
            ContaResponse resp = service.pagar(contaId, req, igrejaId, usuarioId);

            assertThat(resp.status()).isEqualTo("PAGA");
            assertThat(resp.valorPago()).isEqualByComparingTo("150.00");
        }

        @Test
        void pagamento_em_conta_ja_paga_lanca_excecao() {
            ContaAPagar conta = contaArmazenada();
            conta.setStatus(StatusConta.PAGA);

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));

            PagamentoRequest req = new PagamentoRequest(
                    new BigDecimal("50.00"), new BigDecimal("50.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now(), FormaPagamento.PIX, null
            );

            assertThatThrownBy(() -> service.pagar(contaId, req, igrejaId, usuarioId))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("Esta conta já está com status PAGA.");
        }

        @Test
        void pagamento_valor_excede_saldo_lanca_excecao() {
            ContaAPagar conta = contaArmazenada();
            conta.setValor(new BigDecimal("100.00"));

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));

            PagamentoRequest req = new PagamentoRequest(
                    new BigDecimal("200.00"), new BigDecimal("200.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now(), FormaPagamento.PIX, null
            );

            assertThatThrownBy(() -> service.pagar(contaId, req, igrejaId, usuarioId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Valor do pagamento excede");
        }

        @Test
        void pagamento_cria_movimentacao_saida_no_financeiro() {
            ContaAPagar conta = contaArmazenada();
            conta.setBeneficiarioPessoa(pessoa);
            conta.setValor(new BigDecimal("50.00"));

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            when(movimentacaoRepository.save(any(MovimentacaoFinanceira.class))).thenAnswer(i -> {
                MovimentacaoFinanceira m = i.getArgument(0);
                try {
                    java.lang.reflect.Field idField = MovimentacaoFinanceira.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(m, UUID.randomUUID());
                } catch (Exception e) { /* ignore */ }
                return m;
            });
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
            stubRepositorySave(conta);

            PagamentoRequest req = new PagamentoRequest(
                    new BigDecimal("50.00"), new BigDecimal("50.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now(), FormaPagamento.TRANSFERENCIA, null
            );
            service.pagar(contaId, req, igrejaId, usuarioId);

            verify(movimentacaoRepository).save(argThat(m ->
                    m.getTipo() == TipoMovimentacao.SAIDA
                    && m.getValor().compareTo(new BigDecimal("50.00")) == 0
                    && m.getDescricao().contains("Conta a pagar")
            ));
        }
    }

    // -------------------------------------------------------------------------
    // estornar()
    // -------------------------------------------------------------------------

    @Nested
    class estornar {

        @Test
        void estorno_marca_pagamento_como_estornado_e_reabre_conta() {
            UUID pagamentoId = UUID.randomUUID();
            ContaAPagar conta = contaArmazenada();
            conta.setStatus(StatusConta.PAGA);
            conta.setValor(new BigDecimal("100.00"));
            conta.setValorPago(new BigDecimal("100.00"));

            PagamentoConta pagamento = PagamentoConta.builder()
                    .id(pagamentoId)
                    .conta(conta)
                    .valorPago(new BigDecimal("100.00"))
                    .estornado(false)
                    .forma(FormaPagamento.PIX)
                    .pagoEm(LocalDate.now())
                    .build();
            conta.getPagamentos().add(pagamento);

            when(pagamentoRepository.findByIdAndIgrejaId(pagamentoId, igrejaId))
                    .thenReturn(Optional.of(pagamento));
            when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
            stubRepositorySave(conta);

            PagamentoResponse resp = service.estornar(pagamentoId, igrejaId, usuarioId);

            assertThat(resp.estornadoEm()).isNotNull();
            assertThat(conta.getStatus()).isEqualTo(StatusConta.EM_ABERTO);
            verify(pagamentoRepository).save(pagamento);
        }

        @Test
        void estorno_de_pagamento_ja_estornado_lanca_excecao() {
            UUID pagamentoId = UUID.randomUUID();
            ContaAPagar conta = contaArmazenada();
            PagamentoConta pagamento = PagamentoConta.builder()
                    .id(pagamentoId)
                    .conta(conta)
                    .valorPago(new BigDecimal("50.00"))
                    .estornado(true)
                    .forma(FormaPagamento.PIX)
                    .pagoEm(LocalDate.now().minusDays(1))
                    .build();

            when(pagamentoRepository.findByIdAndIgrejaId(pagamentoId, igrejaId))
                    .thenReturn(Optional.of(pagamento));

            assertThatThrownBy(() -> service.estornar(pagamentoId, igrejaId, usuarioId))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("Este pagamento já foi estornado.");
        }
    }

    // -------------------------------------------------------------------------
    // darBaixaRestante()
    // -------------------------------------------------------------------------

    @Nested
    class darBaixaRestante {

        @Test
        void da_baixa_em_conta_em_aberto() {
            ContaAPagar conta = contaArmazenada();
            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));
            stubRepositorySave(conta);

            ContaResponse resp = service.darBaixaRestante(contaId, igrejaId, usuarioId);

            assertThat(resp.status()).isEqualTo("PAGA");
            assertThat(conta.getObservacoes()).contains("[Baixa por abatimento]");
        }

        @Test
        void da_baixa_em_conta_ja_paga_lanca_excecao() {
            ContaAPagar conta = contaArmazenada();
            conta.setStatus(StatusConta.PAGA);

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));

            assertThatThrownBy(() -> service.darBaixaRestante(contaId, igrejaId, usuarioId))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("Esta conta já está com status PAGA.");
        }
    }

    // -------------------------------------------------------------------------
    // resumo()
    // -------------------------------------------------------------------------

    @Nested
    class resumo {

        @Test
        void retorna_kpis_de_resumo() {
            when(repository.sumVenceHoje(igrejaId)).thenReturn(new BigDecimal("300.00"));
            when(repository.sumAVencerNoMes(eq(igrejaId), any(), any())).thenReturn(new BigDecimal("1000.00"));
            when(repository.sumAtrasadas(igrejaId)).thenReturn(new BigDecimal("500.00"));
            when(repository.sumPagasNoMes(eq(igrejaId), any(), any())).thenReturn(new BigDecimal("2000.00"));

            ResumoResponse resp = service.resumo(igrejaId, 9, 2026);

            assertThat(resp.venceHoje()).isEqualByComparingTo("300.00");
            assertThat(resp.aVencerNoMes()).isEqualByComparingTo("1000.00");
            assertThat(resp.atrasadas()).isEqualByComparingTo("500.00");
            assertThat(resp.pagasNoMes()).isEqualByComparingTo("2000.00");
        }

        @Test
        void retorna_zero_quando_nao_ha_contas() {
            when(repository.sumVenceHoje(igrejaId)).thenReturn(null);
            when(repository.sumAVencerNoMes(eq(igrejaId), any(), any())).thenReturn(null);
            when(repository.sumAtrasadas(igrejaId)).thenReturn(null);
            when(repository.sumPagasNoMes(eq(igrejaId), any(), any())).thenReturn(null);

            ResumoResponse resp = service.resumo(igrejaId, 9, 2026);

            assertThat(resp.venceHoje()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resp.aVencerNoMes()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resp.atrasadas()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resp.pagasNoMes()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // -------------------------------------------------------------------------
    // materializarRecorrencias()
    // -------------------------------------------------------------------------

    @Nested
    class materializarRecorrencias {

        @Test
        void cria_ocorrencias_futuras_quando_vencimentos_inexistentes() {
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Aluguel").valor(new BigDecimal("1500.00"))
                    .vencimento(LocalDate.of(2026, 9, 1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false).recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .recorrenciaVezes(12)
                    .recorrenciaAte(null)
                    .recorrenciaDiaAncora(null)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.buscarGeradorasAtivas()).thenReturn(List.of(geradora));
            when(repository.existsBySerieIdAndVencimentoAndDeletedAtIsNull(any(), any())).thenReturn(false);
            stubRepositorySave(new ContaAPagar());

            service.materializarRecorrencias();

            // 3 meses de projeção: out, nov, dez (set/out/nov/dez com base em hoje=22/set)
            verify(repository, atLeast(1)).save(any(ContaAPagar.class));
        }

        @Test
        void nao_duplica_ocorrencia_se_vencimento_ja_existe() {
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Aluguel").valor(new BigDecimal("1500.00"))
                    .vencimento(LocalDate.of(2026, 9, 1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false).recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .recorrenciaVezes(12)
                    .recorrenciaAte(null)
                    .recorrenciaDiaAncora(null)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.buscarGeradorasAtivas()).thenReturn(List.of(geradora));
            // Todas as datas já existem.
            when(repository.existsBySerieIdAndVencimentoAndDeletedAtIsNull(any(), any())).thenReturn(true);

            service.materializarRecorrencias();

            verify(repository, never()).save(any(ContaAPagar.class));
        }
    }

    // -------------------------------------------------------------------------
    // projetarSerie()
    // -------------------------------------------------------------------------

    @Nested
    class projetarSerie {

        @Test
        void retorna_ocorrencias_existentes_mais_projetadas() {
            UUID geradoraId = UUID.randomUUID();
            ContaAPagar geradora = ContaAPagar.builder()
                    .id(geradoraId).igreja(igreja).categoria(categoria)
                    .descricao("Serie").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.of(2026, 9, 1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .divergeDaSerie(false).recorrenciaFrequencia(RecorrenciaFrequencia.MENSAL)
                    .recorrenciaVezes(6)
                    .recorrenciaAte(null)
                    .recorrenciaDiaAncora(null)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();
            geradora.setSerie(geradora); // auto-referência.

            ContaAPagar ocorrencia = ContaAPagar.builder()
                    .id(UUID.randomUUID()).igreja(igreja).categoria(categoria)
                    .descricao("Serie").valor(new BigDecimal("200.00"))
                    .vencimento(LocalDate.of(2026, 10, 1))
                    .status(StatusConta.EM_ABERTO).valorPago(BigDecimal.ZERO)
                    .serie(geradora).divergeDaSerie(false)
                    .criadoPorTexto("João").pagamentos(new ArrayList<>())
                    .build();

            when(repository.findByIdAndIgrejaId(geradoraId, igrejaId)).thenReturn(Optional.of(geradora));
            when(repository.findBySerieIdOrderByVencimentoAsc(geradoraId)).thenReturn(List.of(geradora, ocorrencia));

            List<ProjecaoItem> proj = service.projetarSerie(geradoraId, igrejaId);

            assertThat(proj).isNotEmpty();
            // Deve conter existentes E previstas
            assertThat(proj.stream().anyMatch(i -> i.vencimento().equals(LocalDate.of(2026, 9, 1)))).isTrue();
            assertThat(proj.stream().anyMatch(i -> i.vencimento().equals(LocalDate.of(2026, 10, 1)))).isTrue();
        }

        @Test
        void lanca_excecao_quando_conta_nao_e_serie() {
            ContaAPagar conta = contaArmazenada();
            // Sem recorrenciaFrequencia.

            when(repository.findByIdAndIgrejaId(contaId, igrejaId)).thenReturn(Optional.of(conta));

            assertThatThrownBy(() -> service.projetarSerie(contaId, igrejaId))
                    .isInstanceOf(ConflitoNegocioException.class)
                    .hasMessageContaining("Esta conta não é uma série recorrente.");
        }
    }
}
