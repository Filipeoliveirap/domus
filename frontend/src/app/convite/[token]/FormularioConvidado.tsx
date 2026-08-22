'use client'

import { useState } from 'react'
import { useEntrarComoConvidado } from '@/hooks/convite/useEntrarComoConvidado'
import { CamposExtrasForm } from '@/components/module/eventos/CamposExtrasForm'
import { formatarTelefone } from '@/lib/masks'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import styles from './ConvitePublico.module.css'

interface Props {
  token: string
  campos: CampoPersonalizadoResponse[]
  onSucesso: () => void
}

export function FormularioConvidado({ token, campos, onSucesso }: Props) {
  const entrar = useEntrarComoConvidado(token)
  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouEnviar, setTentouEnviar] = useState(false)

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function aoConfirmar() {
    setTentouEnviar(true)
    if (!nome.trim() || camposObrigatoriosPendentes()) return

    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    entrar.mutate(
      { nome: nome.trim(), telefone: telefone || undefined, respostas },
      { onSuccess: onSucesso },
    )
  }

  return (
    <div className={styles.formulario}>
      <label className={styles.campo}>
        <span className={styles.label}>Nome*</span>
        <input
          type="text"
          placeholder="Ex.: Maria Souza"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
        />
        {tentouEnviar && !nome.trim() && <span className={styles.erroTexto}>O nome é obrigatório.</span>}
      </label>

      <label className={styles.campo}>
        <span className={styles.label}>Telefone (opcional)</span>
        <input
          type="text"
          placeholder="(00) 00000-0000"
          inputMode="numeric"
          value={telefone}
          onChange={(e) => setTelefone(formatarTelefone(e.target.value))}
        />
      </label>

      <CamposExtrasForm campos={campos} valores={camposValores} onChange={(id, valor) => setCamposValores((v) => ({ ...v, [id]: valor }))} tentouEnviar={tentouEnviar} />

      {entrar.isError && (
        <p className={styles.erroTexto}>Não foi possível confirmar sua inscrição. Tente novamente.</p>
      )}

      <button type="button" className={styles.btnConfirmar} onClick={aoConfirmar} disabled={entrar.isPending}>
        {entrar.isPending ? 'Confirmando…' : 'Confirmar inscrição'}
      </button>
    </div>
  )
}
