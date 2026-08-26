'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { X, Share2 } from 'lucide-react'
import { ModalInscreverPessoas } from './ModalInscreverPessoas'
import { ModalCompartilharConvite } from './ModalCompartilharConvite'
import { ModalCompartilharCobranca } from './ModalCompartilharCobranca'
import { useVisitantesBuscaLeve } from '@/hooks/visitante/useVisitantesBuscaLeve'
import { useCriarConvidado } from '@/hooks/inscricao/useCriarConvidado'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
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
  /** Evento pago habilita a escolha de pagamento nas abas Visitantes/Pessoa de fora,
   *  além de "Pessoas da igreja" (ModalInscreverPessoas). */
  preco?: number | null
  onClose: () => void
}

export function ModalInscreverAlguem({ eventoId, tituloEvento, exclusivoMembros, preco, onClose }: Props) {
  const router = useRouter()
  const [aba, setAba] = useState<Aba>('pessoas')

  const [buscaVisitante, setBuscaVisitante] = useState('')
  const buscaDebounced = useDebounce(buscaVisitante, 300)
  const { data: visitantes = [] } = useVisitantesBuscaLeve(buscaDebounced)
  const [visitanteSelecionadoId, setVisitanteSelecionadoId] = useState<string | null>(null)

  // Quem já está inscrito neste evento precisa aparecer bloqueado na busca — mesmo padrão
  // da aba "Pessoas da igreja" (ver ModalInscreverPessoas), agora possível pra visitantes
  // graças ao vínculo visitante_id na inscrição.
  const { data: participantes = [] } = useParticipantes(eventoId)
  const visitantesJaInscritos = useMemo(
    () => new Set(participantes.map((p) => p.visitanteId).filter((id): id is string => id !== null)),
    [participantes],
  )

  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouConfirmar, setTentouConfirmar] = useState(false)

  const { data: campos = [] } = useCamposPersonalizados(eventoId)
  const criarConvidado = useCriarConvidado(eventoId)

  const [compartilharAberto, setCompartilharAberto] = useState(false)
  // Plano 4b: link de cobrança gerado pra um convidado (evento pago, "enviar link").
  const [compartilhandoCobranca, setCompartilhandoCobranca] = useState<{ nome: string; token: string } | null>(null)

  const isPending = criarConvidado.isPending

  function limparFormulario() {
    setNome('')
    setTelefone('')
    setVisitanteSelecionadoId(null)
    setBuscaVisitante('')
    setCamposValores({})
    setTentouConfirmar(false)
  }

  /** Troca de aba limpa nome/telefone/campos — sem isso, selecionar um visitante e depois
   *  ir pra "Pessoa de fora" deixava os dados dele preenchidos lá, como se já tivessem sido
   *  digitados pra outra pessoa. */
  function trocarAba(novaAba: Aba) {
    setAba(novaAba)
    limparFormulario()
  }

  function selecionarVisitante(id: string) {
    if (visitantesJaInscritos.has(id)) return
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

  function telefoneValido(): boolean {
    const digitos = telefone.replace(/\D/g, '')
    return digitos.length === 10 || digitos.length === 11
  }

  /** Evento gratuito: sempre chamado sem gerarLink (irrelevante). Evento pago: chamado
   *  duas vezes possíveis, uma por botão ("Pagar inscrição"/"Enviar link"). */
  function confirmar(gerarLink: boolean) {
    setTentouConfirmar(true)
    if (!nome.trim() || !telefoneValido() || camposObrigatoriosPendentes()) return

    const visitanteId = aba === 'visitantes' ? visitanteSelecionadoId ?? undefined : undefined
    const nomeConfirmado = nome.trim()
    criarConvidado.mutate(
      { nome: nomeConfirmado, telefone: telefone.replace(/\D/g, ''), visitanteId, respostas: montarRespostas(), gerarLink },
      {
        onSuccess: (resposta) => {
          if (!resposta.cobrancaId) {
            // Evento gratuito — fluxo antigo, sem escolha de pagamento.
            onClose()
            return
          }
          if (gerarLink) {
            setCompartilhandoCobranca({ nome: nomeConfirmado, token: resposta.tokenLinkPublico! })
            limparFormulario()
          } else {
            router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaId}`)
          }
        },
      },
    )
  }

  if (compartilhandoCobranca) {
    return (
      <ModalCompartilharCobranca
        nomePessoa={compartilhandoCobranca.nome}
        tituloEvento={tituloEvento}
        valor={preco ?? 0}
        token={compartilhandoCobranca.token}
        onClose={() => setCompartilhandoCobranca(null)}
      />
    )
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
            preco={preco}
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
                        {visitantes.map((v) => {
                          const bloqueado = visitantesJaInscritos.has(v.id)
                          return (
                            <button
                              key={v.id}
                              type="button"
                              className={`${styles.linhaVisitante} ${bloqueado ? styles.linhaVisitanteBloqueada : ''}`}
                              onClick={() => selecionarVisitante(v.id)}
                              disabled={bloqueado}
                            >
                              {v.nome}{v.telefone ? ` — ${v.telefone}` : ''}
                              {bloqueado && <span className={styles.avisoBloqueado}>Já inscrito neste evento</span>}
                            </button>
                          )
                        })}
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
                {tentouConfirmar && !nome.trim() && <span className={styles.avisoErro}>O nome é obrigatório.</span>}
              </label>

              <label className={styles.campo}>
                <span>Telefone*</span>
                <input
                  type="text"
                  placeholder="(00) 00000-0000"
                  inputMode="numeric"
                  value={telefone}
                  onChange={(e) => setTelefone(formatarTelefone(e.target.value))}
                />
                {tentouConfirmar && !telefone.trim() && (
                  <span className={styles.avisoErro}>O telefone é obrigatório.</span>
                )}
                {tentouConfirmar && telefone.trim() && !telefoneValido() && (
                  <span className={styles.avisoErro}>Telefone inválido. Digite um número válido com DDD.</span>
                )}
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
                    <span className={styles.avisoErro}>Essa pergunta é obrigatória.</span>
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
              {preco ? (
                <div className={styles.acoesPagamentoConvidado}>
                  <button type="button" className={styles.btnConfirmar} onClick={() => confirmar(false)} disabled={isPending}>
                    {isPending ? 'Inscrevendo…' : `Pagar inscrição${nome.trim() ? ` de ${nome.trim()}` : ''}`}
                  </button>
                  <button type="button" className={styles.btnEnviarLink} onClick={() => confirmar(true)} disabled={isPending}>
                    Enviar link pra pagar
                  </button>
                </div>
              ) : (
                <button type="button" className={styles.btnConfirmar} onClick={() => confirmar(false)} disabled={isPending}>
                  {isPending ? 'Inscrevendo…' : 'Inscrever'}
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>

    {compartilharAberto && (
      <ModalCompartilharConvite eventoId={eventoId} onClose={() => setCompartilharAberto(false)} />
    )}
    </>
  )
}
