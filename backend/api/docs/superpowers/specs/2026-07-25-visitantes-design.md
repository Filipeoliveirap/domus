# Visitantes — design

> Contexto: primeiro de três specs derivados de uma reunião de brainstorm em 2026-07-25
> (célula, visitantes, novas roles). Visitantes é a base — resolve o problema real do
> cartão de visitante em papel, que se perde, substituindo por um cadastro simples.
> Célula (próximo spec) constrói em cima desta tabela: adiciona o vínculo com célula,
> a ação "mover para célula" e "tornar membro/congregante da igreja". Nenhuma dessas
> três coisas está neste spec — ver "Fora de escopo".

## Motivação

Hoje, quando alguém visita a igreja pela primeira vez, os dados de contato (nome,
telefone, endereço) ficam num cartão de papel preenchido na hora — que se perde com
frequência. Este módulo substitui o cartão por um cadastro simples no sistema, com um
jeito de marcar se já rolou contato/visita/acompanhamento com aquela pessoa, pra não
perder o fio de quem já foi atendido.

## Modelo de dados

Tabela nova `visitante` (migration `V10__visitante.sql`), seguindo o padrão de endereço
estruturado já usado em `pessoa`/`igreja`/`local_evento`, e reaproveitando os enums
`Sexo`/`EstadoCivil` que já existem no módulo `pessoa` (não duplica).

**Decisão deliberada: sem soft delete.** Ao contrário de quase toda entidade do projeto,
`visitante` não tem `deleted_at`. O botão "Apagar" da tabela é uma exclusão real
(`DELETE FROM`), porque neste spec o visitante ainda não tem nenhum vínculo com outra
entidade (sem célula, sem pessoa) — não há nada pra "arquivar por segurança", e o
próprio autor decidiu que faz sentido apagar de verdade. Isso pode mudar quando o spec
de Célula adicionar o vínculo `celula_id`: nesse momento, um visitante já vinculado a
uma célula provavelmente não poderá mais ser apagado desse jeito (fica pra aquele spec
decidir).

```sql
CREATE TABLE visitante (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(255) NOT NULL,
    telefone   VARCHAR(20),
    cep          VARCHAR(9),
    logradouro   VARCHAR(255),
    numero       VARCHAR(20),
    complemento  VARCHAR(255),
    bairro       VARCHAR(255),
    cidade       VARCHAR(255),
    uf           VARCHAR(2),
    sexo          VARCHAR(20),   -- HOMEM | MULHER (enum Sexo, reaproveitado de pessoa)
    estado_civil  VARCHAR(20),   -- enum EstadoCivil, reaproveitado de pessoa
    data_nascimento DATE,
    tem_filhos       BOOLEAN NOT NULL DEFAULT false,
    quantidade_filhos INTEGER,  -- só tem sentido quando tem_filhos = true; UI garante
    observacoes TEXT,
    contato_realizado      BOOLEAN NOT NULL DEFAULT false,
    visita_realizada       BOOLEAN NOT NULL DEFAULT false,
    acompanhamento_feito   BOOLEAN NOT NULL DEFAULT false,
    criado_por_usuario_id     UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_visitante_quantidade_filhos
        CHECK (quantidade_filhos IS NULL OR quantidade_filhos >= 0)
);

CREATE INDEX ix_visitante_igreja ON visitante (igreja_id);
```

Os três marcadores (`contato_realizado`, `visita_realizada`, `acompanhamento_feito`) são
independentes — qualquer combinação é válida (ex.: visita feita sem contato registrado
antes, embora incomum na prática).

## Autorização

`Permissoes.podeGerenciarVisitantes(role)` — hoje resolve para `ADMIN_IGREJA` apenas
(`SO_ADMIN`). Quando o spec de roles adicionar `SECRETARIO`, basta incluir no conjunto
que essa função consulta — nenhum call site muda (mesmo princípio de "capacidade, não
identidade" já usado no projeto).

## Endpoints

- `GET /visitantes` — lista paginada; parâmetros `q` (busca por nome), `contatoRealizado`,
  `visitaRealizada`, `acompanhamentoFeito` (cada um `true`/`false`/ausente = sem filtro),
  `page`, `size`
- `GET /visitantes/{id}` — detalhe
- `POST /visitantes` — criar
- `PUT /visitantes/{id}` — editar
- `DELETE /visitantes/{id}` — apagar (hard delete de verdade)
- `PUT /visitantes/{id}/contato` — alterna `contato_realizado`
- `PUT /visitantes/{id}/visita` — alterna `visita_realizada`
- `PUT /visitantes/{id}/acompanhamento` — alterna `acompanhamento_feito`

Os três endpoints de toggle existem separados do `PUT` completo porque são acionados
direto na linha da tabela (um clique), sem abrir o formulário de edição inteiro.

### Erros

- `nome` em branco → 400 (validação padrão)
- `DELETE` de visitante inexistente (ou de outra igreja) → 404
- Toggle de visitante inexistente (ou de outra igreja) → 404

## Frontend

- **Página `/visitantes`**: mesmo padrão já estabelecido em Pessoas/Eventos/Usuários/Redes
  — breadcrumb (`Início > Visitantes`), título com contador (`Visitantes` + badge com o
  total), campo de busca por nome, paginação.
- **Tabela**: colunas Nome, Telefone, e três indicadores (badges/ícones) para
  Contato/Visita/Acompanhamento — clicáveis direto na linha (chama o endpoint de toggle
  correspondente), sem precisar abrir o cadastro. Estado visual bem distinto entre
  "feito" e "pendente" (cor sólida vs. contorno, por exemplo) pra dar pra escanear a
  lista de relance e saber quem falta atender.
- **Filtros**: três selects independentes (Todos/Feito/Pendente) para Contato, Visita e
  Acompanhamento, combináveis entre si e com a busca por nome.
- **Ações por linha**: Editar (abre o formulário) e Apagar (exclusão real — modal de
  confirmação reforçada, "digite o nome para confirmar", mesmo componente
  `ModalConfirmacaoCritica` já usado para arquivar local/ministério).
- **Formulário de cadastro/edição**: nome (obrigatório), telefone, endereço (com
  autopreenchimento por CEP via ViaCEP, igual ao formulário de pessoa), sexo, data de
  nascimento, estado civil, checkbox "Tem filhos?" que revela o campo de quantidade
  quando marcado, observações (texto livre). Os três marcadores de acompanhamento **não**
  aparecem no formulário — só existem como toggle na tabela, evitando duas superfícies
  controlando o mesmo estado.

## Fora de escopo (deste spec)

- **Vínculo com célula** (`celula_id` em `visitante`), ação "mover para célula" (modal de
  escolha de célula) — spec de Célula.
- **"Tornar membro/congregante da igreja"** — ação que cria um registro em `pessoa` a
  partir de um visitante que já está numa célula, escolhendo o vínculo (MEMBRO ou
  CONGREGANTE) e depois permitindo à secretaria completar o cadastro. Vive na tela de
  Célula (ação sobre um membro do tipo visitante), não na tela de Visitantes — spec de
  Célula.
- **Relatórios** (quantos visitantes viraram célula, quantos de célula viraram membro) —
  depende do spec de Célula existir primeiro (usa os vínculos criados lá).
- **Nova role `SECRETARIO`** — spec próprio, independente.
- Permitir apagar um visitante já vinculado a uma célula (revisar quando o spec de
  Célula adicionar esse vínculo).
