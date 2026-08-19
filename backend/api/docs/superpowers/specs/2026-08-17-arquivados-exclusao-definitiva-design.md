# Lista de arquivados + exclusão definitiva (LGPD) — design

> Fase 3 do roadmap. Escopo: Pessoa, Usuário, Evento, Célula, Ministério, Categoria
> Financeira. Visitante fica de fora — não tem soft delete, exclusão já é definitiva
> desde sempre.

## Contexto

Hoje todo módulo listado tem soft delete (`deleted_at`), mas só Célula tem exclusão
definitiva (`DELETE /celulas/{id}/definitivo`), bloqueada quando há membro vinculado.
Não existe tela de "arquivados" em lugar nenhum — o registro arquivado só some da
listagem normal e não tem mais nenhuma ação disponível sobre ele.

O motivador oficial no roadmap é "direito de eliminação da LGPD", mas isso esbarra
numa regra real do banco: nenhuma FK que aponta pra Pessoa/Usuario/Evento tem
`ON DELETE CASCADE` — é `RESTRICT` por padrão do Postgres. Apagar de vez um registro
com histórico vinculado (movimentação financeira, inscrição em evento, etc.) quebraria
a query na hora. Célula só conseguiu implementar isso porque bloqueia antecipadamente
quando há vínculo.

## Vínculo por módulo

"Tem vínculo" decide tudo: se um módulo/instância tem vínculo, o comportamento muda.

| Módulo | "Tem vínculo" quando... | Sem vínculo | Com vínculo |
|---|---|---|---|
| Célula | tem `CelulaMembro` | Excluir direto (confirmação simples) | Excluir direto, permitido — desvincula todo mundo (confirmação escrita + aviso detalhado) |
| Ministério | tem `MinisterioMembro` | Excluir direto (confirmação simples) | Excluir direto, permitido — desvincula todo mundo (confirmação escrita + aviso detalhado) |
| Categoria Financeira | tem `MovimentacaoFinanceira` | Excluir direto | Arquivar → lista → bloqueado até esvaziar |
| Evento | tem `InscricaoEvento` | Excluir direto | Arquivar → lista → bloqueado até esvaziar |
| Pessoa | tem `Usuario`, `InscricaoEvento`, `MovimentacaoFinanceira`, é `Evento.responsavel_pessoa_id`, `CelulaMembro` ou `MinisterioMembro` | Excluir direto | Arquivar → lista → **anonimizar permitido** |
| Usuário | é `criado_por`/`atualizado_por` em qualquer tabela de domínio | Excluir direto | Arquivar → lista → **anonimizar permitido** |

Duas categorias de vínculo, tratadas diferente:

- **Vínculo de associação/pertencimento** (Célula↔`CelulaMembro`, Ministério↔`MinisterioMembro`):
  é só "essa pessoa está nesse grupo" — não é dado pessoal de terceiro nem histórico
  financeiro. Apagar o grupo não apaga a pessoa, só o vínculo. Por isso **não bloqueia**:
  excluir de vez sempre é permitido, com confirmação simples quando vazio e confirmação
  escrita + aviso detalhado ("isso desvincula N pessoas, mas elas continuam existindo")
  quando tem gente.
- **Vínculo de histórico de terceiro** (Evento↔`InscricaoEvento`, Categoria↔`MovimentacaoFinanceira`):
  apagar destruiria o histórico de inscrição/movimentação de outra pessoa, sem
  nenhum motivo de LGPD. Por isso ficam bloqueadas, com aviso do que está vinculado —
  igual Célula fazia antes desta revisão.

Por que só Pessoa/Usuário anonimizam: são os únicos que *são* dado pessoal — daí o
direito de eliminação da LGPD se aplicar de verdade a eles, mesmo com histórico
vinculado (anonimiza em vez de bloquear ou desvincular).

## Fluxo no front

**Padrão de abas**: cada módulo listado ganha um `layout.tsx` com abas de rota
(mesmo padrão que `financeiro/layout.tsx` já usa: `Movimentações | Categorias |
Relatórios`), adicionando uma aba **Arquivados** — não é uma rota nova solta, é
`/{modulo}/arquivados` como rota-irmã da listagem atual, com o mesmo layout compartilhado.

**Listagem normal**: cada linha/card ganha `temVinculo` no DTO de resposta (calculado
no back, mesma checagem usada pro bloqueio). O botão de exclusão mostra:
- `temVinculo = false` → rótulo **"Excluir"**, chama `DELETE /{modulo}/{id}/definitivo`
  direto (sem passar por arquivar).
- `temVinculo = true` → rótulo **"Arquivar"** (comportamento de hoje, inalterado),
  chama `DELETE /{modulo}/{id}`.

Os dois casos pedem confirmação crítica (`ModalConfirmacaoCritica`, já existente),
mas o texto muda: "excluir" avisa que é irreversível; "arquivar" avisa que pode
restaurar depois.

