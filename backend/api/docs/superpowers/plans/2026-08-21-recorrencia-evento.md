# Recorrência de Evento Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar de exigir que a igreja recadastre o culto toda semana na mão — uma série
recorrente (diária/semanal/mensal) materializa ocorrências reais de `Evento` com antecedência,
com os 3 escopos de edição/cancelamento que qualquer calendário maduro já resolveu: só esta
ocorrência, esta e as seguintes, ou a série inteira.

**Architecture:** `EventoSerie` (nova entidade) guarda só a regra de recorrência; `Evento`
(existente) ganha `serieId` nulável + `divergeDaSerie`, sem duplicar nenhum campo de conteúdo.
Um `RecorrenciaCalculator` puro (sem Spring) calcula as próximas datas por frequência. Um job
diário (`@Scheduled`, mesmo padrão de `ExclusaoIgrejaJob`) materializa ocorrências futuras numa
janela de 60 dias, clonando os campos da ocorrência mais recente não-divergente da série.
Edição/cancelamento com escopo `ESTA_E_SEGUINTES` divide a série em duas (encerra a atual,
cria uma nova a partir daquela data) — mesma técnica que o Google Calendar usa.

**Tech Stack:** Spring Boot (JPA, Flyway, `@Scheduled`), Next.js/TanStack Query/React Hook
Form + Zod, PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-08-21-recorrencia-evento-design.md`

## Global Constraints

- `EventoSerie` nunca guarda título/local/vagas/restrições/etc. — só a regra (frequência,
  intervalo, dias, fim). Conteúdo vive exclusivamente em `Evento`.
- `data_fim` e `numero_ocorrencias` em `EventoSerie` são mutuamente exclusivos (CHECK no
  banco) — os dois nulos significa série sem fim.
- Materialização nunca recria uma data que já tem `Evento` pra aquela série, **mesmo
  soft-deletado** (feriado cancelado não ressuscita).
- O job clona sempre da ocorrência mais recente com `divergeDaSerie = false` — nunca de uma
  ocorrência editada "só este dia", senão a divergência pontual vazaria pras semanas seguintes.
- Escopo `SERIE`/`ESTA_E_SEGUINTES` só afeta ocorrências `AGENDADO` — passado já é bloqueado
  por `EventoService` hoje (`EM_ANDAMENTO`/`ENCERRADO` recusam edição), regra existente cobre
  isso de graça, não precisa reimplementar.
- Notificação `NOVO_EVENTO` dispara em toda ocorrência materializada (inclusive as do job);
  texto muda: avulso usa o texto atual, ocorrência de série usa tom de lembrete/convite.
- Evento avulso (`serieId == null`) não muda de comportamento em nada — todo request sem
  `recorrencia`/sem `escopo` se comporta exatamente como hoje.

---

## Task 1: Migration, enums e entidade `EventoSerie`

**Files:**
- Create: `src/main/resources/db/migration/V22__evento_serie.sql`
- Create: `src/main/java/com/domus/api/modules/evento/serie/FrequenciaRecorrencia.java`
- Create: `src/main/java/com/domus/api/modules/evento/serie/TipoRecorrenciaMensal.java`
- Create: `src/main/java/com/domus/api/modules/evento/serie/EscopoEdicaoEvento.java`
- Create: `src/main/java/com/domus/api/modules/evento/serie/EventoSerie.java`
- Create: `src/main/java/com/domus/api/modules/evento/serie/EventoSerieRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Test: `src/test/java/com/domus/api/modules/evento/serie/EventoSerieRepositoryTest.java`

**Interfaces:**
- Produces: `EventoSerie` (entidade), `FrequenciaRecorrencia` (`DIARIA`, `SEMANAL`, `MENSAL`),
  `TipoRecorrenciaMensal` (`DIA_FIXO`, `DIA_DA_SEMANA`), `EscopoEdicaoEvento` (`ESTA`,
  `ESTA_E_SEGUINTES`, `SERIE`), `EventoSerieRepository` com
  `Optional<EventoSerie> findByIdAndIgrejaId(UUID id, UUID igrejaId)` e
  `List<EventoSerie> findByIgrejaIdAndAtivaTrue(UUID igrejaId)`.
- `Evento` ganha `getSerie()`/`setSerie(EventoSerie)` e `isDivergeDaSerie()`/
  `setDivergeDaSerie(boolean)`.

- [ ] **Step 1: Escrever a migration**

```sql
CREATE TABLE evento_serie (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id                   UUID NOT NULL REFERENCES igreja(id),
    frequencia                  VARCHAR(10) NOT NULL,
    intervalo                   INTEGER NOT NULL DEFAULT 1 CHECK (intervalo > 0),
    dias_semana                 VARCHAR(80),
    tipo_recorrencia_mensal     VARCHAR(20),
    data_fim                    DATE,
    numero_ocorrencias          INTEGER CHECK (numero_ocorrencias > 0),
    ativa                       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por_usuario_id       UUID REFERENCES usuario(id),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),

    CHECK (data_fim IS NULL OR numero_ocorrencias IS NULL)
);

ALTER TABLE evento ADD COLUMN serie_id UUID REFERENCES evento_serie(id);
ALTER TABLE evento ADD COLUMN diverge_da_serie BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_evento_serie ON evento (serie_id);
```

Salvar em `src/main/resources/db/migration/V22__evento_serie.sql`.

- [ ] **Step 2: Criar os enums**

```java
package com.domus.api.modules.evento.serie;

public enum FrequenciaRecorrencia {
    DIARIA, SEMANAL, MENSAL
}
```

```java
package com.domus.api.modules.evento.serie;

/** Só relevante quando {@link FrequenciaRecorrencia#MENSAL}. */
public enum TipoRecorrenciaMensal {
    /** Todo dia 15, por exemplo. */
    DIA_FIXO,
    /** Toda 1ª/2ª/3ª/última terça, por exemplo. */
    DIA_DA_SEMANA
}
```

```java
package com.domus.api.modules.evento.serie;

public enum EscopoEdicaoEvento {
    ESTA, ESTA_E_SEGUINTES, SERIE
}
```

- [ ] **Step 3: Criar a entidade `EventoSerie`**

```java
package com.domus.api.modules.evento.serie;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "evento_serie")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EventoSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FrequenciaRecorrencia frequencia;

    @Column(nullable = false)
    @Builder.Default
    private int intervalo = 1;

    /** CSV de {@code DiaSemana.name()} — só preenchido quando {@code frequencia == SEMANAL}. */
    @Column(name = "dias_semana", length = 80)
    private String diasSemana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recorrencia_mensal", length = 20)
    private TipoRecorrenciaMensal tipoRecorrenciaMensal;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "numero_ocorrencias")
    private Integer numeroOcorrencias;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativa = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    public java.util.Set<com.domus.api.modules.celula.DiaSemana> getDiasSemanaComoSet() {
        if (diasSemana == null || diasSemana.isBlank()) return java.util.Set.of();
        java.util.Set<com.domus.api.modules.celula.DiaSemana> resultado = new java.util.HashSet<>();
        for (String parte : diasSemana.split(",")) {
            resultado.add(com.domus.api.modules.celula.DiaSemana.valueOf(parte.trim()));
        }
        return resultado;
    }
}
```

- [ ] **Step 4: Adicionar `serieId`/`divergeDaSerie` em `Evento`**

Em `src/main/java/com/domus/api/modules/evento/Evento.java`, adicionar junto dos outros
campos (antes de `deletedAt`):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serie_id")
    private com.domus.api.modules.evento.serie.EventoSerie serie;

    @Column(name = "diverge_da_serie", nullable = false)
    @Builder.Default
    private boolean divergeDaSerie = false;
