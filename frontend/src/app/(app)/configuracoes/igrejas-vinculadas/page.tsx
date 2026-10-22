'use client'

import { useState, useEffect, Suspense } from 'react'
import NextLink from 'next/link'
import { useSearchParams } from 'next/navigation'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Church, KeyRound, Link2, Copy, RefreshCw, Unlink, Info, AlertTriangle, MessageCircle, Lock, ArrowUpRight } from 'lucide-react'
import {
  useVinculoStatus, useGerarCodigo, useEntrarNaFamilia,
  useDesvincularCongregacao, useSairDaFamilia,
} from '@/hooks/igreja/useVinculo'
import { obterResumoAssinatura } from '@/services/assinatura.service'
import { authService } from '@/services/auth.service'
import { useAuthStore } from '@/store/authStore'
import { CompartilharConviteCongregacao } from '@/components/common/CompartilharConviteCongregacao/CompartilharConviteCongregacao'
import { notificar } from '@/components/common/Notificacao/notificar'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { SkeletonIgrejasVinculadas } from './SkeletonIgrejasVinculadas'
import type { IgrejaResumo } from '@/types/igreja/vinculo.type'
import styles from './vinculadas.module.css'

function formatarData(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('pt-BR')
}

function localDe(igreja: IgrejaResumo): string {
  if (igreja.cidade && igreja.uf) return `${igreja.cidade}, ${igreja.uf}`
  return igreja.cidade ?? '—'
}