**Tela de Arquivados**: lista os soft-deleted do módulo, com duas ações por linha:
- **Restaurar** — sempre disponível, desfaz o soft delete.
- **Excluir definitivamente** — comportamento depende do vínculo **reavaliado na hora**
  (pode ter mudado desde que foi arquivado):
  - Evento/Categoria com vínculo → botão desabilitado, com tooltip/aviso explicando o
    que está vinculado (ex.: "3 inscrições vinculadas").
  - Célula/Ministério → botão sempre habilitado. Sem vínculo, confirmação simples
    (`ModalConfirmacao`). Com vínculo, confirmação escrita (`ModalConfirmacaoCritica`,
    "digite o nome") com aviso detalhado de quantas pessoas serão desvinculadas — mas
    a exclusão é permitida, porque desvincular não é o mesmo que apagar histórico de
    terceiro.
  - Pessoa/Usuário com vínculo → botão habilitado, confirmação escrita explica a
    consequência real: "isso vai remover o nome, e-mail e telefone desta pessoa; o
    histórico financeiro e de eventos permanece, sem identificação" — e só quem tem
    permissão de gerenciar aquele módulo (mesma checagem de hoje) consegue confirmar.

## Endpoints (padrão único, replicado nos 6 módulos)

- `GET /{modulo}/arquivados` — paginado, mesmo formato de listagem que já existe.
- `POST /{modulo}/{id}/restaurar` — `UPDATE ... SET deleted_at = NULL` (query nativa,
  igual o padrão de `hardDeleteById` que Célula já usa — `@SQLRestriction` esconde o
  registro de qualquer find derivado/JPQL, então restaurar exige SQL nativo).
- `DELETE /{modulo}/{id}/definitivo` — já existe em Célula, passa a existir nos 6.
  - Evento/Ministério/Categoria: bloqueia (`ConflitoNegocioException`) se `temVinculo`.
  - Pessoa/Usuário: se `temVinculo`, anonimiza em vez de deletar a linha; se não, deleta
    de verdade.
  - Reusado tanto pelo botão "Excluir" da listagem normal (registro nunca foi arquivado)
    quanto pelo botão da tela de Arquivados (registro já arquivado) — o service não
    precisa saber de onde veio a chamada, só faz a checagem de vínculo na hora.

## Anonimização (Pessoa/Usuário)

**Pessoa**: `nome` → `"Pessoa removida"`, `email`/`telefone`/`observacoes`/
`data_nascimento`/`data_batismo` → `null`, as 7 colunas de endereço → `null`, foto
desvinculada e removida (reusa `FotoService.remover`). `vinculo` (MEMBRO/CONGREGANTE)
fica como está — não é dado pessoal, é só a categoria do registro no sistema.

**Usuário**: `senha_hash`/`google_sub` → `null`, `ativo` → `false`. Não guarda e-mail
nem nome próprios (vêm da Pessoa via join), então já saem anonimizados junto.

**Busca (Elasticsearch)**: anonimizar Pessoa/Usuário registra `PESSOA ATUALIZADO` /
`USUARIO ATUALIZADO` no outbox — senão o nome antigo continua aparecendo na busca
global até um reindex manual (mesma classe de bug que já corrigi essa semana em
outros fluxos). Exclusão definitiva real (sem vínculo, qualquer módulo) registra
`REMOVIDO`.

## Log de eliminação LGPD

Nova tabela `eliminacao_lgpd`, só pra Pessoa/Usuário (é onde "eliminação" tem sentido
de verdade):

```sql
CREATE TABLE eliminacao_lgpd (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id uuid NOT NULL REFERENCES igreja(id),
    tipo_entidade varchar(20) NOT NULL CHECK (tipo_entidade IN ('PESSOA', 'USUARIO')),
    entidade_id uuid NOT NULL,
    executado_por_usuario_id uuid NOT NULL REFERENCES usuario(id),
    tinha_vinculo boolean NOT NULL,
    executado_em timestamp NOT NULL DEFAULT now()
);
```

Só registro de auditoria — não tem tela própria nesta rodada (dá pra consultar via
banco se algum dia for cobrado). Serve de prova de que o pedido foi atendido, e quando.

## Armadilha conhecida

`Usuario.deleteAt` mapeia pra coluna `delete_at` (sem "d", inconsistente com o resto
do schema que usa `deleted_at`). Não é bug pra corrigir agora — só prestar atenção
na hora de escrever o `restaurar` de Usuário pra não errar o nome da coluna.

## Fora de escopo (anotado, não esquecer)

- **Termos de Uso + Política de Privacidade** — item separado da Fase 3, ainda
  pendente; sem isso não tem base legal documentada pro tratamento de dado.
- **Direito de portabilidade** (exportar dados da pessoa em formato legível).
- **Prazo de resposta ao titular** (LGPD art. 19, 15 dias) — processo, não código.
- **Backup**: anonimizar no Postgres não apaga PII dos backups já feitos (retenção de
  90 dias) — expira sozinha, aceito pelo mesmo motivo que o resto do design de backup.
- **Tela própria pro log de eliminação** — só fica no banco por ora.

## Ordem de implementação sugerida (pedaços testáveis)

1. **Célula** primeiro — já tem o endpoint definitivo, só falta `arquivados` +
   `restaurar` + aba no front + botão dinâmico. Menor risco, prova o padrão.
2. **Ministério** e **Categoria Financeira** — réplica direta do padrão de Célula
   (sem anonimização), dois módulos parecidos, dá pra fazer juntos.
3. **Evento** — mesmo padrão de bloqueio, mas precisa conferir o cascade de
   `LocalEvento`/`responsavel_pessoa_id` não interferir na checagem de vínculo.
4. **Pessoa** — a peça de anonimização entra aqui, mais sensível, pedaço próprio.
5. **Usuário** — depende do padrão de Pessoa já validado; entra por último.

Cada pedaço: endpoint + teste + front + aviso pro autor testar, antes do próximo
(regra do projeto).
