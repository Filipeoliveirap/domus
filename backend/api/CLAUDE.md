# Domus — Roadmap da Versão de Produção

> Planejamento para evoluir o Domus do escopo acadêmico (TCC) para uma versão de
> produção, começando pelo piloto na igreja do autor e preparando o terreno para o
> lançamento comercial. Escrito para servir de **contexto e guia ao trabalhar com o
> Claude Code**. 

## Modo de trabalho: mentoria (instrução para o Claude Code)

O autor está aprendendo engenharia de software e **não quer só ver código pronto** — quer
entender tudo. Em cada decisão e cada implementação, aja como um **mentor/professor**, não
apenas como executor:

- **Antes de escrever código,** explique o plano: o que vai ser feito, por quê, quais
  conceitos estão envolvidos, quais bibliotecas/APIs serão usadas e o **motivo** da escolha
  (e quais alternativas foram descartadas, e por quê).
- **Explique o fluxo de ponta a ponta:** como a requisição entra, passa pelas camadas
  (controller → service → repository) e volta; o que cada parte faz.
- **Explique a lógica e as libs, não só o resultado.** Quando surgir um conceito novo,
  ensine como um professor ensinaria a um aluno, usando **analogias** quando ajudar.
- **Vá um passo por vez** e confirme o entendimento antes de seguir. Prefira ensinar bem a
  entregar rápido.
- Objetivo final: o autor precisa **entender o suficiente para manter e evoluir sozinho**
  cada coisa construída. Trate como pareamento (pair programming), não como entrega.

---

## Como usar este documento

- Isto é um **roadmap**, não uma especificação técnica detalhada. O fluxo completo de
  cada feature será desenhado com o Claude Code na hora de implementar.
- Trabalhe **uma feature por vez**, na ordem das fases. Cada fase tem um objetivo e um
  critério de "pronto".
- Sugestão prática: mantenha um `CLAUDE.md` na raiz do repositório com o contexto fixo
  do projeto (stack, convenções, arquitetura) e aponte o Claude Code para **este**
  roadmap quando for planejar cada item. Assim ele não precisa reler tudo toda vez.
- A seção **Decisões já tomadas** (no final) são *guardrails*: já foram debatidas e não
  precisam ser rediscutidas a cada feature.

---

## Contexto do projeto

Domus é um SaaS **multi-inquilino (multi-tenant)** de gestão administrativa de igrejas
de pequeno e médio porte. Módulos atuais (herdados do TCC): autenticação + recuperação
de senha, usuários, pessoas, eventos, financeiro (com categorias e relatórios) e busca
global unificada.

**Stack — Back:** Java 21, Spring Boot, Spring Security, PostgreSQL (fonte da verdade),
Spring Data JPA, Flyway (migrations), Redis (cache), Elasticsearch (busca, sincronizada
via *transactional outbox*).

**Stack — Front:** Next.js, TypeScript, CSS Modules, TanStack Query, React Hook Form + Zod.

**Convenções e padrões vigentes:**
- **Não commitar antes de o autor testar.** Entregue a correção, avise, e **espere**. Só
  commitar depois do teste — e num commit só, coerente, em vez de vários commits parciais
  da mesma coisa. Exceção: algo que valha por si (ex.: uma correção isolada que não depende
  do resto). Isso vale para **toda a aplicação**, não só para a feature da vez.
- **Feature grande: desenvolver em pedaços testáveis, não tudo de uma vez.** Quando o
  trabalho for grande (várias tasks, back+front, múltiplos módulos), não construir a coisa
  inteira e só depois despejar tudo pro autor testar no final. Entregar um pedaço coerente
  (ex.: uma task do plano, ou um grupo pequeno de tasks relacionadas), avisar, e **esperar
  o autor testar aquele pedaço** antes de seguir pro próximo. Motivo: testar tudo de uma vez
  no final concentra o risco — se algo quebrar, fica difícil saber qual das N mudanças foi
  a causa, e o retrabalho é maior. Isso não substitui a regra acima (não commitar antes do
  teste) — as duas juntas: pedaço pequeno, teste, commit, próximo pedaço.
- **Nunca imprimir segredo.** Não despejar `.env`, chave, token ou senha na conversa — nem via
  `cat`, nem por script que gere `export` no stdout. Segredo impresso não se apaga: fica no
  histórico e só sai por rotação, que custa ao autor. Para carregar variáveis, redirecione para
  arquivo e faça `source`. Para conferir um valor, mostre só a forma, mascarada.
  *(Aconteceu em 2026-07-22: o `.env` inteiro foi impresso e as credenciais do R2 — que eram as
  mesmas do backup — tiveram de ser rotacionadas.)*
- **Esconder no front não é esconder.** Dado que um perfil não pode ver não pode sair da API:
  se o JSON traz o campo, basta abrir o DevTools. Restrição por perfil se faz no **backend**
  (DTO reduzido ou endpoint próprio); a tela só reflete o que já foi omitido.

**Design: programar para interface (SOLID na prática, não como ritual)**

O objetivo é um só: **mudança localizada**. Se alterar uma decisão exige editar N arquivos, o
desenho está errado — não porque violou uma sigla, mas porque N vai crescer.

- **Pergunte pela CAPACIDADE, não pela IDENTIDADE.** `podeGerenciarInscricoes(role)` em vez de
  `role === 'ADMIN_IGREJA' || role === 'LIDER'`. A segunda forma espalha a regra por toda parte
  e transforma renomear um perfil numa caçada. Toda checagem de permissão passa por uma função
  nomeada pela ação; o nome do perfil aparece **em um arquivo só**, de cada lado.
- **Nada de literal de domínio solto.** Perfis, status, vínculos e códigos de erro vivem em
  `enum` (back) e união de tipos (front). String crua no meio do código é erro de digitação
  esperando acontecer, e o compilador não ajuda.
- **Dependa de abstração onde há troca prevista.** `EmailService` com implementação `Log` e
  `Resend` é o exemplo bom que já existe: trocar de provedor não toca em quem envia e-mail.
  Onde a troca **não** é prevista, interface é cerimônia — não crie por reflexo.
- **Uma razão para mudar.** Quando um arquivo passa a mudar por motivos diferentes (regra de
  negócio *e* formatação *e* permissão), separe. Vale para service, componente e CSS.
- **Estenda sem editar.** Adicionar um perfil, um status ou um provedor não deveria exigir
  `if/else` novo em vários lugares — deveria ser mais uma entrada num mapa ou enum.

Regra prática antes de escrever: **"se isto mudar de nome ou de valor amanhã, quantos arquivos
eu abro?"** Se a resposta for mais que um ou dois, o desenho ainda não está pronto.
- Isolamento lógico por `igreja_id` em toda entidade do domínio, **sempre extraído do
  JWT, nunca do corpo da requisição** (defesa contra acesso cruzado entre igrejas).
- Camadas `controller → service → repository`; services retornam **DTOs**, nunca
  entidades de persistência.
- **Soft delete** (`deleted_at`) nas entidades.
- Perfis de acesso: `ADMIN_IGREJA`, `LIDER`, `ACESSO_COMUM`.
- Relação central: todo **usuário** (credencial de acesso) está vinculado a exatamente
  uma **pessoa**. Nem toda pessoa tem usuário. `pessoa.email` é **único**. `MEMBRO` é um
  **vínculo** (batizado), não o cadastro — o cadastro é `pessoa`.
