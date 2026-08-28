vou # Unificar Acompanhante e Convidado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminar `AcompanhanteInscricao` (modelo antigo, aninhado, sem e-mail) e fazer toda
pessoa sem cadastro que participa de um evento — visitante da aba Visitantes, convidado via
convite público, ou alguém que o titular traz junto — virar sua própria `InscricaoEvento`
independente, no mesmo formato: `nome_convidado`/`telefone_convidado`/`email_convidado` +
`convidado_por_pessoa_id` (já existe, marca quem convidou) + respostas de campo
personalizado próprias.

**Architecture:** Migration de dados que converte cada `acompanhante_inscricao` numa
`InscricaoEvento` nova, **reaproveitando o mesmo UUID** da linha antiga — assim
`cobranca_evento.acompanhante_id` e `resposta_campo_personalizado.acompanhante_id`, que já
apontam pra esse UUID, só precisam trocar de coluna (`acompanhante_id` → `inscricao_id`),
sem precisar de tabela de mapeamento. Depois da migration, a tabela e as colunas antigas
somem. Backend perde a ramificação de 3 caminhos (`pessoa`/`acompanhante`/`convidado`) em
`CobrancaController`, `MercadoPagoWebhookService`, `MovimentacaoAutomaticaService`,
`InscricaoService`, `CampoPersonalizadoService` — vira sempre 2 (`pessoa`/`convidado`).
Front perde toda a UI de "acompanhante aninhado" (sub-linhas na lista de inscritos,
presença separada) e ganha um fluxo de "trazer gente junto" que cria N inscrições
independentes, cada uma com campos personalizados próprios.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Flyway; Next.js/TypeScript.

**Spec:** Não há doc de spec separado — o desenho foi fechado em conversa direta com o
usuário nesta sessão (decisões: cada acompanhante vira `InscricaoEvento` própria; liga por
`convidado_por_pessoa_id`, já existente; sem agrupamento visual novo na lista de inscritos;
campos personalizados passam a valer por convidado, o que já era true hoje via
`acompanhante_id` em `RespostaCampoPersonalizado` — só está mudando de coluna). Inventário
completo de código afetado (backend + frontend + testes + migrations) foi levantado antes
deste plano via busca exaustiva no repositório — todo arquivo citado abaixo veio dessa busca.

## Global Constraints

- Nunca commitar antes do autor testar — entregar por task, avisar, esperar teste, só
  então próxima task (ver `CLAUDE.md`).
- Todo teste novo/alterado segue as convenções de `CLAUDE.md` (Mockito puro Estilo A,
  AssertJ, nomenclatura `snake_case` em português).
- Rodar a suíte completa (`mvn -o test`) e `npx tsc --noEmit` + `npx next build` antes de
  considerar qualquer task pronta.
- `igreja_id` sempre do JWT — a migration de dados e as queries novas continuam isoladas
  por igreja onde já eram.

---

## File Structure

**Backend — arquivos removidos por inteiro:**
- `src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteInscricao.java`
- `src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteRepository.java`
- `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/AcompanhanteRequest.java`
- `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/AcompanhanteResponse.java`

**Backend — arquivos alterados:** `InscricaoEvento.java`, `CobrancaEvento.java`,
`RespostaCampoPersonalizado.java`, `InscricaoRepository.java`,
`RespostaCampoPersonalizadoRepository.java`, `InscricaoService.java`,
`CampoPersonalizadoService.java`, `CobrancaEventoService.java`,
`MercadoPagoWebhookService.java`, `MovimentacaoAutomaticaService.java`,
`InscricaoController.java`, `CobrancaController.java`, `SecurityConfig.java`,
`MinhaInscricaoResponse.java`, `InscritoResponse.java`, `ParticipanteResponse.java`,
`CobrancaPublicaDTO.java` (só comentário).

**Frontend — arquivos removidos:** hook `useMarcarPresencaAcompanhante.ts` (vira
`useMarcarPresencaConvidado.ts` já existente ou equivalente unificado).

**Frontend — arquivos alterados:** `types/inscricao.type.ts`, `services/inscricao.service.ts`,
`services/campoPersonalizado.service.ts`, `lib/endpoints.ts`,
`useRemoverConvidado.ts`, `useMarcarTodosPresentes.ts`, `useDesmarcarTodosPresentes.ts`,
`useMarcarPresencaSelecionados.ts`, `useResponderCampos.ts`, `useRespostasCampos.ts`,
`RespostasCamposPersonalizados.tsx`, `ModalResponderCamposPersonalizados.tsx`,
`ModalQuemVai.tsx`, `ModalCompartilharCobranca.tsx`, `BotaoConfirmarPresenca.tsx`,
`app/(app)/eventos/[id]/inscritos/page.tsx`, `ModalInscreverPessoas.tsx`/
`ModalInscreverAlguem.tsx` (fluxo novo de "trazer gente junto").

