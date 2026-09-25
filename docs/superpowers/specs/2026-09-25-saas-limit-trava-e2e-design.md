# Spec de Design: Trava de Limite de Pessoas por Família de Igrejas & UX de Transbordo (Modelo A)

> **Status:** Aprovado em 2026-09-25  
> **Objetivo:** Impedir que igrejas filhas criadas via código de convite burlem os limites do plano da matriz (Igreja Mãe), garantindo cota consolidada por toda a família com mensagens adaptadas à nomenclatura da igreja (rotulos) e permissão diferenciada entre Igreja Mãe (contratante) e Igreja Filha.

---

## 1. Backend: Regras de Cota Consolidada & Mensagens

1. **Escopo Consolidado**:
   - `PessoaService.cadastrarMembro`: calcula o total de pessoas ativas da família (`igreja_id = maeId OR igreja_mae_id = maeId`).
   - Se o limite do plano for atingido, lança `PlanoLimiteExcedidoException`.

2. **Diferenciação por Tipo de Igreja**:
   - **Igreja Mãe (Contratante)**: Lança exceção permitindo upgrade do plano.
   - **Igreja Filha**: Lança exceção indicando que apenas a igreja contratante/matriz pode realizar o upgrade.

---

## 2. Frontend: Nomenclatura Dinâmica & Modal de Upgrade

1. **Uso de Nomenclatura Customizada (`useRotulos`)**:
   - O hook `useRotulos()` retorna a nomenclatura configurada pela igreja para `congregacao`.
   - Se o rótulo for "Congregação", aplica concordância no plural: *"Suas congregações atingiram o limite de X pessoas..."*.
   - Se for "Rede", "Unidade", etc., aplica o artigo correspondente (*"Sua rede de igrejas atingiu o limite de X pessoas..."*).

2. **Diferenciação de Comportamento na UI ao Exceder o Limite**:
   - **Se Igreja Mãe (`igrejaMaeId == null`)**:
     - Exibe Modal/Banner de Upgrade de Plano com ilustração e botão *"Fazer Upgrade Agora"*, direcionando para a página de gestão de assinatura (`/configuracoes/assinatura`).
   - **Se Igreja Filha (`igrejaMaeId != null`)**:
     - Exibe Modal/Aviso informativo bloqueando o upgrade direto: *"Limite da família atingido. Entre em contato com a igreja contratante do plano para solicitar o aumento da capacidade."*.
     - O botão de alteração de plano fica desabilitado/oculto para a igreja filha.

---

## 3. Estratégia de Testes (E2E / Unitários)

- **Backend**: Testar no `PessoaServiceTest` o lançamento de limite excedido e a validação consolidada para Igreja Mãe e Igreja Filha.
- **Frontend**: Testar no `useCadastrarPessoa.test.ts` e `ModalLimiteExcedido.test.tsx` a renderização do modal com a nomenclatura dinâmica (Rede, Congregação) e o bloqueio para igreja filha.