- **Responsividade é obrigatória em toda feature de front.** Toda funcionalidade nova
  (tela, formulário, modal, drawer, tabela) tem que ser ajustada para **mobile** como
  parte da própria entrega — não é etapa separada nem opcional. Padrões já usados:
  tabelas viram **cards** no mobile; headers com título+botão **empilham**; grids de
  formulário **colapsam** para 1 coluna; modais/drawers reduzem padding; `min-width: 0`
  na cadeia flex/grid e larguras fixas (ex.: botão Google) revistas para evitar overflow
  horizontal. Validar no viewport de celular antes de considerar pronto.
- **UX é prioridade em toda feature/fluxo novo, não só a função funcionando.** Rótulo de
  campo sempre carrega um exemplo concreto (via `placeholder` do próprio campo — nunca só o
  nome técnico do dado, tipo "Rótulo" ou "Valor"). Quando o tipo/formato de algo muda como a
  pessoa interage (ex.: lista de escolher uma vs. marcar várias), a UI explica a diferença
  visivelmente, não só pelo nome da opção. Prévia de qualquer builder (formulário, campo,
  template) é **interativa de verdade** sempre que der (inputs reais com estado local, nunca
  `disabled`) — além de UX melhor, prévia estática esconde bug de estado que só aparece
  quando alguém realmente interage (ex.: textarea que filtrava linha vazia a cada tecla e
  "não deixava" digitar Enter — só apareceu quando a prévia virou interativa de verdade).
  Antes de dar uma tela como pronta, perguntar "uma pessoa leiga entenderia isso sem
  explicação?", não só "os testes passam?".

---

## Convenções de teste

> **Regra de ouro:** o teste reflete a feature. Se a feature é "só mulheres podem se
> inscrever", o teste prova exatamente isso — aprova mulher, recusa homem, recusa quando
> `sexo` é nulo. Se a feature é "admin pode cancelar inscrição de qualquer um", o teste
> cobre admin cancelando, líder cancelando, comum sendo recusado. Toda regra de negócio
> nova vem acompanhada do teste que a prova.

### Workflow de desenvolvimento de teste

1. **Criar o teste antes ou junto com o código** (TDD ou pareado)
2. **Rodar e ver passar** (`mvn -q test -Dtest=NomeDaClasse`)
3. **Revisar** — pode ser você mesmo relendo ou delegando a um agente para revisão cruzada
4. **Só commitar depois do teste passar** — commit único e coerente por feature/correção

### Qual tipo de teste usar (decisão por camada)

| Camada | Ferramenta | Quando usar |
|---|---|---|
| **Service (regra de negócio)** | Mockito puro, sem contexto Spring | **Regra padrão.** 90% dos testes do projeto. |
| **Repository (consulta JPA)** | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` | Quando a consulta tem JPQL/query method não trivial. Roda contra Postgres real via Testcontainers (ver nota abaixo). |
| **Integração JPA complexa** | `@SpringBootTest` + `@Transactional` | FK constraints, triggers, concorrência com lock, migration. **Exceção, não regra.** |
| **Controller (HTTP + Security)** | `@SpringBootTest` + `@AutoConfigureMockMvc` + `AutenticacaoTestSupport` | Harness introduzido em 2026-08-20 (piloto: `VisitanteControllerTest`). Ainda **não aplicado a todos os controllers** — expandir módulo a módulo conforme mexer neles. |

> **Por que Mockito puro é a regra?** Serviços com Mockito rodam em milissegundos, não
> precisam de banco, não precisam de `.env`, e testam a lógica de negócio isolada. Mas
> **não pegam** bugs de lazy loading, ordem de `requestMatchers` do Spring Security, ou FK
> constraints — esses precisam de `@SpringBootTest`.

> **Banco de testes via Testcontainers (2026-08-20):** toda classe `@DataJpaTest`/
> `@SpringBootTest` implementa `PostgresTestContainerSupport`
> (`src/test/java/.../shared/testcontainers/`) — uma interface com o container Postgres
> (`postgres:16-alpine`) como campo estático e um `@DynamicPropertySource` que sobrescreve
> `spring.datasource.*`. Por ser estático numa interface, o container sobe **uma vez só**
> por execução do `mvn test` (o Surefire deste projeto roda tudo numa JVM só), não uma vez
> por classe. Migrations do Flyway (inclusive `unaccent` e os triggers em plpgsql) rodam
> sozinhas contra o banco novo. **Não precisa mais de `.env`/Neon pra rodar a suíte** — só
> Docker instalado e rodando. Ao escrever um teste novo nessas camadas, lembre que o banco
> começa **vazio** (só schema, sem dado nenhum): não assuma que já existe uma `igreja` ou
> qualquer outra linha — crie o fixture que o teste precisa (foi isso que quebrou 3 testes
> do `MigracaoV3Test`, escritos assumindo o Neon compartilhado sempre ter dado de sobra).

> **Harness de teste de controller (`AutenticacaoTestSupport`):** o `SecurityFilter` do
> projeto lê o JWT direto de um cookie (`domus_access`), não usa o mecanismo padrão do
> Spring Security — então `@WithMockUser` não autentica nada aqui. `AutenticacaoTestSupport`
> (em `src/test/java/.../shared/security/`) gera um JWT real via `TokenService` e devolve um
> `Cookie` pronto pra `mockMvc.perform(...)`; o método `autenticado(builder, usuario)` já
> anexa esse cookie **e** um token CSRF válido (via `csrf()` do `spring-security-test`, que
> já estava no `pom.xml` mas não era usado). Uso: `@SpringBootTest @AutoConfigureMockMvc
> @Transactional`, instanciar `new AutenticacaoTestSupport(tokenService)` no `@BeforeEach`,
> fixtures de Igreja/Pessoa/Role/Usuario montadas inline por teste (sem fixture
> compartilhada de domínio — só a mecânica de autenticação é compartilhada). Cobre os dois
> ângulos ao mesmo tempo: validação de `@Valid` (pega bug de anotação ausente, tipo o de
> `MoverParaCelulaRequest`) **e** autorização por perfil (`requestMatchers` do
> `SecurityConfig` + checagens `Permissoes.*` dentro do controller).

### Padrão de mock

O projeto tem **dois estilos** que coexistem. Use o que o arquivo já usa:

**Estilo A — `mock()` manual no `@BeforeEach`** (dominante, ~15 arquivos):
```java
class MeuServiceTest {
    MeuRepository repo;
    OutroService outro;
    MeuService service;

    @BeforeEach
    void setup() {
        repo = mock(MeuRepository.class);
        outro = mock(OutroService.class);
        service = new MeuService(repo, outro);
    }
}
```

**Estilo B — `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`** (3 arquivos):
```java
@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {
    @Mock TokenService tokenService;
    @Mock UsuarioRepository usuarioRepository;
    @InjectMocks SecurityFilter filter;
}
```

### Nomenclatura

- **Classe:** `{ClasseAlvo}Test.java`
- **Método:** `snake_case` em português descrevendo o cenário esperado
  - `inscreveQuandoHaVaga()`
  - `eventoExclusivoDeMembrosRecusaCongregante()`
  - `adminPodeCancelarInscricaoDeQualquerUm()`
  - `recusaInscricaoDuplicada()`

### Assertions

- **AssertJ é primário** (usado em ~80% dos testes):
  ```java
  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  ```
- **JUnit Jupiter é aceito** (usado em ~20%, especialmente testes de cookie/segurança):
  ```java
  import static org.junit.jupiter.api.Assertions.*;
  ```
- Use `assertThatThrownBy(...).isInstanceOf(BusinessException.class).hasMessageContaining(...)` para exceções de negócio.
- Use `verify(repo, never()).save(any())` para provar que algo **não** aconteceu.

### Estrutura do arquivo de teste

- **Helpers privados** por classe (não existem base classes nem fixtures compartilhados):
  ```java
  private Igreja igreja() { ... }
  private Evento evento(Integer vagas) { ... }
  private Pessoa pessoa(Vinculo vinculo) { ... }
  private void dado(Evento e, Pessoa p, long ocupadas) { ... }  // setup de mocks
  ```
- Geralmente a classe define campos `UUID igrejaId`, `eventoId`, etc. com `UUID.randomUUID()` no topo.
- Testes de `@DataJpaTest` usam `entityManager.flush()` + `entityManager.clear()` para forçar reload do banco e evitar o cache de 1º nível do Hibernate.

### Rodando os testes

```bash
# Todos os testes (precisa de Docker rodando — o Postgres do @DataJpaTest/@SpringBootTest
# sobe via Testcontainers, não precisa mais de .env)
mvn -q test

