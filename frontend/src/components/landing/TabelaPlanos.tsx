'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { AlertCircle } from 'lucide-react'
import { buscarPlanosServidor, PlanoServidor } from '@/services/plano.service'
import { obterResumoAssinatura, alterarPlanoAssinatura, ResumoAssinatura } from '@/services/assinatura.service'
import { authService } from '@/services/auth.service'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarAssinatura } from '@/lib/permissoes'
import { ModalContratarPlano } from '@/components/module/assinatura/ModalContratarPlano'
import { ModalAcessoRestritoPlano } from './ModalAcessoRestritoPlano'
import { ModalConfirmarTrocaPlano } from './ModalConfirmarTrocaPlano'
import { SucessoContratacaoPlano } from '@/components/module/assinatura/SucessoContratacaoPlano'
import styles from '@/app/(public)/planos/planos.module.css'

export default function TabelaPlanos() {
  const [planos, setPlanos] = useState<PlanoServidor[]>([])
  const [resumo, setResumo] = useState<ResumoAssinatura | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [planoParaContratar, setPlanoParaContratar] = useState<PlanoServidor | null>(null)
  const [planoParaConfirmar, setPlanoParaConfirmar] = useState<PlanoServidor | null>(null)
  const [planoContratado, setPlanoContratado] = useState<PlanoServidor | null>(null)
  const [modoSucesso, setModoSucesso] = useState<'CONTRATACAO' | 'UPGRADE'>('CONTRATACAO')
  const [modalRestritoAberto, setModalRestritoAberto] = useState(false)

  const role = useAuthStore((s) => s.role)
  const igrejaNome = useAuthStore((s) => s.igrejaNome)
  const logado = Boolean(role)
  const ehAdmin = podeGerenciarAssinatura(role)

  useEffect(() => {
    buscarPlanosServidor()
      .then((res) => {
        if (Array.isArray(res)) {
          setPlanos(res)
        }
        setCarregando(false)
      })
      .catch(() => setCarregando(false))

    if (logado) {
      obterResumoAssinatura()
        .then(setResumo)
        .catch(() => undefined)
    }
  }, [logado])

  const handleSucessoContratacao = async (plano: PlanoServidor) => {
    setPlanoParaContratar(null)
    setPlanoContratado(plano)
    try {
      const sessaoAtualizada = await authService.me({ semRedirect: true })
      if (sessaoAtualizada) {
        useAuthStore.setState({ statusAssinatura: sessaoAtualizada.statusAssinatura })
      }
    } catch {
      // Fallback
    }
  }

  const handleSelecionarPlano = async (p: PlanoServidor) => {
    if (!logado) return

    if (!ehAdmin) {
      setModalRestritoAberto(true)
      return
    }

    // Se já possui assinatura ativa/trial com cartão (não pausada): abre modal de confirmação de troca
    if (resumo && resumo.status !== 'PAUSADA' && resumo.plano) {
      if (resumo.plano === p.id) {
        return
      }
      setPlanoParaConfirmar(p)
      return
    }

    // Se está em modo somente leitura (PAUSADA) ou sem assinatura: abre checkout de cartão
    setModoSucesso('CONTRATACAO')
    setPlanoParaContratar(p)
  }

  if (planoContratado) {
    return (
      <SucessoContratacaoPlano
        plano={planoContratado}
        nomeIgreja={igrejaNome}
        modo={modoSucesso}
      />
    )
  }

  if (carregando) {
    return (
      <div style={{ textAlign: 'center', padding: '48px', color: 'var(--color-text-secondary)' }}>
        Carregando catálogo de planos...
      </div>
    )
  }

  return (
    <>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Escolha o plano ideal para sua igreja</h1>
        <p className={styles.subtitulo}>
          Cadastre sua igreja ou contrate o plano ideal para continuar gerenciando sua comunidade com o Domus.
        </p>
      </header>

      <div className={styles.gridPlanos}>
        {(Array.isArray(planos) ? planos : []).map((p) => {
          const popular = p.id === 'PRO'

          const totalPessoas = resumo?.totalPessoas ?? 0
          const totalCongregacoes = resumo?.totalCongregacoes ?? 0
          const invalidoPorPessoas = logado && resumo ? totalPessoas > p.limitePessoas : false
          const invalidoPorCongregacoes = logado && resumo ? totalCongregacoes > p.limiteCongregacoes : false
          const desabilitado = invalidoPorPessoas || invalidoPorCongregacoes

          return (
            <div
              key={p.id}
              className={`${styles.cardPlano} ${popular ? styles.cardPopular : ''} ${desabilitado ? styles.cardInvalido : ''}`}
            >
              {popular && <span className={styles.badgePopular}>Mais Popular</span>}

              <div>
                <h2 className={styles.nomePlano}>{p.nomeExibicao}</h2>
                <p className={styles.limitesText}>
                  Até {p.limitePessoas === 99999 ? 'Ilimitadas' : `${p.limitePessoas}`} pessoas •{' '}
                  {p.limiteCongregacoes === 0 ? 'Sem congregações' : `${p.limiteCongregacoes} congregações`}
                </p>

                <div className={styles.precoBox}>
                  <span className={styles.cifrao}>R$</span>
                  <span className={styles.valor}>
                    {p.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}
                  </span>
                  <span className={styles.periodo}>/mês</span>
                </div>

                <hr className={styles.divisoria} />

                <h3 className={styles.tituloFeatures}>Recursos Incluídos:</h3>
                <div className={styles.listaFeatures}>
                  {p.descricoesFeaturesHabilitadas.map((desc, i) => (
                    <div key={i} className={styles.itemFeature}>
                      <span className={styles.iconeCheck}>✓</span>
                      <span>{desc}</span>
                    </div>
                  ))}
                </div>

                {desabilitado && (
                  <div className={styles.erroLimite}>
                    <AlertCircle size={14} aria-hidden="true" style={{ flexShrink: 0 }} />
                    <span>
                      {invalidoPorPessoas
                        ? `Sua igreja possui ${totalPessoas} pessoas (limite: ${p.limitePessoas}).`
                        : `Sua igreja possui ${totalCongregacoes} congregações (limite: ${p.limiteCongregacoes}).`}
                    </span>
                  </div>
                )}
              </div>

              {logado ? (
                desabilitado ? (
                  <button
                    type="button"
                    disabled
                    className={`${styles.btnAssinar} ${styles.btnDisabled}`}
                  >
                    Limite atual excede plano
                  </button>
                ) : resumo && resumo.status !== 'PAUSADA' && resumo.plano === p.id ? (
                  <button
                    type="button"
                    disabled
                    className={`${styles.btnAssinar} ${styles.btnDisabled}`}
                  >
                    Plano atual
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => handleSelecionarPlano(p)}
                    className={`${styles.btnAssinar} ${popular ? styles.btnPrimary : styles.btnSecondary}`}
                  >
                    {resumo && resumo.status !== 'PAUSADA' && resumo.plano ? 'Alterar para este plano' : 'Contratar plano'}
                  </button>
                )
              ) : (
                <Link
                  href={`/cadastro?plano=${p.id}`}
                  className={`${styles.btnAssinar} ${popular ? styles.btnPrimary : styles.btnSecondary}`}
                >
                  Testar 14 dias grátis
                </Link>
              )}
            </div>
          )
        })}
      </div>

      {planoParaContratar && (
        <ModalContratarPlano
          plano={planoParaContratar}
          resumo={resumo}
          aoFechar={() => setPlanoParaContratar(null)}
          aoSucesso={handleSucessoContratacao}
        />
      )}

      {planoParaConfirmar && resumo && (
        <ModalConfirmarTrocaPlano
          plano={planoParaConfirmar}
          resumo={resumo}
          aoFechar={() => setPlanoParaConfirmar(null)}
          aoSucesso={(plano) => {
            setPlanoParaConfirmar(null)
            setModoSucesso('UPGRADE')
            handleSucessoContratacao(plano)
          }}
        />
      )}

      {modalRestritoAberto && (
        <ModalAcessoRestritoPlano aoFechar={() => setModalRestritoAberto(false)} />
      )}
    </>
  )
}
