'use client'

import { useState } from 'react'
import { MapPin, Plus } from 'lucide-react'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import { ModalLocalForm } from './ModalLocalForm'
import styles from './SeletorLocal.module.css'
import type { LocalEventoResponse } from '@/types/evento.type'

interface SeletorLocalProps {
  localId?: string
  localTexto?: string
  error?: string
  onChangeLocalId: (id: string | undefined) => void
  onChangeLocalTexto: (texto: string | undefined) => void
  onCapacidadeSugerida?: (capacidade: number) => void
}

// Dois caminhos, apresentados de cara num controle segmentado (não escondidos atrás de uma
// opção "outro" no dropdown, que ninguém achava): um ENDEREÇO CADASTRADO (reutilizável, com
// capacidade e endereço próprio) ou um texto livre SÓ PARA ESTE EVENTO. localId e localTexto
// são mutuamente exclusivos por construção — trocar de modo sempre limpa o outro.
type Modo = 'cadastrado' | 'avulso'

export function SeletorLocal({
  localId, localTexto, error, onChangeLocalId, onChangeLocalTexto, onCapacidadeSugerida,
}: SeletorLocalProps) {
  const { data: locais = [] } = useLocaisEvento()
  const [modo, setModo] = useState<Modo>(() => (localTexto && !localId ? 'avulso' : 'cadastrado'))
  const [modalAberto, setModalAberto] = useState(false)

  function irParaCadastrado() {
    setModo('cadastrado')
    onChangeLocalTexto(undefined)
  }

  function irParaAvulso() {
    setModo('avulso')
    onChangeLocalId(undefined)
  }

  function selecionar(id: string) {
    onChangeLocalTexto(undefined)
    onChangeLocalId(id || undefined)
    const local = locais.find((l) => l.id === id)
    if (local?.capacidade != null) onCapacidadeSugerida?.(local.capacidade)
  }

  // Acabou de cadastrar pelo modal: já seleciona (a lista recarrega sozinha via cache) para
  // a pessoa não ter que abrir o dropdown e procurar o que ela mesma acabou de criar.
  function aoCriar(local: LocalEventoResponse) {
    setModo('cadastrado')
    onChangeLocalTexto(undefined)
    onChangeLocalId(local.id)
    if (local.capacidade != null) onCapacidadeSugerida?.(local.capacidade)
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.segmentado} role="group" aria-label="Tipo de local do evento">
        <button
          type="button"
          className={`${styles.segmentoBtn} ${modo === 'cadastrado' ? styles.segmentoAtivo : ''}`}
          aria-pressed={modo === 'cadastrado'}
          onClick={irParaCadastrado}
        >
          Endereço cadastrado
        </button>
        <button
          type="button"
          className={`${styles.segmentoBtn} ${modo === 'avulso' ? styles.segmentoAtivo : ''}`}
          aria-pressed={modo === 'avulso'}
          onClick={irParaAvulso}
        >
          Só este evento
        </button>
      </div>

      <Transicao key={modo} modo="fade" className={styles.wrapper}>
        {modo === 'avulso' ? (
          <InputComSugestoes
            id="local-texto"
            label="ONDE VAI SER"
            placeholder="Ex: Chácara do João, Praça da Matriz"
            sugestoes={[]}
            value={localTexto ?? ''}
            error={error}
            registerProps={{
              value: localTexto ?? '',
              onChange: (e) => onChangeLocalTexto(e.target.value || undefined),
            }}
            onSelecionarSugestao={() => {}}
          />
        ) : locais.length === 0 ? (
          <div className={styles.vazio}>
            <MapPin size={22} aria-hidden="true" className={styles.vazioIcone} />
            <p className={styles.vazioTexto}>
              Nenhum endereço cadastrado ainda. Cadastre um para reaproveitar
              nos próximos eventos — ou use <strong>&quot;Só este evento&quot;</strong> para
              digitar na hora.
            </p>
            <button type="button" className={styles.botaoCadastrar} onClick={() => setModalAberto(true)}>
              <Plus size={16} aria-hidden="true" />
              Cadastrar endereço
            </button>
            {error && <span className={styles.erro}>{error}</span>}
          </div>
        ) : (
          <>
            <label className={styles.label}>ENDEREÇO DO EVENTO</label>
            <SelectMenu
              value={localId ?? ''}
              onChange={selecionar}
              placeholder="Selecione um endereço"
              ariaLabel="Endereço do evento"
              options={locais.map((l) => ({
                value: l.id,
                label: l.capacidade != null ? `${l.nome} — cap. ${l.capacidade}` : l.nome,
              }))}
            />
            <button type="button" className={styles.botaoNovo} onClick={() => setModalAberto(true)}>
              <Plus size={16} aria-hidden="true" />
              Novo endereço
            </button>
            {error && <span className={styles.erro}>{error}</span>}
          </>
        )}
      </Transicao>

      {modalAberto && (
        <ModalLocalForm local={null} onClose={() => setModalAberto(false)} onCriado={aoCriar} />
      )}
    </div>
  )
}
