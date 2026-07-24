# Ministérios da igreja — design

> Contexto: prioridade definida em reunião com a igreja (2026-07-23), ver memória
> `prioridades-gestao-celula-ministerios`. Substitui o campo livre `pessoa.ministerio`
> por um cadastro estruturado, com liderança por ministério e fluxo de pedido de entrada.

## Motivação

Hoje `pessoa.ministerio` é uma coluna de texto livre: sem validação, sem lista fechada,
uma pessoa só pode ter um valor. Na prática uma pessoa participa de vários ministérios
(N-para-N) e a igreja quer que cada ministério tenha responsável(is) que gerenciam quem
entra/sai, sem depender do admin pra tudo.

## Modelo de dados

Segue o padrão já usado por `local_evento`/`categoria_financeira`: tabela por igreja,
nome único (case/acento-insensitive via `imutavel_unaccent`), soft delete, auditoria por
`criado_por_usuario_id`/`atualizado_por_usuario_id` (sem tabela de log genérica — fora de
escopo, ver "Fora de escopo").

Migration `V9__ministerio.sql`:

```sql
CREATE TABLE ministerio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL REFERENCES igreja(id),
    nome VARCHAR(150) NOT NULL,
    criado_por_usuario_id UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);
CREATE UNIQUE INDEX ux_ministerio_igreja_nome
    ON ministerio (igreja_id, LOWER(imutavel_unaccent(nome)))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_ministerio_igreja ON ministerio (igreja_id) WHERE deleted_at IS NULL;

CREATE TABLE ministerio_membro (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL REFERENCES igreja(id),
    ministerio_id UUID NOT NULL REFERENCES ministerio(id),
    pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    papel VARCHAR(20) NOT NULL DEFAULT 'MEMBRO', -- LIDER | MEMBRO
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',  -- PENDENTE | ATIVO
    criado_por_usuario_id UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (ministerio_id, pessoa_id)
);
CREATE INDEX ix_ministerio_membro_pessoa ON ministerio_membro (pessoa_id);
CREATE INDEX ix_ministerio_membro_ministerio ON ministerio_membro (ministerio_id);

-- descarta o texto livre antigo (sem migrar dado, decisão explícita)
ALTER TABLE pessoa DROP COLUMN ministerio;
```

Notas de modelagem:

- `UNIQUE (ministerio_id, pessoa_id)` cobre tanto "não pode pedir duas vezes" quanto "não
  pode ser adicionada duas vezes" — uma pessoa tem no máximo uma linha por ministério.
- Recusar pedido ou remover membro é **hard delete** da linha (não soft delete): é uma
  relação, não uma entidade de domínio com histórico próprio — decisão consistente com
  "log só criado_por/atualizado_por, sem timeline completa" (ver "Fora de escopo").
- `ministerio` tem soft delete (arquivar o cadastro em si). Se arquivado, `@SQLRestriction`
  na entidade `Ministerio` já filtra joins — `ministerio_membro` de um ministério arquivado
  para de aparecer nas listagens (a pessoa) sem precisar limpar a linha.
- Promover/rebaixar líder é só um `UPDATE papel` na linha existente — não precisa a pessoa
  sair e reentrar.

## Autorização

Perfis globais (`ADMIN_IGREJA`/`LIDER`/`ACESSO_COMUM`) **não bastam** aqui: "líder do
ministério X" é autorização por **recurso específico**, não por perfil — o perfil global
`LIDER` não dá nenhum poder automático sobre nenhum ministério.

| Ação | Quem pode |
|---|---|
| Criar / renomear / arquivar ministério | Só `ADMIN_IGREJA` |
| Promover/rebaixar líder (mudar `papel`) | Só `ADMIN_IGREJA` |
| Adicionar/remover membro comum | `ADMIN_IGREJA` **ou** líder ativo daquele ministério |
| Aceitar/recusar pedido de entrada | `ADMIN_IGREJA` **ou** líder ativo daquele ministério |
| Pedir pra entrar (self-service) | Qualquer usuário autenticado, só para a própria pessoa |
| Ver lista de ministérios e quem está em cada um | Qualquer usuário autenticado |
| Ver pedidos pendentes de um ministério | Só `ADMIN_IGREJA` ou líder daquele ministério |

Implementação: `MinisterioService` expõe um método
`ehLiderDoMinisterio(pessoaId, ministerioId)` (busca em `ministerio_membro` por
`papel = LIDER AND status = ATIVO`) usado internamente pelos endpoints de gestão de
membros/pedidos, além do check de perfil (`ADMIN_IGREJA` sempre passa). Isso fica
localizado no service deste módulo — **não** vira um motor de permissão genérico por
recurso (fora de escopo; se outro módulo precisar do mesmo padrão, generaliza-se então).
`Permissoes.java`/`permissoes.ts` ganham só `podeGerenciarCadastroMinisterios(role)` para
a parte perfil-baseada (criar/renomear/arquivar/promover); a checagem de "é líder deste
ministério" não tem como existir no front de forma confiável (dado dinâmico por recurso) —
o front confia no que a API retorna (ex.: `souLiderDesteMinisterio: boolean` no DTO de
detalhe) só para decidir o que mostrar, nunca pra decidir o que é permitido de fato.

## Endpoints

