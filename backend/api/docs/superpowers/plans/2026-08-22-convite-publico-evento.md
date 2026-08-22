# Convite Público de Evento (Spec 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar qualquer pessoa com inscrição num evento compartilhar um link (WhatsApp/copiar)
pra que gente de fora da igreja se inscreva sozinha, e unificar os fluxos de "inscrever
alguém" (membro, visitante ou pessoa de fora) num modal só, reaproveitando os campos
personalizados da Spec 1 com um atalho de template pra dado que já existe em `Pessoa`.

**Architecture:** Convidado sem cadastro ganha `InscricaoEvento` própria (`pessoa_id` nulo,
nome/telefone na própria linha, `convidado_por_pessoa_id` opcional) — não um acompanhante
aninhado (`AcompanhanteInscricao` continua existindo, intocado, só pro fluxo antigo "+1
rápido"). Token de convite vive no Redis (mesmo padrão de `PasswordResetService`), sem tabela
nova. Back: `InscricaoService.inscreverConvidado` (núcleo reaproveitado por modal presencial e
convite público), `ConviteService` (token), extensão de `CampoPersonalizadoService`
(mapeamento). Front: modal unificado 3 abas, página pública `/convite/[token]`, template de
campos no builder de evento.

**Tech Stack:** Spring Boot / JPA / Postgres (Flyway) / Redis no back; Next.js / React Hook
Form / Zod no front — mesmo stack do resto do projeto.

**Spec:** `docs/superpowers/specs/2026-08-22-convite-publico-evento-design.md`

## Global Constraints

- `igreja_id` sempre extraído do JWT (via `UsuarioAutenticado`), nunca do corpo da requisição —
  exceto nos dois endpoints públicos (`GET /convites/{token}`, `POST /convites/{token}/entrar`),
  que resolvem tudo a partir do token (sem JWT nenhum).
- `AcompanhanteInscricao` **não é tocado** por esta spec — continua existindo exatamente como
  está, servindo só o fluxo antigo "Vou levar alguém de fora" (`ModalConvidado`).
- Convidado sem cadastro nunca tem elegibilidade checada (sem `Pessoa`, não tem
  `vinculo`/`sexo`/`estado_civil`/`data_nascimento`), mas é bloqueado em evento
  `exclusivo_membros`, igual ao acompanhante hoje.
- Mapeamento de campo personalizado nunca escreve de volta em `Pessoa` — resposta é sempre
  snapshot isolado no evento.
- Token de convite: opaco (`SecureRandom`, `Base64.getUrlEncoder`), Redis, TTL calculado a
  partir da data do evento — nunca fixo em minutos.
- `GET /convites/{token}` nunca devolve lista de inscritos, e-mail/telefone de quem convidou,
  nem qualquer outra `Pessoa` além do convidante.
- Testes de service: Mockito puro, estilo A do projeto (`mock()` manual no `@BeforeEach`) —
  mesmo padrão de `InscricaoServiceTest`/`CampoPersonalizadoServiceTest`.
- Rodar suite completa (`set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`) antes
  de cada commit de task do backend — precisa de Docker rodando (Testcontainers).
- Front: toda tela nova responsiva desde a entrega (mobile obrigatório, não etapa separada).
  Rótulo de campo sempre com exemplo concreto via `placeholder`.

---

## Backend

### Task 1: `InscricaoEvento` — colunas de convidado, migration, correção de exibição

**Files:**
- Create: `src/main/resources/db/migration/V26__convite_evento.sql`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/InscritoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ParticipanteResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/DTOs/InscritoResponseTest.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/DTOs/ParticipanteResponseTest.java`

**Interfaces:**
- Produces: `InscricaoEvento.getNomeConvidado()/getTelefoneConvidado()/getConvidadoPor()`,
  `InscricaoEvento.isConvidadoSemCadastro(): boolean`; `InscritoResponse.from(InscricaoEvento,
  Pessoa pessoaResolvida, RegistranteResumo, Pessoa convidadoPorResolvida)`;
  `ParticipanteResponse.from(InscricaoEvento, Pessoa pessoaResolvida, Pessoa
  convidadoPorResolvida)`.

- [ ] **Step 1: Escrever a migration**

```sql
-- Convidado sem cadastro ganha inscrição própria (não acompanhante aninhado) — nome e
-- telefone vivem na própria linha quando pessoa_id é nulo POR ESTE MOTIVO. pessoa_id nulo já
-- tinha um significado diferente (Pessoa excluída via LGPD, ver V18) — a distinção entre os
-- dois casos é: convidado sempre tem nome_convidado preenchido; pessoa excluída, não.
ALTER TABLE inscricao_evento ADD COLUMN nome_convidado VARCHAR(255);
ALTER TABLE inscricao_evento ADD COLUMN telefone_convidado VARCHAR(20);

-- Referência informativa a quem convidou (Pessoa da igreja) — nula só quando a linha é de
-- Pessoa cadastrada (não rastreamos "quem convidou" pra quem já é do sistema) ou LGPD-purgada.
ALTER TABLE inscricao_evento ADD COLUMN convidado_por_pessoa_id UUID REFERENCES pessoa(id);

CREATE INDEX idx_inscricao_convidado_por ON inscricao_evento (convidado_por_pessoa_id);

-- Sem CHECK de banco pra "pessoa_id OU nome_convidado preenchido": a exclusão LGPD
-- (desvincularPessoa) produz linhas com os dois nulos, e um CHECK bloquearia esse UPDATE.
-- A regra é só de aplicação (InscricaoService), nunca do banco.
```

- [ ] **Step 2: Adicionar os campos na entidade**

Em `InscricaoEvento.java`, logo depois do campo `pessoa`:

```java
    @Column(name = "nome_convidado", length = 255)
    private String nomeConvidado;

    @Column(name = "telefone_convidado", length = 20)
    private String telefoneConvidado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convidado_por_pessoa_id")
    private Pessoa convidadoPor;
```

E, depois dos outros campos da classe (antes do fechamento `}`):

```java
    /** {@code true} = inscrição de gente sem cadastro no sistema (modelo desta spec — nunca
     *  confundir com {@link #getAcompanhantes()}, que é o modelo antigo aninhado). */
    public boolean isConvidadoSemCadastro() {
        return pessoa == null && nomeConvidado != null;
    }
```

- [ ] **Step 3: Escrever os testes de `InscritoResponse` (falhando)**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InscritoResponseTest {

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(UUID.randomUUID());
        return i;
    }

    private InscricaoEvento inscricaoBase(Igreja igreja) {
        return InscricaoEvento.builder()
                .id(UUID.randomUUID())
                .igreja(igreja)
                .evento(Evento.builder().id(UUID.randomUUID()).igreja(igreja).titulo("Retiro").build())
                .status(StatusInscricao.CONFIRMADA)
                .acompanhantes(List.of())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void mostraNomeConvidadoQuandoPessoaNulaENomeConvidadoPreenchido() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        i.setNomeConvidado("Maria de Fora");
        i.setTelefoneConvidado("11999998888");

        InscritoResponse resp = InscritoResponse.from(i, null, null, null);

        assertThat(resp.nome()).isEqualTo("Maria de Fora");
        assertThat(resp.pessoaRemovida()).isFalse();
    }

    @Test
    void mostraPessoaRemovidaQuandoPessoaENomeConvidadoAmbosNulos() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        // pessoa e nomeConvidado ambos nulos — LGPD purgou a pessoa.

        InscritoResponse resp = InscritoResponse.from(i, null, null, null);

        assertThat(resp.nome()).isEqualTo("Pessoa removida do sistema");
        assertThat(resp.pessoaRemovida()).isTrue();
    }

    @Test
    void mostraPessoaEConvidadoPorQuandoAmbosPreenchidos() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        i.setNomeConvidado("João Visitante");

        Pessoa convidante = Pessoa.builder().id(UUID.randomUUID()).nome("Ana Convidante").build();

        InscritoResponse resp = InscritoResponse.from(i, null, null, convidante);

        assertThat(resp.nome()).isEqualTo("João Visitante");
        assertThat(resp.convidadoPorNome()).isEqualTo("Ana Convidante");
    }

    @Test
    void mostraNomeDaPessoaQuandoPessoaPreenchida() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).nome("Carlos Membro").igreja(igreja).build();

        InscritoResponse resp = InscritoResponse.from(i, pessoa, null, null);

        assertThat(resp.nome()).isEqualTo("Carlos Membro");
        assertThat(resp.pessoaRemovida()).isFalse();
        assertThat(resp.convidadoPorNome()).isNull();
    }
}
```

- [ ] **Step 4: Rodar e ver falhar (assinatura de `from` não bate)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=InscritoResponseTest`
Expected: FAIL — `method from cannot be applied to given types` (assinatura antiga tem 3 args)

- [ ] **Step 5: Atualizar `InscritoResponse`**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.pessoa.Pessoa;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Uma linha da lista de inscritos (ADMIN/LÍDER). */
public record InscritoResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        UUID fotoId,
        boolean pessoaRemovida,
        /** NULL = a pessoa se inscreveu sozinha. */
        UUID inscritoPorUsuarioId,
        /** NULL também quando a conta de quem inscreveu foi arquivada depois — front não inventa nome. */
        String inscritoPorNome,
        UUID inscritoPorFotoId,
        /** Preenchido só pra convidado sem cadastro (ver {@link InscricaoEvento#isConvidadoSemCadastro}). */
        String convidadoPorNome,
        LocalDateTime inscritoEm,
        List<AcompanhanteResponse> acompanhantes,
        EventoResponse.IgrejaResumo igrejaDaPessoa
) {
    private static final String NOME_PESSOA_REMOVIDA = "Pessoa removida do sistema";

    /**
     * @param pessoaResolvida resolvida em lote pelo chamador via bypass do @SQLRestriction
     *                        (nunca {@code i.getPessoa()} direto — pessoa arquivada, mas não
     *                        excluída, ainda mostra os dados reais; NULL só quando excluída
     *                        de vez OU quando é convidado sem cadastro).
     * @param registrante     resumo já resolvido em lote pelo chamador; NULL nos mesmos casos.
     * @param convidadoPorResolvida resolvida em lote (mesmo motivo de pessoaResolvida); NULL
     *                        quando não há convidante (Pessoa cadastrada, ou cadastro avulso
     *                        sem host).
     */
    public static InscritoResponse from(InscricaoEvento i, Pessoa pessoaResolvida,
                                         RegistranteResumo registrante, Pessoa convidadoPorResolvida) {
        boolean pessoaRemovida = pessoaResolvida == null && i.getNomeConvidado() == null;
        String nome = pessoaResolvida != null ? pessoaResolvida.getNome()
                : i.getNomeConvidado() != null ? i.getNomeConvidado()
                : NOME_PESSOA_REMOVIDA;

        return new InscritoResponse(
                i.getId(),
                pessoaResolvida == null ? null : pessoaResolvida.getId(),
                nome,
                pessoaResolvida != null && pessoaResolvida.getFoto() != null ? pessoaResolvida.getFoto().getId() : null,
                pessoaRemovida,
                i.getInscritoPorUsuarioId(),
                registrante == null ? null : registrante.nome(),
                registrante == null ? null : registrante.fotoId(),
                convidadoPorResolvida == null ? null : convidadoPorResolvida.getNome(),
                i.getCreatedAt(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList(),
                EventoResponse.IgrejaResumo.de(pessoaResolvida != null ? pessoaResolvida.getIgreja() : i.getIgreja())
        );
    }
}
```

- [ ] **Step 6: Atualizar `ParticipanteResponse`**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.pessoa.Pessoa;
import java.util.List;
import java.util.UUID;

/**
 * Visível a QUALQUER MEMBRO autenticado — por isso omite, em relação a {@link InscritoResponse},
 * telefone de convidado, quem inscreveu quem e data da inscrição (dados administrativos, não de "quem vai").
 */
public record ParticipanteResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        UUID fotoId,
        String convidadoPorNome,
        List<String> convidados,
        EventoResponse.IgrejaResumo igrejaDaPessoa
) {
    private static final String NOME_PESSOA_REMOVIDA = "Pessoa removida do sistema";

    /** @param pessoaResolvida/@param convidadoPorResolvida resolvidas em lote via bypass —
     *  ver Javadoc de {@link InscritoResponse#from}. */
    public static ParticipanteResponse from(InscricaoEvento i, Pessoa pessoaResolvida, Pessoa convidadoPorResolvida) {
        String nome = pessoaResolvida != null ? pessoaResolvida.getNome()
                : i.getNomeConvidado() != null ? i.getNomeConvidado()
                : NOME_PESSOA_REMOVIDA;

        return new ParticipanteResponse(
                i.getId(),
                pessoaResolvida == null ? null : pessoaResolvida.getId(),
                nome,
                pessoaResolvida != null && pessoaResolvida.getFoto() != null ? pessoaResolvida.getFoto().getId() : null,
                convidadoPorResolvida == null ? null : convidadoPorResolvida.getNome(),
                i.getAcompanhantes().stream().map(a -> a.getNome()).toList(),
                EventoResponse.IgrejaResumo.de(pessoaResolvida != null ? pessoaResolvida.getIgreja() : i.getIgreja())
        );
    }
}
```

- [ ] **Step 7: Rodar os testes de DTO e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=InscritoResponseTest`
Expected: PASS (4 testes)

- [ ] **Step 8: Escrever o teste de `ParticipanteResponse` (mesmo padrão)**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipanteResponseTest {

    @Test
    void mostraNomeConvidadoEConvidadoPorQuandoSemCadastro() {
        Igreja igreja = new Igreja();
        igreja.setId(UUID.randomUUID());
        InscricaoEvento i = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja)
                .evento(Evento.builder().id(UUID.randomUUID()).igreja(igreja).titulo("Culto").build())
                .status(StatusInscricao.CONFIRMADA)
                .nomeConvidado("Pedro de Fora")
                .acompanhantes(List.of())
                .build();
        Pessoa convidante = Pessoa.builder().id(UUID.randomUUID()).nome("Lucas").build();

        ParticipanteResponse resp = ParticipanteResponse.from(i, null, convidante);

        assertThat(resp.nome()).isEqualTo("Pedro de Fora");
        assertThat(resp.convidadoPorNome()).isEqualTo("Lucas");
    }
}
```

- [ ] **Step 9: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=ParticipanteResponseTest`
Expected: PASS

- [ ] **Step 10: Atualizar as chamadas em `InscricaoService` (resolver `convidadoPor` em lote)**

Em `InscricaoService.java`, o método `resolverPessoasEmLote` passa a coletar também os ids de
`convidadoPor` (mesmo mapa, uma query só cobre os dois casos):