function mensagemDeErro(erro: unknown, padrao: string): string {
  return (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ?? padrao
}

function IgrejasVinculadasConteudo() {
  const queryClient = useQueryClient()
  const searchParams = useSearchParams()
  const { data: status, isLoading } = useVinculoStatus()
  const { data: resumoAssinatura } = useQuery({
    queryKey: ['assinatura-resumo'],
    queryFn: obterResumoAssinatura,
  })
  const podeTerFiliais = (resumoAssinatura?.limiteCongregacoes ?? 0) > 0
  const totalCongregacoes = resumoAssinatura?.totalCongregacoes ?? status?.congregacoes?.length ?? 0
  const limiteCongregacoes = resumoAssinatura?.limiteCongregacoes ?? 0
  const limiteAtingido = podeTerFiliais && totalCongregacoes >= limiteCongregacoes
  const gerarCodigo = useGerarCodigo()
  const entrar = useEntrarNaFamilia()
  const desvincular = useDesvincularCongregacao()
  const sair = useSairDaFamilia()
  const { congregacao, concordar } = useRotulos()
  const igrejaNome = useAuthStore((s) => s.igrejaNome)

  const [codigoDigitado, setCodigoDigitado] = useState('')
  const [mostrandoCampo, setMostrandoCampo] = useState(false)
  // Qual congregação a sede está prestes a remover (null = modal fechado).
  const [congregacaoParaRemover, setCongregacaoParaRemover] = useState<IgrejaResumo | null>(null)
  const [confirmandoSaida, setConfirmandoSaida] = useState(false)

  useEffect(() => {
    const codUrl = searchParams.get('codigo')
    if (codUrl) {
      setCodigoDigitado(codUrl.trim().toUpperCase())
      setMostrandoCampo(true)
    }
  }, [searchParams])

  if (isLoading || !status) {
    return <SkeletonIgrejasVinculadas />
  }

  function copiarCodigo() {
    if (!status?.codigoVinculo) return
    navigator.clipboard.writeText(status.codigoVinculo)
    notificar.sucesso('Código copiado', `Envie para o responsável da ${congregacao.singular.toLowerCase()}.`)
  }

  function aoGerarCodigo() {
    const rotacionando = !!status?.codigoVinculo
    gerarCodigo.mutate(undefined, {
      onSuccess: () => {
        notificar.sucesso(
          rotacionando ? 'Novo link de convite gerado' : 'Link de convite gerado',
          rotacionando
            ? `O convite anterior deixou de funcionar. ${concordar(congregacao.genero, 'os')} ${congregacao.plural.toLowerCase()} já ${concordar(congregacao.genero, 'vinculados')} continuam no grupo.`
            : `Compartilhe com ${concordar(congregacao.genero, 'os_min')} ${congregacao.plural.toLowerCase()} que devem entrar no seu grupo.`,
        )
      },
      onError: (e) => notificar.erro('Não foi possível gerar o convite', mensagemDeErro(e, 'Tente novamente.')),
    })
  }

  function aoEntrar(evento: React.FormEvent) {
    evento.preventDefault()
    let cod = codigoDigitado.trim().toUpperCase()
    if (cod.includes('CODIGO=')) {
      const match = cod.match(/CODIGO=([A-Z0-9-]+)/)
      if (match) cod = match[1]
    }
    entrar.mutate(cod, {
      onSuccess: async () => {
        setCodigoDigitado('')
        setMostrandoCampo(false)
        notificar.sucesso('Vínculo criado', `Sua igreja agora faz parte do grupo de ${congregacao.plural.toLowerCase()}.`)
        queryClient.invalidateQueries({ queryKey: ['assinatura-resumo'] })
        try {
          const sessao = await authService.me({ semRedirect: true })
          if (sessao) {
            useAuthStore.setState({
              statusAssinatura: sessao.statusAssinatura,
              igrejaMaeId: sessao.igrejaMaeId,
            })
          }
        } catch {
          // fallback
        }
      },
      onError: (e) => notificar.erro('Não foi possível vincular', mensagemDeErro(e, 'Verifique o código e tente novamente.')),
    })
  }

  function confirmarDesvinculo() {
    const alvo = congregacaoParaRemover
    if (!alvo) return

    desvincular.mutate(alvo.id, {
      onSuccess: () => {
        setCongregacaoParaRemover(null)
        notificar.sucesso(`${congregacao.singular} ${concordar(congregacao.genero, 'removido')}`, `"${alvo.nome}" saiu do grupo.`)
        queryClient.invalidateQueries({ queryKey: ['assinatura-resumo'] })
      },
      onError: (e) => notificar.erro('Não foi possível remover', mensagemDeErro(e, 'Tente novamente.')),
    })
  }

  function confirmarSaida() {
    sair.mutate(undefined, {
      onSuccess: async () => {
        setConfirmandoSaida(false)
        notificar.sucesso('Você saiu do grupo', 'Sua igreja voltou a ser independente.')
        queryClient.invalidateQueries({ queryKey: ['assinatura-resumo'] })
        try {
          const sessao = await authService.me({ semRedirect: true })
          if (sessao) {
            useAuthStore.setState({
              statusAssinatura: sessao.statusAssinatura,
              igrejaMaeId: sessao.igrejaMaeId,
            })
          }
        } catch {
          // fallback
        }
      },
      onError: (e) => notificar.erro('Não foi possível sair', mensagemDeErro(e, 'Tente novamente.')),
    })
  }

  // ---------- FILHA: a congregação só vê a mãe, nunca as irmãs ----------
  if (status.estado === 'FILHA') {
    return (
      <div className={styles.container}>
        <section className={styles.cartaoFilha}>
          <span className={styles.codigoRotulo}>Sua igreja é {concordar(congregacao.genero, 'um')} {congregacao.singular.toLowerCase()} de</span>
          <p className={styles.maeNome}>{status.mae?.nome}</p>
          <p className={styles.maeDetalhe}>
            Vinculada desde {formatarData(status.mae?.vinculadoEm ?? null)}
          </p>

          <p className={styles.aviso}>
            <AlertTriangle size={14} aria-hidden="true" />
            A sede vê os números consolidados da sua igreja (membros, eventos e financeiro).
            Você pode sair do grupo a qualquer momento.
          </p>

          <div className={styles.faixaAcoes} style={{ marginTop: 20 }}>
            <button
              className={styles.botaoPerigo}
              onClick={() => setConfirmandoSaida(true)}
              disabled={sair.isPending}
            >
              <Unlink size={14} aria-hidden="true" />
              {sair.isPending ? 'Saindo...' : 'Sair do grupo'}
            </button>
          </div>
        </section>

        {confirmandoSaida && (
          <ModalConfirmacaoCritica
            titulo="Sair do grupo"
            mensagem={
              <>
                Sua igreja deixará de ser {congregacao.singular.toLowerCase()} de{' '}
                <strong>{status.mae?.nome}</strong>.
              </>
            }
            consequencias={[
              { tipo: 'perde', texto: 'A igreja perderá a cobertura de assinatura e entrará em modo somente leitura até contratar um plano próprio.' },
              { tipo: 'perde', texto: 'Seus números somem dos relatórios consolidados da sede.' },
              { tipo: 'perde', texto: 'Para voltar, será preciso um novo convite da sede.' },
              { tipo: 'mantem', texto: 'Membros, eventos e financeiro da sua igreja ficam intactos.' },
              { tipo: 'mantem', texto: 'Ninguém perde acesso ao sistema.' },
            ]}
            palavraConfirmacao={status.mae?.nome ?? 'SAIR'}
            textoConfirmar="Sair do grupo"
            isLoading={sair.isPending}
            onConfirmar={confirmarSaida}
            onClose={() => setConfirmandoSaida(false)}
          />
        )}
      </div>
    )
  }

  // ---------- INDEPENDENTE e MÃE ----------
  const ehMae = status.estado === 'MAE'

  return (
    <div className={styles.container}>
      <section className={styles.faixa}>
        <div className={styles.faixaTexto}>
          <h2 className={styles.faixaTitulo}>Gerenciar {congregacao.plural.toLowerCase()}</h2>
          <p className={styles.faixaSubtitulo}>
            {ehMae
              ? `${congregacao.plural} ${concordar(congregacao.genero, 'vinculados')} compartilham os números delas com a sede.`
              : `Compartilhe o link de convite para suas ${congregacao.plural.toLowerCase()} entrarem, ou entre no grupo de outra igreja usando o convite dela.`}
          </p>
        </div>

        <div className={styles.faixaAcoes}>
          {!ehMae && (
            <button className={styles.botaoSecundario} onClick={() => setMostrandoCampo((v) => !v)}>
              <Link2 size={16} aria-hidden="true" />
              Vincular com código
            </button>
          )}
          {podeTerFiliais && (
            <button
              className={styles.botaoPrimario}
              onClick={aoGerarCodigo}
              disabled={gerarCodigo.isPending || limiteAtingido}
              title={limiteAtingido ? `Limite de ${congregacao.plural.toLowerCase()} atingido no plano atual` : undefined}
            >
              <KeyRound size={16} aria-hidden="true" />
              {status.codigoVinculo ? 'Gerar novo link' : 'Gerar link de convite'}
            </button>
          )}
        </div>
      </section>

      {!podeTerFiliais && !ehMae && (
        <section className={styles.cardBloqueado}>
          <div className={styles.bloqueadoHeader}>
            <Lock size={16} aria-hidden="true" />
            <span className={styles.bloqueadoBadge}>Recurso Bloqueado</span>
          </div>
          <p className={styles.bloqueadoTexto}>
            Conectar e gerenciar congregações vinculadas é exclusivo a partir do Plano Pro (até 2 congregações filhas inclusas).
          </p>
          <NextLink href="/planos" className={styles.btnUpgradeInline}>
            <span>Fazer upgrade para liberar congregações filiais</span>
            <ArrowUpRight size={14} aria-hidden="true" />
          </NextLink>
        </section>
      )}

      {/* Entrar num grupo só faz sentido para quem ainda não tem congregações:
          a regra dos 2 níveis impede que uma mãe vire filha. */}
      {!ehMae && mostrandoCampo && (
        <section className={styles.faixa}>
          <div className={styles.faixaTexto}>
            <h2 className={styles.faixaTitulo}>Entrar num grupo</h2>
            <p className={styles.faixaSubtitulo}>
              Cole o link de convite ou digite o código gerado pela igreja sede. Ao entrar, os números da sua igreja
              passam a aparecer nos relatórios dela e sua mensalidade fica coberta pela matriz.
            </p>

            <form className={styles.formCodigo} onSubmit={aoEntrar}>
              <input
                className={styles.inputCodigo}
                value={codigoDigitado}
                onChange={(e) => {
                  let val = e.target.value.toUpperCase()
                  if (val.includes('CODIGO=')) {
                    const match = val.match(/CODIGO=([A-Z0-9-]+)/)
                    if (match) val = match[1]
                  }
                  setCodigoDigitado(val)
                }}
                placeholder="DOMUS-XXXXXX"
                maxLength={100}
                aria-label="Código ou link de convite"
              />
              <button
                type="submit"
                className={styles.botaoPrimario}
                disabled={!codigoDigitado.trim() || entrar.isPending}
              >
                {entrar.isPending ? 'Vinculando...' : 'Vincular'}
              </button>
            </form>
          </div>
        </section>
      )}

      {podeTerFiliais && limiteAtingido && (
        <section className={styles.cardBloqueado}>
          <div className={styles.bloqueadoHeader}>
            <AlertTriangle size={16} color="var(--color-warning, #d97706)" aria-hidden="true" />
            <span className={styles.bloqueadoBadge} style={{ color: 'var(--color-warning-dark, #b45309)' }}>
              Capacidade Máxima Atingida ({limiteCongregacoes} de {limiteCongregacoes} {congregacao.plural.toLowerCase()})
            </span>
          </div>
          <p className={styles.bloqueadoTexto}>
            Sua igreja preencheu todas as {congregacao.plural.toLowerCase()} inclusas no plano atual. Para conectar novas {congregacao.plural.toLowerCase()}, faça upgrade do seu plano.
          </p>
          <NextLink href="/planos" className={styles.btnUpgradeInline}>
            <span>Fazer upgrade de plano para liberar mais {congregacao.plural.toLowerCase()}</span>
            <ArrowUpRight size={14} aria-hidden="true" />
          </NextLink>
        </section>
      )}

      {podeTerFiliais && !limiteAtingido && (
        <CompartilharConviteCongregacao
          codigo={status.codigoVinculo}
          nomeMatriz={igrejaNome || null}
          onGerar={aoGerarCodigo}
          isGerando={gerarCodigo.isPending}
        />
      )}

      {ehMae && (
        <>
          <section className={styles.cartao}>
            <div className={styles.tabelaWrapper}>
              <table className={styles.tabela}>
                <thead>
                  <tr>
                    <th>Nome da {congregacao.singular.toLowerCase()}</th>
                    <th>Cidade/Estado</th>
                    <th>Data de vínculo</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {status.congregacoes.map((c) => (
                    <tr key={c.id}>
                      <td data-rotulo={congregacao.singular}>
                        <span className={styles.celulaNome}>
                          <span className={styles.avatarIgreja}>
                            <Church size={16} aria-hidden="true" />
                          </span>
                          <span className={styles.nomeIgreja}>{c.nome}</span>
                        </span>
                      </td>
                      <td data-rotulo="Cidade/Estado">{localDe(c)}</td>
                      <td data-rotulo="Vinculada em">{formatarData(c.vinculadoEm)}</td>
                      <td>
                        <button
                          className={styles.botaoPerigo}
                          onClick={() => setCongregacaoParaRemover(c)}
                          disabled={desvincular.isPending}
                        >
                          <Unlink size={14} aria-hidden="true" />
                          Desvincular
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <p className={styles.rodapeInfo}>
            <Info size={14} aria-hidden="true" />
            Igrejas vinculadas compartilham relatórios consolidados com a sede.
          </p>
        </>
      )}

      {!ehMae && !status.codigoVinculo && !mostrandoCampo && (
        <section className={styles.cartao}>
          <p className={styles.vazio}>
            Sua igreja ainda não faz parte de nenhum grupo.
          </p>
        </section>
      )}

      {congregacaoParaRemover && (
        <ModalConfirmacaoCritica
          titulo={`Desvincular ${congregacao.singular.toLowerCase()}`}
          mensagem={
            <>
              <strong>{congregacaoParaRemover.nome}</strong> deixará de fazer parte do seu
              grupo de {congregacao.plural.toLowerCase()}.
            </>
          }
          consequencias={[
            { tipo: 'perde', texto: `A ${congregacao.singular.toLowerCase()} perderá a cobertura de assinatura e entrará em modo somente leitura até contratar um plano próprio.` },
            { tipo: 'perde', texto: 'Os números dela somem dos seus relatórios consolidados.' },
            { tipo: 'perde', texto: 'Você deixa de ver os relatórios financeiros dela.' },
            { tipo: 'perde', texto: 'Para religar, será preciso enviar um novo convite.' },
            { tipo: 'mantem', texto: `O cadastro da ${congregacao.singular.toLowerCase()} continua intacto: membros, eventos e financeiro.` },
            { tipo: 'mantem', texto: 'Ninguém perde acesso ao sistema.' },
          ]}
          palavraConfirmacao={congregacaoParaRemover.nome}
          textoConfirmar={`Desvincular ${congregacao.singular.toLowerCase()}`}
          isLoading={desvincular.isPending}
          onConfirmar={confirmarDesvinculo}
          onClose={() => setCongregacaoParaRemover(null)}
        />
      )}
    </div>
  )
}

export default function IgrejasVinculadasPage() {
  return (
    <Suspense fallback={<SkeletonIgrejasVinculadas />}>
      <IgrejasVinculadasConteudo />
    </Suspense>
  )
}
