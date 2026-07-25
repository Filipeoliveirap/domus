'use client'

import { useState } from 'react'
import { useParams } from 'next/navigation'
import { Check, X as XIcon, UserPlus, UserMinus, Crown } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterioDetalhe } from '@/hooks/ministerio/useMinisterioDetalhe'
import { useRemoverMembro, useAtualizarPapel } from '@/hooks/ministerio/useMembroMinisterio'
import { usePedirEntrada, useAceitarPedido, useRecusarPedido } from '@/hooks/ministerio/usePedidoMinisterio'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
import { ModalAdicionarMembro } from './ModalAdicionarMembro'
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

  if (isLoading || !ministerio) {
    return <div className={styles.pagina} />
  }

  // souMembroAtivo/tenhoPedidoPendente vêm prontos do backend (GET /ministerios/{id}) —
  // o authStore não guarda pessoaId, só usuarioId/role, então o cálculo é feito no
  // service (MinisterioService.detalhe), que já sabe a pessoa logada via UsuarioAutenticado.
  const souMembro = ministerio.souMembroAtivo
  const jaTemPedido = ministerio.tenhoPedidoPendente
  const podeGerenciarMembros = ministerio.souLiderDesteMinisterio

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>{ministerio.nome}</h1>
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
              <li key={membro.pessoaId} className={styles.itemMembro}>
                {urlFoto(membro.fotoId, 'THUMB') ? (
                  // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                  <img src={urlFoto(membro.fotoId, 'THUMB')!} alt="" className={styles.avatar} />
                ) : (
                  <span className={styles.avatarIniciais}>{iniciais(membro.nome)}</span>
                )}
                <span className={styles.nomeMembro}>{membro.nome}</span>
                {membro.papel === 'LIDER' && (
                  <span className={styles.badgeLider}><Crown size={12} /> Líder</span>
                )}
                {isAdmin && (
                  <button type="button" className={styles.botaoPromover}
                    onClick={() => atualizarPapel.mutate({
                      pessoaId: membro.pessoaId,
                      papel: membro.papel === 'LIDER' ? 'MEMBRO' : 'LIDER',
                    })}>
                    {membro.papel === 'LIDER' ? 'Remover liderança' : 'Tornar líder'}
                  </button>
                )}
                {podeGerenciarMembros && (
                  <button type="button" className={styles.botaoRemover}
                    onClick={() => removerMembro.mutate(membro.pessoaId)}>
                    <UserMinus size={16} />
                  </button>
                )}
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
    </div>
  )
}
