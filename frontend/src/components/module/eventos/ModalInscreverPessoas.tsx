'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { Search, X, Check, AlertTriangle } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useInscreverPessoas } from '@/hooks/inscricao/useInscreverPessoas'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useDebounce } from '@/hooks/useDebounce'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { iniciais, rotuloVinculo } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { EscolhaPagamentoPorPessoa, type EscolhaPagamento } from './EscolhaPagamentoPorPessoa'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { ModalCompartilharCobranca } from './ModalCompartilharCobranca'
import type { PessoaResponse } from '@/types/pessoa.type'
import type { Impedimento, PessoaInscritaComCobranca } from '@/types/inscricao.type'
import styles from './ModalInscreverPessoas.module.css'
import escolhaStyles from './EscolhaPagamentoPorPessoa.module.css'

interface Props {
  eventoId: string
  tituloEvento: string
  /** Evento exclusivo para membros: só quem tem vínculo MEMBRO pode ser inscrito. */
  exclusivoMembros: boolean
  /** Task 14 (revisão pós-review) — evento pago habilita a etapa "Divisão de pagamento"
   *  antes de confirmar. `null`/`undefined` = evento gratuito, fluxo antigo sem mudança. */
  preco?: number | null
  onClose: () => void
  /** Usado dentro de ModalInscreverAlguem (aba "Pessoas da igreja") — sem overlay nem
   *  cabeçalho próprios, porque o modal pai já mostra os dois. */
  embutido?: boolean
}

type Etapa = 'selecao' | 'pagamento' | 'checkout' | 'compartilhar' | 'sem-conta'

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