---

### Task 1: Migration de dados — acompanhante vira InscricaoEvento

**Files:**
- Create: `src/main/resources/db/migration/V33__unificar_acompanhante_convidado.sql`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/AcompanhanteMigracaoV33Test.java`

**Interfaces:**
- Consome: schema atual de `acompanhante_inscricao`, `inscricao_evento`,
  `resposta_campo_personalizado`, `cobranca_evento` (ver tabela de migrations no
  inventário — `V1`, `V6`, `V24`, `V29`, `V30`).
- Produz: toda linha de `acompanhante_inscricao` vira uma `inscricao_evento` com o
  **mesmo `id`**; `resposta_campo_personalizado.inscricao_id` e
  `cobranca_evento.inscricao_id` repontados; colunas/tabela antigas removidas.

- [ ] **Step 1: Escrever o teste de migração (roda contra Testcontainers, `@DataJpaTest`)**

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V33 já rodou (Flyway aplica todas as migrations antes do teste) — este teste só
 * confirma que a tabela/colunas antigas sumiram e que a estrutura nova existe. A
 * conversão de dados em si (linhas reais) não tem como testar aqui porque o banco de
 * teste nasce vazio — quem prova que o SQL de conversão funciona é rodar a migration
 * contra um dump real antes de aplicar em produção (ensaio manual, mesmo padrão do
 * backup do Postgres).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AcompanhanteMigracaoV33Test implements PostgresTestContainerSupport {

    @Autowired EntityManager entityManager;

    @Test
    void tabelaAcompanhanteInscricaoNaoExisteMais() {
        var resultado = entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'acompanhante_inscricao')")
            .getSingleResult();
        assertThat((Boolean) resultado).isFalse();
    }

    @Test
    void colunaAcompanhanteIdSumiuDeCobrancaECampoPersonalizado() {
        var cobranca = (Boolean) entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'cobranca_evento' AND column_name = 'acompanhante_id')")
            .getSingleResult();
        var resposta = (Boolean) entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'resposta_campo_personalizado' AND column_name = 'acompanhante_id')")
            .getSingleResult();
        assertThat(cobranca).isFalse();
        assertThat(resposta).isFalse();
    }
}
```

- [ ] **Step 2: Rodar o teste, ver falhar** (a migration ainda não existe — Flyway nem
  vai subir o schema até a V33 ser criada, então o teste falha na inicialização do
  contexto, não numa asserção específica; isso é esperado).

Run: `mvn -o test -Dtest=AcompanhanteMigracaoV33Test`

- [ ] **Step 3: Escrever a migration**

```sql
-- V33: unifica "acompanhante" (modelo antigo, aninhado, sem e-mail) com "convidado sem
-- cadastro" (nome_convidado/telefone_convidado/email_convidado direto em
-- inscricao_evento) — cada acompanhante vira sua própria inscrição, independente,
-- ligada por convidado_por_pessoa_id (já existe desde V26) ao titular que trouxe.
--
-- Truque: a nova inscricao_evento nasce com o MESMO id da acompanhante_inscricao
-- original — assim cobranca_evento.acompanhante_id e
-- resposta_campo_personalizado.acompanhante_id, que já apontam pra esse id, só
-- precisam trocar de coluna (repontar pra inscricao_id), sem tabela de mapeamento.

INSERT INTO inscricao_evento (
    id, igreja_id, evento_id, pessoa_id, status,
    nome_convidado, telefone_convidado, email_convidado,
    convidado_por_pessoa_id, compareceu, created_at
)
SELECT
    a.id, i.igreja_id, i.evento_id, NULL, i.status,
    a.nome, a.telefone, NULL,
    i.pessoa_id, a.compareceu, a.created_at
FROM acompanhante_inscricao a
JOIN inscricao_evento i ON i.id = a.inscricao_id;

-- Repontar respostas de campo personalizado que hoje ligam por acompanhante_id.
UPDATE resposta_campo_personalizado
SET inscricao_id = acompanhante_id
WHERE acompanhante_id IS NOT NULL;

-- Repontar cobranças que hoje ligam por acompanhante_id — vira o mesmo formato de
-- "convidado sem cadastro" (pessoa_id NULL, inscricao_id aponta pra própria inscrição).
UPDATE cobranca_evento
SET inscricao_id = acompanhante_id, acompanhante_id = NULL
WHERE acompanhante_id IS NOT NULL;

ALTER TABLE resposta_campo_personalizado DROP COLUMN acompanhante_id;
ALTER TABLE cobranca_evento DROP CONSTRAINT IF EXISTS cobranca_evento_pessoa_xor_acompanhante;
ALTER TABLE cobranca_evento DROP COLUMN acompanhante_id;
DROP TABLE acompanhante_inscricao;
```

