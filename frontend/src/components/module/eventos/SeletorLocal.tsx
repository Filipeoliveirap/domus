'use client'

import { useEffect, useRef, useState } from 'react'
import { MapPin, Plus, Landmark, Pencil, Trash2, ChevronDown } from 'lucide-react'
import { clsx } from 'clsx'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
import { Input } from '@/components/common/input/Input'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useClickFora } from '@/hooks/useClickFora'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import { formatarCep } from '@/lib/masks'
import { jaExisteEnderecoDaIgreja, enderecoParaLinhaUnica } from '@/lib/formats/endereco'
import { UF_OPTIONS } from '@/lib/ufs'
import { ModalLocalForm } from './ModalLocalForm'
import styles from './SeletorLocal.module.css'
import type { LocalEventoRequest } from '@/types/evento.type'
import type { Endereco } from '@/types/pessoa.type'

// Três caminhos, todos visíveis de uma vez num controle segmentado (não escondidos atrás de
// uma opção "outro" no dropdown, que ninguém achava): ENDEREÇO CADASTRADO (reutilizável),
// DIGITAR SIMPLES (texto livre ad-hoc) ou ENDEREÇO COMPLETO (estruturado, só daquele evento).
// As três formas são mutuamente exclusivas — trocar de modo sempre limpa as outras duas.
type Modo = 'cadastrado' | 'simples' | 'completo'

/** Card compacto de endereço já definido — ícone + endereço formatado + editar/remover.
 *  `animarAoEditar`: true quando "Editar" TROCA o card por outro bloco (roda a saída animada);
 *  false quando "Editar" só abre um modal por cima (o card continua ali). Remover sempre anima. */
function CardEndereco({ titulo, linhas, nota, animarAoEditar = false, onEditar, onRemover }: {
  titulo?: string
  linhas: string[]
  nota?: string
  animarAoEditar?: boolean
  onEditar: () => void
  /** Ausente = sem botão de remover (ex.: só escolhendo no select, remover não faz nada útil). */
  onRemover?: () => void
}) {
  const [saindo, setSaindo] = useState(false)
  const sair = (fn: () => void) => { setSaindo(true); setTimeout(fn, 150) }

  return (
    <div className={clsx(styles.cardEndereco, saindo && styles.cardSaindo)}>
      <span className={styles.cardIcone}><MapPin size={18} aria-hidden="true" /></span>
      <div className={styles.cardInfo}>
        {titulo && <strong className={styles.cardTitulo}>{titulo}</strong>}
        {linhas.map((l, i) => <span key={i} className={styles.cardLinha}>{l}</span>)}
        {nota && <span className={styles.cardNota}>{nota}</span>}
      </div>
      <div className={styles.cardAcoes}>
        <button type="button" className={styles.cardBotao}
          onClick={() => (animarAoEditar ? sair(onEditar) : onEditar())} aria-label="Editar endereço">
          <Pencil size={15} aria-hidden="true" />
        </button>
        {onRemover && (
          <button type="button" className={styles.cardBotaoPerigo} onClick={() => sair(onRemover)} aria-label="Remover endereço">
            <Trash2 size={15} aria-hidden="true" />
          </button>
        )}
      </div>
    </div>
  )
}

interface SeletorLocalProps {
  localId?: string
  localTexto?: string
  enderecoLocal?: Endereco
  ehEdicao?: boolean
  /** Endereço pendente, digitado pelo próprio formulário de evento — só é cadastrado quando o evento é salvo. */
  novoLocal?: LocalEventoRequest
  error?: string
  errosEndereco?: Partial<Record<keyof Endereco, string>>
  onChangeLocalId: (id: string | undefined) => void
  onChangeLocalTexto: (texto: string | undefined) => void
  onChangeEnderecoLocal: (e: Endereco | undefined) => void
  onChangeNovoLocal: (p: LocalEventoRequest | undefined) => void
  onCapacidadeSugerida?: (capacidade: number) => void
}

function enderecoTemConteudo(e?: Endereco): boolean {
  return !!e && Object.values(e).some((v) => typeof v === 'string' && v.trim() !== '')
}

