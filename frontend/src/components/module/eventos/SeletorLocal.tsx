'use client'

import { useState } from 'react'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import styles from './SeletorLocal.module.css'

// Sentinela de UI, nunca enviado ao backend (localId/localTexto são o par real).
const OUTRO_LOCAL = '__outro__'

interface SeletorLocalProps {
  localId?: string
  localTexto?: string
  error?: string
  onChangeLocalId: (id: string | undefined) => void
  onChangeLocalTexto: (texto: string | undefined) => void
  onCapacidadeSugerida?: (capacidade: number) => void
}

// Dropdown (SelectMenu, mesmo visual do resto do app) com os locais cadastrados da igreja
// (+ capacidade) mais a opção "outro local", que troca para texto livre. localId e
// localTexto são mutuamente exclusivos por construção: trocar de modo sempre limpa o outro.
export function SeletorLocal({
  localId, localTexto, error, onChangeLocalId, onChangeLocalTexto, onCapacidadeSugerida,
}: SeletorLocalProps) {
  const { data: locais = [] } = useLocaisEvento()
  const [modoOutro, setModoOutro] = useState(() => !!localTexto && !localId)

  function aoMudarSelect(valor: string) {
    if (valor === OUTRO_LOCAL) {
      setModoOutro(true)
      onChangeLocalId(undefined)
      return
    }

    setModoOutro(false)
    onChangeLocalTexto(undefined)
    onChangeLocalId(valor || undefined)

    const local = locais.find((l) => l.id === valor)
    if (local?.capacidade != null) {
      onCapacidadeSugerida?.(local.capacidade)
    }
  }

  function voltarAoSelect() {
    setModoOutro(false)
    onChangeLocalTexto(undefined)
  }

  return (
    <div className={styles.wrapper}>
      <Transicao key={modoOutro ? 'texto' : 'select'} modo="fade" className={styles.wrapper}>
        {modoOutro ? (
          <>
            <InputComSugestoes
              id="local-texto"
              label="LOCAL DO EVENTO"
              placeholder="Ex: Chácara do João"
              sugestoes={[]}
              value={localTexto ?? ''}
              error={error}
              registerProps={{
                value: localTexto ?? '',
                onChange: (e) => onChangeLocalTexto(e.target.value || undefined),
              }}
              onSelecionarSugestao={() => {}}
            />
            {/* Sem isto, escolher "outro local" seria um beco sem saída: não haveria como
                voltar para a lista de locais cadastrados. */}
            <button type="button" className={styles.voltar} onClick={voltarAoSelect}>
              Escolher um local cadastrado
            </button>
          </>
        ) : (
          <>
            <label className={styles.label}>LOCAL DO EVENTO</label>
            <SelectMenu
              value={localId ?? ''}
              onChange={aoMudarSelect}
              placeholder="Selecione um local"
              ariaLabel="Local do evento"
              options={[
                ...locais.map((l) => ({
                  value: l.id,
                  label: l.capacidade != null ? `${l.nome} — cap. ${l.capacidade}` : l.nome,
                })),
                { value: OUTRO_LOCAL, label: '— outro local —' },
              ]}
            />
            {error && <span className={styles.erro}>{error}</span>}
          </>
        )}
      </Transicao>
    </div>
  )
}