- `POST /ministerios` `{nome}` — `ADMIN_IGREJA`
- `PUT /ministerios/{id}` `{nome}` — `ADMIN_IGREJA`
- `DELETE /ministerios/{id}` — arquivar, `ADMIN_IGREJA`
- `GET /ministerios` — lista ativos da igreja, qualquer perfil
- `GET /ministerios/{id}` — detalhe: nome, membros ativos (com `papel`), pedidos
  pendentes (só se quem pede é admin/líder), `souLiderDesteMinisterio`
- `POST /ministerios/{id}/membros` `{pessoaId}` — adiciona direto, `status=ATIVO`,
  `papel=MEMBRO`; admin ou líder do ministério
- `DELETE /ministerios/{id}/membros/{pessoaId}` — remove; admin ou líder do ministério
- `PUT /ministerios/{id}/membros/{pessoaId}/papel` `{papel}` — `ADMIN_IGREJA`
- `POST /ministerios/{id}/pedidos` — pede entrada pra própria pessoa logada; cria linha
  `status=PENDENTE`
- `PUT /ministerios/{id}/pedidos/{pessoaId}/aceitar` — `PENDENTE → ATIVO`; admin ou líder
- `DELETE /ministerios/{id}/pedidos/{pessoaId}` — recusa, remove a linha; admin ou líder
- `GET /pessoas/{id}/ministerios` — ministérios ativos de uma pessoa (perfil, drawer)

### Erros

- Nome duplicado (case/acento-insensitive) → 409, mensagem amigável (`TextoUtil`, mesmo
  padrão de `LocalEvento`/`CategoriaFinanceira`)
- Pedido/adição quando já existe linha (`ATIVO` ou `PENDENTE`) para aquela pessoa → 409
- Ação de gestão (adicionar/remover/aceitar/recusar) por quem não é admin nem líder
  daquele ministério → 403
- Promover/rebaixar líder de pessoa que não é membro ativo → 400 (precisa ser membro
  primeiro)

## Frontend

- Página `/ministerios`: lista em cards (responsivo — mobile já entra ajustado, ver
  `mobile-responsivo-obrigatorio`). `ADMIN_IGREJA` vê botão "novo ministério"; demais
  perfis só veem a lista.
- Detalhe do ministério (drawer ou página, a definir no plano): membros com badge
  `LIDER`/`MEMBRO`; seção "pedidos pendentes" visível só se `souLiderDesteMinisterio` ou
  admin; botão "pedir pra entrar" visível só se a pessoa logada ainda não tem linha
  (nem `ATIVO` nem `PENDENTE`) naquele ministério.
- Perfil de pessoa (`DrawerDetalhePessoa`, `/perfil`): nova seção "Ministérios" listando
  os ministérios ativos da pessoa (via `GET /pessoas/{id}/ministerios`) — substitui o
  `MinisterioInput` de texto livre, que é removido do formulário de pessoa.
- `permissoes.ts` ganha `podeGerenciarCadastroMinisterios(role)` espelhando o back; a
  UI de gestão de membros/pedidos dentro de um ministério específico usa o
  `souLiderDesteMinisterio` vindo da API de detalhe, não um cálculo local.

### Prompt para o Google Stitch (tela de detalhe do ministério)

```
App de gestão administrativa para igrejas (Domus), estilo clean e profissional, cores
neutras com um azul de destaque. Preciso de uma tela de "detalhe do ministério" dentro
de um painel administrativo (sidebar já existe à esquerda, não desenhar).

Conteúdo da tela:
- Cabeçalho com nome do ministério (ex.: "Louvor") e, se o usuário logado for líder
  daquele ministério ou admin, um botão de opções (editar/arquivar) e um botão "adicionar
  pessoa".
- Uma lista/tabela de membros do ministério: foto (ou iniciais), nome, um badge indicando
  "Líder" para quem tem esse papel, e um botão de remover (só visível para quem gerencia).
- Uma seção separada "Pedidos pendentes" (só aparece para quem gerencia o ministério):
  cada linha mostra foto/nome de quem pediu entrada, com dois botões, aceitar (verde) e
  recusar (vermelho).
- Um botão "Pedir para entrar neste ministério", visível só para quem ainda não é membro
  nem tem pedido pendente — estado de "pedido enviado, aguardando aprovação" quando já
  pediu.
- Ambos os estados: com pedidos pendentes e sem nenhum pedido pendente (empty state
  simples, tipo "nenhum pedido pendente").
- Versão mobile: lista de membros vira cards empilhados em vez de tabela.

Gere 2-3 variações de layout para eu comparar.
```

## Fora de escopo (anotado, não é pra construir agora)

- Log de atividade genérico / timeline completa de ações (quem entrou, quem saiu, quem
  aceitou o quê, quando) — fica só `criado_por`/`atualizado_por` por enquanto. Se a
  igreja precisar de histórico completo depois, é um brainstorm próprio (padrão pra
  todas as tabelas, não só ministério).
- Notificação por e-mail de pedido pendente — líder vê dentro do sistema, sem e-mail.
- Reuniões, escalas, cronograma do ministério.
- Descrição/outros campos no cadastro do ministério além do nome.
- Autorização por recurso genérica (framework de permissão por dono de recurso) — a
  checagem de "é líder deste ministério" fica local a este módulo.
- Célula (módulo separado, aguardando resposta do pastor).

## Migração de dado existente

`ALTER TABLE pessoa DROP COLUMN ministerio` descarta o valor atual sem tentar
auto-vincular a um ministério estruturado (decisão explícita — o texto livre nunca foi
uma fonte confiável, sem normalização). Pessoas ficam sem nenhum ministério até
admin/líder adicionar de novo manualmente.
