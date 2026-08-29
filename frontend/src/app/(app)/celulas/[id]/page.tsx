'use client'

import { use, useState } from 'react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { ChevronRight, UserPlus, UserX, Star, Pencil, Crown, ArrowLeftRight, TrendingUp, Grid3X3, Archive, ArrowLeft } from 'lucide-react'
import { useCelula } from '@/hooks/celula/useCelula'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import { visitanteService } from '@/services/visitante.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { ModalAdicionarMembro } from './ModalAdicionarMembro'
import { ModalConverterVisitante } from './ModalConverterVisitante'
import { ModalCelulaForm } from '../(lista)/ModalCelulaForm'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { iniciaisVisitante } from '@/lib/formats/visitanteFormat'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { DrawerDetalheVisitante } from '@/app/(app)/pessoas/visitantes/(detalhe)/DrawerDetalheVisitante'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { VisitanteForm, type VisitanteFormData } from '@/components/module/visitantes/VisitanteForm'
import { useVisitanteForm } from '@/hooks/visitante/useVisitanteForm'
import type { MembroCelulaResponse } from '@/types/celula.type'
import styles from './page.module.css'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'

export default function CelulaDetalhePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const searchParams = useSearchParams()
  const { data: celula, isLoading, isError, refetch } = useCelula(id)
  const queryClient = useQueryClient()
  const role = useAuthStore(s => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const isAdmin = podeGerenciarCelulas(role, capacidadesExtras)
  const podeGerenciarCelula = isAdmin || !!celula?.souLiderDestaCelula
  const { celula: rotuloCelula, concordar } = useRotulos()
  const [filtro, setFiltro] = useState<'TODOS' | 'PESSOA' | 'VISITANTE'>('TODOS')
  const [editando, setEditando] = useState(false)
  const [modalAdicionarAberto, setModalAdicionarAberto] = useState(false)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)
  const [visitanteDetalheId, setVisitanteDetalheId] = useState<string | null>(
    () => searchParams.get('visitante')
  )
  const [convertendoId, setConvertendoId] = useState<string | null>(null)
  const [cadastrarExterno, setCadastrarExterno] = useState(false)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)
  const externoSaida = useFecharAnimado(() => setCadastrarExterno(false), 260)

  const formExterno = useVisitanteForm({})

  // ids saindo — cada linha colapsa animada, independente. Pode remover vários seguidos.
  const [removendo, setRemovendo] = useState<Set<string>>(() => new Set())
  const semIdRemov = (s: Set<string>, mid: string) => {
    const n = new Set(s)
    n.delete(mid)
    return n
  }

  function handleRemoverMembro(membroId: string) {
    if (removendo.has(membroId)) return
    setRemovendo((s) => new Set(s).add(membroId))
    setTimeout(async () => {
      try {
        await celulaService.removerMembro(id, membroId)
        invalidarCache(queryClient, 'celula')
        // sem toast de sucesso: a linha colapsa animada, já é visível
        setTimeout(() => setRemovendo((s) => semIdRemov(s, membroId)), 350)
      } catch {
        notificar.erro('Erro ao remover membro.')
        setRemovendo((s) => semIdRemov(s, membroId))
      }
    }, 420)
  }

  async function handlePromover(membroId: string, papelAtual: 'LIDER' | 'MEMBRO') {
    try {
      const novoPapel = papelAtual === 'LIDER' ? 'MEMBRO' : 'LIDER'
      await celulaService.atualizarPapel(id, membroId, novoPapel)
      invalidarCache(queryClient, 'celula')
      // sem toast de sucesso: o badge de líder entra/sai animado, já é visível
    } catch {
      notificar.erro('Erro ao atualizar papel.')
    }
  }

  async function handleConverterVisitante(visitanteId: string) {
    setConvertendoId(visitanteId)
  }

  async function handleCriarExterno(data: VisitanteFormData) {
    try {
      const payload = {
        ...data,
        telefone: data.telefone?.replace(/\D/g, '') || undefined,
        sexo: (data.sexo && data.sexo !== '') ? data.sexo : undefined,
        estadoCivil: (data.estadoCivil && data.estadoCivil !== '') ? data.estadoCivil : undefined,
        dataNascimento: data.dataNascimento || undefined,
        temFilhos: data.temFilhos ?? false,
        quantidadeFilhos: data.temFilhos ? (data.quantidadeFilhos ?? undefined) : undefined,
        observacoes: data.observacoes || undefined,
        endereco: data.endereco ? {
          ...data.endereco,
          cep: data.endereco.cep?.replace(/\D/g, '') || undefined,
        } : undefined,
      }
      // sexo/estadoCivil já vêm validados pelo schema do form; o cast só estreita o tipo.
      const visitante = await visitanteService.criar(payload as Parameters<typeof visitanteService.criar>[0])
      await celulaService.adicionarMembro(id, { visitanteId: visitante.id })
      invalidarCache(queryClient, 'celula')
      queryClient.invalidateQueries({ queryKey: ['visitantes'] })
      notificar.sucesso(`Visitante cadastrado e adicionado à ${rotuloCelula.singular.toLowerCase()}.`)
      setCadastrarExterno(false)
      formExterno.reset()
    } catch { notificar.erro('Erro ao cadastrar.') }
  }

  function membrosFiltrados(): MembroCelulaResponse[] {
    if (!celula) return []
    if (filtro === 'PESSOA') return celula.membros.filter(m => m.tipo === 'PESSOA')
    if (filtro === 'VISITANTE') return celula.membros.filter(m => m.tipo === 'VISITANTE')
    return celula.membros
  }

  const totalMembros = celula?.membros.length ?? 0
  const totalPessoas = celula?.membros.filter(m => m.tipo === 'PESSOA').length ?? 0
  const totalVisitantes = celula?.membros.filter(m => m.tipo === 'VISITANTE').length ?? 0

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb}>
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/celulas" className={styles.breadcrumbLink}>{rotuloCelula.plural}</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{celula?.nome ?? '…'}</span>
      </nav>

      {celula?.arquivada && (
        <Link href="/celulas/arquivados" className={styles.avisoArquivada}>
          <ArrowLeft size={16} className={styles.avisoSeta} />
          <Archive size={16} />
          <span>{concordar(rotuloCelula.genero, 'este')} {rotuloCelula.singular.toLowerCase()} está {concordar(rotuloCelula.genero, 'arquivado')}. Toque para restaurá-{concordar(rotuloCelula.genero, 'lo')} na lista de {concordar(rotuloCelula.genero, 'arquivados')}.</span>
        </Link>
      )}

      {isLoading ? (
        <Skeleton width="100%" height="200px" radius="var(--radius-lg)" />
      ) : isError || !celula ? (
        <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão."
          aoTentarNovamente={() => refetch()} />
      ) : (
        <>
          <header className={styles.cabecalho}>
            <div className={styles.fotoDetalhe}>
              {celula.fotoId ? (
                <img src={urlFoto(celula.fotoId, 'DISPLAY')!} alt="" className={styles.fotoDetalheImg}
                  onClick={() => setFotoVisualizando(celula.fotoId)} />
              ) : (
                <div className={styles.fotoDetalheFallback}>
                  <Grid3X3 size={32} />
                </div>
              )}
            </div>
            <div>
              <div className={styles.tituloLinha}>
                <h1 className={styles.titulo}>{celula.nome}</h1>
                {podeGerenciarCelula && (
                <button className={styles.btnEditar} onClick={() => setEditando(true)}
                  title={`Editar ${rotuloCelula.singular.toLowerCase()}`}>
                  <Pencil size={16} />
                </button>
                )}
              </div>
              {(celula.diaSemana || celula.horario) && (
                <p className={styles.subtitulo}>
                  {[rotuloDiaSemana(celula.diaSemana), formatarHorario(celula.horario)].filter(Boolean).join(', ')}
                </p>
              )}
            </div>
            {podeGerenciarCelula && (
            <button className={styles.botaoPrimario} onClick={() => setModalAdicionarAberto(true)}>
              <UserPlus size={18} /> Adicionar
            </button>
            )}
          </header>

          <div className={styles.stats}>
            <div className={styles.statCard}>
              <p className={styles.statLabel}>Total</p>
              <p className={styles.statValor}>{totalMembros}</p>
            </div>
            <div className={styles.statCard}>
              <p className={styles.statLabel}>Pessoas DA IGREJA</p>
              <p className={styles.statValor}>{totalPessoas}</p>
            </div>
            <div className={styles.statCard}>
              <p className={styles.statLabel}>Visitantes</p>
              <p className={styles.statValor}>{totalVisitantes}</p>
            </div>
          </div>

          <div className={styles.filtros}>
            {['TODOS', 'PESSOA', 'VISITANTE'].map(f => (
              <button key={f} className={`${styles.filtroBtn} ${filtro === f ? styles.filtroAtivo : ''}`}
                onClick={() => setFiltro(f as typeof filtro)}>
                {f === 'TODOS' ? 'Todos' : f === 'PESSOA' ? 'Pessoas da Igreja' : 'Visitantes'}
              </button>
            ))}
          </div>

          <Transicao key={filtro} modo="fade" className={styles.lista}>
            {membrosFiltrados().map(m => {
              const podeGerenciar = podeGerenciarCelula

              const acoes: ItemAcao[] = []
              if (podeGerenciarCelula && m.tipo === 'PESSOA') {
                acoes.push({
                  label: m.papel === 'LIDER' ? 'Remover liderança' : 'Tornar líder',
                  icone: m.papel === 'LIDER' ? TrendingUp : Crown,
                  onClick: () => handlePromover(m.id, m.papel),
                })
              }
              if (m.tipo === 'VISITANTE' && podeGerenciar) {
                acoes.push({
                  label: 'Tornar membro/congregante',
                  icone: ArrowLeftRight,
                  onClick: () => handleConverterVisitante(m.visitanteId!),
                })
              }
              if (podeGerenciar) {
                acoes.push({
                  label: 'Remover',
                  icone: UserX,
                  onClick: () => handleRemoverMembro(m.id),
                  perigo: true,
                  separadorAntes: acoes.length > 0,
                })
              }

              return (
                <div key={m.id}
                  className={clsx(styles.membro, m.tipo === 'VISITANTE' && styles.membroVisitante, removendo.has(m.id) && styles.membroSaindo)}
                  onClick={() => {
                    if (m.tipo === 'PESSOA' && m.pessoaId) setPessoaDetalheId(m.pessoaId)
                    else if (m.visitanteId) setVisitanteDetalheId(m.visitanteId)
                  }}
                >
                  <div className={styles.membroInfo}>
                    {m.fotoId ? (
                      <img src={urlFoto(m.fotoId, 'THUMB')!} alt="" className={styles.membroAvatar}
                        onClick={(e) => { e.stopPropagation(); setFotoVisualizando(m.fotoId) }} />
                    ) : (
                      <span className={styles.membroAvatar}>
                        {m.tipo === 'PESSOA' ? iniciais(m.nome) : iniciaisVisitante(m.nome)}
                      </span>
                    )}
                    <span className={styles.membroNome}>
                      {m.nome}
                      {m.papel === 'LIDER' && <Star size={14} className={styles.estrela} />}
                    </span>
                  </div>
                  <div className={styles.membroAcoes}>
                    {m.papel === 'LIDER' && (
                      <span className={styles.badgeLider}><Crown size={12} /> Líder</span>
                    )}
                    <span className={`${styles.membroBadge} ${m.tipo === 'PESSOA' ? styles.badgePessoa : styles.badgeVisitante}`}>
                      {m.tipo === 'PESSOA' ? 'Pessoa da igreja' : 'Visitante'}
                    </span>
                    {acoes.length > 0 && (
                      <span onClick={e => e.stopPropagation()}>
                        <MenuAcoes itens={acoes} />
                      </span>
                    )}
                  </div>
                </div>
              )
            })}
          </Transicao>
        </>
      )}

      {editando && celula && (
        <ModalCelulaForm celula={celula} onClose={() => setEditando(false)} />
      )}

      {modalAdicionarAberto && celula && (
        <ModalAdicionarMembro
          celulaId={id}
          membrosPessoaIds={new Set(celula.membros.filter(m => m.pessoaId).map(m => m.pessoaId!))}
          membrosVisitanteIds={new Set(celula.membros.filter(m => m.visitanteId).map(m => m.visitanteId!))}
          onClose={() => setModalAdicionarAberto(false)}
          onCadastrarExterno={() => {
            setModalAdicionarAberto(false)
            setCadastrarExterno(true)
          }}
        />
      )}
      {pessoaDetalheId && (
        <DrawerDetalhePessoa pessoaId={pessoaDetalheId} onClose={() => setPessoaDetalheId(null)} />
      )}
      {visitanteDetalheId && (
        <DrawerDetalheVisitante visitanteId={visitanteDetalheId} onClose={() => setVisitanteDetalheId(null)} />
      )}
      {convertendoId && celula && (
        <ModalConverterVisitante
          celulaId={id}
          visitanteId={convertendoId}
          visitanteNome={celula.membros.find(m => m.visitanteId === convertendoId)?.nome ?? ''}
          onClose={() => setConvertendoId(null)}
        />
      )}
      {cadastrarExterno && (
        <div className={clsx(styles.modalOverlay, externoSaida.saindo && styles.modalSaindo)} onMouseDown={externoSaida.fechar}>
          <div className={styles.modalLargo} onMouseDown={e => e.stopPropagation()}>
            <span className={styles.modalGrabber} aria-hidden="true" />
            <button className={styles.modalClose} onClick={externoSaida.fechar} aria-label="Fechar">✕</button>
            <h2 className={styles.modalTitulo}>Cadastrar Visitante</h2>
            <VisitanteForm
              {...formExterno}
              emModal
              onCancel={externoSaida.fechar}
              onSubmit={async (data) => {
                await handleCriarExterno(data)
              }}
            />
          </div>
        </div>
      )}
      {fotoVisualizando && (
        <VisualizadorFoto fotoId={fotoVisualizando} descricao="Foto de perfil" onClose={() => setFotoVisualizando(null)} />
      )}
    </div>
  )
}