```java
    private Map<UUID, Pessoa> resolverPessoasEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = new ArrayList<>();
        for (InscricaoEvento i : inscricoes) {
            if (i.getPessoa() != null) ids.add(i.getPessoa().getId());
            if (i.getConvidadoPor() != null) ids.add(i.getConvidadoPor().getId());
        }
        List<UUID> idsUnicos = ids.stream().distinct().toList();
        if (idsUnicos.isEmpty()) {
            return Map.of();
        }
        return membroRepository.findByIdInIncluindoArquivadas(idsUnicos).stream()
                .collect(java.util.stream.Collectors.toMap(Pessoa::getId, p -> p));
    }
```

Adicionar o helper `resolverConvidadoPor` (mesmo padrão de `resolverPessoa`), logo abaixo dele:

```java
    private Pessoa resolverConvidadoPor(InscricaoEvento i, Map<UUID, Pessoa> pessoasResolvidas) {
        Pessoa p = i.getConvidadoPor();
        if (p == null) return null;
        if (!(p instanceof org.hibernate.proxy.HibernateProxy)) return p;
        return pessoasResolvidas.get(p.getId());
    }
```

E os dois pontos de chamada de `InscritoResponse.from`/`ParticipanteResponse.from` (dentro de
`listarInscritos` e `listarParticipantes`) passam o quarto/terceiro argumento:

```java
        List<InscritoResponse> inscritosDaPagina = inscricoes.stream()
                .map(i -> InscritoResponse.from(i,
                        resolverPessoa(i, pessoas),
                        registrantes.get(i.getInscritoPorUsuarioId()),
                        resolverConvidadoPor(i, pessoas)))
                .toList();
```

```java
        return inscricoes.stream()
                .map(i -> ParticipanteResponse.from(i, resolverPessoa(i, pessoas), resolverConvidadoPor(i, pessoas)))
                .toList();
```

- [ ] **Step 11: Rodar a suíte completa e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS (compila e nada quebrou — `InscricaoServiceTest` continua passando)

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/db/migration/V26__convite_evento.sql \
  src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java \
  src/main/java/com/domus/api/modules/evento/inscricao/DTOs/InscritoResponse.java \
  src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ParticipanteResponse.java \
  src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
  src/test/java/com/domus/api/modules/evento/inscricao/DTOs/InscritoResponseTest.java \
  src/test/java/com/domus/api/modules/evento/inscricao/DTOs/ParticipanteResponseTest.java
git commit -m "feat(evento): colunas de convidado sem cadastro e correcao de exibicao"
```

---

### Task 2: `InscricaoService.inscreverConvidado`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `EventoRepository.buscarComLockVisivelParaFamilia` (já existe), `validarEventoAberto`,
  `validarVaga` (já existem, privados/package-private).
- Produces: `InscricaoService.inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
  String telefone, UUID convidadoPorPessoaId): InscricaoEvento`.

- [ ] **Step 1: Escrever os testes (falhando)**

Adicionar ao `InscricaoServiceTest.java` existente (mesmo estilo de mocks já usado nesse
arquivo — `mock()` manual, campos `eventoId`/`igrejaId` já existem no topo da classe):

```java
    @Test
    void inscreverConvidadoCriaInscricaoComPessoaNulaENomeConvidadoPreenchido() {
        UUID convidadoPorId = UUID.randomUUID();
        Evento evento = evento(null); // sem limite de vagas
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria de Fora",
                "11999998888", convidadoPorId);

        assertThat(salva.getPessoa()).isNull();
        assertThat(salva.getNomeConvidado()).isEqualTo("Maria de Fora");
        assertThat(salva.getTelefoneConvidado()).isEqualTo("11999998888");
        assertThat(salva.isConvidadoSemCadastro()).isTrue();
        verify(inscricaoRepository).save(any());
    }

    @Test
    void inscreverConvidadoRecusaQuandoVagasEsgotadas() {
        Evento evento = evento(1);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoRecusaQuandoEventoExclusivoMembros() {
        Evento evento = evento(null);
        evento.setExclusivoMembros(true);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exclusivo");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoNaoChecaElegibilidade() {
        Evento evento = evento(null);
        evento.setIdadeMin(18); // restrição que bloquearia qualquer Pessoa sem data de nascimento
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null);

        verifyNoInteractions(elegibilidadeService);
    }

    @Test
    void inscreverConvidadoGravaConvidadoPorPessoaIdQuandoInformado() {
        UUID convidadoPorId = UUID.randomUUID();
        Pessoa convidante = Pessoa.builder().id(convidadoPorId).build();
        Evento evento = evento(null);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(membroRepository.findByIdAndIgrejaId(convidadoPorId, igrejaId)).thenReturn(Optional.of(convidante));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, convidadoPorId);

        assertThat(salva.getConvidadoPor()).isEqualTo(convidante);
    }

    @Test
    void inscreverConvidadoAceitaConvidadoPorNulo() {
        Evento evento = evento(null);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null);

        assertThat(salva.getConvidadoPor()).isNull();
        verify(membroRepository, never()).findByIdAndIgrejaId(any(), any());
    }
```

> Se `InscricaoServiceTest.java` não tiver um helper `evento(Integer vagas)` pronto, usar o
> helper equivalente já existente no arquivo (ex.: `evento(vagas)` ou similar — conferir o
> nome real antes de escrever o teste; os testes acima assumem que existe um jeito de construir
> um `Evento` com `id`, `igreja`, `vagas` e `requerInscricao=true`, já que é o padrão usado
> pelos outros testes de `inscrever`/`adicionarAcompanhante` nesse arquivo).

- [ ] **Step 2: Rodar e ver falhar (método não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=InscricaoServiceTest`
Expected: FAIL — `cannot find symbol inscreverConvidado`

- [ ] **Step 3: Implementar `inscreverConvidado`**

Adicionar em `InscricaoService.java`, logo depois de `adicionarAcompanhante`:

```java
    /** Convidado sem cadastro ganha inscrição própria (não acompanhante aninhado) — sem
     *  elegibilidade checada (não existe Pessoa pra avaliar), mas ainda bloqueado em evento
     *  exclusivo pra membros. Usado tanto pelo modal presencial (convidadoPorPessoaId = quem
     *  está logado) quanto pelo convite público (convidadoPorPessoaId = dono do token). */
    @Transactional
    public InscricaoEvento inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
                                               String telefone, UUID convidadoPorPessoaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        validarOrganizaInscricao(evento, "Este evento não permite convidados.");
        if (evento.isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(evento);
        validarVaga(evento, 1);

        Pessoa convidadoPor = convidadoPorPessoaId == null ? null
                : membroRepository.findByIdAndIgrejaId(convidadoPorPessoaId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        InscricaoEvento inscricao = InscricaoEvento.builder()
                .igreja(evento.getIgreja())
                .evento(evento)
                .pessoa(null)
                .nomeConvidado(TextoUtil.capitalizar(nome))
                .telefoneConvidado(telefone)
                .convidadoPor(convidadoPor)
                .status(StatusInscricao.CONFIRMADA)
                .build();

        InscricaoEvento salva = inscricaoRepository.save(inscricao);
        log.info("Convidado inscrito. evento_id={}, convidado_por_pessoa_id={}, igreja_id={}",
                eventoId, convidadoPorPessoaId, igrejaId);
        return salva;
    }
```

- [ ] **Step 4: Rodar os testes e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=InscricaoServiceTest`
Expected: PASS (todos os testes de `InscricaoServiceTest`, inclusive os 6 novos)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
  src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(evento): InscricaoService.inscreverConvidado sem checar elegibilidade"
```

---

### Task 3: DTO + endpoint `POST /eventos/{eventoId}/inscricoes/convidados`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/CriarConvidadoRequest.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ConvidadoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java`
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoControllerTest.java`
  (criar se não existir; se já existir um teste de controller para este módulo, adicionar nele)

**Interfaces:**
- Consumes: `InscricaoService.inscreverConvidado` (Task 2), `CampoPersonalizadoService.responder`
  (Spec 1, sem mudança nesta task — chamado só se `respostas` não vier vazio).
- Produces: `POST /eventos/{eventoId}/inscricoes/convidados` → `201` com `ConvidadoResponse`.

- [ ] **Step 1: Criar os DTOs**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CriarConvidadoRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String nome,
        @Size(max = 20, message = "Máximo 20 caracteres.")
        String telefone,
        @Valid
        List<RespostaRequest> respostas
) {}
```

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.util.UUID;

public record ConvidadoResponse(UUID inscricaoId, String nome, String telefone) {
    public static ConvidadoResponse from(InscricaoEvento i) {
        return new ConvidadoResponse(i.getId(), i.getNomeConvidado(), i.getTelefoneConvidado());
    }
}
```

- [ ] **Step 2: Adicionar o endpoint no controller**

Em `InscricaoController.java`, logo depois de `adicionarAcompanhante`:

```java
    @PostMapping("/eventos/{eventoId}/inscricoes/convidados")
    public ResponseEntity<ConvidadoResponse> criarConvidado(
            @PathVariable UUID eventoId,
            @Valid @RequestBody CriarConvidadoRequest data) {
        var usuario = usuarioAutenticado.get();
        var inscricao = inscricaoService.inscreverConvidado(
                eventoId, usuario.getIgreja().getId(), data.nome(), data.telefone(),
                usuario.getPessoa().getId());
        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responder(inscricao.getId(), null, data.respostas(),
                    usuario.getIgreja().getId(), usuario.getPessoa().getId(), usuario.getRole().getNome());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ConvidadoResponse.from(inscricao));
    }
```

> Nota: `campoPersonalizadoService.responder` checa "dono da inscrição OU gestor" — quem acabou
> de chamar `criarConvidado` é sempre o `convidadoPor` dessa inscrição (`usuario.getPessoa()`),
> mas a inscrição criada tem `pessoa = null`, então a checagem de "dono" (que compara
> `inscricao.getPessoa().getId()`) sempre falha aqui. **Isto exige que quem chama este endpoint
> seja sempre alguém com `podeGerenciarEventos` verdadeiro** (`ADMIN_IGREJA`/`LIDER`), senão
> `responder` lança `SEM_PERMISSAO` mesmo sendo o próprio criador do convidado. Ver ajuste de
> rota no Step 3 abaixo — o endpoint fica restrito a quem gerencia eventos nesta task; abrir
> pra `ACESSO_COMUM` (membro comum convidando alguém) é tratado explicitamente na Task 10
> (frontend), que **não** deve expor "adicionar campos personalizados" pra quem não gerencia —
> nesse caso o modal chama `criarConvidado` sem `respostas` (só nome/telefone) e mostra os
> campos como pendência depois, ou a Task 3 aqui já resolve isso relaxando `responder` — decidir
> na hora: **a abordagem mais simples e correta é comparar `convidadoPor` também como "dono"**
> em `CampoPersonalizadoService.responder`. Ver Step 3.

- [ ] **Step 3: Ajustar `CampoPersonalizadoService.responder` pra aceitar quem convidou como dono**

Em `CampoPersonalizadoService.java`, a checagem de autorização em `responder` passa a
considerar também `convidadoPor`:

```java
        boolean ehDono = (inscricao.getPessoa() != null
                        && java.util.Objects.equals(inscricao.getPessoa().getId(), pessoaLogadaId))
                || (inscricao.getConvidadoPor() != null
                        && java.util.Objects.equals(inscricao.getConvidadoPor().getId(), pessoaLogadaId));
```

Isso substitui a linha `boolean ehDono = inscricao.getPessoa() != null && ...` já existente —
qualquer pessoa comum que convidou alguém pode responder os campos personalizados por essa
pessoa, mesmo sem ser ADMIN/LÍDER. Reaproveita o `responder()` como está, sem endpoint/DTO novo.

- [ ] **Step 4: Adicionar o matcher no `SecurityConfig`**

Logo abaixo da linha `.requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes")` em
`SecurityConfig.java` (mesmo grupo de rotas de inscrição, visível pra qualquer perfil
autenticado — a checagem de "dono ou gestor" já é feita dentro do service/campos
personalizados, não precisa travar por role aqui):

```java
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes/convidados")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
```

> Adicionar **antes** do matcher genérico `.requestMatchers("/eventos/*/inscricoes/**")` (linha
> ~93 hoje) — Spring Security usa o primeiro matcher que casar, então uma regra mais específica
> precisa vir antes da mais genérica. Conferir a ordem final abrindo o arquivo antes de editar.

- [ ] **Step 5: Escrever o teste de integração (controller)**