- [ ] **Step 4: Rodar o teste de novo, ver passar**

Run: `mvn -o test -Dtest=AcompanhanteMigracaoV33Test`
Expected: PASS

- [ ] **Step 5: Ensaio manual contra uma cópia do banco real** (não pular — é a única
  forma de provar que a conversão de dados de verdade funciona, já que o banco de teste
  nasce vazio). Restaurar o backup mais recente num Postgres descartável, rodar a
  migration, conferir manualmente: `SELECT count(*) FROM inscricao_evento WHERE
  convidado_por_pessoa_id IS NOT NULL` bate com o `count(*)` que
  `acompanhante_inscricao` tinha antes; nenhuma `cobranca_evento`/
  `resposta_campo_personalizado` ficou órfã (`inscricao_id` aponta pra uma
  `inscricao_evento` que existe).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V33__unificar_acompanhante_convidado.sql \
        src/test/java/com/domus/api/modules/evento/inscricao/AcompanhanteMigracaoV33Test.java
git commit -m "feat(evento): migration unifica acompanhante em InscricaoEvento propria (V33)"
```

---

### Task 2: Entidades — remover AcompanhanteInscricao e suas referências

**Files:**
- Delete: `AcompanhanteInscricao.java`
- Modify: `InscricaoEvento.java` (remove `@OneToMany acompanhantes`, remove Javadoc que
  cita `getAcompanhantes()`), `CobrancaEvento.java` (remove campo/getter
  `acompanhanteId`, remove a checagem XOR pessoa/acompanhante no construtor — vira
  "pessoaId nulo é sempre convidado sem cadastro"), `RespostaCampoPersonalizado.java`
  (remove `@ManyToOne acompanhante`).

**Interfaces:**
- Consome: nada novo.
- Produz: `CobrancaEvento` construtor perde o parâmetro `acompanhanteId` — quem chama
  (Task 6) precisa ajustar.

- [ ] **Step 1: Deletar `AcompanhanteInscricao.java`**
- [ ] **Step 2: Remover `acompanhantes` de `InscricaoEvento.java`** — apagar o campo
  `@OneToMany`, o import de `AcompanhanteInscricao`, e reescrever o Javadoc de
  `isConvidadoSemCadastro()` que hoje avisa "não confundir com `getAcompanhantes()`"
  (esse aviso deixa de fazer sentido).
- [ ] **Step 3: Remover `acompanhanteId` de `CobrancaEvento.java`** — apagar o campo, o
  getter, a checagem `if (pessoaId != null && acompanhanteId != null)` no construtor
  (vira só `pessoaId` nulo ou preenchido, sem XOR nenhum pra checar).
- [ ] **Step 4: Remover `acompanhante` de `RespostaCampoPersonalizado.java`** — apagar o
  `@ManyToOne`, o getter/setter.
- [ ] **Step 5: Compilar** (vai quebrar em cascata nos services/controllers/DTOs — é
  esperado, as próximas tasks resolvem).

Run: `mvn -o compile 2>&1 | grep ERROR`
Expected: erros só nos arquivos das próximas tasks (services, controllers, DTOs,
testes) — não em nenhum arquivo fora dessa lista.

- [ ] **Step 6: Commit** (junto com Task 3, já que Task 3 é o que faz compilar de novo —
  ver nota no fim da Task 3).

---

### Task 3: Repositories — remover AcompanhanteRepository, simplificar queries

**Files:**
- Delete: `AcompanhanteRepository.java`
- Modify: `InscricaoRepository.java`, `RespostaCampoPersonalizadoRepository.java`

**Interfaces:**
- Consome: nada novo.
- Produz: `RespostaCampoPersonalizadoRepository` ganha (ou já tinha, confirmar)
  `findByCampoIdAndInscricaoId(campoId, inscricaoId)` — sem o parâmetro
  `acompanhanteId`, que deixa de existir.

- [ ] **Step 1: Deletar `AcompanhanteRepository.java`**
- [ ] **Step 2: Em `InscricaoRepository.java`**, reescrever as queries que hoje somam
  `AcompanhanteInscricao` nas contagens (`contarPessoasConfirmadas`,
  `countConvidadosInscritos`, `countConvidadosCompareceram`) pra contar só
  `InscricaoEvento` — como cada acompanhante agora é sua própria linha, a contagem
  simplifica pra `COUNT(i) WHERE i.evento.id = :eventoId AND i.status = 'CONFIRMADA'`
  (ajustar por caso — conferir o texto exato de cada query antes de reescrever, elas têm
  nuance de contar só confirmados/só quem compareceu). Remover `LEFT JOIN FETCH
  i.acompanhantes` de `listarPorEvento`/`listarComDetalhesPorIds`.
- [ ] **Step 3: Em `RespostaCampoPersonalizadoRepository.java`**, trocar
  `findByCampoIdAndInscricaoIdAndAcompanhanteId` por
  `findByCampoIdAndInscricaoId(campoId, inscricaoId)` (sem o terceiro parâmetro).
- [ ] **Step 4: Compilar** — mesma expectativa da Task 2 (erros só nas próximas tasks).

Run: `mvn -o compile 2>&1 | grep ERROR`

---

### Task 4: InscricaoService — remover métodos antigos, garantir criação de convidado com `convidadoPorPessoaId`

**Files:**
- Modify: `InscricaoService.java`
- Test: `InscricaoServiceTest.java`

**Interfaces:**
- Consome: método existente de criar convidado sem cadastro (o que hoje serve o convite
  público) — **conferir a assinatura exata antes de mexer**; provavelmente algo como
  `inscreverConvidado(eventoId, nome, telefone, email, respostas, convidadoPorPessoaId,
  igrejaId)`.
- Produz: esse mesmo método passa a ser o ÚNICO caminho pra "titular traz alguém junto"
  — chamado com `convidadoPorPessoaId` = id do titular, em vez do antigo
  `adicionarAcompanhante`.

- [ ] **Step 1: Ler o método de criar convidado atual por inteiro** (o que hoje atende
  `/convites/{token}/entrar` ou equivalente) e confirmar se ele já aceita
  `convidadoPorPessoaId` opcional vindo de um contexto autenticado (não só do fluxo
  público sem sessão). Se não aceitar, esse é o ponto que precisa mudar.

- [ ] **Step 2: Escrever o teste que prova o caminho novo**

```java
@Test
void titularTrazConvidadoCriaInscricaoPropriaComConvidadoPorPessoaId() {
    UUID eventoId = UUID.randomUUID();
    UUID pessoaIdTitular = UUID.randomUUID();
    when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento(10)));
    when(cobrancaEventoRepository.contarPessoasComVagaReservada(any(), any())).thenReturn(0L);

    var resposta = service.inscreverConvidado(eventoId, "Amigo do Titular", "11999999999",
            null, List.of(), pessoaIdTitular, igrejaId);

    ArgumentCaptor<InscricaoEvento> captor = ArgumentCaptor.forClass(InscricaoEvento.class);
    verify(inscricaoRepository).save(captor.capture());
    assertThat(captor.getValue().getConvidadoPorPessoaId()).isEqualTo(pessoaIdTitular);
    assertThat(captor.getValue().getPessoa()).isNull();
    assertThat(captor.getValue().getNomeConvidado()).isEqualTo("Amigo do Titular");
}
```

(Ajustar a assinatura exata conforme o Step 1 confirmar — o teste acima é o formato
esperado, não necessariamente os nomes exatos dos parâmetros.)

- [ ] **Step 3: Rodar o teste, ver falhar (ou passar, se o Step 1 já confirmou que o
  método já suporta isso)**

Run: `mvn -o test -Dtest=InscricaoServiceTest#titularTrazConvidadoCriaInscricaoPropriaComConvidadoPorPessoaId`

