package com.domus.api.modules.igreja;

import com.domus.api.modules.celula.busca.CelulaSearchRepository;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.igreja.dto.CadastroCongregacaoRequest;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import com.domus.api.modules.ministerio.busca.MinisterioSearchRepository;
import com.domus.api.modules.pessoa.DTO.PessoaRequestDTO;
import com.domus.api.modules.pessoa.PessoaService;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.pessoa.busca.PessoaSearchRepository;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import com.domus.api.modules.visitante.busca.VisitanteSearchRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SaasConcorrenciaTest implements PostgresTestContainerSupport {

    @MockBean PessoaSearchRepository pessoaSearchRepository;
    @MockBean EventoSearchRepository eventoSearchRepository;
    @MockBean UsuarioSearchRepository usuarioSearchRepository;
    @MockBean MovimentacaoSearchRepository movimentacaoSearchRepository;
    @MockBean CategoriaSearchRepository categoriaSearchRepository;
    @MockBean CelulaSearchRepository celulaSearchRepository;
    @MockBean MinisterioSearchRepository ministerioSearchRepository;
    @MockBean VisitanteSearchRepository visitanteSearchRepository;

    @Autowired PessoaService pessoaService;
    @Autowired CodigoConviteService codigoConviteService;
    @Autowired CadastroCongregacaoService cadastroCongregacaoService;
    @Autowired EntityManagerFactory emf;

    private UUID matrizId;

    @AfterEach
    void limpar() {
        if (matrizId == null) return;
        executarCommitado(em -> {
            em.createNativeQuery("DELETE FROM codigo_convite_congregacao WHERE matriz_id = :id").setParameter("id", matrizId).executeUpdate();
            em.createNativeQuery("UPDATE igreja SET vinculado_por_usuario_id = NULL WHERE id = :id OR igreja_mae_id = :id").setParameter("id", matrizId).executeUpdate();
            em.createNativeQuery("DELETE FROM usuario WHERE igreja_id IN (SELECT id FROM igreja WHERE id = :id OR igreja_mae_id = :id)").setParameter("id", matrizId).executeUpdate();
            em.createNativeQuery("DELETE FROM pessoa WHERE igreja_id IN (SELECT id FROM igreja WHERE id = :id OR igreja_mae_id = :id)").setParameter("id", matrizId).executeUpdate();
            em.createNativeQuery("DELETE FROM igreja WHERE igreja_mae_id = :id").setParameter("id", matrizId).executeUpdate();
            em.createNativeQuery("DELETE FROM igreja WHERE id = :id").setParameter("id", matrizId).executeUpdate();
        });
    }

    @Test
    void doisCadastrosDePessoasSimultaneosNaUltimaVaga_apenasUmVence() throws Exception {
        UUID igrejaId = criarIgrejaBasicoCom49Pessoas();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger recusasPorLimite = new AtomicInteger();

        Callable<Void> task1 = () -> tentarCadastrarPessoa("Pessoa A", igrejaId, largada, sucessos, recusasPorLimite);
        Callable<Void> task2 = () -> tentarCadastrarPessoa("Pessoa B", igrejaId, largada, sucessos, recusasPorLimite);

        Future<Void> f1 = pool.submit(task1);
        Future<Void> f2 = pool.submit(task2);

        largada.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(recusasPorLimite.get()).isEqualTo(1);

        executarCommitado(em -> {
            Number total = (Number) em.createNativeQuery("SELECT COUNT(*) FROM pessoa WHERE igreja_id = :ig AND deleted_at IS NULL")
                    .setParameter("ig", igrejaId)
                    .getSingleResult();
            assertThat(total.longValue()).isEqualTo(50L);
        });
    }

    @Test
    void doisCadastrosDeCongregacaoSimultaneosNaUltimaVaga_apenasUmVence() throws Exception {
        matrizId = criarMatrizProCom1Filha();
        String codigoConvite = gerarCodigoConvite(matrizId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger recusasPorLimite = new AtomicInteger();

        Callable<Void> task1 = () -> tentarRegistrarCongregacao(codigoConvite, "Filha Alpha", "admin.alpha@teste.com", largada, sucessos, recusasPorLimite);
        Callable<Void> task2 = () -> tentarRegistrarCongregacao(codigoConvite, "Filha Beta", "admin.beta@teste.com", largada, sucessos, recusasPorLimite);

        Future<Void> f1 = pool.submit(task1);
        Future<Void> f2 = pool.submit(task2);

        largada.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(recusasPorLimite.get()).isEqualTo(1);

        executarCommitado(em -> {
            Number totalFilhas = (Number) em.createNativeQuery("SELECT COUNT(*) FROM igreja WHERE igreja_mae_id = :mae")
                    .setParameter("mae", matrizId)
                    .getSingleResult();
            assertThat(totalFilhas.longValue()).isEqualTo(2L);
        });
    }

    private Void tentarCadastrarPessoa(String nome, UUID igrejaId, CountDownLatch largada, AtomicInteger sucessos, AtomicInteger recusasPorLimite) throws Exception {
        largada.await();
        try {
            PessoaRequestDTO dto = new PessoaRequestDTO(nome, nome.toLowerCase().replace(" ", ".") + "@teste.com", "11999999999", null, null, Vinculo.MEMBRO, null, null, null, null, null, null);
            pessoaService.cadastrarMembro(dto, igrejaId);
            sucessos.incrementAndGet();
        } catch (PlanoLimiteExcedidoException e) {
            recusasPorLimite.incrementAndGet();
        }
        return null;
    }

    private Void tentarRegistrarCongregacao(String codigo, String nome, String email, CountDownLatch largada, AtomicInteger sucessos, AtomicInteger recusasPorLimite) throws Exception {
        largada.await();
        try {
            CadastroCongregacaoRequest request = new CadastroCongregacaoRequest(codigo, nome, email, "Admin", "senha123");
            cadastroCongregacaoService.registrarCongregacao(request);
            sucessos.incrementAndGet();
        } catch (PlanoLimiteExcedidoException e) {
            recusasPorLimite.incrementAndGet();
        }
        return null;
    }

    private UUID criarIgrejaBasicoCom49Pessoas() {
        UUID[] res = new UUID[1];
        executarCommitado(em -> {
            UUID igId = (UUID) em.createNativeQuery("INSERT INTO igreja (nome, email, plano, status_assinatura) VALUES ('Igreja Básico Teste', 'basico@teste.com', 'BASICO', 'ATIVA') RETURNING id").getSingleResult();
            matrizId = igId;
            for (int i = 1; i <= 49; i++) {
                em.createNativeQuery("INSERT INTO pessoa (igreja_id, nome, email, vinculo) VALUES (:ig, :nome, :email, 'MEMBRO')")
                        .setParameter("ig", igId)
                        .setParameter("nome", "Membro " + i)
                        .setParameter("email", "membro" + i + "@teste.com")
                        .executeUpdate();
            }
            res[0] = igId;
        });
        return res[0];
    }

    private UUID criarMatrizProCom1Filha() {
        UUID[] res = new UUID[1];
        executarCommitado(em -> {
            UUID mId = (UUID) em.createNativeQuery("INSERT INTO igreja (nome, email, plano, status_assinatura) VALUES ('Igreja Matriz PRO', 'matrizpro@teste.com', 'PRO', 'ATIVA') RETURNING id").getSingleResult();
            res[0] = mId;
            em.createNativeQuery("INSERT INTO igreja (nome, email, igreja_mae_id, status_assinatura) VALUES ('Igreja Filha 1', 'filha1@teste.com', :mae, 'ATIVA')")
                    .setParameter("mae", mId)
                    .executeUpdate();
        });
        return res[0];
    }

    private String gerarCodigoConvite(UUID mId) {
        String[] res = new String[1];
        executarCommitado(em -> {
            String codigo = "DOMUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            em.createNativeQuery("INSERT INTO codigo_convite_congregacao (matriz_id, codigo) VALUES (:m, :c)")
                    .setParameter("m", mId)
                    .setParameter("c", codigo)
                    .executeUpdate();
            em.createNativeQuery("UPDATE igreja SET codigo_vinculo = :c, codigo_gerado_em = NOW() WHERE id = :m")
                    .setParameter("m", mId)
                    .setParameter("c", codigo)
                    .executeUpdate();
            res[0] = codigo;
        });
        return res[0];
    }

    private void executarCommitado(java.util.function.Consumer<EntityManager> acao) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            acao.accept(em);
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
