# Auditoria — módulo de eventos + pagamento de eventos

> Revisão somente-leitura (2026-09-10), pedida antes de fechar o módulo e seguir pro próximo
> item do escopo comercial. Nada foi corrigido: este documento só levanta e ordena.
>
> Escopo lido: `modules/evento/**`, `modules/pagamento/**`,
> `financeiro/movimentacao/MovimentacaoAutomaticaService`, e o lado do front em
> `components/module/eventos/` + rotas de checkout. Cruzado com os specs de
> `docs/superpowers/specs/` (inscrição, convite público, cobrança de evento pago, fluxo de
> pagamento, prazo de inscrição, meio de pagamento/taxa) e com o
> `BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`.
>
> **Contagem:** 4 Críticos · 14 Importantes · 8 Menores · 11 Ideias de produto.

---

## Sumário executivo — o que importa mais, em ordem

1. **[C1] Cancelamento de inscrição com complemento pendente não devolve o que já foi pago.**
   `CobrancaEventoExpiracaoJob` expira a cobrança de complemento e cancela a inscrição
   direto, sem passar por nenhum caminho de estorno — quem já tinha pago o valor original
   perde a vaga *e* o dinheiro. É o achado com maior potencial de dano real para a igreja
   piloto.

2. **[C2] A confirmação de pagamento não é transacional nem serializada.** Webhook e poll
   ativo correm de propósito em paralelo, mas a "guarda de idempotência" é um
   read-then-write sem lock nem versão — dá para os dois passarem juntos e gerarem duas
   ENTRADAS no financeiro e dois e-mails de comprovante para o mesmo pagamento.

3. **[C3] `POST /cobrancas/{id}/reiniciar` pode órfãozar um pagamento aprovado.** O cancelamento
   no Mercado Pago é *best-effort*, mas o `mpPaymentId` é limpo de qualquer jeito. Se o
   pagamento tiver sido aprovado na janela entre o poll e o clique, o Domus perde a referência
   e a pessoa pode pagar de novo.

4. **[C4] Quem está com pagamento pendente depois do prazo fica sem caminho para pagar.**
   O front barra a tela inteira (`BotaoConfirmarPresenca:234`) porque `minha.inscrito` só é
   `true` para CONFIRMADA — o bloco "Pagamento pendente — continuar" desaparece. Pior: o
   `PrazoInscricaoProcessador` manda uma notificação dizendo exatamente "falta pagar pra
   garantir sua vaga", e a tela não deixa.

5. **[I1] `POST /cobrancas/{id}/pagar` não olha o evento.** Nem prazo de inscrição, nem
   situação. Um link de 48h gerado na véspera continua cobrando depois de o evento ter
   começado — ou terminado.

6. **[I2] Depois do V40 (gross-up), os e-mails ainda anunciam o valor errado.** Comprovante,
   lembrete e aviso de "evento virou pago" usam `cobranca.getValor()` (o alvo líquido), mas o
   pagador foi debitado em `valorCobrado` (bruto). O comprovante de pagamento diz um número
   que não bate com a fatura.

7. **[I3] Duas contagens de vaga diferentes convivem no mesmo evento pago.** A reserva conta
   cobranças (`contarPessoasComVagaReservada`); tudo que a pessoa **vê** conta inscrições
   confirmadas (`contarPessoasConfirmadas`). A tela oferece uma vaga que o backend recusa.

8. **[I4] Estorno em lote pode devolver dinheiro no Mercado Pago sem registrar nada.** O
   padrão fail-fast de duas passadas protege contra mutação suja, mas não contra a chamada
   externa que já teve sucesso na primeira passada quando uma seguinte falha.

9. **[I5] Editar "toda a série" propaga o preço sem propagar dinheiro.** As outras ocorrências
   ficam com `preco` novo e as cobranças velhas intocadas. `inscricoesAte` sequer é propagado.

10. **[I6] Toda falha do Mercado Pago vira 500.** `MercadoPagoApi` embrulha tudo em
    `IllegalStateException`, que não é `BusinessException` — o pagador vê erro genérico
    de servidor em vez de "não deu pra iniciar o pagamento, tente de novo".

---

## Categoria 1 — Casos de erro não tratados

### [C2] Confirmação de pagamento sem transação e sem serialização — **Crítico**
`pagamento/webhook/MercadoPagoWebhookService.java:100-163`

`confirmarPagamento` não tem `@Transactional` e faz quatro escritas independentes: salva a
cobrança, salva a inscrição, envia e-mail e lança no financeiro. A guarda de idempotência
(linha 119) é uma leitura do status seguida de uma escrita, sem lock pessimista nem
`@Version`.

