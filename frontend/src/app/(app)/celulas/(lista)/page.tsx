'use client'

import { useState } from 'react'
import { Plus, Pencil, Archive, Grid3X3, Crown, X, Trash2 } from 'lucide-react'
import { useCelulas } from '@/hooks/celula/useCelulas'
import { useCelulaForm } from '@/hooks/celula/useCelulaForm'
import { useExcluirCelulaDefinitivamente } from '@/hooks/celula/useExcluirCelulaDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { Button } from '@/components/common/button/Button'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { urlFoto } from '@/lib/urlFoto'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { celulaService } from '@/services/celula.service'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { CelulaResponse } from '@/types/celula.type'
import styles from './page.module.css'

const DIA_OPTIONS = [
  { value: 'SEGUNDA', label: 'Segunda' },
  { value: 'TERCA', label: 'Terça' },
  { value: 'QUARTA', label: 'Quarta' },
  { value: 'QUINTA', label: 'Quinta' },
  { value: 'SEXTA', label: 'Sexta' },
  { value: 'SABADO', label: 'Sábado' },
  { value: 'DOMINGO', label: 'Domingo' },
]

export default function CelulasPage() {
  const { data: celulas, isLoading, isError, refetch } = useCelulas()
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const podeGerenciar = podeGerenciarCelulas(role)
  const [modalAberto, setModalAberto] = useState(false)
  const [editando, setEditando] = useState<CelulaResponse | null>(null)
  const [fotoId, setFotoId] = useState<string | null>(null)
  const [arquivando, setArquivando] = useState<string | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)
  const [excluindoDefinitivo, setExcluindoDefinitivo] = useState<CelulaResponse | null>(null)
  const queryClient = useQueryClient()
  const { celula: rotuloCelula } = useRotulos()

  const form = useCelulaForm()
  const { register, handleSubmit, setValue, watch, formState: { errors }, isFormIncomplete, isLoading: salvando, erroGeral } = form
  const horarioValue = watch('horario') as string ?? ''

  async function onSalvar() {
    const data = form.getValues()
    try {
      const payload = {
        nome: data.nome,
        diaSemana: (data.diaSemana || undefined) as 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO',
        horario: data.horario ? data.horario + ':00' : undefined,
        fotoId: fotoId ?? undefined,
      }
      if (editando) {
        await celulaService.atualizar(editando.id, payload)
      } else {
        await celulaService.criar(payload)
      }
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(editando ? `${rotuloCelula.singular} atualizada!` : `${rotuloCelula.singular} criada!`)
      form.reset()
      setFotoId(null)
      setEditando(null)
      setModalAberto(false)
    } catch {
      notificar.erro(`Erro ao salvar ${rotuloCelula.singular.toLowerCase()}.`)
    }
  }

  async function handleToggleArquivar(id: string) {
    if (arquivando) return
    const celula = celulas?.find(c => c.id === id)
    if (!celula) return
    setArquivando(id)
    try {
      await celulaService.excluir(id)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${celula.nome} foi removida.`)
    } catch {
      notificar.erro(`Erro ao remover ${rotuloCelula.singular.toLowerCase()}.`)
    } finally {
      setArquivando(null)
    }
  }

  if (!hidratado) return <div className={styles.pagina} />

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>{rotuloCelula.plural}</h1>
            {celulas && celulas.length > 0 && <span className={styles.contador}>{celulas.length}</span>}
          </div>
          <p className={styles.subtitulo}>Pequenos grupos de estudo bíblico.</p>
        </div>
        {podeGerenciar && (
          <button className={styles.botaoPrimario} onClick={() => { setFotoId(null); setEditando(null); setModalAberto(true) }}>
            Nova {rotuloCelula.singular.toLowerCase()}
          </button>
        )}
      </header>

        {isLoading ? (
          <div className={styles.grid}>
            {[1,2,3].map(i => (
              <div key={i} className={styles.card}>
                <div className={styles.cardIcon}>
                  <Skeleton width="48px" height="48px" radius="var(--radius-lg)" />
                </div>
                <Skeleton width="70%" height="18px" />
                <Skeleton width="50%" height="14px" />
                <div className={styles.cardFooter}>
                  <Skeleton width="80px" height="14px" />
                  <Skeleton width="30px" height="24px" radius="var(--radius-full)" />
                </div>
              </div>
            ))}
          </div>
      ) : isError ? (
        <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão."
          aoTentarNovamente={() => refetch()} />
      ) : celulas && celulas.length === 0 ? (
        <EstadoVazio icone={Grid3X3} titulo={`Nenhuma ${rotuloCelula.singular.toLowerCase()}`}
          mensagem={`Comece cadastrando a primeira ${rotuloCelula.singular.toLowerCase()} da sua igreja.`}
          acaoPrimaria={podeGerenciar ? { label: `Nova ${rotuloCelula.singular.toLowerCase()}`, onClick: () => setModalAberto(true) } : undefined} />
      ) : (
        <div className={styles.grid}>
          {celulas?.map(c => {
            const podeEditarEsta = podeGerenciar || c.souLiderDestaCelula
            const acoes: ItemAcao[] = [
              ...(podeEditarEsta ? [{ label: 'Editar', icone: Pencil, onClick: () => {
                form.reset({
                  nome: c.nome,
                  diaSemana: c.diaSemana ?? '',
                  horario: c.horario ? c.horario.slice(0, 5) : '',
                })
                setFotoId(c.fotoId ?? null)
                setEditando(c)
                setModalAberto(true)
              }}] : []),
              ...(podeGerenciar ? [
                c.temVinculo
                  ? { label: 'Arquivar', icone: Archive, onClick: () => handleToggleArquivar(c.id), perigo: true, separadorAntes: true }
                  : { label: 'Excluir', icone: Trash2, onClick: () => setExcluindoDefinitivo(c), perigo: true, separadorAntes: true }
              ] : []),
            ]
            return (
              <div key={c.id} className={styles.card}
                onClick={() => window.location.href = `/celulas/${c.id}`}>
                {acoes.length > 0 && (
                <div className={styles.cardActions} onClick={e => e.stopPropagation()}>
                  <MenuAcoes itens={acoes} />
                </div>
                )}
                <div className={styles.cardIcon}
                  onClick={c.fotoId ? (e) => { e.stopPropagation(); setFotoVisualizando(c.fotoId) } : undefined}>
                  {c.fotoId ? (
                    <img src={urlFoto(c.fotoId, 'THUMB')!} alt="" className={styles.cardFoto} />
                  ) : (
                    <Grid3X3 size={24} />
                  )}
                </div>
                <h3 className={styles.cardNome}>{c.nome}</h3>
                {(c.diaSemana || c.horario) && (
                  <p className={styles.cardHorario}>
                    {[rotuloDiaSemana(c.diaSemana), formatarHorario(c.horario)].filter(Boolean).join(', ')}
                  </p>
                )}
                <div className={styles.cardFooter}>
                  {c.lideres.length > 0 ? (
                    <div className={styles.cardLider}><Crown size={14} /><span>{c.lideres[0]}</span></div>
                  ) : (
                    <span className={styles.cardLiderVazio}>Sem líder</span>
                  )}
                  <span className={styles.cardMembros}>{c.totalMembros}</span>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {modalAberto && (
        <div className={styles.modalOverlay} onMouseDown={() => { setModalAberto(false); setEditando(null) }}>
          <div className={styles.modal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.modalClose} onClick={() => { setModalAberto(false); setEditando(null) }}>✕</button>
            <h2 className={styles.modalTitulo}>{editando ? `Editar ${rotuloCelula.singular}` : `Nova ${rotuloCelula.singular}`}</h2>
            <form onSubmit={handleSubmit(onSalvar)} className={styles.modalForm}>
              <div className={styles.fotoWrap}>
                <UploadFoto
                  valor={fotoId}
                  onChange={(id) => setFotoId(id)}
                  formato="circulo"
                  nomeFallback={form.getValues('nome') as string}
                />
              </div>
              <Input id="nome-modal" label="NOME*" placeholder={`Nome da ${rotuloCelula.singular.toLowerCase()}`}
                error={errors.nome?.message} {...register('nome')} />
              <Select id="diaSemana-modal" label={`DIA QUE A ${rotuloCelula.singular.toUpperCase()} OCORRE`} placeholder="Selecione"
                options={DIA_OPTIONS} error={errors.diaSemana?.message} {...register('diaSemana')} />
              <Input id="horario-modal" label="HORÁRIO DE INÍCIO" placeholder="hh:mm" inputMode="numeric" maxLength={5}
                value={horarioValue} onChange={e => {
                  const digits = e.target.value.replace(/\D/g, '').slice(0, 4)
                  const formatted = digits.length <= 2 ? digits : digits.replace(/(\d{2})(\d{0,2})/, '$1:$2')
                  setValue('horario', formatted, { shouldValidate: true })
                }} error={errors.horario?.message} />
              {erroGeral && <p className={styles.modalErro}>{erroGeral}</p>}
              <div className={styles.modalAcoes}>
                <Button type="submit" variant="primary" isLoading={salvando}
                  disabled={isFormIncomplete || salvando}>
                  {editando ? 'Salvar' : `Criar ${rotuloCelula.singular.toLowerCase()}`}
                </Button>
              </div>
            </form>
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

      {excluindoDefinitivo && (
        <ModalExcluirDefinitivo celula={excluindoDefinitivo} onClose={() => setExcluindoDefinitivo(null)} />
      )}
    </div>
  )
}

function ModalExcluirDefinitivo({ celula, onClose }: { celula: CelulaResponse; onClose: () => void }) {
  // Esse botão só aparece quando a célula não tem ninguém vinculado (senão o menu
  // mostra "Arquivar") — confirmação simples basta, sem precisar digitar o nome.
  const { confirmar, isLoading } = useExcluirCelulaDefinitivamente(celula, onClose)
  const { celula: rotuloCelula } = useRotulos()
  return (
    <ModalConfirmacao
      titulo={`Excluir ${rotuloCelula.singular.toLowerCase()} definitivamente?`}
      mensagem={<>Isso vai apagar <strong>{celula.nome}</strong> de vez. Não tem como desfazer.</>}
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