```

- [ ] **Step 5: Criar o repositório**

```java
package com.domus.api.modules.evento.serie;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoSerieRepository extends JpaRepository<EventoSerie, UUID> {
    Optional<EventoSerie> findByIdAndIgrejaId(UUID id, UUID igrejaId);
    List<EventoSerie> findByIgrejaIdAndAtivaTrue(UUID igrejaId);
}
```

- [ ] **Step 6: Escrever o teste de repositório**

```java
package com.domus.api.modules.evento.serie;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoSerieRepositoryTest implements PostgresTestContainerSupport {

    @Autowired EventoSerieRepository repository;
    @Autowired IgrejaRepository igrejaRepository;

    @Test
    void salvaERecuperaPorIgreja() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste Série");
        igreja.setEmailContato("serie@teste.com");
        igreja = igrejaRepository.save(igreja);

        EventoSerie serie = EventoSerie.builder()
                .igreja(igreja)
                .frequencia(FrequenciaRecorrencia.SEMANAL)
                .intervalo(1)
                .diasSemana("QUINTA")
                .build();
        UUID id = repository.save(serie).getId();

        assertThat(repository.findByIdAndIgrejaId(id, igreja.getId())).isPresent();
        assertThat(repository.findByIgrejaIdAndAtivaTrue(igreja.getId()))
                .extracting(EventoSerie::getId).contains(id);
    }
}
```

- [ ] **Step 7: Rodar e conferir**

```bash
set -a; source .env >/dev/null 2>&1; set +a
./mvnw -q -o test -Dtest=EventoSerieRepositoryTest
```

Expected: PASS (precisa de Docker rodando — sobe Postgres via Testcontainers).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V22__evento_serie.sql \
        src/main/java/com/domus/api/modules/evento/serie/ \
        src/main/java/com/domus/api/modules/evento/Evento.java \
        src/test/java/com/domus/api/modules/evento/serie/
git commit -m "feat(evento): migration, enums e entidade EventoSerie"
```

---

## Task 2: `RecorrenciaCalculator` (cálculo de datas, sem Spring)

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/serie/RecorrenciaCalculator.java`
- Test: `src/test/java/com/domus/api/modules/evento/serie/RecorrenciaCalculatorTest.java`

**Interfaces:**
- Consumes: `EventoSerie` (Task 1).
- Produces: `RecorrenciaCalculator.proximasDatas(EventoSerie serie, LocalDateTime ultimaOcorrencia, LocalDate limiteJanela, int ocorrenciasJaGeradas)` retornando `List<LocalDateTime>` — usado pelo job (Task 5) e, indiretamente, valida a regra ao criar a série (Task 4).

Lógica pura, sem banco — Mockito nem entra aqui, é só `EventoSerie` + datas.

- [ ] **Step 1: Escrever os testes (um por frequência)**

```java
package com.domus.api.modules.evento.serie;

import com.domus.api.modules.celula.DiaSemana;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciaCalculatorTest {

    @Test
    void diariaSomaIntervaloDeDias() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(2).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0);

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 8), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 3, 19, 0),
                LocalDateTime.of(2026, 9, 5, 19, 0),
                LocalDateTime.of(2026, 9, 7, 19, 0));
    }

    @Test
    void semanalRespeitaDiasDaSemanaEscolhidos() {
        // Serie de terça e quinta; última ocorrência foi terça 2026-09-01.
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.SEMANAL).intervalo(1)
                .diasSemana("TERCA,QUINTA").build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0); // terça

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 10), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 3, 19, 0),  // quinta
                LocalDateTime.of(2026, 9, 8, 19, 0),  // terça (semana seguinte)
                LocalDateTime.of(2026, 9, 10, 19, 0)); // quinta
    }

    @Test
    void semanalQuinzenalPulaUmaSemanaInteira() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.SEMANAL).intervalo(2)
                .diasSemana("QUINTA").build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 3, 19, 0); // quinta

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 24), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 17, 19, 0)); // pula 09/09/10, pula 09/09/16→17? ver nota abaixo
    }

    @Test
    void mensalDiaFixoMantemODiaDoMes() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.MENSAL).intervalo(1)
                .tipoRecorrenciaMensal(TipoRecorrenciaMensal.DIA_FIXO).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 8, 15, 19, 0);

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 10, 20), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 15, 19, 0),
                LocalDateTime.of(2026, 10, 15, 19, 0));
    }

    @Test
    void mensalDiaDaSemanaMantemAPosicao() {
        // 1ª terça de agosto/2026 é dia 04.
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.MENSAL).intervalo(1)
                .tipoRecorrenciaMensal(TipoRecorrenciaMensal.DIA_DA_SEMANA).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 8, 4, 19, 0); // 1ª terça de agosto

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 10, 10), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 1, 19, 0),  // 1ª terça de setembro
                LocalDateTime.of(2026, 10, 6, 19, 0)); // 1ª terça de outubro
    }

    @Test
    void respeitaNumeroDeOcorrenciasRestante() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1)
                .numeroOcorrencias(3).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0);

        // ocorrenciasJaGeradas=1 (a primeira, criada na hora do cadastro) — só faltam 2.
        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 30), 1);

        assertThat(proximas).hasSize(2);
    }

    @Test
    void respeitaDataFim() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1)
                .dataFim(LocalDate.of(2026, 9, 4)).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0);

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 30), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 2, 19, 0),
                LocalDateTime.of(2026, 9, 3, 19, 0),
                LocalDateTime.of(2026, 9, 4, 19, 0));
    }
}
```

Nota do teste `semanalQuinzenalPulaUmaSemanaInteira`: "a cada 2 semanas" conta a partir da
**semana da série inteira** (ISO week), não da última ocorrência isolada — por isso só uma
data cabe na janela até 24/09 (17/09 é a próxima quinzena; 24/09 já seria a 3ª, fora do
intervalo par). Se ao implementar o cálculo real der uma data diferente por causa de como o
"a cada N semanas" é contado, ajuste o teste pra refletir a regra que a implementação define
— mas documente a regra escolhida no Javadoc do método (é uma ambiguidade legítima do domínio,
não um teste errado).

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test -Dtest=RecorrenciaCalculatorTest
```

Expected: FAIL (classe `RecorrenciaCalculator` não existe ainda).

- [ ] **Step 3: Implementar**

```java
package com.domus.api.modules.evento.serie;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Calcula as próximas datas de uma série, sem tocar banco — usado pelo job de
 *  materialização e, na criação, só pra validar que a regra gera pelo menos uma data. */
public class RecorrenciaCalculator {

    private RecorrenciaCalculator() {}

    public static List<LocalDateTime> proximasDatas(EventoSerie serie, LocalDateTime ultimaOcorrencia,
                                                      LocalDate limiteJanela, int ocorrenciasJaGeradas) {
        List<LocalDateTime> resultado = new ArrayList<>();
        LocalDateTime atual = ultimaOcorrencia;
        int geradas = ocorrenciasJaGeradas;

        while (true) {
            atual = proxima(serie, atual);
            if (atual.toLocalDate().isAfter(limiteJanela)) break;
            if (serie.getDataFim() != null && atual.toLocalDate().isAfter(serie.getDataFim())) break;
            if (serie.getNumeroOcorrencias() != null && geradas >= serie.getNumeroOcorrencias()) break;
            resultado.add(atual);
            geradas++;
        }
        return resultado;
    }

    private static LocalDateTime proxima(EventoSerie serie, LocalDateTime de) {
        return switch (serie.getFrequencia()) {
            case DIARIA -> de.plusDays(serie.getIntervalo());
            case SEMANAL -> proximaSemanal(serie, de);
            case MENSAL -> proximaMensal(serie, de);
        };
    }

    private static LocalDateTime proximaSemanal(EventoSerie serie, LocalDateTime de) {
        Set<DayOfWeek> dias = paraDayOfWeek(serie.getDiasSemanaComoSet());
        LocalDateTime candidata = de.plusDays(1);
        while (!dias.contains(candidata.getDayOfWeek())
                || semanasEntre(de, candidata) % serie.getIntervalo() != 0) {
            candidata = candidata.plusDays(1);
        }
        return candidata;
    }

    /** Conta em semanas ISO cheias a partir da primeira ocorrência da série (aproximação:
     *  a partir de {@code de}, que é sempre a última ocorrência gerada). */
    private static long semanasEntre(LocalDateTime origem, LocalDateTime candidata) {
        return java.time.temporal.ChronoUnit.WEEKS.between(
                origem.toLocalDate().with(java.time.DayOfWeek.MONDAY),
                candidata.toLocalDate().with(java.time.DayOfWeek.MONDAY));
    }

    private static LocalDateTime proximaMensal(EventoSerie serie, LocalDateTime de) {
        LocalDateTime proximoMes = de.plusMonths(serie.getIntervalo());
        if (serie.getTipoRecorrenciaMensal() == TipoRecorrenciaMensal.DIA_FIXO) {
            return proximoMes;
        }
        // DIA_DA_SEMANA: mesma posição (1ª, 2ª...) do mesmo dia da semana de `de`.
        DayOfWeek diaDaSemana = de.getDayOfWeek();
        int posicao = (de.getDayOfMonth() - 1) / 7 + 1;
        LocalDate primeiroDoMes = proximoMes.toLocalDate().withDayOfMonth(1);
        LocalDate resultado = primeiroDoMes.with(TemporalAdjusters.dayOfWeekInMonth(posicao, diaDaSemana));
        return resultado.atTime(de.toLocalTime());
    }

    private static Set<DayOfWeek> paraDayOfWeek(Set<com.domus.api.modules.celula.DiaSemana> dias) {
        Set<DayOfWeek> resultado = new java.util.HashSet<>();
        for (var d : dias) {
            resultado.add(switch (d) {
                case SEGUNDA -> DayOfWeek.MONDAY;
                case TERCA -> DayOfWeek.TUESDAY;
                case QUARTA -> DayOfWeek.WEDNESDAY;
                case QUINTA -> DayOfWeek.THURSDAY;
                case SEXTA -> DayOfWeek.FRIDAY;
                case SABADO -> DayOfWeek.SATURDAY;
                case DOMINGO -> DayOfWeek.SUNDAY;
            });
        }
        return resultado;
    }
}
```