- [ ] **Step 4: Se precisar, ajustar `inscreverConvidado` pra aceitar
  `convidadoPorPessoaId` vindo de contexto autenticado** (hoje provavelmente só usa isso
  no fluxo de convite público via token).

- [ ] **Step 5: Remover os métodos antigos**: `adicionarAcompanhante`,
  `removerAcompanhante`, `marcarPresencaAcompanhante` — o que eles faziam agora é
  coberto por `inscreverConvidado` (criar) e o cancelamento/presença normais de
  `InscricaoEvento` (já existem, servem qualquer inscrição inclusive convidado).

- [ ] **Step 6: Ajustar `marcarTodosPresentes`/`desmarcarTodosPresentes`** — hoje iteram
  `inscricao.getAcompanhantes()` além do titular; como não existe mais essa lista
  aninhada, cada convidado já é uma `InscricaoEvento` própria que o método principal já
  processa no loop de inscrições do evento — conferir se o loop existente já cobre
  todo mundo sem precisar de lógica extra.

- [ ] **Step 7: Ajustar `cancelarInterno`** — remove a linha
  `inscricao.getAcompanhantes().clear()` (não existe mais o que limpar; cancelar o
  titular não cancela automaticamente quem ele convidou, cada um se cancela
  independente — **decisão a confirmar com o usuário se isso for diferente do
  comportamento esperado**, já que hoje cancelar o titular cancelava os acompanhantes
  junto).

