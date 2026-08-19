# Termos de Uso + Política de Privacidade — design

> Fase 3 do roadmap: "Termos de Uso + Política de Privacidade". Obrigatório sob a LGPD
> antes de usuário real, e antes de vender pra fora.

## Contexto

Hoje ninguém aceita nada pra usar o Domus. Isso precisa mudar antes do piloto virar
uso real: a igreja (via quem cadastra) e cada pessoa com acesso precisam concordar com
Termos de Uso e Política de Privacidade — e esse aceite precisa ser **garantido pelo
backend**, não só por uma checkbox no front, porque dá pra criar conta direto pela API
sem passar pela tela.

## Decisões já tomadas (durante o brainstorm)

- **Aceite ligado ao `usuario` (login), não à `pessoa`.** Só quem acessa o sistema
  "usa" o produto de fato. Dados de pessoas cadastradas pela igreja (membros sem
  login) são responsabilidade da igreja como controladora — não geram aceite próprio.
- **Versionado, com reaceite quando a versão muda de forma relevante.** Prática comum
  no mercado (Slack, GitHub, Google) e favorecida pela LGPD quando a mudança altera
  finalidade de uso do dado. Mudança de texto sem alterar finalidade (ex.: correção de
  português) não precisa subir a versão.
- **Texto vive no código do front** (páginas estáticas, git-versionado, deploy normal),
  não num CMS/tabela editável. Sem necessidade real de editar sem deploy agora.
- **Guarda IP de quem aceitou**, além de usuário/versão/data — evidência jurídica
  adicional, custo baixo (o IP já passa pela requisição).
- **Contas que já existem hoje (antes desta feature) são tratadas igual a "versão
  desatualizada"** — sem caso especial: `usuario` sem registro de aceite da versão
  atual cai automaticamente no mesmo fluxo de reaceite bloqueante no próximo login.
- **Dois tipos de aceite separados** (`TERMOS_DE_USO` e `POLITICA_PRIVACIDADE`), não um
  aceite genérico — são documentos distintos e podem mudar de versão independentemente
  no futuro (a tabela já suporta isso; o modal desta versão mostra os dois juntos
  sempre que qualquer um estiver desatualizado — seletividade fica pra quando houver
  necessidade real).

## Modelo de dados

Nova migration adicionando a tabela `termo_aceite`:

```sql
CREATE TABLE termo_aceite (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo       VARCHAR(30) NOT NULL,
    versao     VARCHAR(20) NOT NULL,
    ip         VARCHAR(45),
    aceito_em  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_termo_aceite_tipo CHECK (tipo IN ('TERMOS_DE_USO', 'POLITICA_PRIVACIDADE'))
);

CREATE INDEX ix_termo_aceite_usuario ON termo_aceite (usuario_id, tipo);
```

Registro histórico/jurídico: **nunca se apaga nem se edita**, só se acumula (uma linha
nova a cada aceite, seja o primeiro ou um reaceite de versão). Sem soft delete — não
faz sentido "arquivar" prova de consentimento.

A versão atual de cada documento vive numa constante única, compartilhada entre back e
front pelo mesmo valor literal (ex.: `"1.0"`) — não numa tabela de configuração, YAGNI:
muda tão raramente que um valor hardcoded de cada lado, atualizado junto no mesmo PR
que muda o texto, é suficiente.

## Fluxo

### 1. Aceite no momento da criação de acesso

Três pontos de entrada exigem `aceitouTermos: boolean` no corpo da requisição, e o
backend **recusa a operação** (400) se vier `false`/ausente:

1. `POST /igrejas/registrar` (cadastro nativo da igreja)
2. `POST /auth/google/registrar` (cadastro via Google)
3. `POST /auth/reset-password` no modo convite (definir senha por convite)

Em todos os três, depois de criado/ativado o `usuario`, grava duas linhas em
`termo_aceite` (uma `TERMOS_DE_USO`, uma `POLITICA_PRIVACIDADE`) com a versão atual,
**na mesma transação** da criação/ativação da conta — nunca em passo separado que
possa falhar independente.

**Por que não um quarto ponto pra "entrar com Google pela primeira vez num convite":**
esse caminho usa o **mesmo endpoint** do login Google normal (`POST
/auth/google/login`), chamado toda vez que qualquer pessoa já provisionada loga — não
dá pra exigir `aceitouTermos` ali sem quebrar todo login recorrente. Não precisa:
quem entra assim ainda não tem nenhuma linha em `termo_aceite`, então cai sozinho no
modal bloqueante da seção seguinte (Fluxo 2) no primeiro `GET /auth/me` da sessão —
mesmo mecanismo genérico que cobre contas antigas, sem caso especial no login.

### 2. Reforço no login (pega contas antigas + futuras mudanças de versão)

`GET /auth/me` — já chamado toda vez que a sessão carrega — passa a conferir se
`usuario` tem aceite da versão **atual** de cada tipo (`TERMOS_DE_USO` e
`POLITICA_PRIVACIDADE`). Se faltar qualquer um dos dois (nunca aceitou, ou aceitou
versão antiga), a resposta ganha `precisaAceitarTermos: true`.

O front, ao ver isso, mostra um **modal bloqueante** — sem "X" pra fechar, sem clicar
fora, sem navegar pra outra tela — até a pessoa aceitar de novo. Um novo endpoint
`POST /termos/aceitar` grava as duas linhas novas (mesma versão atual) e libera o
modal. Contas criadas antes desta feature simplesmente nunca tiveram registro nenhum,
então caem nesse fluxo automaticamente no primeiro login pós-deploy — sem
migração de dados nem caso especial no código.