- [ ] **Step 4: Rodar e ajustar até passar**

```bash
./mvnw -q -o test -Dtest=RecorrenciaCalculatorTest
```

Expected: PASS. Se `semanalQuinzenalPulaUmaSemanaInteira` não bater com a implementação
acima, ajuste o teste pra refletir a contagem de semanas que o código realmente faz (ver nota
no Step 1) — não force a implementação a mentir pro teste.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/serie/RecorrenciaCalculator.java \
        src/test/java/com/domus/api/modules/evento/serie/RecorrenciaCalculatorTest.java
git commit -m "feat(evento): RecorrenciaCalculator calcula proximas datas por frequencia"
```

---

## Task 3: DTOs de recorrência e `EventoResponse`/`EventoRequest`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/serie/DTOs/RecorrenciaRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/serie/DTOs/RecorrenciaRequestTest.java`

**Interfaces:**
- Produces: `RecorrenciaRequest(FrequenciaRecorrencia frequencia, Integer intervalo,
  Set<DiaSemana> diasSemana, TipoRecorrenciaMensal tipoRecorrenciaMensal, LocalDate dataFim,
  Integer numeroOcorrencias)`. `EventoRequest` ganha `RecorrenciaRequest recorrencia` (último
  campo do record, nulável). `EventoResponse` ganha `UUID serieId` e `boolean divergeDaSerie`.

- [ ] **Step 1: Escrever o teste de validação do `RecorrenciaRequest`**

```java
package com.domus.api.modules.evento.serie.DTOs;

import com.domus.api.modules.evento.serie.FrequenciaRecorrencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciaRequestTest {

    @Test
    void dataFimENumeroOcorrenciasJuntosRecusaComViolacao() {
        var request = new RecorrenciaRequest(
                FrequenciaRecorrencia.DIARIA, 1, Set.of(), null,
                LocalDate.now().plusDays(10), 5);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void somenteDataFimNaoGeraViolacao() {
        var request = new RecorrenciaRequest(
                FrequenciaRecorrencia.DIARIA, 1, Set.of(), null,
                LocalDate.now().plusDays(10), null);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void semFimNemContagemNaoGeraViolacao() {
        var request = new RecorrenciaRequest(
                FrequenciaRecorrencia.SEMANAL, 1, Set.of(com.domus.api.modules.celula.DiaSemana.QUINTA),
                null, null, null);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test -Dtest=RecorrenciaRequestTest
```

Expected: FAIL (classe não existe).

- [ ] **Step 3: Criar o DTO com validação cruzada**

```java
package com.domus.api.modules.evento.serie.DTOs;

import com.domus.api.modules.celula.DiaSemana;
import com.domus.api.modules.evento.serie.FrequenciaRecorrencia;
import com.domus.api.modules.evento.serie.TipoRecorrenciaMensal;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.Set;

public record RecorrenciaRequest(
        @NotNull(message = "A frequência é obrigatória.")
        FrequenciaRecorrencia frequencia,
        @Positive(message = "O intervalo deve ser maior que zero.")
        Integer intervalo,
        /** Só usado quando {@code frequencia == SEMANAL}. */
        Set<DiaSemana> diasSemana,
        /** Só usado quando {@code frequencia == MENSAL}. */
        TipoRecorrenciaMensal tipoRecorrenciaMensal,
        LocalDate dataFim,
        @Positive(message = "O número de ocorrências deve ser maior que zero.")
        Integer numeroOcorrencias
) {
    @AssertTrue(message = "Escolha uma data de fim OU um número de ocorrências, não os dois.")
    public boolean isFimValido() {
        return dataFim == null || numeroOcorrencias == null;
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./mvnw -q -o test -Dtest=RecorrenciaRequestTest
```

Expected: PASS.

- [ ] **Step 5: Adicionar `recorrencia` em `EventoRequest`**

Em `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`, adicionar como
último campo do record, com `@jakarta.validation.Valid`:

```java
        /** {@code null} = evento avulso. Preenchido = cria uma EventoSerie junto. */
        @jakarta.validation.Valid
        com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest recorrencia
```

- [ ] **Step 6: Adicionar `serieId`/`divergeDaSerie` em `EventoResponse`**

Em `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`, adicionar os campos
no record e nos dois métodos `from(...)` (ambos passam `e.getSerie() != null ? e.getSerie().getId() : null`
e `e.isDivergeDaSerie()`).

- [ ] **Step 7: Recompilar e rodar os testes de `EventoRequest`/`EventoResponse` existentes**

```bash
./mvnw -q -o test-compile
./mvnw -q -o test -Dtest=EventoRequestTest
```

Expected: compila limpo, `EventoRequestTest` continua PASS (campo novo é nulável, não quebra
nenhum request existente).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/serie/DTOs/ \
        src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java \
        src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java \
        src/test/java/com/domus/api/modules/evento/serie/DTOs/
git commit -m "feat(evento): RecorrenciaRequest e campos de serie em EventoRequest/Response"
```

---

## Task 4: `cadastrarEvento` cria a série + primeira ocorrência

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `EventoSerieRepository` (Task 1), `RecorrenciaRequest` (Task 3).
- Produces: `EventoService` ganha o construtor com `EventoSerieRepository` como novo
  parâmetro (**atualizar todo `new EventoService(...)` manual nos testes** — rodar
  `grep -rn "new EventoService(" src/test` pra achar todos os sites antes de mexer).

- [ ] **Step 1: Escrever o teste**

```java
@Test
void cadastrarEventoComRecorrenciaCriaASerieEAPrimeiraOcorrencia() {
    var recorrencia = new com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest(
            com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL, 1,
            java.util.Set.of(com.domus.api.modules.celula.DiaSemana.QUINTA), null, null, null);
    EventoRequest req = requestComRecorrencia(recorrencia);
    when(eventoSerieRepository.save(any())).thenAnswer(inv -> {
        var serie = (com.domus.api.modules.evento.serie.EventoSerie) inv.getArgument(0);
        serie.setId(UUID.randomUUID());
        return serie;
    });

    EventoResponse response = service.cadastrarEvento(req, igrejaId, usuarioId);

    assertThat(response.serieId()).isNotNull();
    verify(eventoRepository).save(argThat(e -> e.getSerie() != null && !e.isDivergeDaSerie()));
}

@Test
void cadastrarEventoSemRecorrenciaNaoCriaSerie() {
    EventoRequest req = requestComRestricao(false);
    service.cadastrarEvento(req, igrejaId, usuarioId);
    verify(eventoSerieRepository, never()).save(any());
    verify(eventoRepository).save(argThat(e -> e.getSerie() == null));
}
```

Adicionar também o helper `requestComRecorrencia`:

```java
private EventoRequest requestComRecorrencia(com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest recorrencia) {
    return new EventoRequest(
            "Culto Dominical", "Descrição do evento", LocalDateTime.now().plusDays(1),
            null, null, "Salão Social", "Culto", null, null, null, null, null, null,
            false, false, false, false, null, recorrencia);
}
```

Ajustar `requestComResponsavel`/`requestComRestricao` pra passar `null` como último argumento
(o `recorrencia` novo) em todo lugar que constrói `EventoRequest` inline nesse arquivo.

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test-compile
```

