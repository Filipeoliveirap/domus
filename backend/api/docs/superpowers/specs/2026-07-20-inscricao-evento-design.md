# Inscrição em evento — design

**Data:** 2026-07-20
**Fase do roadmap:** 2
**Escopo:** Spec A de quatro (ver `BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md` para B, C e D)

## Problema

O Domus registra eventos, mas não registra quem vai. A igreja controla presença em papel, e
eventos com limite de vagas (retiro, ceia, passeio) não têm como parar de aceitar gente.

O front já está preparado: `ModalEventoResumo` tem o botão "Confirmar presença (em breve)"
desabilitado e o tipo `ParticipanteResumo` declarado esperando esta entrega.

## Decisões e o porquê

### Quem se inscreve: os dois caminhos

**Nem todo membro tem usuário** — é decisão fixa do projeto. Se a inscrição só funcionasse para
quem tem login, a maior parte da igreja ficaria de fora e a lista do sistema não seria a lista
real do evento. A secretária voltaria ao caderno, e o Domus viraria trabalho dobrado.

Então: quem tem login se inscreve sozinho, **e** qualquer membro com login pode inscrever outros
membros pela lista. Mesma tabela, mesma regra de vagas; muda só quem apertou o botão, registrado
em `inscrito_por_usuario_id`.

Efeito colateral desejável: isso dá ao membro comum o **primeiro benefício concreto** de ter uma
conta no Domus — hoje não existe nenhum.

### Membro inscrito é dono da própria inscrição

Um membro inscrito por outra pessoa tem inscrição de primeira classe: aparece na lista de eventos
dele e ele pode cancelá-la. Não fica aninhado sob quem o inscreveu.

Foi uma correção durante o brainstorm: aninhar transformaria a pessoa em apêndice de outra, sem
controle sobre a própria presença.

### Acompanhante é só para quem não é da igreja

Pessoa de fora não tem cadastro, então entra pendurada na inscrição de quem a trouxe — com nome e
telefone. O propósito do vínculo é de leitura, não de hierarquia de dados:

> ver na lista alguém que ninguém conhece e saber de onde essa pessoa veio.

### Vagas contam pessoas, não inscrições

Um responsável + dois convidados = três vagas. Se contasse inscrições, o limite não limitaria.

### Cancelamento: você controla você e o que você trouxe

Podem cancelar: o próprio inscrito (se tiver login), ADMIN, LÍDER, e o responsável **apenas sobre
os convidados de fora que ele mesmo cadastrou**.

**Quem inscreveu não pode desinscrever.** Inscrever alguém ocupa uma vaga; desinscrever tira a
pessoa de um evento que ela achava que ia — e ela descobre no dia. É assimétrico em dano, e por
isso é assimétrico em permissão.

### Preço é informativo

A escolha de provedor de pagamento é estudo da Fase 6. O Domus registra a inscrição, não o
dinheiro: mostra o valor com aviso de que o pagamento é combinado com a igreja (PIX ou comprovante
informado na descrição do evento).

Deixar claro na tela é o ponto. Um valor exibido sem aviso faz a pessoa achar que já pagou.

### `batizado` entra agora; `ATIVO` não servia

`membro.status` é `ATIVO | INATIVO | VISITANTE`, e **`ATIVO` não significa batizado** — a criança
de 8 anos é `ATIVO` e não é batizada; quem se mudou é batizado e está `INATIVO`. Usar `status`
como se fosse batismo faria o filtro mentir em silêncio.

Entram `batizado BOOLEAN NOT NULL DEFAULT FALSE` e `data_batismo DATE NULL` (a data é opcional
porque a secretaria nem sempre a tem).

Como todos nascem `false`, o toggle "só batizados" exibe **sempre** um aviso fixo abaixo dele:

> Membros que não estiverem marcados como batizados não poderão se inscrever nem ser inscritos.

É declaração da regra, não detecção de estado vazio: sem consulta extra, funciona igual para
igreja com 0 ou 300 batizados, e não vira alarme que o usuário aprende a ignorar.

## Modelo de dados

```
inscricao_evento
  id                       UUID PK
  igreja_id                UUID FK NOT NULL   -- isolamento multi-tenant
  evento_id                UUID FK NOT NULL
  membro_id                UUID FK NOT NULL
  inscrito_por_usuario_id  UUID FK NULL       -- NULL = auto-inscrição
  status                   VARCHAR            -- CONFIRMADA | CANCELADA
  created_at / updated_at
  UNIQUE (evento_id, membro_id)               -- evita ocupar duas vagas

acompanhante_inscricao
  id             UUID PK
  inscricao_id   UUID FK NOT NULL
  nome           VARCHAR NOT NULL
  telefone       VARCHAR NULL
  created_at

evento  (+ colunas)
  vagas                 INTEGER NULL   -- NULL = sem limite
  preco                 NUMERIC NULL   -- NULL = gratuito
  exclusivo_membros     BOOLEAN NOT NULL DEFAULT FALSE
  exclusivo_batizados   BOOLEAN NOT NULL DEFAULT FALSE

membro  (+ colunas)
  batizado       BOOLEAN NOT NULL DEFAULT FALSE
  data_batismo   DATE NULL
```

