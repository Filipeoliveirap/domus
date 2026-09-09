'use client'

import { CalendarClock } from 'lucide-react'
import { formatarValorDigitado } from '@/lib/formats/financeiro/movimentacaoFormat'
import { urlFoto } from '@/lib/urlFoto'
import type { RestricaoEstadoCivil, RestricaoSexo } from '@/types/evento.type'
import styles from './PreviaEvento.module.css'

export interface PreviaEventoProps {
  titulo?: string
  tipo?: string
  inicioData?: string
  inicioHora?: string
  fimData?: string
  localResumo?: string
  fotoId?: string | null
  requerInscricao: boolean
  tipoInscricao: 'GRATUITO' | 'PAGO'
  preco?: string
  vagas?: number
  inscricoesAteData?: string
  exclusivoMembros: boolean
  idadeMin?: number
  idadeMax?: number
  restricaoEstadoCivil?: RestricaoEstadoCivil | null
  restricaoSexo?: RestricaoSexo | null
  restritoPropriaIgreja: boolean
  temFamilia: boolean
  rotuloOutrasCongregacoes: string
  controlaPresenca: boolean
  camposFaltando: string[]
}

const DIAS_ABREV = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']
const MESES_ABREV = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** "2026-03-15" -> "sáb, 15 mar". String vazia/inválida -> "". */
function formatarDataCurta(iso?: string): string {
  if (!iso) return ''
  const [ano, mes, dia] = iso.split('-').map(Number)
  if (!ano || !mes || !dia) return ''
  const d = new Date(ano, mes - 1, dia)
  return `${DIAS_ABREV[d.getDay()]}, ${dia} ${MESES_ABREV[mes - 1]}`
}

/** Junta ["a", "b", "c"] -> "a, b e c". */
function juntarComE(itens: string[]): string {
  if (itens.length <= 1) return itens.join('')
  return `${itens.slice(0, -1).join(', ')} e ${itens[itens.length - 1]}`
}

/**
 * Regras do evento em português, uma frase por linha. `faltando: true` = linha
 * vermelha de "falta preencher". Ordem: faltando primeiro, depois as regras.
 */
export function resumirRegras(p: PreviaEventoProps): { texto: string; faltando: boolean }[] {
  const linhas: { texto: string; faltando: boolean }[] = []

  if (p.camposFaltando.length > 0) {
    linhas.push({ texto: `Falta preencher: ${juntarComE(p.camposFaltando)}`, faltando: true })
  }

  const temRestricaoPublico =
    p.exclusivoMembros || p.idadeMin != null || p.idadeMax != null ||
    p.restricaoEstadoCivil != null || p.restricaoSexo != null

  if (!p.requerInscricao) {
    linhas.push({
      texto: temRestricaoPublico ? 'Sem inscrição' : 'Aberto a todos · sem inscrição',
      faltando: false,
    })
  } else {
    const partes: string[] = ['Inscrição obrigatória']
    partes.push(p.vagas != null ? `${p.vagas} vagas` : 'vagas ilimitadas')
    if (p.inscricoesAteData) partes.push(`inscrições até ${formatarDataCurta(p.inscricoesAteData)}`)
    partes.push(
      p.tipoInscricao === 'PAGO' && p.preco
        ? formatarValorDigitado(p.preco)
        : 'gratuito',
    )
    linhas.push({ texto: partes.join(' · '), faltando: false })
  }

  if (p.exclusivoMembros) linhas.push({ texto: 'Só para membros', faltando: false })

  if (p.idadeMin != null || p.idadeMax != null) {
    const faixa =
      p.idadeMin != null && p.idadeMax != null ? `${p.idadeMin}–${p.idadeMax} anos`
        : p.idadeMin != null ? `a partir de ${p.idadeMin} anos`
          : `até ${p.idadeMax} anos`
    linhas.push({ texto: faixa, faltando: false })
  }

  if (p.restricaoSexo === 'MULHER') linhas.push({ texto: 'Somente mulheres', faltando: false })
  if (p.restricaoSexo === 'HOMEM') linhas.push({ texto: 'Somente homens', faltando: false })

  if (p.restricaoEstadoCivil) {
    const mapa: Record<RestricaoEstadoCivil, string> = {
      SOLTEIRO: 'apenas solteiros(as)',
      CASADO: 'apenas casados(as)',
      DIVORCIADO: 'apenas divorciados(as)',
      VIUVO: 'apenas viúvos(as)',
    }
    linhas.push({ texto: mapa[p.restricaoEstadoCivil], faltando: false })
  }

  if (p.restritoPropriaIgreja && p.temFamilia) {
    linhas.push({ texto: `Não aparece para ${p.rotuloOutrasCongregacoes}`, faltando: false })
  }

  if (p.controlaPresenca) linhas.push({ texto: 'Check-in ativado', faltando: false })

  return linhas
}

export function PreviaEvento(props: PreviaEventoProps) {
  const dataCurta = formatarDataCurta(props.inicioData)
  const linhaQuando = [dataCurta, props.inicioHora, props.localResumo]
    .filter((x): x is string => !!x && x.trim() !== '')
    .join(' · ')

  const regras = resumirRegras(props)
  const urlThumb = urlFoto(props.fotoId, 'THUMB')

  return (
    <div className={styles.previa}>
      <span className={styles.rotulo}>Prévia</span>

      <div className={styles.card}>
        <div className={styles.thumb}>
          {urlThumb ? (
            // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
            <img src={urlThumb} alt="" className={styles.thumbImg} />
          ) : (
            <CalendarClock size={20} aria-hidden="true" />
          )}
        </div>
        <div className={styles.info}>
          <span className={props.titulo ? styles.titulo : styles.tituloVazio}>
            {props.titulo || 'Sem título'}
          </span>
          {linhaQuando && <span className={styles.quando}>{linhaQuando}</span>}
          {props.tipo && <span className={styles.tipo}>{props.tipo}</span>}
        </div>
      </div>

      <ul className={styles.regras}>
        {regras.map((r, i) => (
          <li key={i} className={r.faltando ? styles.regraFaltando : undefined}>
            {r.texto}
          </li>
        ))}
      </ul>
    </div>
  )
}
