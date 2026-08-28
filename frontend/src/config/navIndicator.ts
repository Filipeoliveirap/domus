// Modo do indicador de navegação de rota. Navegação nunca bloqueia — pra operação
// bloqueante (pagar, excluir, submeter form) use <OverlayCarregando>, não isto.
//   'barra'         → barra fina de progresso no topo da tela
//   'barra-e-link'  → barra no topo + item do sidebar clicado troca o ícone por spinner
export const MODO_INDICADOR_NAV: 'barra' | 'barra-e-link' = 'barra'