- [ ] **Step 8: Ajustar `validarConvidadoNaoDuplicado`/`validarConvidadoTopoNaoDuplicado`/
  `mesmoConvidado`** — simplificam pra checar duplicidade só entre `InscricaoEvento` do
  mesmo evento (sem separar "topo" de "acompanhante", já que não existe mais essa
  distinção).

- [ ] **Step 9: Ajustar `enviarEmailCancelamento`** — remove o comentário/branch
  "Acompanhante (modelo antigo) não tem e-mail"; todo convidado agora tem
  `emailConvidado` (obrigatório se o evento é pago, opcional se não).

- [ ] **Step 10: Rodar a suíte de `InscricaoServiceTest` inteira, ajustar os testes que
  quebrarem** (a Task 9 cobre a limpeza formal dos testes antigos — aqui é só garantir
  que nada trava a compilação/execução).

Run: `mvn -o test -Dtest=InscricaoServiceTest`

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java \
        src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteInscricao.java \
        src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteRepository.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java \
        src/main/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizado.java \
        src/main/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizadoRepository.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "refactor(inscricao): remove modelo antigo de acompanhante do InscricaoService"
```

---

### Task 5: CampoPersonalizadoService — sempre por inscricaoId, nunca mais acompanhanteId

**Files:**
- Modify: `CampoPersonalizadoService.java`
- Test: `CampoPersonalizadoServiceTest.java`

**Interfaces:**
- Consome: `RespostaCampoPersonalizadoRepository.findByCampoIdAndInscricaoId` (Task 3).
- Produz: `responder(inscricaoId, ...)` e `respostasPorInscricao(inscricaoId)` perdem o
  parâmetro `acompanhanteId` — cada convidado responde via seu próprio `inscricaoId`.

- [ ] **Step 1: Remover o parâmetro `acompanhanteId` de `responder`/
  `respostasPorInscricao`** — apagar a busca `inscricao.getAcompanhantes()` e a
  associação `nova.setAcompanhante(achado)`.
- [ ] **Step 2: Rodar `CampoPersonalizadoServiceTest`, ajustar os testes que chamavam
  com `acompanhanteId`** (viram chamadas com o `inscricaoId` do próprio convidado).

Run: `mvn -o test -Dtest=CampoPersonalizadoServiceTest`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java \
        src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java
git commit -m "refactor(campopersonalizado): resposta sempre por inscricaoId, sem acompanhanteId"
```

---

### Task 6: Pagamento e financeiro — ramificação vira só pessoa/convidado

**Files:**
- Modify: `CobrancaEventoService.java`, `MercadoPagoWebhookService.java`,
  `MovimentacaoAutomaticaService.java`, `CobrancaController.java`
- Test: `CobrancaEventoServiceTest.java`, `MercadoPagoWebhookServiceTest.java`,
  `CobrancaControllerTest.java`

**Interfaces:**
- Consome: `CobrancaEvento` sem `acompanhanteId` (Task 2).
- Produz: `criarParaTerceiro` perde o parâmetro `acompanhanteId` (só `pessoaId`, que já
  aceitava nulo pro caso de convidado sem cadastro).

- [ ] **Step 1: `CobrancaEventoService.criarParaTerceiro`** — remove o parâmetro
  `acompanhanteId` e a lógica que decidia entre pessoa/acompanhante; vira sempre
  `pessoaId` (nulo = convidado sem cadastro, igual já funciona pro Plano 4b).
- [ ] **Step 2: `MercadoPagoWebhookService.resolverNomePagador`** (Task da sessão
  anterior que criou esse método) — remove o branch
  `cobranca.getAcompanhanteId() != null` e a injeção de `AcompanhanteRepository`; vira
  só `pessoa != null ? pessoa.getNome() : inscricao.getNomeConvidado()`. Mesma coisa em
  `enviarEmailConfirmacao` (o branch que hoje faz `return` cedo pra acompanhante sem
  e-mail — não existe mais esse caso, todo convidado tem e-mail).
