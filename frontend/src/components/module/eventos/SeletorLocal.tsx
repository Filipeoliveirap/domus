'use client'

import { useState } from 'react'
import { MapPin, Plus, Landmark } from 'lucide-react'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
import { Input } from '@/components/common/input/Input'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import { formatarCep } from '@/lib/masks'
import { UF_OPTIONS } from '@/lib/ufs'
import { ModalLocalForm } from './ModalLocalForm'
import styles from './SeletorLocal.module.css'
import type { LocalEventoResponse } from '@/types/evento.type'
import type { Endereco } from '@/types/pessoa.type'

// Três caminhos, todos visíveis de uma vez num controle segmentado (não escondidos atrás de
// uma opção "outro" no dropdown, que ninguém achava): ENDEREÇO CADASTRADO (reutilizável),
// DIGITAR SIMPLES (texto livre ad-hoc) ou ENDEREÇO COMPLETO (estruturado, só daquele evento).
// As três formas são mutuamente exclusivas — trocar de modo sempre limpa as outras duas.
type Modo = 'cadastrado' | 'simples' | 'completo'

interface SeletorLocalProps {
  localId?: string
  localTexto?: string
  enderecoLocal?: Endereco
  error?: string
  errosEndereco?: Partial<Record<keyof Endereco, string>>
  onChangeLocalId: (id: string | undefined) => void
  onChangeLocalTexto: (texto: string | undefined) => void
  onChangeEnderecoLocal: (e: Endereco | undefined) => void
  onCapacidadeSugerida?: (capacidade: number) => void
}

function enderecoTemConteudo(e?: Endereco): boolean {
  return !!e && Object.values(e).some((v) => typeof v === 'string' && v.trim() !== '')
}

