'use client'

import { useEffect } from 'react'
import { X, Phone, Cake, Heart, Church, MapPin, FileText, CalendarClock, Droplet } from 'lucide-react'
import { useMembro } from '@/hooks/membro/useMembro'
import {
  iniciais, rotuloStatus, varianteStatus, formatarData,
  formatarTelefoneExibicao, rotuloEstadoCivil, formatarDataNascimento, formatarEndereco,
} from '@/lib/formats/membroFormat'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { SkeletonDrawerMembro } from './SkeletonDrawerMembro'
import styles from './DrawerDetalheMembro.module.css'

interface DrawerDetalheMembroProps {
  membroId: string
  onClose: () => void
}

export function DrawerDetalheMembro({ membroId, onClose }: DrawerDetalheMembroProps) {
  const { data: membro, isPending, isError, refetch } = useMembro(membroId)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const endereco = membro ? formatarEndereco(membro.endereco) : null
  const nascimento = membro ? formatarDataNascimento(membro.dataNascimento) : null

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
          <SkeletonDrawerMembro />
        ) : isError || !membro ? (
          <EstadoErro
            titulo="Não foi possível carregar as informações do membro"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <>
            <div className={styles.conteudo}>
              <div className={styles.topo}>
                <span className={styles.avatar}>{iniciais(membro.nome)}</span>
                <div className={styles.identidade}>
                  <span className={styles.nome}>{membro.nome}</span>
                  {membro.email && <span className={styles.email}>{membro.email}</span>}
                </div>
              </div>

              <span className={`${styles.statusBadge} ${styles[varianteStatus(membro.status)]}`}>
                {rotuloStatus(membro.status)}
              </span>

              <div className={styles.infos}>
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Phone size={18} /></span>
                  <div>
                    <p className={styles.infoLabel}>Telefone</p>
                    <p className={styles.infoValor}>{formatarTelefoneExibicao(membro.telefone)}</p>
                  </div>
                </div>

                {nascimento && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Cake size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Nascimento</p>
                      <p className={styles.infoValor}>{nascimento}</p>
                    </div>
                  </div>
                )}

                {membro.estadoCivil && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Heart size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Estado civil</p>
                      <p className={styles.infoValor}>{rotuloEstadoCivil(membro.estadoCivil)}</p>
                    </div>
                  </div>
                )}

                {membro.ministerio && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Church size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Ministério</p>
                      <p className={styles.infoValor}>{membro.ministerio}</p>
                    </div>
                  </div>
                )}

                {/* F11: batizado importa porque decide elegibilidade em eventos exclusivos para batizados. */}
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Droplet size={18} /></span>
                  <div>
                    <p className={styles.infoLabel}>Batismo</p>
                    <p className={styles.infoValor}>
                      {membro.batizado
                        ? (membro.dataBatismo ? `Batizado em ${formatarDataNascimento(membro.dataBatismo)}` : 'Batizado')
                        : 'Não batizado'}
                    </p>
                  </div>
                </div>
              </div>

              {endereco && (
                <div className={styles.bloco}>
                  <div className={styles.blocoHeader}>
                    <MapPin size={16} />
                    <span>Endereço</span>
                  </div>
                  {endereco.linha1 && <p className={styles.blocoTexto}>{endereco.linha1}</p>}
                  {endereco.linha2 && <p className={styles.infoValorSecundaria}>{endereco.linha2}</p>}
                </div>
              )}

              {membro.observacoes && (
                <div className={styles.bloco}>
                  <div className={styles.blocoHeader}>
                    <FileText size={16} />
                    <span>Observações</span>
                  </div>
                  <p className={styles.blocoTexto}>{membro.observacoes}</p>
                </div>
              )}
            </div>

            <div className={styles.auditoria}>
              <CalendarClock size={14} />
              <span>Cadastrado em {formatarData(membro.createdAt)}</span>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