- [ ] **Step 3: `MovimentacaoAutomaticaService`** — conferir se algum comentário/branch
  cita "acompanhante" (achado no inventário, era só comentário) e atualizar o texto.
- [ ] **Step 4: `CobrancaController.buscarPorId`/`buscar`** — remove o branch
  `cobranca.getAcompanhanteId() != null` na resolução de `nomePagador`, remove a
  injeção de `AcompanhanteRepository`.
- [ ] **Step 5: Rodar os três arquivos de teste, ajustar chamadas que passavam
  `acompanhanteId`.**

Run: `mvn -o test -Dtest=CobrancaEventoServiceTest,MercadoPagoWebhookServiceTest,CobrancaControllerTest`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoService.java \
        src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java \
        src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java \
        src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoAutomaticaService.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoServiceTest.java \
        src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java
git commit -m "refactor(pagamento): remove ramificacao de acompanhante em cobranca/webhook/financeiro"
```

---

### Task 7: Controller e SecurityConfig — remover endpoints de acompanhante

**Files:**
- Modify: `InscricaoController.java`, `SecurityConfig.java`
- DTOs: `MinhaInscricaoResponse.java`, `InscritoResponse.java`, `ParticipanteResponse.java`
  (remove campo `acompanhantes`/`convidados` derivado da lista antiga — cada convidado já
  vem como sua própria linha na listagem geral de inscritos, não precisa mais de lista
  aninhada).
- Delete: `AcompanhanteRequest.java`, `AcompanhanteResponse.java`

**Interfaces:**
- Produz: `GET /inscricoes/{inscricaoId}/respostas` e `PUT .../respostas` perdem o query
  param `acompanhanteId` (Task 5 já tirou do service).

- [ ] **Step 1: Remover de `InscricaoController.java`**: os endpoints `POST
  .../acompanhantes`, `DELETE /acompanhantes/{id}`, `PATCH
  .../presenca/acompanhantes/{acompanhanteId}`; remover o query param
  `acompanhanteId` de `GET`/`PUT .../respostas`.
- [ ] **Step 2: Remover de `SecurityConfig.java`** o matcher
  `"/acompanhantes/**"` da regra de `DELETE`.
- [ ] **Step 3: Deletar `AcompanhanteRequest.java`/`AcompanhanteResponse.java`.**
- [ ] **Step 4: Em `MinhaInscricaoResponse`/`InscritoResponse`/`ParticipanteResponse`**,
  remover o campo derivado de acompanhantes — a lista de inscritos do evento (endpoint
  que já existe) passa a listar cada convidado como linha própria, então esses DTOs não
  precisam mais aninhar nada.
- [ ] **Step 5: Compilar tudo, rodar a suíte inteira.**

Run: `mvn -o test`
Expected: só falham os testes que a Task 9 ainda vai limpar (arquivos
`InscricaoServiceTest`/`InscricaoPresencaTest`/etc. já tratados nas tasks anteriores não
devem mais falhar; os que restam são os cobertos pela Task 9).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java \
        src/main/java/com/domus/api/config/SecurityConfig.java \
        src/main/java/com/domus/api/modules/evento/inscricao/DTOs/
git commit -m "refactor(inscricao): remove endpoints e DTOs de acompanhante"
```

---

### Task 8: Limpeza dos testes backend restantes

**Files:**
- Modify/Delete: `InscricaoPresencaTest.java`, `InscricaoRepositoryPresencaTest.java`,
  `InscricaoElegibilidadeTest.java`, `InscricaoConcorrenciaTest.java`,
  `ParticipanteResponseTest.java`, `InscritoResponseTest.java`,
  `RespostaCampoPersonalizadoRepositoryTest.java`, `EventoDefaultsTest.java`,
  `MovimentacaoAutomaticaServiceTest.java`, `CobrancaEventoTest.java`,
  `CobrancaEventoRepositoryTest.java`, `PurgaIgrejaIntegrationTest.java`

- [ ] **Step 1: Passar por cada arquivo da lista** — remover cenários que testavam
  comportamento exclusivo de `AcompanhanteInscricao` (ex.: `PurgaIgrejaIntegrationTest`
  criava um acompanhante de teste pra provar cascade — vira criar um convidado
  `InscricaoEvento` normal, já que agora é isso). Onde o cenário ainda faz sentido
  (ex.: contagem de vaga incluindo convidados), ajustar pra usar `InscricaoEvento`
  direto em vez de `AcompanhanteInscricao`.
