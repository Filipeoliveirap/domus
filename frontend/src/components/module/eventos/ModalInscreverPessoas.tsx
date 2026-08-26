'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Search, X, Check, AlertTriangle, ArrowLeft } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useInscreverPessoas } from '@/hooks/inscricao/useInscreverPessoas'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import { useDebounce } from '@/hooks/useDebounce'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { iniciais, rotuloVinculo } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { CamposExtrasForm } from './CamposExtrasForm'
import { ModalCompartilharCobranca } from './ModalCompartilharCobranca'
import type { PessoaResponse } from '@/types/pessoa.type'
import type { Impedimento } from '@/types/inscricao.type'
import styles from './ModalInscreverPessoas.module.css'

interface Props {
  eventoId: string
  tituloEvento: string
  /** Evento exclusivo para membros: só quem tem vínculo MEMBRO pode ser inscrito. */
  exclusivoMembros: boolean
  /** Evento pago habilita o fluxo pessoa a pessoa antes de confirmar. `null`/`undefined`
   *  = evento gratuito, fluxo antigo (seleção múltipla) sem mudança. */
  preco?: number | null
  onClose: () => void
  /** Usado dentro de ModalInscreverAlguem (aba "Pessoas da igreja") — sem overlay nem
   *  cabeçalho próprios, porque o modal pai já mostra os dois. */
  embutido?: boolean
}

function jaInscrita(p: PessoaResponse, jaInscritos: Set<string>): boolean {
  return jaInscritos.has(p.id)
}

// Não bloqueia: EXCLUSIVO_MEMBROS é contornável pelo backend para quem gerencia.
function avisoElegibilidade(p: PessoaResponse, exclusivoMembros: boolean): string | null {
  if (exclusivoMembros && p.vinculo !== 'MEMBRO') {
    return 'Congregante — evento exclusivo para membros'
  }
  return null
}