**Cenário de falha:** o webhook do Mercado Pago chega no mesmo instante em que
`PagamentoPollingService.pollarConfirmacao` (tentativa nº 7) consulta o MP. As duas threads
leem `status == PENDENTE`, as duas passam da linha 119, as duas chamam `marcarComoPago` e as
duas chamam `registrarNoFinanceiro` → **duas ENTRADAS de "Pagamento de inscrição" no
financeiro da igreja para um pagamento só**, duas SAÍDAS de taxa, e dois e-mails de
comprovante. O balancete fica inflado e ninguém percebe até fechar o mês.

Além disso, sem transação, uma falha no meio (banco cai depois de salvar a cobrança e antes
de salvar a inscrição) deixa cobrança PAGO com inscrição AGUARDANDO_PAGAMENTO — que o
`CobrancaEventoExpiracaoJob` mais tarde cancela.

### [C3] `reiniciar` pode descartar a referência de um pagamento aprovado — **Crítico**
`pagamento/cobranca/CobrancaController.java:322-334`

```java
mercadoPagoClient.cancelarPagamento(cobranca.getIgrejaId(), cobranca.getMpPaymentId());
cobranca.liberarParaNovaTentativa();   // <- limpa mpPaymentId incondicionalmente
```

O javadoc diz que `cancelarPagamento` é best-effort. Ele não é: o retorno é ignorado.

**Cenário de falha:** pessoa gera o QR Pix, paga no app do banco, a confirmação demora, ela
se impacienta e clica "Gerar novo QR code". O MP recusa o cancelamento (já aprovado), o
Domus limpa o `mpPaymentId` mesmo assim. Quando o webhook do pagamento aprovado chegar, ele
consulta pelo `external_reference` e ainda encontra a cobrança — mas se ela já tiver uma
segunda tentativa em curso, o `mpPaymentId` gravado é o da segunda; a pessoa paga duas
vezes e o segundo pagamento não tem estorno automático. O mínimo aqui seria só liberar
quando o cancelamento no MP confirmar, ou reconsultar o status antes de liberar.

### [I6] Falhas do Mercado Pago viram 500 em vez de 4xx — **Importante**
`pagamento/MercadoPagoApi.java:94, 173-176, 247-249, 311-312, 341-342, 371`

Todos os `catch (Exception e)` relançam `IllegalStateException`. Nenhum é `BusinessException`,
então o `GlobalExceptionHandler` responde 500. Isso cobre timeout, 4xx do MP por token
inválido, e resposta malformada.

**Cenário:** o Mercado Pago está lento; `POST /cobrancas/{id}/pagar` estoura o timeout do
`RestClient`. O pagador — que pode estar numa aba anônima, sem sessão — recebe um 500 sem
explicação. Pior: o Sentry recebe isso como erro de servidor do Domus, poluindo o
rastreamento de erro real. Recusa de cartão, por outro lado, está correta (volta 200 com
`status: rejected`).

### [I7] `enviarLembretePagamento` é o único envio de e-mail sem `try/catch` — **Importante**
`evento/inscricao/InscricaoService.java:1925`

Todos os outros envios do arquivo (`enviarEmailCancelamento:868`,
`enviarEmailEventoVirouGratuito:1403`, `enviarEmailComplementoPagamento:1785`,
`enviarEmailEstornoParcial:1827`) embrulham `emailService.enviar` em `try/catch` e só logam.
Este não. Uma falha do Resend devolve 500 pro admin que clicou "Enviar lembrete", e aborta
a transação — a notificação in-app da linha 1927 nunca é criada.

### [I8] `CalculadoraTaxaPagamento` divide por zero com taxa mal configurada — **Importante**
`pagamento/CalculadoraTaxaPagamento.java:49-50`

```java
BigDecimal fator = BigDecimal.ONE.subtract(taxaPercent.movePointLeft(2));
return valorAlvo.divide(fator, MathContext.DECIMAL128)...
```

`taxaPercent` vem do override por igreja em `conta_pagamento_igreja` (`getTaxaPixPercent`
etc.). Com `100`, `fator` é zero → `ArithmeticException` → 500 em `/pagar`,
`/opcoes-pagamento` **e** na simulação do formulário de evento. Com valor acima de 100, o
gross-up vira negativo e passa. Não há `@Min`/`@Max` nem validação no ponto de escrita da
taxa negociada. Hoje é campo interno, mas é o tipo de coisa que vira suporte quando a
camada comercial abrir.

