'use client'

import { useState } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { useParams, useRouter } from 'next/navigation'
import { ChevronRight, Users, Ticket, Armchair, UserPlus, ArrowLeft, CheckCircle2, Check, ListChecks, X, Archive, XCircle, ClipboardList } from 'lucide-react'
import { useDebounce } from '@/hooks/useDebounce'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useEvento } from '@/hooks/evento/useEvento'
import { useListaInscritos } from '@/hooks/inscricao/useListaInscritos'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useRemoverConvidado } from '@/hooks/inscricao/useRemoverConvidado'
import { useMarcarTodosPresentes } from '@/hooks/inscricao/useMarcarTodosPresentes'
import { useDesmarcarTodosPresentes } from '@/hooks/inscricao/useDesmarcarTodosPresentes'
import { useMarcarPresencaInscricao } from '@/hooks/inscricao/useMarcarPresencaInscricao'
import { useMarcarPresencaAcompanhante } from '@/hooks/inscricao/useMarcarPresencaAcompanhante'
import { useMarcarPresencaSelecionados, type ItemSelecionado } from '@/hooks/inscricao/useMarcarPresencaSelecionados'
import { useRelatorioEvento } from '@/hooks/evento/useRelatorioEvento'
import { ModalInscreverAlguem } from '@/components/module/eventos/ModalInscreverAlguem'
import { CardsRelatorioEvento } from '@/components/module/eventos/CardsRelatorioEvento'
import { ConfirmarCancelamentoInscricao } from '@/components/module/eventos/ConfirmarCancelamentoInscricao'
import { PendenciaCamposBadge } from '@/components/module/eventos/PendenciaCamposBadge'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { DrawerDetalhePessoa, type ContextoExtraDrawer } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { SkeletonInscritos } from './SkeletonInscritos'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import { podeVerListaCompletaDeInscritos } from '@/lib/permissoes'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { formatarData } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import type { InscritoResponse } from '@/types/inscricao.type'
import styles from './inscritos.module.css'