export function SeletorLocal({
  localId, localTexto, enderecoLocal, ehEdicao, novoLocal, error, errosEndereco,
  onChangeLocalId, onChangeLocalTexto, onChangeEnderecoLocal, onChangeNovoLocal, onCapacidadeSugerida,
}: SeletorLocalProps) {
  const { data: locais = [] } = useLocaisEvento()
  const { data: igreja } = useMinhaIgreja()
  const { buscar, carregando: carregandoCep } = useBuscaCep()

  const mostrarUsarIgreja = !!igreja?.endereco && !jaExisteEnderecoDaIgreja(locais, igreja.endereco)
  const cadastradoSelecionado = localId ? locais.find((l) => l.id === localId) : undefined

  const [modo, setModo] = useState<Modo>(() => {
    if (enderecoTemConteudo(enderecoLocal)) return 'completo'
    if (localTexto && !localId) return 'simples'
    return 'cadastrado'
  })
  // 'novo' = criar um pendente; 'editar-novo' = ajustar o pendente; 'editar-cadastrado' =
  // editar (e persistir) o endereço cadastrado já selecionado; null = fechado.
  const [modalAberto, setModalAberto] = useState<'novo' | 'editar-novo' | 'editar-cadastrado' | null>(null)

  const dropdownRef = useRef<HTMLDivElement>(null)
  const [dropdownAberto, setDropdownAberto] = useState(false)
  useClickFora(dropdownRef, () => setDropdownAberto(false))

  // No modo "endereço completo": quando o evento JÁ tinha um endereço salvo (edição), mostra
  // um card em vez de jogar o formulário na cara. Só colapsa o endereço que veio do evento —
  // nunca o que a pessoa está digitando agora (usuarioMexeu trava isso).
  const [enderecoEmCard, setEnderecoEmCard] = useState(false)
  const jaColapsou = useRef(false)
  const usuarioMexeu = useRef(false)
  const temEnderecoCompleto = enderecoTemConteudo(enderecoLocal)
  useEffect(() => {
    if (!jaColapsou.current && !usuarioMexeu.current && ehEdicao && temEnderecoCompleto) {
      jaColapsou.current = true
      setEnderecoEmCard(true)
      setModo('completo')
    }
  }, [ehEdicao, temEnderecoCompleto])

  function trocarModo(novo: Modo) {
    setModo(novo)
    if (novo !== 'cadastrado') { onChangeLocalId(undefined); onChangeNovoLocal(undefined) }
    if (novo !== 'simples') onChangeLocalTexto(undefined)
    if (novo !== 'completo') onChangeEnderecoLocal(undefined)
  }

  function patchEndereco(patch: Partial<Endereco>) {
    usuarioMexeu.current = true
    onChangeEnderecoLocal({ ...(enderecoLocal ?? {}), ...patch })
  }

  function selecionar(id: string) {
    onChangeLocalId(id || undefined)
    onChangeNovoLocal(undefined)
    const l = locais.find((x) => x.id === id)
    if (l?.capacidade != null) onCapacidadeSugerida?.(l.capacidade)
  }

  // No formulário de evento o endereço NÃO é cadastrado na hora — fica "segurado" no evento
  // e só vira cadastro quando o evento é salvo (backend, mesma transação).
  function aoDefinir(payload: LocalEventoRequest) {
    setModo('cadastrado')
    onChangeLocalId(undefined)
    onChangeLocalTexto(undefined)
    onChangeEnderecoLocal(undefined)
    onChangeNovoLocal(payload)
    if (payload.capacidade != null) onCapacidadeSugerida?.(payload.capacidade)
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
    usuarioMexeu.current = true
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

        {modo === 'completo' && enderecoEmCard && enderecoTemConteudo(enderecoLocal) && (
          <Transicao key="endereco-card" modo="subir">
            <CardEndereco
              linhas={[enderecoParaLinhaUnica(enderecoLocal!)]}
              animarAoEditar
              onEditar={() => setEnderecoEmCard(false)}
              onRemover={() => { onChangeEnderecoLocal(undefined); setEnderecoEmCard(false) }}
            />
          </Transicao>
        )}

        {modo === 'completo' && !(enderecoEmCard && enderecoTemConteudo(enderecoLocal)) && (
          <Transicao key="endereco-form" modo="subir" className={styles.enderecoCompleto}>
            {mostrarUsarIgreja && (
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
          </Transicao>
        )}

        {modo === 'cadastrado' && (
          novoLocal ? (
            <Transicao key="novo-local-card" modo="subir">
              <CardEndereco
                titulo={novoLocal.nome}
                linhas={[
                  novoLocal.cepLogradouroNumero ?? '',
                  novoLocal.complementoBairroCidadeUf ?? '',
                  novoLocal.capacidade != null ? `Capacidade: ${novoLocal.capacidade}` : '',
                ].filter(Boolean)}
                nota="Endereço novo · será cadastrado quando você salvar o evento."
                onEditar={() => setModalAberto('editar-novo')}
                onRemover={() => onChangeNovoLocal(undefined)}
              />
              {error && <span className={styles.erro}>{error}</span>}
            </Transicao>
          ) : locais.length === 0 ? (
            <Transicao key="cadastrado-vazio" modo="subir" className={styles.vazio}>
              <MapPin size={22} aria-hidden="true" className={styles.vazioIcone} />
              <p className={styles.vazioTexto}>
                Nenhum endereço cadastrado ainda. Cadastre um para reaproveitar
                nos próximos eventos — ou use <strong>&quot;Digitar simples&quot;</strong> /
                <strong> &quot;Endereço completo&quot;</strong> só para este.
              </p>
              <button type="button" className={styles.botaoCadastrar} onClick={() => setModalAberto('novo')}>
                <Plus size={16} aria-hidden="true" />
                Cadastrar endereço
              </button>
              {error && <span className={styles.erro}>{error}</span>}
            </Transicao>
          ) : cadastradoSelecionado ? (
            <Transicao key="cadastrado-card" modo="subir">
              <CardEndereco
                titulo={cadastradoSelecionado.nome}
                linhas={[
                  cadastradoSelecionado.endereco ?? '',
                  cadastradoSelecionado.capacidade != null ? `Capacidade: ${cadastradoSelecionado.capacidade}` : '',
                  cadastradoSelecionado.enderecoHerdado ? 'Endereço da igreja' : '',
                ].filter(Boolean)}
                onEditar={() => setModalAberto('editar-cadastrado')}
                onRemover={() => onChangeLocalId(undefined)}
              />
              {error && <span className={styles.erro}>{error}</span>}
            </Transicao>
          ) : (
            <Transicao key="cadastrado-dropdown" modo="subir">
              <label className={styles.label}>ENDEREÇO DO EVENTO</label>
              <div className={styles.dropdown} ref={dropdownRef}>
                <button
                  type="button"
                  className={styles.dropdownGatilho}
                  onClick={() => setDropdownAberto((v) => !v)}
                  aria-haspopup="listbox"
                  aria-expanded={dropdownAberto}
                >
                  <span className={styles.dropdownPlaceholder}>Selecione um endereço</span>
                  <ChevronDown size={16} className={clsx(styles.chevron, dropdownAberto && styles.chevronAberto)} aria-hidden="true" />
                </button>

                {dropdownAberto && (
                  <div className={styles.dropdownPainel} role="listbox">
                    {locais.map((l) => (
                      <button
                        key={l.id}
                        type="button"
                        role="option"
                        aria-selected={false}
                        className={styles.dropdownOpcao}
                        onClick={() => { selecionar(l.id); setDropdownAberto(false) }}
                      >
                        <span className={styles.opcaoIcone}><MapPin size={16} aria-hidden="true" /></span>
                        <span className={styles.opcaoInfo}>
                          <strong>{l.nome}</strong>
                          {l.endereco && <span className={styles.opcaoEndereco}>{l.endereco}</span>}
                          {l.capacidade != null && <span className={styles.opcaoMeta}>Capacidade: {l.capacidade}</span>}
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <button type="button" className={styles.botaoNovo} onClick={() => setModalAberto('novo')}>
                <Plus size={16} aria-hidden="true" />
                Novo endereço
              </button>
              {error && <span className={styles.erro}>{error}</span>}
            </Transicao>
          )
        )}
      </Transicao>

      {modalAberto === 'editar-cadastrado' && cadastradoSelecionado && (
        <ModalLocalForm local={cadastradoSelecionado} onClose={() => setModalAberto(null)} />
      )}
      {(modalAberto === 'novo' || modalAberto === 'editar-novo') && (
        <ModalLocalForm
          local={null}
          valoresIniciais={modalAberto === 'editar-novo' ? novoLocal : null}
          onClose={() => setModalAberto(null)}
          onDefinir={aoDefinir}
        />
      )}
    </div>
  )
}
