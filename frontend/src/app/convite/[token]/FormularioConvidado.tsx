'use client'

import { useState } from 'react'
import axios from 'axios'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEntrarComoConvidado } from '@/hooks/convite/useEntrarComoConvidado'
import { CamposExtrasForm } from '@/components/module/eventos/CamposExtrasForm'
import { formatarTelefone } from '@/lib/masks'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import type { ApiError } from '@/types/api.types'
import styles from './ConvitePublico.module.css'

interface Props {
  token: string
  eventoId: string
  campos: CampoPersonalizadoResponse[]
  onSucesso: () => void
}

export function FormularioConvidado({ token, eventoId, campos, onSucesso }: Props) {
  const router = useRouter()
  const entrar = useEntrarComoConvidado(token)
  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouEnviar, setTentouEnviar] = useState(false)

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function telefoneValido(): boolean {
    const digitos = telefone.replace(/\D/g, '')
    return digitos.length === 10 || digitos.length === 11
  }

  function aoConfirmar() {
    setTentouEnviar(true)
    if (!nome.trim() || !telefoneValido() || camposObrigatoriosPendentes()) return

    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    entrar.mutate(
      { nome: nome.trim(), telefone: telefone.replace(/\D/g, ''), respostas },
      {
        onSuccess: (resposta) => {
          if (resposta.cobrancaId) {
            router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaId}`)
          } else {
            onSucesso()
          }
        },
      },
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
        <span className={styles.label}>Telefone*</span>
        <input
          type="text"
          placeholder="(00) 00000-0000"
          inputMode="numeric"
          value={telefone}
          onChange={(e) => setTelefone(formatarTelefone(e.target.value))}
        />
        {tentouEnviar && !telefone.trim() && <span className={styles.erroTexto}>O telefone é obrigatório.</span>}
        {tentouEnviar && telefone.trim() && !telefoneValido() && (
          <span className={styles.erroTexto}>Telefone inválido. Digite um número válido com DDD.</span>
        )}
      </label>

      <CamposExtrasForm campos={campos} valores={camposValores} onChange={(id, valor) => setCamposValores((v) => ({ ...v, [id]: valor }))} tentouEnviar={tentouEnviar} />

      {entrar.isError && (
        <p className={styles.erroTexto}>
          {axios.isAxiosError<ApiError>(entrar.error) && entrar.error.response?.data?.message
            ? entrar.error.response.data.message
            : 'Não foi possível confirmar sua inscrição. Tente novamente.'}
        </p>
      )}

      <button type="button" className={styles.btnConfirmar} onClick={aoConfirmar} disabled={entrar.isPending}>
        {entrar.isPending ? 'Confirmando…' : 'Confirmar inscrição'}
      </button>

      <Link href={`/login?next=${encodeURIComponent(`/convite/${token}?entrar=1`)}`} className={styles.linkJaTenhoConta}>
        Já tenho conta — Fazer login
      </Link>
    </div>
  )
}
