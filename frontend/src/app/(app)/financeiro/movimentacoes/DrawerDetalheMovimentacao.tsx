'use client'

import { useEffect } from 'react'
import { X, Calendar, Tag, User, FileText, ArrowDownCircle, ArrowUpCircle, History } from 'lucide-react'
import { useMovimentacao } from '@/hooks/financeiro/movimentacao/useMovimentacao'
import { formatarMoeda, formatarData, rotuloTipo, varianteTipo } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './DrawerDetalheMovimentacao.module.css'

interface DrawerDetalheMovimentacaoProps {
  movimentacaoId: string
  onClose: () => void
}

export function DrawerDetalheMovimentacao({ movimentacaoId, onClose }: DrawerDetalheMovimentacaoProps) {
  const { data: mov, isPending, isError } = useMovimentacao(movimentacaoId)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <aside
        className={styles.drawer}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        {isPending ? (
          <div className={styles.carregando}>Carregando…</div>
        ) : isError || !mov ? (
          <div className={styles.erro}>Não foi possível carregar a movimentação.</div>
        ) : (
          <>
            <div className={styles.conteudo}>
              <div className={styles.topo}>
                <span className={`${styles.selo} ${styles[varianteTipo(mov.tipo)]}`}>
                  {mov.tipo === 'ENTRADA' ? <ArrowDownCircle size={16} /> : <ArrowUpCircle size={16} />}
                  {rotuloTipo(mov.tipo)}
                </span>
                <p className={`${styles.valor} ${styles[varianteTipo(mov.tipo)]}`}>
                  {mov.tipo === 'SAIDA' && '- '}{formatarMoeda(mov.valor)}
                </p>
              </div>

              <div className={styles.infos}>
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Tag size={18} /></span>
                  <div>
                    <p className={styles.infoLabel}>Categoria</p>
                    <p className={styles.infoValor}>{mov.categoriaNome}</p>
                  </div>
                </div>

                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Calendar size={18} /></span>
                  <div>
                    <p className={styles.infoLabel}>Data</p>
                    <p className={styles.infoValor}>{formatarData(mov.dataMovimentacao)}</p>
                  </div>
                </div>

                {mov.membroNome && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><User size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>
                        {mov.tipo === 'ENTRADA' ? 'Contribuinte' : 'Beneficiário'}
                      </p>
                      <p className={styles.infoValor}>{mov.membroNome}</p>
                    </div>
                  </div>
                )}
              </div>

              {mov.descricao && (
                <div className={styles.descricaoBloco}>
                  <div className={styles.descricaoHeader}>
                    <FileText size={16} />
                    <span>Descrição</span>
                  </div>
                  <p className={styles.descricaoTexto}>{mov.descricao}</p>
                </div>
              )}
            </div>

            <div className={styles.auditoria}>
              <History size={14} />
              <span>
                Registrado por {mov.criadoPorNome}
                {mov.atualizadoPorNome && ` · editado por ${mov.atualizadoPorNome}`}
              </span>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}