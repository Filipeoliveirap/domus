'use client'

import { useEffect, useRef, useState } from 'react'
import { X, Copy, Check } from 'lucide-react'
import { useGerarConvite } from '@/hooks/inscricao/useGerarConvite'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import painelStyles from './ModalInscreverPessoas.module.css'
import styles from './ModalCompartilharConvite.module.css'

interface Props {
  eventoId: string
  onClose: () => void
}

export function ModalCompartilharConvite({ eventoId, onClose }: Props) {
  const { data: minha } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever(eventoId, true)
  const gerarConvite = useGerarConvite(eventoId)
  // Marca que a pergunta "você também vai participar?" já foi respondida (sim ou não) —
  // precisa ser um estado PRÓPRIO, não derivado de `minha.inscrito`: a resposta da API só
  // chega depois (assíncrono), então usar só `minha.inscrito` deixava a pergunta reaparecendo
  // (ou travada) entre o clique em "sim" e o refetch confirmar a inscrição.
  const [jaRespondeu, setJaRespondeu] = useState(false)
  const [copiado, setCopiado] = useState(false)
  const jaTentouGerar = useRef(false)

  // Já inscrito (sem precisar perguntar) OU já respondeu a pergunta: gera o convite sozinho.
  // Só chama mutate() aqui — nunca setState direto no corpo do efeito.
  const podeGerarDireto = (minha !== undefined && minha.inscrito) || jaRespondeu
  useEffect(() => {
    if (!podeGerarDireto || jaTentouGerar.current) return
    jaTentouGerar.current = true
    gerarConvite.mutate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [podeGerarDireto])

  const perguntaParticipar = minha !== undefined && !minha.inscrito && !jaRespondeu

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  function aoResponderParticipar(sim: boolean) {
    setJaRespondeu(true)
    if (sim) {
      jaTentouGerar.current = true
      inscrever.mutate(undefined, { onSuccess: () => gerarConvite.mutate(), onError: () => gerarConvite.mutate() })
    }
  }

  function copiarLink() {
    if (!gerarConvite.data) return
    navigator.clipboard.writeText(gerarConvite.data.link)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  function abrirWhatsapp() {
    if (!gerarConvite.data) return
    const texto = encodeURIComponent(`Você foi convidado! ${gerarConvite.data.link}`)
    window.open(`https://wa.me/?text=${texto}`, '_blank')
  }

  if (perguntaParticipar) {
    return (
      <ModalConfirmacao
        titulo="Você também vai participar?"
        textoConfirmar="Sim, também vou"
        textoCancelar="Não, só estou compartilhando"
        onConfirmar={() => aoResponderParticipar(true)}
        onClose={() => aoResponderParticipar(false)}
        mensagem={<p>Quer se inscrever nesse evento também, antes de compartilhar o link?</p>}
      />
    )
  }

  return (
    <div className={painelStyles.overlay} onMouseDown={onClose}>
      <div
        className={painelStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-compartilhar-convite"
      >
        <div className={painelStyles.header}>
          <div>
            <h2 className={painelStyles.titulo} id="titulo-compartilhar-convite">Compartilhar evento</h2>
          </div>
          <button type="button" className={painelStyles.btnFechar} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.corpo}>
          <p className={styles.aviso}>Quem usar este link entra como seu convidado.</p>

          {gerarConvite.isPending && <p className={styles.estado}>Gerando link…</p>}
          {gerarConvite.isError && <p className={styles.estado}>Não foi possível gerar o link. Tente novamente.</p>}

          {gerarConvite.data && (
            <>
              <div className={styles.linkBox}>
                <input type="text" readOnly value={gerarConvite.data.link} className={styles.linkInput} />
                <button type="button" onClick={copiarLink} className={styles.btnCopiar}>
                  {copiado ? <Check size={16} /> : <Copy size={16} />}
                  {copiado ? 'Copiado' : 'Copiar'}
                </button>
              </div>
              <button type="button" onClick={abrirWhatsapp} className={styles.btnWhatsapp}>
                Enviar por WhatsApp
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
