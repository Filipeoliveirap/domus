# Ambiente da apresentação do TCC

Stack local completa e **isolada** do ambiente de trabalho: Postgres, Redis e Elasticsearch
próprios, em portas e volumes próprios.

## Por que existe separada

O TCC roda a branch **`develop`**, cujo schema é anterior à renomeação `membro`→`pessoa`, ao
upload de foto e à consolidação das migrations. O ambiente de trabalho roda **`producao`**.

Enquanto os dois dividiam a mesma infra, trocar de branch quebrava o outro lado: o Flyway
aplicava as migrations da branch atual sobre o banco da outra.

**Isso aconteceu de verdade em 22/07/2026.** Depois de uma apresentação feita a partir da
`develop`, o banco de desenvolvimento voltou ao schema antigo (tabela `membro`, migrations
V1–V9) e 28 testes passaram a falhar por falha de carga de contexto do Spring. O diagnóstico
custou tempo porque o sintoma (`Failed to load ApplicationContext`) não aponta para a causa.

Com stacks separadas, isso não pode mais acontecer.

## Como subir

```bash
cd backend/api
docker compose -f docker-compose.tcc.yml up -d
```

Depois, rode a aplicação com as variáveis do TCC:

```bash
set -a; . ./.env.tcc; set +a
mvn spring-boot:run
```

⚠️ Certifique-se de estar na branch **`develop`** — o schema do banco do TCC é o dela.

## Como descer

```bash
docker compose -f docker-compose.tcc.yml down       # preserva os dados
docker compose -f docker-compose.tcc.yml down -v    # APAGA os dados junto
```

## Portas

| Serviço | TCC | Trabalho |
|---|---|---|
| Postgres | **5433** | Neon (online) |
| Redis | **6380** | 6379 |
| Elasticsearch | **9201** | 9200 |
| Kibana | — | 5601 |

As duas stacks podem ficar de pé ao mesmo tempo sem se enxergar.

## O que o `.env.tcc` desliga

- **Fotos em memória** (`FOTOS_ARMAZENAMENTO=memoria`) — sem R2, sem internet, sem credencial.
  Consequência esperada: foto enviada some ao reiniciar a aplicação.
- **E-mail só no log** (`EMAIL_PROVIDER=log`) — nenhuma mensagem real sai durante a demonstração.
- **Sentry sem DSN** — erro de demonstração não polui o painel real.
- **`JWT_SECRET` próprio**, gerado só para este ambiente. Não é o de dev nem o de produção: se
  vazar, não dá acesso a nada além deste banco local.

## Dados

`dump-tcc-2026-07-22.sql` é o estado apresentado ao orientador em 22/07/2026:
1 igreja, 26 membros, 3 usuários, 11 eventos, 8 categorias, 133 movimentações.

Restaurar:

```bash
docker exec -i domus-db-tcc psql -U domus -d domus < scripts/tcc/dump-tcc-2026-07-22.sql
```

Gerar um dump novo depois de mexer na demonstração:

```bash
docker exec domus-db-tcc pg_dump -U domus -d domus --clean --if-exists \
  > scripts/tcc/dump-tcc-$(date +%F).sql
```
