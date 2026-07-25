'use client'

import { useState } from 'react'
import Link from 'next/link'
import { ChevronRight, Plus, Pencil, Archive, Grid3X3 } from 'lucide-react'
import { useCelulas } from '@/hooks/celula/useCelulas'
import { useCelulaForm } from '@/hooks/celula/useCelulaForm'
import { useExcluirCelula } from '@/hooks/celula/useExcluirCelula'
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
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { celulaService } from '@/services/celula.service'
import { notificar } from '@/components/common/Notificacao/notificar'
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
  const [arquivando, setArquivando] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const form = useCelulaForm()
  const { register, handleSubmit, setValue, watch, formState: { errors }, isFormIncomplete, isLoading: salvando, erroGeral } = form
  const horarioValue = watch('horario') as string ?? ''

  async function onCriar() {
    const data = form.getValues()
    try {
      const payload = {
        nome: data.nome,
        diaSemana: (data.diaSemana || undefined) as 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO',
        horario: data.horario ? data.horario + ':00' : undefined,
      }
      await celulaService.criar(payload)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso('Célula criada com sucesso!')
      form.reset()
      setModalAberto(false)
    } catch {
      notificar.erro('Erro ao criar célula.')
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
      notificar.erro('Erro ao remover célula.')
    } finally {
      setArquivando(null)
    }
  }

  if (!hidratado) return <div className={styles.pagina} />

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb}>
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Células</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>Células</h1>
            {celulas && celulas.length > 0 && <span className={styles.contador}>{celulas.length}</span>}
          </div>
          <p className={styles.subtitulo}>Pequenos grupos de estudo bíblico.</p>
        </div>
        {podeGerenciar && (
          <button className={styles.botaoPrimario} onClick={() => setModalAberto(true)}>
            Nova célula
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
        <EstadoVazio icone={Grid3X3} titulo="Nenhuma célula"
          mensagem="Comece cadastrando a primeira célula da sua igreja."
          acaoPrimaria={podeGerenciar ? { label: 'Nova célula', onClick: () => setModalAberto(true) } : undefined} />
      ) : (
        <div className={styles.grid}>
          {celulas?.map(c => {
            const acoes: ItemAcao[] = [
              ...(podeGerenciar ? [{ label: 'Editar', icone: Pencil, onClick: () => window.location.href = `/celulas/${c.id}` }] : []),
              ...(podeGerenciar ? [{ label: 'Arquivar', icone: Archive, onClick: () => handleToggleArquivar(c.id), perigo: true, separadorAntes: true }] : []),
            ]
            return (
              <div key={c.id} className={styles.card}
                onClick={() => window.location.href = `/celulas/${c.id}`}>
                {acoes.length > 0 && (
                <div className={styles.cardActions} onClick={e => e.stopPropagation()}>
                  <MenuAcoes itens={acoes} />
                </div>
                )}
                <div className={styles.cardIcon}>
                  <Grid3X3 size={24} />
                </div>
                <h3 className={styles.cardNome}>{c.nome}</h3>
                {(c.diaSemana || c.horario) && (
                  <p className={styles.cardHorario}>
                    {[rotuloDiaSemana(c.diaSemana), formatarHorario(c.horario)].filter(Boolean).join(', ')}
                  </p>
                )}
                <div className={styles.cardFooter}>
                  {c.lideres.length > 0 ? (
                    <span className={styles.cardLider}>{c.lideres[0]}</span>
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
        <div className={styles.modalOverlay} onMouseDown={() => setModalAberto(false)}>
          <div className={styles.modal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.modalClose} onClick={() => setModalAberto(false)}>✕</button>
            <h2 className={styles.modalTitulo}>Nova Célula</h2>
            <form onSubmit={handleSubmit(onCriar)} className={styles.modalForm}>
              <Input id="nome-modal" label="NOME*" placeholder="Nome da célula"
                error={errors.nome?.message} {...register('nome')} />
              <Select id="diaSemana-modal" label="DIA QUE A CÉLULA OCORRE" placeholder="Selecione"
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
                  Criar célula
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
