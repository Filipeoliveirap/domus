# Design — Backup automático do Postgres

- **Data:** 2026-07-16
- **Fase:** 1 (fundações) — item de segurança, pré-requisito para dado real
- **Status:** design aprovado, pendente plano de implementação

## Problema

O Domus vai guardar dado cadastral e financeiro real de igrejas. Perda é imperdoável. O
roadmap exige: *"rotina agendada de dump, política de retenção e teste periódico de
restauração — backup que nunca foi restaurado não é backup, é esperança."*

## Por que o PITR do Neon não basta

O banco é **Neon, plano Free**, que dá **6 horas** de point-in-time recovery (limitado a 1 GB
de mudanças). Não 24h — **6 horas**.

Isso inverte o papel do dump externo. Ele não é a segunda rede de segurança: **é a primeira e
única**. Cenário real: alguém apaga os membros na sexta à noite, ninguém percebe até segunda
de manhã. O PITR fechou a janela 60 horas antes. Sem dump externo, o dado acabou.

E mesmo com PITR longo, ele **compartilha o destino do original**: conta suspensa por
pagamento, credencial comprometida, projeto apagado por engano ou mudança de tier levam banco
e backup juntos. Backup que mora no mesmo lugar que o dado não é backup — é redundância.

## Arquitetura

```
GitHub Actions (cron diário, ~03:00 BRT)
      │
      ├─ 1. check-in "in_progress" ─────────→ Sentry Crons
      ├─ 2. pg_dump -Fc  (Neon → arquivo)
      ├─ 3. TESTE: restaura num Postgres descartável (service container) e confere
      ├─ 4. age -r <chave pública>  (criptografa)
      ├─ 5. upload  ────────────────────────→ Cloudflare R2
      └─ 6. check-in "ok" | "error" ────────→ Sentry Crons
                                                   │
                                       ausência de check-in = alerta
```

Sem servidor, sem cron para manter, e **independente da decisão de hospedagem** (que ainda
não foi tomada).

## Decisões

### Onde roda: GitHub Actions

Já existe, é grátis nessa escala, e não depende de onde a aplicação for hospedada.

**Armadilha conhecida:** o GitHub **desativa workflows agendados após 60 dias sem commit no
repositório**. O backup pararia **em silêncio**. Não se resolve prevendo — resolve-se
monitorando (ver Sentry Crons abaixo).

### Alertas: Sentry Crons (dead man's switch)

**Todo plano do Sentry, inclusive o Developer grátis, inclui 1 monitor de cron com check-ins
ilimitados.** O Sentry já está configurado no projeto (feito em 2026-07-16). **Zero conta
nova.**

O monitor compara os check-ins com o horário esperado e marca `missed` quando o job não roda,
criando um issue e notificando por e-mail.

**Por que isso é o desenho certo:** em vez de tentar prever cada forma de quebrar
(workflow desativado por inatividade, falha silenciosa, GitHub fora do ar, credencial do Neon
expirada, R2 recusando), monitora-se a **única coisa que importa**: *"o backup aconteceu?"*.
Qualquer causa produz o mesmo sintoma — ausência de check-in — e o mesmo alerta.

### Storage: Cloudflare R2

10 GB grátis, zero egress, API compatível com S3. **Exige cartão cadastrado** (não cobra
dentro do limite).

**Por que R2 e não Backblaze B2** (também 10 GB grátis): a conta Cloudflare serve **três
propósitos**, e dois já são necessários:

1. **DNS** do `domusigreja.com.br` (comprado em 2026-07-16);
2. **Proxy reverso** — resolve o requisito descoberto no design de cookies: *"precisa de um
   proxy reverso real na frente setando `X-Forwarded-For`, senão o rate limiting vira um
   balde único"*. Também entrega TLS e HSTS de borda;
3. **R2** para os backups.

Backblaze só faria a terceira. A escolha da Cloudflare já entra na conversa de hospedagem que
vem a seguir.

### Criptografia: `age`, assimétrica

O dump contém **dado pessoal** (nome, e-mail, telefone, endereço, data de nascimento,
`observacoes` — possíveis anotações pastorais) e **o financeiro inteiro da igreja**. Um
arquivo desses num bucket mal configurado é vazamento de dado pessoal com nome e sobrenome —
LGPD, não hipótese.

**Assimétrica de propósito:** a chave **pública** fica no GitHub e só serve para *escrever*.
A chave **privada** fica com o autor, offline. Assim, **GitHub comprometido não lê backup
nenhum**.

> ⚠️ **PONTO ÚNICO DE FALHA: a chave privada.** Perdê-la torna **todos** os backups lixo.
> Ela precisa estar no gerenciador de senhas **e** num segundo lugar independente (papel no
> cofre, pendrive). Backup criptografado com chave perdida é indistinguível de não ter backup.

### A ordem importa: testar ANTES de criptografar

A tentação é "criptografa e testa a restauração". **Não funciona**: se o CI só tem a chave
pública, ele **não consegue descriptografar** — e é exatamente isso que se quer.

Ordem correta: **dump → testa em claro → criptografa → sobe**. O job restaura o dump num
Postgres descartável (service container) e só então criptografa e envia.

**O que o teste compara:** a contagem de linhas de **cada tabela do schema `public`**, na
origem (Neon) contra o destino (o container restaurado). Iguais → passa.

