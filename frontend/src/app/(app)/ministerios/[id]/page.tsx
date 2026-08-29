'use client'

import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import Image from 'next/image'
import { clsx } from 'clsx'
import { ChevronRight, Check, X as XIcon, UserPlus, UserMinus, Crown, Star, Users, Archive, ArrowLeft, Pencil } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterioDetalhe } from '@/hooks/ministerio/useMinisterioDetalhe'
import { useRemoverMembro, useAtualizarPapel } from '@/hooks/ministerio/useMembroMinisterio'
import { usePedirEntrada, useAceitarPedido, useRecusarPedido } from '@/hooks/ministerio/usePedidoMinisterio'
import { ModalMinisterioForm } from '@/app/(app)/ministerios/(lista)/ModalMinisterioForm'
import type { MinisterioResponse } from '@/types/ministerio.type'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { ModalAdicionarMembro } from './ModalAdicionarMembro'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './detalhe.module.css'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'

// Todas as mutations usadas aqui (useRemoverMembro, useAtualizarPapel, usePedirEntrada,
// useAceitarPedido, useRecusarPedido) já disparam notificar.sucesso/erro sozinhas (Task 9)
// — este componente só chama .mutate()/.mutateAsync(), sem repetir o toast.
export default function MinisterioDetalhePage() {
  const { id } = useParams<{ id: string }>()
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const podeGerenciarCadastro = podeGerenciarCadastroMinisterios(role, capacidadesExtras)

  const { data: ministerio, isLoading } = useMinisterioDetalhe(id)
  const removerMembro = useRemoverMembro(id)
  const atualizarPapel = useAtualizarPapel(id)
  const pedirEntrada = usePedirEntrada(id)
  const aceitarPedido = useAceitarPedido(id)
  const recusarPedido = useRecusarPedido(id)
  const { ministerio: rotuloMinisterio, concordar } = useRotulos()

  const [adicionarAberto, setAdicionarAberto] = useState(false)
  const [editarAberto, setEditarAberto] = useState(false)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)
  // pessoaIds saindo — cada linha colapsa animada, independente das outras. Pode remover
  // vários rápido em sequência.
  const [removendo, setRemovendo] = useState<Set<string>>(() => new Set())
  const semId = (s: Set<string>, id: string) => {
    const n = new Set(s)
    n.delete(id)
    return n
  }

  function removerComAnimacao(pessoaId: string) {
    if (removendo.has(pessoaId)) return // só ignora re-clique na MESMA pessoa
    setRemovendo((s) => new Set(s).add(pessoaId))
    setTimeout(() => {
      removerMembro.mutate(pessoaId, {
        // sucesso: espera o refetch tirar a linha antes de limpar o id (senão "pisca de volta")
        onSuccess: () => setTimeout(() => setRemovendo((s) => semId(s, pessoaId)), 350),
        onError: () => setRemovendo((s) => semId(s, pessoaId)),
      })
    }, 420)
  }

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
  // Espelha o padrão de Célula: admin/secretário (cadastro) OU líder desta rede.
  // O backend já resolve os dois em souLiderDesteMinisterio, mas o OR local mantém
  // a UI correta caso o cálculo do backend mude.
  const podeGerenciar = podeGerenciarCadastro || ministerio.souLiderDesteMinisterio

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/ministerios" className={styles.breadcrumbLink}>{rotuloMinisterio.plural}</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{ministerio.nome}</span>
      </nav>

      {ministerio.arquivada && (
        <Link href="/ministerios/arquivados" className={styles.avisoArquivada}>
          <ArrowLeft size={16} />
          <Archive size={16} />
          <span>{concordar(rotuloMinisterio.genero, 'este')} {rotuloMinisterio.singular.toLowerCase()} está {concordar(rotuloMinisterio.genero, 'arquivado')}. Toque para restaurá-{concordar(rotuloMinisterio.genero, 'lo')} na lista de {concordar(rotuloMinisterio.genero, 'arquivados')}.</span>
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
        <div className={styles.tituloLinha}>
          <h1 className={styles.titulo}>{ministerio.nome}</h1>
          {podeGerenciar && !ministerio.arquivada && (
            <button type="button" className={styles.btnEditar} onClick={() => setEditarAberto(true)}
              title={`Editar ${rotuloMinisterio.singular.toLowerCase()}`}>
              <Pencil size={16} />
            </button>
          )}
        </div>
        {podeGerenciar && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setAdicionarAberto(true)}>
            <UserPlus size={16} /> Adicionar pessoa
          </button>
        )}
        {!podeGerenciar && !souMembro && !jaTemPedido && (
          <button type="button" className={styles.botaoPrimario} onClick={() => pedirEntrada.mutate()}>
            Pedir para entrar
          </button>
        )}
        {!podeGerenciar && jaTemPedido && (
          <span className={styles.tagPendente}>Pedido enviado — aguardando aprovação</span>
        )}
      </header>

      {podeGerenciar && ministerio.pedidosPendentes.length > 0 && (
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
          <EstadoVazio titulo="Nenhum membro ainda" mensagem={`Adicione pessoas a esta ${rotuloMinisterio.singular.toLowerCase()}.`} />
        ) : (
          <ul className={styles.lista}>
            {ministerio.membros.map((membro) => (
              <li
                key={membro.pessoaId}
                className={clsx(styles.itemMembro, removendo.has(membro.pessoaId) && styles.itemMembroSaindo)}
                onClick={() => setPessoaDetalheId(membro.pessoaId)}
              >
                <div className={styles.itemMembroInfo}>
                  {urlFoto(membro.fotoId, 'THUMB') ? (
                    <Image src={urlFoto(membro.fotoId, 'THUMB')!} alt="" width={32} height={32} unoptimized className={styles.avatar}
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
                  {podeGerenciar && (
                    <button type="button" className={styles.botaoPromover}
                      disabled={removendo.has(membro.pessoaId)}
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
                  {podeGerenciar && (
                    <button type="button" className={styles.botaoRemover}
                      disabled={removendo.has(membro.pessoaId)}
                      onClick={(e) => { e.stopPropagation(); removerComAnimacao(membro.pessoaId) }}>
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
      {editarAberto && (
        <ModalMinisterioForm
          ministerio={{
            id: ministerio.id,
            nome: ministerio.nome,
            fotoId: ministerio.fotoId,
            lideres: [],
            totalMembros: ministerio.membros.length,
            souLiderDesteMinisterio: ministerio.souLiderDesteMinisterio,
            temVinculo: true,
          } satisfies MinisterioResponse}
          onClose={() => setEditarAberto(false)}
        />
      )}
      {pessoaDetalheId && (
        <DrawerDetalhePessoa pessoaId={pessoaDetalheId} onClose={() => setPessoaDetalheId(null)} />
      )}
      {fotoVisualizando && (
        <VisualizadorFoto fotoId={fotoVisualizando} descricao="Foto de perfil" onClose={() => setFotoVisualizando(null)} />
      )}
    </div>
  )
}
