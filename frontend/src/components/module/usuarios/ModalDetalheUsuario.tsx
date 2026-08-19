'use client'

import { useEffect } from 'react'
import Image from 'next/image'
import { X, Shield, Clock, CalendarPlus, KeyRound } from 'lucide-react'
import { useUsuario } from '@/hooks/usuario/useUsuario'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { rotuloRole, varianteRole, iniciais, formatarUltimoAcesso } from '@/lib/formats/usuarioFormat'
import { urlFoto } from '@/lib/urlFoto'
import styles from './ModalDetalheUsuario.module.css'

interface Props {
  usuarioId: string
  onClose: () => void
}

export function ModalDetalheUsuario({ usuarioId, onClose }: Props) {
  const { data: usuario, isPending, isError, refetch } = useUsuario(usuarioId)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button type="button" className={styles.fechar} onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        {isPending ? (
          <div className={styles.carregando} />
        ) : isError || !usuario ? (
          <EstadoErro
            titulo="Não foi possível carregar o usuário"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <>
            <div className={styles.cabecalho}>
              <span className={styles.avatar}>
                {urlFoto(usuario.fotoId, 'THUMB') ? (
                  <Image src={urlFoto(usuario.fotoId, 'THUMB')!} alt="" width={48} height={48} unoptimized className={styles.avatarFoto} />
                ) : (
                  iniciais(usuario.nome)
                )}
              </span>
              <div>
                <h2 className={styles.nome}>{usuario.nome}</h2>
                <p className={styles.email}>{usuario.email}</p>
              </div>
            </div>

            <div className={styles.corpo}>
              <div className={styles.linha}>
                <Shield size={16} className={styles.iconeLinha} />
                <span className={`${styles.badgeRole} ${styles[varianteRole(usuario.role)]}`}>
                  {rotuloRole(usuario.role)}
                </span>
                {usuario.convitePendente ? (
                  <span className={styles.badgeConvite}>Convite pendente</span>
                ) : (
                  <span className={`${styles.badgeStatus} ${usuario.ativo ? styles.ativo : styles.inativo}`}>
                    {usuario.ativo ? 'Ativo' : 'Inativo'}
                  </span>
                )}
              </div>

              {usuario.capacidadesExtras && usuario.capacidadesExtras.length > 0 && (
                <div className={styles.linha}>
                  <KeyRound size={16} className={styles.iconeLinha} />
                  <span>{usuario.capacidadesExtras.join(', ')}</span>
                </div>
              )}

              <div className={styles.linha}>
                <Clock size={16} className={styles.iconeLinha} />
                <span>Último acesso: {formatarUltimoAcesso(usuario.ultimoLoginEm)}</span>
              </div>

              <div className={styles.linha}>
                <CalendarPlus size={16} className={styles.iconeLinha} />
                <span>Acesso concedido em {formatarUltimoAcesso(usuario.criadoEm)}</span>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
