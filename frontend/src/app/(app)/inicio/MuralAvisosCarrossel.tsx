'use client'

import { useRef, useState } from 'react'
import { Megaphone, ChevronLeft, ChevronRight, Plus, Clock, User } from 'lucide-react'
import { useMuralAvisos } from '@/hooks/postagem/useMuralAvisos'
import { useAuthStore } from '@/store/authStore'
import { ModalNovoAviso } from './ModalNovoAviso'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import type { Postagem } from '@/types/postagem.type'
import styles from './MuralAvisosCarrossel.module.css'

export function MuralAvisosCarrossel() {
  const { data: avisos, isLoading } = useMuralAvisos()
  const perfil = useAuthStore((s) => s.perfil)
  const podeCriarAviso = perfil === 'ADMIN_IGREJA' || perfil === 'LIDER'
  const [modalNovoAberto, setModalNovoAberto] = useState(false)
  const trilhaRef = useRef<HTMLDivElement>(null)

  const rolar = (direcao: 'esq' | 'dir') => {
    if (!trilhaRef.current) return
    const deslocamento = 300
    trilhaRef.current.scrollBy({
      left: direcao === 'esq' ? -deslocamento : deslocamento,
      behavior: 'smooth',
    })
  }

  if (isLoading) {
    return (
      <div className={styles.secaoMural}>
        <Skeleton style={{ height: 160, borderRadius: 16 }} />
      </div>
    )
  }

  const listaAvisos = avisos ?? []

  return (
    <section className={styles.secaoMural}>
      <div className={styles.cabecalho}>
        <div className={styles.tituloInfo}>
          <div className={styles.iconeCampaign}>
            <Megaphone size={20} />
          </div>
          <div>
            <h2 className={styles.titulo}>
              Mural Oficial & Quadro de Avisos
              {listaAvisos.length > 0 && (
                <span className={styles.badgeAtivos}>{listaAvisos.length} Ativos</span>
              )}
            </h2>
            <p className={styles.subtitulo}>
              Comunicados pastorais, escalas ministeriais e avisos oficiais da congregação
            </p>
          </div>
        </div>

        <div className={styles.controles}>
          {podeCriarAviso && (
            <button
              type="button"
              className={styles.btnNovoAviso}
              onClick={() => setModalNovoAberto(true)}
            >
              <Plus size={16} />
              Novo aviso
            </button>
          )}
          <button
            type="button"
            className={styles.btnNavegacao}
            onClick={() => rolar('esq')}
            aria-label="Aviso anterior"
          >
            <ChevronLeft size={18} />
          </button>
          <button
            type="button"
            className={styles.btnNavegacao}
            onClick={() => rolar('dir')}
            aria-label="Próximo aviso"
          >
            <ChevronRight size={18} />
          </button>
        </div>
      </div>

      {listaAvisos.length === 0 ? (
        <div className={`${styles.cardAviso} card-painel`}>
          <p className={styles.cardConteudo}>Nenhum aviso publicado no mural até o momento.</p>
        </div>
      ) : (
        <div className={styles.carrosselTrilha} ref={trilhaRef}>
          {listaAvisos.map((aviso: Postagem) => (
            <article key={aviso.id} className={`${styles.cardAviso} card-interativo`}>
              <div className={styles.faixaDestaque} />
              <div>
                <div className={styles.cardTopo}>
                  <span className={styles.tagTipo}>{aviso.tipo.replace('_', ' ')}</span>
                  <span className={styles.dataTime}>
                    <Clock size={13} />
                    {new Date(aviso.criadoEm).toLocaleDateString('pt-BR')}
                  </span>
                </div>
                {aviso.titulo && <h3 className={styles.cardTitulo}>{aviso.titulo}</h3>}
                <p className={styles.cardConteudo}>{aviso.conteudo}</p>
              </div>
              <div className={styles.cardRodape}>
                <div className={styles.autorInfo}>
                  <User size={14} />
                  <span>{doisPrimeirosNomes(aviso.autor.nome)}</span>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {modalNovoAberto && <ModalNovoAviso aoFechar={() => setModalNovoAberto(false)} />}
    </section>
  )
}