# Um teste específico
mvn -q test -Dtest=NomeDaClasse

# Offline (dependências já cacheadas)
mvn -q -o test -Dtest=NomeDaClasse
```

### Testes de segurança

- **Filtros (SecurityFilter, RateLimitFilter):** Mockito puro com mocks de Servlet (`MockHttpServletRequest`/`Response`).
- **Permissões:** `PermissoesTest` cobre `podeGerenciarInscricoes(role)` etc. sem Spring.
- **Ordem de `requestMatchers` do Spring Security:** **não é coberta por teste unitário.** Se mexer no `SecurityConfig`, valide manualmente com curl nos endpoints protegidos.
- **Cookies de sessão:** `AuthCookieFactoryTest` testa atributos (`httpOnly`, `Secure`, `SameSite`, `Path`) sem contexto Spring.

### Dívidas técnicas de teste (conhecidas, não repetir o erro)

| Dívida | Impacto |
|---|---|
| **Sem harness de autorização por endpoint** — não existe `@WebMvcTest` com `SecurityConfig` real | Bugs de ordem de `requestMatchers` só são pegos manualmente |
| **Mockito self-attaching agent** — warning nos logs | Em JDKs futuros vai quebrar; precisa configurar byte-buddy como Java agent no surefire |
| **Sem testes de frontend** — não há Jest, Vitest, Cypress ou Playwright configurados | Validação de front é manual no navegador |

### Regras práticas

- **Teste comprova a feature, não o contrário.** Se o teste passa mas não prova a regra de negócio, ele não serve.
- **Mock só o que é externo ao SUT.** Dependências (repositories, outros services) são mockadas. Regras de negócio internas (`ElegibilidadeService` com `RegraFaixaEtaria`, etc.) são **instanciadas de verdade** para o teste fazer sentido.
- **Não mockar tipos de domínio** (`Igreja`, `Evento`, `Pessoa`). Use builders ou `new`.
- **Cada teste prova uma coisa só.** Um cenário de sucesso e um de falha por teste.
- **Arquivo de teste cresce com a classe.** `InscricaoServiceTest` tem 850+ linhas e 47 testes — é o esperado para um service central. Não quebre em arquivos menores artificialmente.
- **Nunca altere o teste só pra fazer passar — mude o código de produção, ou pare e explique por quê o teste não pode passar como está.** Enfraquecer uma asserção, trocar `equals` por `contains`, remover um `verify`, apagar um cenário difícil ou marcar como `@Disabled` sem justificativa real são formas de mentir que a feature funciona. Se um teste está genuinamente errado (prova a coisa errada, tem um typo), diga isso explicitamente antes de tocar nele — nunca silenciosamente. E nunca reporte "teste passou" sem ter rodado o comando de verdade e visto o resultado — sem chutar, sem assumir.

---

## Modelo de dados (diagrama ER)

> **Fonte da verdade são as migrations** (`src/main/resources/db/migration`), não este
> diagrama. Ao mexer no schema, atualize aqui também. Estado atual: **V32**.
> `V1__schema_inicial.sql` consolida as antigas V1–V16 em 2026-07-21 (ver nota logo
> abaixo do diagrama). Campos de rotina (`created_at`, `updated_at`, `deleted_at`) foram
> omitidos por ruído, exceto quando têm significado (soft delete).

```mermaid
erDiagram
    IGREJA ||--o{ IGREJA : "é sede de (igreja_mae_id)"
    IGREJA ||--o{ PESSOA : tem
    IGREJA ||--o{ USUARIO : tem
    IGREJA ||--o{ EVENTO : tem
    IGREJA ||--o{ CATEGORIA_FINANCEIRA : tem
    IGREJA ||--o{ MOVIMENTACAO_FINANCEIRA : tem
    IGREJA ||--o{ INSCRICAO_EVENTO : tem
    IGREJA ||--o{ LOCAL_EVENTO : tem
    PESSOA ||--o| USUARIO : "pode ter (1-1)"
    PESSOA ||--o{ INSCRICAO_EVENTO : "se inscreve em"
    ROLE   ||--o{ USUARIO : define
    CATEGORIA_FINANCEIRA ||--o{ MOVIMENTACAO_FINANCEIRA : classifica
    MOVIMENTACAO_FINANCEIRA ||--o{ MOVIMENTACAO_CONTRIBUINTE : "V15 - um ou mais contribuintes"
    PESSOA ||--o{ MOVIMENTACAO_CONTRIBUINTE : "atribuída a (ou nome_externo sem cadastro)"
    USUARIO ||--o{ MOVIMENTACAO_FINANCEIRA : "criou/atualizou"
    USUARIO ||--o{ INSCRICAO_EVENTO : "inscreveu"
    USUARIO ||--o{ IGREJA : "atualizou/vinculou"
    EVENTO ||--o{ INSCRICAO_EVENTO : "tem"
    INSCRICAO_EVENTO ||--o{ ACOMPANHANTE_INSCRICAO : "pode ter (modelo antigo, sem e-mail)"
    VISITANTE ||--o| INSCRICAO_EVENTO : "V28 - convidado vinculado de volta"
    PESSOA }o--o| FOTO : tem
    EVENTO }o--o| FOTO : tem
    IGREJA }o--o| FOTO : "tem (logo)"
    LOCAL_EVENTO ||--o{ EVENTO : "V3 - local cadastrado (ou local_texto ad-hoc)"
    PESSOA ||--o{ EVENTO : "V3 - responsável"
    USUARIO ||--o{ EVENTO : "V3 - criou/atualizou"
    IGREJA ||--o| CONTA_PAGAMENTO_IGREJA : "V29 - conta Mercado Pago conectada"
    INSCRICAO_EVENTO ||--o{ COBRANCA_EVENTO : "V29 - evento pago gera cobrança"
    PESSOA ||--o{ COBRANCA_EVENTO : "paga (ou acompanhante/convidado, V29-V30)"
    ACOMPANHANTE_INSCRICAO ||--o| COBRANCA_EVENTO : "V29 - paga por acompanhante"

    IGREJA {
        uuid      id PK
        uuid      igreja_mae_id FK "V12 - NULL=independente; 2 níveis"
        varchar   codigo_vinculo UK "V12 - 8 chars XK4P-2M7Q"
        timestamp codigo_gerado_em "V13 - não expira; permite sugerir rotação"
        timestamp vinculado_em "V13"
        uuid      vinculado_por_usuario_id FK "V13 - quem digitou o código"
        varchar   nome
        varchar   razao_social "V13 - par do CNPJ p/ nota fiscal"
        varchar   cnpj
        varchar   denominacao "V13"
        varchar   email "contato"
        varchar   telefone
        uuid      logo_foto_id FK "V2 - era logo_url; agora aponta pra FOTO"
        varchar   plano
        varchar   cep_logradouro_numero "V13 - endereço estruturado"
        varchar   complemento_bairro_cidade_uf "V13"
        uuid      atualizado_por_usuario_id FK "V13 - logs de atividade"
    }

    PESSOA {
        uuid      id PK
        uuid      igreja_id FK "isolamento multi-tenant"
        varchar   nome
        varchar   email UK "único - vira a chave de login"
        varchar   telefone
        date      data_nascimento
        varchar   vinculo "MEMBRO|CONGREGANTE - substitui status+batizado"
        varchar   estado_civil
        varchar   sexo "V3 - HOMEM|MULHER, nulável (habilita restricao_sexo do evento)"
        varchar   ministerio
        uuid      foto_id FK "V2 - era varchar; agora aponta pra FOTO"
        varchar   cep_logradouro_numero "V11 - endereço estruturado"
        varchar   complemento_bairro_cidade_uf "V11"
        date      data_batismo "só tem sentido se vinculo=MEMBRO"
        timestamp deleted_at "soft delete"
    }

    USUARIO {
        uuid      id PK
        uuid      igreja_id FK
        uuid      pessoa_id FK,UK "1-1: todo usuário é uma pessoa"
        uuid      role_id FK
        varchar   senha_hash "nullable desde V10 (conta só-Google)"
        varchar   google_sub UK "V10"
        boolean   ativo
        timestamp ultimo_login_em
        timestamp delete_at "soft delete"
    }

    ROLE {
        uuid    id PK
        varchar nome UK "ADMIN_IGREJA|LIDER|ACESSO_COMUM"
        varchar descricao
    }

    EVENTO {
        uuid      id PK
        uuid      igreja_id FK
        varchar   titulo
        text      descricao
        timestamp inicio_em
        timestamp fim_em "NULL = sem fim declarado"
        uuid      local_id FK "V3 - local cadastrado; XOR com local_texto"
        varchar   local_texto "V3 - era 'local' (RENAME); texto livre ad-hoc; XOR com local_id"
        uuid      foto_id FK "V2 - era varchar; agora aponta pra FOTO"
        integer   vagas "V15 - NULL = sem limite"
        numeric   preco "V15 - NULL = gratuito"
        boolean   exclusivo_membros "cobre batizados - vinculo=MEMBRO é quem é batizado"
        boolean   requer_inscricao "V16"
        varchar   tipo "V3 - texto livre que aprende (autocomplete); não é 'categoria'"
        uuid      responsavel_pessoa_id FK "V3 - organizador, ON DELETE SET NULL"
        uuid      criado_por_usuario_id FK "V3 - auditoria, padrão de movimentacao_financeira"
        uuid      atualizado_por_usuario_id FK "V3"
        varchar   recorte_etario "V3 - rótulo do recorte (ex.: KIDS, JOVENS, 3A_IDADE), informativo"
        integer   idade_min "V3 - CHECK >= 0 e <= idade_max"
        integer   idade_max "V3 - CHECK >= 0"
        varchar   restricao_estado_civil "V3 - SOLTEIRO|CASADO|DIVORCIADO|VIUVO, nulável"
        varchar   restricao_sexo "V3 - HOMEM|MULHER, nulável"
        timestamp deleted_at "soft delete"
    }

    LOCAL_EVENTO {
        uuid      id PK
        uuid      igreja_id FK "V3 - isolamento multi-tenant"
        varchar   nome "V3 - único por igreja, ignorando acento/caixa (unaccent)"
        integer   capacidade "V3 - CHECK > 0; SUGERE vagas, não impõe limite"
        varchar   cep_logradouro_numero "V3 - endereço próprio; se NULL, herda o da igreja"
        varchar   complemento_bairro_cidade_uf "V3"
        timestamp deleted_at "soft delete"
    }

    INSCRICAO_EVENTO {
        uuid      id PK
        uuid      igreja_id FK "isolamento multi-tenant"
        uuid      evento_id FK
        uuid      pessoa_id FK "nulável - convidado sem cadastro não tem, ou pessoa já excluída de vez"
        uuid      inscrito_por_usuario_id FK "V15 - NULL = auto-inscrição"
        varchar   status "AGUARDANDO_PAGAMENTO|CONFIRMADA|CANCELADA (V29 - 1º valor novo p/ evento pago)"
        varchar   nome_convidado "V26 - convidado sem cadastro (convite público)"
        varchar   telefone_convidado "V26"
        varchar   email_convidado "V31 - obrigatório quando o evento é pago"
        uuid      convidado_por_pessoa_id FK "V26 - quem gerou o convite"
        uuid      visitante_id FK "V28 - liga de volta ao registro de Visitante, ON DELETE SET NULL"
    }

    ACOMPANHANTE_INSCRICAO {
        uuid      id PK
        uuid      inscricao_id FK "ON DELETE CASCADE"
        varchar   nome
        varchar   telefone "V15 - opcional"
    }

    CATEGORIA_FINANCEIRA {
        uuid      id PK
        uuid      igreja_id FK
        varchar   nome "único por igreja (case-insensitive, V7)"
        varchar   tipo "ENTRADA|SAIDA|AMBOS"
        timestamp deleted_at "soft delete"
    }

    MOVIMENTACAO_FINANCEIRA {
        uuid      id PK
        uuid      igreja_id FK
        uuid      categoria_id FK
        uuid      criado_por_usuario_id FK "nulável - ver criado_por_texto"
        varchar   criado_por_texto "usuário excluído (LGPD) ou lançamento automático do sistema"
        uuid      atualizado_por_usuario_id FK "nulável"
        varchar   atualizado_por_texto
        varchar   tipo "ENTRADA|SAIDA"
        numeric   valor "CHECK > 0"
        date      data_movimentacao
        text      descricao
        timestamp deleted_at "soft delete"
    }

    MOVIMENTACAO_CONTRIBUINTE {
        uuid      id PK
        uuid      movimentacao_id FK "ON DELETE CASCADE"
        uuid      pessoa_id FK "nulável - XOR com nome_externo"
        varchar   nome_externo "V32 - contribuinte/beneficiário sem cadastro (ex.: doação avulsa)"
        numeric   valor "CHECK > 0; soma dos contribuintes = valor da movimentação"
    }

    CONTA_PAGAMENTO_IGREJA {
        uuid      id PK
        uuid      igreja_id FK,UK "1-1 - uma conta MP conectada por igreja"
        varchar   mp_user_id "id da conta no Mercado Pago"
        text      access_token "V29 - criptografado em repouso (AES-GCM)"
        text      refresh_token "V29 - idem; renovado sozinho antes de vencer"
        timestamp expira_em
        timestamp conectado_em
        uuid      conectado_por_usuario_id FK
    }

    COBRANCA_EVENTO {
        uuid      id PK
        uuid      igreja_id FK "isolamento multi-tenant"
        uuid      evento_id FK
        uuid      inscricao_id FK "ON DELETE CASCADE"
        uuid      pessoa_id FK "nulável - XOR com acompanhante_id; os dois nulos = convidado sem cadastro (V30)"
        uuid      acompanhante_id FK "ON DELETE CASCADE"
        numeric   valor "CHECK > 0"
        varchar   status "PENDENTE|PAGO|EXPIRADO|CANCELADO|REEMBOLSADO"
        varchar   mp_payment_id "nulável até a 1ª tentativa de pagamento"
        varchar   token_link_publico UK "V29 - link 'enviar pra pagar' compartilhável"
        timestamp expira_em
        timestamp pago_em
        uuid      criado_por_usuario_id FK "nulável (V30) - NULL = auto-registro anônimo via convite"
    }

    OUTBOX {
        uuid    id PK
        varchar tipo_entidade
        uuid    entidade_id
        varchar operacao
        boolean processado "transactional outbox p/ Elasticsearch"
    }

    FOTO {
        uuid      id PK
        uuid      igreja_id FK "isolamento multi-tenant"
        varchar   chave UK "V2 - prefixo aleatório no bucket R2"
        varchar   tipo "V2 - image/jpeg|image/png|image/webp, do original"
        bigint    bytes "V2 - do original, pra acompanhar consumo"
    }
```

### O que ler neste diagrama

- **`igreja_id` em toda entidade de domínio** é o isolamento multi-tenant. Vem sempre do
  JWT, nunca do corpo da requisição.
- **A auto-relação de `IGREJA`** (`igreja_mae_id`) é a hierarquia sede↔congregações. Uma
  congregação **é** uma igreja que tem mãe — por isso as checagens de isolamento
  continuam valendo sem alteração. **Regra dos 2 níveis:** quem tem mãe não pode ser mãe.
- **`PESSOA ||--o| USUARIO`** é 1-para-1 opcional: todo usuário está vinculado a uma
  pessoa; nem toda pessoa tem usuário (só quem recebeu acesso). O cadastro é `pessoa` —
  `MEMBRO` é um **vínculo** dela, não o registro em si (dá pra ter login sem ser batizado).
- **`pessoa.vinculo`** (`MEMBRO`|`CONGREGANTE`) substitui o antigo `status` +
  `batizado`: `MEMBRO` é quem foi batizado e formalmente membro; `CONGREGANTE` é quem
  frequenta sem ser batizado (absorve o antigo `VISITANTE`). Não existe "inativo" — quem
  parou de frequentar é **arquivado** (`deleted_at`), o mecanismo que já existia.
  `data_batismo` só faz sentido quando `vinculo = MEMBRO`.
- **`igreja` referencia `usuario` e vice-versa.** As FKs circulares são intencionais e
  seguras porque as de `igreja → usuario` são todas nuláveis (auditoria).
- **Inscrição em evento:** uma inscrição pertence a uma pessoa e a um evento.
  Vagas contam **pessoas** (inscritos confirmados + seus acompanhantes), não inscrições.
  Acompanhantes (`acompanhante_inscricao`) existem apenas para quem NÃO tem vínculo com a
  igreja e servem para saber "de onde essa pessoa veio". Cancelamento é mudança de status
  (preserva histórico de quem inscreveu quem); reinscricão reaproveita a mesma linha
  graças ao `UNIQUE (evento_id, pessoa_id)`. O `requer_inscricao` é o master toggle
  que separa evento que se organiza de evento que só acontece. Convidado sem cadastro
  (V26, via convite público) não tem `pessoa_id` — usa `nome_convidado`/
  `telefone_convidado`/`email_convidado` direto na própria linha; `email_convidado` é
  obrigatório quando o evento é pago (comprovante). Em evento pago, a inscrição nasce
  `AGUARDANDO_PAGAMENTO` e só vira `CONFIRMADA` quando o pagamento é aprovado de verdade
  (ver `COBRANCA_EVENTO` abaixo).

- **Cadastro de evento enriquecido (V3):** `local_texto` é o antigo `local` (RENAME, não
  ADD, para preservar dado); `local_id` aponta para `LOCAL_EVENTO`, um local cadastrado
  com endereço próprio ou, se `NULL`, o endereço é herdado do da própria igreja. O CHECK
  `local_id IS NULL OR local_texto IS NULL` impede os dois ao mesmo tempo — um evento é ou
  num local cadastrado, ou num texto livre ad-hoc, nunca ambos. `LOCAL_EVENTO.capacidade`
  **sugere** o número de vagas do evento; não é limite imposto pelo banco nem pela regra de
  negócio (fica pro backlog). `tipo` é texto livre com autocomplete que aprende com o uso —
  deliberadamente não chamado de "categoria" (nome já ocupado por `categoria_financeira`).
  `responsavel_pessoa_id`, `criado_por_usuario_id` e `atualizado_por_usuario_id` reusam o
  padrão de auditoria de `movimentacao_financeira`. `recorte_etario` + `idade_min`/
  `idade_max` + `restricao_estado_civil` + `restricao_sexo` são a elegibilidade de
  inscrição: quatro regras independentes, avaliadas no momento de inscrever — não somam
  automaticamente, cada uma bloqueia por conta própria quando o dado da pessoa falta
  (idade sem `data_nascimento`, sexo sem `pessoa.sexo`).
- **Pagamento de evento (V29-V32):** `CONTA_PAGAMENTO_IGREJA` é a conta Mercado Pago
  conectada (1-por-igreja); token renovado sozinho antes de vencer, sem intervenção
  manual (job diário). `COBRANCA_EVENTO` nasce quando alguém escolhe pagar (ou "enviar
  link pra pagar") uma inscrição em evento pago — o pagador é **um destes três**, nunca
  mais de um: `pessoa_id` (tem cadastro), `acompanhante_id` (modelo antigo, sem e-mail),
  ou os dois nulos (convidado sem cadastro via convite público, resolvido só por
  `inscricao_id`). Confirmação de pagamento é sempre assíncrona (webhook do Mercado Pago
  **e** um poll ativo correndo em paralelo, idempotentes entre si) — nunca na resposta do
  `POST .../pagar`. Pagamento aprovado e estorno em cancelamento entram automaticamente
  no financeiro da igreja (`MOVIMENTACAO_FINANCEIRA`, categoria "Eventos" auto-criada na
  1ª vez).
- **`MOVIMENTACAO_CONTRIBUINTE` (V15, ganhou `nome_externo` em V32):** uma movimentação
  pode ter **zero, um ou vários** contribuintes/beneficiários — cada linha soma pro valor
  total da movimentação (`CHECK` de que a soma bate, aplicado em código, não em SQL).
  Contribuinte é uma pessoa cadastrada (`pessoa_id`) **ou** um nome livre sem cadastro
  (`nome_externo`, ex.: doação de visitante avulso) — nunca os dois ao mesmo tempo; os
  dois nulos é o estado legítimo de "pessoa foi excluída definitivamente" (LGPD), exibido
  como "Pessoa removida do sistema".
- **`FOTO`** (V2) é metadado apenas — os bytes vivem num bucket **privado** do Cloudflare
  R2, servido pela própria API (`GET /fotos/{id}`), nunca por URL pública. `pessoa.foto`,
  `evento.foto` e `igreja.logo_url` deixaram de ser `varchar` de URL e viraram
  `foto_id`/`logo_foto_id` (`uuid`, `ON DELETE RESTRICT`): o job de limpeza decide o que
  apagar por **ausência** de referência, e a FK faz o banco recusar apagar uma foto ainda
  referenciada — a proteção não depende de a consulta da limpeza estar certa.

> **Consolidação das migrations (2026-07-21):** `V1__schema_inicial.sql` substitui as
> antigas V1–V16 num único arquivo — não havia dado real em produção, então as duas
> bases (dev e produção) foram recriadas do zero. **Backups tirados antes dessa data não
> restauram contra o código atual** (o schema não bate mais: `membro` virou `pessoa`,
> `status`/`batizado` viraram `vinculo`, etc.).

---

## Princípios norteadores

1. **Igreja = design partner, não primeiro cliente comercial.** É um *soft opening*:
   onboarding na mão, sem self-service nem cobrança. O objetivo é observar uso real e
   aprender antes de escalar.
2. **MVP é mínimo *viável*.** Entregar a menor coisa que gera valor real e destrava
   aprendizado. Toda feature construída antes do uso real é construída no escuro.
3. **Construir o mínimo, depois observar.** Adicionar campos e filtros com base em uso
   real, não em suposição (YAGNI).
4. **Build vs. buy.** Não reinventar o que provedores maduros já fazem (pagamento,
   e-mail, SMS). Integrar, não construir do zero.
5. **Fundações e segurança antes de dado real.** Backup, e-mail, rastreamento de erro,
   modelo de autenticação e correções de segurança precisam existir **antes** de a igreja
   entrar de verdade.

---

## Fases

### Fase 1 — Fundações, autenticação e endurecimento de produção

> **Objetivo:** deixar o ambiente seguro, observável e com o **modelo de autenticação
> definido**, antes de qualquer dado real de igreja entrar. Quase nada aqui é "feature
> visível", mas tudo é pré-requisito — inclusive a auth, que é a única porta de entrada
> do sistema.

> **Progresso (atualizado em 2026-07-15):** ver detalhes na memória do projeto
> (`refresh-token-familia-auth`, `email-transacional-reset-senha`). Tudo em branch `producao`.

- [x] **Modelo de autenticação: híbrido (Google OAuth + e-mail/senha nativo)** — **FEITO**:
  nativo + reset de senha ✅ e Google OAuth (login + cadastro) ✅.
- Decisão: duas formas de entrar, que convivem — "Entrar com Google" (OAuth) e
  e-mail/senha nativo (que JÁ existe e funciona, com proteções tipo bcrypt).
- [x] Falta no nativo: função "esqueci minha senha" (reset via link por e-mail) — **FEITO**
  (endpoints `/auth/forgot-password` e `/auth/reset-password` + telas no front).
- [x] Login E cadastro com Google (OAuth) — **FEITO** (endpoints `/auth/google/login` e
  `/auth/google/registrar`; ID token validado com `GoogleIdTokenVerifier`; `senha_hash`
  nullable + `google_sub` único; login nativo barra conta só-Google com `CONTA_SEM_SENHA`;
  botões no front de login e cadastro). Ver spec/plano em `docs/superpowers/`.
- Entra de novo: login E cadastro com Google. No cadastro, o Google cria igreja +
  primeiro membro + primeiro usuário (ADMIN_IGREJA) já com e-mail e nome verificados.
  *(Texto histórico da Fase 1: à época a tabela chamava-se `membro`; hoje é `pessoa`.)*
- Provisionamento (admin dá acesso) ≠ login. Depois de provisionado, o membro entra por:
  (a) Google — vínculo pelo e-mail (membro.email é único), primeiro login verifica posse;
  (b) Nativo — precisa definir uma senha antes, reusando o MESMO fluxo do reset.
- Sessão: nos dois caminhos, após identificar a pessoa (token Google ou bcrypt), o
  backend emite os próprios JWT + refresh. Logo, refresh/revogação e rate limiting valem
  para ambos.

- [x] **E-mail transacional** (Resend) — **FEITO e validado** (envio real confirmado).
    - *Back:* `EmailService` (interface) + `LogEmailService`/`ResendEmailService` chaveados
      por `email.provider`. Falta: verificar domínio no Resend (hoje só envia p/ o dono da
      conta no sandbox).
    - *Front:* estado "e-mail enviado" na tela `/forgot-password` ✅.

- [x] **Backup automático do Postgres** — **FEITO e validado ao vivo** (2026-07-17):
    - *Motivação real:* o **Neon Free dá só 6h de PITR**. O dump externo não é a segunda
      rede de segurança — é a **única**. E backup que mora no mesmo provedor que o dado é
      redundância, não backup.
    - *Como:* workflow diário (`0 6 * * *` UTC = 03:00 BRT) → `pg_dump -Fc` → **teste de
      restauração** num Postgres 16 descartável comparando a contagem de **cada tabela**
      contra a origem → criptografia **`age` assimétrica** (só a chave pública no CI: ele
      escreve e não lê) → **Cloudflare R2** (`domus-backups`, retenção de 90 dias via
      lifecycle rule). Lógica em `scripts/backup-postgres.sh` — roda local, porque workflow
      só se testa empurrando commit.
    - *Alerta:* **Sentry Crons** como dead man's switch + issue alert `Backup do Postgres
      falhou`. **A regra padrão do projeto NÃO servia** (filtra "high priority" e o issue de
      cron não entra) — descoberto quebrando de propósito. O alerta precisa dos **dois**
      gatilhos: `new issue` **e** `resolved becomes unresolved` — o issue auto-resolve
      quando o backup volta, então as falhas seguintes são **reaberturas**, e só o primeiro
      gatilho avisaria uma única vez na vida.
    - *Validado provocando as falhas:* dump truncado → o teste reprova; secret quebrado →
      job falha, check-in `error`, issue, **e-mail confirmado na caixa**. E o ensaio manual
      completo: baixar do R2, descriptografar com a chave privada, restaurar, conferir.
    - ⚠️ **A chave privada `age` é ponto único de falha** (Bitwarden + cópia offline).
    - ⚠️ **Ensaio manual trimestral** (`scripts/ensaio-restauracao.sh`) no calendário — a
      automação **não** prova que o arquivo abre com a sua chave; o CI não tem a privada.
    - Ver spec/plano em `docs/superpowers/`.

- [x] **Rastreamento de erro (Sentry) + logs estruturados** — **FEITO** (2026-07-16):
    - *Back:* `sentry-spring-boot-starter-jakarta` (DSN por env, só captura 500, scrub de PII);
      logs estruturados (`logback-spring.xml`: JSON em prod / console+MDC em dev) com
      `RequestIdFilter` (`request_id` + header `X-Request-Id`) e `usuario_id`/`igreja_id` no MDC.
    - *Front:* `@sentry/nextjs` (instrumentation client+server, DSN por env, scrub); CSP libera `*.sentry.io`.
    - *Falta ligar:* criar conta no sentry.io e plugar `SENTRY_DSN` (back) e `NEXT_PUBLIC_SENTRY_DSN` (front).

- **Correções de segurança (as "nuances")**
    - [x] **Refresh token + revogação:** **FEITO** — refresh opaco no Redis, rotação,
      revogação por logout e **detecção de reuso** (famílias de token). Access token 10 min.
      Bônus: corrigido o bug do contador de tentativas de login (nunca bloqueava) e o
      backend agora devolve **401** (não 403) p/ token ausente/expirado (destrava o refresh no front).
    - [x] **Rate limiting em todos os endpoints** (não só no login) — **FEITO** (2026-07-16):
      `RateLimitFilter` (janela fixa no Redis, global 100/min + auth 10/min por IP, 429 +
      `Retry-After`, `X-Forwarded-For` sob flag) e `LoginAttemptService` migrado p/ Redis.
      Limites por env (`app.ratelimit.*`). Validado ao vivo (curl → 429).
    - [x] **Segredos em variáveis de ambiente** — já em uso (`.env`, gitignored).
    - [x] **CORS restrito + security headers** — **FEITO** (commit `f607b0f`): CORS por env
      (`app.cors.allowed-origins`) e security headers no back (HSTS, X-Frame-Options, nosniff,
      Referrer-Policy, CSP) e no front (`next.config.ts`).
    - [x] **Token fora do `localStorage` (XSS) + CSRF reativado** — **FEITO e validado ao vivo**
      (2026-07-16): a sessão vive em cookies `httpOnly`+`Secure`+`SameSite=Lax` emitidos pelo
      backend (`domus_access` 10 min `Path=/api`; `domus_refresh` 7 dias `Path=/api/auth`).
      Saíram o `persist` do zustand, o `localStorage.setItem` e o `document.cookie` por JS —
      **o localStorage não participa mais da autenticação** (nem o `id`). **CSRF double-submit
      reativado** junto, como o modelo de cookie exige. Entrou `GET /auth/me` (o servidor virou
      dono da verdade da sessão; de quebra mata a role velha em cache) e o front passou a falar
      com a API por um **proxy same-origin** (`/api/*` no Next), o que desacopla o cookie da
      decisão de hospedagem. Validado no navegador: `document.cookie` mostra só `XSRF-TOKEN`
      (legível por design) e `g_state` (do Google), sem os cookies de sessão.
      **⚠️ Requisito de deploy que isso criou:** precisa de um proxy reverso real na frente do
      Next (`X-Forwarded-For`/`Proto`) + `RATELIMIT_TRUST_FORWARDED_FOR=true` +
      `FORWARD_HEADERS_STRATEGY=framework`, senão o rate limiting por IP vira um balde único.
      Ver spec/plano em `docs/superpowers/` e os resíduos no BACKLOG.
    - [x] **Vulnerabilidades de dependência (front)** — **FEITO** (2026-07-16, commit `ae95bb8`):
      `npm audit` saiu de 7 (1 baixa, 3 médias, 3 altas) para **0**. Como: `npm audit fix` (sem
      `--force`, que rebaixaria o Next p/ 9.3.3 e quebraria o build), `next@16.2.10` explícito e
      `overrides.postcss ^8.5.15`. **Falta:** avaliar o back (ex.: OWASP dependency-check).
    - [x] Revisão de **validação de input** em toda entrada — **FEITO** (2026-08-20, detalhe
      no BACKLOG): harness de teste de controller, `@Valid`/`@NotNull` faltando em 2 lugares
      (1 causava 500 real), `@Size` em texto livre, teto de paginação, `@Size` em parâmetros
      de busca livre. Segunda passada cobriu célula/ministério/local-evento/financeiro —
      nenhum bug real, só pontos descartados por julgamento. Todos os módulos auditados.
    - [x] **Verificar domínio no Resend** — **FEITO** (2026-07-18): domínio `domusigreja.com.br`
      verificado (DKIM + SPF/MX no subdomínio `send`, via DNS na Cloudflare). Remetente de
      produção `Domus <nao-responda@domusigreja.com.br>` (env `EMAIL_FROM`). Testado ao vivo:
      "esqueci minha senha" chegou na caixa, sem cair no spam.

- **Critério de pronto:** dá pra colocar dado real sem medo de perder, sem ficar cego a
  erros, com auth definida e sem os gaps de segurança conhecidos.

- ✅ **FASE 1 CONCLUÍDA (2026-07-18).** Produção no ar em `https://domusigreja.com.br`
  (Hetzner VPS + Cloudflare Tunnel + Neon Frankfurt). Ver detalhes de deploy e os
  "gotchas" na memória do projeto (`producao-no-ar-deploy`).

---

### Fase 2 — Funcionalidades de valor pra igreja

> **Objetivo:** o que faz a igreja realmente querer usar.

- [x] **Upload de foto** (pessoa, evento e logo da igreja) — **FEITO** (2026-07-22):
  tabela `foto` (V2) + bucket **privado** no Cloudflare R2, servido pela própria API
  (`GET /fotos/{id}?tamanho=thumb|display`, sessão e igreja validadas — nunca URL pública,
  porque são rostos de membros, inclusive crianças). Três versões (`original` guardado,
  nunca servido; `display` 1200px; `thumb` 200px). Validação por **conteúdo** (não
  extensão), limite de 50 megapixels checado no header antes de decodificar, e
  redecodificação que descarta EXIF (inclusive a coordenada de GPS do celular). Limpeza
  automática: órfã após 24h, foto de pessoa arquivada após 6 meses, troca remove a
  anterior na hora — ambas as janelas configuráveis (`app.fotos.orfa-horas`,
  `app.fotos.arquivada-meses`). `pessoa.foto`/`evento.foto`/`igreja.logo_url` viraram FK
  (`ON DELETE RESTRICT`) pra `foto.id`. Componente único `<UploadFoto>` no front, com
  recorte obrigatório em pessoa/logo (formato fixo) e opcional no banner de evento. Ver
  spec em `docs/superpowers/specs/2026-07-22-upload-foto-design.md`.
    - *Ficou de fora* (fora de escopo desta entrega): galeria (múltiplas fotos por
      entidade), vídeo, CDN de borda, e WebP como formato de **entrada** (ver BACKLOG).

- [x] **Endereço estruturado** *(colunas, não tabela nova)* — **FEITO**: `pessoa` tem
  `@Embedded Endereco` (mesmo embeddable de `Igreja`/`LocalEvento`), com auto-preenchimento
  via ViaCEP no formulário (`useBuscaCep`, `PessoaForm.tsx`).

- [x] **Inscrição de pessoa em evento + preço e vagas** — **FEITO (2026-07-21):**
  inscrição dois níveis (self + inscrever outros), acompanhantes para quem não tem
  vínculo com a igreja, contagem de vagas com lock pessimista, `requer_inscricao` como
  toggle master, campo `vinculo` (`MEMBRO`|`CONGREGANTE`), lista reduzida para pessoas
  comuns (sem telefone de convidado) e completa para admins/líderes, preço apenas
  informativo (cobrança decidida na Fase 6).

- [x] **Auditoria de evento (criado_por / atualizado_por)** — **FEITO**: colunas
  `criado_por_usuario_id`/`atualizado_por_usuario_id` (padrão de `movimentacao_financeira`,
  entregue na Spec B de eventos, V3), exibidas no drawer de detalhe do evento
  (`DrawerDetalheEvento.tsx`).

- [x] **Convite de acesso por e-mail (novo fluxo de provisionamento)** — **FEITO**: admin
  concede acesso escolhendo role, sem definir senha (`senha_hash = null`); o sistema envia
  e-mail de convite (`UsuarioService.concederAcesso`/`enviarConvite`) com link de definição
  de senha (reusa o fluxo de reset). Existe também reenvio de convite
  (`POST /usuarios/{id}/reenviar-convite`) para quem ainda não aceitou.

- [x] **Validação de formato de e-mail e telefone (BR)** — **FEITO**: e-mail com `@Email`
  (back, `PessoaRequestDTO`) + `z.email()` (front); telefone com `@Pattern` (back) +
  máscara BR (front), sem SMS.

---

### Fase 3 — Gestão de conta e configurações

- **Aba de Configuração:** perfil do usuário + dados da igreja (visualizar e editar) —
  **DECIDIDO FICAR ASSIM** (2026-08-19): existe `/configuracoes/igreja` (dados da igreja,
  incluindo upload de logo — ver Fase 2) e `/perfil` (perfil do usuário). Continuam como
  rotas separadas por decisão — **não vamos unificar em abas por ora**.
- [x] **Excluir conta.** — **FEITO**: exclusão de igreja com carência de 10 dias
  cancelável (`ExclusaoIgrejaController`/`ExclusaoIgrejaService`/`PurgaIgrejaService`),
  reautenticação por senha nativa OU Google, aviso e job diário de purga definitiva.
- [x] **Lista de arquivados por módulo + exclusão definitiva** (usuários, pessoas,
  eventos, locais de evento, células, ministérios) — **FEITO**. Complementa o soft delete
  já existente; a exclusão definitiva atende ao **direito de eliminação da LGPD**. Pessoa
  em especial: `excluirDefinitivo` apaga o cadastro, mas movimentação/inscrição já
  existentes mantêm a linha no histórico, mostrando "Pessoa removida do sistema" —
  documentado na Política de Privacidade.
- [x] **Termos de Uso + Política de Privacidade.** — **FEITO** (2026-08-19): tabela
  `termo_aceite` (versionada, por `usuario`, com IP), enforcement no cadastro nativo e
  Google, `precisaAceitarTermos`/`termosAceitosEm` em `/auth/me` e login, modal bloqueante
  de reaceite (`ModalReaceitarTermos`), páginas estáticas `/termos` e `/privacidade`. Ver
  spec/plano em `docs/superpowers/`.

---

### Fase 4 — Dashboard / início

- [x] Dashboard **simples de propósito**: 3–4 números-chave + 1 lista (ex.: próximos
  eventos) — **FEITO**: `DashboardService`/`/dashboard` (pessoas, eventos, financeiro,
  movimentações recentes, próximos eventos). Nada de gráfico complexo, como planejado.

> **➜ A igreja entra no ar em algum ponto entre a Fase 3 e a Fase 4.**
> As fases seguintes são a camada "vender pra igrejas externas".

---

### Fase 5 — Camada comercial (self-service pra igrejas externas)

> **Objetivo:** abrir o cadastro para igrejas de fora se registrarem sozinhas.

- Como o **cadastro do dono via Google já foi construído na Fase 1**, aqui sobra:
    - **Expor o cadastro publicamente** (hoje é uso interno/piloto).
    - **Polir o onboarding pós-cadastro:** boas-vindas e próximos passos (continuar
      cadastro, cadastrar pessoa, ir pro painel…).
    - **Aviso de acesso a novos usuários:** quando o admin concede acesso a uma pessoa,
      notificar por e-mail ("você tem acesso, entre com Google") — depende do e-mail
      transacional da Fase 1.
    - Qualquer trava comercial (ex.: escolha de plano) — depende do estudo da Fase 6.

---

### Fase 6 — Estudo (não é build)

- [x] **Estudo de pagamento (a: cobrança de eventos pagos)** — **FEITO E JÁ EM PRODUÇÃO**
  (V29-V32, 2026-08-25/26): provedor escolhido foi **Mercado Pago**, via OAuth
  (`ContaPagamentoIgreja`) — cada igreja conecta a própria conta, o Domus nunca guarda
  dinheiro de ninguém. Payment Brick embutido (cartão + Pix), webhook + poll ativo pra
  confirmação, estorno automático no cancelamento, token renovado sozinho antes de
  vencer, pagamento/estorno entrando no financeiro da igreja. Superou o texto original
  desta fase (que previa só uma recomendação, sem código) — decisão validada com uso real
  no piloto, não só estudo de mesa. *Ainda em aberto, ver `docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`:*
  escolha de meio de pagamento/parcelamento por evento, quem absorve a taxa do Mercado
  Pago, taxa aparecer separada no financeiro.
- [ ] **Estudo de pagamento (b: cobrança das igrejas pelos planos do Domus)** — ainda não
  feito, só o item (a) foi resolvido. Continua exatamente como descrito originalmente:
  decidir provedor/modelo pra cobrar a própria assinatura da igreja no Domus (distinto de
  cobrar o evento pago *dela* dos membros dela) — depende da Fase 5 (camada comercial)
  fazer sentido de verdade.

---

## Fora do escopo desta versão (anotado pra não esquecer)

Deixado para o **fim deste scope** ("versão pra minha igreja") ou depois:

- ~~Filtros extras em movimentação financeira (ex.: por atribuinte/pessoa).~~ **FEITO**
  (`pessoaId` em `MovimentacaoFinanceiraController.listar`/`totais`).
- ~~Múltiplos atribuintes numa mesma movimentação financeira.~~ **FEITO** (V15,
  `MOVIMENTACAO_CONTRIBUINTE` — ver diagrama ER acima; ganhou contribuinte sem cadastro
  em V32).
- Verificação de **posse** de telefone via SMS (pago, com atrito — só se houver
  necessidade real de antifraude).
- Expansão de campos de pessoa dirigida por uso real (YAGNI).
- Novos itens que surgirem — anotar aqui, em vez de embutir no meio do caminho.

---

## Decisões já tomadas (guardrails)

- Igreja é **design partner** (piloto/soft opening), não cliente comercial — sem
  self-service nem billing para o piloto.
- **Autenticação = híbrida (Google OAuth + e-mail/senha nativo).** Decisão confirmada em
  2026-07-14: as duas formas convivem. O e-mail/senha nativo (bcrypt) já existe e
  funciona; falta o "esqueci minha senha" (depende do e-mail transacional). O Google
  (login + cadastro) entra novo. Como o nativo continua, reset de senha, rate limiting e
  proteção a força bruta continuam valendo — não somem.
- **Provisionamento ≠ autenticação.** O admin concede acesso (provisionamento, sem
  OAuth); a pessoa loga com Google (autenticação). O **e-mail** (`pessoa.email`, único) é
  a chave que liga a identidade do Google ao usuário. O **primeiro login com Google** já
  serve de verificação de posse do e-mail.
- **Auth é fundação:** construída (Fase 1) antes de provisionamento de pessoas e
  configurações.
- Nos dois caminhos (Google ou nativo), **o app emite os próprios JWT + refresh** após
  identificar a pessoa; refresh/revogação e rate limiting valem para ambos.
- Endereço = **colunas estruturadas na tabela `pessoa`**, não tabela separada (habilita
  filtro por bairro sem over-engineering). Regra geral: tabela nova é para N-para-N ou
  dado repetido/compartilhado — não para 1-para-1.
- Telefone e e-mail = **validação de formato** apenas; SMS de posse fica fora por ora.
- Pagamento de eventos = **integrado (Mercado Pago), não é mais só estudo** — feito e em
  produção (V29-V32, ver Fase 6 e diagrama ER). Cobrança das igrejas pelos planos do
  Domus (o outro caso do estudo original) continua em aberto.
- Auditoria de evento = **reusar o padrão de `movimentacao_financeira`**.

---

## Ordem de execução resumida

`Fase 1 (fundações + auth) → Fase 2 → Fase 3 → Fase 4` *(igreja no ar)* `→ Fase 5 → Fase 6`

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

---

## Session Start Protocol ⚡

**MANDATORY** at start of each session:

```bash
# Load essential docs (~800 tokens - 2 min read)
✓ .claude/COMMON_MISTAKES.md      # ⚠️ CRITICAL - Read FIRST
✓ .claude/QUICK_START.md          # Essential commands
✓ .claude/ARCHITECTURE_MAP.md     # File locations
```

**At task completion:**
- Create completion doc in `.claude/completions/YYYY-MM-DD-task-name.md`
- Move session file to `.claude/sessions/archive/` (if created)

**⚠️ NEVER auto-load:**
- Files in `.claude/completions/` (0 token cost)
- Files in `.claude/sessions/` (0 token cost)
- Files in `docs/archive/` (0 token cost)

---

**Last Updated**: 2026-08-26
**Optimized with**: [Claude Token Optimizer](https://github.com/nadimtuhin/claude-token-optimizer)
