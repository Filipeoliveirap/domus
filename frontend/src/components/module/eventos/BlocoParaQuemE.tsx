'use client'

import { useState } from 'react'
import { Users, AlertTriangle } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import type { RestricaoEstadoCivil, RestricaoSexo } from '@/types/evento.type'
import styles from './BlocoParaQuemE.module.css'
import formStyles from './EventoForm.module.css'

/**
 * Recortes etários prontos: escolher um chip preenche idadeMin/idadeMax, mas os dois
 * campos continuam editáveis à mão (a pessoa pode ajustar sem perder o nome do recorte —
 * é ele que alimenta o selo no card, não os números).
 */
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

/**
 * Bloco "Para quem é" — elegibilidade do evento. Recolhido em "Todos" por padrão: a
 * maioria dos eventos não restringe ninguém, e não deveria pagar o custo visual desta
 * feature. Só existe dentro da seção "Inscrições" (requerInscricao=true) — não faz
 * sentido restringir quem se inscreve num evento que não tem inscrição.
 */
export function BlocoParaQuemE(props: BlocoParaQuemEProps) {
  const {
    recorteEtario, idadeMin, idadeMax, restricaoEstadoCivil, restricaoSexo, exclusivoMembros,
    erroIdadeMax, mostrarExclusivoMembros = true,
    onChangeRecorteEtario, onChangeIdadeMin, onChangeIdadeMax,
    onChangeEstadoCivil, onChangeSexo, onChangeExclusivoMembros,
  } = props

  // "Faixa específica" aberta = há restrição nos dados OU a pessoa abriu a seção à mão.
  // O estado local existe para o segundo caso: sem ele, o único jeito de abrir a seção
  // seria já gravar uma restrição — e era isso que forçava um `idadeMin = 0` espúrio no
  // payload de quem só queria restringir por sexo. Na edição, `temRestricaoAtiva` abre
  // sozinho, então o reidratar continua funcionando.
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
        <div className={styles.detalhes}>
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
              <select
                className={styles.select}
                value={restricaoEstadoCivil ?? ''}
                onChange={(e) => onChangeEstadoCivil((e.target.value || null) as RestricaoEstadoCivil | null)}
              >
                <option value="">Qualquer</option>
                <option value="SOLTEIRO">Solteiro(a)</option>
                <option value="CASADO">Casado(a)</option>
                <option value="DIVORCIADO">Divorciado(a)</option>
                <option value="VIUVO">Viúvo(a)</option>
              </select>
            </div>

            <div className={styles.campoSelect}>
              <span className={styles.subLabel}>Sexo</span>
              <select
                className={styles.select}
                value={restricaoSexo ?? ''}
                onChange={(e) => onChangeSexo((e.target.value || null) as RestricaoSexo | null)}
              >
                <option value="">Qualquer</option>
                <option value="HOMEM">Homem</option>
                <option value="MULHER">Mulher</option>
              </select>
            </div>
          </div>
        </div>
      )}

      {mostrarExclusivoMembros && (
        <>
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
            <div className={formStyles.infoBox}>
              <AlertTriangle size={18} className={formStyles.infoIcon} />
              <p className={formStyles.infoText}>
                Pessoas com vínculo Congregante não poderão se inscrever nem ser inscritas.
              </p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
