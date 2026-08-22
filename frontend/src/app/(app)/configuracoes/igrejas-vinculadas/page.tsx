'use client'

import { useState } from 'react'
import { Church, KeyRound, Link2, Copy, RefreshCw, Unlink, Info, AlertTriangle } from 'lucide-react'
import {
  useVinculoStatus, useGerarCodigo, useEntrarNaFamilia,
  useDesvincularCongregacao, useSairDaFamilia,
} from '@/hooks/igreja/useVinculo'
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

export default function IgrejasVinculadasPage() {
  const { data: status, isLoading } = useVinculoStatus()
  const gerarCodigo = useGerarCodigo()
  const entrar = useEntrarNaFamilia()
  const desvincular = useDesvincularCongregacao()
  const sair = useSairDaFamilia()
  const { congregacao, concordar } = useRotulos()

  const [codigoDigitado, setCodigoDigitado] = useState('')
  const [mostrandoCampo, setMostrandoCampo] = useState(false)
  // Qual congregação a sede está prestes a remover (null = modal fechado).
  const [congregacaoParaRemover, setCongregacaoParaRemover] = useState<IgrejaResumo | null>(null)
  const [confirmandoSaida, setConfirmandoSaida] = useState(false)

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
          rotacionando ? 'Novo código gerado' : 'Código gerado',
          rotacionando
            ? `O código anterior deixou de funcionar. ${concordar(congregacao.genero, 'os')} ${congregacao.plural.toLowerCase()} já ${concordar(congregacao.genero, 'vinculados')} continuam na família.`
            : `Compartilhe com ${concordar(congregacao.genero, 'os_min')} ${congregacao.plural.toLowerCase()} que devem entrar na sua família.`,
        )
      },
      onError: (e) => notificar.erro('Não foi possível gerar o código', mensagemDeErro(e, 'Tente novamente.')),
    })
  }

  function aoEntrar(evento: React.FormEvent) {
    evento.preventDefault()
    entrar.mutate(codigoDigitado, {
      onSuccess: () => {
        setCodigoDigitado('')
        setMostrandoCampo(false)
        notificar.sucesso('Vínculo criado', 'Sua igreja agora faz parte da família.')
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
        notificar.sucesso(`${congregacao.singular} ${concordar(congregacao.genero, 'removido')}`, `"${alvo.nome}" saiu da família.`)
      },
      onError: (e) => notificar.erro('Não foi possível remover', mensagemDeErro(e, 'Tente novamente.')),
    })
  }

  function confirmarSaida() {
    sair.mutate(undefined, {
      onSuccess: () => {
        setConfirmandoSaida(false)
        notificar.sucesso('Você saiu da família', 'Sua igreja voltou a ser independente.')
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
            Você pode sair da família a qualquer momento.
          </p>

          <div className={styles.faixaAcoes} style={{ marginTop: 20 }}>
            <button
              className={styles.botaoPerigo}
              onClick={() => setConfirmandoSaida(true)}
              disabled={sair.isPending}
            >
              <Unlink size={14} aria-hidden="true" />
              {sair.isPending ? 'Saindo...' : 'Sair da família'}
            </button>
          </div>
        </section>

        {confirmandoSaida && (
          <ModalConfirmacaoCritica
            titulo="Sair da família"
            mensagem={
              <>
                Sua igreja deixará de ser {congregacao.singular.toLowerCase()} de{' '}
                <strong>{status.mae?.nome}</strong>.
              </>
            }
            consequencias={[
              { tipo: 'perde', texto: 'Seus números somem dos relatórios consolidados da sede.' },
              { tipo: 'perde', texto: 'Para voltar, será preciso um novo código da sede.' },
              { tipo: 'mantem', texto: 'Membros, eventos e financeiro da sua igreja ficam intactos.' },
              { tipo: 'mantem', texto: 'Ninguém perde acesso ao sistema.' },
            ]}
            palavraConfirmacao={status.mae?.nome ?? 'SAIR'}
            textoConfirmar="Sair da família"
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
              : `Gere um código para suas ${congregacao.plural.toLowerCase()} entrarem, ou entre na família de outra igreja usando o código dela.`}
          </p>
        </div>

        <div className={styles.faixaAcoes}>
          {!ehMae && (
            <button className={styles.botaoSecundario} onClick={() => setMostrandoCampo((v) => !v)}>
              <Link2 size={16} aria-hidden="true" />
              Vincular com código
            </button>
          )}
          <button className={styles.botaoPrimario} onClick={aoGerarCodigo} disabled={gerarCodigo.isPending}>
            <KeyRound size={16} aria-hidden="true" />
            {status.codigoVinculo ? 'Gerar novo código' : 'Gerar código de vínculo'}
          </button>
        </div>
      </section>

      {/* Entrar numa família só faz sentido para quem ainda não tem congregações:
          a regra dos 2 níveis impede que uma mãe vire filha. */}
      {!ehMae && mostrandoCampo && (
        <section className={styles.faixa}>
          <div className={styles.faixaTexto}>
            <h2 className={styles.faixaTitulo}>Entrar numa família</h2>
            <p className={styles.faixaSubtitulo}>
              Digite o código que a igreja sede gerou. Ao entrar, os números da sua igreja
              passam a aparecer nos relatórios dela.
            </p>

            <form className={styles.formCodigo} onSubmit={aoEntrar}>
              <input
                className={styles.inputCodigo}
                value={codigoDigitado}
                onChange={(e) => setCodigoDigitado(e.target.value)}
                placeholder="XK4P-2M7Q"
                maxLength={9}
                aria-label="Código de vínculo"
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

      {status.codigoVinculo && (
        <section className={styles.blocoCodigo}>
          <div>
            <span className={styles.codigoRotulo}>Código de vínculo</span>
            <p className={styles.codigoValor}>{status.codigoVinculo}</p>
            <p className={styles.codigoAjuda}>
              Gerado em {formatarData(status.codigoGeradoEm)}. Pode ser usado por várias{' '}
              {congregacao.plural.toLowerCase()}. Gerar um novo invalida este.
            </p>
          </div>

          <div className={styles.codigoAcoes}>
            <button className={styles.botaoSecundario} onClick={copiarCodigo}>
              <Copy size={16} aria-hidden="true" />
              Copiar
            </button>
            <button className={styles.botaoSecundario} onClick={aoGerarCodigo} disabled={gerarCodigo.isPending}>
              <RefreshCw size={16} aria-hidden="true" />
              Gerar novo
            </button>
          </div>
        </section>
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
            Sua igreja ainda não faz parte de nenhuma família.
          </p>
        </section>
      )}

      {congregacaoParaRemover && (
        <ModalConfirmacaoCritica
          titulo={`Desvincular ${congregacao.singular.toLowerCase()}`}
          mensagem={
            <>
              <strong>{congregacaoParaRemover.nome}</strong> deixará de fazer parte da sua
              família de igrejas.
            </>
          }
          consequencias={[
            { tipo: 'perde', texto: 'Os números dela somem dos seus relatórios consolidados.' },
            { tipo: 'perde', texto: 'Você deixa de ver os relatórios financeiros dela.' },
            { tipo: 'perde', texto: 'Para religar, ela precisará digitar o código de novo.' },
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