export default function InscritosPage() {
  const params = useParams()
  const eventoId = params.id as string
  const router = useRouter()

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeVerListaCompletaDeInscritos(role)

  const { data: evento } = useEvento(eventoId)
  const { data: camposPersonalizados } = useCamposPersonalizados(eventoId)
  const camposObrigatorios = (camposPersonalizados ?? []).filter((c) => c.obrigatorio)

  const [busca, setBusca] = useState('')
  const buscaDebounced = useDebounce(busca)
  const [pagina, setPagina] = useState(0)

  const { data: lista, isPending, isError, refetch } = useListaInscritos(
    eventoId, autorizado, buscaDebounced, pagina,
  )

  const [modalInscreverAberto, setModalInscreverAberto] = useState(false)
  const [inscritoCancelando, setInscritoCancelando] = useState<InscritoResponse | null>(null)
  const [convidadoCancelando, setConvidadoCancelando] = useState<{ id: string; nome: string } | null>(null)

  const cancelarInscricao = useCancelarInscricao()
  const removerConvidado = useRemoverConvidado()
  const marcarTodos = useMarcarTodosPresentes(eventoId)
  const desmarcarTodos = useDesmarcarTodosPresentes(eventoId)
  const marcarPresencaInscricao = useMarcarPresencaInscricao(eventoId)
  const marcarPresencaAcompanhante = useMarcarPresencaAcompanhante(eventoId)
  const marcarPresencaSelecionados = useMarcarPresencaSelecionados(eventoId)
  const [confirmarMarcarTodos, setConfirmarMarcarTodos] = useState(false)
  const [confirmarDesmarcarTodos, setConfirmarDesmarcarTodos] = useState(false)

  // Detalhe do participante: abre o cadastro (mesmo drawer da tela de Pessoas) com um
  // bloco extra de contexto do evento no topo — não existe pra convidado (não tem cadastro).
  const [pessoaDetalhe, setPessoaDetalhe] = useState<{ pessoaId: string; contexto: ContextoExtraDrawer } | null>(null)
  // Convidado sem cadastro: não tem drawer de pessoa pra abrir, mas ainda merece um jeito
  // de ver os detalhes (quem convidou, telefone) sem precisar cancelar pra descobrir.
  const [convidadoDetalhe, setConvidadoDetalhe] = useState<{
    nome: string; telefone: string | null; convidadoPorNome: string | null; inscritoEm: string
  } | null>(null)

  function abrirDetalheConvidado(nome: string, telefone: string | null, convidadoPorNome: string | null, inscritoEm: string) {
    setConvidadoDetalhe({ nome, telefone, convidadoPorNome, inscritoEm })
  }

  function abrirDetalhe(inscrito: InscritoResponse) {
    if (!inscrito.pessoaId || inscrito.pessoaRemovida) return
    const linhas = [
      `Inscrito em ${formatarData(inscrito.inscritoEm)}`,
      inscrito.inscritoPorUsuarioId === null
        ? 'Inscrito por ele mesmo'
        : `Inscrito por ${inscrito.inscritoPorNome ?? 'cadastro removido'}`,
    ]
    if (inscrito.acompanhantes.length > 0) {
      linhas.push(`${inscrito.acompanhantes.length} convidado(s): ${inscrito.acompanhantes.map((a) => a.nome).join(', ')}`)
    }
    if (mostraPresenca) {
      linhas.push(inscrito.compareceu ? 'Presença confirmada neste evento' : 'Ainda não marcado presente neste evento')
    }
    setPessoaDetalhe({
      pessoaId: inscrito.pessoaId,
      contexto: { titulo: 'Neste evento', icone: ClipboardList, linhas },
    })
  }

  // Modo seleção: checkboxes para marcar subconjunto. Chave composta (tipo:id) porque
  // inscrito e acompanhante têm ids de espaços distintos.
  const [modoSelecao, setModoSelecao] = useState(false)
  const [selecionados, setSelecionados] = useState<Set<string>>(new Set())

  function chaveSelecao(item: ItemSelecionado) {
    return `${item.tipo}:${item.id}`
  }

  function alternarSelecao(item: ItemSelecionado) {
    const chave = chaveSelecao(item)
    setSelecionados((atual) => {
      const proximo = new Set(atual)
      if (proximo.has(chave)) proximo.delete(chave)
      else proximo.add(chave)
      return proximo
    })
  }

  function aoTeclarLinha(e: React.KeyboardEvent, acao: () => void) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      acao()
    }
  }

  function sairDoModoSelecao() {
    setModoSelecao(false)
    setSelecionados(new Set())
  }

  function aoMarcarSelecionados() {
    const itens: ItemSelecionado[] = Array.from(selecionados).map((chave) => {
      const [tipo, id] = chave.split(':') as [ItemSelecionado['tipo'], string]
      return { tipo, id }
    })
    marcarPresencaSelecionados.mutate(itens, { onSuccess: sairDoModoSelecao })
  }

  function aoDigitarBusca(valor: string) {
    setBusca(valor)
    setPagina(0)
    setSelecionados(new Set())
  }

  function irParaPagina(proxima: number) {
    setPagina(proxima)
    setSelecionados(new Set())
  }

  // Presença só faz sentido em evento que já começou — backend recusa com 409
  const mostraPresenca = autorizado && !!evento?.controlaPresenca && evento!.situacao !== 'AGENDADO'

  // Relatório busca sempre que há inscrições; cards de comparecimento são condicionados em CardsRelatorioEvento
  const { data: relatorio } = useRelatorioEvento(eventoId, autorizado && !!evento?.requerInscricao)

  // "Marcar todos vieram" vira "Desmarcar todos" quando não sobra ninguém pra marcar — permite
  // desfazer um "marcar todos" clicado sem querer, ou reiniciar a contagem do zero.
  const todosPresentes = !!relatorio?.compareceram
    && relatorio.inscritos.pessoas > 0
    && relatorio.compareceram.pessoas === relatorio.inscritos.pessoas
    && relatorio.compareceram.convidados === relatorio.inscritos.convidados

  // Fora de AGENDADO o backend recusa cancelar, mesmo para ADMIN/LÍDER
  const podeCancelar = evento ? podeCancelarInscricao(evento.situacao) : true
  // ENCERRADO mostra "Participou"; EM_ANDAMENTO mostra "Em andamento"
  const textoBloqueado = evento?.situacao === 'ENCERRADO' ? 'Participou' : 'Em andamento'

  const igrejasDistintas = new Set(
    (lista?.inscritos.content ?? []).map((i) => i.igrejaDaPessoa.id),
  )
  const mostrarIgreja = !evento?.restritoPropriaIgreja || igrejasDistintas.size > 1

  if (!hidratado) {
    return <div className={styles.pagina} />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  function aoConfirmarCancelamento() {
    if (!inscritoCancelando) return
    cancelarInscricao.mutate(inscritoCancelando.id, {
      onSuccess: () => setInscritoCancelando(null),
    })
  }

  function aoConfirmarRemocaoConvidado() {
    if (!convidadoCancelando) return
    removerConvidado.mutate(convidadoCancelando.id, {
      onSuccess: () => setConvidadoCancelando(null),
    })
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Inscritos</span>
      </nav>

      {evento?.arquivado && (
        <Link href="/eventos/arquivados" className={styles.avisoArquivado}>
          <Archive size={16} />
          <span>Este evento está arquivado. Toque para restaurá-lo na lista de arquivados.</span>
        </Link>
      )}

      <header className={styles.cabecalho}>
        <div className={styles.cabecalhoTextos}>
          <button
            type="button"
            className={styles.voltar}
            onClick={() => router.back()}
          >
            <ArrowLeft size={16} aria-hidden="true" />
            Voltar
          </button>
          {evento && <span className={styles.eventoTitulo}>{evento.titulo}</span>}
          <h1 className={styles.titulo}>Lista de Inscritos</h1>
        </div>
        <button
          type="button"
          className={styles.botaoPrimario}
          onClick={() => setModalInscreverAberto(true)}
        >
          <UserPlus size={18} />
          Nova Inscrição
        </button>
      </header>

      {isPending ? (
        <SkeletonInscritos />
      ) : isError || !lista ? (
        <div className={styles.painel}>
          <EstadoErro
            titulo="Não foi possível carregar os inscritos"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        </div>
      ) : (
        <>
          <div className={styles.stats}>
            <div className={styles.statCard}>
              <span className={styles.statIcone}><Users size={18} /></span>
              <div>
                <p className={styles.statValor}>{lista.totalPessoas}</p>
                <p className={styles.statLabel}>Total de pessoas</p>
              </div>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statIcone}><Ticket size={18} /></span>
              <div>
                <p className={styles.statValor}>{lista.vagas ?? 'Sem limite'}</p>
                <p className={styles.statLabel}>Vagas</p>
              </div>
            </div>
            {lista.vagas != null && (
              <div className={styles.statCard}>
                <span className={styles.statIcone}><Armchair size={18} /></span>
                <div>
                  <p className={styles.statValor}>{lista.vagasRestantes}</p>
                  <p className={styles.statLabel}>Vagas restantes</p>
                </div>
              </div>
            )}
          </div>

          {lista.totalPessoas > 0 && (
            <div className={styles.buscaLinha}>
              <input
                type="text"
                className={styles.inputBusca}
                placeholder="Buscar por nome…"
                value={busca}
                onChange={(e) => aoDigitarBusca(e.target.value)}
                aria-label="Buscar inscrito por nome"
              />
              {mostraPresenca && (
                <button
                  type="button"
                  className={styles.botaoSelecionar}
                  onClick={() => (modoSelecao ? sairDoModoSelecao() : setModoSelecao(true))}
                >
                  {modoSelecao ? <X size={16} aria-hidden="true" /> : <ListChecks size={16} aria-hidden="true" />}
                  {modoSelecao ? 'Cancelar seleção' : 'Selecionar'}
                </button>
              )}
              {mostraPresenca && lista.totalPessoas > 0 && (
                <button
                  type="button"
                  className={styles.botaoMarcarTodos}
                  onClick={() => (todosPresentes ? setConfirmarDesmarcarTodos(true) : setConfirmarMarcarTodos(true))}
                  disabled={marcarTodos.isPending || desmarcarTodos.isPending}
                >
                  {todosPresentes
                    ? <><XCircle size={16} aria-hidden="true" /> Desmarcar todos</>
                    : <><CheckCircle2 size={16} aria-hidden="true" /> Marcar todos vieram</>}
                </button>
              )}
            </div>
          )}

          <div className={styles.painel}>
            {lista.inscritos.content.length === 0 ? (
              <EstadoVazio
                icone={Users}
                titulo={busca ? 'Nenhum inscrito encontrado' : 'Ninguém se inscreveu ainda'}
                mensagem={busca ? `Nenhum nome bate com "${busca}".` : undefined}
                acaoPrimaria={busca ? undefined : { label: 'Nova Inscrição', onClick: () => setModalInscreverAberto(true) }}
              />
            ) : (
              <>
                <div className={`${styles.tabelaHeader} ${mostraPresenca ? styles.tabelaHeaderComPresenca : ''}`}>
                  <span className={styles.colParticipante}>PARTICIPANTE</span>
                  <span className={styles.colData}>DATA</span>
                  <span className={styles.colInscritoPor}>INSCRITO POR</span>
                  {mostraPresenca && <span className={styles.colPresenca}>PRESENÇA</span>}
                  <span className={styles.colAcoes}>AÇÕES</span>
                </div>

                <div className={styles.linhas}>
                  {lista.inscritos.content.map((inscrito) => {
                    const ehConvidadoSemCadastro = !inscrito.pessoaId && !inscrito.pessoaRemovida
                    const clicavel = modoSelecao || !!inscrito.pessoaId || ehConvidadoSemCadastro
                    const aoClicarLinha = () => {
                      if (modoSelecao) alternarSelecao({ tipo: 'inscricao', id: inscrito.id })
                      else if (ehConvidadoSemCadastro) {
                        abrirDetalheConvidado(inscrito.nome, inscrito.telefoneConvidado, inscrito.convidadoPorNome, inscrito.inscritoEm)
                      } else abrirDetalhe(inscrito)
                    }
                    return (
                    <div key={inscrito.id} className={styles.grupo}>
                      <div
                        className={`${styles.linha} ${mostraPresenca ? styles.linhaComPresenca : ''} ${clicavel ? styles.linhaClicavel : ''}`}
                        onClick={clicavel ? aoClicarLinha : undefined}
                        onKeyDown={clicavel ? (e) => aoTeclarLinha(e, aoClicarLinha) : undefined}
                        role={clicavel ? 'button' : undefined}
                        tabIndex={clicavel ? 0 : undefined}
                      >
                        <div className={styles.colParticipante}>
                          <span className={styles.avatar}>
                            {urlFoto(inscrito.fotoId, 'THUMB') ? (
                              <Image src={urlFoto(inscrito.fotoId, 'THUMB')!} alt="" width={36} height={36} unoptimized className={styles.avatarFoto} />
                            ) : (
                              iniciais(inscrito.nome)
                            )}
                          </span>
                          <span className={styles.colParticipanteTextos}>
                            <span className={styles.nome}>{inscrito.nome}</span>
                            {inscrito.convidadoPorNome && (
                              <span className={styles.subtitulo}>Convidado por {inscrito.convidadoPorNome}</span>
                            )}
                          </span>
                          {!inscrito.pessoaId && !inscrito.pessoaRemovida && (
                            <span className={styles.pillConvidado}>Convidado</span>
                          )}
                          {mostrarIgreja && (
                            <span className={styles.pillIgreja}>
                              {inscrito.igrejaDaPessoa.sigla ?? inscrito.igrejaDaPessoa.nome}
                            </span>
                          )}
                          {camposObrigatorios.length > 0 && (
                            <PendenciaCamposBadge inscricaoId={inscrito.id} camposObrigatorios={camposObrigatorios} />
                          )}
                        </div>
                        <div className={styles.colData}>{formatarData(inscrito.inscritoEm)}</div>
                        <div className={styles.colInscritoPor}>
                          {inscrito.inscritoPorUsuarioId === null ? (
                            <span className={styles.textoMuted}>Ele mesmo</span>
                          ) : inscrito.inscritoPorNome ? (
                            <span className={styles.inscritoPor}>
                              <span className={styles.avatarInscritoPor}>
                                {urlFoto(inscrito.inscritoPorFotoId, 'THUMB') ? (
                                  <Image src={urlFoto(inscrito.inscritoPorFotoId, 'THUMB')!} alt="" width={20} height={20} unoptimized className={styles.avatarFoto} />
                                ) : (
                                  iniciais(inscrito.inscritoPorNome)
                                )}
                              </span>
                              <span>{inscrito.inscritoPorNome}</span>
                            </span>
                          ) : (
                            <span className={styles.textoMuted}>Cadastro removido</span>
                          )}
                        </div>
                        {mostraPresenca && (
                          <div className={styles.colPresenca}>
                            {modoSelecao ? (
                              <input
                                type="checkbox"
                                className={styles.checkboxSelecao}
                                checked={selecionados.has(chaveSelecao({ tipo: 'inscricao', id: inscrito.id }))}
                                aria-label={`Selecionar ${inscrito.nome}`}
                                onChange={() => alternarSelecao({ tipo: 'inscricao', id: inscrito.id })}
                                onClick={(e) => e.stopPropagation()}
                              />
                            ) : (
                              <button
                                type="button"
                                className={`${styles.botaoPresenca} ${inscrito.compareceu ? styles.presente : ''}`}
                                aria-pressed={inscrito.compareceu}
                                disabled={
                                  marcarPresencaInscricao.isPending
                                  && marcarPresencaInscricao.variables?.inscricaoId === inscrito.id
                                }
                                onClick={(e) => {
                                  e.stopPropagation()
                                  marcarPresencaInscricao.mutate({
                                    inscricaoId: inscrito.id,
                                    compareceu: !inscrito.compareceu,
                                  })
                                }}
                              >
                                {inscrito.compareceu
                                  ? <><Check size={14} aria-hidden="true" /> Presente</>
                                  : 'Marcar presença'}
                              </button>
                            )}
                          </div>
                        )}
                        <div className={styles.colAcoes}>
                          {podeCancelar ? (
                            <button
                              type="button"
                              className={styles.btnCancelar}
                              onClick={(e) => { e.stopPropagation(); setInscritoCancelando(inscrito) }}
                            >
                              Cancelar
                            </button>
                          ) : (
                            <span className={styles.textoMuted}>{textoBloqueado}</span>
                          )}
                        </div>
                      </div>

                      {inscrito.acompanhantes.map((convidado) => {
                        const aoClicarConvidado = () => (
                          modoSelecao
                            ? alternarSelecao({ tipo: 'acompanhante', id: convidado.id })
                            : abrirDetalheConvidado(convidado.nome, convidado.telefone, inscrito.nome, inscrito.inscritoEm)
                        )
                        return (
                        <div
                          key={convidado.id}
                          className={`${styles.linhaConvidado} ${mostraPresenca ? styles.linhaComPresenca : ''} ${styles.linhaClicavel}`}
                          onClick={aoClicarConvidado}
                          onKeyDown={(e) => aoTeclarLinha(e, aoClicarConvidado)}
                          role="button"
                          tabIndex={0}
                        >
                          <span className={styles.conector} aria-hidden="true" />
                          <div className={styles.colParticipante}>
                            <span className={styles.avatarConvidado}>{iniciais(convidado.nome)}</span>
                            <span className={styles.colParticipanteTextos}>
                              <span className={styles.nome}>{convidado.nome}</span>
                              <span className={styles.subtitulo}>Convidado por {inscrito.nome}</span>
                            </span>
                            <span className={styles.pillConvidado}>Convidado</span>
                          </div>
                          <div className={styles.colData} />
                          <div className={styles.colInscritoPor} />
                          {mostraPresenca && (
                            <div className={styles.colPresenca}>
                              {modoSelecao ? (
                                <input
                                  type="checkbox"
                                  className={styles.checkboxSelecao}
                                  checked={selecionados.has(chaveSelecao({ tipo: 'acompanhante', id: convidado.id }))}
                                  aria-label={`Selecionar ${convidado.nome}`}
                                  onChange={() => alternarSelecao({ tipo: 'acompanhante', id: convidado.id })}
                                  onClick={(e) => e.stopPropagation()}
                                />
                              ) : (
                                <button
                                  type="button"
                                  className={`${styles.botaoPresenca} ${convidado.compareceu ? styles.presente : ''}`}
                                  aria-pressed={convidado.compareceu}
                                  disabled={
                                    marcarPresencaAcompanhante.isPending
                                    && marcarPresencaAcompanhante.variables?.acompanhanteId === convidado.id
                                  }
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    marcarPresencaAcompanhante.mutate({
                                      acompanhanteId: convidado.id,
                                      compareceu: !convidado.compareceu,
                                    })
                                  }}
                                >
                                  {convidado.compareceu
                                    ? <><Check size={14} aria-hidden="true" /> Presente</>
                                    : 'Marcar presença'}
                                </button>
                              )}
                            </div>
                          )}
                          <div className={styles.colAcoes}>
                            {podeCancelar ? (
                              <button
                                type="button"
                                className={styles.btnCancelar}
                                onClick={(e) => { e.stopPropagation(); setConvidadoCancelando({ id: convidado.id, nome: convidado.nome }) }}
                              >
                                Remover
                              </button>
                            ) : (
                              <span className={styles.textoMuted}>{textoBloqueado}</span>
                            )}
                          </div>
                        </div>
                        )
                      })}
                    </div>
                  )})}
                </div>

                {lista.inscritos.totalPages > 1 && (
                  <footer className={styles.rodape}>
                    <span className={styles.contagem}>
                      Exibindo {lista.inscritos.content.length} de {lista.inscritos.totalElements} inscritos
                    </span>
                    <div className={styles.paginacao}>
                      <button
                        type="button"
                        onClick={() => irParaPagina(Math.max(0, pagina - 1))}
                        disabled={pagina === 0}
                        className={styles.botaoPagina}
                      >‹</button>
                      <span className={styles.infoPagina}>
                        Página {pagina + 1} de {lista.inscritos.totalPages}
                      </span>
                      <button
                        type="button"
                        onClick={() => irParaPagina(pagina + 1)}
                        disabled={pagina + 1 >= lista.inscritos.totalPages}
                        className={styles.botaoPagina}
                      >›</button>
                    </div>
                  </footer>
                )}
              </>
            )}
          </div>

          {modoSelecao && selecionados.size > 0 && (
            <div className={styles.barraSelecao}>
              <span className={styles.barraSelecaoTexto}>
                {selecionados.size === 1 ? '1 selecionado' : `${selecionados.size} selecionados`}
              </span>
              <div className={styles.barraSelecaoAcoes}>
                <button
                  type="button"
                  className={styles.btnCancelar}
                  onClick={sairDoModoSelecao}
                  disabled={marcarPresencaSelecionados.isPending}
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  className={styles.botaoMarcarTodos}
                  onClick={aoMarcarSelecionados}
                  disabled={marcarPresencaSelecionados.isPending}
                >
                  <CheckCircle2 size={16} aria-hidden="true" />
                  {marcarPresencaSelecionados.isPending
                    ? 'Marcando…'
                    : `Marcar presença (${selecionados.size})`}
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {relatorio && <CardsRelatorioEvento relatorio={relatorio} />}

      {modalInscreverAberto && evento && (
        <ModalInscreverAlguem
          eventoId={eventoId}
          tituloEvento={evento.titulo}
          exclusivoMembros={evento.exclusivoMembros}
          preco={evento.preco}
          onClose={() => setModalInscreverAberto(false)}
        />
      )}

      {inscritoCancelando && (
        <ConfirmarCancelamentoInscricao
          nome={inscritoCancelando.nome}
          proprio={false}
          quantidadeConvidados={inscritoCancelando.acompanhantes.length}
          isLoading={cancelarInscricao.isPending}
          onConfirmar={aoConfirmarCancelamento}
          onClose={() => setInscritoCancelando(null)}
        />
      )}

      {convidadoCancelando && (
        <div className={styles.confirmInlineOverlay} onMouseDown={() => setConvidadoCancelando(null)}>
          <div className={styles.confirmInline} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <p className={styles.confirmInlineTexto}>
              Remover <strong>{convidadoCancelando.nome}</strong> da lista de convidados?
            </p>
            <div className={styles.confirmInlineAcoes}>
              <button
                type="button"
                className={styles.btnCancelar}
                onClick={() => setConvidadoCancelando(null)}
                disabled={removerConvidado.isPending}
              >
                Não
              </button>
              <button
                type="button"
                className={styles.btnConfirmarInline}
                onClick={aoConfirmarRemocaoConvidado}
                disabled={removerConvidado.isPending}
              >
                {removerConvidado.isPending ? 'Removendo…' : 'Sim, remover'}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmarMarcarTodos && (
        <ModalConfirmacao
          titulo="Marcar todos como presentes?"
          mensagem="Todo inscrito confirmado e seus convidados serão marcados como presentes. Você pode corrigir exceções (quem não veio) depois, um por um."
          textoConfirmar="Marcar todos"
          isLoading={marcarTodos.isPending}
          onConfirmar={() => marcarTodos.mutate(undefined, { onSuccess: () => setConfirmarMarcarTodos(false) })}
          onClose={() => setConfirmarMarcarTodos(false)}
        />
      )}

      {confirmarDesmarcarTodos && (
        <ModalConfirmacao
          titulo="Desmarcar presença de todos?"
          mensagem="Todo inscrito e seus convidados voltam a ficar sem presença marcada. Use se marcou todos por engano ou quer reiniciar a contagem."
          textoConfirmar="Desmarcar todos"
          isLoading={desmarcarTodos.isPending}
          onConfirmar={() => desmarcarTodos.mutate(undefined, { onSuccess: () => setConfirmarDesmarcarTodos(false) })}
          onClose={() => setConfirmarDesmarcarTodos(false)}
        />
      )}

      {pessoaDetalhe && (
        <DrawerDetalhePessoa
          pessoaId={pessoaDetalhe.pessoaId}
          contextoExtra={pessoaDetalhe.contexto}
          onClose={() => setPessoaDetalhe(null)}
        />
      )}

      {/* Convidado sem cadastro não tem drawer de pessoa (não existe Pessoa pra abrir) —
          um resumo simples, só leitura, é o suficiente aqui. */}
      {convidadoDetalhe && (
        <div className={styles.confirmInlineOverlay} onMouseDown={() => setConvidadoDetalhe(null)}>
          <div className={styles.confirmInline} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className={styles.detalheConvidadoHeader}>
              <span className={styles.avatarConvidado}>{iniciais(convidadoDetalhe.nome)}</span>
              <div>
                <p className={styles.detalheConvidadoNome}>{convidadoDetalhe.nome}</p>
                <span className={styles.pillConvidado}>Convidado</span>
              </div>
            </div>
            <ul className={styles.detalheConvidadoLista}>
              <li>Inscrito em {formatarData(convidadoDetalhe.inscritoEm)}</li>
              <li>{convidadoDetalhe.telefone ? `Telefone: ${convidadoDetalhe.telefone}` : 'Sem telefone informado'}</li>
              {convidadoDetalhe.convidadoPorNome && <li>Convidado por {convidadoDetalhe.convidadoPorNome}</li>}
            </ul>
            <button type="button" className={styles.btnCancelar} onClick={() => setConvidadoDetalhe(null)}>
              Fechar
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