### [M1] Mapa de reconferência sem expiração — **Menor**
`pagamento/PagamentoPollingService.java:52`

`ultimaReconferencia` é um `ConcurrentHashMap` que nunca remove entrada. O comentário
reconhece e aceita para o piloto; anotado aqui só para não virar surpresa quando escalar.

### [M2] Webhook ignora `refunded` e `charged_back` — **Menor**
`pagamento/webhook/MercadoPagoWebhookService.java:55-56, 130-139`

`STATUS_TERMINAL_NAO_APROVADO` cobre só `rejected` e `cancelled`. Um chargeback ou um estorno
feito direto no painel do Mercado Pago não chega em lugar nenhum do Domus: a inscrição
continua CONFIRMADA, a ENTRADA continua no financeiro, e a pessoa entra no evento sem ter
pago. Pelo menos uma notificação para o responsável já resolveria 90% do problema.

---

## Categoria 2 — Casos de borda, integridade de dado e corridas

### [C1] Complemento de pagamento não pago cancela a inscrição sem estornar o original — **Crítico**
`pagamento/job/CobrancaEventoExpiracaoJob.java:29-44`

O job pega toda `CobrancaEvento` PENDENTE vencida, marca EXPIRADO e cancela a
`InscricaoEvento` que estiver `AGUARDANDO_PAGAMENTO`. Ele não distingue "nunca pagou nada"
de "já pagou o valor original e deve só um complemento" — distinção que o resto do módulo
faz com cuidado (`InscricaoService.valorJaPago`, a tag "Falta complementar" da lista de
inscritos, o texto próprio do lembrete em `:1898`).

**Cenário de falha:** evento a R$ 100. Maria paga, fica CONFIRMADA. O admin reajusta para
R$ 130 → `aplicarMudancaValorPago` cria uma cobrança de complemento de R$ 30 (link de 48h) e
volta Maria para AGUARDANDO_PAGAMENTO (`InscricaoService.java:1655`). Maria não vê o e-mail em
48h. O job expira os R$ 30 e cancela a inscrição dela. **Resultado: Maria está fora do evento
e os R$ 100 dela ficaram com a igreja**, sem estorno, sem e-mail de cancelamento, sem linha
de "Reembolso" no financeiro. O `cancelarInterno`, que é o único caminho que sabe estornar,
nunca é chamado — o job mexe no status na mão.

Nem o caminho de cancelamento manual sofre disso (`cancelar` → `cancelarInterno` → estorno),
o que reforça que é esquecimento e não decisão.

### [I4] Estorno em lote: chamada externa bem-sucedida perdida quando uma seguinte falha — **Importante**
`evento/inscricao/InscricaoService.java:702-748`

O comentário explica bem a correção "fail-fast": primeiro todas as chamadas externas, depois
todas as mutações. O que ele não cobre é o caso de a **segunda** chamada externa falhar
depois de a primeira ter tido sucesso.

**Cenário:** uma inscrição com duas cobranças PAGO (valor original + um complemento que
chegou a ser pago). A 1ª passada estorna a cobrança A com sucesso e falha na B (MP fora do
ar). O `throw` da linha 728 acontece antes da 2ª passada, então **a cobrança A continua com
status PAGO e `valorEstornado = 0` no banco, mas o dinheiro já saiu no Mercado Pago**. O
`estornoPendente` só é marcado em B. Na retentativa, `valorRestanteParaEstornar()` de A ainda
devolve o valor cheio e o MP recusa por falta de saldo — exatamente o bug de 2026-08-27,
reintroduzido por outro caminho.

Efeito colateral do mesmo trecho: em `cancelar(...)` individual (não em lote), o
`marcarEstornoPendente()` + `save` da linha 726-727 é desfeito pelo rollback da própria
`BusinessException` — a tag "Estorno pendente" **nunca aparece** nesse caminho, só nos
caminhos em lote que capturam a exceção. O comentário de linha 722 assume que sempre
persiste.

### [I3] Duas definições de "vaga ocupada" — **Importante**
Reserva: `InscricaoService.contarOcupadas:459-464` +
`CobrancaController.pagar:183` (contam cobranças PAGO/PENDENTE não vencida).
Exibição: `InscricaoService.listarInscritos:1060-1063`,
`EventoService.atualizarEvento:247`, `ConviteController.consultar:66`,
`BotaoConfirmarPresenca.tsx:132` (contam inscrições CONFIRMADA).

Num evento pago, as duas divergem sempre que há gente em AGUARDANDO_PAGAMENTO.

