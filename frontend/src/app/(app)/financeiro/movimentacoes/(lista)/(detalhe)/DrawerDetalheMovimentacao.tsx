'use client'

import { useEffect } from 'react'
import Link from 'next/link'
import { X, Calendar, Tag, User, FileText, ArrowDownCircle, ArrowUpCircle, History, Archive } from 'lucide-react'
import { useMovimentacao } from '@/hooks/financeiro/movimentacao/useMovimentacao'
import { formatarMoeda, formatarData, rotuloTipo, varianteTipo } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './DrawerDetalheMovimentacao.module.css'
import { SkeletonDrawerMovimentacao } from "./SkeletonDrawerMovimentacao";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro' 

interface DrawerDetalheMovimentacaoProps {
  movimentacaoId: string
  onClose: () => void
}

export function DrawerDetalheMovimentacao({ movimentacaoId, onClose }: DrawerDetalheMovimentacaoProps) {
  const { data: mov, isPending, isError, refetch } = useMovimentacao(movimentacaoId)

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
          <SkeletonDrawerMovimentacao />
        ) : isError || !mov ? (
          <EstadoErro
            titulo="Não foi possível carregar as informações da movimentação"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <>
            <div className={styles.conteudo}>
              {mov.arquivada && (
                <Link href="/financeiro/movimentacoes/arquivadas" className={styles.avisoArquivada} onClick={onClose}>
                  <Archive size={16} />
                  <span>Esta movimentação está arquivada. Toque para restaurá-la na lista de arquivadas.</span>
                </Link>
              )}
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

                {mov.contribuintes.length > 0 && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><User size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>
                        {mov.tipo === 'ENTRADA' ? 'Contribuintes' : 'Beneficiários'}
                      </p>
                      <div className={styles.contribuintesLista}>
                        {mov.contribuintes.map((c) => (
                          <p key={c.pessoaId} className={styles.infoValor}>
                            {c.pessoaNome} <span className={styles.contribuinteValor}>{formatarMoeda(c.valor)}</span>
                          </p>
                        ))}
                      </div>
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