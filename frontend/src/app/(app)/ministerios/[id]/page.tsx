'use client'

import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { ChevronRight, Check, X as XIcon, UserPlus, UserMinus, Crown, Star, Users, Archive, ArrowLeft } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterioDetalhe } from '@/hooks/ministerio/useMinisterioDetalhe'
import { useRemoverMembro, useAtualizarPapel } from '@/hooks/ministerio/useMembroMinisterio'
import { usePedirEntrada, useAceitarPedido, useRecusarPedido } from '@/hooks/ministerio/usePedidoMinisterio'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import { ModalAdicionarMembro } from './ModalAdicionarMembro'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './detalhe.module.css'

// Todas as mutations usadas aqui (useRemoverMembro, useAtualizarPapel, usePedirEntrada,
// useAceitarPedido, useRecusarPedido) já disparam notificar.sucesso/erro sozinhas (Task 9)
// — este componente só chama .mutate()/.mutateAsync(), sem repetir o toast.
export default function MinisterioDetalhePage() {
  const { id } = useParams<{ id: string }>()
  const role = useAuthStore((s) => s.role)
  const isAdmin = podeGerenciarCadastroMinisterios(role)

  const { data: ministerio, isLoading } = useMinisterioDetalhe(id)
  const removerMembro = useRemoverMembro(id)
  const atualizarPapel = useAtualizarPapel(id)
  const pedirEntrada = usePedirEntrada(id)
  const aceitarPedido = useAceitarPedido(id)
  const recusarPedido = useRecusarPedido(id)

  const [adicionarAberto, setAdicionarAberto] = useState(false)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)

  if (isLoading || !ministerio) {
    return (
      <div className={styles.pagina}>
        <Skeleton width="250px" height="32px" />
        <Skeleton width="180px" height="16px" />
        <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[1,2,3,4,5].map(i => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0' }}>
              <Skeleton width="40px" height="40px" circle />
              <Skeleton width="150px" height="16px" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  // souMembroAtivo/tenhoPedidoPendente vêm prontos do backend (GET /ministerios/{id}) —
  // o authStore não guarda pessoaId, só usuarioId/role, então o cálculo é feito no
  // service (MinisterioService.detalhe), que já sabe a pessoa logada via UsuarioAutenticado.
  const souMembro = ministerio.souMembroAtivo
  const jaTemPedido = ministerio.tenhoPedidoPendente
  const podeGerenciarMembros = ministerio.souLiderDesteMinisterio

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/ministerios" className={styles.breadcrumbLink}>{ROTULO_MINISTERIO_PLURAL}</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{ministerio.nome}</span>
      </nav>

      {ministerio.arquivada && (
        <Link href="/ministerios/arquivados" className={styles.avisoArquivada}>
          <ArrowLeft size={16} />
          <Archive size={16} />
          <span>Esta {ROTULO_MINISTERIO.toLowerCase()} está arquivada. Toque para restaurá-la na lista de arquivadas.</span>
        </Link>
      )}

      <header className={styles.cabecalho}>
        <div className={styles.fotoDetalhe}>
          {ministerio.fotoId ? (
            <img src={urlFoto(ministerio.fotoId, 'DISPLAY')!} alt="" className={styles.fotoDetalheImg}
              onClick={() => setFotoVisualizando(ministerio.fotoId)} />
          ) : (
            <div className={styles.fotoDetalheFallback}>
              <Users size={32} />
            </div>
          )}
        </div>
        <div>
          <h1 className={styles.titulo}>{ministerio.nome}</h1>
        </div>
        {podeGerenciarMembros && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setAdicionarAberto(true)}>
            <UserPlus size={16} /> Adicionar pessoa
          </button>
        )}
        {!podeGerenciarMembros && !souMembro && !jaTemPedido && (
          <button type="button" className={styles.botaoPrimario} onClick={() => pedirEntrada.mutate()}>
            Pedir para entrar
          </button>
        )}
        {!podeGerenciarMembros && jaTemPedido && (
          <span className={styles.tagPendente}>Pedido enviado — aguardando aprovação</span>
        )}
      </header>

      {podeGerenciarMembros && ministerio.pedidosPendentes.length > 0 && (
        <section className={styles.secao}>
          <h2 className={styles.subtitulo}>Pedidos pendentes</h2>
          <ul className={styles.lista}>
            {ministerio.pedidosPendentes.map((membro) => (
              <li key={membro.pessoaId} className={styles.itemMembro}>
                <span className={styles.nomeMembro}>{membro.nome}</span>
                <div className={styles.acoesPedido}>
                  <button type="button" className={styles.botaoAceitar}
                    onClick={() => aceitarPedido.mutate(membro.pessoaId)}>
                    <Check size={16} />
                  </button>
                  <button type="button" className={styles.botaoRecusar}
                    onClick={() => recusarPedido.mutate(membro.pessoaId)}>
                    <XIcon size={16} />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className={styles.secao}>
        <h2 className={styles.subtitulo}>Membros</h2>
        {ministerio.membros.length === 0 ? (
          <EstadoVazio titulo="Nenhum membro ainda" mensagem={`Adicione pessoas a esta ${ROTULO_MINISTERIO.toLowerCase()}.`} />
        ) : (
          <ul className={styles.lista}>
            {ministerio.membros.map((membro) => (
              <li key={membro.pessoaId} className={styles.itemMembro}
                onClick={() => setPessoaDetalheId(membro.pessoaId)}
              >
                <div className={styles.itemMembroInfo}>
                  {urlFoto(membro.fotoId, 'THUMB') ? (
                    // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                    <img src={urlFoto(membro.fotoId, 'THUMB')!} alt="" className={styles.avatar}
                      onClick={(e) => { e.stopPropagation(); setFotoVisualizando(membro.fotoId) }} />
                  ) : (
                    <span className={styles.avatarIniciais}>{iniciais(membro.nome)}</span>
                  )}
                  <span className={styles.nomeMembro}>
                    {membro.nome}
                    {membro.papel === 'LIDER' && <Star size={14} className={styles.estrela} />}
                  </span>
                  {membro.papel === 'LIDER' && (
                    <span className={styles.badgeLider}><Crown size={12} /> Líder</span>
                  )}
                </div>
                <div className={styles.itemMembroAcoes}>
                  {isAdmin && (
                    <button type="button" className={styles.botaoPromover}
                      onClick={(e) => {
                        e.stopPropagation()
                        atualizarPapel.mutate({
                          pessoaId: membro.pessoaId,
                          papel: membro.papel === 'LIDER' ? 'MEMBRO' : 'LIDER',
                        })
                      }}>
                      {membro.papel === 'LIDER' ? 'Remover liderança' : 'Tornar líder'}
                    </button>
                  )}
                  {podeGerenciarMembros && (
                    <button type="button" className={styles.botaoRemover}
                      onClick={(e) => { e.stopPropagation(); removerMembro.mutate(membro.pessoaId) }}>
                      <UserMinus size={16} />
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {adicionarAberto && (
        <ModalAdicionarMembro
          ministerioId={id}
          membrosAtuaisIds={new Set(ministerio.membros.map((m) => m.pessoaId))}
          onClose={() => setAdicionarAberto(false)}
        />
      )}
      {pessoaDetalheId && (
        <DrawerDetalhePessoa pessoaId={pessoaDetalheId} onClose={() => setPessoaDetalheId(null)} />
      )}
      {fotoVisualizando && (
        <div className={styles.viewerOverlay} onMouseDown={() => setFotoVisualizando(null)}>
          <div className={styles.viewerModal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.viewerClose} onClick={() => setFotoVisualizando(null)}>
              <XIcon size={20} />
            </button>
            <img src={urlFoto(fotoVisualizando, 'DISPLAY')!} alt="" className={styles.viewerImg} />
          </div>
        </div>
      )}
    </div>
  )
}
