'use client'

import { useState } from 'react'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import { ModalResponderCamposPersonalizados } from './ModalResponderCamposPersonalizados'
import type { CampoPersonalizadoResponse, RespostaResponse } from '@/types/campoPersonalizado.type'
import styles from './RespostasCamposPersonalizados.module.css'

export function RespostasCamposPersonalizados({
  eventoId, inscricaoId, acompanhanteId, abrirAutomaticamente = false,
}: { eventoId: string; inscricaoId: string; acompanhanteId?: string; abrirAutomaticamente?: boolean }) {
  const { data: campos } = useCamposPersonalizados(eventoId)
  const { data: respostas } = useRespostasCampos(inscricaoId, acompanhanteId)

  if (!campos || campos.length === 0 || !respostas) return null

  // key pelas respostas já carregadas: estado local (abertura do modal) nasce do servidor,
  // sem useEffect pra sincronizar (mesmo padrão do CamposPersonalizadosPainel).
  return (
    <Gatilho
      key={inscricaoId + (acompanhanteId ?? '')}
      inscricaoId={inscricaoId}
      acompanhanteId={acompanhanteId}
      campos={campos}
      respostasIniciais={respostas}
      abrirAutomaticamente={abrirAutomaticamente}
    />
  )
}

function Gatilho({
  inscricaoId, acompanhanteId, campos, respostasIniciais, abrirAutomaticamente,
}: {
  inscricaoId: string
  acompanhanteId?: string
  campos: CampoPersonalizadoResponse[]
  respostasIniciais: RespostaResponse[]
  abrirAutomaticamente: boolean
}) {
  const [modalAberto, setModalAberto] = useState(abrirAutomaticamente)

  const valores = Object.fromEntries(respostasIniciais.map((r) => [r.campoId, r.valor]))
  const pendentes = campos.filter((c) => c.obrigatorio && !(valores[c.id]?.trim()))

  return (
    <>
      {pendentes.length > 0 ? (
        <button type="button" className={styles.avisoBtn} onClick={() => setModalAberto(true)}>
          Faltam {pendentes.length === 1 ? '1 pergunta obrigatória' : `${pendentes.length} perguntas obrigatórias`} do evento — Responder
        </button>
      ) : (
        <button type="button" className={styles.linkVer} onClick={() => setModalAberto(true)}>
          Respostas do evento enviadas — ver/editar
        </button>
      )}

      {modalAberto && (
        <ModalResponderCamposPersonalizados
          inscricaoId={inscricaoId}
          acompanhanteId={acompanhanteId}
          campos={campos}
          respostasIniciais={respostasIniciais}
          onClose={() => setModalAberto(false)}
          onSalvo={() => setModalAberto(false)}
        />
      )}
    </>
  )
}