- [ ] **Step 2: Rodar a suíte completa.**

Run: `mvn -o test`
Expected: PASS, zero falha, zero erro.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/domus/api/modules/evento/ src/test/java/com/domus/api/modules/pagamento/ src/test/java/com/domus/api/modules/pessoa/PurgaIgrejaIntegrationTest.java
git commit -m "test(evento): limpa testes que cobriam o modelo antigo de acompanhante"
```

---

### Task 9: Frontend — tipos, serviços, hooks

**Files:**
- Modify: `types/inscricao.type.ts`, `services/inscricao.service.ts`,
  `services/campoPersonalizado.service.ts`, `lib/endpoints.ts`
- Delete: `hooks/inscricao/useMarcarPresencaAcompanhante.ts`
- Modify: `useRemoverConvidado.ts`, `useMarcarTodosPresentes.ts`,
  `useDesmarcarTodosPresentes.ts`, `useMarcarPresencaSelecionados.ts`,
  `useResponderCampos.ts`, `useRespostasCampos.ts`

**Interfaces:**
- Produz: `inscricaoService`/`campoPersonalizadoService` sem nenhum método que recebe
  `acompanhanteId`; `marcarPresenca` unificado (um só método serve titular e convidado,
  já que os dois são `InscricaoEvento`).

- [ ] **Step 1: `types/inscricao.type.ts`** — remover `AcompanhanteResponse`,
  `AcompanhanteRequest`, e o campo `acompanhantes: AcompanhanteResponse[]` de onde
  aparecer.
- [ ] **Step 2: `lib/endpoints.ts`** — remover `ACOMPANHANTES`,
  `REMOVER_ACOMPANHANTE`, `ACOMPANHANTE` (presença).
- [ ] **Step 3: `services/inscricao.service.ts`** — remover
  `adicionarAcompanhante`/`removerAcompanhante`/`marcarPresencaAcompanhante`; os
  métodos `respostas`/`responder` perdem o parâmetro `acompanhanteId`.
- [ ] **Step 4: Deletar `useMarcarPresencaAcompanhante.ts`** — presença de convidado já
  usa o hook normal de marcar presença de inscrição (conferir qual é, provavelmente
  `useMarcarPresenca.ts` ou nome equivalente já usado pro titular).
- [ ] **Step 5: `useMarcarTodosPresentes.ts`/`useDesmarcarTodosPresentes.ts`** —
  remover o optimistic update em `i.acompanhantes` (não existe mais essa chave).
- [ ] **Step 6: `useMarcarPresencaSelecionados.ts`** — remover o tipo
  `'acompanhante'` da união (`'inscricao' | 'acompanhante'` vira só `'inscricao'`, já
  que convidado também é inscrição agora).
- [ ] **Step 7: `useResponderCampos.ts`/`useRespostasCampos.ts`** — remover o parâmetro
  opcional `acompanhanteId`.
- [ ] **Step 8: `npx tsc --noEmit`** — confirmar que só sobram erros nos arquivos das
  próximas duas tasks.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/types/inscricao.type.ts frontend/src/services/inscricao.service.ts \
        frontend/src/services/campoPersonalizado.service.ts frontend/src/lib/endpoints.ts \
        frontend/src/hooks/inscricao/
git commit -m "refactor(inscricao): remove tipos/servicos/hooks do modelo antigo de acompanhante"
```

---

### Task 10: Frontend — lista de inscritos, ModalQuemVai, presença, campos personalizados

**Files:**
- Modify: `RespostasCamposPersonalizados.tsx`, `ModalResponderCamposPersonalizados.tsx`,
  `ModalQuemVai.tsx`, `ModalCompartilharCobranca.tsx`, `BotaoConfirmarPresenca.tsx`,
  `app/(app)/eventos/[id]/inscritos/page.tsx`

**Interfaces:**
- Consome: services/hooks da Task 9.
- Produz: nenhuma prop `acompanhanteId` sobrevive nesses componentes; lista de inscritos
  para de renderizar sub-linhas de acompanhante (cada convidado já chega como linha
  própria na resposta do backend).

- [ ] **Step 1: `RespostasCamposPersonalizados.tsx`/`ModalResponderCamposPersonalizados.tsx`**
  — remover a prop `acompanhanteId?`; sempre responde pelo `inscricaoId` recebido
  (que agora é sempre o do próprio convidado, não do titular).
