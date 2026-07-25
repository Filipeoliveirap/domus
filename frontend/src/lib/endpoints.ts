
export const Endpoints = {
  inicio: {
    GET: '/inicio',
  },
  dashboard: {
    GET: '/dashboard',
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
  },
  pessoas: {
    LISTAR: '/pessoas',
    CRIAR: '/pessoas',
    BY_ID: (id: string) => `/pessoas/${id}`,
    ARQUIVAR: (id: string) => `/pessoas/${id}`,
    BAIRROS: '/pessoas/bairros',
    ME: '/pessoas/me',
    PESSOA_MINISTERIOS: (pessoaId: string) => `/pessoas/${pessoaId}/ministerios`,
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
  },

  locaisEvento: {
    LISTAR: '/locais-evento',
    CRIAR: '/locais-evento',
    BY_ID: (id: string) => `/locais-evento/${id}`,
  },

  ministerios: {
    LISTAR: '/ministerios',
    CRIAR: '/ministerios',
    BY_ID: (id: string) => `/ministerios/${id}`,
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
    ACOMPANHANTES: (eventoId: string, inscricaoId: string) =>
      `/eventos/${eventoId}/inscricoes/${inscricaoId}/acompanhantes`,
    PARTICIPANTES: (eventoId: string) => `/eventos/${eventoId}/inscricoes/participantes`,
    LISTAR: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    CANCELAR: (inscricaoId: string) => `/inscricoes/${inscricaoId}`,
    REMOVER_ACOMPANHANTE: (acompanhanteId: string) => `/acompanhantes/${acompanhanteId}`,
  },

  presenca: {
    MARCAR_TODOS: (eventoId: string) => `/eventos/${eventoId}/presenca/marcar-todos`,
    INSCRICAO: (eventoId: string, inscricaoId: string) =>
      `/eventos/${eventoId}/presenca/inscricoes/${inscricaoId}`,
    ACOMPANHANTE: (eventoId: string, acompanhanteId: string) =>
      `/eventos/${eventoId}/presenca/acompanhantes/${acompanhanteId}`,
  },

  categorias: {
    base: '/categorias',
    todas: '/categorias/todas',
    porId: (id: string) => `/categorias/${id}`,
    contagemMovimentacoes: (id: string) => `/categorias/${id}/contagem-movimentacoes`,
  },

  movimentacoes: {
    base: '/movimentacoes',
    porId: (id: string) => `/movimentacoes/${id}`,
  },

  relatorios: {
    resumo: '/relatorios/resumo',
    porCategoria: '/relatorios/por-categoria',
    evolucaoMensal: '/relatorios/evolucao-mensal',
    maiorLancamento: '/relatorios/maior-lancamento',
    congregacoes: '/relatorios/congregacoes',
  },

  igreja: {
    MINHA: '/igrejas/minha',
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
  },

  fotos: {
    UPLOAD: '/fotos',
    BY_ID: (id: string) => `/fotos/${id}`,
  },

  celulas: {
    LISTAR: '/celulas',
    CRIAR: '/celulas',
    BY_ID: (id: string) => `/celulas/${id}`,
    MEMBROS: (id: string) => `/celulas/${id}/membros`,
    MEMBRO: (id: string, membroId: string) => `/celulas/${id}/membros/${membroId}`,
    PAPEL: (id: string, membroId: string) => `/celulas/${id}/membros/${membroId}/papel`,
    CONVERTER: (id: string, visitanteId: string) => `/celulas/${id}/converter/${visitanteId}`,
  },
}