// Sem "selecionar todos" de propósito: evita inscrição em massa por engano.
export function ModalInscreverPessoas({
  eventoId, tituloEvento, exclusivoMembros, preco, onClose, embutido = false,
}: Props) {
  const [busca, setBusca] = useState('')
  // Map (não Set) porque as etapas seguintes (pagamento/checkout/compartilhar) precisam
  // mostrar o NOME de cada pessoa — e a lista de busca (usePessoas) pode já ter mudado de
  // página/termo quando chegamos lá, então guardamos o nome no momento da seleção.
  const [selecionados, setSelecionados] = useState<Map<string, string>>(new Map())
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

  // Task 14: só importa quando o evento é pago (ver etapa 'sem-conta' abaixo).
  const { data: contaPagamento } = useContaPagamento()

  // Só o gestor pode contornar, então só ele passa o callback. Para os demais, o hook
  // notifica o 422 normalmente (era aqui que o erro sumia em silêncio antes).
  const inscreverPessoas = useInscreverPessoas(eventoId, {
    onContornavel: ehGestor
      ? (impedimentos) => setImpedimentosParaConfirmar(impedimentos)
      : undefined,
  })

  // ---- Task 14: fluxo de pagamento pós-seleção ----
  const [etapa, setEtapa] = useState<Etapa>('selecao')
  const [escolhasPagamento, setEscolhasPagamento] = useState<Record<string, EscolhaPagamento> | null>(null)
  const [resultados, setResultados] = useState<PessoaInscritaComCobranca[] | null>(null)
  const [indiceCheckout, setIndiceCheckout] = useState(0)
  const [indiceCompartilhar, setIndiceCompartilhar] = useState(0)

  // Quem ficou "eu pago agora" (Brick, um de cada vez) e quem ficou "gerar link"
  // (ModalCompartilharCobranca, um de cada vez) — derivado da RESPOSTA do backend, não da
  // escolha enviada: a pessoa que fez a ação nunca vira link mesmo que peça (Task 9), então
  // confiar na resposta real evita mostrar um link que na verdade não existe.
  const pagarAgora = useMemo(
    () => (resultados ?? []).filter((r) => r.cobrancaId && !r.tokenLinkPublico),
    [resultados],
  )
  const comLink = useMemo(
    () => (resultados ?? []).filter((r) => r.tokenLinkPublico),
    [resultados],
  )

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !inscreverPessoas.isPending && etapa === 'selecao') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, inscreverPessoas.isPending, etapa])

  function alternarSelecao(p: PessoaResponse) {
    setSelecionados((atual) => {
      const novo = new Map(atual)
      if (novo.has(p.id)) novo.delete(p.id)
      else novo.set(p.id, p.nome)
      return novo
    })
  }

  /** Avança pra etapa que faz sentido depois de a inscrição em lote responder — pula
   *  etapas vazias em vez de mostrar uma tela de checkout/compartilhar sem nada nela. */
  function avancarAposInscricao(lista: PessoaInscritaComCobranca[]) {
    const temPagarAgora = lista.some((r) => r.cobrancaId && !r.tokenLinkPublico)
    const temLink = lista.some((r) => r.tokenLinkPublico)
    if (temPagarAgora) {
      setIndiceCheckout(0)
      setEtapa('checkout')
    } else if (temLink) {
      setIndiceCompartilhar(0)
      setEtapa('compartilhar')
    } else {
      onClose()
    }
  }

  function chamarInscricao(escolhas: Record<string, EscolhaPagamento> | undefined, confirmado = false) {
    const pessoaIds = Array.from(selecionados.keys())
    const pessoasParaLink = escolhas
      ? pessoaIds.filter((id) => escolhas[id] === 'GERAR_LINK')
      : undefined
    inscreverPessoas.mutate({ pessoaIds, confirmado, pessoasParaLink }, {
      onSuccess: (lista) => {
        setImpedimentosParaConfirmar(null)
        if (!preco) {
          onClose()
          return
        }
        setResultados(lista)
        avancarAposInscricao(lista)
      },
      onError: () => setImpedimentosParaConfirmar(null),
    })
  }

  function aoConfirmar() {
    if (preco) {
      // Sem conta MP conectada, o Brick nem carregaria (não há pra quem receber) — aviso
      // com atalho em vez de deixar a pessoa escolher uma forma de pagamento que ia falhar.
      setEtapa(contaPagamento?.conectada ? 'pagamento' : 'sem-conta')
      return
    }
    chamarInscricao(undefined)
  }

  /** "Inscrever mesmo assim": reenvia com `confirmado=true` (só surte efeito para gestor). */
  function aoConfirmarMesmoAssim() {
    chamarInscricao(escolhasPagamento ?? undefined, true)
  }

  // ---- Etapa: Divisão de pagamento ----
  if (etapa === 'pagamento' || etapa === 'sem-conta') {
    const conteudoPagamento = etapa === 'sem-conta' ? (
      <div className={styles.lista}>
        <div className={escolhaStyles.wrapper}>
          <h3 className={escolhaStyles.titulo}>Conecte uma conta de pagamento</h3>
          <p className={escolhaStyles.subtitulo}>
            Este evento é pago, mas a igreja ainda não conectou uma conta para receber
            pagamentos.{' '}
            {ehGestor ? (
              <Link href="/configuracoes/igreja">Conectar agora</Link>
            ) : (
              'Fale com a secretaria da igreja.'
            )}
          </p>
        </div>
      </div>
    ) : (
      <div className={styles.lista}>
        <EscolhaPagamentoPorPessoa
          pessoas={Array.from(selecionados.entries()).map(([id, nome]) => ({
            id, nome, valor: preco ?? 0, ehTitular: false,
          }))}
          isLoading={inscreverPessoas.isPending}
          onConfirmar={(escolhas) => {
            setEscolhasPagamento(escolhas)
            chamarInscricao(escolhas)
          }}
        />
      </div>
    )

    return (
      <>
        {embutido ? conteudoPagamento : (
          <div className={styles.overlay} onMouseDown={() => !inscreverPessoas.isPending && onClose()}>
            <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
              <div className={styles.header}>
                <div>
                  <h2 className={styles.titulo}>Inscrever pessoas</h2>
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
              {conteudoPagamento}
            </div>
          </div>
        )}

        {impedimentosParaConfirmar && (
          <ModalConfirmacao
            titulo="Inscrever mesmo assim?"
            textoConfirmar="Inscrever mesmo assim"
            isLoading={inscreverPessoas.isPending}
            onConfirmar={aoConfirmarMesmoAssim}
            onClose={() => setImpedimentosParaConfirmar(null)}
            mensagem={
              <>
                <p>
                  {selecionados.size === 1 ? 'Esta pessoa não atende' : 'Uma ou mais pessoas selecionadas não atendem'}
                  {' '}a todos os requisitos deste evento:
                </p>
                <ul>
                  {impedimentosParaConfirmar.map((imp) => (
                    <li key={imp.codigo}>{imp.mensagem}</li>
                  ))}
                </ul>
              </>
            }
          />
        )}
      </>
    )
  }

  // ---- Etapa: checkout sequencial (Payment Brick, uma pessoa de cada vez) ----
  if (etapa === 'checkout') {
    const atual = pagarAgora[indiceCheckout]
    const nome = atual ? selecionados.get(atual.pessoaId) ?? 'Pessoa' : ''

    function proximoCheckout() {
      if (indiceCheckout + 1 < pagarAgora.length) {
        setIndiceCheckout((i) => i + 1)
      } else if (comLink.length > 0) {
        setIndiceCompartilhar(0)
        setEtapa('compartilhar')
      } else {
        onClose()
      }
    }

    const conteudo = atual ? (
      <div className={styles.lista}>
        <p className={styles.contadorCheckout}>
          Pagando por {nome} ({indiceCheckout + 1} de {pagarAgora.length})
        </p>
        <PaymentBrickCheckout
          cobrancaId={atual.cobrancaId!}
          valor={preco ?? 0}
          onPagamentoCriado={proximoCheckout}
        />
      </div>
    ) : null

    return embutido ? conteudo : (
      <div className={styles.overlay}>
        <div className={styles.modal} role="dialog" aria-modal="true">
          <div className={styles.header}>
            <div>
              <h2 className={styles.titulo}>Pagamento</h2>
              <p className={styles.subtitulo}>{tituloEvento}</p>
            </div>
          </div>
          {conteudo}
        </div>
      </div>
    )
  }

  // ---- Etapa: compartilhar link sequencial (uma pessoa de cada vez) ----
  if (etapa === 'compartilhar') {
    const atual = comLink[indiceCompartilhar]
    if (!atual) {
      onClose()
      return null
    }
    const nome = selecionados.get(atual.pessoaId) ?? 'Pessoa'

    return (
      <ModalCompartilharCobranca
        nomePessoa={nome}
        tituloEvento={tituloEvento}
        valor={preco ?? 0}
        token={atual.tokenLinkPublico!}
        onClose={() => {
          if (indiceCompartilhar + 1 < comLink.length) {
            setIndiceCompartilhar((i) => i + 1)
          } else {
            onClose()
          }
        }}
      />
    )
  }

  // ---- Etapa: seleção (fluxo original) ----
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

        <div className={styles.lista}>
          {isLoading ? (
            <p className={styles.estado}>Carregando pessoas…</p>
          ) : pessoas.length === 0 ? (
            <p className={styles.estado}>Nenhuma pessoa encontrada.</p>
          ) : (
            pessoas.map((p) => {
              const bloqueado = jaInscrita(p, jaInscritos)
              const aviso = !bloqueado ? avisoElegibilidade(p, exclusivoMembros) : null
              const marcado = selecionados.has(p.id)
              return (
                <label
                  key={p.id}
                  className={[
                    styles.linha,
                    marcado ? styles.linhaSelecionada : '',
                    bloqueado ? styles.linhaBloqueada : '',
                  ].join(' ')}
                >
                  <input
                    type="checkbox"
                    className={styles.checkbox}
                    checked={marcado}
                    disabled={bloqueado}
                    onChange={() => alternarSelecao(p)}
                  />
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
                      {bloqueado
                        ? 'Já inscrita neste evento'
                        : (aviso ?? rotuloVinculo(p.vinculo))}
                    </span>
                  </span>
                  {aviso && !bloqueado && (
                    <AlertTriangle
                      size={15}
                      className={styles.avisoIcone}
                      aria-label="Pode não ser elegível para este evento"
                    />
                  )}
                  {marcado && <Check size={16} className={styles.checkIcone} aria-hidden="true" />}
                </label>
              )
            })
          )}
        </div>

        <div className={styles.footer}>
          <span className={styles.contador}>
            {selecionados.size} selecionado{selecionados.size === 1 ? '' : 's'}
          </span>
          <div className={styles.footerAcoes}>
            <button
              type="button"
              className={styles.btnCancelar}
              onClick={onClose}
              disabled={inscreverPessoas.isPending}
            >
              Cancelar
            </button>
            <button
              type="button"
              className={styles.btnConfirmar}
              onClick={aoConfirmar}
              disabled={selecionados.size === 0 || inscreverPessoas.isPending}
            >
              {inscreverPessoas.isPending ? 'Inscrevendo…' : 'Inscrever'}
            </button>
          </div>
        </div>
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

    {impedimentosParaConfirmar && (
      <ModalConfirmacao
        titulo="Inscrever mesmo assim?"
        textoConfirmar="Inscrever mesmo assim"
        isLoading={inscreverPessoas.isPending}
        onConfirmar={aoConfirmarMesmoAssim}
        onClose={() => setImpedimentosParaConfirmar(null)}
        mensagem={
          <>
            <p>
              {selecionados.size === 1 ? 'Esta pessoa não atende' : 'Uma ou mais pessoas selecionadas não atendem'}
              {' '}a todos os requisitos deste evento:
            </p>
            <ul>
              {impedimentosParaConfirmar.map((imp) => (
                <li key={imp.codigo}>{imp.mensagem}</li>
              ))}
            </ul>
          </>
        }
      />
    )}
    </>
  )
}
