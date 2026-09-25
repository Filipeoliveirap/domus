'use client'

import { Search, X } from 'lucide-react'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { CampoData } from '@/components/common/CampoData/CampoData'
import type { ListarContasFiltros } from '@/types/contas-a-pagar'
import styles from './ContasAPagarFiltros.module.css'

interface Props {
  filtros: ListarContasFiltros
  onChange: (filtros: Partial<ListarContasFiltros>) => void
  competencia?: string
  onCompetenciaChange?: (v: string) => void
  temFiltroAtivo: boolean
  onLimpar: () => void
}

export function ContasAPagarFiltros({
  filtros,
  onChange,
  competencia,
  onCompetenciaChange,
  temFiltroAtivo,
  onLimpar,
}: Props) {
  function setFiltro(campo: keyof ListarContasFiltros, valor: string) {
    onChange({ [campo]: valor || undefined } as Partial<ListarContasFiltros>)
  }

  return (
    <div className={styles.filtros}>
      <div className={styles.filtroCampo}>
        <label className={styles.filtroLabel}>STATUS</label>
        <SelectMenu
          value={filtros.status ?? ''}
          onChange={(v) => setFiltro('status', v)}
          placeholder="Todos"
          ariaLabel="Filtrar por status"
          options={[
            { value: 'EM_ABERTO', label: 'Em aberto' },
            { value: 'PAGA', label: 'Paga' },
            { value: 'PARCIAL', label: 'Parcial' },
          ]}
        />
      </div>

      <div className={styles.filtroCampo}>
        <label className={styles.filtroLabel}>BENEFICIÁRIO</label>
        <div className={styles.inputWrapper}>
          <Search size={16} className={styles.inputIcone} aria-hidden="true" />
          <input
            type="text"
            className={styles.input}
            placeholder="Buscar beneficiário..."
            value={filtros.beneficiario ?? ''}
            onChange={(e) => setFiltro('beneficiario', e.target.value)}
            aria-label="Buscar beneficiário"
          />
        </div>
      </div>

      <div className={styles.filtroCampo}>
        <label className={styles.filtroLabel}>COMPETÊNCIA</label>
        <input
          type="month"
          className={styles.mesInput}
          value={competencia ?? ''}
          onChange={(e) => onCompetenciaChange?.(e.target.value)}
          aria-label="Filtrar por competência"
        />
      </div>

      <div className={styles.filtroCampo}>
        <label className={styles.filtroLabel}>VENCIMENTO ATÉ</label>
        <CampoData
          semLabel
          value={filtros.vencimentoAte ?? ''}
          onChange={(v) => setFiltro('vencimentoAte', v)}
        />
      </div>

      {temFiltroAtivo && (
        <button className={styles.btnLimpar} onClick={onLimpar}>
          <X size={14} />
          Limpar
        </button>
      )}
    </div>
  )
}
