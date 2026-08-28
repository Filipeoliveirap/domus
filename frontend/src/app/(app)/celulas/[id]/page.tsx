'use client'

import { use, useState, useMemo } from 'react'
import { useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { ChevronRight, UserPlus, UserX, Star, Pencil, Crown, UserMinus, ArrowLeftRight, TrendingUp, Grid3X3, X, Archive, ArrowLeft } from 'lucide-react'
import { useCelula } from '@/hooks/celula/useCelula'
import { useCelulaForm } from '@/hooks/celula/useCelulaForm'
import { useAtualizarFotoCelula } from '@/hooks/celula/useAtualizarFotoCelula'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import { visitanteService } from '@/services/visitante.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { Button } from '@/components/common/button/Button'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { ModalAdicionarMembro } from './ModalAdicionarMembro'
import { ModalConverterVisitante } from './ModalConverterVisitante'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { iniciaisVisitante } from '@/lib/formats/visitanteFormat'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { DrawerDetalheVisitante } from '@/app/(app)/pessoas/visitantes/(detalhe)/DrawerDetalheVisitante'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { VisitanteForm } from '@/components/module/visitantes/VisitanteForm'
import { useVisitanteForm } from '@/hooks/visitante/useVisitanteForm'
import type { MembroCelulaResponse } from '@/types/celula.type'
import styles from './page.module.css'

const DIA_OPTIONS = [
  { value: '', label: 'Sem dia fixo' },
  { value: 'SEGUNDA', label: 'Segunda' },
  { value: 'TERCA', label: 'Terça' },
  { value: 'QUARTA', label: 'Quarta' },
  { value: 'QUINTA', label: 'Quinta' },
  { value: 'SEXTA', label: 'Sexta' },
  { value: 'SABADO', label: 'Sábado' },
  { value: 'DOMINGO', label: 'Domingo' },
]

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
  const [fotoId, setFotoId] = useState<string | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)

  const formExterno = useVisitanteForm({})

  const celulaInicial = useMemo(() => celula ? {
    id: celula.id,
    nome: celula.nome,
    diaSemana: celula.diaSemana,
    horario: celula.horario,
    fotoId: celula.fotoId,
    lideres: [] as string[],
    totalMembros: celula.membros.length,
    souLiderDestaCelula: celula.souLiderDestaCelula,
    temVinculo: celula.membros.length > 0,
  } : undefined, [celula?.id, celula?.nome, celula?.diaSemana, celula?.horario, celula?.fotoId, celula?.souLiderDestaCelula, celula?.membros.length])

  const form = useCelulaForm({ celulaId: id, celulaInicial })
  const atualizarFoto = useAtualizarFotoCelula(id)
  const { register, handleSubmit, setValue, watch, formState: { errors }, isLoading: salvando, erroGeral } = form
  const horarioValue = watch('horario') as string ?? ''

  async function handleRemoverMembro(membroId: string) {
    try {
      await celulaService.removerMembro(id, membroId)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso('Membro removido.')
    } catch {
      notificar.erro('Erro ao remover membro.')
    }
  }

  async function handlePromover(membroId: string, papelAtual: 'LIDER' | 'MEMBRO') {
    try {
      const novoPapel = papelAtual === 'LIDER' ? 'MEMBRO' : 'LIDER'
      await celulaService.atualizarPapel(id, membroId, novoPapel)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso('Papel atualizado.')
    } catch {
      notificar.erro('Erro ao atualizar papel.')
    }
  }

  async function handleConverterVisitante(visitanteId: string) {
    setConvertendoId(visitanteId)
  }

  async function handleCriarExterno(data: any) {
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
      const visitante = await visitanteService.criar(payload)
      await celulaService.adicionarMembro(id, { visitanteId: visitante.id })
      invalidarCache(queryClient, 'celula')
      queryClient.invalidateQueries({ queryKey: ['visitantes'] })
      notificar.sucesso(`Visitante cadastrado e adicionado à ${rotuloCelula.singular.toLowerCase()}.`)
      setCadastrarExterno(false)
      formExterno.reset()
    } catch { notificar.erro('Erro ao cadastrar.') }
  }

  async function onSalvarEdicao() {
    const data = form.getValues()
    try {
      await celulaService.atualizar(id, {
        nome: data.nome,
        diaSemana: (data.diaSemana || undefined) as 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO',
        horario: data.horario ? data.horario + ':00' : undefined,
        fotoId: fotoId ?? undefined,
      })
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${rotuloCelula.singular} atualizada.`)
      setEditando(false)
    } catch {
      notificar.erro('Erro ao atualizar.')
    }
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
                <button className={styles.btnEditar} onClick={() => {
                  if (celula) form.reset({
                    nome: celula.nome,
                    diaSemana: celula.diaSemana ?? '',
                    horario: celula.horario ? celula.horario.slice(0, 5) : '',
                  })
                  setFotoId(celula?.fotoId ?? null)
                  setEditando(true)
                }} title={`Editar ${rotuloCelula.singular.toLowerCase()}`}>
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

          <div className={styles.lista}>
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
                  className={`${styles.membro} ${m.tipo === 'VISITANTE' ? styles.membroVisitante : ''}`}
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
          </div>
        </>
      )}

      {editando && (
        <div className={styles.modalOverlay} onMouseDown={() => setEditando(false)}>
          <div className={styles.modal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.modalClose} onClick={() => setEditando(false)}>✕</button>
            <h2 className={styles.modalTitulo}>Editar {rotuloCelula.singular}</h2>
            <form onSubmit={handleSubmit(onSalvarEdicao)} className={styles.modalForm}>
              <div className={styles.fotoWrap}>
                <UploadFoto
                  valor={fotoId}
                  onChange={(novoFotoId) => {
                    const fotoAnterior = fotoId
                    setFotoId(novoFotoId)
                    atualizarFoto.mutate(novoFotoId, {
                      onSuccess: () => notificar.sucesso(novoFotoId ? 'Foto atualizada.' : 'Foto removida.'),
                      onError: (erro: unknown) => {
                        setFotoId(fotoAnterior)
                        const mensagem =
                          (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                          'Tente novamente em alguns instantes.'
                        notificar.erro('Não foi possível salvar a foto', mensagem)
                      },
                    })
                  }}
                  formato="circulo"
                  nomeFallback={form.getValues('nome') as string}
                />
              </div>
              <Input id="nome-edit" label="NOME*" placeholder={`Nome da ${rotuloCelula.singular.toLowerCase()}`}
                error={errors.nome?.message} {...register('nome')} />
              <Select id="diaSemana-edit" label="DIA QUE A CÉLULA OCORRE" placeholder="Selecione"
                options={DIA_OPTIONS} error={errors.diaSemana?.message} {...register('diaSemana')} />
              <Input id="horario-edit" label="HORÁRIO DE INÍCIO" placeholder="hh:mm" inputMode="numeric" maxLength={5}
                value={horarioValue} onChange={e => {
                  const digits = e.target.value.replace(/\D/g, '').slice(0, 4)
                  const formatted = digits.length <= 2 ? digits : digits.replace(/(\d{2})(\d{0,2})/, '$1:$2')
                  setValue('horario', formatted, { shouldValidate: true })
                }} error={errors.horario?.message} />
              {erroGeral && <p className={styles.modalErro}>{erroGeral}</p>}
              <div className={styles.modalAcoes}>
                <Button type="button" variant="secondary" onClick={() => setEditando(false)}>Cancelar</Button>
                <Button type="submit" variant="primary" isLoading={salvando}
                  disabled={form.isFormIncomplete || salvando}>
                  Salvar
                </Button>
              </div>
            </form>
          </div>
        </div>
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
        <div className={styles.modalOverlay} onMouseDown={() => setCadastrarExterno(false)}>
          <div className={styles.modalLargo} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.modalClose} onClick={() => setCadastrarExterno(false)}>✕</button>
            <h2 className={styles.modalTitulo}>Cadastrar Visitante</h2>
            <VisitanteForm
              {...formExterno}
              onSubmit={async (data) => {
                await handleCriarExterno(data)
              }}
            />
          </div>
        </div>
      )}
      {fotoVisualizando && (
        <div className={styles.viewerOverlay} onMouseDown={() => setFotoVisualizando(null)}>
          <div className={styles.viewerModal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.viewerClose} onClick={() => setFotoVisualizando(null)}>
              <X size={20} />
            </button>
            <img src={urlFoto(fotoVisualizando, 'DISPLAY')!} alt="" className={styles.viewerImg} />
          </div>
        </div>
      )}
    </div>
  )
}
