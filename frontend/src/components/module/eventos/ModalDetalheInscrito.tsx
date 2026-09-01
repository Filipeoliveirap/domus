'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { clsx } from 'clsx'
import Image from 'next/image'
import { User } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { formatarData, iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { ModalRespostasInscrito } from './ModalRespostasInscrito'
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
  const { saindo, fechar } = useFecharAnimado(onClose, 180)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !verRespostas) fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, verRespostas])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  if (typeof document === 'undefined') return null

  const foto = urlFoto(inscrito.fotoId, 'THUMB')

  return createPortal(
    <>
      <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
        <div
          className={styles.modal}
          onMouseDown={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-detalhe-inscrito-titulo"
        >
          <span className={styles.grabber} aria-hidden="true" />
          <div className={styles.cabecalho}>
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
              <h2 className={styles.titulo} id="modal-detalhe-inscrito-titulo">{inscrito.nome}</h2>
              {ehConvidado && <span className={styles.pillConvidado}>Convidado</span>}
            </div>
          </div>

          <div className={styles.corpo}>
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

          <div className={styles.rodape}>
            <button type="button" className={styles.btnCancelar} onClick={fechar}>
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
