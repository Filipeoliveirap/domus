package com.domus.api.modules.evento.inscricao;

import com.domus.api.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Prova que duas inscrições simultâneas na última vaga não passam as duas. Precisa de Postgres real (Neon) com duas conexões disputando a mesma linha — mock não expõe corrida.
 * O cenário é criado e commitado antes das threads começarem, fora da transação do teste, senão as outras conexões não enxergam o setup ainda.
 */
@SpringBootTest
class InscricaoConcorrenciaTest implements PostgresTestContainerSupport {

    @Autowired
    InscricaoService inscricaoService;

    @Autowired
    EntityManagerFactory emf;

    private UUID igrejaId;
    private UUID eventoId;

    @AfterEach
    void limpar() {
        if (igrejaId == null) return;
        // Ordem segura de FK: inscricao (convidado já é sua própria linha, não precisa de
        // DELETE separado) -> membro/evento -> igreja.
        executarCommitado(em -> {
            em.createNativeQuery("DELETE FROM inscricao_evento WHERE evento_id = :ev")
                    .setParameter("ev", eventoId).executeUpdate();
            em.createNativeQuery("DELETE FROM evento WHERE id = :ev")
                    .setParameter("ev", eventoId).executeUpdate();
            em.createNativeQuery("DELETE FROM pessoa WHERE igreja_id = :ig")
                    .setParameter("ig", igrejaId).executeUpdate();
            em.createNativeQuery("DELETE FROM igreja WHERE id = :ig")
                    .setParameter("ig", igrejaId).executeUpdate();
        });
    }

    @Test
    void duasInscricoesNaUltimaVaga_apenasUmaVence() throws Exception {
        UUID membroA = criarCenarioComUmaVaga();
        UUID membroB = criarMembro("Bruno Concorrencia");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger recusasPorVaga = new AtomicInteger();

        Callable<Void> tentativaA = () -> tentarInscrever(membroA, largada, sucessos, recusasPorVaga);
        Callable<Void> tentativaB = () -> tentarInscrever(membroB, largada, sucessos, recusasPorVaga);

        Future<Void> f1 = pool.submit(tentativaA);
        Future<Void> f2 = pool.submit(tentativaB);
        largada.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Exatamente uma pessoa entra, exatamente uma é recusada POR FALTA DE VAGA
        // (não por qualquer outro motivo — um catch genérico contaria erros de verdade
        // como "recusa correta" e mascararia um bug real).
        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(recusasPorVaga.get()).isEqualTo(1);
    }

    /** Tenta inscrever um membro depois de esperar a largada; classifica o resultado. */
    private Void tentarInscrever(UUID pessoaId, CountDownLatch largada,
                                 AtomicInteger sucessos, AtomicInteger recusasPorVaga) throws Exception {
        largada.await();
        try {
            inscricaoService.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);
            sucessos.incrementAndGet();
        } catch (BusinessException e) {
            // Só conta como "recusa esperada" o motivo de vaga esgotada. Qualquer outra
            // exceção (not-found, NPE, etc.) deve estourar o teste, não ser engolida.
            if (!"VAGAS_ESGOTADAS".equals(e.getCodigo())) {
                throw e;
            }
            recusasPorVaga.incrementAndGet();
        }
        return null;
    }

    /** Cria igreja, evento com vagas=1 e o primeiro membro; devolve o id do membro A. Committed. */
    private UUID criarCenarioComUmaVaga() {
        UUID[] resultado = new UUID[2];
        executarCommitado(em -> {
            igrejaId = (UUID) em.createNativeQuery("""
                    INSERT INTO igreja (nome, email) VALUES ('Concorrencia Teste', 'concorrencia@c.test')
                    RETURNING id
                    """).getSingleResult();

            eventoId = (UUID) em.createNativeQuery("""
                    INSERT INTO evento (igreja_id, titulo, inicio_em, vagas, requer_inscricao)
                    VALUES (:ig, 'Retiro Concorrencia', NOW() + INTERVAL '10 days', 1, TRUE)
                    RETURNING id
                    """).setParameter("ig", igrejaId).getSingleResult();

            resultado[0] = (UUID) em.createNativeQuery("""
                    INSERT INTO pessoa (igreja_id, nome, email, vinculo)
                    VALUES (:ig, 'Ana Concorrencia', 'ana.concorrencia@c.test', 'MEMBRO')
                    RETURNING id
                    """).setParameter("ig", igrejaId).getSingleResult();
        });
        return resultado[0];
    }

    private UUID criarMembro(String nome) {
        UUID[] resultado = new UUID[1];
        executarCommitado(em -> resultado[0] = (UUID) em.createNativeQuery("""
                INSERT INTO pessoa (igreja_id, nome, email, vinculo)
                VALUES (:ig, :nome, :email, 'MEMBRO')
                RETURNING id
                """)
                .setParameter("ig", igrejaId)
                .setParameter("nome", nome)
                .setParameter("email", nome.toLowerCase().replace(" ", ".") + "@c.test")
                .getSingleResult());
        return resultado[0];
    }

    /** EntityManager próprio pra não vazar pro contexto de persistência do teste; commita de verdade. */
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
