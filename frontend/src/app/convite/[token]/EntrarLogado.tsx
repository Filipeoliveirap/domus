'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import { CamposExtrasForm } from '@/components/module/eventos/CamposExtrasForm'
import styles from './ConvitePublico.module.css'

interface Props {
  eventoId: string
  nomeUsuario: string
  onSucesso: () => void
}

/** Quem abre o convite já logado ganha inscrição própria de verdade (elegibilidade avaliada,
 *  ocupa vaga como qualquer inscrito) — nunca vira convidado sem cadastro, mesmo vindo por
 *  link (ver spec). Não guarda "veio de convite" pra Pessoa cadastrada, por decisão. */
export function EntrarLogado({ eventoId, nomeUsuario, onSucesso }: Props) {
  const router = useRouter()
  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever(eventoId, true)
  const { data: campos = [] } = useCamposPersonalizados(eventoId)
  const { responder, isLoading: respondendo } = useResponderCampos()

  const [inscricaoId, setInscricaoId] = useState<string | null>(null)
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouEnviar, setTentouEnviar] = useState(false)

  if (isLoading) return <p className={styles.estado}>Carregando…</p>

  // Já estava inscrita ANTES de abrir o link (não é o caso de "acabei de confirmar aqui" —
  // esse caso tem `inscricaoId` preenchido e cai no fluxo normal abaixo, mesmo depois do
  // refetch marcar `minha.inscrito = true`; sem o `!inscricaoId` aqui, o formulário de
  // perguntas extras sumia sozinho assim que a inscrição recém-criada era confirmada).
  if (minha?.inscrito && !inscricaoId) {
    return (
      <div className={styles.formulario}>
        <p className={styles.aviso}>{nomeUsuario}, você já está inscrito(a) nesse evento.</p>
        <button type="button" className={styles.btnConfirmar} onClick={() => router.push('/inicio')}>
          Continuar
        </button>
      </div>
    )
  }

  const idParaResponder = inscricaoId ?? minha?.id ?? null
  const jaConfirmouInscricao = idParaResponder !== null

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function aoConfirmarInscricao() {
    inscrever.mutate(undefined, {
      onSuccess: (dados) => {
        setInscricaoId(dados.id)
        if (campos.length === 0) onSucesso()
      },
    })
  }

  async function aoSalvarRespostas() {
    setTentouEnviar(true)
    if (camposObrigatoriosPendentes() || !idParaResponder) return

    const dados = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    const sucesso = await responder(idParaResponder, dados)
    if (sucesso) onSucesso()
  }

  if (!jaConfirmouInscricao) {
    return (
      <div className={styles.formulario}>
        <p className={styles.aviso}>Confirmar sua inscrição usando seu cadastro?</p>
        <button type="button" className={styles.btnConfirmar} onClick={aoConfirmarInscricao} disabled={inscrever.isPending}>
          {inscrever.isPending ? 'Confirmando…' : 'Confirmar inscrição'}
        </button>
      </div>
    )
  }

  return (
    <div className={styles.formulario}>
      <p className={styles.aviso}>Inscrição confirmada! Falta só responder abaixo.</p>
      <CamposExtrasForm campos={campos} valores={camposValores} onChange={(id, valor) => setCamposValores((v) => ({ ...v, [id]: valor }))} tentouEnviar={tentouEnviar} />
      <button type="button" className={styles.btnConfirmar} onClick={aoSalvarRespostas} disabled={respondendo}>
        {respondendo ? 'Salvando…' : 'Salvar e concluir'}
      </button>
    </div>
  )
}
