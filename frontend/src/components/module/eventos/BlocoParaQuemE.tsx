'use client'

import { useState } from 'react'
import { Users, AlertTriangle } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { Revelar } from '@/components/common/Transicao/Revelar'
import type { RestricaoEstadoCivil, RestricaoSexo } from '@/types/evento.type'
import styles from './BlocoParaQuemE.module.css'
import formStyles from './EventoForm.module.css'

// Escolher um chip preenche idadeMin/idadeMax, mas os campos continuam editáveis — o nome do recorte alimenta o selo, não os números.
export const RECORTES_ETARIOS = [
  { nome: 'Kids', idadeMin: 0, idadeMax: 11 },
  { nome: 'Adolescentes', idadeMin: 12, idadeMax: 17 },
  { nome: 'Jovens', idadeMin: 18, idadeMax: 29 },
  { nome: 'Adultos', idadeMin: 30, idadeMax: 59 },
  { nome: '3ª idade', idadeMin: 60, idadeMax: null },
] as const

interface ValoresParaQuemE {
  recorteEtario: string | null | undefined
  idadeMin: number | undefined
  idadeMax: number | undefined
  restricaoEstadoCivil: RestricaoEstadoCivil | null | undefined
  restricaoSexo: RestricaoSexo | null | undefined
  exclusivoMembros: boolean
}

interface BlocoParaQuemEProps extends ValoresParaQuemE {
  erroIdadeMax?: string
  /** Esconde o toggle "Somente membros da igreja" quando false — sem inscrição não há quem restringir. */
  mostrarExclusivoMembros?: boolean
  onChangeRecorteEtario: (nome: string | null) => void
  onChangeIdadeMin: (idade: number | undefined) => void
  onChangeIdadeMax: (idade: number | undefined) => void
  onChangeEstadoCivil: (valor: RestricaoEstadoCivil | null) => void
  onChangeSexo: (valor: RestricaoSexo | null) => void
  onChangeExclusivoMembros: (valor: boolean) => void
}

/** Alguma restrição de "quem pode" está ligada — decide se o rádio abre em "Faixa específica". */
function temRestricaoAtiva(v: ValoresParaQuemE): boolean {
  return v.idadeMin != null || v.idadeMax != null
    || v.restricaoEstadoCivil != null || v.restricaoSexo != null
}