**Cenário:** evento com 10 vagas, 10 pessoas inscritas e ainda pagando. A lista de inscritos
diz "10 vagas restantes"; o card e o convite público dizem que há vaga; o botão "Se
inscrever" fica ativo; o backend recusa com `VAGAS_ESGOTADAS` na hora do `/pagar` — depois de
a pessoa já ter preenchido o cartão. Do outro lado, `atualizarEvento:247` deixa o admin
reduzir as vagas para um número abaixo do que está de fato reservado, porque só olha
confirmadas.

### [I9] `marcarTodosPresentes` marca presença de inscrição cancelada — **Importante**
`evento/inscricao/InscricaoService.java:1166-1172` vs. `:1233-1238`

`marcarPresencaInscricao` (individual) valida `status == CONFIRMADA` e recusa com
`INSCRICAO_NAO_CONFIRMADA`. O `marcarTodosPresentes` percorre `listarPorEvento(eventoId)` e
marca **todo mundo**, inclusive CANCELADA e AGUARDANDO_PAGAMENTO. O
`desmarcarTodosPresentes` tem o mesmo problema. Resultado: o relatório de presença conta
quem cancelou e quem nunca pagou, e a única forma de corrigir é destrocar um a um — pelo
endpoint individual, que aí *recusa* mexer em cancelada.

### [I10] `cancelarPorCobranca` fura a política de cancelamento pós-prazo — **Importante**
`evento/inscricao/InscricaoService.java:562-576`

O `cancelar(...)` autenticado passa por `validarCancelamentoPermitido` e respeita
`PoliticaCancelamentoAposPrazo.NAO_PERMITIDO`. O `cancelarPorCobranca` — usado pelo link
"Não vou mais — cancelar inscrição" dos e-mails de lembrete e de complemento — só valida
`validarEventoAberto`. A regra que o evento configurou é contornável por qualquer um que
tenha o e-mail. Como é a mesma pessoa, o dano é baixo, mas é uma regra de negócio que existe
em um caminho e não no outro.

### [I11] Prazo de inscrição no passado é aceito no cadastro — **Importante**
`evento/EventoService.java` (`validarPrazoInscricao`, ~linha 918 do arquivo)

A validação checa só duas coisas: que `requerInscricao` está ligado e que `inscricoesAte` é
anterior a `inicioEm`. Nada impede cadastrar um evento novo já com o prazo vencido — o
evento nasce com as inscrições fechadas, sem nenhum aviso no formulário, e o admin só
descobre quando alguém reclama. Um `isBefore(now)` no cadastro (não na edição, onde pode
ser intencional para fechar na marra) resolveria.

### [I12] `aplicarMudancaValorPago` estorna só da cobrança paga mais recente, com corte silencioso — **Importante**
`evento/inscricao/InscricaoService.java:1673-1693`

O excedente é estornado exclusivamente da última cobrança PAGO
(`.reduce((a, b) -> b)`), e se o `restanteEstornavel` dela for menor que o `aEstornar`, o
valor é simplesmente truncado (`if (aEstornar.compareTo(restanteEstornavel) > 0) aEstornar =
restanteEstornavel;`) sem procurar o saldo nas cobranças anteriores e sem registrar em lugar
nenhum que faltou devolver.

**Cenário:** pessoa pagou R$ 100 (cobrança A) e depois um complemento de R$ 30 (cobrança B).
O admin baixa o preço para R$ 50. O excedente é R$ 80, mas só R$ 30 são estornáveis em B —
os R$ 50 restantes ficam com a igreja em silêncio, e nem a tag "Estorno pendente" aparece.

### [M3] Ordem de `.reduce((a,b) -> b)` presume ordenação por criação — **Menor**
`evento/inscricao/InscricaoService.java:1675`

O comentário afirma que "a mais recente é a última da lista (ordem de criação)", mas
`cobrancaEventoRepository.findByEventoId` não tem `ORDER BY`. Sem cláusula explícita o
Postgres não garante ordem nenhuma. Funciona na prática hoje; é frágil por construção.

### [M4] Reabertura de cobrança expirada pode estourar as vagas — **Menor**
`pagamento/webhook/MercadoPagoWebhookService.java:117-128`

O `expiradaMasRecuperavel` (decisão correta — não descartar dinheiro real) reabre a inscrição
sem reconferir vaga. Se o job já tinha liberado a vaga e outra pessoa entrou no lugar, o
evento fica com uma pessoa a mais do que o limite. Provavelmente é o mal menor, mas hoje isso
acontece em silêncio — nem log de aviso para o responsável.