### 3. Transparência (perfil)

Em Configurações → perfil, uma linha mostrando "Termos aceitos em DD/MM/AAAA" —
lê o `aceito_em` mais recente do `usuario` logado. Só leitura, sem ação.

## Endpoints novos

- `POST /termos/aceitar` — grava o reaceite da versão atual (chamado pelo modal
  bloqueante). Corpo vazio; usa `igrejaId`/`usuarioId` do JWT, `versao` da constante
  atual do backend, `ip` do request.
- `GET /auth/me` (existente) ganha `precisaAceitarTermos: boolean`.
- `POST /igrejas/registrar`, `POST /auth/google/registrar`, `POST /auth/reset-password`
  (existentes) ganham o campo obrigatório `aceitouTermos` no corpo e passam a gravar
  `termo_aceite` na mesma transação de criação/ativação da conta.

## Conteúdo (o texto em si)

Duas páginas estáticas: `/termos-de-uso` e `/politica-de-privacidade`. Ponto de
partida: adaptar termos/políticas de produtos do mercado (Markdown/HTML direto no
código do front), cobrindo especificamente:

- **LGPD — controlador vs. operador.** A igreja é **controladora** dos dados de
  pessoas/membros que ela cadastra (decide o que coletar e por quê); o Domus é
  **operador** (processa em nome da igreja). Precisa estar explícito, não é padrão de
  produto B2C genérico.
- **Direito à eliminação.** Referencia as duas features já construídas como o
  mecanismo real de exercer esse direito: "excluir igreja" (carência de 10 dias, apaga
  tudo) e exclusão definitiva por módulo (pessoa, evento, etc.) — não é só uma
  promessa no papel, é um recurso existente no produto.
- **Subprocessadores** (obrigatório declarar quem mais toca no dado): Neon (banco),
  Cloudflare R2 (fotos), Resend (e-mail), Google (login OAuth), Elasticsearch
  self-hospedado.
- **Cookies.** Só os de sessão (`domus_access`/`domus_refresh`, httpOnly) — sem
  rastreamento de terceiro, analytics ou publicidade. Vale deixar isso explícito como
  diferencial, não só formalidade.

O texto jurídico completo é escrito na hora de implementar, não nesta spec — aqui só
os pontos obrigatórios que precisam entrar, listados acima.

## Frontend

- **Checkbox obrigatória** ("Li e concordo com os [Termos de Uso] e a [Política de
  Privacidade]", cada link abrindo a página correspondente em nova aba) nos três
  pontos de entrada da seção Fluxo → 1. Botão de submeter desabilitado até marcar —
  mesmo padrão de UX já usado noutros formulários do projeto.
- **Modal bloqueante de reaceite** (seção Fluxo → 2), reaproveitando a mesma
  checkbox/link, sem formulário em volta — só "Aceitar e continuar".
- **Linha em Configurações → perfil** (seção Fluxo → 3) mostrando a data do aceite
  mais recente.

## Casos de borda

- `aceitouTermos: false` ou ausente em qualquer um dos três endpoints de
  criação/ativação → 400, nada é criado/ativado.
- Conta criada antes desta feature, sem nenhum registro em `termo_aceite` → mesmo
  tratamento de "versão desatualizada", cai no modal bloqueante no próximo login.
- Pessoa sem `usuario` (nunca recebeu acesso) → nunca gera nem precisa de aceite; é
  dado da igreja como controladora, não do titular usando o produto.
- Igreja "filha" (vinculada a uma mãe) — cada `usuario`, de qualquer igreja da família,
  segue a mesma regra individual; não há aceite "herdado" da igreja mãe.

## Testes

- `TermoAceiteServiceTest` (Mockito puro): grava aceite corretamente nos três pontos
  de entrada; recusa criação/ativação quando `aceitouTermos` é falso/ausente;
  `precisaAceitarTermos` retorna `true` quando falta qualquer um dos dois tipos ou a
  versão está desatualizada, `false` quando os dois batem com a versão atual.
- Teste específico provando que conta antiga (sem nenhuma linha em `termo_aceite`) é
  tratada como "precisa aceitar" — não crasha, não assume aceite implícito.
- Teste de integração leve confirmando que o registro é gravado na mesma transação da
  criação de `usuario` (rollback de um lado desfaz o outro).

## Estratégia de implementação

Feature de porte pequeno/moderado — sem necessidade de subagentes revisando em fases
(diferente de "excluir igreja"). Ordem sugerida:

1. Schema (migration) + `TermoAceiteService` (gravar aceite, checar
   `precisaAceitarTermos`) + `POST /termos/aceitar`
2. Enforcement nos três endpoints de criação/ativação de conta
3. `GET /auth/me` ganha `precisaAceitarTermos`
4. Frontend: páginas de conteúdo, checkbox nos três formulários, modal bloqueante,
   linha em Configurações → perfil

## Fora do escopo desta versão

- CMS pra editar o texto sem deploy — decidido: fica no código, versionado no git.
- Reaceite seletivo por tipo (só Termos, ou só Política, separadamente) — a tabela já
  suporta por ter `tipo`, mas o modal desta versão sempre mostra os dois juntos quando
  qualquer um estiver desatualizado.