// Recolhido em "Todos" por padrão: maioria dos eventos não restringe ninguém.
export function BlocoParaQuemE(props: BlocoParaQuemEProps) {
  const {
    recorteEtario, idadeMin, idadeMax, restricaoEstadoCivil, restricaoSexo, exclusivoMembros,
    erroIdadeMax, mostrarExclusivoMembros = true,
    onChangeRecorteEtario, onChangeIdadeMin, onChangeIdadeMax,
    onChangeEstadoCivil, onChangeSexo, onChangeExclusivoMembros,
  } = props

  // Estado local separado evita forçar idadeMin=0 espúrio só pra abrir a seção sem restrição de idade.
  const [abertoManual, setAbertoManual] = useState(false)
  const modoFaixa = abertoManual || temRestricaoAtiva(props)

  function aoEscolherTodos() {
    setAbertoManual(false)
    onChangeRecorteEtario(null)
    onChangeIdadeMin(undefined)
    onChangeIdadeMax(undefined)
    onChangeEstadoCivil(null)
    onChangeSexo(null)
  }

  function aoEscolherChip(chip: (typeof RECORTES_ETARIOS)[number]) {
    onChangeRecorteEtario(chip.nome)
    onChangeIdadeMin(chip.idadeMin)
    onChangeIdadeMax(chip.idadeMax ?? undefined)
  }

  return (
    <div className={styles.bloco}>
      <div className={styles.header}>
        <Users size={16} className={styles.headerIcone} />
        <span className={formStyles.labelData}>PARA QUEM É</span>
      </div>

      <div className={styles.segmentado}>
        <button
          type="button"
          className={`${styles.segmentoBtn} ${!modoFaixa ? styles.segmentoAtivo : ''}`}
          onClick={aoEscolherTodos}
        >
          Todos
        </button>
        <button
          type="button"
          className={`${styles.segmentoBtn} ${modoFaixa ? styles.segmentoAtivo : ''}`}
          onClick={() => setAbertoManual(true)}
        >
          Faixa específica
        </button>
      </div>

      {modoFaixa && (
        <Revelar className={styles.detalhes}>
          <div>
            <span className={styles.subLabel}>Faixa etária</span>
            <div className={styles.chips}>
              {RECORTES_ETARIOS.map((chip) => (
                <button
                  key={chip.nome}
                  type="button"
                  className={`${styles.chip} ${recorteEtario === chip.nome ? styles.chipAtivo : ''}`}
                  onClick={() => aoEscolherChip(chip)}
                >
                  {chip.nome}
                </button>
              ))}
            </div>

            <div className={styles.linhaIdades}>
              <Input
                id="idade-min"
                type="number"
                label="IDADE MÍNIMA"
                placeholder="0"
                min={0}
                value={idadeMin ?? ''}
                onChange={(e) => onChangeIdadeMin(e.target.value === '' ? undefined : Number(e.target.value))}
              />
              <Input
                id="idade-max"
                type="number"
                label="IDADE MÁXIMA"
                placeholder="Sem limite"
                min={0}
                error={erroIdadeMax}
                value={idadeMax ?? ''}
                onChange={(e) => onChangeIdadeMax(e.target.value === '' ? undefined : Number(e.target.value))}
              />
            </div>
          </div>

          <div className={styles.linhaSelects}>
            <div className={styles.campoSelect}>
              <span className={styles.subLabel}>Estado civil</span>
              <SelectMenu
                value={restricaoEstadoCivil ?? ''}
                onChange={(v) => onChangeEstadoCivil((v || null) as RestricaoEstadoCivil | null)}
                placeholder="Qualquer"
                ariaLabel="Restrição de estado civil"
                options={[
                  { value: 'SOLTEIRO', label: 'Solteiro(a)' },
                  { value: 'CASADO', label: 'Casado(a)' },
                  { value: 'DIVORCIADO', label: 'Divorciado(a)' },
                  { value: 'VIUVO', label: 'Viúvo(a)' },
                ]}
              />
            </div>

            <div className={styles.campoSelect}>
              <span className={styles.subLabel}>Sexo</span>
              <SelectMenu
                value={restricaoSexo ?? ''}
                onChange={(v) => onChangeSexo((v || null) as RestricaoSexo | null)}
                placeholder="Qualquer"
                ariaLabel="Restrição de sexo"
                options={[
                  { value: 'HOMEM', label: 'Homem' },
                  { value: 'MULHER', label: 'Mulher' },
                ]}
              />
            </div>
          </div>
        </Revelar>
      )}

      {mostrarExclusivoMembros && (
        <Revelar className={styles.detalhes}>
          <label className={formStyles.toggleRow}>
            <span className={formStyles.toggleTexto}>
              <span className={formStyles.toggleTitulo}>Somente membros da igreja</span>
            </span>
            <span className={formStyles.switch}>
              <input
                type="checkbox"
                className={formStyles.switchInput}
                checked={exclusivoMembros}
                onChange={(e) => onChangeExclusivoMembros(e.target.checked)}
              />
              <span className={formStyles.switchTrilho} />
            </span>
          </label>

          {exclusivoMembros && (
            <Transicao modo="subir">
              <div className={formStyles.infoBox}>
                <AlertTriangle size={18} className={formStyles.infoIcon} />
                <p className={formStyles.infoText}>
                  Pessoas com vínculo Congregante não poderão se inscrever nem ser inscritas.
                </p>
              </div>
            </Transicao>
          )}
        </Revelar>
      )}
    </div>
  )
}