export function ModalInscreverPessoas({
  eventoId, tituloEvento, exclusivoMembros, preco, onClose, embutido = false,
}: Props) {
  const router = useRouter()
  const [busca, setBusca] = useState('')
  // Evento gratuito: seleção múltipla por checkbox (Map pra guardar o nome no momento da
  // seleção — a lista de busca pode mudar de página/termo depois).
  const [selecionados, setSelecionados] = useState<Map<string, string>>(new Map())
  // Evento pago: uma pessoa por vez.
  const [pessoaClicada, setPessoaClicada] = useState<{ id: string; nome: string } | null>(null)
  // Guarda qual ação foi tentada, pra o retry de "inscrever mesmo assim" (422 contornável)
  // refazer a MESMA escolha — sem isto, o retry sempre viraria "pagar agora".
  const [acaoPendente, setAcaoPendente] = useState<'pagar' | 'link' | null>(null)
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouConfirmarCampos, setTentouConfirmarCampos] = useState(false)
  const [compartilhando, setCompartilhando] = useState<{ nome: string; token: string } | null>(null)
  // A mutation já resolveu (isPending vira false) antes do router.push completar a
  // navegação — sem isto, o botão "pisca" de volta pro texto normal por um instante
  // enquanto a rota de checkout ainda está carregando.
  const [navegandoParaCheckout, setNavegandoParaCheckout] = useState(false)
  // Impedimentos contornáveis devolvidos pelo 422 — abre a confirmação "inscrever mesmo
  // assim" só para quem gerencia. `null` = confirmação fechada.
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const role = useAuthStore((s) => s.role)
  const ehGestor = podeGerenciarInscricoes(role)

  const buscaDebounced = useDebounce(busca, 300)
  const { data, isLoading } = usePessoas({ q: buscaDebounced, page: 0, size: 30 })
  const pessoas = data?.content ?? []

  // Quem já está inscrito precisa aparecer desabilitado. `useParticipantes` é a lista
  // reduzida que QUALQUER pessoa autenticada pode chamar — a completa (`useListaInscritos`)
  // é restrita a ADMIN/LÍDER e devolveria 401 para uma pessoa comum abrindo este modal.
  const { data: participantes = [] } = useParticipantes(eventoId)
  const jaInscritos = useMemo(
    () => new Set(participantes.map((p) => p.pessoaId).filter((id): id is string => id !== null)),
    [participantes],
  )

  // Só importa quando o evento é pago.
  const { data: contaPagamento } = useContaPagamento()
  const { data: campos = [] } = useCamposPersonalizados(eventoId)
  const { responder: responderCampos } = useResponderCampos()

  const inscreverPessoas = useInscreverPessoas(eventoId, {
    onContornavel: ehGestor
      ? (impedimentos) => setImpedimentosParaConfirmar(impedimentos)
      : undefined,
  })

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !inscreverPessoas.isPending && !pessoaClicada && !compartilhando) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, inscreverPessoas.isPending, pessoaClicada, compartilhando])

  function alternarSelecao(p: PessoaResponse) {
    setSelecionados((atual) => {
      const novo = new Map(atual)
      if (novo.has(p.id)) novo.delete(p.id)
      else novo.set(p.id, p.nome)
      return novo
    })
  }

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function voltarParaLista() {
    setPessoaClicada(null)
    setCamposValores({})
    setTentouConfirmarCampos(false)
    setAcaoPendente(null)
  }

  /** Núcleo do fluxo pago pessoa-a-pessoa: cria a inscrição (com ou sem link), anexa
   *  respostas de campos adicionais se houver, e decide o próximo passo. */
  function confirmarPessoa(gerarLink: boolean, confirmado = false) {
    if (!pessoaClicada) return
    if (!confirmado && camposObrigatoriosPendentes()) {
      setTentouConfirmarCampos(true)
      return
    }
    setAcaoPendente(gerarLink ? 'link' : 'pagar')

    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))

    inscreverPessoas.mutate(
      { pessoaIds: [pessoaClicada.id], pessoasParaLink: gerarLink ? [pessoaClicada.id] : undefined, confirmado },
      {
        onSuccess: async (lista) => {
          setImpedimentosParaConfirmar(null)
          const item = lista[0]
          if (campos.length > 0 && item) {
            await responderCampos(item.inscricaoId, respostas)
          }
          if (!item?.cobrancaId) {
            // Não deveria acontecer (evento tem preço), mas não trava o gestor numa tela morta.
            voltarParaLista()
            return
          }
          if (gerarLink) {
            setCompartilhando({ nome: pessoaClicada.nome, token: item.tokenLinkPublico! })
            voltarParaLista()
          } else {
            setNavegandoParaCheckout(true)
            router.push(`/eventos/${eventoId}/pagamento/${item.cobrancaId}`)
          }
        },
        onError: () => setImpedimentosParaConfirmar(null),
      },
    )
  }

  function aoConfirmarSelecaoGratuita() {
    const pessoaIds = Array.from(selecionados.keys())
    inscreverPessoas.mutate({ pessoaIds }, {
      onSuccess: () => {
        setImpedimentosParaConfirmar(null)
        onClose()
      },
      onError: () => setImpedimentosParaConfirmar(null),
    })
  }

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscreverPessoas.isPending}
      onConfirmar={() => {
        if (pessoaClicada) {
          confirmarPessoa(acaoPendente === 'link', true)
        } else {
          inscreverPessoas.mutate({ pessoaIds: Array.from(selecionados.keys()), confirmado: true }, {
            onSuccess: () => { setImpedimentosParaConfirmar(null); onClose() },
          })
        }
      }}
      onClose={() => setImpedimentosParaConfirmar(null)}
      mensagem={
        <>
          <p>
            {(pessoaClicada ? 1 : selecionados.size) === 1
              ? 'Esta pessoa não atende'
              : 'Uma ou mais pessoas selecionadas não atendem'}
            {' '}a todos os requisitos deste evento:
          </p>
          <ul className={styles.listaImpedimentos}>
            {impedimentosParaConfirmar.map((imp) => (
              <li key={imp.codigo}>{imp.mensagem}</li>
            ))}
          </ul>
        </>
      }
    />
  )

  // ---- Evento pago: sem conta MP conectada ----
  if (preco && !contaPagamento?.conectada) {
    const conteudo = (
      <div className={styles.lista}>
        <p className={styles.estado}>
          Este evento é pago, mas a igreja ainda não conectou uma conta para receber
          pagamentos.{' '}
          {ehGestor ? <Link href="/configuracoes/igreja">Conectar agora</Link> : 'Fale com a secretaria da igreja.'}
        </p>
      </div>
    )
    return embutido ? conteudo : (
      <div className={styles.overlay} onMouseDown={() => onClose()}>
        <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
          <div className={styles.header}>
            <div>
              <h2 className={styles.titulo}>Inscrever pessoas</h2>
              <p className={styles.subtitulo}>{tituloEvento}</p>
            </div>
            <button type="button" className={styles.btnFechar} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
          </div>
          {conteudo}
        </div>
      </div>
    )
  }

  // ---- Evento pago: compartilhando o link de uma pessoa ----
  if (preco && compartilhando) {
    return (
      <ModalCompartilharCobranca
        nomePessoa={compartilhando.nome}
        tituloEvento={tituloEvento}
        valor={preco}
        token={compartilhando.token}
        onClose={() => setCompartilhando(null)}
      />
    )
  }

  // ---- Evento pago: painel de uma pessoa (campos adicionais + escolha de pagamento) ----
  if (preco && pessoaClicada) {
    const conteudo = (
      <div className={styles.lista}>
        <button type="button" className={styles.botaoVoltar} onClick={voltarParaLista}>
          <ArrowLeft size={16} aria-hidden="true" />
          Voltar
        </button>

        <div className={styles.painelPessoa}>
          <h3 className={styles.painelTitulo}>Inscrever {pessoaClicada.nome}</h3>

          {campos.length > 0 && (
            <CamposExtrasForm
              campos={campos}
              valores={camposValores}
              onChange={(campoId, valor) => setCamposValores((v) => ({ ...v, [campoId]: valor }))}
              tentouEnviar={tentouConfirmarCampos}
            />
          )}

          <p className={styles.painelValor}>{formatarMoeda(preco)}</p>

          <div className={styles.acoesPagamento}>
            <button
              type="button"
              className={styles.botaoPagar}
              disabled={inscreverPessoas.isPending || navegandoParaCheckout}
              onClick={() => confirmarPessoa(false)}
            >
              {inscreverPessoas.isPending || navegandoParaCheckout ? 'Inscrevendo…' : `Pagar inscrição de ${pessoaClicada.nome}`}
            </button>
            <button
              type="button"
              className={styles.botaoLink}
              disabled={inscreverPessoas.isPending || navegandoParaCheckout}
              onClick={() => confirmarPessoa(true)}
            >
              Enviar link pra {pessoaClicada.nome} pagar
            </button>
          </div>
        </div>
      </div>
    )

    return (
      <>
        {embutido ? conteudo : (
          <div className={styles.overlay} onMouseDown={() => !inscreverPessoas.isPending && onClose()}>
            <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
              <div className={styles.header}>
                <div>
                  <h2 className={styles.titulo}>Inscrever pessoas</h2>
                  <p className={styles.subtitulo}>{tituloEvento}</p>
                </div>
                <button type="button" className={styles.btnFechar} onClick={onClose} aria-label="Fechar" disabled={inscreverPessoas.isPending}><X size={20} /></button>
              </div>
              {conteudo}
            </div>
          </div>
        )}
        {modalContorno}
      </>
    )
  }

  // ---- Lista: sem checkbox se pago, com checkbox se gratuito ----
  const listaConteudo = (
    <div className={styles.lista}>
      {isLoading ? (
        <p className={styles.estado}>Carregando pessoas…</p>
      ) : pessoas.length === 0 ? (
        <p className={styles.estado}>Nenhuma pessoa encontrada.</p>
      ) : (
        pessoas.map((p) => {
          const bloqueado = jaInscrita(p, jaInscritos)
          const aviso = !bloqueado ? avisoElegibilidade(p, exclusivoMembros) : null
          const marcado = !preco && selecionados.has(p.id)
          return (
            <label
              key={p.id}
              className={[
                styles.linha,
                marcado ? styles.linhaSelecionada : '',
                bloqueado ? styles.linhaBloqueada : '',
              ].join(' ')}
              onClick={(e) => {
                if (preco && !bloqueado) {
                  e.preventDefault()
                  setPessoaClicada({ id: p.id, nome: p.nome })
                }
              }}
            >
              {!preco && (
                <input
                  type="checkbox"
                  className={styles.checkbox}
                  checked={marcado}
                  disabled={bloqueado}
                  onChange={() => alternarSelecao(p)}
                />
              )}
              <span className={styles.avatar}>
                {urlFoto(p.fotoId, 'THUMB') ? (
                  <Image src={urlFoto(p.fotoId, 'THUMB')!} alt="" width={36} height={36} unoptimized className={styles.avatarFoto} />
                ) : (
                  iniciais(p.nome)
                )}
              </span>
              <span className={styles.info}>
                <span className={styles.nome}>{p.nome}</span>
                <span className={styles.detalhe}>
                  {bloqueado ? 'Já inscrita neste evento' : (aviso ?? rotuloVinculo(p.vinculo))}
                </span>
              </span>
              {aviso && !bloqueado && (
                <AlertTriangle size={15} className={styles.avisoIcone} aria-label="Pode não ser elegível para este evento" />
              )}
              {marcado && <Check size={16} className={styles.checkIcone} aria-hidden="true" />}
            </label>
          )
        })
      )}
    </div>
  )

  const conteudo = (
    <>
      {!embutido && (
        <div className={styles.header}>
          <div>
            <h2 className={styles.titulo} id="titulo-inscrever-pessoas">Inscrever pessoas</h2>
            <p className={styles.subtitulo}>{tituloEvento}</p>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={inscreverPessoas.isPending}
          >
            <X size={20} />
          </button>
        </div>
      )}

      <div className={styles.buscaWrap}>
        <Search size={16} className={styles.buscaIcone} />
        <input
          ref={inputRef}
          type="text"
          className={styles.buscaInput}
          placeholder="Buscar por nome…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      </div>

      {listaConteudo}

      {!preco && (
        <div className={styles.footer}>
          <span className={styles.contador}>
            {selecionados.size} selecionado{selecionados.size === 1 ? '' : 's'}
          </span>
          <div className={styles.footerAcoes}>
            <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={inscreverPessoas.isPending}>
              Cancelar
            </button>
            <button
              type="button"
              className={styles.btnConfirmar}
              onClick={aoConfirmarSelecaoGratuita}
              disabled={selecionados.size === 0 || inscreverPessoas.isPending}
            >
              {inscreverPessoas.isPending ? 'Inscrevendo…' : 'Inscrever'}
            </button>
          </div>
        </div>
      )}
    </>
  )

  return (
    <>
      {embutido ? conteudo : (
        <div className={styles.overlay} onMouseDown={() => !inscreverPessoas.isPending && onClose()}>
          <div
            className={styles.modal}
            onMouseDown={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="titulo-inscrever-pessoas"
          >
            {conteudo}
          </div>
        </div>
      )}
      {modalContorno}
    </>
  )
}