Usar `AutenticacaoTestSupport` (padrão do projeto, ver `CLAUDE.md`). Se
`InscricaoControllerTest.java` não existir ainda, criar:

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.usuario.role.Role;
import com.domus.api.modules.usuario.role.RoleRepository;
import com.domus.api.shared.security.AutenticacaoTestSupport;
import com.domus.api.shared.security.Perfil;
import com.domus.api.config.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InscricaoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EventoRepository eventoRepository;

    AutenticacaoTestSupport auth;
    Igreja igreja;
    Usuario usuarioComum;
    Evento evento;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);

        igreja = new Igreja();
        igreja.setNome("Igreja Teste Convidado");
        igreja.setEmailContato("convidado@teste.com");
        igreja = igrejaRepository.save(igreja);

        Pessoa pessoaComum = Pessoa.builder().igreja(igreja).nome("Comum")
                .email("comum@teste.com").vinculo(Vinculo.MEMBRO).build();
        pessoaComum = pessoaRepository.save(pessoaComum);

        Role roleComum = roleRepository.findByNome(Perfil.ACESSO_COMUM.name())
                .orElseThrow(() -> new IllegalStateException("Seed de roles ausente."));
        usuarioComum = Usuario.builder().igreja(igreja).pessoa(pessoaComum).role(roleComum)
                .senhaHash("hash").ativo(true).build();
        usuarioComum = usuarioRepository.save(usuarioComum);

        evento = Evento.builder().igreja(igreja).titulo("Culto")
                .inicioEm(LocalDateTime.now().plusDays(3)).requerInscricao(true).build();
        evento = eventoRepository.save(evento);
    }

    @Test
    void membroComumConseguePendurarConvidadoSemCadastro() throws Exception {
        var cookie = auth.autenticado(usuarioComum);

        mockMvc.perform(post("/eventos/" + evento.getId() + "/inscricoes/convidados")
                        .cookie(cookie.toArray(new jakarta.servlet.http.Cookie[0]))
                        .contentType("application/json")
                        .content("{\"nome\":\"Maria de Fora\",\"telefone\":\"11999998888\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void semAutenticacaoRecusa() throws Exception {
        mockMvc.perform(post("/eventos/" + evento.getId() + "/inscricoes/convidados")
                        .contentType("application/json")
                        .content("{\"nome\":\"Maria de Fora\"}"))
                .andExpect(status().isUnauthorized());
    }
}
```

> Ajustar `AutenticacaoTestSupport.autenticado(...)` pro método/assinatura exata já existente no
> projeto (ver `VisitanteControllerTest.java` como referência de uso real, citado no
> `CLAUDE.md`) — o esqueleto acima segue a descrição do harness, mas confirme a assinatura (pode
> devolver `Cookie[]`, `List<Cookie>` ou já vir com CSRF anexado via outro método) antes de
> finalizar o teste.

- [ ] **Step 6: Rodar os testes e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=InscricaoControllerTest`
Expected: PASS

- [ ] **Step 7: Rodar a suíte completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/DTOs/CriarConvidadoRequest.java \
  src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ConvidadoResponse.java \
  src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java \
  src/main/java/com/domus/api/config/SecurityConfig.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java \
  src/test/java/com/domus/api/modules/evento/inscricao/InscricaoControllerTest.java
git commit -m "feat(evento): endpoint de criar convidado sem cadastro"
```

---

### Task 4: `CampoPersonalizadoEvento.mapeamento` — migration, enum, entidade

**Files:**
- Create: `src/main/resources/db/migration/V27__mapeamento_campo_personalizado.sql`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/MapeamentoCampoPersonalizado.java`
- Modify: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEvento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequestTest.java`

**Interfaces:**
- Produces: `MapeamentoCampoPersonalizado` enum (`IDADE`, `ESTADO_CIVIL`, `SEXO`, `ENDERECO`);
  `CampoPersonalizadoEvento.getMapeamento()/setMapeamento(...)`.

- [ ] **Step 1: Escrever a migration**

```sql
-- Mapeamento de campo personalizado pra dado estruturado de Pessoa (Spec 2). NULL = campo
-- criado manualmente, nunca pula pergunta mesmo se a Pessoa já tiver o dado.
ALTER TABLE campo_personalizado_evento ADD COLUMN mapeamento VARCHAR(20);
```

- [ ] **Step 2: Criar o enum**

```java
package com.domus.api.modules.evento.campopersonalizado;

public enum MapeamentoCampoPersonalizado {
    IDADE,
    ESTADO_CIVIL,
    SEXO,
    ENDERECO
}
```

- [ ] **Step 3: Adicionar o campo na entidade**

Em `CampoPersonalizadoEvento.java`, logo depois de `ordem`:

```java
    /** Groundwork/atalho pra Spec 2: campo vindo do template de dados básicos. NULL = campo
     *  manual, nunca pula pergunta pra quem já tem o dado em Pessoa. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MapeamentoCampoPersonalizado mapeamento;
```

- [ ] **Step 4: Escrever o teste de request com mapeamento (falhando)**

Adicionar ao `CampoPersonalizadoRequestTest.java` existente:

```java
    @Test
    void aceitaMapeamentoOpcional() {
        var request = new CampoPersonalizadoRequest(
                null, "Idade", "Ex.: 24", TipoCampoPersonalizado.TEXTO_CURTO, null, false, true, 0,
                MapeamentoCampoPersonalizado.IDADE);

        assertThat(validator.validate(request)).isEmpty();
    }
```

- [ ] **Step 5: Rodar e ver falhar (construtor não bate)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoRequestTest`
Expected: FAIL — `constructor CampoPersonalizadoRequest cannot be applied to given types`

- [ ] **Step 6: Atualizar `CampoPersonalizadoRequest`/`CampoPersonalizadoResponse`**

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.MapeamentoCampoPersonalizado;
import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** {@code id} nulo = campo novo; preenchido = atualiza o existente (ver salvar() no service). */
public record CampoPersonalizadoRequest(
        UUID id,
        @NotBlank(message = "O rótulo é obrigatório.")
        @Size(max = 120, message = "Máximo 120 caracteres.")
        String label,
        @Size(max = 160, message = "Máximo 160 caracteres.")
        String placeholder,
        @NotNull(message = "Escolha o tipo do campo.")
        TipoCampoPersonalizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean visivelAoPublico,
        int ordem,
        MapeamentoCampoPersonalizado mapeamento
) {
    @AssertTrue(message = "Informe pelo menos uma opção.")
    public boolean isOpcoesValidas() {
        boolean precisaDeOpcoes = tipo == TipoCampoPersonalizado.OPCAO_UNICA
                || tipo == TipoCampoPersonalizado.MULTIPLA_ESCOLHA;
        return !precisaDeOpcoes || (opcoes != null && !opcoes.isEmpty());
    }
}
```

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEvento;
import com.domus.api.modules.evento.campopersonalizado.MapeamentoCampoPersonalizado;
import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;

import java.util.List;
import java.util.UUID;

public record CampoPersonalizadoResponse(
        UUID id,
        String label,
        String placeholder,
        TipoCampoPersonalizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean visivelAoPublico,
        int ordem,
        MapeamentoCampoPersonalizado mapeamento
) {
    public static CampoPersonalizadoResponse from(CampoPersonalizadoEvento c) {
        return new CampoPersonalizadoResponse(
                c.getId(), c.getLabel(), c.getPlaceholder(), c.getTipo(),
                c.getOpcoesComoLista(), c.isObrigatorio(), c.isVisivelAoPublico(), c.getOrdem(),
                c.getMapeamento()
        );
    }
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoRequestTest`
Expected: PASS

- [ ] **Step 8: Atualizar `CampoPersonalizadoService.salvar` pra propagar/zerar o mapeamento**

Em `CampoPersonalizadoService.java`, dentro do loop de `salvar`, dois ajustes: (a) gravar
`r.mapeamento()` num campo novo; (b) zerar o mapeamento existente se a estrutura mudou (tipo ou
opções diferentes do que estavam salvos — "editou estrutura, perde o atalho"):

```java
            boolean mapeamentoAnterior = campo.getMapeamento() != null;
            boolean estruturaMudou = mapeamentoAnterior
                    && (campo.getTipo() != r.tipo() || !campo.getOpcoesComoLista().equals(r.opcoes() == null ? List.of() : r.opcoes()));

            campo.setLabel(r.label());
            campo.setPlaceholder(r.placeholder());
            campo.setTipo(r.tipo());
            campo.setOpcoesComoLista(r.opcoes());
            campo.setObrigatorio(r.obrigatorio());
            campo.setVisivelAoPublico(r.visivelAoPublico());
            campo.setOrdem(r.ordem());
            campo.setMapeamento(estruturaMudou ? null : r.mapeamento());
```

> Isso substitui as 6 linhas de `campo.set...` já existentes no loop — mesma posição, só
> acrescentando a linha de `mapeamento` no fim e o cálculo de `estruturaMudou` antes.

- [ ] **Step 9: Escrever o teste de `estruturaMudou` zera o mapeamento**

Adicionar ao `CampoPersonalizadoServiceTest.java` existente:

```java
    @Test
    void salvarZeraMapeamentoQuandoTipoMuda() {
        var existenteId = UUID.randomUUID();
        var existente = CampoPersonalizadoEvento.builder()
                .id(existenteId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.IDADE).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));
        when(campoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var requestComTipoDiferente = new CampoPersonalizadoRequest(
                existenteId, "Idade", null, TipoCampoPersonalizado.SIM_NAO, null, false, true, 0,
                MapeamentoCampoPersonalizado.IDADE);

        var resultado = service.salvar(eventoId, igrejaId, List.of(requestComTipoDiferente), UUID.randomUUID());

        assertThat(resultado.get(0).mapeamento()).isNull();
    }

    @Test
    void salvarMantemMapeamentoQuandoSoRotuloMuda() {
        var existenteId = UUID.randomUUID();
        var existente = CampoPersonalizadoEvento.builder()
                .id(existenteId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.IDADE).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));
        when(campoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var requestSoRotulo = new CampoPersonalizadoRequest(
                existenteId, "Quantos anos você tem?", null, TipoCampoPersonalizado.TEXTO_CURTO,
                null, false, true, 0, MapeamentoCampoPersonalizado.IDADE);

        var resultado = service.salvar(eventoId, igrejaId, List.of(requestSoRotulo), UUID.randomUUID());

        assertThat(resultado.get(0).mapeamento()).isEqualTo(MapeamentoCampoPersonalizado.IDADE);
    }
```

- [ ] **Step 10: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: PASS

- [ ] **Step 11: Rodar a suíte completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/db/migration/V27__mapeamento_campo_personalizado.sql \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/MapeamentoCampoPersonalizado.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEvento.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequest.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoResponse.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequestTest.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java
git commit -m "feat(evento): mapeamento de campo personalizado pra dado de Pessoa"
```

---

### Task 5: `CampoPersonalizadoService` — pular pergunta mapeada + `responderComoConvidado`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java`

**Interfaces:**
- Consumes: `Pessoa.getDataNascimento()/getEstadoCivil()/getSexo()/getEndereco()` (já existem).
- Produces: `CampoPersonalizadoService.listarParaResponder(UUID eventoId, UUID igrejaId, Pessoa
  pessoaOuNull): List<CampoPersonalizadoResponse>`; `CampoPersonalizadoService
  .responderComoConvidado(UUID inscricaoId, List<RespostaRequest> respostas, UUID igrejaId):
  void`.

- [ ] **Step 1: Escrever os testes de `listarParaResponder` (falhando)**

Adicionar ao `CampoPersonalizadoServiceTest.java`:

```java
    @Test
    void listarParaResponderOmiteCampoMapeadoQuandoPessoaJaTemODado() {
        var campoIdade = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.IDADE).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoIdade));

        Pessoa pessoaComData = Pessoa.builder().id(UUID.randomUUID())
                .dataNascimento(java.time.LocalDate.of(2000, 1, 1)).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, pessoaComData);

        assertThat(resultado).isEmpty();
    }

    @Test
    void listarParaResponderMostraCampoMapeadoQuandoPessoaNaoTemODado() {
        var campoEstadoCivil = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Estado civil").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .mapeamento(MapeamentoCampoPersonalizado.ESTADO_CIVIL).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoEstadoCivil));

        Pessoa pessoaSemEstadoCivil = Pessoa.builder().id(UUID.randomUUID()).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, pessoaSemEstadoCivil);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void listarParaResponderSempreMostraCamposMapeadosParaConvidadoSemPessoa() {
        var campoSexo = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Sexo").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .mapeamento(MapeamentoCampoPersonalizado.SEXO).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoSexo));

        var resultado = service.listarParaResponder(eventoId, igrejaId, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void listarParaResponderPulaEnderecoSeQualquerParteExiste() {
        var campoEndereco = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Endereço").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.ENDERECO).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoEndereco));

        var enderecoSoComCidade = com.domus.api.shared.dominio.Endereco.builder().cidade("Recife").build();
        Pessoa pessoaComCidade = Pessoa.builder().id(UUID.randomUUID()).endereco(enderecoSoComCidade).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, pessoaComCidade);

        assertThat(resultado).isEmpty();
    }

    @Test
    void listarParaResponderMostraCampoNaoMapeadoSempre() {
        var campoLivre = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Tamanho da camiseta").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .build(); // sem mapeamento
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoLivre));

        Pessoa qualquerPessoa = Pessoa.builder().id(UUID.randomUUID()).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, qualquerPessoa);

        assertThat(resultado).hasSize(1);
    }
```

- [ ] **Step 2: Rodar e ver falhar (método não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: FAIL — `cannot find symbol listarParaResponder`

- [ ] **Step 3: Implementar `listarParaResponder` e o helper de mapeamento**

Adicionar em `CampoPersonalizadoService.java`, logo depois de `listar`:

```java
    /** Só os campos que ainda precisam de resposta — pula os mapeados que a Pessoa já tem.
     *  {@code pessoaOuNull} nulo (convidado sem cadastro) nunca pula nenhum. */
    public List<CampoPersonalizadoResponse> listarParaResponder(UUID eventoId, UUID igrejaId, com.domus.api.modules.pessoa.Pessoa pessoaOuNull) {
        return campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId).stream()
                .filter(c -> valorJaConhecido(c.getMapeamento(), pessoaOuNull).isEmpty())
                .map(CampoPersonalizadoResponse::from)
                .toList();
    }

    private java.util.Optional<String> valorJaConhecido(MapeamentoCampoPersonalizado mapeamento,
                                                          com.domus.api.modules.pessoa.Pessoa pessoa) {
        if (pessoa == null || mapeamento == null) return java.util.Optional.empty();
        return switch (mapeamento) {
            case IDADE -> java.util.Optional.ofNullable(pessoa.getDataNascimento())
                    .map(d -> String.valueOf(java.time.Period.between(d, java.time.LocalDate.now()).getYears()));
            case ESTADO_CIVIL -> java.util.Optional.ofNullable(pessoa.getEstadoCivil()).map(Enum::name);
            case SEXO -> java.util.Optional.ofNullable(pessoa.getSexo()).map(Enum::name);
            case ENDERECO -> temAlgumDadoDeEndereco(pessoa.getEndereco())
                    ? java.util.Optional.of(formatarEndereco(pessoa.getEndereco())) : java.util.Optional.empty();
        };
    }

    private boolean temAlgumDadoDeEndereco(com.domus.api.shared.dominio.Endereco e) {
        if (e == null) return false;
        return e.getCep() != null || e.getLogradouro() != null || e.getNumero() != null
                || e.getComplemento() != null || e.getBairro() != null || e.getCidade() != null || e.getUf() != null;
    }

    private String formatarEndereco(com.domus.api.shared.dominio.Endereco e) {
        StringBuilder sb = new StringBuilder();
        if (e.getLogradouro() != null) sb.append(e.getLogradouro());
        if (e.getNumero() != null) sb.append(", ").append(e.getNumero());
        if (e.getBairro() != null) sb.append(" - ").append(e.getBairro());
        if (e.getCidade() != null) sb.append(", ").append(e.getCidade());
        if (e.getUf() != null) sb.append("/").append(e.getUf());
        return sb.toString();
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: PASS

- [ ] **Step 5: Escrever o teste de resposta automática ao pular campo mapeado**

```java
    @Test
    void respostaAutomaticaEhCriadaQuandoCampoMapeadoEPulado() {
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID campoIdadeId = UUID.randomUUID();

        Pessoa pessoa = Pessoa.builder().id(pessoaId)
                .dataNascimento(java.time.LocalDate.now().minusYears(20)).build();
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        var campoIdade = CampoPersonalizadoEvento.builder()
                .id(campoIdadeId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .obrigatorio(true).mapeamento(MapeamentoCampoPersonalizado.IDADE).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoIdade));
        when(respostaRepository.findByCampoIdAndInscricaoIdAndAcompanhanteId(campoIdadeId, inscricaoId, null))
                .thenReturn(Optional.empty());

        // Responde sem incluir o campo mapeado — o service preenche sozinho a partir de Pessoa.
        service.responder(inscricaoId, null, List.of(), igrejaId, pessoaId, "ACESSO_COMUM");

        verify(respostaRepository).save(argThat(r -> r.getValor().equals("20") && r.getAcompanhante() == null));
    }
```

- [ ] **Step 6: Rodar e ver falhar (obrigatoriedade bloqueia — resposta automática ainda não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: FAIL — `CAMPO_OBRIGATORIO_PENDENTE` (o `responder()` atual não sabe preencher sozinho)

- [ ] **Step 7: Atualizar `responder()` pra resolver mapeamento antes de validar obrigatoriedade**

Em `CampoPersonalizadoService.responder`, logo depois de carregar `campos` (antes do loop de
`valoresEnviados`), preencher automaticamente os campos mapeados que a Pessoa já tem — só
quando o dono é o titular (`acompanhanteId == null` e existe `inscricao.getPessoa()`; convidado
sem cadastro e acompanhante nunca têm Pessoa pra checar):

```java
        Map<UUID, String> valoresEnviados = new HashMap<>();
        for (var r : respostas) valoresEnviados.put(r.campoId(), r.valor());

        if (acompanhanteId == null && inscricao.getPessoa() != null) {
            for (CampoPersonalizadoEvento campo : campos) {
                if (campo.getMapeamento() == null || valoresEnviados.containsKey(campo.getId())) continue;
                valorJaConhecido(campo.getMapeamento(), inscricao.getPessoa())
                        .ifPresent(valor -> valoresEnviados.put(campo.getId(), valor));
            }
        }
```

Isso substitui só o bloco `Map<UUID, String> valoresEnviados = new HashMap<>(); for (var r :
respostas) valoresEnviados.put(...)` já existente — o resto do método (validação de
obrigatoriedade e upsert) já funciona sem mudança, porque agora lê de `valoresEnviados`
enriquecido.

- [ ] **Step 8: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: PASS

- [ ] **Step 9: Escrever os testes de `responderComoConvidado` (falhando)**

```java
    @Test
    void responderComoConvidadoNaoExigeDonoNemGestor() {
        UUID inscricaoId = UUID.randomUUID();
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).nomeConvidado("Maria de Fora").build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());

        service.responderComoConvidado(inscricaoId, List.of(), igrejaId);

        // Não lança SEM_PERMISSAO — chegou até o fim sem exceção de autorização.
    }

    @Test
    void responderComoConvidadoAindaValidaObrigatoriedade() {
        UUID inscricaoId = UUID.randomUUID();
        UUID campoObrigatorioId = UUID.randomUUID();
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).nomeConvidado("Maria de Fora").build();
        var campoObrigatorio = CampoPersonalizadoEvento.builder()
                .id(campoObrigatorioId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Tamanho da camiseta").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .obrigatorio(true).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoObrigatorio));

        assertThatThrownBy(() -> service.responderComoConvidado(inscricaoId, List.of(), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);
    }
```

- [ ] **Step 10: Rodar e ver falhar (método não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: FAIL — `cannot find symbol responderComoConvidado`

- [ ] **Step 11: Extrair a lógica compartilhada e implementar `responderComoConvidado`**

Substituir o corpo de `responder()` por uma chamada ao método privado compartilhado, e
adicionar `responderComoConvidado`:

```java
    /** Titular responde quando {@code acompanhanteId == null}; senão, responde por esse
     *  acompanhante específico. Valida obrigatoriedade aqui — nunca em inscrever(). */
    @Transactional
    public void responder(UUID inscricaoId, UUID acompanhanteId,
                          List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                          UUID igrejaId, UUID pessoaLogadaId, String role) {
        var inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        boolean ehDono = (inscricao.getPessoa() != null
                        && java.util.Objects.equals(inscricao.getPessoa().getId(), pessoaLogadaId))
                || (inscricao.getConvidadoPor() != null
                        && java.util.Objects.equals(inscricao.getConvidadoPor().getId(), pessoaLogadaId));
        if (!ehDono && !com.domus.api.shared.security.Permissoes.podeGerenciarEventos(role)) {
            throw new com.domus.api.shared.exception.BusinessException(
                    "SEM_PERMISSAO", "Você não pode responder por essa inscrição.");
        }

        validarEResponder(inscricao, acompanhanteId, respostas, igrejaId);
    }

    /** Variante sem autor logado, usada só pelo fluxo de convite público (entrar sem conta): a
     *  posse do token — já validado antes de chegar aqui — É a autorização. Responde sempre
     *  como titular da inscrição recém-criada ({@code acompanhanteId} sempre null). */
    @Transactional
    public void responderComoConvidado(UUID inscricaoId,
                                        List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                                        UUID igrejaId) {
        var inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        validarEResponder(inscricao, null, respostas, igrejaId);
    }

    private void validarEResponder(com.domus.api.modules.evento.inscricao.InscricaoEvento inscricao,
                                    UUID acompanhanteId,
                                    List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                                    UUID igrejaId) {
        List<CampoPersonalizadoEvento> campos = campoRepository
                .findByEventoIdAndIgrejaIdOrderByOrdemAsc(inscricao.getEvento().getId(), igrejaId);

        Map<UUID, String> valoresEnviados = new HashMap<>();
        for (var r : respostas) valoresEnviados.put(r.campoId(), r.valor());

        if (acompanhanteId == null && inscricao.getPessoa() != null) {
            for (CampoPersonalizadoEvento campo : campos) {
                if (campo.getMapeamento() == null || valoresEnviados.containsKey(campo.getId())) continue;
                valorJaConhecido(campo.getMapeamento(), inscricao.getPessoa())
                        .ifPresent(valor -> valoresEnviados.put(campo.getId(), valor));
            }
        }

        for (CampoPersonalizadoEvento campo : campos) {
            if (!campo.isObrigatorio()) continue;
            String valor = valoresEnviados.get(campo.getId());
            boolean respondidoAgora = valor != null && !valor.isBlank();
            boolean jaRespondidoAntes = !respondidoAgora && respostaRepository
                    .findByCampoIdAndInscricaoIdAndAcompanhanteId(campo.getId(), inscricao.getId(), acompanhanteId)
                    .map(r -> r.getValor() != null && !r.getValor().isBlank())
                    .orElse(false);
            if (!respondidoAgora && !jaRespondidoAntes) {
                throw new com.domus.api.shared.exception.BusinessException(
                        "CAMPO_OBRIGATORIO_PENDENTE", "\"" + campo.getLabel() + "\" é obrigatório.");
            }
        }

        for (var entry : valoresEnviados.entrySet()) {
            CampoPersonalizadoEvento campo = campos.stream()
                    .filter(c -> c.getId().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Campo não encontrado."));

            var existente = respostaRepository
                    .findByCampoIdAndInscricaoIdAndAcompanhanteId(entry.getKey(), inscricao.getId(), acompanhanteId);

            RespostaCampoPersonalizado resposta = existente.orElseGet(() -> {
                var nova = RespostaCampoPersonalizado.builder().campo(campo).inscricao(inscricao).build();
                if (acompanhanteId != null) {
                    var achado = inscricao.getAcompanhantes().stream()
                            .filter(a -> a.getId().equals(acompanhanteId)).findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Acompanhante não encontrado."));
                    nova.setAcompanhante(achado);
                }
                return nova;
            });
            resposta.setValor(entry.getValue());
            respostaRepository.save(resposta);
        }
    }
```

> **Atenção**: o loop final mudou de `for (var r : respostas)` pra `for (var entry :
> valoresEnviados.entrySet())` — isso é intencional: `valoresEnviados` agora inclui tanto o que
> veio no request quanto o que foi preenchido automaticamente por mapeamento, então salvar a
> partir dele (não de `respostas` direto) é o que garante a "resposta automática" do Step 5.

- [ ] **Step 12: Rodar todos os testes de `CampoPersonalizadoServiceTest` e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: PASS (todos, inclusive os já existentes da Spec 1 — nenhuma regressão)

- [ ] **Step 13: Rodar a suíte completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java
git commit -m "feat(evento): listarParaResponder pula campo mapeado e responderComoConvidado"
```

---

### Task 6: `ConviteService` — token no Redis

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/convite/ConviteService.java`
- Create: `src/main/java/com/domus/api/modules/evento/convite/DTOs/ConvitePublicoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/convite/ConviteServiceTest.java`

**Interfaces:**
- Consumes: `EventoRepository.findByIdAndIgrejaId` (já existe), `InscricaoRepository
  .findByEventoIdAndPessoaId` (já existe), `StringRedisTemplate` (já configurado no projeto,
  mesmo bean usado por `PasswordResetService`).
- Produces: `ConviteService.gerarToken(UUID eventoId, UUID pessoaId, UUID igrejaId): String`;
  `ConviteService.resolverInscricaoConvidante(String token): InscricaoEvento` (lança
  `BusinessException("CONVITE_INVALIDO", ...)` se não encontrar/expirado, e
  `BusinessException("EVENTO_ENCERRADO", ...)` se o evento já passou).

- [ ] **Step 1: Escrever os testes (falhando)**

```java
package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConviteServiceTest {

    StringRedisTemplate redisTemplate;
    ValueOperations<String, String> valueOps;
    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    ConviteService service;

    UUID igrejaId;
    UUID eventoId;
    UUID pessoaId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        service = new ConviteService(redisTemplate, eventoRepository, inscricaoRepository);

        igrejaId = UUID.randomUUID();
        eventoId = UUID.randomUUID();
        pessoaId = UUID.randomUUID();
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento eventoComFim(LocalDateTime fimEm) {
        return Evento.builder().id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(fimEm.minusHours(2)).fimEm(fimEm).requerInscricao(true).build();
    }

    @Test
    void gerarTokenGravaNoRedisComTtlAteFimDoEvento() {
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        UUID inscricaoId = UUID.randomUUID();
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CONFIRMADA).build();

        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.of(inscricao));

        String token = service.gerarToken(eventoId, pessoaId, igrejaId);

        assertThat(token).isNotBlank();
        verify(valueOps).set(eq("convite:" + token), eq(inscricaoId.toString()), any(Duration.class));
    }

    @Test
    void gerarTokenLancaNotFoundQuandoNaoInscrito() {
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.gerarToken(eventoId, pessoaId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolverInscricaoConvidanteDevolveInscricaoQuandoValido() {
        UUID inscricaoId = UUID.randomUUID();
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CONFIRMADA).build();

        when(valueOps.get("convite:abc")).thenReturn(inscricaoId.toString());
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        InscricaoEvento resolvida = service.resolverInscricaoConvidante("abc");

        assertThat(resolvida.getId()).isEqualTo(inscricaoId);
    }

    @Test
    void resolverInscricaoConvidanteLancaInvalidoQuandoTokenNaoExiste() {
        when(valueOps.get("convite:abc")).thenReturn(null);

        assertThatThrownBy(() -> service.resolverInscricaoConvidante("abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "CONVITE_INVALIDO");
    }

    @Test
    void resolverInscricaoConvidanteLancaEncerradoQuandoEventoJaAconteceu() {
        UUID inscricaoId = UUID.randomUUID();
        Evento evento = eventoComFim(LocalDateTime.now().minusDays(1));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CONFIRMADA).build();

        when(valueOps.get("convite:abc")).thenReturn(inscricaoId.toString());
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.resolverInscricaoConvidante("abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "EVENTO_ENCERRADO");
    }

    @Test
    void resolverInscricaoConvidanteLancaInvalidoQuandoInscricaoFoiCancelada() {
        UUID inscricaoId = UUID.randomUUID();
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CANCELADA).build();

        when(valueOps.get("convite:abc")).thenReturn(inscricaoId.toString());
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.resolverInscricaoConvidante("abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "CONVITE_INVALIDO");
    }
}
```

> Conferir se `BusinessException` expõe um getter `getCodigo()` (usado aqui via
> `hasFieldOrPropertyWithValue("codigo", ...)`) — se o campo tiver outro nome, ajustar a
> asserção pra bater com a classe real (`grep -n "class BusinessException" -A 15
> src/main/java/com/domus/api/shared/exception/BusinessException.java` antes de escrever).

- [ ] **Step 2: Rodar e ver falhar (classe não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=ConviteServiceTest`
Expected: FAIL — `cannot find symbol ConviteService`

- [ ] **Step 3: Implementar `ConviteService`**

```java
package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConviteService {

    private static final String PREFIXO = "convite:";
    /** Margem quando o evento não tem fim declarado (usa início + margem como referência de encerramento/TTL). */
    private static final Duration MARGEM_SEM_FIM = Duration.ofHours(6);
    private static final Duration TTL_MINIMO = Duration.ofHours(1);
    private final SecureRandom secureRandom = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;

    /** Gera um token novo (não idempotente) pra inscrição de {@code pessoaId} no evento. */
    public String gerarToken(UUID eventoId, UUID pessoaId, UUID igrejaId) {
        InscricaoEvento inscricao = inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(i -> i.getIgreja().getId().equals(igrejaId))
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        String token = gerarTokenAleatorio();
        Duration ttl = calcularTtl(inscricao.getEvento());
        redisTemplate.opsForValue().set(chave(token), inscricao.getId().toString(), ttl);
        return token;
    }

    /** Resolve o token e devolve a inscrição de quem convidou — valida existência/expiração
     *  (CONVITE_INVALIDO), inscrição cancelada (CONVITE_INVALIDO) e evento já encerrado
     *  (EVENTO_ENCERRADO). */
    public InscricaoEvento resolverInscricaoConvidante(String token) {
        String inscricaoIdTexto = redisTemplate.opsForValue().get(chave(token));
        if (inscricaoIdTexto == null) {
            throw new BusinessException("CONVITE_INVALIDO", "Este convite não é mais válido.");
        }

        InscricaoEvento inscricao = inscricaoRepository.findById(UUID.fromString(inscricaoIdTexto))
                .orElseThrow(() -> new BusinessException("CONVITE_INVALIDO", "Este convite não é mais válido."));

        if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) {
            throw new BusinessException("CONVITE_INVALIDO", "Este convite não é mais válido.");
        }
        if (inscricao.getEvento().getSituacao() == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO", "Este evento já aconteceu.");
        }

        return inscricao;
    }

    private Duration calcularTtl(Evento evento) {
        LocalDateTime referencia = evento.getFimEm() != null
                ? evento.getFimEm()
                : evento.getInicioEm().plus(MARGEM_SEM_FIM);
        Duration ate = Duration.between(LocalDateTime.now(), referencia);
        return ate.compareTo(TTL_MINIMO) > 0 ? ate : TTL_MINIMO;
    }

    private String gerarTokenAleatorio() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String chave(String token) {
        return PREFIXO + token;
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=ConviteServiceTest`
Expected: PASS

- [ ] **Step 5: Criar o DTO de resposta pública**

```java
package com.domus.api.modules.evento.convite.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Devolvido por GET /convites/{token} — NUNCA inclui lista de inscritos, e-mail/telefone de
 *  quem convidou, nem qualquer outra Pessoa além do convidante. */
public record ConvitePublicoResponse(
        UUID eventoId,
        String titulo,
        String descricao,
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        String localNome,
        String localEndereco,
        UUID fotoId,
        String igrejaNome,
        UUID igrejaLogoFotoId,
        String convidadoPorNome,
        UUID convidadoPorFotoId,
        Integer vagasRestantes,
        BigDecimal preco,
        List<CampoPersonalizadoResponse> campos
) {}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/convite/ConviteService.java \
  src/main/java/com/domus/api/modules/evento/convite/DTOs/ConvitePublicoResponse.java \
  src/test/java/com/domus/api/modules/evento/convite/ConviteServiceTest.java
git commit -m "feat(evento): ConviteService com token opaco no Redis"
```

---

### Task 7: `ConviteController` — gerar, consultar e entrar

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/convite/DTOs/GerarConviteResponse.java`
- Create: `src/main/java/com/domus/api/modules/evento/convite/DTOs/EntrarConviteRequest.java`
- Create: `src/main/java/com/domus/api/modules/evento/convite/ConviteController.java`
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java`
- Modify: `src/main/java/com/domus/api/shared/security/RateLimitFilter.java`
- Test: `src/test/java/com/domus/api/modules/evento/convite/ConviteControllerTest.java`

**Interfaces:**
- Consumes: `ConviteService` (Task 6), `InscricaoService.inscreverConvidado` (Task 2),
  `CampoPersonalizadoService.listarParaResponder`/`responderComoConvidado` (Task 5).
- Produces: `POST /eventos/{eventoId}/inscricoes/minha/convite`, `GET /convites/{token}`,
  `POST /convites/{token}/entrar`.

- [ ] **Step 1: Criar os DTOs**

```java
package com.domus.api.modules.evento.convite.DTOs;

public record GerarConviteResponse(String token, String link) {}
```

```java
package com.domus.api.modules.evento.convite.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EntrarConviteRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String nome,
        @Size(max = 20, message = "Máximo 20 caracteres.")
        String telefone,
        @Valid
        List<RespostaRequest> respostas
) {}
```

- [ ] **Step 2: Criar o controller**

```java
package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoService;
import com.domus.api.modules.evento.convite.DTOs.ConvitePublicoResponse;
import com.domus.api.modules.evento.convite.DTOs.EntrarConviteRequest;
import com.domus.api.modules.evento.convite.DTOs.GerarConviteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ConvidadoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.DTOs.LocalEventoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ConviteController {

    private final ConviteService conviteService;
    private final InscricaoService inscricaoService;
    private final CampoPersonalizadoService campoPersonalizadoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/eventos/{eventoId}/inscricoes/minha/convite")
    public ResponseEntity<GerarConviteResponse> gerarConvite(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        String token = conviteService.gerarToken(eventoId, usuario.getPessoa().getId(), usuario.getIgreja().getId());
        return ResponseEntity.ok(new GerarConviteResponse(token, frontendUrl + "/convite/" + token));
    }

    @GetMapping("/convites/{token}")
    public ResponseEntity<ConvitePublicoResponse> consultar(@PathVariable String token) {
        InscricaoEvento inscricaoConvidante = conviteService.resolverInscricaoConvidante(token);
        var evento = inscricaoConvidante.getEvento();
        var convidante = inscricaoConvidante.getPessoa();

        String localNome = null;
        String localEndereco = null;
        if (evento.getLocal() != null) {
            LocalEvento local = evento.getLocal();
            LocalEventoResponse localResp = LocalEventoResponse.from(local);
            localNome = local.getNome();
            localEndereco = localResp.endereco();
        } else if (evento.getLocalTexto() != null) {
            localNome = evento.getLocalTexto();
        }

        Integer vagasRestantes = evento.getVagas() == null ? null
                : Math.max(0, evento.getVagas() - (int) inscricaoService.contarPessoasConfirmadas(evento.getId()));

        var campos = campoPersonalizadoService.listarParaResponder(evento.getId(), evento.getIgreja().getId(), null);

        return ResponseEntity.ok(new ConvitePublicoResponse(
                evento.getId(), evento.getTitulo(), evento.getDescricao(),
                evento.getInicioEm(), evento.getFimEm(),
                localNome, localEndereco,
                evento.getFoto() != null ? evento.getFoto().getId() : null,
                evento.getIgreja().getNome(),
                evento.getIgreja().getLogoFoto() != null ? evento.getIgreja().getLogoFoto().getId() : null,
                convidante != null ? convidante.getNome() : null,
                convidante != null && convidante.getFoto() != null ? convidante.getFoto().getId() : null,
                vagasRestantes, evento.getPreco(), campos
        ));
    }

    @PostMapping("/convites/{token}/entrar")
    public ResponseEntity<ConvidadoResponse> entrar(@PathVariable String token,
                                                      @Valid @RequestBody EntrarConviteRequest data) {
        InscricaoEvento inscricaoConvidante = conviteService.resolverInscricaoConvidante(token);
        var evento = inscricaoConvidante.getEvento();
        UUID convidadoPorPessoaId = inscricaoConvidante.getPessoa() != null
                ? inscricaoConvidante.getPessoa().getId() : null;

        var inscricao = inscricaoService.inscreverConvidado(
                evento.getId(), evento.getIgreja().getId(), data.nome(), data.telefone(), convidadoPorPessoaId);

        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responderComoConvidado(
                    inscricao.getId(), data.respostas(), evento.getIgreja().getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ConvidadoResponse.from(inscricao));
    }
}
```

> `inscreverConvidado` (Task 2) resolve `convidadoPor` a partir de `convidadoPorPessoaId` via
> `membroRepository.findByIdAndIgrejaId` — como o id vem da própria `inscricaoConvidante`
> (já resolvida e confiável), não precisa de checagem extra aqui.

- [ ] **Step 3: Adicionar as rotas no `SecurityConfig` e isentar `/convites/**` de CSRF**

```java
                        .requestMatchers("/convites/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes/minha/convite")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
```

Adicionar `/convites/**` no bloco `.permitAll()` já existente (junto com `/auth/login` etc.), e
o matcher de `POST .../minha/convite` **antes** do matcher genérico `/eventos/**` (mesma regra
de ordem da Task 3).

`permitAll()` só afasta a exigência de **autenticação** — não afasta CSRF, que é checado por um
filtro separado (`CsrfFilter`, configurado no bloco `.csrf(...)` no topo de
`securityFilterChain`). `POST /convites/{token}/entrar` é chamado por um estranho que nunca
visitou o Domus antes (abriu o link direto do WhatsApp) — não faz sentido exigir o cookie
`XSRF-TOKEN` dele, porque CSRF protege **sessões autenticadas** de serem forjadas por outro
site, e aqui não existe sessão nenhuma pra forjar. Isentar explicitamente, no mesmo bloco
`.csrf(...)`:

```java
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        .ignoringRequestMatchers("/convites/**"))
```

Isso substitui a chamada `.csrf(csrf -> csrf.csrfTokenRepository(...).csrfTokenRequestHandler(...))`
já existente (só acrescenta `.ignoringRequestMatchers(...)` na cadeia).

- [ ] **Step 4: Adicionar `/convites/` em `ROTAS_AUTH` no `RateLimitFilter`**

Em `RateLimitFilter.java`, adicionar `"/convites/"` à lista `ROTAS_AUTH` (o match é por
`startsWith`, então cobre `GET /convites/{token}` e `POST /convites/{token}/entrar`):

```java
    private static final List<String> ROTAS_AUTH = List.of(
            "/auth/login",
            "/auth/google/login",
            "/auth/google/registrar",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/igrejas/registrar",
            "/igrejas-vinculadas/entrar",
            "/convites/"
    );
```

- [ ] **Step 5: Escrever o teste de integração**

```java
package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConviteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired StringRedisTemplate redisTemplate;

    Igreja igreja;
    Pessoa convidante;
    Evento evento;
    InscricaoEvento inscricaoConvidante;

    @BeforeEach
    void setup() {
        igreja = new Igreja();
        igreja.setNome("Igreja Teste Convite");
        igreja.setEmailContato("convite@teste.com");
        igreja = igrejaRepository.save(igreja);

        convidante = pessoaRepository.save(Pessoa.builder().igreja(igreja).nome("Ana Convidante")
                .email("ana@teste.com").vinculo(Vinculo.MEMBRO).build());

        evento = eventoRepository.save(Evento.builder().igreja(igreja).titulo("Culto de Jovens")
                .inicioEm(LocalDateTime.now().plusDays(3)).requerInscricao(true).build());

        inscricaoConvidante = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(convidante).status(StatusInscricao.CONFIRMADA).build());

        redisTemplate.opsForValue().set("convite:token-teste", inscricaoConvidante.getId().toString());
    }

    @Test
    void getConvitePublicoNaoExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/convites/token-teste"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Culto de Jovens")));
    }

    @Test
    void getConvitePublicoNaoVazaEmailOuTelefoneDoConvidante() throws Exception {
        mockMvc.perform(get("/convites/token-teste"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ana@teste.com"))));
    }

    @Test
    void getConviteComTokenInvalidoDevolve404() throws Exception {
        mockMvc.perform(get("/convites/nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void entrarComoConvidadoCriaInscricaoEOcupaVaga() throws Exception {
        mockMvc.perform(post("/convites/token-teste/entrar")
                        .contentType("application/json")
                        .content("{\"nome\":\"Maria de Fora\",\"telefone\":\"11999998888\"}"))
                .andExpect(status().isCreated());
    }
}
```

- [ ] **Step 6: Rodar os testes e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=ConviteControllerTest`
Expected: PASS

- [ ] **Step 7: Rodar a suíte completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS

- [ ] **Step 8: Validar manualmente com curl a ordem dos matchers no `SecurityConfig`**

Conforme convenção do projeto (ordem de `requestMatchers` não é coberta por teste unitário):

```bash
# Sem cookie nenhum — deve dar 404, NUNCA 401/403 (senão o permitAll não pegou antes do genérico)
curl -i http://localhost:8080/convites/token-que-nao-existe

# POST sem cookie CSRF nenhum — prova que a isenção funcionou. Deve dar 400/422 (validação do
# corpo) ou 404 (token inválido), NUNCA 403 com corpo mencionando CSRF.
curl -i -X POST http://localhost:8080/convites/token-que-nao-existe/entrar \
  -H "Content-Type: application/json" -d '{"nome":"Teste"}'
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/convite/ \
  src/main/java/com/domus/api/config/SecurityConfig.java \
  src/main/java/com/domus/api/shared/security/RateLimitFilter.java \
  src/test/java/com/domus/api/modules/evento/convite/ConviteControllerTest.java
git commit -m "feat(evento): endpoints publicos de convite (gerar/consultar/entrar)"
```

---

### Task 8: `GET /visitantes/busca-leve`

**Files:**
- Create: `src/main/java/com/domus/api/modules/visitante/DTOs/VisitanteBuscaLeveResponse.java`
- Modify: `src/main/java/com/domus/api/modules/visitante/VisitanteRepository.java`
- Modify: `src/main/java/com/domus/api/modules/visitante/VisitanteController.java`
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java`
- Test: `src/test/java/com/domus/api/modules/visitante/VisitanteControllerTest.java` (já existe
  — adicionar os testes novos nele, é o piloto do harness `AutenticacaoTestSupport` citado no
  `CLAUDE.md`)

**Interfaces:**
- Produces: `GET /visitantes/busca-leve?q=` → `List<VisitanteBuscaLeveResponse>` (`id`, `nome`,
  `telefone`).

- [ ] **Step 1: Criar o DTO**

```java
package com.domus.api.modules.visitante.DTOs;

import com.domus.api.modules.visitante.Visitante;
import java.util.UUID;

public record VisitanteBuscaLeveResponse(UUID id, String nome, String telefone) {
    public static VisitanteBuscaLeveResponse from(Visitante v) {
        return new VisitanteBuscaLeveResponse(v.getId(), v.getNome(), v.getTelefone());
    }
}
```

- [ ] **Step 2: Adicionar a query no repositório**

Em `VisitanteRepository.java`:

```java
    @org.springframework.data.jpa.repository.Query("""
        SELECT v FROM Visitante v
        WHERE v.igreja.id = :igrejaId
          AND (CAST(:q AS string) IS NULL OR LOWER(v.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY v.nome ASC
        """)
    List<Visitante> buscaLeve(@org.springframework.data.repository.query.Param("igrejaId") UUID igrejaId,
                              @org.springframework.data.repository.query.Param("q") String q,
                              org.springframework.data.domain.Pageable pageable);
```

- [ ] **Step 3: Adicionar o endpoint no controller**

Em `VisitanteController.java`, endpoint novo — **sem** `exigirGestao()` (diferente do `listar()`
completo): qualquer perfil autenticado que possa abrir o modal de inscrever evento pode buscar.

```java
    @GetMapping("/busca-leve")
    public ResponseEntity<List<VisitanteBuscaLeveResponse>> buscaLeve(
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String q,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        String filtro = (q == null || q.isBlank()) ? null : q.trim();
        List<Visitante> visitantes = visitanteRepository.buscaLeve(
                usuarioAutenticado.getIgrejaId(), filtro, pageable);
        return ResponseEntity.ok(visitantes.stream().map(VisitanteBuscaLeveResponse::from).toList());
    }
```

> Precisa injetar `VisitanteRepository` no controller (hoje só `VisitanteService` é injetado) —
> adicionar `private final VisitanteRepository visitanteRepository;` no topo da classe.

- [ ] **Step 4: Adicionar o matcher no `SecurityConfig`**

Antes do matcher `/visitantes/**` genérico (que hoje provavelmente exige ADMIN/LÍDER — conferir
a linha real antes de editar):

```java
                        .requestMatchers(HttpMethod.GET, "/visitantes/busca-leve")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
```

- [ ] **Step 5: Escrever os testes de integração**

Adicionar ao `VisitanteControllerTest.java` existente (segue o padrão já estabelecido nesse
arquivo com `AutenticacaoTestSupport` — usar os mesmos fixtures de `Igreja`/`Usuario` já
montados no `@BeforeEach` daquele arquivo):

```java
    @Test
    void buscaLeveDevolveApenasIdNomeTelefone() throws Exception {
        var visitante = new Visitante();
        visitante.setIgreja(igreja);
        visitante.setNome("Pedro Visitante");
        visitante.setTelefone("11988887777");
        visitante.setObservacoes("Observação sensível que não deve vazar aqui.");
        visitanteRepository.save(visitante);

        var cookie = auth.autenticado(usuarioComum); // ACESSO_COMUM — prova que não exige gestão

        mockMvc.perform(get("/visitantes/busca-leve?q=Pedro")
                        .cookie(cookie.toArray(new jakarta.servlet.http.Cookie[0])))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pedro Visitante")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Observação sensível"))));
    }
```

> Ajustar nomes de fixture (`igreja`, `usuarioComum`, `auth`, `visitanteRepository`) pros que já
> existem de verdade no `@BeforeEach` de `VisitanteControllerTest.java` — abrir o arquivo antes
> de escrever pra confirmar.

- [ ] **Step 6: Rodar os testes e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=VisitanteControllerTest`
Expected: PASS

- [ ] **Step 7: Rodar a suíte completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/visitante/ src/main/java/com/domus/api/config/SecurityConfig.java \
  src/test/java/com/domus/api/modules/visitante/VisitanteControllerTest.java
git commit -m "feat(visitante): endpoint de busca leve pra modal de inscrever evento"
```

---

## Frontend

> A partir daqui, cada task é um pedaço fechado e testável no navegador — entregar, avisar,
> **esperar o autor testar** antes de seguir pra próxima (regra do projeto).

### Task 9: Types e services

**Files:**
- Modify: `src/types/inscricao.type.ts`
- Create: `src/types/convite.type.ts`
- Create: `src/services/convite.service.ts`
- Modify: `src/services/inscricao.service.ts`
- Create: `src/types/visitanteBuscaLeve.type.ts`
- Modify: `src/services/visitante.service.ts` (ou criar, se não existir um service próprio de
  visitante no front hoje — verificar antes)

**Interfaces:**
- Produces: `InscritoResponse`/`ParticipanteResponse` (front) com `convidadoPorNome`;
  `ConvitePublico` type; `gerarConvite(eventoId)`, `consultarConvite(token)`,
  `entrarComoConvidado(token, dados)`; `criarConvidado(eventoId, dados)`;
  `buscarVisitantesLeve(q)`.

- [ ] **Step 1: Atualizar `src/types/inscricao.type.ts`**

Adicionar `convidadoPorNome: string | null` aos types `InscritoResponse` e
`ParticipanteResponse` já existentes (localizar os types no arquivo e adicionar o campo,
espelhando exatamente os DTOs do backend da Task 1). Adicionar também:

```typescript
export interface CriarConvidadoRequest {
  nome: string
  telefone?: string
  respostas?: RespostaRequest[]
}

export interface ConvidadoResponse {
  inscricaoId: string
  nome: string
  telefone: string | null
}
```

> `RespostaRequest` já deve existir no arquivo de types de campos personalizados da Spec 1 —
> importar de lá em vez de duplicar.

- [ ] **Step 2: Criar `src/types/convite.type.ts`**

```typescript
import type { CampoPersonalizadoResponse } from './campoPersonalizado.type'

export interface GerarConviteResponse {
  token: string
  link: string
}

export interface ConvitePublico {
  eventoId: string
  titulo: string
  descricao: string | null
  inicioEm: string
  fimEm: string | null
  localNome: string | null
  localEndereco: string | null
  fotoId: string | null
  igrejaNome: string
  igrejaLogoFotoId: string | null
  convidadoPorNome: string | null
  convidadoPorFotoId: string | null
  vagasRestantes: number | null
  preco: number | null
  campos: CampoPersonalizadoResponse[]
}

export interface EntrarConviteRequest {
  nome: string
  telefone?: string
  respostas?: { campoId: string; valor: string }[]
}
```

> Confirmar o nome real do arquivo de types de campos personalizados da Spec 1
> (`campoPersonalizado.type.ts` é um chute plausível — checar com `find src/types -iname
> "*campo*"` antes de escrever o import).

- [ ] **Step 3: Criar `src/services/convite.service.ts`**

```typescript
import { api } from '@/lib/api'
import type { GerarConviteResponse, ConvitePublico, EntrarConviteRequest } from '@/types/convite.type'
import type { ConvidadoResponse } from '@/types/inscricao.type'

export async function gerarConvite(eventoId: string): Promise<GerarConviteResponse> {
  const { data } = await api.post(`/eventos/${eventoId}/inscricoes/minha/convite`)
  return data
}

export async function consultarConvite(token: string): Promise<ConvitePublico> {
  const { data } = await api.get(`/convites/${token}`)
  return data
}

export async function entrarComoConvidado(token: string, dados: EntrarConviteRequest): Promise<ConvidadoResponse> {
  const { data } = await api.post(`/convites/${token}/entrar`, dados)
  return data
}
```

> Confirmar o import de `api` (cliente HTTP do projeto) olhando um service já existente, ex.:
> `src/services/inscricao.service.ts` — usar o mesmo padrão de import/base URL de lá.

- [ ] **Step 4: Adicionar `criarConvidado` em `src/services/inscricao.service.ts`**

```typescript
export async function criarConvidado(eventoId: string, dados: CriarConvidadoRequest): Promise<ConvidadoResponse> {
  const { data } = await api.post(`/eventos/${eventoId}/inscricoes/convidados`, dados)
  return data
}
```

- [ ] **Step 5: Criar `src/types/visitanteBuscaLeve.type.ts` e o service correspondente**

```typescript
export interface VisitanteBuscaLeve {
  id: string
  nome: string
  telefone: string | null
}
```

```typescript
export async function buscarVisitantesLeve(q: string): Promise<VisitanteBuscaLeve[]> {
  const { data } = await api.get('/visitantes/busca-leve', { params: { q } })
  return data
}
```

> Adicionar essa função no service de visitante já existente se houver um (`find src/services
> -iname "*visitante*"` antes de decidir criar arquivo novo vs. adicionar num existente).

- [ ] **Step 6: Testar manualmente no navegador**

Não há teste automatizado de frontend no projeto (Jest/Playwright não configurados — ver
`CLAUDE.md`). Validar chamando os services no console do navegador (`npm run dev`, abrir
DevTools, `await fetch('/api/convites/token-invalido')` deve devolver 404) ou aguardar a Task
10+ que já consome esses services visualmente.

- [ ] **Step 7: Commit**

```bash
git add src/types/inscricao.type.ts src/types/convite.type.ts src/types/visitanteBuscaLeve.type.ts \
  src/services/convite.service.ts src/services/inscricao.service.ts
git commit -m "feat(evento): types e services de convite publico e convidado"
```

**PARAR AQUI — avisar o autor e esperar ele confirmar antes de seguir pra Task 10.**

---

### Task 10: Modal unificado "Inscrever alguém" — 3 abas

**Files:**
- Create: `src/components/module/eventos/ModalInscreverAlguem.tsx`
- Create: `src/components/module/eventos/ModalInscreverAlguem.module.css`
- Create: `src/hooks/inscricao/useCriarConvidado.ts`
- Create: `src/hooks/visitante/useVisitantesBuscaLeve.ts` (ou pasta equivalente já usada por
  outros hooks de visitante)
- Modify: `src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx`

**Interfaces:**
- Consumes: `usePessoas` (já existe, Task de `ModalInscreverPessoas`), `useVisitantesBuscaLeve`
  (novo, mesmo padrão de `usePessoas` com debounce), `useCriarConvidado` (novo, TanStack
  Query mutation chamando `criarConvidado`), `useInscreverPessoas` (já existe), `useMinhaInscricao`
  (já existe — necessário pra saber se quem está logado já está inscrita).
- Produces: `<ModalInscreverAlguem eventoId tituloEvento exclusivoMembros onClose />`.

- [ ] **Step 1: Criar `useCriarConvidado`**

```typescript
'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { criarConvidado } from '@/services/inscricao.service'
import type { CriarConvidadoRequest } from '@/types/inscricao.type'
import { notificar } from '@/lib/notificar'

export function useCriarConvidado(eventoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dados: CriarConvidadoRequest) => criarConvidado(eventoId, dados),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inscricoes', eventoId] })
      queryClient.invalidateQueries({ queryKey: ['participantes', eventoId] })
    },
    onError: (erro) => notificar.erro(erro),
  })
}
```

> Confirmar o nome exato do helper de notificação (`notificar`/`useNotificar`/outro) e o
> formato de `queryKey` usado pelos hooks vizinhos (`useInscreverPessoas.ts`,
> `useCancelarInscricao.ts`) antes de escrever — copiar o padrão de lá, não inventar um novo.

- [ ] **Step 2: Criar `useVisitantesBuscaLeve`**

```typescript
'use client'

import { useQuery } from '@tanstack/react-query'
import { buscarVisitantesLeve } from '@/services/visitante.service'

export function useVisitantesBuscaLeve(q: string) {
  return useQuery({
    queryKey: ['visitantes-busca-leve', q],
    queryFn: () => buscarVisitantesLeve(q),
    enabled: q.length >= 2,
  })
}
```

- [ ] **Step 3: Criar o modal com as 3 abas**

```tsx
'use client'

import { useState } from 'react'
import { X } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { ModalInscreverPessoas } from './ModalInscreverPessoas'
import { useVisitantesBuscaLeve } from '@/hooks/visitante/useVisitantesBuscaLeve'
import { useCriarConvidado } from '@/hooks/inscricao/useCriarConvidado'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useDebounce } from '@/hooks/useDebounce'
import { formatarTelefone } from '@/lib/masks'
import styles from './ModalInscreverAlguem.module.css'

type Aba = 'pessoas' | 'visitantes' | 'fora'

interface Props {
  eventoId: string
  tituloEvento: string
  exclusivoMembros: boolean
  onClose: () => void
}

interface FormularioConvidado {
  nome: string
  telefone: string
}

export function ModalInscreverAlguem({ eventoId, tituloEvento, exclusivoMembros, onClose }: Props) {
  const [aba, setAba] = useState<Aba>('pessoas')
  const [buscaVisitante, setBuscaVisitante] = useState('')
  const buscaDebounced = useDebounce(buscaVisitante, 300)
  const { data: visitantes = [] } = useVisitantesBuscaLeve(buscaDebounced)
  const [visitanteSelecionado, setVisitanteSelecionado] = useState<{ nome: string; telefone: string | null } | null>(null)

  const { data: minha } = useMinhaInscricao(eventoId)
  const criarConvidado = useCriarConvidado(eventoId)

  const { register, handleSubmit, setValue, formState: { errors } } = useForm<FormularioConvidado>({
    defaultValues: { nome: '', telefone: '' },
  })

  function selecionarVisitante(v: { nome: string; telefone: string | null }) {
    setVisitanteSelecionado(v)
    setValue('nome', v.nome)
    setValue('telefone', v.telefone ?? '')
  }

  function aoConfirmarFora(dados: FormularioConvidado) {
    criarConvidado.mutate({ nome: dados.nome, telefone: dados.telefone || undefined }, {
      onSuccess: () => onClose(),
    })
  }

  return (
    <div className={styles.overlay} onMouseDown={() => !criarConvidado.isPending && onClose()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className={styles.header}>
          <div>
            <h2 className={styles.titulo}>Inscrever alguém</h2>
            <p className={styles.subtitulo}>{tituloEvento}</p>
          </div>
          <button type="button" className={styles.btnFechar} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.abas}>
          <button type="button" className={aba === 'pessoas' ? styles.abaAtiva : styles.aba} onClick={() => setAba('pessoas')}>
            Pessoas da igreja
          </button>
          <button type="button" className={aba === 'visitantes' ? styles.abaAtiva : styles.aba} onClick={() => setAba('visitantes')}>
            Visitantes
          </button>
          <button type="button" className={aba === 'fora' ? styles.abaAtiva : styles.aba} onClick={() => setAba('fora')}>
            Pessoa de fora
          </button>
        </div>

        {aba === 'pessoas' && (
          <ModalInscreverPessoas
            eventoId={eventoId}
            tituloEvento={tituloEvento}
            exclusivoMembros={exclusivoMembros}
            onClose={onClose}
            semOverlayProprio
          />
        )}

        {aba === 'visitantes' && (
          <div className={styles.conteudoAba}>
            <input
              type="text"
              className={styles.buscaInput}
              placeholder="Buscar visitante por nome…"
              value={buscaVisitante}
              onChange={(e) => setBuscaVisitante(e.target.value)}
            />
            <div className={styles.lista}>
              {visitantes.map((v) => (
                <button
                  key={v.id}
                  type="button"
                  className={styles.linhaVisitante}
                  onClick={() => selecionarVisitante(v)}
                >
                  {v.nome} {v.telefone ? `— ${v.telefone}` : ''}
                </button>
              ))}
            </div>
            {visitanteSelecionado && (
              <form className={styles.form} onSubmit={handleSubmit(aoConfirmarFora)}>
                <p className={styles.selecionado}>Selecionado: {visitanteSelecionado.nome}</p>
                <button type="submit" className={styles.btnConfirmar} disabled={criarConvidado.isPending}>
                  {criarConvidado.isPending ? 'Inscrevendo…' : 'Inscrever'}
                </button>
              </form>
            )}
          </div>
        )}

        {aba === 'fora' && (
          <form className={styles.form} onSubmit={handleSubmit(aoConfirmarFora)}>
            <label className={styles.campo}>
              <span>Nome*</span>
              <input
                type="text"
                placeholder="Ex.: Maria Souza"
                {...register('nome', { required: 'O nome é obrigatório.' })}
              />
              {errors.nome && <span className={styles.erro}>{errors.nome.message}</span>}
            </label>
            <label className={styles.campo}>
              <span>Telefone (opcional)</span>
              <input
                type="text"
                placeholder="(00) 00000-0000"
                inputMode="numeric"
                {...register('telefone')}
                onChange={(e) => setValue('telefone', formatarTelefone(e.target.value))}
              />
            </label>
            <button type="submit" className={styles.btnConfirmar} disabled={criarConvidado.isPending}>
              {criarConvidado.isPending ? 'Inscrevendo…' : 'Inscrever'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
```

> **Pontos a resolver na hora de implementar** (não são placeholders — são decisões de UI que
> dependem de olhar o CSS/estado atual do componente antes de travar o código):
> 1. `ModalInscreverPessoas` hoje renderiza seu próprio `overlay` — a prop `semOverlayProprio`
>    acima é um sinal de que esse componente precisa aceitar renderizar só o conteúdo interno
>    quando usado dentro de outra aba (refatorar `ModalInscreverPessoas.tsx` pra extrair o
>    `<div className={styles.overlay}>` externo como opcional, ou o componente pai duplica menos
>    overlay). Resolver isso é parte desta task — o teste manual (Step 6) só passa se não
>    houver dois overlays sobrepostos.
> 2. Este esqueleto ainda não implementa "você também vai participar?" — ver Step 4.
> 3. Este esqueleto ainda não injeta os campos personalizados do evento na aba "fora"/"visitantes"
>    quando o evento tem campos configurados — ver Step 5.

- [ ] **Step 4: Implementar "você também vai participar?"**

Adicionar estado `perguntaParticipar: boolean` que abre um `ModalConfirmacao` (componente já
existente no projeto, usado em `ModalInscreverPessoas.tsx` para o fluxo de "inscrever mesmo
assim") antes de chamar `criarConvidado`, só quando `minha?.inscrito === false`:

```tsx
  const [aguardandoRespostaParticipar, setAguardandoRespostaParticipar] = useState<FormularioConvidado | null>(null)
  const inscrever = useInscrever(eventoId) // hook já existente (POST /eventos/{id}/inscricoes)

  function aoConfirmarFora(dados: FormularioConvidado) {
    if (minha && !minha.inscrito) {
      setAguardandoRespostaParticipar(dados)
      return
    }
    criarConvidado.mutate({ nome: dados.nome, telefone: dados.telefone || undefined }, { onSuccess: () => onClose() })
  }

  function aoResponderTambemVouParticipar(sim: boolean) {
    const dados = aguardandoRespostaParticipar!
    setAguardandoRespostaParticipar(null)
    if (sim) {
      inscrever.mutate(undefined, {
        onSuccess: () => criarConvidado.mutate(
          { nome: dados.nome, telefone: dados.telefone || undefined },
          { onSuccess: () => onClose() },
        ),
      })
    } else {
      criarConvidado.mutate({ nome: dados.nome, telefone: dados.telefone || undefined }, { onSuccess: () => onClose() })
    }
  }
```

E no JSX, depois do `</div>` que fecha `.modal`:

```tsx
      {aguardandoRespostaParticipar && (
        <ModalConfirmacao
          titulo="Você também vai participar?"
          textoConfirmar="Sim, também vou"
          textoCancelar="Não, só estou cadastrando"
          onConfirmar={() => aoResponderTambemVouParticipar(true)}
          onClose={() => aoResponderTambemVouParticipar(false)}
          mensagem={<p>Quer se inscrever nesse evento também, junto com quem você está cadastrando?</p>}
        />
      )}
```

> Confirmar a assinatura exata de `ModalConfirmacao` (props `textoCancelar`/`onClose` podem ter
> nomes diferentes) olhando `src/components/common/ModalConfirmacao/ModalConfirmacao.tsx` antes
> de finalizar — e confirmar/criar o hook `useInscrever` (pode já existir com outro nome, ex.
> dentro de `useMinhaInscricao.ts` ou um hook próprio `useAutoInscrever` — checar
> `src/hooks/inscricao/` antes de criar um novo).

- [ ] **Step 5: Injetar campos personalizados do evento nas abas "visitantes"/"fora"**

Buscar `useCamposPersonalizados(eventoId)` (hook já existente da Spec 1 — usado no builder de
`EventoForm.tsx`) e, se `campos.length > 0`, renderizar os campos (mesmo componente/lógica que
`RespostasCamposPersonalizados.tsx` já usa pra responder) dentro do `<form>` de cada aba, antes
do botão de confirmar — os valores entram no `respostas` do `CriarConvidadoRequest` enviado.

> Esta etapa reaproveita um componente de renderização de campo que já existe da Spec 1
> (`RespostasCamposPersonalizados.tsx` ou equivalente) — abrir esse arquivo antes de escrever
> pra ver se dá pra extrair um sub-componente `<CamposPersonalizadosForm campos respostas
> onChange />` reaproveitável aqui e na página pública (Task 12), em vez de duplicar JSX de
> input por tipo de campo.

- [ ] **Step 6: Trocar os botões em `DrawerDetalheEvento.tsx`**

Substituir o bloco dos dois botões (`'membros'`/`'convidado'`) por um botão só que abre
`ModalInscreverAlguem`:

```tsx
              {evento.requerInscricao && !inscricaoBloqueadaPelaSituacao && (
                <button
                  type="button"
                  className={styles.acaoSecundaria}
                  onClick={() => setModalAberto('inscrever-alguem')}
                  disabled={esgotado}
                >
                  <Users size={16} aria-hidden="true" />
                  {esgotado ? 'Vagas esgotadas' : 'Inscrever alguém'}
                </button>
              )}
```

E o bloco de renderização condicional:

```tsx
            {modalAberto === 'inscrever-alguem' && (
              <ModalInscreverAlguem
                eventoId={evento.id}
                tituloEvento={evento.titulo}
                exclusivoMembros={evento.exclusivoMembros}
                onClose={() => setModalAberto(null)}
              />
            )}
```

`ModalConvidado`/`'convidado'` deixam de ser referenciados aqui (mas o arquivo
`ModalConvidado.tsx` continua existindo, sem ser apagado — ver "Fora do escopo" da spec: o
backend antigo (`AcompanhanteInscricao`) não é tocado, e este componente é o front dele; só não
tem mais botão próprio nesta tela). Atualizar o type de `modalAberto` no topo do arquivo (linha
`useState<'membros' | 'convidado' | 'lista' | null>`) pra `useState<'inscrever-alguem' | 'lista'
| null>`.

- [ ] **Step 7: Rodar o dev server e testar manualmente**

```bash
npm run dev
```

Checklist manual (mobile + desktop, `CLAUDE.md` exige as duas):
- Abrir um evento com `requerInscricao=true`, clicar "Inscrever alguém".
- Aba "Pessoas da igreja": buscar e inscrever um membro (fluxo antigo intacto).
- Aba "Visitantes": buscar um visitante existente, selecionar, confirmar — aparece como
  convidado na lista de inscritos.
- Aba "Pessoa de fora": preencher nome/telefone, confirmar — mesma coisa.
- Repetir os dois últimos casos logado como alguém **ainda não inscrito** no evento — deve
  aparecer a pergunta "você também vai participar?", e testar as duas respostas.
- Testar em evento com campos personalizados configurados — os campos aparecem no formulário.
- Redimensionar pra mobile — abas empilham/rolam sem overflow horizontal.

- [ ] **Step 8: Commit**

```bash
git add src/components/module/eventos/ModalInscreverAlguem.tsx \
  src/components/module/eventos/ModalInscreverAlguem.module.css \
  src/components/module/eventos/ModalInscreverPessoas.tsx \
  src/hooks/inscricao/useCriarConvidado.ts \
  src/hooks/visitante/useVisitantesBuscaLeve.ts \
  src/app/\(app\)/eventos/\(lista\)/\(detalhe\)/DrawerDetalheEvento.tsx
git commit -m "feat(evento): modal unificado Inscrever alguem com 3 abas"
```

**PARAR AQUI — avisar o autor e esperar ele testar antes de seguir pra Task 11.**

---

### Task 11: Compartilhar evento (link)

**Files:**
- Create: `src/components/module/eventos/ModalCompartilharConvite.tsx`
- Create: `src/components/module/eventos/ModalCompartilharConvite.module.css`
- Create: `src/hooks/inscricao/useGerarConvite.ts`
- Modify: `src/components/module/eventos/ModalInscreverAlguem.tsx` (botão "Compartilhar" na
  aba "Pessoa de fora")
- Modify: `src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx` (botão
  "Compartilhar" próprio)

**Interfaces:**
- Consumes: `gerarConvite` (Task 9), `useMinhaInscricao`/`useInscrever` (já existentes/Task 10).
- Produces: `<ModalCompartilharConvite eventoId onClose />`.

- [ ] **Step 1: Criar `useGerarConvite`**

```typescript
'use client'

import { useMutation } from '@tanstack/react-query'
import { gerarConvite } from '@/services/convite.service'
import { notificar } from '@/lib/notificar'

export function useGerarConvite(eventoId: string) {
  return useMutation({
    mutationFn: () => gerarConvite(eventoId),
    onError: (erro) => notificar.erro(erro),
  })
}
```

- [ ] **Step 2: Criar `ModalCompartilharConvite`**

```tsx
'use client'

import { useEffect, useState } from 'react'
import { X, Copy, Check } from 'lucide-react'
import { useGerarConvite } from '@/hooks/inscricao/useGerarConvite'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import styles from './ModalCompartilharConvite.module.css'

interface Props {
  eventoId: string
  onClose: () => void
}

export function ModalCompartilharConvite({ eventoId, onClose }: Props) {
  const { data: minha } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever(eventoId)
  const gerarConvite = useGerarConvite(eventoId)
  const [perguntaParticipar, setPerguntaParticipar] = useState(false)
  const [copiado, setCopiado] = useState(false)

  useEffect(() => {
    if (minha === undefined) return
    if (!minha.inscrito) {
      setPerguntaParticipar(true)
      return
    }
    gerarConvite.mutate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [minha])

  function aoResponderParticipar(sim: boolean) {
    setPerguntaParticipar(false)
    if (sim) {
      inscrever.mutate(undefined, { onSuccess: () => gerarConvite.mutate() })
    } else {
      gerarConvite.mutate()
    }
  }

  function copiarLink() {
    if (!gerarConvite.data) return
    navigator.clipboard.writeText(gerarConvite.data.link)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  function abrirWhatsapp() {
    if (!gerarConvite.data) return
    const texto = encodeURIComponent(`Você foi convidado! ${gerarConvite.data.link}`)
    window.open(`https://wa.me/?text=${texto}`, '_blank')
  }

  if (perguntaParticipar) {
    return (
      <ModalConfirmacao
        titulo="Você também vai participar?"
        textoConfirmar="Sim, também vou"
        textoCancelar="Não, só estou compartilhando"
        onConfirmar={() => aoResponderParticipar(true)}
        onClose={() => aoResponderParticipar(false)}
        mensagem={<p>Quer se inscrever nesse evento também, antes de compartilhar o link?</p>}
      />
    )
  }

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className={styles.header}>
          <h2>Compartilhar evento</h2>
          <button type="button" onClick={onClose} aria-label="Fechar"><X size={20} /></button>
        </div>

        <p className={styles.aviso}>Quem usar este link entra como seu convidado.</p>

        {gerarConvite.isPending && <p>Gerando link…</p>}

        {gerarConvite.data && (
          <>
            <div className={styles.linkBox}>
              <input type="text" readOnly value={gerarConvite.data.link} className={styles.linkInput} />
              <button type="button" onClick={copiarLink} className={styles.btnCopiar}>
                {copiado ? <Check size={16} /> : <Copy size={16} />}
                {copiado ? 'Copiado' : 'Copiar'}
              </button>
            </div>
            <button type="button" onClick={abrirWhatsapp} className={styles.btnWhatsapp}>
              Enviar por WhatsApp
            </button>
          </>
        )}
      </div>
    </div>
  )
}
```

> Confirmar se `useInscrever` (usado também na Task 10) já foi criado nessa task anterior — se
> sim, reaproveitar o mesmo hook, não duplicar.

- [ ] **Step 3: Adicionar o botão "Compartilhar" no drawer e na aba "Pessoa de fora"**

Em `DrawerDetalheEvento.tsx`, ao lado do botão "Inscrever alguém":

```tsx
              <button type="button" className={styles.acaoSecundaria} onClick={() => setModalAberto('compartilhar')}>
                <Share2 size={16} aria-hidden="true" />
                Compartilhar
              </button>
```

```tsx
            {modalAberto === 'compartilhar' && (
              <ModalCompartilharConvite eventoId={evento.id} onClose={() => setModalAberto(null)} />
            )}
```

Em `ModalInscreverAlguem.tsx`, dentro da aba `'fora'`, adicionar um botão secundário "ou
compartilhar link" que abre o mesmo `ModalCompartilharConvite` (estado local próprio do modal).

- [ ] **Step 4: Testar manualmente**

- Compartilhar estando já inscrito: gera direto, sem pergunta.
- Compartilhar sem estar inscrito: pergunta aparece, testar as duas respostas.
- Copiar link e abrir numa aba anônima (sem sessão) — deve cair na página pública (ainda não
  existe até a Task 12; validar só que a URL gerada tem o formato certo por ora).
- Testar em mobile.

- [ ] **Step 5: Commit**

```bash
git add src/components/module/eventos/ModalCompartilharConvite.tsx \
  src/components/module/eventos/ModalCompartilharConvite.module.css \
  src/hooks/inscricao/useGerarConvite.ts \
  src/components/module/eventos/ModalInscreverAlguem.tsx \
  src/app/\(app\)/eventos/\(lista\)/\(detalhe\)/DrawerDetalheEvento.tsx
git commit -m "feat(evento): compartilhar evento por link (copiar/whatsapp)"
```

**PARAR AQUI — avisar o autor e esperar ele testar antes de seguir pra Task 12.**

---

### Task 12: Página pública `/convite/[token]`

**Files:**
- Create: `src/app/convite/[token]/page.tsx`
- Create: `src/app/convite/[token]/ConvitePublico.module.css`
- Create: `src/app/convite/[token]/FormularioConvidado.tsx`
- Create: `src/hooks/convite/useConvitePublico.ts`
- Create: `src/hooks/convite/useEntrarComoConvidado.ts`

**Interfaces:**
- Consumes: `consultarConvite`, `entrarComoConvidado` (Task 9), `GET /auth/me` (já existe,
  usado pelo `authStore`), fluxo de login já existente do projeto.
- Produces: rota pública `/convite/[token]`.

- [ ] **Step 1: Criar os hooks**

```typescript
'use client'

import { useQuery } from '@tanstack/react-query'
import { consultarConvite } from '@/services/convite.service'

export function useConvitePublico(token: string) {
  return useQuery({
    queryKey: ['convite-publico', token],
    queryFn: () => consultarConvite(token),
    retry: false,
  })
}
```

```typescript
'use client'

import { useMutation } from '@tanstack/react-query'
import { entrarComoConvidado } from '@/services/convite.service'
import type { EntrarConviteRequest } from '@/types/convite.type'

export function useEntrarComoConvidado(token: string) {
  return useMutation({
    mutationFn: (dados: EntrarConviteRequest) => entrarComoConvidado(token, dados),
  })
}
```

- [ ] **Step 2: Criar `FormularioConvidado.tsx` (form de identidade + campos personalizados)**

```tsx
'use client'

import { useForm } from 'react-hook-form'
import { useEntrarComoConvidado } from '@/hooks/convite/useEntrarComoConvidado'
import { formatarTelefone } from '@/lib/masks'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import styles from './ConvitePublico.module.css'

interface Props {
  token: string
  campos: CampoPersonalizadoResponse[]
  onSucesso: () => void
}

interface FormularioValores {
  nome: string
  telefone: string
  [campoId: string]: string
}

export function FormularioConvidado({ token, campos, onSucesso }: Props) {
  const entrar = useEntrarComoConvidado(token)
  const { register, handleSubmit, setValue, formState: { errors } } = useForm<FormularioValores>()

  function aoConfirmar(valores: FormularioValores) {
    const { nome, telefone, ...respostasPorId } = valores
    const respostas = campos.map((c) => ({ campoId: c.id, valor: respostasPorId[c.id] ?? '' }))
    entrar.mutate({ nome, telefone: telefone || undefined, respostas }, { onSuccess: onSucesso })
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(aoConfirmar)}>
      <label className={styles.campo}>
        <span>Nome*</span>
        <input type="text" placeholder="Ex.: Maria Souza" {...register('nome', { required: 'O nome é obrigatório.' })} />
        {errors.nome && <span className={styles.erro}>{errors.nome.message}</span>}
      </label>
      <label className={styles.campo}>
        <span>Telefone (opcional)</span>
        <input
          type="text"
          placeholder="(00) 00000-0000"
          inputMode="numeric"
          {...register('telefone')}
          onChange={(e) => setValue('telefone', formatarTelefone(e.target.value))}
        />
      </label>

      {campos.map((c) => (
        <label key={c.id} className={styles.campo}>
          <span>{c.label}{c.obrigatorio ? '*' : ''}</span>
          {c.tipo === 'OPCAO_UNICA' || c.tipo === 'SIM_NAO' ? (
            <select {...register(c.id, { required: c.obrigatorio ? 'Campo obrigatório.' : false })}>
              <option value="">Selecione…</option>
              {(c.tipo === 'SIM_NAO' ? ['Sim', 'Não'] : c.opcoes).map((op) => (
                <option key={op} value={op}>{op}</option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              placeholder={c.placeholder ?? ''}
              {...register(c.id, { required: c.obrigatorio ? 'Campo obrigatório.' : false })}
            />
          )}
        </label>
      ))}

      <button type="submit" className={styles.btnConfirmar} disabled={entrar.isPending}>
        {entrar.isPending ? 'Confirmando…' : 'Confirmar inscrição'}
      </button>
    </form>
  )
}
```

> Tipo `MULTIPLA_ESCOLHA` fica fora deste formulário simples por ora (checkboxes múltiplos com
> serialização `"a | b"`) — se o evento tiver esse tipo de campo, resolver na hora olhando como
> `RespostasCamposPersonalizados.tsx` (Spec 1) já trata esse caso, e replicar aqui em vez de
> deixar quebrado.

- [ ] **Step 3: Criar a página**

```tsx
'use client'

import { use, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useConvitePublico } from '@/hooks/convite/useConvitePublico'
import { useAuthStore } from '@/store/authStore'
import { FormularioConvidado } from './FormularioConvidado'
import { urlFoto } from '@/lib/urlFoto'
import styles from './ConvitePublico.module.css'

export default function ConvitePublicoPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params)
  const { data: convite, isLoading, error } = useConvitePublico(token)
  const usuario = useAuthStore((s) => s.usuario)
  const [etapa, setEtapa] = useState<'landing' | 'escolha' | 'formulario' | 'sucesso'>('landing')

  if (isLoading) return <div className={styles.pagina}><p>Carregando…</p></div>

  if (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    return (
      <div className={styles.pagina}>
        <div className={styles.erroCard}>
          <h1>{status === 410 ? 'Este evento já aconteceu' : 'Convite inválido'}</h1>
          <p>{status === 410
            ? 'O evento pro qual você foi convidado já passou.'
            : 'Este link não é mais válido — peça um novo pra quem te convidou.'}</p>
        </div>
      </div>
    )
  }

  if (!convite) return null

  if (etapa === 'sucesso') {
    return (
      <div className={styles.pagina}>
        <div className={styles.erroCard}>
          <h1>Inscrição confirmada!</h1>
          <p>Você está inscrito em &quot;{convite.titulo}&quot;.</p>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.topo}>
        {convite.igrejaLogoFotoId && (
          <Image src={urlFoto(convite.igrejaLogoFotoId, 'THUMB')!} alt="" width={40} height={40} unoptimized />
        )}
        <span>{convite.igrejaNome}</span>
      </header>

      <div className={styles.hero}>
        {convite.fotoId && (
          <img src={urlFoto(convite.fotoId, 'DISPLAY')!} alt={convite.titulo} className={styles.banner} />
        )}
        <h1>{convite.titulo}</h1>
      </div>

      {convite.convidadoPorNome && (
        <div className={styles.convidantePor}>
          {convite.convidadoPorFotoId && (
            <Image src={urlFoto(convite.convidadoPorFotoId, 'THUMB')!} alt="" width={48} height={48} unoptimized />
          )}
          <div>
            <p className={styles.convidantePorLabel}>Você foi convidado por</p>
            <p className={styles.convidantePorNome}>{convite.convidadoPorNome}</p>
          </div>
        </div>
      )}

      {convite.descricao && <p className={styles.descricao}>{convite.descricao}</p>}

      <div className={styles.infoCard}>
        <p>{new Date(convite.inicioEm).toLocaleDateString('pt-BR', { day: '2-digit', month: 'long', year: 'numeric' })}</p>
        <p>{new Date(convite.inicioEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</p>
        {convite.localNome && <p>{convite.localNome}{convite.localEndereco ? ` — ${convite.localEndereco}` : ''}</p>}
        {convite.vagasRestantes !== null && <p>{convite.vagasRestantes} vagas restantes</p>}

        {etapa === 'landing' && (
          <button type="button" className={styles.btnInscrever} onClick={() => setEtapa(usuario ? 'formulario' : 'escolha')}>
            Inscrever-se
          </button>
        )}
      </div>

      {etapa === 'escolha' && (
        <div className={styles.escolha}>
          <Link href={`/login?redirect=/convite/${token}`} className={styles.btnLogin}>
            Já tenho conta — Fazer login
          </Link>
          <button type="button" className={styles.btnSemConta} onClick={() => setEtapa('formulario')}>
            Continuar sem conta
          </button>
        </div>
      )}

      {etapa === 'formulario' && !usuario && (
        <FormularioConvidado token={token} campos={convite.campos} onSucesso={() => setEtapa('sucesso')} />
      )}

      {etapa === 'formulario' && usuario && (
        <p className={styles.aviso}>
          Você está logado como {usuario.nome} — confirmar inscrição usando seu cadastro ainda
          precisa ser ligado nesta etapa (ver Step 4 abaixo).
        </p>
      )}
    </div>
  )
}
```

> O `<img>` puro (não `<Image>`) pro banner do evento segue o mesmo padrão já usado em
> `DrawerDetalheEvento.tsx` pra imagem de evento (`urlFoto` externo ao domínio do Next, fora do
> `next/image` otimizado) — confirmar isso é mesmo a convenção olhando o arquivo antes.

- [ ] **Step 4: Implementar o caminho "usuário logado" (self-inscrição via convite)**

Dentro do bloco `etapa === 'formulario' && usuario`, substituir o aviso por um componente que:
1. Chama `GET /eventos/{eventoId}/inscricoes/minha` (`useMinhaInscricao`, já existe).
2. Se `!minha.inscrito`, chama `useInscrever` (já criado na Task 10/11) e, no sucesso, mostra o
   formulário de campos personalizados pendentes (`useCamposPersonalizados` + o mesmo
   sub-componente reaproveitado de renderização de campo mencionado na Task 10, Step 5) e chama
   `PUT /inscricoes/{id}/respostas` ao confirmar.
3. Se já `minha.inscrito`, pula direto pra `etapa: 'sucesso'`.

> Esta etapa depende diretamente da extração de componente sugerida na Task 10 Step 5
> (`<CamposPersonalizadosForm />` reaproveitável) — se ela não tiver sido feita lá, fazer aqui
> agora é tarde demais pra não duplicar; voltar e extrair antes de prosseguir.

- [ ] **Step 5: Testar manualmente (público, sem app)**

- Abrir o link gerado na Task 11 numa aba anônima (sem login) — landing carrega, banner/logo/
  convidante aparecem.
- Clicar "Inscrever-se" sem conta → escolha aparece → "Continuar sem conta" → preencher
  formulário → confirma → tela de sucesso.
- Conferir na lista de inscritos do evento (logado como admin) que a pessoa aparece com
  "Convidado de {nome}".
- Testar com token inválido (`/convite/xxxx`) — tela de erro amigável.
- Testar com evento já encerrado (ajustar datas de teste) — tela "já aconteceu".
- Logar numa conta existente e reabrir o link — pula pro formulário sem pedir nome/telefone.
- Mobile: landing inteira sem overflow horizontal, botão CTA sempre visível.

- [ ] **Step 6: Commit**

```bash
git add src/app/convite/ src/hooks/convite/
git commit -m "feat(evento): pagina publica de convite (/convite/[token])"
```

**PARAR AQUI — avisar o autor e esperar ele testar antes de seguir pra Task 13.**

---

### Task 13: Template de campos personalizados no builder

**Files:**
- Modify: (componente do painel de campos personalizados criado na Spec 1 — provavelmente
  `src/components/module/eventos/PainelCamposPersonalizados.tsx` ou dentro de `EventoForm.tsx`
  diretamente; **confirmar o nome real do arquivo** com `grep -rl "campos personalizados"
  src/components src/app` antes de editar, o nome aqui é uma suposição razoável baseada no
  padrão de nomenclatura do projeto)
- Modify: type `CampoPersonalizadoRequest` no front (adicionar `mapeamento`)

**Interfaces:**
- Consumes: `MapeamentoCampoPersonalizado` (novo, espelhar o enum do backend no front).

- [ ] **Step 1: Adicionar o type de mapeamento no front**

No arquivo de types de campos personalizados (`src/types/campoPersonalizado.type.ts` ou nome
equivalente já existente da Spec 1):

```typescript
export type MapeamentoCampoPersonalizado = 'IDADE' | 'ESTADO_CIVIL' | 'SEXO' | 'ENDERECO'
```

E adicionar `mapeamento?: MapeamentoCampoPersonalizado | null` ao type já existente
`CampoPersonalizadoRequest`/`CampoPersonalizadoResponse` (espelhando os DTOs da Task 4).

- [ ] **Step 2: Adicionar o botão "Usar template de dados básicos"**

No componente do painel (identificado no Step 1 do File header desta task), acima da lista de
campos:

```tsx
  function usarTemplateDadosBasicos() {
    const novos: CampoPersonalizadoRequest[] = [
      { id: null, label: 'Idade', placeholder: 'Ex.: 24', tipo: 'TEXTO_CURTO', opcoes: null,
        obrigatorio: false, visivelAoPublico: true, ordem: campos.length, mapeamento: 'IDADE' },
      { id: null, label: 'Estado civil', placeholder: null, tipo: 'OPCAO_UNICA',
        opcoes: ['Solteiro(a)', 'Casado(a)', 'Divorciado(a)', 'Viúvo(a)'],
        obrigatorio: false, visivelAoPublico: true, ordem: campos.length + 1, mapeamento: 'ESTADO_CIVIL' },
      { id: null, label: 'Sexo', placeholder: null, tipo: 'OPCAO_UNICA', opcoes: ['Homem', 'Mulher'],
        obrigatorio: false, visivelAoPublico: true, ordem: campos.length + 2, mapeamento: 'SEXO' },
      { id: null, label: 'Endereço', placeholder: 'Rua, número, bairro, cidade', tipo: 'TEXTO_CURTO',
        opcoes: null, obrigatorio: false, visivelAoPublico: true, ordem: campos.length + 3, mapeamento: 'ENDERECO' },
    ]
    setCampos((atual) => [...atual, ...novos])
  }
```

```tsx
      <button type="button" className={styles.btnTemplate} onClick={usarTemplateDadosBasicos}>
        Usar template de dados básicos
      </button>
      <p className={styles.avisoTemplate}>
        Estes campos também aparecem para quem se inscreve sem cadastro no sistema (convidados)
        — pense no que você gostaria de saber dessas pessoas.
      </p>
```

> A implementação exata de `setCampos`/estado da lista depende de como o painel já gerencia
> estado hoje (React Hook Form `useFieldArray`, `useState` simples, etc.) — usar o mesmo
> mecanismo já existente pra adicionar/remover campo, não introduzir um segundo padrão de
> estado paralelo.

- [ ] **Step 3: Desmarcar `mapeamento` quando o admin edita tipo/opções de um campo mapeado**

No handler de edição de campo já existente (`aoMudarTipo`/`aoMudarOpcoes` ou equivalente),
adicionar: se o campo tem `mapeamento` e o `tipo` ou `opcoes` mudou em relação ao valor
original do template, setar `mapeamento: null` nesse campo — mesmo espírito do backend (Task
4, Step 8), só que no front pra refletir a mudança na prévia sem esperar salvar.

- [ ] **Step 4: Adicionar nome/telefone fixos na prévia ao vivo**

No componente de prévia (já existe da Spec 1), adicionar no topo, antes dos campos
configurados, duas linhas fixas não-editáveis:

```tsx
      <div className={styles.previaCampoFixo}>
        <span>Nome</span>
        <input type="text" disabled placeholder="Sempre coletado automaticamente" />
      </div>
      <div className={styles.previaCampoFixo}>
        <span>Telefone</span>
        <input type="text" disabled placeholder="Sempre coletado automaticamente" />
      </div>
```

> Lembrete do `CLAUDE.md`: prévia de builder precisa ser **interativa de verdade** (inputs reais
> com estado local, nunca `disabled`, exceto justamente estas duas linhas fixas — que são a
> ÚNICA exceção deliberada, porque não são editáveis por design, não porque "ainda não
> implementamos"). Os campos configurados de verdade na prévia continuam interativos como já
> estão.

- [ ] **Step 5: Testar manualmente**

- Abrir configuração de campos de um evento, clicar "Usar template" — 4 campos aparecem.
- Apagar um (ex.: Endereço) — resto continua.
- Editar o tipo de um campo mapeado (ex.: mudar "Sexo" de opção única pra texto curto) —
  confirmar que ele deixa de pular pergunta depois (testar salvando e checando na Task 12/10 se
  volta a perguntar mesmo pra quem já tem o dado).
- Prévia mostra Nome/Telefone fixos no topo, cinza, com a legenda.
- Salvar, reabrir a config — os 4 campos (ou os que sobraram) continuam lá com os rótulos
  editados se algum foi editado.
- Mobile.

- [ ] **Step 6: Commit**

```bash
git add src/types/campoPersonalizado.type.ts \
  src/components/module/eventos/  # arquivo real identificado no Step 1
git commit -m "feat(evento): template de campos basicos no builder de campos personalizados"
```

**PARAR AQUI — avisar o autor e esperar ele testar antes de seguir pra Task 14.**

---

### Task 14: Lista de inscritos — selo "Convidado de {nome}"

**Files:**
- Modify: `src/app/(app)/eventos/[id]/inscritos/page.tsx` (ou o componente de linha da tabela,
  a confirmar o arquivo exato)

**Interfaces:**
- Consumes: `InscritoResponse.convidadoPorNome` (front, Task 9).

- [ ] **Step 1: Adicionar o selo na linha do inscrito**

No componente que renderiza cada linha da lista (identificar com `grep -rn "pessoaRemovida"
src/app src/components` — é o mesmo ponto onde "Pessoa removida do sistema" já é tratado hoje),
adicionar, logo abaixo do nome, quando `inscrito.convidadoPorNome` não for nulo:

```tsx
              {inscrito.convidadoPorNome && (
                <span className={styles.selo}>Convidado de {inscrito.convidadoPorNome}</span>
              )}
```

- [ ] **Step 2: Testar manualmente**

- Evento com pelo menos um convidado sem cadastro (criado via Task 10 ou 12) — selo aparece com
  o nome de quem convidou.
- Pessoa cadastrada normal — sem selo.
- Pessoa removida via LGPD (se houver dado de teste pra isso) — continua mostrando "Pessoa
  removida do sistema", sem selo de convidado.
- Mobile: tabela vira cards (padrão já estabelecido no projeto) — selo cabe sem overflow.

- [ ] **Step 3: Commit**

```bash
git add src/app/\(app\)/eventos/\[id\]/inscritos/  # arquivo real identificado no Step 1
git commit -m "feat(evento): selo Convidado de na lista de inscritos"
```

**FIM DO PLANO — avisar o autor que todas as tasks estão prontas pra revisão final.**
