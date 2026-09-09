'use client'

import { useRef, useState } from 'react'
import axios from 'axios'
import Link from 'next/link'
import { useEntrarComoConvidado } from '@/hooks/convite/useEntrarComoConvidado'
import { useIrParaCheckout } from '@/hooks/pagamento/useIrParaCheckout'
import { useRolarParaErro } from '@/hooks/forms/useRolarParaErro'
import { CamposExtrasForm } from '@/components/module/eventos/CamposExtrasForm'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { formatarTelefone } from '@/lib/masks'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import type { ApiError } from '@/types/api.types'
import styles from './ConvitePublico.module.css'

interface Props {
  token: string
  eventoId: string
  campos: CampoPersonalizadoResponse[]
  /** Evento pago torna o e-mail obrigatório (comprovante de pagamento). */
  preco: number | null
  onSucesso: () => void
}

export function FormularioConvidado({ token, eventoId, campos, preco, onSucesso }: Props) {
  const irParaCheckout = useIrParaCheckout()
  const entrar = useEntrarComoConvidado(token)
  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [email, setEmail] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouEnviar, setTentouEnviar] = useState(false)

  const formRef = useRef<HTMLDivElement>(null)
  const { rolarParaErro } = useRolarParaErro(formRef)

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function telefoneValido(): boolean {
    const digitos = telefone.replace(/\D/g, '')
    return digitos.length === 10 || digitos.length === 11
  }

  function emailValido(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
  }

  function aoConfirmar() {
    setTentouEnviar(true)
    const invalido =
      !nome.trim() || !telefoneValido() || camposObrigatoriosPendentes() ||
      (preco !== null && !emailValido())
    if (invalido) {
      // deixa o React pintar os erros (tentouEnviar acabou de virar true) antes de rolar
      requestAnimationFrame(() => rolarParaErro())
      return
    }

    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    entrar.mutate(
      { nome: nome.trim(), telefone: telefone.replace(/\D/g, ''), email: email.trim() || undefined, respostas },
      {
        onSuccess: (resposta) => {
          if (resposta.cobrancaId) {
            irParaCheckout(eventoId, resposta.cobrancaId, undefined, 0)
          } else {
            onSucesso()
          }
        },
      },
    )
  }

  return (
    <div className={styles.formulario} ref={formRef}>
      <label className={styles.campo}>
        <span className={styles.label}>Nome*</span>
        <input
          type="text"
          placeholder="Ex.: Maria Souza"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
        />
        {tentouEnviar && !nome.trim() && (
          <Transicao modo="fade"><span className={styles.erroTexto} data-campo-erro>O nome é obrigatório.</span></Transicao>
        )}
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
        {tentouEnviar && !telefone.trim() && (
          <Transicao modo="fade"><span className={styles.erroTexto} data-campo-erro>O telefone é obrigatório.</span></Transicao>
        )}
        {tentouEnviar && telefone.trim() && !telefoneValido() && (
          <Transicao modo="fade"><span className={styles.erroTexto} data-campo-erro>Telefone inválido. Digite um número válido com DDD.</span></Transicao>
        )}
      </label>

      {preco !== null && (
        <label className={styles.campo}>
          <span className={styles.label}>E-mail*</span>
          <input
            type="email"
            placeholder="Ex.: maria@email.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <span className={styles.dica}>Evento pago — o comprovante de pagamento é enviado pra esse e-mail.</span>
          {tentouEnviar && !email.trim() && (
            <Transicao modo="fade"><span className={styles.erroTexto} data-campo-erro>O e-mail é obrigatório em evento pago.</span></Transicao>
          )}
          {tentouEnviar && email.trim() && !emailValido() && (
            <Transicao modo="fade"><span className={styles.erroTexto} data-campo-erro>E-mail inválido.</span></Transicao>
          )}
        </label>
      )}

      <CamposExtrasForm campos={campos} valores={camposValores} onChange={(id, valor) => setCamposValores((v) => ({ ...v, [id]: valor }))} tentouEnviar={tentouEnviar} />

      {entrar.isError && (
        <Transicao modo="fade">
          <p className={styles.erroTexto}>
            {axios.isAxiosError<ApiError>(entrar.error) && entrar.error.response?.data?.message
              ? entrar.error.response.data.message
              : 'Não foi possível confirmar sua inscrição. Tente novamente.'}
          </p>
        </Transicao>
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