### [M5] Convite público sem teto próprio de requisições — **Menor**
`evento/convite/ConviteController.java:111-132`, `SecurityConfig` (`/convites/**` permitAll)

`POST /convites/{token}/entrar` cria inscrição (e cobrança) sem sessão, sob o rate limit
global de 100/min por IP. Um token compartilhado num grupo grande de WhatsApp basta para
alguém encher as vagas de um evento gratuito com nomes falsos. O backlog já registra que
falta um tier por rota — anotado aqui porque esta rota é a mais exposta do módulo.

### [M6] Token de convite fica órfão se quem convidou for arquivado — **Menor**
`evento/convite/ConviteService.java:65-66`

`pessoaRepository.findById` respeita o `@SQLRestriction` de arquivamento, então arquivar a
pessoa que gerou o link transforma todo convite dela em 404 "Este convite não é mais válido"
— sem distinguir de um token expirado. É defensável, mas a mensagem mente sobre o motivo.

---

## Categoria 3 — Coisas pela metade ou inconsistentes

### [C4] Pagamento pendente + prazo vencido = beco sem saída no front — **Crítico**
`frontend/src/components/module/eventos/BotaoConfirmarPresenca.tsx:234-240`
+ `backend/.../DTOs/MinhaInscricaoResponse.java:29` (`inscrito` = `i.estaConfirmada()`)

```tsx
if (encerradoPorPrazo && !podeFurarPrazo && !minha?.inscrito) {
  return <button disabled>Inscrições encerradas em {dataPrazo}</button>
}
```

Uma inscrição AGUARDANDO_PAGAMENTO tem `inscrito === false`. O `return` acontece **antes** do
cálculo de `cenaAcao` (linha 312), então o bloco `'pendente'` — que contém o link
`/eventos/{id}/pagamento/{cobrancaId}` e o botão de cancelar — nunca é renderizado.

**Cenário completo:** evento com `inscricoesAte`. Pessoa se inscreve na véspera, fecha o
checkout sem concluir. O prazo passa. O `PrazoInscricaoProcessador:57-61` manda a notificação
"O prazo encerra em X. Falta pagar pra garantir sua vaga" — mas a única tela que ela tem
mostra um botão desabilitado. Ela não consegue pagar nem cancelar. Horas depois o
`CobrancaEventoExpiracaoJob` cancela a inscrição dela. A pessoa perde a vaga por um bloqueio
de UI, não por uma regra. O backend, note-se, **deixaria** ela pagar (`/pagar` não checa
prazo — ver I1), então é puramente o front travando.

### [I1] `POST /cobrancas/{id}/pagar` não valida prazo nem situação do evento — **Importante**
`pagamento/cobranca/CobrancaController.java:143-239`

O método carrega o evento (linha 180) e usa dele **só** `getVagas`, `isPagamentoAceitaCartao`
e `getPagamentoMaxParcelas`. Nem `validarEventoAberto`, nem `inscricoesAte`.

**Cenário:** link de pagamento de 48h gerado no sábado para um evento de domingo. O evento
acontece, termina. Na segunda-feira o convidado abre o link e paga. A cobrança vira PAGO, a
inscrição vira CONFIRMADA num evento ENCERRADO, entra dinheiro no financeiro, e o pagador
recebe um comprovante de um evento que já passou — e não consegue cancelar, porque
`cancelarPorCobranca` chama `validarEventoAberto` e recusa. Precisa de intervenção manual da
igreja para devolver.