Expected: FAIL (não compila — `EventoService` ainda não tem `EventoSerieRepository`, e
`EventoRequest` novo campo não existe nesse construtor de teste até o Task 3 estar mergeado).

- [ ] **Step 3: Injetar `EventoSerieRepository` e implementar**

Em `EventoService.java`, adicionar o campo/construtor (via `@RequiredArgsConstructor`, é só
adicionar o `private final EventoSerieRepository eventoSerieRepository;` junto dos outros) e,
em `cadastrarEvento`, antes do `return EventoResponse.from(...)`:

```java
        if (data.recorrencia() != null) {
            var serie = criarSerie(data.recorrencia(), igreja, usuario);
            salvo.setSerie(serie);
            salvo = eventoRepository.save(salvo);
        }
```

E o método privado:

```java
    private com.domus.api.modules.evento.serie.EventoSerie criarSerie(
            com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest data, Igreja igreja, Usuario usuario) {
        String dias = data.diasSemana() == null || data.diasSemana().isEmpty()
                ? null
                : data.diasSemana().stream().map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(","));
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder()
                .igreja(igreja)
                .frequencia(data.frequencia())
                .intervalo(data.intervalo() == null ? 1 : data.intervalo())
                .diasSemana(dias)
                .tipoRecorrenciaMensal(data.tipoRecorrenciaMensal())
                .dataFim(data.dataFim())
                .numeroOcorrencias(data.numeroOcorrencias())
                .criadoPor(usuario)
                .build();
        return eventoSerieRepository.save(serie);
    }
```

- [ ] **Step 4: Atualizar todo site que instancia `EventoService` manualmente**

```bash
grep -rln "new EventoService(" src/test
```

Pra cada arquivo encontrado, adicionar um mock `EventoSerieRepository eventoSerieRepository =
mock(EventoSerieRepository.class);` no setup e passar como novo argumento (sempre por último,
já que `@RequiredArgsConstructor` respeita a ordem de declaração dos campos) na chamada
`new EventoService(...)`.

- [ ] **Step 5: Rodar e confirmar que passa**

```bash
set -a; source .env >/dev/null 2>&1; set +a
./mvnw -q -o test -Dtest=EventoServiceTest,EventoServiceCamposInscricaoTest,EventoTipoENormalizacaoTest
```

Expected: PASS em tudo.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/
git commit -m "feat(evento): cadastrarEvento cria EventoSerie e a primeira ocorrencia"
```

---

## Task 5: Job de materialização

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/serie/EventoSerieMaterializacaoJob.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Test: `src/test/java/com/domus/api/modules/evento/serie/EventoSerieMaterializacaoJobTest.java`

**Interfaces:**
- Consumes: `EventoSerieRepository.findByIgrejaIdAndAtivaTrue` (Task 1),
  `RecorrenciaCalculator.proximasDatas` (Task 2).
- Produces: `EventoRepository` ganha
  `Optional<Evento> findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(UUID serieId)` e
  `boolean existsBySerieIdAndInicioEm(UUID serieId, LocalDateTime inicioEm)` (esta última
  **sem** o `@SQLRestriction` de soft-delete — precisa achar mesmo arquivado; ver Step 2).

- [ ] **Step 1: Escrever o teste (Mockito puro)**

```java
package com.domus.api.modules.evento.serie;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventoSerieMaterializacaoJobTest {

    EventoSerieRepository serieRepository;
    EventoRepository eventoRepository;
    EventoSerieMaterializacaoJob job;

    UUID igrejaId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        serieRepository = mock(EventoSerieRepository.class);
        eventoRepository = mock(EventoRepository.class);
        job = new EventoSerieMaterializacaoJob(serieRepository, eventoRepository);
    }

    @Test
    void materializaOcorrenciaNovaQuandoNaoExisteAindaParaAData() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        EventoSerie serie = EventoSerie.builder()
                .id(serieId).igreja(igreja)
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1).build();
        when(serieRepository.findByIgrejaIdAndAtivaTrue(any())).thenReturn(List.of());
        when(serieRepository.findAll()).thenReturn(List.of(serie));

        Evento ultima = Evento.builder()
                .igreja(igreja).titulo("Culto").inicioEm(LocalDateTime.now().minusDays(1))
                .serie(serie).divergeDaSerie(false).build();
        when(eventoRepository.findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(serieId))
                .thenReturn(Optional.of(ultima));
        when(eventoRepository.existsBySerieIdAndInicioEm(eq(serieId), any())).thenReturn(false);
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.materializar();

        verify(eventoRepository, atLeastOnce()).save(argThat(e ->
                e.getSerie() != null && e.getSerie().getId().equals(serieId)
                        && e.getTitulo().equals("Culto") && !e.isDivergeDaSerie()));
    }

    @Test
    void naoMaterializaDataQueJaExisteParaASerie() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        EventoSerie serie = EventoSerie.builder()
                .id(serieId).igreja(igreja)
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1).build();
        when(serieRepository.findAll()).thenReturn(List.of(serie));

        Evento ultima = Evento.builder()
                .igreja(igreja).titulo("Culto").inicioEm(LocalDateTime.now().minusDays(1))
                .serie(serie).divergeDaSerie(false).build();
        when(eventoRepository.findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(serieId))
                .thenReturn(Optional.of(ultima));
        // Toda data já existe (inclusive as arquivadas) — nada deve ser materializado.
        when(eventoRepository.existsBySerieIdAndInicioEm(eq(serieId), any())).thenReturn(true);

        job.materializar();

        verify(eventoRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Adicionar as queries em `EventoRepository`**

```java
    Optional<Evento> findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(UUID serieId);

    /** Sem @SQLRestriction de propósito — soft-deletado (feriado cancelado) também conta,
     *  senão o job de materialização ressuscitaria a data no próximo dia de rodagem. */
    @Query(value = "SELECT COUNT(*) > 0 FROM evento WHERE serie_id = :serieId AND inicio_em = :inicioEm",
           nativeQuery = true)
    boolean existsBySerieIdAndInicioEm(@Param("serieId") UUID serieId, @Param("inicioEm") LocalDateTime inicioEm);
```

(`@SQLRestriction` na entidade só afeta consultas JPQL via o "caminho normal" do Hibernate —
nativa passa direto, mesma técnica que outras queries do projeto já usam pra bypassar soft
delete quando precisam enxergar arquivado.)

- [ ] **Step 3: Rodar e confirmar que falha**

```bash
./mvnw -q -o test-compile
```

Expected: FAIL (`EventoSerieMaterializacaoJob` não existe).

- [ ] **Step 4: Implementar o job**

```java
package com.domus.api.modules.evento.serie;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Materializa ocorrências futuras de série numa janela móvel — mesmo padrão de
 *  ExclusaoIgrejaJob/LimpezaFotosJob (cron diário, de madrugada, depois do backup). */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventoSerieMaterializacaoJob {

    private static final int JANELA_DIAS = 60;

    private final EventoSerieRepository serieRepository;
    private final EventoRepository eventoRepository;

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void materializar() {
        List<EventoSerie> series = serieRepository.findAll().stream()
                .filter(EventoSerie::isAtiva)
                .toList();
        LocalDate limite = LocalDate.now().plusDays(JANELA_DIAS);
        int totalCriadas = 0;

        for (EventoSerie serie : series) {
            totalCriadas += materializarSerie(serie, limite);
        }
        log.info("Materialização de séries concluída. series_processadas={}, ocorrencias_criadas={}",
                series.size(), totalCriadas);
    }

    private int materializarSerie(EventoSerie serie, LocalDate limite) {
        Evento ultima = eventoRepository
                .findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(serie.getId())
                .orElse(null);
        if (ultima == null) return 0; // série sem nenhuma ocorrência não-divergente pra clonar

        List<LocalDateTime> proximasDatas = RecorrenciaCalculator.proximasDatas(
                serie, ultima.getInicioEm(), limite, 1);

        int criadas = 0;
        for (LocalDateTime data : proximasDatas) {
            if (eventoRepository.existsBySerieIdAndInicioEm(serie.getId(), data)) continue;
            eventoRepository.save(clonar(ultima, data));
            criadas++;
        }
        return criadas;
    }

    private Evento clonar(Evento origem, LocalDateTime novaData) {
        long duracaoMinutos = origem.getFimEm() == null ? -1
                : java.time.Duration.between(origem.getInicioEm(), origem.getFimEm()).toMinutes();
        return Evento.builder()
                .igreja(origem.getIgreja())
                .titulo(origem.getTitulo())
                .descricao(origem.getDescricao())
                .inicioEm(novaData)
                .fimEm(duracaoMinutos < 0 ? null : novaData.plusMinutes(duracaoMinutos))
                .local(origem.getLocal())
                .localTexto(origem.getLocalTexto())
                .tipo(origem.getTipo())
                .responsavel(origem.getResponsavel())
                .recorteEtario(origem.getRecorteEtario())
                .idadeMin(origem.getIdadeMin())
                .idadeMax(origem.getIdadeMax())
                .restricaoEstadoCivil(origem.getRestricaoEstadoCivil())
                .restricaoSexo(origem.getRestricaoSexo())
                .foto(origem.getFoto())
                .vagas(origem.getVagas())
                .preco(origem.getPreco())
                .exclusivoMembros(origem.isExclusivoMembros())
                .requerInscricao(origem.isRequerInscricao())
                .controlaPresenca(origem.isControlaPresenca())
                .restritoPropriaIgreja(origem.isRestritoPropriaIgreja())
                .serie(origem.getSerie())
                .divergeDaSerie(false)
                .build();
    }
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

```bash
./mvnw -q -o test -Dtest=EventoSerieMaterializacaoJobTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/serie/EventoSerieMaterializacaoJob.java \
        src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/test/java/com/domus/api/modules/evento/serie/EventoSerieMaterializacaoJobTest.java
git commit -m "feat(evento): job diario materializa ocorrencias futuras de serie"
```

---

## Task 6: Escopo `ESTA` — editar/cancelar só uma ocorrência

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `EscopoEdicaoEvento` (Task 1).
- Produces: `atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId,
  boolean cancelarNaoElegiveis, EscopoEdicaoEvento escopo)` — assinatura nova, escopo é o
  **último** parâmetro. `arquivarEvento(UUID id, UUID igrejaId, UUID usuarioId,
  EscopoEdicaoEvento escopo)` idem.

- [ ] **Step 1: Escrever o teste**

```java
@Test
void atualizarEventoComEscopoEstaMarcaDivergeDaSerie() {
    UUID eventoId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();
    Evento existente = Evento.builder()
            .id(eventoId)
            .igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical")
            .inicioEm(LocalDateTime.now().plusDays(1))
            .serie(com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).build())
            .build();
    when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

    EventoRequest req = requestComRestricao(false);
    service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
            com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

    assertThat(existente.isDivergeDaSerie()).isTrue();
}

