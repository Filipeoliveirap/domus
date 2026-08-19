'use client'

import { useState } from 'react'
import { Select } from '@/components/common/select/Select'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
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

// <select> com os locais cadastrados da igreja (+ capacidade, quando houver) mais a opção
// "outro local", que troca para texto livre. localId e localTexto são mutuamente
// exclusivos por construção: trocar de modo sempre limpa o outro campo.
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

  if (modoOutro) {
    return (
      <div className={styles.wrapper}>
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
      </div>
    )
  }

  return (
    <div className={styles.wrapper}>
      <Select
        id="local-id"
        label="LOCAL DO EVENTO"
        placeholder="Selecione um local"
        error={error}
        value={localId ?? ''}
        onChange={(e) => aoMudarSelect(e.target.value)}
        options={[
          ...locais.map((l) => ({
            value: l.id,
            label: l.capacidade != null ? `${l.nome} — cap. ${l.capacidade}` : l.nome,
          })),
          { value: OUTRO_LOCAL, label: '— outro local —' },
        ]}
      />
    </div>
  )
}
