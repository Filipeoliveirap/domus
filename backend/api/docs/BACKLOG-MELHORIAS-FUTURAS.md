# Backlog — Melhorias futuras (pós-lançamento comercial)

> Levantado em 2026-08-20, no mesmo brainstorm que gerou `BACKLOG-PRE-VENDA.md`. **Critério de
> entrada aqui:** dá pra vender e operar o Domus sem isso — vira valor real, mas não é bloqueio.
> Boa parte destes itens fica melhor desenhada depois de uso real com clientes pagantes de
> verdade (o mesmo princípio de "construir no escuro" que já guiava o roadmap original).

---

## Eventos

- **Programação do evento + equipe servindo (Spec E).** Linha do tempo do evento (19:00 café,
  19:30 louvor, 20:15 pregação) + lista de quem serve (não ocupa vaga, é equipe, não
  público). Confirmado como feature futura no brainstorm de 2026-08-20 — fica melhor desenhada
  vendo alguns eventos reais primeiro.
- **Capacidade do local impondo limite de vagas.** Hoje `LocalEvento.capacidade` só sugere;
  nada barra cadastrar vagas acima da capacidade. Ficou de fora da Spec B de propósito.
- **Lista de espera quando o evento esgota.** Não existe hoje — quando abrir vaga por
  cancelamento, ninguém é avisado automaticamente.
- **Exportar lista de inscritos (CSV/PDF).** Botão existia no protótipo, removido por falta de
  endpoint.
- **Builder visual de formulário personalizado** (arrastar-e-soltar, mais tipos de campo). A
  v1 do item 6 do backlog pré-venda é uma lista de campos com tipo fixo — evoluir pra editor
  livre só se o uso real pedir.
- **Endereço do encontro da célula variando semana a semana** — precisaria de histórico por
  encontro, não campo fixo. Fora de escopo de propósito, ver memória do projeto.

## Pagamento e financeiro

- **Contas a pagar executando o pagamento de verdade** (não só registrar/lembrar). Integra
  com o gateway escolhido no item 1 do backlog pré-venda pra emitir/pagar boleto ou PIX pro
  fornecedor a partir da própria plataforma. Envolve compliance, custo por transação, KYC —
  bem mais pesado que o ledger simples da v1, por isso adiado.
- **Filtros extras em movimentação financeira** (por atribuinte/pessoa) — já estava fora de
  escopo no `CLAUDE.md` original.
- **Múltiplos atribuintes numa mesma movimentação** — já resolvido como N-pra-N
  (`movimentacao_contribuinte`, V15); o que falta é só refinamento de filtro, não a
  funcionalidade base.

## Comunidade / engajamento (tela de início)

- **Qualquer pessoa poder postar no mural**, não só ADMIN/LÍDER — decisão de v1 foi restringir
  pra evitar carga de moderação logo de cara.
- **Notificação de nova postagem** (push ou e-mail, pra quem não abriu o Domus) — a central de
  notificações da v1 (`BACKLOG-PRE-VENDA.md`, item 4) já avisa curtida/comentário in-app;
  isso aqui é avisar QUEM AINDA NÃO ABRIU o app que saiu uma postagem nova, canal diferente.
- **Denúncia de conteúdo impróprio.**
- **Postagem buscável na busca global** — decidir se faz sentido, hoje nem entra no
  Elasticsearch.

## Notificações

- **Push notification** — depende de PWA/app (ver seção abaixo). Central de notificações da
  v1 é só in-app (sino na `TopBar`).
- **Preferências de notificação por usuário** — silenciar um tipo específico (ex.: não avisar
  de curtida, só de comentário). v1 é tudo ligado, sem configuração.

## Self-service / multi-tenant

- **App mobile / PWA.** Hoje é só web responsivo. Concorrentes de gestão de igreja costumam
  ter app nativo — avaliar demanda real antes de construir (custo alto, dois codebases ou
  React Native/Capacitor por cima do que já existe).
- **Suporte in-app** (chat, FAQ, central de ajuda) — hoje não existe canal dentro do produto.
- **Exportação de dados pelo próprio cliente** ("baixe todos os meus dados") — LGPD já cobre
  isso pelo direito de eliminação (exclusão definitiva por módulo, feito na Fase 3), mas
  portabilidade de dados (exportar sem excluir) é capacidade diferente, ainda não construída.
- **Importação de outros módulos além de pessoa** (eventos, histórico financeiro) — a v1 do
  item 10 do backlog pré-venda cobre só pessoa, que é o que toda igreja migrando pede primeiro.
- **Landing page / site de marketing fora do app** (pricing, comparação com concorrentes,
  SEO) — hoje o "site" é o próprio app; separar um site institucional é decisão de marketing,
  não força engenharia por si só.

## Dívida técnica de baixa prioridade (herdada do backlog antigo, ainda válida)

Estes já estavam aceitos como dívida consciente antes deste brainstorm — continuam válidos,
só reorganizados aqui por completude:

- **Rate limiting: janela fixa, só por IP.** Sem sliding window/token bucket, sem limite por
  usuário autenticado, sem limite por rota individual. Reavaliar se abrir pra múltiplas
  igrejas externas mudar o perfil de tráfego/abuso o suficiente pra justificar.
- **WebP não aceito no upload de foto.** `ImageIO` do Java 21 não lê sem dependência extra.
- **Backup: janela de perda de 24h, restore manual.** Aceito — ver
  `BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md` pro raciocínio completo.
