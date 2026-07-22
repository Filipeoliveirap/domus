'use client'

import { useEffect, useState } from 'react'
import { X, Phone, Cake, Heart, Church, MapPin, FileText, CalendarClock, Droplet } from 'lucide-react'
import { usePessoa } from '@/hooks/pessoa/usePessoa'
import {
  iniciais, rotuloVinculo, varianteVinculo, formatarData,
  formatarTelefoneExibicao, rotuloEstadoCivil, formatarDataNascimento, formatarEndereco,
} from '@/lib/formats/pessoaFormat'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { SkeletonDrawerPessoa } from './SkeletonDrawerPessoa'
import { urlFoto } from '@/lib/urlFoto'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import styles from './DrawerDetalhePessoa.module.css'

interface DrawerDetalhePessoaProps {
  pessoaId: string
  onClose: () => void
}

export function DrawerDetalhePessoa({ pessoaId, onClose }: DrawerDetalhePessoaProps) {
  const { data: pessoa, isPending, isError, refetch } = usePessoa(pessoaId)
  const [ampliada, setAmpliada] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const endereco = pessoa ? formatarEndereco(pessoa.endereco) : null
  const nascimento = pessoa ? formatarDataNascimento(pessoa.dataNascimento) : null

  return (
    <>
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
          <SkeletonDrawerPessoa />
        ) : isError || !pessoa ? (
          <EstadoErro
            titulo="Não foi possível carregar as informações da pessoa"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <>
            <div className={styles.conteudo}>
              <div className={styles.topo}>
                {/*
                  Vira <button> só quando HÁ foto. Sem foto o avatar mostra iniciais, e
                  não há nada para ampliar — um botão ali prometeria uma ação que não
                  existe, e no teclado viraria uma parada inútil na navegação.
                */}
                {pessoa.fotoId ? (
                  <button
                    type="button"
                    className={`${styles.avatar} ${styles.avatarClicavel}`}
                    onClick={() => setAmpliada(true)}
                    aria-label={`Ampliar foto de ${pessoa.nome}`}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos */}
                    <img src={urlFoto(pessoa.fotoId, 'DISPLAY')!} alt="" className={styles.avatarFoto} />
                  </button>
                ) : (
                  <span className={styles.avatar}>{iniciais(pessoa.nome)}</span>
                )}
                <div className={styles.identidade}>
                  <span className={styles.nome}>{pessoa.nome}</span>
                  {pessoa.email && <span className={styles.email}>{pessoa.email}</span>}
                </div>
              </div>

              <span className={`${styles.statusBadge} ${styles[varianteVinculo(pessoa.vinculo)]}`}>
                {rotuloVinculo(pessoa.vinculo)}
              </span>

              <div className={styles.infos}>
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Phone size={18} /></span>
                  <div>
                    <p className={styles.infoLabel}>Telefone</p>
                    <p className={styles.infoValor}>{formatarTelefoneExibicao(pessoa.telefone)}</p>
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

                {pessoa.estadoCivil && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Heart size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Estado civil</p>
                      <p className={styles.infoValor}>{rotuloEstadoCivil(pessoa.estadoCivil)}</p>
                    </div>
                  </div>
                )}

                {pessoa.ministerio && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Church size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Ministério</p>
                      <p className={styles.infoValor}>{pessoa.ministerio}</p>
                    </div>
                  </div>
                )}

                {/* Batismo só faz sentido/é exibido quando o vínculo é Membro. */}
                {pessoa.vinculo === 'MEMBRO' && (
                  <div className={styles.infoItem}>
                    <span className={styles.infoIcone}><Droplet size={18} /></span>
                    <div>
                      <p className={styles.infoLabel}>Batismo</p>
                      <p className={styles.infoValor}>
                        {pessoa.dataBatismo ? `Batizado em ${formatarDataNascimento(pessoa.dataBatismo)}` : 'Batizado'}
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

              {pessoa.observacoes && (
                <div className={styles.bloco}>
                  <div className={styles.blocoHeader}>
                    <FileText size={16} />
                    <span>Observações</span>
                  </div>
                  <p className={styles.blocoTexto}>{pessoa.observacoes}</p>
                </div>
              )}
            </div>

            <div className={styles.auditoria}>
              <CalendarClock size={14} />
              <span>Cadastrado em {formatarData(pessoa.createdAt)}</span>
            </div>
          </>
        )}
      </aside>
    </div>

    {/*
      Irmão do overlay do drawer, não filho: o overlay fecha o drawer no mouseDown, e um
      clique para dispensar a foto ampliada levaria o cadastro junto.
    */}
    {ampliada && pessoa?.fotoId && (
      <VisualizadorFoto
        fotoId={pessoa.fotoId}
        descricao={`Foto de ${pessoa.nome}`}
        onClose={() => setAmpliada(false)}
      />
    )}
    </>
  )
}
