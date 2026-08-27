
export const Endpoints = {
  inicio: {
    GET: '/inicio',
  },
  dashboard: {
    GET: '/dashboard',
  },
  notificacoes: {
    LISTAR: '/notificacoes',
    CONTAGEM_NAO_LIDAS: '/notificacoes/contagem-nao-lidas',
    MARCAR_LIDA: (id: string) => `/notificacoes/${id}/lida`,
    MARCAR_TODAS_LIDAS: '/notificacoes/lidas',
  },
  auth: {
    LOGIN: '/auth/login',
    GOOGLE_LOGIN: '/auth/google/login',
    GOOGLE_REGISTRAR: '/auth/google/registrar',
    REFRESH: '/auth/refresh',
    LOGOUT: '/auth/logout',
    ME: '/auth/me',
    FORGOT_PASSWORD: '/auth/forgot-password',
    RESET_PASSWORD: '/auth/reset-password',
    REGISTER_IGREJA: '/igrejas/registrar',
    CHANGE_PASSWORD: '/auth/change-password',
  },
  termos: {
    ACEITAR: '/termos/aceitar',
  },
  usuarios: {
    LISTAR_USUARIOS: '/usuarios',
    BY_ID: (id: string) => `/usuarios/${id}`,
    STATUS: (id: string) => `/usuarios/${id}/status`,
    ROLE: (id: string) => `/usuarios/${id}/role`,
    CONCEDER_ACESSO: '/usuarios/conceder-acesso',
    REATIVAR_ACESSO: '/usuarios/reativar-acesso',
    REENVIAR_CONVITE: (id: string) => `/usuarios/${id}/reenviar-convite`,
    CAPACIDADE: (id: string) => `/usuarios/${id}/capacidades`,
    CAPACIDADE_ESPECIFICA: (id: string, cap: string) => `/usuarios/${id}/capacidades/${cap}`,
    ARQUIVADOS: '/usuarios/arquivados',
    RESTAURAR: (id: string) => `/usuarios/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/usuarios/${id}/definitivo`,
  },
  pessoas: {
    LISTAR: '/pessoas',
    CRIAR: '/pessoas',
    BY_ID: (id: string) => `/pessoas/${id}`,
    ARQUIVAR: (id: string) => `/pessoas/${id}`,
    BAIRROS: '/pessoas/bairros',
    ME: '/pessoas/me',
    MINHA_FOTO: '/pessoas/me/foto',
    FOTO: (id: string) => `/pessoas/${id}/foto`,
    PESSOA_MINISTERIOS: (pessoaId: string) => `/pessoas/${pessoaId}/ministerios`,
    ARQUIVADOS: '/pessoas/arquivados',
    RESTAURAR: (id: string) => `/pessoas/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/pessoas/${id}/definitivo`,
  },

  eventos: {
    LISTAR: '/eventos',
    CRIAR: '/eventos',
    BY_ID: (id: string) => `/eventos/${id}`,
    TIPOS: '/eventos/tipos',
    IMPACTO_RESTRICAO: (id: string) => `/eventos/${id}/impacto-restricao`,
    ELEGIBILIDADE: (id: string) => `/eventos/${id}/elegibilidade`,
    RELATORIO: (id: string) => `/eventos/${id}/relatorio`,
    RELATORIO_GERAL: '/eventos/relatorio-geral',
    ARQUIVADOS: '/eventos/arquivados',
    RESTAURAR: (id: string) => `/eventos/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/eventos/${id}/definitivo`,
    FOTO: (id: string) => `/eventos/${id}/foto`,
    CAMPOS_PERSONALIZADOS: (id: string) => `/eventos/${id}/campos-personalizados`,
    CAMPOS_PERSONALIZADOS_MINHA: (id: string) => `/eventos/${id}/campos-personalizados/minha`,
  },

  locaisEvento: {
    LISTAR: '/locais-evento',
    CRIAR: '/locais-evento',
    BY_ID: (id: string) => `/locais-evento/${id}`,
    ARQUIVADOS: '/locais-evento/arquivados',
    RESTAURAR: (id: string) => `/locais-evento/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/locais-evento/${id}/definitivo`,
  },

  ministerios: {
    LISTAR: '/ministerios',
    CRIAR: '/ministerios',
    BY_ID: (id: string) => `/ministerios/${id}`,
    FOTO: (id: string) => `/ministerios/${id}/foto`,
    ARQUIVADOS: '/ministerios/arquivados',
    RESTAURAR: (id: string) => `/ministerios/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/ministerios/${id}/definitivo`,
    MEMBROS: (id: string) => `/ministerios/${id}/membros`,
    MEMBRO: (id: string, pessoaId: string) => `/ministerios/${id}/membros/${pessoaId}`,
    PAPEL: (id: string, pessoaId: string) => `/ministerios/${id}/membros/${pessoaId}/papel`,
    PEDIDOS: (id: string) => `/ministerios/${id}/pedidos`,
    ACEITAR_PEDIDO: (id: string, pessoaId: string) => `/ministerios/${id}/pedidos/${pessoaId}/aceitar`,
    RECUSAR_PEDIDO: (id: string, pessoaId: string) => `/ministerios/${id}/pedidos/${pessoaId}`,
  },

  inscricoes: {
    INSCREVER: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    MINHA: (eventoId: string) => `/eventos/${eventoId}/inscricoes/minha`,
    INSCREVER_MEMBROS: (eventoId: string) => `/eventos/${eventoId}/inscricoes/pessoas`,
    CONVIDADOS: (eventoId: string) => `/eventos/${eventoId}/inscricoes/convidados`,
    PARTICIPANTES: (eventoId: string) => `/eventos/${eventoId}/inscricoes/participantes`,
    LISTAR: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    CANCELAR: (inscricaoId: string) => `/inscricoes/${inscricaoId}`,
    RESPOSTAS: (inscricaoId: string) => `/inscricoes/${inscricaoId}/respostas`,
  },

  presenca: {
    MARCAR_TODOS: (eventoId: string) => `/eventos/${eventoId}/presenca/marcar-todos`,
    DESMARCAR_TODOS: (eventoId: string) => `/eventos/${eventoId}/presenca/desmarcar-todos`,
    INSCRICAO: (eventoId: string, inscricaoId: string) =>
      `/eventos/${eventoId}/presenca/inscricoes/${inscricaoId}`,
  },

  categorias: {
    base: '/categorias',
    todas: '/categorias/todas',
    porId: (id: string) => `/categorias/${id}`,
    contagemMovimentacoes: (id: string) => `/categorias/${id}/contagem-movimentacoes`,
    arquivadas: '/categorias/arquivadas',
    restaurar: (id: string) => `/categorias/${id}/restaurar`,
    definitivo: (id: string) => `/categorias/${id}/definitivo`,
  },

  movimentacoes: {
    base: '/movimentacoes',
    porId: (id: string) => `/movimentacoes/${id}`,
    totais: '/movimentacoes/totais',
    arquivadas: '/movimentacoes/arquivadas',
    restaurar: (id: string) => `/movimentacoes/${id}/restaurar`,
    definitivo: (id: string) => `/movimentacoes/${id}/definitivo`,
  },

  relatorios: {
    resumo: '/relatorios/resumo',
    porCategoria: '/relatorios/por-categoria',
    evolucaoMensal: '/relatorios/evolucao-mensal',
    maiorLancamento: '/relatorios/maior-lancamento',
    porContribuinte: '/relatorios/por-contribuinte',
    congregacoes: '/relatorios/congregacoes',
    balanceteAnual: '/relatorios/balancete-anual',
    balanceteFamilia: '/relatorios/balancete-anual/congregacoes',
  },

  igreja: {
    MINHA: '/igrejas/minha',
    ROTULOS: '/igrejas/minha/rotulos',
    LOGO: '/igrejas/minha/logo',
    exclusao: {
      RESUMO: '/igrejas/exclusao/resumo',
      AGENDAR: '/igrejas/exclusao/agendar',
      CANCELAR: '/igrejas/exclusao/cancelar',
    },
  },

  igrejasVinculadas: {
    STATUS: '/igrejas-vinculadas',
    GERAR_CODIGO: '/igrejas-vinculadas/codigo',
    ENTRAR: '/igrejas-vinculadas/entrar',
    DESVINCULAR: (congregacaoId: string) => `/igrejas-vinculadas/congregacoes/${congregacaoId}`,
    SAIR: '/igrejas-vinculadas/sair',
  },

  visitantes: {
    LISTAR: '/visitantes',
    CRIAR: '/visitantes',
    BY_ID: (id: string) => `/visitantes/${id}`,
    TOGGLE_CONTATO: (id: string) => `/visitantes/${id}/contato`,
    TOGGLE_VISITA: (id: string) => `/visitantes/${id}/visita`,
    TOGGLE_ACOMPANHAMENTO: (id: string) => `/visitantes/${id}/acompanhamento`,
    TOGGLE_CELULA: (id: string) => `/visitantes/${id}/celula`,
    BUSCA_LEVE: '/visitantes/busca-leve',
  },

  convites: {
    GERAR: (eventoId: string) => `/eventos/${eventoId}/inscricoes/minha/convite`,
    CONSULTAR: (token: string) => `/convites/${token}`,
    ENTRAR: (token: string) => `/convites/${token}/entrar`,
  },

  fotos: {
    UPLOAD: '/fotos',
    BY_ID: (id: string) => `/fotos/${id}`,
  },

  pagamento: {
    STATUS: '/pagamentos/conta/status',
    CONECTAR: '/pagamentos/conta/conectar',
    DESCONECTAR: '/pagamentos/conta',
  },

  cobrancas: {
    BUSCAR_POR_TOKEN: (token: string) => `/cobrancas/${token}`,
    BUSCAR_POR_ID: (id: string) => `/cobrancas/id/${id}`,
    PAGAR: (id: string) => `/cobrancas/${id}/pagar`,
    STATUS: (id: string) => `/cobrancas/${id}/status`,
  },

  celulas: {
    LISTAR: '/celulas',
    CRIAR: '/celulas',
    BY_ID: (id: string) => `/celulas/${id}`,
    FOTO: (id: string) => `/celulas/${id}/foto`,
    ARQUIVADOS: '/celulas/arquivados',
    RESTAURAR: (id: string) => `/celulas/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/celulas/${id}/definitivo`,
    MEMBROS: (id: string) => `/celulas/${id}/membros`,
    MEMBRO: (id: string, membroId: string) => `/celulas/${id}/membros/${membroId}`,
    PAPEL: (id: string, membroId: string) => `/celulas/${id}/membros/${membroId}/papel`,
    CONVERTER: (id: string, visitanteId: string) => `/celulas/${id}/converter/${visitanteId}`,
  },
}