@Test
void atualizarEventoAvulsoIgnoraEscopo() {
    UUID eventoId = UUID.randomUUID();
    Evento existente = Evento.builder()
            .id(eventoId)
            .igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical")
            .inicioEm(LocalDateTime.now().plusDays(1))
            .build(); // sem série
    when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

    EventoRequest req = requestComRestricao(false);
    service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
            com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

    assertThat(existente.isDivergeDaSerie()).isFalse(); // nunca marca quem não tem série
}
```

Ajustar todo `service.atualizarEvento(eventoId, req, igrejaId, usuarioId)` já existente no
arquivo pra passar `com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA` como último
argumento (comportamento padrão idêntico ao de hoje) — usar `grep -n
"service.atualizarEvento(" EventoServiceTest.java` pra achar todos.

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test-compile
```

Expected: FAIL.

- [ ] **Step 3: Implementar em `EventoService`**

Trocar a assinatura de `atualizarEvento` (as duas sobrecargas — a de 4 args vira a de 5,
adicionando escopo com valor padrão `ESTA` internamente; a de 5 vira 6):

```java
    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId,
                                          com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        return atualizarEvento(id, data, igrejaId, usuarioId, false, escopo);
    }

    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId,
                                          boolean cancelarNaoElegiveis,
                                          com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        // ... corpo existente até salvar o evento continua igual ...
```

Logo após `Evento salvo = eventoRepository.save(evento);` (mesmo ponto onde hoje já roda a
notificação de data/local), adicionar:

```java
        if (evento.getSerie() != null
                && escopo == com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA) {
            evento.setDivergeDaSerie(true);
            salvo = eventoRepository.save(evento);
        }
```

(Escopos `SERIE`/`ESTA_E_SEGUINTES` ficam pras Tasks 7 e 8 — por ora, qualquer valor diferente
de `ESTA` se comporta como `ESTA` até essas tasks entrarem.)

- [ ] **Step 4: Atualizar `EventoController`**

```java
    @PutMapping("/{id}")
    public ResponseEntity<EventoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EventoRequest data,
            @RequestParam(defaultValue = "false") boolean cancelarNaoElegiveis,
            @RequestParam(defaultValue = "ESTA") com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return ResponseEntity.ok(eventoService.atualizarEvento(
                id, data, igrejaId, usuarioId, cancelarNaoElegiveis, escopo));
    }
```

- [ ] **Step 5: Atualizar todo outro caller de `atualizarEvento`/`arquivarEvento` fora do teste**

```bash
grep -rn "\.atualizarEvento(\|\.arquivarEvento(" src/main
```

Cada call site que não seja o `EventoController` (ex.: `InscricaoService` chamando
`removerInscritosNaoElegiveis` indiretamente não conta — é `atualizarEvento` mesmo que
importa) passa `EscopoEdicaoEvento.ESTA` como argumento adicional.

- [ ] **Step 6: Rodar e confirmar que passa**

```bash
set -a; source .env >/dev/null 2>&1; set +a
./mvnw -q -o test
```

Expected: PASS em toda a suíte (não só o arquivo tocado — assinatura mudou, precisa validar
que nada mais quebrou).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/main/java/com/domus/api/modules/evento/EventoController.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): escopo ESTA marca divergeDaSerie ao editar uma ocorrencia"
```

---

## Task 7: Escopo `SERIE` — editar/cancelar todas as ocorrências futuras

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Produces: `EventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(UUID serieId,
  LocalDateTime de)` — usada pra achar as ocorrências futuras da série (o filtro por
  `AGENDADO` é feito em memória, reusando `Evento.getSituacao()` que já existe).

- [ ] **Step 1: Escrever o teste**

```java
@Test
void atualizarEventoComEscopoSerieAtualizaTodasAsFuturasAgendadas() {
    UUID eventoId = UUID.randomUUID();
    UUID outraOcorrenciaId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();
    var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).build();
    Evento existente = Evento.builder()
            .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1))
            .serie(serie).build();
    Evento outraFutura = Evento.builder()
            .id(outraOcorrenciaId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(8))
            .serie(serie).divergeDaSerie(true).build(); // divergência antiga — deve ser limpa
    when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
    when(eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(eq(serieId), any()))
            .thenReturn(List.of(existente, outraFutura));

    EventoRequest req = requestComRestricao(false); // muda algo, ex. localTexto
    service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
            com.domus.api.modules.evento.serie.EscopoEdicaoEvento.SERIE);

    assertThat(outraFutura.getTitulo()).isEqualTo(req.titulo());
    assertThat(outraFutura.isDivergeDaSerie()).isFalse();
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test-compile
```

Expected: FAIL (`findBySerieIdAndInicioEmGreaterThanEqual` não existe).

- [ ] **Step 3: Adicionar a query e implementar o escopo**

```java
    List<Evento> findBySerieIdAndInicioEmGreaterThanEqual(UUID serieId, LocalDateTime de);
```

Em `EventoService`, no ponto identificado no Task 6 (onde hoje trata `ESTA`), estender:

```java
        if (evento.getSerie() != null) {
            switch (escopo) {
                case ESTA -> {
                    evento.setDivergeDaSerie(true);
                    salvo = eventoRepository.save(evento);
                }
                case SERIE -> salvo = propagarParaSerie(salvo, igrejaId);
                case ESTA_E_SEGUINTES -> { /* Task 8 */ }
            }
        }
