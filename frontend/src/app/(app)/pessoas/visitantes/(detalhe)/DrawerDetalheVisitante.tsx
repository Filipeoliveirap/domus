'use client'

import { useEffect } from 'react'
import { clsx } from 'clsx'
import { X, Phone, Cake, Heart, MapPin, FileText, CalendarClock, Baby } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useVisitante } from '@/hooks/visitante/useVisitante'
import {
  iniciaisVisitante, formatarTelefoneExibicao, rotuloEstadoCivil,
  formatarDataNascimento, calcularIdade, formatarEndereco, formatarData,
} from '@/lib/formats/visitanteFormat'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './DrawerDetalheVisitante.module.css'

interface DrawerDetalheVisitanteProps {
  visitanteId: string
  onClose: () => void
}

export function DrawerDetalheVisitante({ visitanteId, onClose }: DrawerDetalheVisitanteProps) {
  const { data: visitante, isPending, isError, refetch } = useVisitante(visitanteId)
  const { saindo, fechar } = useFecharAnimado(onClose, 260)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [fechar])

  const endereco = visitante ? formatarEndereco(visitante.endereco) : null
  const nascimento = visitante ? formatarDataNascimento(visitante.dataNascimento) : null
  const idade = visitante ? calcularIdade(visitante.dataNascimento) : null

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <aside
        className={styles.drawer}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button type="button" className={styles.btnClose} onClick={fechar} aria-label="Fechar">
          <X size={20} />
        </button>

        {isPending ? (
          <div className={styles.conteudo}>
            <div className={styles.topo}>
              <Skeleton width="48px" height="48px" circle />
              <div>
                <Skeleton width="180px" height="18px" />
                <Skeleton width="120px" height="14px" />
              </div>
            </div>
            <Skeleton width="100px" height="28px" radius="var(--radius-full)" />
          </div>
        ) : isError || !visitante ? (
          <EstadoErro
            titulo="Não foi possível carregar os dados do visitante"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <>
            <div className={styles.conteudo}>
              <div className={styles.topo}>
                <span className={styles.avatar}>{iniciaisVisitante(visitante.nome)}</span>
                <div className={styles.identidade}>
                  <span className={styles.nome}>{visitante.nome}</span>
                  {visitante.telefone && (
                    <span className={styles.telefone}>{formatarTelefoneExibicao(visitante.telefone)}</span>
                  )}
                </div>
              </div>

              <div className={styles.toggles}>
                <span className={`${styles.toggle} ${visitante.contatoRealizado ? styles.toggleAtivo : styles.toggleInativo}`}>
                  Contato {visitante.contatoRealizado ? '✓' : '—'}
                </span>
                <span className={`${styles.toggle} ${visitante.visitaRealizada ? styles.toggleAtivo : styles.toggleInativo}`}>
                  Visita {visitante.visitaRealizada ? '✓' : '—'}
                </span>
                <span className={`${styles.toggle} ${visitante.acompanhamentoFeito ? styles.toggleAtivo : styles.toggleInativo}`}>
                  Acomp. {visitante.acompanhamentoFeito ? '✓' : '—'}
                </span>
              </div>

              <div className={styles.infos}>
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Phone size={18} /></span>
                  <div>
                    <p className={styles.infoLabel}>Telefone</p>
                    <p className={styles.infoValor}>{formatarTelefoneExibicao(visitante.telefone)}</p>
                  </div>
                </div>

                {nascimento && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Cake size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Nascimento</p>
                      <p className={styles.infoValor}>
                        {nascimento}{idade != null ? ` (${idade} ${idade === 1 ? 'ano' : 'anos'})` : ''}
                      </p>
                    </div>
                  </div>
                )}

                {visitante.estadoCivil && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Heart size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Estado civil</p>
                      <p className={styles.infoValor}>{rotuloEstadoCivil(visitante.estadoCivil)}</p>
                    </div>
                  </div>
                )}

                {(visitante.temFilhos || visitante.quantidadeFilhos != null) && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Baby size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Filhos</p>
                      <p className={styles.infoValor}>
                        {visitante.quantidadeFilhos != null ? visitante.quantidadeFilhos : 'Sim'}
                      </p>
                    </div>
                  </div>
                )}
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

              {visitante.observacoes && (
                <div className={styles.bloco}>
                  <div className={styles.blocoHeader}>
                    <FileText size={16} />
                    <span>Observações</span>
                  </div>
                  <p className={styles.blocoTexto}>{visitante.observacoes}</p>
                </div>
              )}
            </div>

            <div className={styles.auditoria}>
              <CalendarClock size={14} />
              <span>Cadastrado em {formatarData(visitante.createdAt)}</span>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
