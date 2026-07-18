# Convite de acesso por e-mail — Design

**Data:** 2026-07-18
**Fase:** 2 (funcionalidades de valor)

## Objetivo

Substituir o fluxo atual de "conceder acesso" (onde o **admin define a senha** do membro) por
um fluxo de **convite por e-mail**: o admin só escolhe a **role** e convida; o **próprio
usuário** define a senha depois (reusando o mecanismo de reset de senha da Fase 1), ou entra
direto com **Google** (o e-mail é a chave).

## Decisões (brainstorming 2026-07-18)

1. **Status "Convite pendente"** na lista de usuários + botão **"Reenviar convite"** para quem
   não aceitou. Selo/texto: *"Ainda não fez login no sistema"*.
2. **Validade do link do convite: 7 dias.**
3. **Reativação de acesso arquivado** também vira convite (nenhum caminho pede senha do admin).
4. Convite deixa de ser "pendente" no **primeiro login por qualquer método** (senha nativa OU
   Google) — não basta definir a senha; conta como aceito quem **fez login**.
5. **Abordagem de token:** reaproveitar o mecanismo do reset (Redis `pwreset:<token>` →
   `usuario_id`) com TTL parametrizável (7 dias no convite). O `redefinir()` serve os dois.
6. **Google na tela de aceite:** a tela de definir senha do convite mostra os dois caminhos
   (definir senha OU "Entrar com Google").
7. **Pré-checagens no conceder acesso:** já tem acesso → mensagem; sem e-mail → modal para
   cadastrar e-mail + role e seguir o fluxo.

## Backend

### Migration V12
- `ALTER TABLE usuario ADD COLUMN convite_pendente BOOLEAN NOT NULL DEFAULT false;`
- Só o fluxo de convite marca `true`. Usuários existentes ficam `false`.

### Token (refatorar `PasswordResetService`)
- Extrair a geração/armazenamento do token para aceitar um **TTL** (hoje fixo `Duration.ofMinutes(30)`).
- Convite gera token no **mesmo prefixo** `pwreset:<token>` com TTL de **7 dias**, apontando
  para `usuario_id`. O `redefinir(token, novaSenha)` funciona sem mudança (seta senha + revoga sessões).
- Método reutilizável, ex.: `gerarLinkDefinicaoSenha(Usuario, Duration ttl): String` que
  retorna o token; o corpo do e-mail (reset vs convite) fica com quem chama.

### `UsuarioService`
- `concederAcesso(ConcederAcessoRequestDTO, igrejaId)`:
  - DTO passa a ser `{ membroId, role, email? }` (sai `senha`).
  - Se membro já tem acesso ativo → `MEMBRO_JA_TEM_ACESSO`. Se tem arquivado → `MEMBRO_TEM_USUARIO_ARQUIVADO`.
  - Se `membro.email` vazio **e** `email` não enviado → `MEMBRO_SEM_EMAIL` (front abre modal de e-mail).
  - Se `membro.email` vazio **e** `email` enviado → grava `membro.email` (validando o único de
    `membro.email`, inclusive arquivados) e segue.
  - Cria `usuario` com `senhaHash = null`, `ativo = true`, `convite_pendente = true`, role.
  - Gera token (7d) e envia **e-mail de convite** (link `/reset-password?token=X&convite=1`).
- `reativarAcesso(ConcederAcessoRequestDTO, igrejaId)`: religa a conta arquivada (`deleteAt=null`,
  `ativo=true`, role), **mantém** o `senhaHash` antigo, `convite_pendente = true`, envia convite.
- `reenviarConvite(usuarioId, igrejaId)`: se `convite_pendente == false` → `CONVITE_JA_ACEITO`;
  senão regenera token (7d) e reenvia o e-mail.
- `UsuarioResponseDTO` expõe `convitePendente`.

### Marcar aceite (primeiro login)
- No sucesso do login **nativo** e do login **Google** (`AuthenticationService`/`GoogleAuthService`),
  se `usuario.convite_pendente == true`, setar `false` e salvar. Idempotente.

### E-mail de convite (`InviteEmail`/no `UsuarioService`)
- Assunto: *"Você foi convidado para o Domus"*.
- Corpo: nome do convidado + nome da igreja, botão "Definir minha senha" (link com `convite=1`),
  e uma linha "ou entre direto com sua conta Google". Menciona validade de 7 dias.
- Usa o `EmailService` existente. Falha de envio é logada, não vira 500 (padrão do reset).

### Endpoints
- `POST /usuarios/conceder-acesso` → `{ membroId, role, email? }`.
- `POST /usuarios/reativar-acesso` → `{ membroId, role, email? }`.
- `POST /usuarios/{id}/reenviar-convite` → sem body.
- `POST /auth/reset-password` (existente) → define a senha do convite, sem mudança.

## Frontend

### ModalConcederAcesso (e reativar)
- Remove os campos de senha/confirmar. Fica: **seleção de role** + botão **"Enviar convite"**.
- Se `membro.email` for nulo/vazio → mostra campo de **e-mail obrigatório** no próprio modal
  (o back grava no membro). Se tiver e-mail, só role.
- Erro `MEMBRO_JA_TEM_ACESSO` → mensagem clara "Esta pessoa já tem acesso ao sistema".

### Lista de usuários
- Selo **"Convite pendente"** (texto *"Ainda não fez login no sistema"*) quando `convitePendente`.
- Ação **"Reenviar convite"** no menu, visível para usuários pendentes.

### Tela de aceite (`/reset-password?token=X&convite=1`)
- Quando `convite=1`: título/copy de **"Defina sua senha de acesso"** e, além do form de senha,
  o botão **"Entrar com Google"** (reusa o fluxo de login Google existente, que vincula por e-mail).
- Sem `convite=1`: comportamento atual de redefinição.

## Erros e bordas
- `MEMBRO_SEM_EMAIL`, `MEMBRO_JA_TEM_ACESSO`, `MEMBRO_TEM_USUARIO_ARQUIVADO`, `EMAIL_DUPLICADO`
  (ao gravar e-mail já usado), `CONVITE_JA_ACEITO`, `TOKEN_INVALIDO` (link expirado/usado).
- Reenviar convite em usuário não-pendente → `CONVITE_JA_ACEITO`.
- Link expirado → a pessoa pode usar "esqueci minha senha" (conta existe) ou o admin reenvia.

## Testes (backend, Mockito)
- `concederAcesso`: cria usuário com senha null + `convite_pendente=true` + envia e-mail; grava
  e-mail no membro quando fornecido; erros (já tem acesso, sem e-mail sem email, e-mail duplicado).
- `reativarAcesso`: religa + convite pendente.
- `reenviarConvite`: guarda `CONVITE_JA_ACEITO`; reenvia quando pendente.
- Primeiro login (nativo e Google) zera `convite_pendente`.
- Front sem runner: verificação manual no navegador (modal, selo, reenviar, tela de aceite com Google).