```

```java
    /** Copia os campos editáveis pra toda ocorrência AGENDADO da mesma série — limpa
     *  divergeDaSerie de todas (edição de série sempre vence uma divergência antiga). */
    private Evento propagarParaSerie(Evento editado, UUID igrejaId) {
        List<Evento> futuras = eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(
                editado.getSerie().getId(), editado.getInicioEm());
        for (Evento ocorrencia : futuras) {
            if (ocorrencia.getId().equals(editado.getId())) continue;
            if (ocorrencia.getSituacao() != SituacaoEvento.AGENDADO) continue;
            ocorrencia.setTitulo(editado.getTitulo());
            ocorrencia.setDescricao(editado.getDescricao());
            ocorrencia.setLocal(editado.getLocal());
            ocorrencia.setLocalTexto(editado.getLocalTexto());
            ocorrencia.setTipo(editado.getTipo());
            ocorrencia.setResponsavel(editado.getResponsavel());
            ocorrencia.setRecorteEtario(editado.getRecorteEtario());
            ocorrencia.setIdadeMin(editado.getIdadeMin());
            ocorrencia.setIdadeMax(editado.getIdadeMax());
            ocorrencia.setRestricaoEstadoCivil(editado.getRestricaoEstadoCivil());
            ocorrencia.setRestricaoSexo(editado.getRestricaoSexo());
            ocorrencia.setVagas(editado.getVagas());
            ocorrencia.setPreco(editado.getPreco());
            ocorrencia.setExclusivoMembros(editado.isExclusivoMembros());
            ocorrencia.setRequerInscricao(editado.isRequerInscricao());
            ocorrencia.setControlaPresenca(editado.isControlaPresenca());
            ocorrencia.setRestritoPropriaIgreja(editado.isRestritoPropriaIgreja());
            ocorrencia.setDivergeDaSerie(false);
            eventoRepository.save(ocorrencia);
        }
        editado.setDivergeDaSerie(false);
        return eventoRepository.save(editado);
    }
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
set -a; source .env >/dev/null 2>&1; set +a
./mvnw -q -o test -Dtest=EventoServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): escopo SERIE propaga edicao pras ocorrencias futuras agendadas"
```

---

## Task 8: Escopo `ESTA_E_SEGUINTES` — divide a série

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `EventoSerieRepository`, `EventoRepository.findBySerieIdAndInicioEmGreaterThanEqual`
  (Task 7).

- [ ] **Step 1: Escrever o teste**

```java
@Test
void atualizarEventoComEscopoEstaESeguintesDivideASerie() {
    UUID eventoId = UUID.randomUUID();
    UUID outraFuturaId = UUID.randomUUID();
    UUID serieAntigaId = UUID.randomUUID();
    var serieAntiga = com.domus.api.modules.evento.serie.EventoSerie.builder()
            .id(serieAntigaId)
            .frequencia(com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL)
            .intervalo(1).diasSemana("QUINTA").build();
    Evento existente = Evento.builder()
            .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1))
            .serie(serieAntiga).build();
    Evento outraFutura = Evento.builder()
            .id(outraFuturaId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(8))
            .serie(serieAntiga).build();
    when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
    when(eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(eq(serieAntigaId), any()))
            .thenReturn(List.of(existente, outraFutura));
    when(eventoSerieRepository.save(any())).thenAnswer(inv -> {
        var s = (com.domus.api.modules.evento.serie.EventoSerie) inv.getArgument(0);
        if (s.getId() == null) s.setId(UUID.randomUUID());
        return s;
    });

    EventoRequest req = requestComRestricao(false);
    service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
            com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA_E_SEGUINTES);

    assertThat(serieAntiga.isAtiva()).isFalse();
    assertThat(outraFutura.getSerie()).isNotSameAs(serieAntiga);
    assertThat(outraFutura.getSerie().getFrequencia())
            .isEqualTo(com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL);
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test -Dtest=EventoServiceTest
```

Expected: FAIL (`case ESTA_E_SEGUINTES` ainda vazio).

- [ ] **Step 3: Implementar**

Completar o `switch` do Task 7:

```java
                case ESTA_E_SEGUINTES -> salvo = dividirSerie(salvo, igrejaId);
```

```java
    /** "Esta e as seguintes": encerra a série atual na véspera desta ocorrência, cria uma
     *  série nova (clone da regra) e reponta essa ocorrência + as futuras agendadas pra ela. */
    private Evento dividirSerie(Evento editado, UUID igrejaId) {
        var antiga = editado.getSerie();
        antiga.setDataFim(editado.getInicioEm().toLocalDate().minusDays(1));
        antiga.setNumeroOcorrencias(null); // CHECK de exclusão mútua no banco
        eventoSerieRepository.save(antiga);

        var nova = com.domus.api.modules.evento.serie.EventoSerie.builder()
                .igreja(antiga.getIgreja())
                .frequencia(antiga.getFrequencia())
                .intervalo(antiga.getIntervalo())
                .diasSemana(antiga.getDiasSemana())
                .tipoRecorrenciaMensal(antiga.getTipoRecorrenciaMensal())
                .criadoPor(antiga.getCriadoPor())
                .build();
        nova = eventoSerieRepository.save(nova);

        List<Evento> futuras = eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(
                antiga.getId(), editado.getInicioEm());
        for (Evento ocorrencia : futuras) {
            if (ocorrencia.getSituacao() != SituacaoEvento.AGENDADO
                    && !ocorrencia.getId().equals(editado.getId())) continue;
            ocorrencia.setSerie(nova);
            ocorrencia.setDivergeDaSerie(false);
            if (!ocorrencia.getId().equals(editado.getId())) {
                // mesmo tratamento de campos do escopo SERIE
                ocorrencia.setTitulo(editado.getTitulo());
                ocorrencia.setDescricao(editado.getDescricao());
                ocorrencia.setLocal(editado.getLocal());
                ocorrencia.setLocalTexto(editado.getLocalTexto());
            }
            eventoRepository.save(ocorrencia);
        }
        editado.setSerie(nova);
        editado.setDivergeDaSerie(false);
        return eventoRepository.save(editado);
    }
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
set -a; source .env >/dev/null 2>&1; set +a
./mvnw -q -o test
```

Expected: PASS em toda a suíte.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): escopo ESTA_E_SEGUINTES divide a serie a partir da ocorrencia"
```

---

## Task 9: Cancelamento com escopo (`arquivarEvento`)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Produces: `arquivarEvento(UUID id, UUID igrejaId, UUID usuarioId, EscopoEdicaoEvento escopo)`.

- [ ] **Step 1: Escrever o teste**

```java
@Test
void arquivarEventoComEscopoSerieArquivaTodasAsFuturasEDesativaASerie() {
    UUID eventoId = UUID.randomUUID();
    UUID outraFuturaId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();
    var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).ativa(true).build();
    Evento existente = Evento.builder()
            .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).serie(serie).build();
    Evento outraFutura = Evento.builder()
            .id(outraFuturaId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(8)).serie(serie).build();
    when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
    when(eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(eq(serieId), any()))
            .thenReturn(List.of(existente, outraFutura));

    service.arquivarEvento(eventoId, igrejaId, usuarioId,
            com.domus.api.modules.evento.serie.EscopoEdicaoEvento.SERIE);

    verify(eventoRepository).delete(existente); // delete = soft delete, via @SQLDelete
    verify(eventoRepository).delete(outraFutura);
    assertThat(serie.isAtiva()).isFalse();
}

@Test
void arquivarEventoComEscopoEstaArquivaSoAqueleDia() {
    UUID eventoId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();
    var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).ativa(true).build();
    Evento existente = Evento.builder()
            .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
            .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).serie(serie).build();
    when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

    service.arquivarEvento(eventoId, igrejaId, usuarioId,
            com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

    verify(eventoRepository).delete(existente);
    verify(eventoRepository, never()).findBySerieIdAndInicioEmGreaterThanEqual(any(), any());
    assertThat(serie.isAtiva()).isTrue(); // série continua — só esta ocorrência sumiu
}
```

