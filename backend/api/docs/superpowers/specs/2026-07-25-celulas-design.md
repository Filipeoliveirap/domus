# Células — design

> Segundo de três specs derivados do brainstorm de 2026-07-25 (célula, visitantes,
> novas roles). Depende do spec de Visitantes (`2026-07-25-visitantes-design.md`) já
> existir — este spec ALTERA a tabela `visitante` e a tela `/visitantes` por cima
> daquele trabalho. Terceiro spec (roles `SECRETARIO`/`TESOUREIRO` + múltiplas roles
> por usuário) é independente e não depende deste.

## Motivação

Célula é o pequeno grupo de estudo bíblico da igreja — ao contrário de Ministério
(onde só gente já vinculada à igreja participa), célula recebe tanto pessoas
cadastradas quanto visitantes que ainda não têm vínculo formal. O sistema precisa
modelar essa dualidade sem duplicar cadastro, e dar um caminho natural pro visitante
que "pegou gosto" na célula virar de fato membro/congregante da igreja.

Termo "Célula" fica hardcoded por enquanto (mesma decisão já tomada para "Rede"):
quando abrir para outras igrejas, vira config por igreja — ver
`rotulo-ministerio-self-service-fase5` (memória) e o item já registrado no backlog.

## Modelo de dados

Migration nova (próxima disponível após `V10__visitante.sql`, do spec anterior).

**`celula`** — mesmo padrão de `ministerio` (soft delete, nome único por igreja
case/acento-insensitive via `imutavel_unaccent`, auditoria `criado_por`/`atualizado_por`),
mais dois campos de agenda:

```sql
CREATE TABLE celula (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(150) NOT NULL,
    dia_semana VARCHAR(20),   -- SEGUNDA|TERCA|QUARTA|QUINTA|SEXTA|SABADO|DOMINGO, opcional
    horario    TIME,          -- opcional
    criado_por_usuario_id     UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);
CREATE UNIQUE INDEX ux_celula_igreja_nome
    ON celula (igreja_id, LOWER(imutavel_unaccent(nome)))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_celula_igreja ON celula (igreja_id) WHERE deleted_at IS NULL;
```

Sem campo de endereço do encontro (varia semana a semana, ex.: "casa do fulano") —
fica fora de escopo, feature futura se o uso real pedir (anotar no backlog).

**`celula_membro`** — vínculo polimórfico (pessoa OU visitante, nunca os dois), com a
regra "**uma célula por vez**" (diferente de `ministerio_membro`, que permite N
ministérios simultâneos): os índices únicos são por `pessoa_id`/`visitante_id` sozinhos,
não por `(celula_id, pessoa_id)`.

```sql
CREATE TABLE celula_membro (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id    UUID NOT NULL REFERENCES igreja(id),
    celula_id    UUID NOT NULL REFERENCES celula(id),
    pessoa_id    UUID REFERENCES pessoa(id),
    visitante_id UUID REFERENCES visitante(id),
    papel        VARCHAR(20) NOT NULL DEFAULT 'MEMBRO', -- LIDER | MEMBRO
    criado_por_usuario_id     UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_celula_membro_xor CHECK (
        (pessoa_id IS NOT NULL AND visitante_id IS NULL) OR
        (pessoa_id IS NULL AND visitante_id IS NOT NULL)
    ),
    CONSTRAINT chk_celula_membro_lider_e_pessoa CHECK (
        papel <> 'LIDER' OR pessoa_id IS NOT NULL   -- visitante nunca é líder:
        -- não tem login/usuário no sistema, não há como ele "gerenciar" nada de fato.
    )
);
CREATE UNIQUE INDEX ux_celula_membro_pessoa ON celula_membro (pessoa_id) WHERE pessoa_id IS NOT NULL;
CREATE UNIQUE INDEX ux_celula_membro_visitante ON celula_membro (visitante_id) WHERE visitante_id IS NOT NULL;
CREATE INDEX ix_celula_membro_celula ON celula_membro (celula_id);
```

"Mover para outra célula" é um `UPDATE celula_membro SET celula_id = ...` na mesma
linha (preserva o vínculo), não um delete+insert — os índices únicos acima já impedem
a pessoa/visitante de acabar em duas células ao mesmo tempo por engano.

**Alteração em `visitante`** (2 colunas novas — este spec altera a tabela do spec
anterior):

```sql
ALTER TABLE visitante ADD COLUMN entrou_em_celula_em TIMESTAMP;
ALTER TABLE visitante ADD COLUMN convertido_pessoa_id UUID REFERENCES pessoa(id);
```

- `entrou_em_celula_em`: marcado **uma vez**, na primeira vez que o visitante entra em
  alguma célula — nunca desfeito, mesmo que ele saia depois. É o dado que sustenta o
  relatório futuro "quantos visitantes já entraram em alguma célula". **Diferente** de
  "está numa célula *agora*", que é sempre uma consulta viva em `celula_membro` (join),
  nunca lida deste campo.
- `convertido_pessoa_id`: marcado no momento da conversão (visitante → pessoa). Sustenta
  o relatório futuro "quantos de uma célula viraram membro/congregante".

## Autorização

