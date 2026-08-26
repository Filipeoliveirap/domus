'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCamposPersonalizadosMinha } from '@/hooks/evento/useCamposPersonalizadosMinha'
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
  const { data: campos = [] } = useCamposPersonalizadosMinha(eventoId)
  const { responder, isLoading: respondendo } = useResponderCampos()

  // Só existe depois que ESTA visita confirma a inscrição (não é preenchido a partir de
  // `minha`, de propósito — ver o branch de "pendente de antes" abaixo, que trata esse
  // outro caso separadamente em vez de fingir que acabou de confirmar aqui).
  const [inscricaoId, setInscricaoId] = useState<string | null>(null)
  const [cobrancaPendenteId, setCobrancaPendenteId] = useState<string | null>(null)
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouEnviar, setTentouEnviar] = useState(false)

  if (isLoading) return <p className={styles.estado}>Carregando…</p>

  // Já estava confirmada ANTES de abrir o link.
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

  // Já existe uma inscrição pendente de pagamento de ANTES (reabriu o link, ou saiu no
  // meio do checkout) — retoma o pagamento direto, em vez de mostrar "confirmada" pra
  // quem na verdade ainda não pagou nada.
  if (!minha?.inscrito && minha?.cobrancaPendenteId && !inscricaoId) {
    return (
      <div className={styles.formulario}>
        <p className={styles.aviso}>Você já iniciou essa inscrição — falta só concluir o pagamento.</p>
        <button
          type="button"
          className={styles.btnConfirmar}
          onClick={() => router.push(`/eventos/${eventoId}/pagamento/${minha.cobrancaPendenteId}`)}
        >
          Continuar pagamento
        </button>
      </div>
    )
  }

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function aoConfirmarInscricao() {
    inscrever.mutate(undefined, {
      onSuccess: (dados) => {
        // Sem campo nenhum pra responder, não há por que passar pela tela intermediária
        // ("Inscrição confirmada! Falta só responder abaixo") — ela chegava a aparecer
        // por uma fração de segundo antes da navegação, mesmo sem pergunta nenhuma.
        if (campos.length === 0) {
          finalizar(dados.cobrancaPendenteId)
        } else {
          setInscricaoId(dados.id)
          setCobrancaPendenteId(dados.cobrancaPendenteId)
        }
      },
    })
  }

  /** Evento pago (cobrancaId presente) navega pro checkout dedicado; gratuito segue pro
   *  fluxo antigo (`onSucesso`, tela estática "Inscrição confirmada!"). */
  function finalizar(cobrancaId: string | null) {
    if (cobrancaId) {
      router.push(`/eventos/${eventoId}/pagamento/${cobrancaId}`)
    } else {
      onSucesso()
    }
  }

  async function aoSalvarRespostas() {
    setTentouEnviar(true)
    if (camposObrigatoriosPendentes() || !inscricaoId) return

    const dados = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    const sucesso = await responder(inscricaoId, dados)
    if (sucesso) finalizar(cobrancaPendenteId)
  }

  if (!inscricaoId) {
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
      <p className={styles.aviso}>
        {cobrancaPendenteId ? 'Falta só responder abaixo antes de pagar.' : 'Inscrição confirmada! Falta só responder abaixo.'}
      </p>
      <CamposExtrasForm campos={campos} valores={camposValores} onChange={(id, valor) => setCamposValores((v) => ({ ...v, [id]: valor }))} tentouEnviar={tentouEnviar} />
      <button type="button" className={styles.btnConfirmar} onClick={aoSalvarRespostas} disabled={respondendo}>
        {respondendo ? 'Salvando…' : 'Salvar e concluir'}
      </button>
    </div>
  )
}