Ajustar as chamadas antigas de `service.arquivarEvento(eventoId, igrejaId, usuarioId)` já
existentes no arquivo pra passar `EscopoEdicaoEvento.ESTA` como último argumento.

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test-compile
```

Expected: FAIL.

- [ ] **Step 3: Implementar**

```java
    @Transactional
    public void arquivarEvento(UUID id, UUID igrejaId, UUID usuarioId,
                               com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        log.info("Arquivando evento. id={}, igreja_id={}", id, igrejaId);
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        if (evento.getSituacao() == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Não é possível arquivar um evento em andamento.");
        }

        List<Evento> paraArquivar = List.of(evento);
        if (evento.getSerie() != null
                && escopo != com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA) {
            paraArquivar = eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(
                    evento.getSerie().getId(), evento.getInicioEm());
            evento.getSerie().setAtiva(false);
            eventoSerieRepository.save(evento.getSerie());
        }

        for (Evento ocorrencia : paraArquivar) {
            if (ocorrencia.getSituacao() == SituacaoEvento.EM_ANDAMENTO) continue;
            notificarInscritos(ocorrencia, igrejaId, usuarioId,
                    "O evento \"" + ocorrencia.getTitulo() + "\" foi cancelado.", "/eventos");
            eventoRepository.delete(ocorrencia);
            outboxRegistrador.registrar(TipoEntidadeOutbox.EVENTO, TipoEventoOutbox.REMOVIDO,
                    ocorrencia.getId(), igrejaId);
        }
        log.info("Evento(s) arquivado(s). id={}, igreja_id={}, total={}", id, igrejaId, paraArquivar.size());
        evictarCacheDeEventosDaFamilia(igrejaId);
    }
```

(Isso substitui o corpo antigo de `arquivarEvento` — a versão de 3 argumentos que a Fase de
notificações introduziu vira só a de 4, sem sobrecarga extra; todo caller já foi ajustado no
Step 1/atualização de callers.)

- [ ] **Step 4: Atualizar `EventoController`**

```java
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ESTA") com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        eventoService.arquivarEvento(id, igrejaId, usuarioId, escopo);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 5: Rodar toda a suíte**

```bash
set -a; source .env >/dev/null 2>&1; set +a
./mvnw -q -o test
```

Expected: PASS (checar em especial `EventoArquivamentoNotificaInscritosTest`, que já cobre o
caso real de `TransientObjectException` corrigido antes — precisa continuar passando com a
assinatura nova).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/main/java/com/domus/api/modules/evento/EventoController.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): arquivarEvento aceita escopo (esta/serie/esta-e-seguintes)"
```

---

## Task 10: Notificação — texto diferente pra ocorrência de série

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `notificarNovoEvento` (já existe, produtor de `NOVO_EVENTO`).

- [ ] **Step 1: Escrever o teste**

```java
@Test
void cadastrarEventoDeSerieNotificaComTextoDeLembrete() {
    UUID outroUsuarioId = UUID.randomUUID();
    when(usuarioRepository.findIdsAtivosPorIgreja(igrejaId)).thenReturn(List.of(outroUsuarioId));
    when(eventoSerieRepository.save(any())).thenAnswer(inv -> {
        var s = (com.domus.api.modules.evento.serie.EventoSerie) inv.getArgument(0);
        s.setId(UUID.randomUUID());
        return s;
    });
    var recorrencia = new com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest(
            com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL, 1,
            java.util.Set.of(com.domus.api.modules.celula.DiaSemana.QUINTA), null, null, null);
    EventoRequest req = requestComRecorrencia(recorrencia);

    service.cadastrarEvento(req, igrejaId, usuarioId);

    verify(notificacaoService).criar(
            eq(com.domus.api.modules.notificacao.TipoNotificacao.NOVO_EVENTO), eq(igrejaId),
            eq(outroUsuarioId),
            argThat(texto -> !texto.startsWith("Novo evento") && texto.contains("Vem participar")),
            anyString());
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
./mvnw -q -o test -Dtest=EventoServiceTest
```

Expected: FAIL (texto ainda sempre "Novo evento: ...").

- [ ] **Step 3: Implementar o texto condicional**

Em `notificarNovoEvento` (método privado já existente), trocar o texto fixo por:

```java
    private void notificarNovoEvento(Evento evento, UUID igrejaId, UUID usuarioIdAtor) {
        List<UUID> usuarioIds = usuarioRepository.findIdsAtivosPorIgreja(igrejaId);
        String texto = evento.getSerie() != null
                ? textoLembreteDeSerie(evento)
                : "Novo evento: \"" + evento.getTitulo() + "\". Dá uma olhada!";
        for (UUID usuarioId : usuarioIds) {
            if (usuarioId.equals(usuarioIdAtor)) continue;
            notificacaoService.criar(
                    com.domus.api.modules.notificacao.TipoNotificacao.NOVO_EVENTO,
                    igrejaId, usuarioId, texto, "/eventos?detalhe=" + evento.getId());
        }
    }

    private static final java.time.format.DateTimeFormatter FORMATADOR_LEMBRETE =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", new java.util.Locale("pt", "BR"));

    private String textoLembreteDeSerie(Evento evento) {
        String diaDaSemana = evento.getInicioEm().getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
        return evento.getTitulo() + " é " + diaDaSemana + ", "
                + evento.getInicioEm().format(FORMATADOR_LEMBRETE) + ". Vem participar!";
    }
```

- [ ] **Step 4: Rodar e confirmar que passa**

```bash
./mvnw -q -o test -Dtest=EventoServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): notificacao de serie usa texto de lembrete, nao de anuncio"
```

---

## Task 11: Frontend — types e service

**Files:**
- Modify: `frontend/src/types/evento.type.ts`
- Modify: `frontend/src/services/evento.service.ts`
- Modify: `frontend/src/lib/endpoints.ts` (se `atualizar`/`arquivar` de evento passam por lá)

**Interfaces:**
- Produces: `RecorrenciaRequest` (tipo TS), `EscopoEdicaoEvento` (`'ESTA' |
  'ESTA_E_SEGUINTES' | 'SERIE'`), `EventoResponse.serieId: string | null`,
  `EventoResponse.divergeDaSerie: boolean`. `eventosService.atualizar(id, payload, opts)` e
  `eventosService.arquivar(id, opts)` ganham um parâmetro opcional `{ escopo?:
  EscopoEdicaoEvento }`.

- [ ] **Step 1: Adicionar os tipos**

Em `frontend/src/types/evento.type.ts`:

```typescript
export type FrequenciaRecorrencia = 'DIARIA' | 'SEMANAL' | 'MENSAL'
export type TipoRecorrenciaMensal = 'DIA_FIXO' | 'DIA_DA_SEMANA'
export type EscopoEdicaoEvento = 'ESTA' | 'ESTA_E_SEGUINTES' | 'SERIE'
export type DiaSemana = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO'

export interface RecorrenciaRequest {
  frequencia: FrequenciaRecorrencia
  intervalo: number
  diasSemana?: DiaSemana[]
  tipoRecorrenciaMensal?: TipoRecorrenciaMensal | null
  dataFim?: string | null
  numeroOcorrencias?: number | null
}
```

E no `EventoRequest`/`EventoResponse` já existentes: `recorrencia?: RecorrenciaRequest | null`
no request; `serieId: string | null` e `divergeDaSerie: boolean` no response.

- [ ] **Step 2: Atualizar o service**

Localizar `atualizar`/`arquivar` em `frontend/src/services/evento.service.ts` e adicionar o
parâmetro `escopo` como query string:

```typescript
  atualizar: (id: string, data: EventoRequest, escopo?: EscopoEdicaoEvento) =>
    api.put<EventoResponse>(`${Endpoints.eventos.BASE}/${id}`, data, {
      params: escopo ? { escopo } : undefined,
    }).then((res) => res.data),

  arquivar: (id: string, escopo?: EscopoEdicaoEvento) =>
    api.delete(`${Endpoints.eventos.BASE}/${id}`, {
      params: escopo ? { escopo } : undefined,
    }),
```

(Ajustar pro nome real do método/endpoint já existente no arquivo — o service já tem
`atualizar`/`arquivar`, só adicionar o parâmetro novo mantendo os existentes funcionando sem
`escopo` — default do backend já é `ESTA`.)

- [ ] **Step 3: Typecheck**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus/frontend
npx tsc --noEmit
```