| Ação | Quem pode |
|---|---|
| Criar célula | Só `ADMIN_IGREJA` |
| Renomear / editar dia-horário | `ADMIN_IGREJA` **ou** líder daquela célula |
| Arquivar (soft delete) | Só `ADMIN_IGREJA` |
| Apagar de vez (só se vazia) | Só `ADMIN_IGREJA` |
| Promover/rebaixar líder | Só `ADMIN_IGREJA` |
| Adicionar/remover/mover membro (pessoa ou visitante) | `ADMIN_IGREJA` **ou** líder daquela célula |
| "Tornar membro/congregante" um visitante da célula | `ADMIN_IGREJA` **ou** líder daquela célula |
| Ver lista de células e quem está em cada uma | Qualquer usuário autenticado |

`CelulaService.ehLiderDaCelula(pessoaId, celulaId)` reaproveita a mesma lógica de
`MinisterioService.ehLiderDoMinisterio` (existência em `celula_membro` com
`papel = LIDER`) — mesmo padrão de autorização por recurso, não por perfil.

## Endpoints

- `GET /celulas` — lista (qualquer autenticado)
- `GET /celulas/{id}` — detalhe: nome, dia/horário, membros (pessoa e visitante, com
  badge de tipo e papel), `souLiderDestaCelula` (mesmo padrão do `souLiderDesteMinisterio`)
- `POST /celulas` `{nome, diaSemana?, horario?}` — `ADMIN_IGREJA`
- `PUT /celulas/{id}` `{nome, diaSemana?, horario?}` — `ADMIN_IGREJA` **ou** líder
- `DELETE /celulas/{id}` — arquivar (soft delete) — `ADMIN_IGREJA`
- `DELETE /celulas/{id}/definitivo` — apagar de verdade; **409** se a célula tiver
  algum membro — `ADMIN_IGREJA`
- `POST /celulas/{id}/membros` `{pessoaId}` **ou** `{visitanteId}` — adiciona; se a
  pessoa/visitante já está em outra célula, **move** (atualiza a linha existente,
  idempotente — sem erro) — `ADMIN_IGREJA` ou líder
- `DELETE /celulas/{id}/membros/{membroId}` — remove da célula — `ADMIN_IGREJA` ou líder
- `PUT /celulas/{id}/membros/{membroId}/papel` `{papel}` — promove/rebaixa líder;
  400 se o alvo for visitante — `ADMIN_IGREJA`
- `POST /celulas/{id}/converter/{visitanteId}` `{vinculo: MEMBRO|CONGREGANTE}` — cria
  `Pessoa` a partir dos dados do visitante (nome, telefone, endereço, sexo,
  dataNascimento, estadoCivil + vínculo escolhido), atualiza a linha de
  `celula_membro` (`visitante_id → null`, `pessoa_id → nova pessoa`), marca
  `visitante.convertido_pessoa_id`. Retorna a `Pessoa` criada — o front navega para a
  tela de edição dela, pra secretaria completar o cadastro. `ADMIN_IGREJA` ou líder.

### Ajustes no módulo de Visitantes (spec anterior)

- `GET /visitantes` passa a excluir quem tem linha ativa em `celula_membro`
  (`NOT EXISTS`) — some da lista ativa ao entrar numa célula, sem duplicar estado.
- `PUT /visitantes/{id}/celula` `{celulaId}` — novo endpoint, usado pelo botão "Mover
  para célula" da tabela de Visitantes; grava `entrou_em_celula_em` só se ainda nulo
  (não sobrescreve numa segunda mudança de célula), e cria/move a linha em
  `celula_membro`.
- `DELETE /visitantes/{id}` passa a responder **409** se o visitante tiver linha ativa
  em `celula_membro` — nesse ponto ele já nem aparece na lista (então a UI não chega a
  oferecer o botão), mas a API recusa por segurança mesmo assim.

## Frontend

- **Página `/celulas`**: mesmo padrão de `/ministerios` — breadcrumb, título com
  contador, cards com nome, líder, dia/horário e quantidade de membros.
  `ADMIN_IGREJA` cria; líder da própria célula também vê "Editar" (nome/dia/horário)
  no menu do card, mas não "Arquivar"/"Apagar".
- **Página `/celulas/{id}`**: lista de membros com badge distinguindo Pessoa de
  Visitante, badge de líder. Botão "Adicionar pessoa" abre modal com:
  - Toggle "Pessoas cadastradas" / "Visitantes" — busca por nome em cada tabela
    (reaproveita `usePessoas`/`useVisitantes`).
  - Botão "Cadastrar pessoa de fora" — abre um mini-formulário com os campos de
    Visitante direto no modal; ao salvar, cria o Visitante e já adiciona na célula.
  - Selecionar um resultado adiciona (ou move, se já estava em outra célula) direto.
- Para membros do tipo **Visitante**: botão "Tornar membro/congregante" — abre escolha
  simples (Membro/Congregante), confirma, cria a `Pessoa` e navega para a tela de
  edição dela pra secretaria completar o cadastro.
- **Tabela de Visitantes** (ajuste no spec anterior): nova ação "Mover para célula" na
  linha (abre modal de escolha de célula, busca por nome); quem já está numa célula
  não aparece mais nessa lista (fica só na tela da própria célula).

## Fora de escopo (deste spec)

- Endereço do encontro da célula (varia semana a semana — feature futura, anotar
  backlog).
- Relatórios (visitante → célula → membro) — usa os dados que este spec grava
  (`entrou_em_celula_em`, `convertido_pessoa_id`), mas o relatório em si é outra
  entrega.
- Multiplicação de célula, líder em treinamento, supervisor/rede de supervisão — modelo
  do "cell church" clássico, mas o autor decidiu simplificar (sem essas regras por ora).
- Novas roles `SECRETARIO`/`TESOUREIRO` e múltiplas roles por usuário — spec próprio,
  independente.
