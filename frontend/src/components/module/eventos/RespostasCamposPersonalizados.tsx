'use client'

import { useState } from 'react'
import { useCamposPersonalizadosMinha } from '@/hooks/evento/useCamposPersonalizadosMinha'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import { ModalResponderCamposPersonalizados } from './ModalResponderCamposPersonalizados'
import type { CampoPersonalizadoResponse, RespostaResponse } from '@/types/campoPersonalizado.type'
import styles from './RespostasCamposPersonalizados.module.css'

export function RespostasCamposPersonalizados({
  eventoId, inscricaoId, abrirAutomaticamente = false,
}: { eventoId: string; inscricaoId: string; abrirAutomaticamente?: boolean }) {
  // Este componente é usado só pra própria inscrição (titular) — a lista filtrada já
  // pula o que a Pessoa dele já tem cadastrado.
  const { data: campos } = useCamposPersonalizadosMinha(eventoId)
  const { data: respostas } = useRespostasCampos(inscricaoId)

  if (!campos || campos.length === 0 || !respostas) return null

  // key pelas respostas já carregadas: estado local (abertura do modal) nasce do servidor,
  // sem useEffect pra sincronizar (mesmo padrão do CamposPersonalizadosPainel).
  return (
    <Gatilho
      key={inscricaoId}
      inscricaoId={inscricaoId}
      campos={campos}
      respostasIniciais={respostas}
      abrirAutomaticamente={abrirAutomaticamente}
    />
  )
}

function Gatilho({
  inscricaoId, campos, respostasIniciais, abrirAutomaticamente,
}: {
  inscricaoId: string
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
          campos={campos}
          respostasIniciais={respostasIniciais}
          onClose={() => setModalAberto(false)}
          onSalvo={() => setModalAberto(false)}
        />
      )}
    </>
  )
}