### Cancelar e voltar atrás

Cancelamento é **mudança de status**, não exclusão: a linha vira `CANCELADA` e preserva o
histórico de quem inscreveu quem.

Isso obriga duas regras, senão o `UNIQUE (evento_id, membro_id)` vira armadilha — quem cancelasse
ficaria impedido de voltar ao próprio evento:

1. **Reinscrição reaproveita a linha existente**, voltando o status para `CONFIRMADA` (e não
   tenta inserir uma nova, que violaria o `UNIQUE`).
2. **Só `CONFIRMADA` ocupa vaga.** A contagem sob lock ignora canceladas.

Os acompanhantes de uma inscrição cancelada são **apagados**, não apenas descontados. Decidido em
2026-07-21, depois de o teste ao vivo mostrar o contrário: eles voltavam sozinhos na reinscrição.
Quem cancelou porque o convidado desistiu o veria reaparecer em silêncio, ocupando vaga. Levar a
pessoa de novo passa a exigir cadastrá-la de novo — o passo consciente que o silêncio não tinha.

`igreja_id` em `inscricao_evento` é redundante com `evento.igreja_id`, mas segue o padrão de toda
entidade de domínio do projeto e evita JOIN em toda checagem de isolamento.

## Concorrência: o ponto crítico

Duas inscrições simultâneas na última vaga **não podem** ambas passar. Contar e depois inserir sem
trava falha sob READ COMMITTED — cada transação lê o estado antigo da outra e as duas aprovam.

É a mesma classe de erro do vínculo de igrejas (V14), e a lição já está documentada.

Solução: `SELECT ... FOR UPDATE` na linha do **evento** antes de contar as vagas ocupadas. A
serialização é por evento, então inscrições em eventos diferentes não se bloqueiam.

Teste obrigatório: duas threads disputando a última vaga; exatamente uma vence.

## Fluxo

```
POST /eventos/{id}/inscricoes           auto-inscrição
POST /eventos/{id}/inscricoes/membros   inscrever outros (lista de membro_id)
POST /eventos/{id}/inscricoes/{ins}/acompanhantes
DELETE /inscricoes/{id}                 cancelar
DELETE /acompanhantes/{id}              remover convidado
GET  /eventos/{id}/inscricoes           lista — ADMIN e LÍDER só
```

Validações no serviço, em ordem: evento existe e é da igreja → não passou → `exclusivo_membros` →
`exclusivo_batizados` → já inscrito → vagas (sob lock).

## Telas

- **Botão "Confirmar presença"** funcional onde o evento aparece (modal do início, drawer de
  detalhe, card). Estado "Inscrito" com opção de cancelar.
- **Modal de inscrever membros:** busca por nome, seleção múltipla, salvar.
- **Botão "vou levar alguém de fora":** só quando o evento não é exclusivo de membros. Formulário
  de nome (+ telefone opcional).
- **Lista de inscritos (ADMIN/LÍDER):** inscritos e convidados, mostrando quem inscreveu quem e o
  responsável por cada convidado, com opção de cancelar.
- **Cadastro de evento:** campos de vagas, preço e os dois toggles, com o aviso do batismo.
- **Cadastro de membro:** `batizado` + `data_batismo`.

Responsividade é parte da entrega, não etapa separada — a lista de inscritos vira cards no mobile,
como as demais tabelas do sistema.

## Fora de escopo

Cobrança real (Fase 6), recorrência (Spec C), campos personalizados (Spec D), lista de espera,
elegibilidade por idade/estado civil e equipe servindo (backlog).

## Testes

- Concorrência na última vaga (duas threads, uma vence)
- Vagas contam acompanhantes
- Isolamento: inscrição em evento de outra igreja é 404
- `exclusivo_batizados` bloqueia membro não batizado
- Quem inscreveu não consegue desinscrever
- Responsável remove só o próprio convidado
- Lista de inscritos negada para MEMBRO
- `UNIQUE` impede inscrição duplicada
- Cancelar e reinscrever funciona (reaproveita a linha, não estoura o `UNIQUE`)
- Inscrição cancelada não ocupa vaga, nem seus acompanhantes