export function SeletorLocal({
  localId, localTexto, enderecoLocal, error, errosEndereco,
  onChangeLocalId, onChangeLocalTexto, onChangeEnderecoLocal, onCapacidadeSugerida,
}: SeletorLocalProps) {
  const { data: locais = [] } = useLocaisEvento()
  const { data: igreja } = useMinhaIgreja()
  const { buscar, carregando: carregandoCep } = useBuscaCep()

  const [modo, setModo] = useState<Modo>(() => {
    if (enderecoTemConteudo(enderecoLocal)) return 'completo'
    if (localTexto && !localId) return 'simples'
    return 'cadastrado'
  })
  const [modalAberto, setModalAberto] = useState(false)

  function trocarModo(novo: Modo) {
    setModo(novo)
    if (novo !== 'cadastrado') onChangeLocalId(undefined)
    if (novo !== 'simples') onChangeLocalTexto(undefined)
    if (novo !== 'completo') onChangeEnderecoLocal(undefined)
  }

  function patchEndereco(patch: Partial<Endereco>) {
    onChangeEnderecoLocal({ ...(enderecoLocal ?? {}), ...patch })
  }

  function selecionar(id: string) {
    onChangeLocalId(id || undefined)
    const l = locais.find((x) => x.id === id)
    if (l?.capacidade != null) onCapacidadeSugerida?.(l.capacidade)
  }

  // Acabou de cadastrar pelo modal: já seleciona (a lista recarrega sozinha via cache).
  function aoCriar(l: LocalEventoResponse) {
    trocarModo('cadastrado')
    onChangeLocalId(l.id)
    if (l.capacidade != null) onCapacidadeSugerida?.(l.capacidade)
  }

  async function aoSairDoCep(valor: string) {
    const achado = await buscar(valor)
    if (!achado) return
    patchEndereco({
      cep: achado.cep,
      logradouro: achado.logradouro || enderecoLocal?.logradouro,
      bairro: achado.bairro || enderecoLocal?.bairro,
      cidade: achado.cidade || enderecoLocal?.cidade,
      uf: achado.uf || enderecoLocal?.uf,
    })
  }

  function usarEnderecoDaIgreja() {
    if (!igreja?.endereco) return
    onChangeEnderecoLocal({ ...igreja.endereco })
  }

  const BOTOES: { modo: Modo; label: string }[] = [
    { modo: 'cadastrado', label: 'Endereço cadastrado' },
    { modo: 'simples', label: 'Digitar simples' },
    { modo: 'completo', label: 'Endereço completo' },
  ]

  return (
    <div className={styles.wrapper}>
      <div className={styles.segmentado} role="group" aria-label="Como definir o local do evento">
        {BOTOES.map((b) => (
          <button
            key={b.modo}
            type="button"
            className={`${styles.segmentoBtn} ${modo === b.modo ? styles.segmentoAtivo : ''}`}
            aria-pressed={modo === b.modo}
            onClick={() => trocarModo(b.modo)}
          >
            {b.label}
          </button>
        ))}
      </div>

      <Transicao key={modo} modo="fade" className={styles.wrapper}>
        {modo === 'simples' && (
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
        )}

        {modo === 'completo' && (
          <div className={styles.enderecoCompleto}>
            {igreja?.endereco && (
              <button type="button" className={styles.botaoNovo} onClick={usarEnderecoDaIgreja}>
                <Landmark size={16} aria-hidden="true" />
                Usar o endereço da igreja
              </button>
            )}
            <div className={styles.gridEndereco}>
              <div className={styles.spanFull}>
                <Input
                  id="ev-cep" label="CEP" placeholder="00000-000" inputMode="numeric" maxLength={9}
                  value={enderecoLocal?.cep ?? ''}
                  error={errosEndereco?.cep}
                  onChange={(e) => patchEndereco({ cep: formatarCep(e.target.value) })}
                  onBlur={(e) => void aoSairDoCep(e.target.value)}
                />
                {carregandoCep && <span className={styles.erro}>buscando CEP…</span>}
              </div>
              <div className={styles.spanFull}>
                <Input id="ev-logradouro" label="LOGRADOURO" placeholder="Rua, avenida…"
                  value={enderecoLocal?.logradouro ?? ''} error={errosEndereco?.logradouro}
                  onChange={(e) => patchEndereco({ logradouro: e.target.value })} />
              </div>
              <Input id="ev-numero" label="NÚMERO" placeholder="123, s/n…"
                value={enderecoLocal?.numero ?? ''} error={errosEndereco?.numero}
                onChange={(e) => patchEndereco({ numero: e.target.value })} />
              <Input id="ev-complemento" label="COMPLEMENTO" placeholder="Bloco, sala…"
                value={enderecoLocal?.complemento ?? ''} error={errosEndereco?.complemento}
                onChange={(e) => patchEndereco({ complemento: e.target.value })} />
              <Input id="ev-bairro" label="BAIRRO" placeholder="Centro…"
                value={enderecoLocal?.bairro ?? ''} error={errosEndereco?.bairro}
                onChange={(e) => patchEndereco({ bairro: e.target.value })} />
              <Input id="ev-cidade" label="CIDADE" placeholder="Recife…"
                value={enderecoLocal?.cidade ?? ''} error={errosEndereco?.cidade}
                onChange={(e) => patchEndereco({ cidade: e.target.value })} />
              <div className={styles.campoUf}>
                <span className={styles.label}>UF</span>
                <SelectMenu
                  value={enderecoLocal?.uf ?? ''}
                  onChange={(v) => patchEndereco({ uf: v })}
                  placeholder="UF" ariaLabel="Estado (UF)" options={UF_OPTIONS}
                />
                {errosEndereco?.uf && <span className={styles.erro}>{errosEndereco.uf}</span>}
              </div>
            </div>
          </div>
        )}

        {modo === 'cadastrado' && (
          locais.length === 0 ? (
            <div className={styles.vazio}>
              <MapPin size={22} aria-hidden="true" className={styles.vazioIcone} />
              <p className={styles.vazioTexto}>
                Nenhum endereço cadastrado ainda. Cadastre um para reaproveitar
                nos próximos eventos — ou use <strong>&quot;Digitar simples&quot;</strong> /
                <strong> &quot;Endereço completo&quot;</strong> só para este.
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
          )
        )}
      </Transicao>

      {modalAberto && (
        <ModalLocalForm local={null} onClose={() => setModalAberto(false)} onCriado={aoCriar} />
      )}
    </div>
  )
}
