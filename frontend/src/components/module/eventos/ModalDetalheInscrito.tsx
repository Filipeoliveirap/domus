'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import Image from 'next/image'
import { User } from 'lucide-react'
import { formatarData, iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { ModalRespostasInscrito } from './ModalRespostasInscrito'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './ModalDetalheInscrito.module.css'
import type { InscritoResponse } from '@/types/inscricao.type'

/** Detalhe de UM inscrito na tela de gestão do evento — pessoa com cadastro ou convidado
 *  sem cadastro, mesma UI pros dois. Diferente do DrawerDetalhePessoa (genérico, usado na
 *  tela de Pessoas): mostra só o que é relevante PRO EVENTO, sem dado de perfil (nascimento,
 *  estado civil, ministérios, endereço etc. ficam de fora — quem quiser isso vai na tela de
 *  Pessoas). Não faz requisição própria: usa só o que a lista de inscritos já trouxe. */
export function ModalDetalheInscrito({
  inscrito, ehConvidado, mostraPresenca, temCamposPersonalizados, onClose,
}: {
  inscrito: InscritoResponse
  ehConvidado: boolean
  mostraPresenca: boolean
  temCamposPersonalizados: boolean
  onClose: () => void
}) {
  const [verRespostas, setVerRespostas] = useState(false)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !verRespostas) onClose() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, verRespostas])

  if (typeof document === 'undefined') return null

  const foto = urlFoto(inscrito.fotoId, 'THUMB')

  return createPortal(
    <>
      <div className={baseStyles.overlay} onMouseDown={onClose}>
        <div
          className={baseStyles.modal}
          onMouseDown={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-detalhe-inscrito-titulo"
        >
          <div className={baseStyles.cabecalho}>
            <span className={styles.avatar}>
              {foto ? (
                <Image src={foto} alt="" width={44} height={44} unoptimized className={styles.avatarFoto} />
              ) : ehConvidado ? (
                iniciais(inscrito.nome)
              ) : (
                <User size={20} aria-hidden="true" />
              )}
            </span>
            <div className={styles.cabecalhoTextos}>
              <h2 className={baseStyles.titulo} id="modal-detalhe-inscrito-titulo">{inscrito.nome}</h2>
              {ehConvidado && <span className={styles.pillConvidado}>Convidado</span>}
            </div>
          </div>

          <div className={baseStyles.corpo}>
            <ul className={styles.lista}>
              <li>Inscrito em {formatarData(inscrito.inscritoEm)}</li>
              {ehConvidado ? (
                <>
                  {inscrito.convidadoPorNome && <li>Convidado por {inscrito.convidadoPorNome}</li>}
                  <li>{inscrito.telefoneConvidado ? `Telefone: ${inscrito.telefoneConvidado}` : 'Sem telefone informado'}</li>
                </>
              ) : (
                <>
                  <li>
                    {inscrito.inscritoPorUsuarioId === null
                      ? 'Inscrito por ele mesmo'
                      : inscrito.inscritoPorNome
                        ? `Inscrito por ${inscrito.inscritoPorNome}`
                        : 'Inscrito por cadastro removido'}
                  </li>
                  <li>{inscrito.telefonePessoa ? `Telefone: ${inscrito.telefonePessoa}` : 'Sem telefone cadastrado'}</li>
                  <li>{inscrito.emailPessoa ? `E-mail: ${inscrito.emailPessoa}` : 'Sem e-mail cadastrado'}</li>
                </>
              )}
              {mostraPresenca && (
                <li>{inscrito.compareceu ? 'Presença confirmada neste evento' : 'Ainda não marcado presente neste evento'}</li>
              )}
            </ul>

            {temCamposPersonalizados && (
              <button type="button" className={styles.btnVerRespostas} onClick={() => setVerRespostas(true)}>
                Ver respostas dos campos do evento
              </button>
            )}
          </div>

          <div className={baseStyles.rodape}>
            <button type="button" className={baseStyles.btnCancelar} onClick={onClose}>
              Fechar
            </button>
          </div>
        </div>
      </div>

      {verRespostas && (
        <ModalRespostasInscrito
          nome={inscrito.nome}
          inscricaoId={inscrito.id}
          onClose={() => setVerRespostas(false)}
        />
      )}
    </>,
    document.body,
  )
}
