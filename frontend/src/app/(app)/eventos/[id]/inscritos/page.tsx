'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useParams, useRouter } from 'next/navigation'
import { ChevronRight, Users, Ticket, Armchair, UserPlus, ArrowLeft } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useEvento } from '@/hooks/evento/useEvento'
import { useListaInscritos } from '@/hooks/inscricao/useListaInscritos'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useRemoverConvidado } from '@/hooks/inscricao/useRemoverConvidado'
import { ModalInscreverPessoas } from '@/components/module/eventos/ModalInscreverPessoas'
import { ConfirmarCancelamentoInscricao } from '@/components/module/eventos/ConfirmarCancelamentoInscricao'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import { podeVerListaCompletaDeInscritos } from '@/lib/permissoes'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { formatarData } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import type { InscritoResponse } from '@/types/inscricao.type'
import styles from './inscritos.module.css'

export default function InscritosPage() {
  const params = useParams()
  const eventoId = params.id as string
  const router = useRouter()

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeVerListaCompletaDeInscritos(role)

  const { data: evento } = useEvento(eventoId)
  const { data: lista, isPending, isError, refetch } = useListaInscritos(eventoId, autorizado)

  const [modalInscreverAberto, setModalInscreverAberto] = useState(false)
  const [inscritoCancelando, setInscritoCancelando] = useState<InscritoResponse | null>(null)
  const [convidadoCancelando, setConvidadoCancelando] = useState<{ id: string; nome: string } | null>(null)

  const cancelarInscricao = useCancelarInscricao()
  const removerConvidado = useRemoverConvidado()

  // A2/rodada 3: o backend recusa cancelar fora de AGENDADO, mesmo para ADMIN/LÍDER —
  // presença em evento em andamento/encerrado é histórico, não algo que se desfaz aqui.
  const podeCancelar = evento ? podeCancelarInscricao(evento.situacao) : true

  if (!hidratado) {
    return <div className={styles.pagina} />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  function aoConfirmarCancelamento() {
    if (!inscritoCancelando) return
    cancelarInscricao.mutate(inscritoCancelando.id, {
      onSuccess: () => setInscritoCancelando(null),
    })
  }

  function aoConfirmarRemocaoConvidado() {
    if (!convidadoCancelando) return
    removerConvidado.mutate(convidadoCancelando.id, {
      onSuccess: () => setConvidadoCancelando(null),
    })
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Inscritos</span>
      </nav>

      <header className={styles.cabecalho}>
        <div className={styles.cabecalhoTextos}>
          {/*
            Botão de voltar além do breadcrumb: esta tela costuma ser aberta a partir de um
            card de evento, e a rota de volta ("eventos") nem sempre é de onde a pessoa veio.
          */}
          <button
            type="button"
            className={styles.voltar}
            onClick={() => router.back()}
          >
            <ArrowLeft size={16} aria-hidden="true" />
            Voltar
          </button>
          {evento && <span className={styles.eventoTitulo}>{evento.titulo}</span>}
          <h1 className={styles.titulo}>Lista de Inscritos</h1>
        </div>
        <button
          type="button"
          className={styles.botaoPrimario}
          onClick={() => setModalInscreverAberto(true)}
        >
          <UserPlus size={18} />
          Nova Inscrição
        </button>
      </header>

      {isPending ? (
        <div className={styles.painel}>
          <p className={styles.estado}>Carregando inscritos…</p>
        </div>
      ) : isError || !lista ? (
        <div className={styles.painel}>
          <EstadoErro
            titulo="Não foi possível carregar os inscritos"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        </div>
      ) : (
        <>
          {/* ─── Estatísticas ─── */}
          <div className={styles.stats}>
            <div className={styles.statCard}>
              <span className={styles.statIcone}><Users size={18} /></span>
              <div>
                <p className={styles.statValor}>{lista.totalPessoas}</p>
                <p className={styles.statLabel}>Total de pessoas</p>
              </div>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statIcone}><Ticket size={18} /></span>
              <div>
                <p className={styles.statValor}>{lista.vagas ?? 'Sem limite'}</p>
                <p className={styles.statLabel}>Vagas</p>
              </div>
            </div>
            {lista.vagas != null && (
              <div className={styles.statCard}>
                <span className={styles.statIcone}><Armchair size={18} /></span>
                <div>
                  <p className={styles.statValor}>{lista.vagasRestantes}</p>
                  <p className={styles.statLabel}>Vagas restantes</p>
                </div>
              </div>
            )}
          </div>

          {/* ─── Tabela ─── */}
          <div className={styles.painel}>
            {lista.inscritos.length === 0 ? (
              <EstadoVazio
                icone={Users}
                titulo="Ninguém se inscreveu ainda"
                acaoPrimaria={{ label: 'Nova Inscrição', onClick: () => setModalInscreverAberto(true) }}
              />
            ) : (
              <>
                <div className={styles.tabelaHeader}>
                  <span className={styles.colParticipante}>PARTICIPANTE</span>
                  <span className={styles.colData}>DATA</span>
                  <span className={styles.colInscritoPor}>INSCRITO POR</span>
                  <span className={styles.colConvidados}>CONVIDADOS</span>
                  <span className={styles.colAcoes}>AÇÕES</span>
                </div>

                <div className={styles.linhas}>
                  {lista.inscritos.map((inscrito) => (
                    <div key={inscrito.id} className={styles.grupo}>
                      <div className={styles.linha}>
                        <div className={styles.colParticipante}>
                          <span className={styles.avatar}>
                            {urlFoto(inscrito.fotoId, 'THUMB') ? (
                              // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                              <img src={urlFoto(inscrito.fotoId, 'THUMB')!} alt="" className={styles.avatarFoto} />
                            ) : (
                              iniciais(inscrito.nome)
                            )}
                          </span>
                          <span className={styles.nome}>{inscrito.nome}</span>
                        </div>
                        <div className={styles.colData}>{formatarData(inscrito.inscritoEm)}</div>
                        <div className={styles.colInscritoPor}>
                          {inscrito.inscritoPorUsuarioId === null ? (
                            <span className={styles.textoMuted}>Ele mesmo</span>
                          ) : inscrito.inscritoPorNome ? (
                            <span className={styles.inscritoPor}>
                              <span className={styles.avatarInscritoPor}>
                                {urlFoto(inscrito.inscritoPorFotoId, 'THUMB') ? (
                                  // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                                  <img src={urlFoto(inscrito.inscritoPorFotoId, 'THUMB')!} alt="" className={styles.avatarFoto} />
                                ) : (
                                  iniciais(inscrito.inscritoPorNome)
                                )}
                              </span>
                              <span>{inscrito.inscritoPorNome}</span>
                            </span>
                          ) : (
                            <span className={styles.textoMuted}>Cadastro removido</span>
                          )}
                        </div>
                        <div className={styles.colConvidados}>
                          {inscrito.acompanhantes.length > 0 ? inscrito.acompanhantes.length : '—'}
                        </div>
                        <div className={styles.colAcoes}>
                          {podeCancelar ? (
                            <button
                              type="button"
                              className={styles.btnCancelar}
                              onClick={() => setInscritoCancelando(inscrito)}
                            >
                              Cancelar
                            </button>
                          ) : (
                            <span className={styles.textoMuted}>Participou</span>
                          )}
                        </div>
                      </div>

                      {inscrito.acompanhantes.map((convidado) => (
                        <div key={convidado.id} className={styles.linhaConvidado}>
                          <span className={styles.conector} aria-hidden="true" />
                          <div className={styles.colParticipante}>
                            <span className={styles.avatarConvidado}>{iniciais(convidado.nome)}</span>
                            <span className={styles.nome}>{convidado.nome}</span>
                            <span className={styles.pillConvidado}>Convidado</span>
                          </div>
                          <div className={styles.colData} />
                          <div className={styles.colInscritoPor} />
                          <div className={styles.colConvidados} />
                          <div className={styles.colAcoes}>
                            {podeCancelar ? (
                              <button
                                type="button"
                                className={styles.btnCancelar}
                                onClick={() => setConvidadoCancelando({ id: convidado.id, nome: convidado.nome })}
                              >
                                Remover
                              </button>
                            ) : (
                              <span className={styles.textoMuted}>Participou</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </>
      )}

      {modalInscreverAberto && evento && (
        <ModalInscreverPessoas
          eventoId={eventoId}
          tituloEvento={evento.titulo}
          exclusivoMembros={evento.exclusivoMembros}
          onClose={() => setModalInscreverAberto(false)}
        />
      )}

      {inscritoCancelando && (
        <ConfirmarCancelamentoInscricao
          nome={inscritoCancelando.nome}
          proprio={false}
          quantidadeConvidados={inscritoCancelando.acompanhantes.length}
          isLoading={cancelarInscricao.isPending}
          onConfirmar={aoConfirmarCancelamento}
          onClose={() => setInscritoCancelando(null)}
        />
      )}

      {convidadoCancelando && (
        <div className={styles.confirmInlineOverlay} onMouseDown={() => setConvidadoCancelando(null)}>
          <div className={styles.confirmInline} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <p className={styles.confirmInlineTexto}>
              Remover <strong>{convidadoCancelando.nome}</strong> da lista de convidados?
            </p>
            <div className={styles.confirmInlineAcoes}>
              <button
                type="button"
                className={styles.btnCancelar}
                onClick={() => setConvidadoCancelando(null)}
                disabled={removerConvidado.isPending}
              >
                Não
              </button>
              <button
                type="button"
                className={styles.btnConfirmarInline}
                onClick={aoConfirmarRemocaoConvidado}
                disabled={removerConvidado.isPending}
              >
                {removerConvidado.isPending ? 'Removendo…' : 'Sim, remover'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