Comparar contra a origem, e não checar "tem pelo menos uma linha", é deliberado por dois
motivos:

- **"Tem linha" daria falso negativo**: um banco legitimamente vazio (ou uma tabela sem
  registros) reprovaria um dump perfeito.
- **"Tem linha" daria falso positivo**, que é pior: um dump truncado com 3 de 300 membros
  passaria alegremente. Só a comparação com a origem pega dump parcial.

A contagem da origem é tirada **na mesma execução**, logo após o dump. Uma diferença pequena
por escrita concorrente é possível em teoria; na prática, às 03:00 o sistema está parado — e
`pg_dump` usa uma transação consistente, então o dump é um retrato de um instante. Se a
divergência aparecer na vida real, aí sim vale investigar em vez de afrouxar o teste.

**O que esse teste prova:** que o dump é íntegro e restaurável.
**O que ele NÃO prova:** que o arquivo criptografado abre com a chave certa. Uma chave errada
produziria 90 backups perfeitos e ilegíveis. Isso só um humano verifica — ver "Ensaio manual".

### Formato: `pg_dump -Fc`

Custom format: comprimido e permite **restauração seletiva** — dá para recuperar *uma* tabela
sem derrubar o resto do banco. Num incidente real ("apagaram os membros"), isso é a diferença
entre cirurgia e amputação.

### Frequência e retenção

| Item | Escolha | Por quê |
|---|---|---|
| **Frequência** | 1×/dia, ~03:00 BRT | A igreja lança dízimo no domingo e cadastra membro na quarta. Perder 24h = redigitar poucos lançamentos. 2×/dia custaria ≈ nada, mas dobra peças por ganho inexistente |
| **Retenção** | 90 dias | Dump de igreja tem poucos MB; cabe folgado nos 10 GB |
| **Como expira** | *Lifecycle rule* do R2 | Não por script: menos código para manter e não falha junto com o job |

## Segredos e configuração

> ⚠️ **A `DATABASE_URL` do projeto NÃO serve para o `pg_dump`.** Ela está em formato **JDBC**
> (`jdbc:postgresql://ep-....sa-east-1.aws.neon.tech/neondb?sslmode=require`) e o usuário e a
> senha vivem em variáveis separadas. O `pg_dump` fala **libpq**
> (`postgresql://user:senha@host/db?sslmode=require`). É preciso um secret **próprio**, já no
> formato libpq. Reaproveitar o existente quebra na primeira execução.

Secrets no GitHub:

| Secret | O quê |
|---|---|
| `BACKUP_DATABASE_URL` | string libpq do Neon (formato acima) — **não** o JDBC |
| `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` | credenciais do R2 |
| `AGE_PUBLIC_KEY` | chave pública (não é segredo, mas fica junto por conveniência) |
| `SENTRY_CRONS_URL` | URL de check-in do monitor |

> ⚠️ **Versão do `pg_dump` ≥ versão do servidor.** Um `pg_dump` mais novo dumpa servidor mais
> antigo; **o contrário quebra**. A versão do Neon não pôde ser verificada ao escrever este
> spec (sem `psql` na máquina). **Primeiro passo do plano: conferir `select version()` e fixar
> a versão do cliente no workflow.**

**Nota sobre o Neon Free:** o compute **suspende por inatividade**. A primeira conexão do job
acorda o banco e pode demorar — o timeout precisa ser generoso, não o padrão.

## Ensaio manual trimestral (não automatizável)

O teste automático cobre a integridade do dump. **A camada de criptografia só um humano
testa.** Trimestralmente:

1. Baixar o backup mais recente do R2.
2. Descriptografar com a **chave privada**.
3. Restaurar num Postgres local.
4. Conferir que os dados estão lá.

É chato de propósito. É o *"backup que nunca foi restaurado não é backup, é esperança"* do
roadmap, aplicado onde a automação não alcança. **Registrar como lembrete recorrente no
calendário** — não depender de memória.

## Verificação (critério de pronto)

- O workflow roda e o arquivo aparece no R2, criptografado.
- O teste de restauração falha o job quando as contagens divergem — validar **provocando** a
  falha (ex.: truncar o dump de propósito), não só vendo passar. *Um teste que nunca foi
  visto falhar não prova nada.*
- O Sentry mostra o monitor como `ok` após uma execução.
- **Simular ausência**: desligar/atrasar o job e confirmar que o Sentry marca `missed` e
  notifica. *Um alerta que nunca disparou é tão confiável quanto um backup nunca restaurado.*
- A *lifecycle rule* do R2 está ativa.
- O ensaio manual foi feito **uma vez**, antes de considerar o item pronto — inclusive para
  validar que a chave privada guardada é a certa.

## Fora de escopo

- **Backup do Redis:** contém só sessão (refresh tokens) e contadores de rate limit — dado
  descartável e de vida curta. Perder = usuários refazem login. Não vale backup.
- **Backup do Elasticsearch:** é índice derivado do Postgres, reconstruível pela reindexação
  que já existe (`ReindexacaoService`). Restaurar o Postgres e reindexar é o caminho.
- **Replicação/alta disponibilidade:** outro problema (disponibilidade, não durabilidade).
  Fora do escopo do piloto.
- **Upgrade do plano do Neon:** aumentaria o PITR, mas não resolve o problema principal (tudo
  continua no Neon). Reavaliar quando houver receita.