Isso conecta com o item já registrado no backlog ("prazo do link fixo em 48h devia acompanhar
o evento"), mas é maior que aquele: mesmo com o prazo do link certo, o `/pagar` deveria ter
sua própria porta.

### [I2] Valores em e-mail e comprovante ficaram no alvo, não no cobrado (regressão do V40) — **Importante**
- `pagamento/webhook/MercadoPagoWebhookService.java:251-252` — "Valor pago: X" usa
  `cobranca.getValor()`.
- `evento/inscricao/InscricaoService.java:1885-1886` — lembrete diz "falta pagar X" com
  `cobranca.getValor()`.
- `evento/inscricao/InscricaoService.java:1464-1465` — "evento virou pago" anuncia `preco`.
- `evento/inscricao/InscricaoService.java:1644, 1657` — complemento anuncia `novoValorDevido`.

Depois do gross-up (V40), o pagador é debitado em `valorCobrado` = alvo ÷ (1 − taxa). O
comprovante que ele recebe por e-mail declara o alvo. Num evento de R$ 100 com cartão em 6x, a
diferença passa de 10% — o e-mail diz R$ 100,00 e a fatura diz R$ 111,73.

O comprovante é o caso mais grave dos quatro, porque é o documento que a pessoa guarda. Os
outros três são anúncios de valor a pagar e poderiam legitimamente mostrar o alvo *desde que*
a tela seguinte deixe claro que a taxa é acrescida — o que `EscolhaMeioPagamento` faz. O
comprovante não tem essa defesa.

### [I5] Edição "toda a série" propaga preço e política sem propagar as consequências — **Importante**
`evento/EventoService.java:454-476` (`copiarCamposEditaveisPara`) e `:290-306`

O comentário na linha 437 é explícito: "Nunca toca em inscrição/cobrança", e a decisão de
2026-08-27 (linha 290) diz que o reembolso em lote roda só na ocorrência editada. O problema
é que `copiarCamposEditaveisPara` **copia `preco`** (linha 468) para todas as outras
ocorrências.

**Cenário:** série semanal de 8 ocorrências, todas pagas a R$ 50, com gente inscrita e paga em
várias. O admin edita uma e escolhe "toda a série", tirando o preço. A ocorrência editada
estorna todo mundo corretamente. As outras 7 ficam com `preco = null` (gratuitas) e as
cobranças PAGO delas intocadas — dinheiro retido de um evento que agora é de graça — e quem
estava AGUARDANDO_PAGAMENTO nelas fica travado para sempre: não há mais cobrança que confirme
(o evento é gratuito), e o job de expiração vai cancelar essas inscrições. O caminho inverso
(gratuito → pago em série) é pior: as outras ocorrências ficam pagas com todo mundo CONFIRMADA
sem nunca ter pago nada.

Ou o preço sai de `copiarCamposEditaveisPara`, ou a propagação chama os `aplicar*` por
ocorrência. Do jeito que está, as duas metades da decisão se contradizem.

**Junto disso:** `inscricoesAte` e `avisoPrazo*` **não** estão em `copiarCamposEditaveisPara`.
Prazo de inscrição é a única configuração de evento recente que não propaga para a série —
provavelmente esquecimento na Task de V38, já que `politicaCancelamentoAposPrazo` (V39, linha
472) propaga.

### [I13] A prévia de "virar gratuito" mostra um valor menor do que vai ser estornado — **Importante**
`evento/inscricao/InscricaoService.java:1277-1282`

```java
if (cobranca.getStatus() == StatusCobranca.PAGO) {
    valorTotalAEstornar = valorTotalAEstornar.add(cobranca.getValor());
}
```

Usa `getValor()` (alvo) enquanto o estorno real usa `valorRestanteParaEstornar()` (bruto,
`valorCobrado`), e não desconta o que já foi estornado. O `ModalImpactoMudancaPreco` mostra ao
admin "isso vai estornar R$ X de N pessoas" com um X sistematicamente abaixo do real, e
conta como "a estornar" cobranças que já foram totalmente estornadas antes. O
`calcularImpactoMudancaValorPago` logo abaixo (`:1535`) faz certo, usando `valorAlvoRestante()`
— a inconsistência entre as duas prévias irmãs é o que mais indica que é bug e não decisão.

O `calcularImpactoEventoVirarPago:1307` tem uma versão mais leve do mesmo: multiplica
`precoNovo × pessoas`, sem gross-up, então subestima o que as pessoas de fato vão pagar.

### [M7] `contarPessoasConfirmadas` é `@Transactional(readOnly = true)` e chamado de dentro de transação de escrita — **Menor**
`evento/inscricao/InscricaoService.java:437-440`, chamado de `EventoService.atualizarEvento:247`

Sem efeito prático (a propagação REQUIRED reusa a transação existente e o `readOnly` é
ignorado), mas o método é declarado como se abrisse transação própria. Só ruído; anotado para
quem for ler depois não achar que há isolamento ali.

### [M8] `MercadoPagoClient.criarPagamento(UUID, CobrancaEvento)` morto — **Menor**
Já registrado no backlog (seção V40). Continua lá, e agora cobraria o alvo líquido em vez do
bruto se alguém o chamasse.

---

## Categoria 4 — Segurança e multi-tenant

Esta é a parte mais sólida do módulo. Não encontrei nenhum caminho que leia `igreja_id` do
corpo ou de parâmetro: `EventoController`, `InscricaoController` e `CobrancaController`
(no único endpoint autenticado dele) usam `UsuarioAutenticado.getIgrejaId()`. As checagens de
permissão passam todas por `Permissoes.podeGerenciarInscricoes(role)` / `podeGerenciarEventos`
— capacidade, não identidade, como o CLAUDE.md manda. Não achei nenhum
`role.equals("ADMIN_IGREJA")` solto no módulo.

Os pontos abaixo são observações, não falhas exploráveis:

- **Rotas públicas de cobrança expõem o mínimo.** `CobrancaCheckoutDTO` e `CobrancaPublicaDTO`
  levam título do evento, data, nome do pagador e valor — nada de telefone/e-mail, coerente
  com o comentário de classe. `tentarEstornoNovamente` exige sessão de gestor e valida
  `cobranca.getIgrejaId().equals(igrejaId)` antes de agir (`InscricaoService:791-793`),
  devolvendo 404 e não 403 — bom, não vaza existência.
- **`ConviteController.foto` está bem fechado.** Só serve os três ids que o próprio
  `consultar` já expõe; qualquer outro dá 404, e o javadoc explica por que a exceção à regra
  "foto nunca é URL pública" é aceitável. Todos os acessos a `getFoto()` estão guardados por
  null-check — verifiquei linha a linha procurando NPE e não há.
- **Cancelamento cruzado está coberto.** `InscricaoService.cancelar:585-593` exige gestor *da
  mesma igreja* ou ser a própria pessoa; um LÍDER de igreja irmã não passa.
- **[M5]** (acima) é a única superfície que eu apertaria: `/convites/*/entrar` sem teto próprio.

---

## Já rastreado no backlog

Encontrado e confirmado durante esta auditoria, já registrado em
`BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md` — não re-explico:

- `taxaDevolvida = BigDecimal.ZERO` fixo no estorno.
- Os dois lançamentos (bruto + taxa) linkados só por string de descrição, sem FK.
- `MercadoPagoClient.criarPagamento(UUID, CobrancaEvento)` morto (repetido acima como M8 só
  porque o V40 piorou a consequência).
- Aprender a taxa real do `fee_details` para corrigir o gross-up.
- Prazo do link de pagamento fixo em 48h, sem relação com o evento (o [I1] acima é um problema
  distinto e maior: o `/pagar` não tem porta nenhuma além do prazo do link).
- Frontend das Tasks 9–12 do V40 + validação e2e no sandbox.
- Exportar lista de inscritos (CSV/PDF).
- Rate limiting sem tier por rota individual.

---

## Categoria 5 — Ideias de produto (no estilo `politica_cancelamento_apos_prazo`)

Priorizadas por (valor para uma igreja que já cobra) ÷ (tamanho da mudança). Todas cabem no
modelo atual — coluna nova em `evento` ou em `inscricao_evento`, sem tabela nova, exceto onde
indicado.

**Alta prioridade**

1. **Quem absorve a taxa do Mercado Pago, por evento.** Hoje o gross-up sempre joga a taxa no
   pagador (`CalculadoraTaxaPagamento`). Um `evento.taxa_absorvida_pela_igreja` (boolean) faria
   o `valorACobrar` devolver o alvo puro quando ligado, e o financeiro já sabe separar a taxa
   em "Taxas de pagamento". *Por quê:* "R$ 50" que vira "R$ 51,73" no checkout é a reclamação
   nº 1 de qualquer evento de igreja; algumas preferem absorver e anunciar redondo.
   *Onde:* `CalculadoraTaxaPagamento.valorACobrar` + um toggle no `EventoForm`. É a mudança de
   menor esforço com maior impacto percebido, e já está meio pronta (o item está no backlog
   como pergunta em aberto — aqui vira proposta concreta).

2. **Política de reembolso ANTES do prazo.** Hoje `PoliticaCancelamentoAposPrazo` só governa o
   depois; antes do prazo estorna sempre 100%. Uma igreja que compra camiseta e reserva ônibus
   precisa reter algo. Proposta mínima: `evento.percentual_retido_no_cancelamento` (default 0)
   aplicado em `estornarCobrancasERetornarValor`. *Por quê:* é a continuação natural do V39 e
   a única coisa que hoje impede a igreja de fazer acampamento no Domus sem prejuízo.

3. **Encerrar / reabrir inscrições na mão.** `inscricoesAte` resolve o caso planejado; falta o
   caso "lotou, fecha agora" e "abriu mais 10 vagas, reabre". Hoje o único jeito é editar a
   data para o passado — e nem isso funciona bem, porque a edição não valida nada
   (ver I11). Um `evento.inscricoes_fechadas_manualmente` (boolean) entraria como 6ª porta em
   `validarPrazoInscricao`, e a `SituacaoInscricao` ganharia `ENCERRADA_MANUALMENTE`.
   *Por quê:* é o botão que todo organizador procura e não acha.

4. **Cortesia / isenção por inscrição.** Um `inscricao_evento.isento` (boolean, só gestor
   marca) que faz `inscreverInterno` pular a criação da `CobrancaEvento` e nascer CONFIRMADA.
   *Por quê:* pastor convidado, preletor, equipe de apoio, família que não pode pagar — hoje a
   única saída é o admin pagar do próprio bolso ou o evento inteiro virar gratuito. É a
   lacuna que mais aparece em evento pago de igreja.

5. **Aviso de inscrição cancelada por falta de pagamento.** O `CobrancaEventoExpiracaoJob`
   cancela em silêncio absoluto — nem e-mail, nem notificação, nem para o inscrito nem para o
   responsável. Todo o resto do módulo avisa de tudo (virou pago, virou gratuito, complemento,
   estorno parcial, prazo chegando). *Por quê:* a pessoa acha que está inscrita até chegar no
   portão. É o menor esforço da lista: um `enviarEmail...` no job, reusando o padrão que já
   existe cinco vezes no `InscricaoService`. (Depende de resolver o [C1] antes.)

**Média prioridade**

6. **Pagar na portaria / cobrança manual.** `evento.pagamento_presencial` — evento com preço
   mas sem cobrança automática; a inscrição nasce CONFIRMADA e o gestor marca "pago" na lista
   de inscritos, o que dispara o lançamento no financeiro pelo
   `MovimentacaoAutomaticaService` já existente. *Por quê:* nem toda igreja quer, ou pode,
   conectar Mercado Pago — e hoje evento pago sem conta conectada simplesmente não deixa
   ninguém se inscrever (`validarContaPagamentoConectada`).

7. **Cancelar inscrição de outra pessoa escolhendo reembolsar ou não, na hora.** Hoje o gestor
   sempre estorna, exceto no caso muito específico de pós-prazo com
   `PERMITIDO_SEM_REEMBOLSO`. Um checkbox no `ConfirmarCancelamentoInscricao` que vira um
   parâmetro do `cancelar(...)` (a mecânica já existe — `cancelarInterno(inscricao,
   semReembolso)`). *Por quê:* "essa pessoa desistiu em cima da hora, combinamos de não
   devolver" é uma conversa real, e forçar a política a ser do evento inteiro é grosso demais.

8. **Reenviar comprovante de pagamento.** Existe "reenviar convite" para usuário e "enviar
   lembrete de pagamento" para quem deve; não existe reenviar o comprovante de quem já pagou.
   Um `POST /eventos/{id}/inscricoes/{id}/reenviar-comprovante` reusando
   `MercadoPagoWebhookService.montarCorpoEmail`. *Por quê:* e-mail some, cai em spam, a pessoa
   troca de endereço. Barato e resolve suporte. (Aproveitar para corrigir o [I2] no mesmo
   lugar.)

9. **Limite de convidados por pessoa.** `evento.max_convidados_por_pessoa` checado em
   `inscreverConvidado` contando por `convidado_por_pessoa_id`. Hoje um convite público pode
   trazer gente ilimitada, limitado só pelas vagas do evento. *Por quê:* evita que uma pessoa
   sozinha consuma o evento inteiro, e é o que dá segurança para a igreja divulgar o link
   público de verdade.

**Baixa prioridade / quando houver demanda**

10. **Lista de espera.** Um `status = EM_ESPERA` em `InscricaoEvento` e uma promoção automática
    quando uma cobrança expira ou alguém cancela. *Por quê:* alto valor percebido, mas exige
    decidir política de promoção (ordem de chegada? aviso com prazo para pagar?) e mexe no
    coração da contagem de vagas — que, como mostra o [I3], já precisa ser unificada antes.
    Não fazer sem brainstorm.

11. **Desconto por faixa / lote (early bird).** `preco` vira uma pequena tabela de faixas por
    data. É a única ideia desta lista que exige tabela nova, e provavelmente só faz sentido
    depois de a igreja rodar dois ou três eventos pagos de verdade — YAGNI até lá.

---

## Nota sobre teste

Nenhum dos achados Críticos tem teste cobrindo o cenário. Em particular, `InscricaoServiceTest`
(o arquivo mais completo do projeto) não tem caso para "cobrança de complemento expira" nem
para "confirmação concorrente webhook + poll", e `CobrancaEventoExpiracaoJob` não tem classe de
teste própria. Seguindo a regra de ouro do CLAUDE.md — o teste prova a feature — cada correção
de C1–C4 deveria nascer com o teste do cenário descrito aqui.