Expected: sem erros (pode dar erro nos callers de `atualizar`/`arquivar` que ainda não passam
o novo parâmetro opcional — é opcional, não deveria quebrar nada existente).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/evento.type.ts frontend/src/services/evento.service.ts
git commit -m "feat(evento): types e service da recorrencia no frontend"
```

---

## Task 12: Frontend — toggle "Repetir" no formulário de criar evento

**Files:**
- Modify: `frontend/src/hooks/evento/useEventoForm.ts`
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx`
- Modify: `frontend/src/lib/validators.ts` (ou onde `eventoSchema` vive)

**Interfaces:**
- Consumes: `RecorrenciaRequest`, `DiaSemana` (Task 11), o seletor de dias da semana que a
  Célula já usa (mesmo componente/estilo visual, não reinventar).

- [ ] **Step 1: Estender `eventoSchema` com os campos de recorrência**

No arquivo onde `eventoSchema` é definido (`frontend/src/lib/validators.ts` ou equivalente —
localizar com `grep -rn "eventoSchema" frontend/src/lib`), adicionar:

```typescript
  repetir: z.boolean().optional(),
  recorrenciaFrequencia: z.enum(['DIARIA', 'SEMANAL', 'MENSAL']).optional(),
  recorrenciaIntervalo: z.coerce.number().positive().optional(),
  recorrenciaDiasSemana: z.array(z.string()).optional(),
  recorrenciaTipoMensal: z.enum(['DIA_FIXO', 'DIA_DA_SEMANA']).optional(),
  recorrenciaFimTipo: z.enum(['NUNCA', 'DATA', 'CONTAGEM']).optional(),
  recorrenciaDataFim: z.string().optional(),
  recorrenciaNumeroOcorrencias: z.coerce.number().positive().optional(),
```

- [ ] **Step 2: Montar o `RecorrenciaRequest` no payload de envio**

Em `useEventoForm.ts`, no ponto onde o `EventoRequest` é montado antes de chamar
`eventosService.criar(...)`, adicionar:

```typescript
    const recorrencia: RecorrenciaRequest | null = !data.repetir ? null : {
      frequencia: data.recorrenciaFrequencia!,
      intervalo: data.recorrenciaIntervalo ?? 1,
      diasSemana: data.recorrenciaFrequencia === 'SEMANAL'
        ? (data.recorrenciaDiasSemana as DiaSemana[]) : undefined,
      tipoRecorrenciaMensal: data.recorrenciaFrequencia === 'MENSAL'
        ? data.recorrenciaTipoMensal : undefined,
      dataFim: data.recorrenciaFimTipo === 'DATA' ? data.recorrenciaDataFim : undefined,
      numeroOcorrencias: data.recorrenciaFimTipo === 'CONTAGEM'
        ? data.recorrenciaNumeroOcorrencias : undefined,
    }
    const payload: EventoRequest = { ...camposExistentes, recorrencia }
```

(`camposExistentes` representa o objeto que o hook já monta hoje — não reescrever o resto do
mapeamento, só acrescentar a chave `recorrencia`.)

- [ ] **Step 3: Adicionar a seção "Repetir" no formulário**

Em `EventoForm.tsx`, logo após o campo de data/hora de início, adicionar um toggle e, quando
ligado, os campos condicionais (frequência → dias da semana se semanal / tipo mensal se
mensal → fim). Reusar o componente de chips de dia da semana que `SeletorRedes`/célula já usa
como referência visual (`grep -rn "DiaSemana" frontend/src/components` pra achar o padrão
exato de chip a copiar) — não inventar um componente novo do zero pra isso.

- [ ] **Step 4: Testar manualmente no navegador**

Rodar o front (`npm run dev`), abrir "Cadastrar evento", ligar "Repetir", escolher semanal +
quinta-feira, salvar, conferir no banco que `evento_serie` e o primeiro `evento.serie_id`
foram criados corretamente.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/evento/useEventoForm.ts \
        frontend/src/components/module/eventos/EventoForm.tsx \
        frontend/src/lib/validators.ts
git commit -m "feat(evento): toggle Repetir no formulario de cadastro"
```

---

## Task 13: Frontend — modal de escopo ao editar/cancelar

**Files:**
- Create: `frontend/src/components/module/eventos/ModalEscopoEdicaoEvento.tsx`
- Modify: `frontend/src/app/(app)/eventos/[id]/page.tsx` (tela de edição)
- Modify: `frontend/src/app/(app)/eventos/(lista)/ModalArquivarEvento.tsx`

**Interfaces:**
- Consumes: `EscopoEdicaoEvento` (Task 11).
- Produces: `ModalEscopoEdicaoEvento({ aberto, aoEscolher: (escopo: EscopoEdicaoEvento) => void, aoFechar: () => void })`.

- [ ] **Step 1: Criar o modal de escolha de escopo**

```typescript
'use client'

import styles from './ModalEscopoEdicaoEvento.module.css'
import type { EscopoEdicaoEvento } from '@/types/evento.type'

interface Props {
  aberto: boolean
  aoEscolher: (escopo: EscopoEdicaoEvento) => void
  aoFechar: () => void
}

export function ModalEscopoEdicaoEvento({ aberto, aoEscolher, aoFechar }: Props) {
  if (!aberto) return null
  return (
    <div className={styles.overlay} onClick={aoFechar}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2>Este evento faz parte de uma série</h2>
        <p>O que você quer alterar?</p>
        <button onClick={() => aoEscolher('ESTA')}>Só este</button>
        <button onClick={() => aoEscolher('ESTA_E_SEGUINTES')}>Este e os seguintes</button>
        <button onClick={() => aoEscolher('SERIE')}>Toda a série</button>
        <button onClick={aoFechar}>Cancelar</button>
      </div>
    </div>
  )
}
```

Criar também `ModalEscopoEdicaoEvento.module.css` seguindo o mesmo padrão visual de outro
modal simples já existente no projeto (ex.: `ModalArquivarEvento.module.css` como referência
de espaçamento/cores).

- [ ] **Step 2: Ligar no fluxo de edição**

Na tela de edição de evento (`eventos/[id]/page.tsx`), ao salvar: se `eventoInicial?.serieId`
existir, abrir `ModalEscopoEdicaoEvento` em vez de chamar `eventosService.atualizar` direto;
o `aoEscolher` do modal chama `eventosService.atualizar(id, payload, escopo)`. Se não tem
`serieId`, comportamento continua idêntico a hoje (chama direto, sem modal).

- [ ] **Step 3: Ligar no fluxo de arquivar**

Mesmo tratamento em `ModalArquivarEvento.tsx` (ou onde o botão "Arquivar" da lista/detalhe
dispara `eventosService.arquivar`): se o evento tem `serieId`, mostrar o seletor de escopo
antes de confirmar o arquivamento.

- [ ] **Step 4: Testar manualmente no navegador**

Editar uma ocorrência de série existente, escolher cada um dos 3 escopos separadamente e
conferir no banco/lista que o efeito bate com o desenhado (só aquela, futuras, toda a série).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/module/eventos/ModalEscopoEdicaoEvento.tsx \
        frontend/src/components/module/eventos/ModalEscopoEdicaoEvento.module.css \
        "frontend/src/app/(app)/eventos/[id]/page.tsx" \
        "frontend/src/app/(app)/eventos/(lista)/ModalArquivarEvento.tsx"
git commit -m "feat(evento): modal de escopo ao editar/arquivar evento de serie"
```

---

## Task 14: Fechar o item no backlog

**Files:**
- Modify: `docs/BACKLOG-PRE-VENDA.md`

- [ ] **Step 1: Marcar o item 6 como resolvido**

Trocar o título `## 6. Recorrência de evento (Spec C)` por
`## 6. ~~Recorrência de evento (Spec C)~~ RESOLVIDO (<data real de quando terminar>)` e
adicionar um parágrafo curto resumindo o que foi entregue (série + materialização em janela
móvel + 3 escopos de edição), igual ao fechamento que os itens 1 e 4 já têm nesse arquivo.

- [ ] **Step 2: Commit**

```bash
git add docs/BACKLOG-PRE-VENDA.md
git commit -m "docs(backlog): fecha recorrencia de evento (item 6)"
```
