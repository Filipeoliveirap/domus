# Meu Perfil (+ foto na tabela de usuários) — design

**Data:** 2026-07-23
**Fase do roadmap:** 3 (gestão de conta), com um pedaço avulso da Fase 2 (foto na tabela)

## Problema

Duas coisas pequenas, mesmo contexto:

1. A tabela de usuários (`usuarios/page.tsx`) mostra iniciais mesmo quando a pessoa tem foto —
   porque nunca puxou o `fotoId` nem tem componente de avatar reutilizável.
2. A Sidebar já tem um link para `/perfil` (rodapé, embaixo do logout) — mas a rota não existe.
   Todo usuário logado precisa de um lugar para ver seus dados e trocar foto/senha, sem precisar
   passar por um admin para tudo.

## Decisões

### Dado mostrado: tudo que existe em `pessoa`

A tela de Meu Perfil não mostra só nome/email/foto — mostra os mesmos campos que a tela de
cadastro de Pessoa tem (telefone, endereço estruturado, data de nascimento, vínculo, ministério,
estado civil, sexo). Regra de edição por capacidade, não por identidade de perfil:

| Quem | O que pode mudar |
|---|---|
| `ACESSO_COMUM`, `LIDER` | só `foto_id` (+ senha, que é da conta, não da pessoa) |
| `ADMIN_IGREJA` | tudo, **exceto `email`** |

`email` é sempre somente-leitura, para todo mundo — é a chave de login (nativo e Google) e
trocar sem revalidação de posse quebraria o vínculo de conta Google e a unicidade. Trocar e-mail
fica fora de escopo (ver BACKLOG).

Para quem só pode mudar a foto, a tela mostra um aviso fixo: *"Seus dados só podem ser
alterados pela secretaria da igreja, caso estejam incorretos ou desatualizados."*

A checagem de capacidade vive no `PessoaService` (backend), nunca só no front — campo
desabilitado no React não impede um PUT direto na API.

### Endpoint: `GET/PUT /pessoas/me`, não `/usuarios/me`

Os dados exibidos são principalmente de `Pessoa`, não de `Usuario` (que só tem role/senha/status).
Um endpoint self-service em `PessoaController` resolve `pessoa_id` a partir do usuário autenticado
(igual todo endpoint do sistema, nunca do corpo da requisição) e reaproveita o `PessoaService`/DTOs
que já existem, só acrescentando a resolução "usuário logado → pessoa" e a checagem de capacidade
por dentro do update. O `role`/cargo exibido na tela vem à parte, de `usuario` (via sessão já
carregada no front, não precisa ir de novo no back).

### Troca de senha: `PUT /auth/change-password`, padrão mercado

Fica em `AuthenticationController`/`AuthService`, junto do resto do que mexe em credencial
(login, forgot-password, reset-password) — não em `UsuarioController`.

- Recebe `senhaAtual` + `novaSenha`; valida `senhaAtual` com bcrypt contra `usuario.senha_hash`.
- Conta só-Google (`senha_hash == null`) → erro `CONTA_SEM_SENHA`, reaproveitando o código de erro
  que já existe no login nativo (mesma mensagem: "Esta conta usa login com Google").
- Sucesso → atualiza o hash e revoga todos os refresh tokens do usuário **exceto a sessão atual**
  (reaproveita a lógica de família de refresh token da Fase 1; mesmo espírito do reset por
  e-mail, mas sem derrubar quem acabou de trocar a própria senha).

### Componente `<Avatar>` compartilhado

Hoje a lógica "foto ou fallback" está duplicada (Sidebar usa ícone `User`, tabela de usuários e
`UploadFoto` usam iniciais via `iniciais()`). Esta entrega extrai
`frontend/src/components/common/Avatar/Avatar.tsx`: props `{ fotoId, nome, tamanho }`; usa
`urlFoto(fotoId, 'THUMB')` quando existe, senão iniciais. Usado na tabela de usuários e no
preview de Meu Perfil. Migrar a Sidebar para ele é bônus, não obrigatório nesta entrega.

## Frontend

- `frontend/src/app/(app)/perfil/page.tsx`: novo hook `useMinhaPessoa()` (GET `/pessoas/me`),
  formulário com React Hook Form + Zod, mesmo padrão de `PessoaForm`.
- Foto: `<UploadFoto formato="circulo">` já existente, sem mudança nele.
- Bloco de senha é seção separada (senha atual, nova, confirmar), sempre visível — não depende
  do perfil, é dado da conta. Esconde/mostra erro `CONTA_SEM_SENHA` quando aplicável.
- Responsivo: formulário colapsa para 1 coluna no mobile, seguindo o padrão já usado em
  `PessoaForm`/`EventoForm`.
- Feedback via `notificar()` (convenção do projeto), nunca toast solto nem `window.confirm`.

## Backend

- `PessoaController`: `GET /pessoas/me`, `PUT /pessoas/me`. Resolve `pessoa_id` via
  `usuario.pessoa_id` do usuário autenticado (nunca do corpo).
- `PessoaService`: novo método de update "self" que aplica a whitelist de campos por capacidade
  (`ACESSO_COMUM`/`LIDER` só `foto_id`; `ADMIN_IGREJA` tudo exceto `email`) — ignora
  silenciosamente campos fora da whitelist enviados no payload (não é erro do usuário, o front já
  desabilita; é defesa em profundidade).
- `AuthenticationController`: `PUT /auth/change-password`. `AuthService` valida senha atual,
  atualiza hash, revoga refresh tokens exceto o da sessão atual.
- `UsuarioController` (listagem): DTO de listagem de usuários passa a incluir `fotoId` (da pessoa
  vinculada), para a tabela poder usar `<Avatar>`.

## Testes

- Backend: `PessoaService` — `ACESSO_COMUM` tentando mudar telefone é ignorado; `ADMIN_IGREJA`
  tentando mudar email é ignorado; isolamento por `igreja_id` continua valendo (não dá pra editar
  pessoa de outra igreja via `/me`, mas isso já é garantido por resolver `pessoa_id` da sessão).
  `AuthService.changePassword` — senha atual errada rejeita; conta só-Google rejeita com
  `CONTA_SEM_SENHA`; sucesso revoga outras sessões e mantém a atual.
- Frontend: validar que campos aparecem desabilitados corretamente por perfil (teste manual no
  navegador, como de costume no projeto — sem suíte de teste de front hoje).

## Fora de escopo (backlog)

- Trocar o próprio e-mail (precisaria de revalidação de posse).
- Migrar a Sidebar para usar `<Avatar>` (fica como possível follow-up, não bloqueia esta entrega).
