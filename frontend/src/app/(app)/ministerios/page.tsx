'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { ChevronRight, Pencil, Archive, Users, Crown, X } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterios } from '@/hooks/ministerio/useMinisterios'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { urlFoto } from '@/lib/urlFoto'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { ModalMinisterioForm } from './ModalMinisterioForm'
import { ModalArquivarMinisterio } from './ModalArquivarMinisterio'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './ministerios.module.css'

// Rótulo de líder(es) do card — nome do líder + contagem de membros no próprio card,
// sem precisar abrir o detalhe. Sem líder ainda = "Sem líder".
function rotuloLideres(lideres: string[]): string {
  if (lideres.length === 0) return 'Sem líder'
  if (lideres.length === 1) return lideres[0]
  return `${lideres[0]} +${lideres.length - 1}`
}

export default function MinisteriosPage() {
  const router = useRouter()
  const role = useAuthStore((s) => s.role)
  const hidratado = useAuthStore((s) => s.hidratado)
  const podeGerenciar = podeGerenciarCadastroMinisterios(role)

  const { data: ministerios = [], isLoading } = useMinisterios()
  // `null` = fechado; `'novo'` = criar; objeto = editar (mesma convenção de /eventos/locais).
  const [formAberto, setFormAberto] = useState<'novo' | MinisterioResponse | null>(null)
  const [arquivando, setArquivando] = useState<MinisterioResponse | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)

  if (!hidratado || isLoading) {
    return (
      <div className={styles.pagina}>
        <div className={styles.grade}>
          {[1,2,3].map(i => (
            <div key={i} className={styles.card}>
              <Skeleton width="48px" height="48px" radius="var(--radius-lg)" />
              <Skeleton width="70%" height="18px" />
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 'auto', paddingTop: 12 }}>
                <Skeleton width="80px" height="14px" />
                <Skeleton width="30px" height="24px" radius="var(--radius-full)" />
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{ROTULO_MINISTERIO_PLURAL}</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>{ROTULO_MINISTERIO_PLURAL}</h1>
            {ministerios.length > 0 && <span className={styles.contador}>{ministerios.length}</span>}
          </div>
          <p className={styles.subtitulo}>{ROTULO_MINISTERIO_PLURAL} da igreja e quem participa de cada uma</p>
        </div>
        {podeGerenciar && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setFormAberto('novo')}>
            Nova {ROTULO_MINISTERIO.toLowerCase()}
          </button>
        )}
      </header>

      {ministerios.length === 0 ? (
        <EstadoVazio
          icone={Users}
          titulo={`Nenhuma ${ROTULO_MINISTERIO.toLowerCase()} cadastrada`}
          mensagem={podeGerenciar
            ? `Cadastre a primeira ${ROTULO_MINISTERIO.toLowerCase()} da igreja.`
            : `Nenhuma ${ROTULO_MINISTERIO.toLowerCase()} foi cadastrada ainda.`}
          acaoPrimaria={podeGerenciar ? { label: `Nova ${ROTULO_MINISTERIO.toLowerCase()}`, onClick: () => setFormAberto('novo') } : undefined}
        />
      ) : (
        <div className={styles.grade}>
          {ministerios.map((ministerio) => {
            const acoes: ItemAcao[] = [
              { label: 'Editar', icone: Pencil, onClick: () => setFormAberto(ministerio) },
              { label: 'Arquivar', icone: Archive, onClick: () => setArquivando(ministerio), perigo: true, separadorAntes: true },
            ]
            return (
              <div
                key={ministerio.id}
                className={styles.card}
                role="button"
                tabIndex={0}
                onClick={() => router.push(`/ministerios/${ministerio.id}`)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') router.push(`/ministerios/${ministerio.id}`)
                }}
              >
                {podeGerenciar && (
                  <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
                    <MenuAcoes itens={acoes} />
                  </div>
                )}
                {ministerio.fotoId ? (
                  <img
                    src={urlFoto(ministerio.fotoId, 'THUMB')!} alt=""
                    className={styles.cardFoto}
                    onClick={(e) => { e.stopPropagation(); setFotoVisualizando(ministerio.fotoId) }}
                  />
                ) : (
                  <div className={styles.cardIcon}>
                    <Users size={24} />
                  </div>
                )}
                <div className={styles.cardTopo}>
                  <span className={styles.cardTitulo}>{ministerio.nome}</span>
                </div>
                <div className={styles.cardLider}>
                  <Crown size={14} />
                  <span>{rotuloLideres(ministerio.lideres)}</span>
                </div>
                <div className={styles.cardMembros}>
                  {ministerio.totalMembros} {ministerio.totalMembros === 1 ? 'membro' : 'membros'}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {formAberto && (
        <ModalMinisterioForm ministerio={formAberto === 'novo' ? null : formAberto} onClose={() => setFormAberto(null)} />
      )}
      {arquivando && (
        <ModalArquivarMinisterio ministerio={arquivando} onClose={() => setArquivando(null)} />
      )}
      {fotoVisualizando && (
        <div className={styles.viewerOverlay} onMouseDown={() => setFotoVisualizando(null)}>
          <div className={styles.viewerModal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.viewerClose} onClick={() => setFotoVisualizando(null)}>
              <X size={20} />
            </button>
            <img src={urlFoto(fotoVisualizando, 'DISPLAY')!} alt="" className={styles.viewerImg} />
          </div>
        </div>
      )}
    </div>
  )
}
