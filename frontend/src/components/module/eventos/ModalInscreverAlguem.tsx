'use client'

import { useState } from 'react'
import { X, Share2 } from 'lucide-react'
import { ModalInscreverPessoas } from './ModalInscreverPessoas'
import { ModalCompartilharConvite } from './ModalCompartilharConvite'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useVisitantesBuscaLeve } from '@/hooks/visitante/useVisitantesBuscaLeve'
import { useCriarConvidado } from '@/hooks/inscricao/useCriarConvidado'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useDebounce } from '@/hooks/useDebounce'
import { formatarTelefone } from '@/lib/masks'
import type { RespostaRequest } from '@/types/campoPersonalizado.type'
import styles from './ModalInscreverAlguem.module.css'
import painelStyles from './ModalInscreverPessoas.module.css'

type Aba = 'pessoas' | 'visitantes' | 'fora'

interface Props {
  eventoId: string
  tituloEvento: string
  exclusivoMembros: boolean
  onClose: () => void
}

interface DadosConvidado {
  nome: string
  telefone: string
}

export function ModalInscreverAlguem({ eventoId, tituloEvento, exclusivoMembros, onClose }: Props) {
  const [aba, setAba] = useState<Aba>('pessoas')

  const [buscaVisitante, setBuscaVisitante] = useState('')
  const buscaDebounced = useDebounce(buscaVisitante, 300)
  const { data: visitantes = [] } = useVisitantesBuscaLeve(buscaDebounced)
  const [visitanteSelecionadoId, setVisitanteSelecionadoId] = useState<string | null>(null)

  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouConfirmar, setTentouConfirmar] = useState(false)

  const { data: campos = [] } = useCamposPersonalizados(eventoId)
  const { data: minha } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever(eventoId, true)
  const criarConvidado = useCriarConvidado(eventoId)

  const [aguardandoRespostaParticipar, setAguardandoRespostaParticipar] = useState<DadosConvidado | null>(null)
  const [compartilharAberto, setCompartilharAberto] = useState(false)

  const isPending = criarConvidado.isPending || inscrever.isPending

  /** Troca de aba limpa nome/telefone/campos — sem isso, selecionar um visitante e depois
   *  ir pra "Pessoa de fora" deixava os dados dele preenchidos lá, como se já tivessem sido
   *  digitados pra outra pessoa. */
  function trocarAba(novaAba: Aba) {
    setAba(novaAba)
    setNome('')
    setTelefone('')
    setVisitanteSelecionadoId(null)
    setBuscaVisitante('')
    setCamposValores({})
    setTentouConfirmar(false)
  }

  function selecionarVisitante(id: string) {
    const v = visitantes.find((x) => x.id === id)
    if (!v) return
    setVisitanteSelecionadoId(id)
    setNome(v.nome)
    setTelefone(v.telefone ?? '')
  }

  function montarRespostas(): RespostaRequest[] {
    return campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
  }

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function aoConfirmar() {
    setTentouConfirmar(true)
    if (!nome.trim() || camposObrigatoriosPendentes()) return

    const dados: DadosConvidado = { nome: nome.trim(), telefone }
    if (minha && !minha.inscrito) {
      setAguardandoRespostaParticipar(dados)
      return
    }
    criarConvidado.mutate(
      { nome: dados.nome, telefone: dados.telefone || undefined, respostas: montarRespostas() },
      { onSuccess: () => onClose() },
    )
  }

  function aoResponderTambemVouParticipar(sim: boolean) {
    const dados = aguardandoRespostaParticipar
    setAguardandoRespostaParticipar(null)
    if (!dados) return

    const criar = () => criarConvidado.mutate(
      { nome: dados.nome, telefone: dados.telefone || undefined, respostas: montarRespostas() },
      { onSuccess: () => onClose() },
    )

    if (sim) {
      inscrever.mutate(undefined, { onSuccess: criar, onError: criar })
    } else {
      criar()
    }
  }

  return (
    <>
    <div className={painelStyles.overlay} onMouseDown={() => !isPending && onClose()}>
      <div
        className={painelStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-inscrever-alguem"
      >
        <div className={painelStyles.header}>
          <div>
            <h2 className={painelStyles.titulo} id="titulo-inscrever-alguem">Inscrever alguém</h2>
            <p className={painelStyles.subtitulo}>{tituloEvento}</p>
          </div>
          <button
            type="button"
            className={painelStyles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={isPending}
          >
            <X size={20} />
          </button>
        </div>

        <div className={styles.abas}>
          <button type="button" className={aba === 'pessoas' ? styles.abaAtiva : styles.aba} onClick={() => trocarAba('pessoas')}>
            Pessoas da igreja
          </button>
          <button type="button" className={aba === 'visitantes' ? styles.abaAtiva : styles.aba} onClick={() => trocarAba('visitantes')}>
            Visitantes
          </button>
          <button type="button" className={aba === 'fora' ? styles.abaAtiva : styles.aba} onClick={() => trocarAba('fora')}>
            Pessoa de fora
          </button>
        </div>

        {aba === 'pessoas' && (
          <ModalInscreverPessoas
            eventoId={eventoId}
            tituloEvento={tituloEvento}
            exclusivoMembros={exclusivoMembros}
            onClose={onClose}
            embutido
          />
        )}

        {(aba === 'visitantes' || aba === 'fora') && (
          <>
            <div className={styles.conteudoAba}>
              {aba === 'visitantes' && (
                <>
                  <p className={styles.avisoCamposExtra}>
                    Busque alguém que já está cadastrado como visitante na igreja, ou alguém que
                    está numa célula.
                  </p>
                  <div className={styles.buscaContainer}>
                    <input
                      type="text"
                      className={styles.buscaInput}
                      placeholder="Nome de um visitante já conhecido pela igreja…"
                      value={buscaVisitante}
                      onChange={(e) => { setBuscaVisitante(e.target.value); setVisitanteSelecionadoId(null) }}
                    />
                    {visitantes.length > 0 && !visitanteSelecionadoId && (
                      <div className={styles.listaVisitantes}>
                        {visitantes.map((v) => (
                          <button
                            key={v.id}
                            type="button"
                            className={styles.linhaVisitante}
                            onClick={() => selecionarVisitante(v.id)}
                          >
                            {v.nome}{v.telefone ? ` — ${v.telefone}` : ''}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  {visitanteSelecionadoId && (
                    <p className={styles.selecionado}>Selecionado: {nome}</p>
                  )}
                </>
              )}

              <label className={styles.campo}>
                <span>Nome*</span>
                <input
                  type="text"
                  placeholder="Ex.: Maria Souza"
                  value={nome}
                  onChange={(e) => { setNome(e.target.value); if (aba === 'visitantes') setVisitanteSelecionadoId(null) }}
                />
                {tentouConfirmar && !nome.trim() && <span className={styles.avisoCamposExtra}>O nome é obrigatório.</span>}
              </label>

              <label className={styles.campo}>
                <span>Telefone (opcional)</span>
                <input
                  type="text"
                  placeholder="(00) 00000-0000"
                  inputMode="numeric"
                  value={telefone}
                  onChange={(e) => setTelefone(formatarTelefone(e.target.value))}
                />
              </label>

              {campos.length > 0 && (
                <p className={styles.avisoCamposExtra}>
                  Este evento também pede as informações abaixo.
                </p>
              )}

              {campos.map((campo) => (
                <label key={campo.id} className={styles.campo}>
                  <span>{campo.label}{campo.obrigatorio ? '*' : ''}</span>
                  {campo.tipo === 'OPCAO_UNICA' || campo.tipo === 'SIM_NAO' ? (
                    <select
                      value={camposValores[campo.id] ?? ''}
                      onChange={(e) => setCamposValores((v) => ({ ...v, [campo.id]: e.target.value }))}
                    >
                      <option value="">Selecione…</option>
                      {(campo.tipo === 'SIM_NAO' ? ['Sim', 'Não'] : campo.opcoes).map((op) => (
                        <option key={op} value={op}>{op}</option>
                      ))}
                    </select>
                  ) : campo.tipo === 'MULTIPLA_ESCOLHA' ? (
                    <div className={styles.listaVisitantes}>
                      {campo.opcoes.map((op) => {
                        const selecionadas = (camposValores[campo.id] ?? '').split(' | ').filter(Boolean)
                        const marcado = selecionadas.includes(op)
                        return (
                          <label key={op} className={painelStyles.linha}>
                            <input
                              type="checkbox"
                              checked={marcado}
                              onChange={() => {
                                const novas = marcado ? selecionadas.filter((s) => s !== op) : [...selecionadas, op]
                                setCamposValores((v) => ({ ...v, [campo.id]: novas.join(' | ') }))
                              }}
                            />
                            {op}
                          </label>
                        )
                      })}
                    </div>
                  ) : (
                    <input
                      type="text"
                      placeholder={campo.placeholder ?? ''}
                      value={camposValores[campo.id] ?? ''}
                      onChange={(e) => setCamposValores((v) => ({ ...v, [campo.id]: e.target.value }))}
                    />
                  )}
                  {tentouConfirmar && campo.obrigatorio && !(camposValores[campo.id]?.trim()) && (
                    <span className={styles.avisoCamposExtra}>Essa pergunta é obrigatória.</span>
                  )}
                </label>
              ))}
            </div>

            {aba === 'fora' && (
              <button type="button" className={styles.btnLinkCompartilhar} onClick={() => setCompartilharAberto(true)}>
                <Share2 size={14} aria-hidden="true" />
                Ou compartilhe com quem você quer levar
              </button>
            )}

            <div className={styles.footer}>
              <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={isPending}>
                Cancelar
              </button>
              <button type="button" className={styles.btnConfirmar} onClick={aoConfirmar} disabled={isPending}>
                {isPending ? 'Inscrevendo…' : 'Inscrever'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>

    {compartilharAberto && (
      <ModalCompartilharConvite eventoId={eventoId} onClose={() => setCompartilharAberto(false)} />
    )}

    {aguardandoRespostaParticipar && (
      <ModalConfirmacao
        titulo="Você também vai participar?"
        textoConfirmar="Sim, também vou"
        textoCancelar="Não, só estou cadastrando"
        isLoading={isPending}
        onConfirmar={() => aoResponderTambemVouParticipar(true)}
        onClose={() => aoResponderTambemVouParticipar(false)}
        mensagem={<p>Quer se inscrever nesse evento também, junto com quem você está cadastrando?</p>}
      />
    )}
    </>
  )
}
