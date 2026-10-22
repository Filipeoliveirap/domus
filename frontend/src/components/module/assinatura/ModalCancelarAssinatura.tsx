'use client'

import React, { useState } from 'react'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { cancelarAssinatura } from '@/services/assinatura.service'

interface ModalCancelarAssinaturaProps {
  aoFechar: () => void
  aoSucesso: () => void
}

export function ModalCancelarAssinatura({ aoFechar, aoSucesso }: ModalCancelarAssinaturaProps) {
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const handleCancelar = async () => {
    setSalvando(true)
    setErro(null)
    try {
      await cancelarAssinatura()
      aoSucesso()
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        (err as { message?: string })?.message ||
        'Não foi possível cancelar a assinatura no momento.'
      setErro(msg)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <ModalConfirmacaoCritica
      titulo="Cancelar Assinatura"
      mensagem={
        <>
          Tem certeza de que deseja cancelar a assinatura do Domus da sua igreja?
        </>
      }
      consequencias={[
        { tipo: 'mantem', texto: 'Sua igreja continuará com acesso total até o término do ciclo atual.' },
        { tipo: 'perde', texto: 'Ao final do ciclo, a igreja entrará em modo somente leitura até contratar novo plano.' },
        { tipo: 'perde', texto: 'Congregações filhas perderão a cobertura da matriz ao término do ciclo.' },
        { tipo: 'mantem', texto: 'Nenhum dado, membro ou lançamento financeiro será apagado.' },
      ]}
      palavraConfirmacao="CANCELAR"
      textoConfirmar="Cancelar Assinatura"
      isLoading={salvando}
      erro={erro}
      onConfirmar={handleCancelar}
      onClose={aoFechar}
    />
  )
}