- [ ] **Step 2: `inscritos/page.tsx`** — remover a renderização de sub-linha de
  acompanhante (checkbox + switch de presença aninhados); cada convidado já aparece
  como linha própria na tabela principal, igual qualquer outra inscrição. Ajustar a
  exportação (hoje inclui `inscrito.acompanhantes` — remover, já que não existe mais
  esse campo). Ajustar `quantidadeConvidados` no modal de cancelamento — hoje conta
  `inscritoCancelando.acompanhantes.length`; **decisão pendente da Task 4 Step 7**: se
  cancelar o titular não cancela mais quem ele convidou automaticamente, esse aviso de
  "quantidade de convidados" no cancelamento pode deixar de fazer sentido — conferir
  com o usuário antes de simplesmente remover.
- [ ] **Step 3: `ModalQuemVai.tsx`** — mesma limpeza: para de mapear
  `i.acompanhantes` pra lista de nomes; se ainda fizer sentido mostrar "quem esse
  titular convidou", isso agora é uma consulta separada por `convidadoPorPessoaId`
  (conferir se já existe um jeito de buscar isso, ou se essa visão simplesmente some).
- [ ] **Step 4: `ModalCompartilharCobranca.tsx`** — ajustar o comentário/texto que cita
  "acompanhante que escolheu enviar" pra "convidado que escolheu enviar" (é só
  nomenclatura, a lógica de compartilhar link de cobrança de terceiro já era genérica).
- [ ] **Step 5: `BotaoConfirmarPresenca.tsx`** — `minha.acompanhantes.length` não existe
  mais; a contagem de "quantos convidados esse usuário trouxe" agora é uma
  query/prop separada (ver o que a Task 4/11 expõe pra isso).
- [ ] **Step 6: `npx tsc --noEmit` e `npx next build`.**

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/module/eventos/ frontend/src/app/\(app\)/eventos/\[id\]/inscritos/page.tsx
git commit -m "refactor(eventos): lista de inscritos e presenca sem modelo antigo de acompanhante"
```

---

### Task 11: Frontend — fluxo "trazer gente junto" (a única peça nova de verdade)

**Files:**
- Modify: `ModalInscreverPessoas.tsx` e/ou `ModalInscreverAlguem.tsx` (conferir qual
  hoje tem a UI de "adicionar acompanhante inline" — é ali que precisa virar "adicionar
  convidado inline, um de cada vez, respondendo os campos de cada um").

**Interfaces:**
- Consome: `inscreverConvidado` (Task 4) chamado uma vez por convidado, com
  `convidadoPorPessoaId` = titular.

Esta é a única task do plano que não é refatoração/limpeza — é UX nova. O usuário
descreveu o comportamento esperado: *"se um amigo ou um pai está escrevendo seus
convidados ele responde os campos de cada um, o seu e quando enviar o seu aparece os
demais um de cada vez, ou então ele decide enviar pra as próprias pessoas fazerem suas
inscrições, como é feito hoje"* — ou seja, dois caminhos que já existem separados hoje
("preencher aqui" vs. "gerar link pra pessoa preencher sozinha") continuam existindo,
só que agora "preencher aqui" vira um mini-wizard: um convidado por vez, campos
personalizados desse convidado específico, decide quantos trazer.

- [ ] **Step 1: Ler o componente atual por inteiro** (o que hoje tem o formulário
  inline de "adicionar acompanhante": nome + telefone) pra entender exatamente onde
  entra o novo formato.
- [ ] **Step 2: Desenhar a interação exata com o usuário antes de codar** — isso é
  decisão de produto/UX que não estava fechada na conversa (quantos convidados de uma
  vez? um formulário por convidado em sequência, ou uma lista expansível?). **Não
  implementar sem confirmar o desenho — voltar pro brainstorm só desta task, não
  precisa refazer o resto do plano.**
- [ ] **Step 3-N:** (a definir depois do Step 2, junto com o usuário).

---

## Auto-Review

**Cobertura da spec (decisões da conversa):** cada acompanhante vira `InscricaoEvento`
própria (Task 1, 4) ✓; liga por `convidado_por_pessoa_id` já existente, sem agrupamento
visual novo (Task 10) ✓; campos personalizados por convidado (Task 5) ✓; inventário
completo de arquivos coberto (Tasks 2-10 cobrem os ~63 arquivos levantados) ✓.

**Gaps assumidos, não decisões implícitas:** Task 4 Step 7 (cancelar titular cancela
convidados dele?) e Task 11 inteira (desenho exato do wizard) ficam marcados como
pendência explícita — não foram inventados, precisam de confirmação antes de codar essa
parte específica.
