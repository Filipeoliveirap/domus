'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import axios from 'axios'
import { Copy, Check } from 'lucide-react'
import { initMercadoPago, Payment } from '@mercadopago/sdk-react'
import { notificar } from '@/components/common/Notificacao/notificar'
import { cobrancaService } from '@/services/cobranca.service'
import type { ApiError } from '@/types/api.types'
import styles from './PaymentBrickCheckout.module.css'

interface Props {
  cobrancaId: string
  valor: number
  /** E-mail do pagador — o Brick aceita o campo vazio e deixa a pessoa preencher, mas
   *  pré-preencher com o e-mail já conhecido (titular logado) evita retrabalho. */
  emailPagador?: string
  onPagamentoCriado: (mpPaymentId: string) => void
  /** Erro em que continuar tentando NO MESMO formulário não resolve — a cobrança em si
   *  não pode mais ser paga (expirou, vagas esgotadas, já foi paga/cancelada). A mensagem
   *  já vem pronta do backend (`ApiError.message`), em português. O pai troca de tela em
   *  vez de deixar a pessoa martelar "Pagar" contra uma cobrança morta. */
  onCobrancaIndisponivel: (mensagem: string) => void
}

/** Códigos de `BusinessException` de `CobrancaController.pagar` em que a cobrança em si
 *  não tem mais como ser paga — tentar de novo no mesmo formulário nunca resolve, é
 *  preciso voltar e se inscrever de novo (o que gera uma cobrança nova). */
const ERROS_COBRANCA_MORTA = new Set([
  'COBRANCA_NAO_PENDENTE',
  'COBRANCA_JA_EM_PROCESSAMENTO',
  'COBRANCA_EXPIRADA',
  'VAGAS_ESGOTADAS',
])

// Guarda a CHAVE usada, não só um booleano — se `NEXT_PUBLIC_MERCADOPAGO_PUBLIC_KEY`
// mudar (ex.: troca de teste pra produção em dev), o SDK precisa reinicializar com a
// chave nova, senão fica "preso" na primeira chave usada nesta instância do módulo
// (o Brick tokeniza o cartão contra o ambiente errado, e o backend recusa o pagamento
// com "Card Token not found" ao cobrar com credenciais de um ambiente diferente).
let chaveInicializada: string | null = null

/**
 * Payment Brick embutido (PIX + cartão na mesma tela) — decisão do brainstorm de não
 * redirecionar pro Mercado Pago ("fica amador"). O tokenizador de cartão roda no
 * navegador via SDK do Mercado Pago; o dado de cartão nunca passa pelo backend do Domus,
 * só o token já gerado (`formData.token`).
 *
 * <p>Campos usados de `formData` (confirmados no `.d.ts` do pacote instalado,
 * `node_modules/@mercadopago/sdk-react/esm/bricks/cardPayment/type.d.ts`):
 * `token`, `payment_method_id`, `installments`, `payer.email` — repassados quase iguais
 * pro backend em {@code POST /cobrancas/{id}/pagar} (ver CobrancaController, Task 14).
 * PIX não gera `token`/`installments` (o Brick manda `undefined`) — o backend aceita os
 * dois nulos nesse caso.</p>
 */
export function PaymentBrickCheckout({ cobrancaId, valor, emailPagador, onPagamentoCriado, onCobrancaIndisponivel }: Props) {
  const publicKeyRef = useRef(process.env.NEXT_PUBLIC_MERCADOPAGO_PUBLIC_KEY ?? '')
  const [enviando, setEnviando] = useState(false)
  // Só é preenchido quando o meio escolhido é Pix — nesse caso o pagamento nasce `pending`
  // (diferente de cartão, que resolve aprovado/recusado na hora) e a gente precisa mostrar
  // o QR/código pra pessoa pagar, em vez de fechar o checkout como se já tivesse terminado.
  const [pix, setPix] = useState<{ mpPaymentId: string; qrCode: string; qrCodeBase64: string } | null>(null)
  const [copiado, setCopiado] = useState(false)
  // `onPagamentoCriado` chega como arrow function inline do componente pai (recriada a cada
  // render dele) — guardar só o valor mais recente numa ref evita que ela apareça nas
  // dependências de `aoEnviar` abaixo, que é o que mantém o `useEffect` do SDK estável (ver
  // comentário logo antes do `useCallback`).
  const onPagamentoCriadoRef = useRef(onPagamentoCriado)
  onPagamentoCriadoRef.current = onPagamentoCriado
  const onCobrancaIndisponivelRef = useRef(onCobrancaIndisponivel)
  onCobrancaIndisponivelRef.current = onCobrancaIndisponivel

  useEffect(() => {
    if (chaveInicializada !== publicKeyRef.current && publicKeyRef.current) {
      initMercadoPago(publicKeyRef.current, { locale: 'pt-BR' })
      chaveInicializada = publicKeyRef.current
    }
  }, [])

  // Enquanto o QR do Pix está na tela, o único jeito de saber que a pessoa pagou é
  // perguntar de novo — o Mercado Pago confirma via webhook (assíncrono, Task 10), não tem
  // callback nenhum no navegador. Poll simples a cada 4s; para sozinho ao desmontar ou
  // assim que a cobrança sair de PENDENTE.
  useEffect(() => {
    if (!pix) return
    const intervalo = setInterval(async () => {
      try {
        const { status } = await cobrancaService.status(cobrancaId)
        if (status === 'PAGO') {
          clearInterval(intervalo)
          onPagamentoCriadoRef.current(pix.mpPaymentId)
        } else if (status !== 'PENDENTE') {
          // Expirado/cancelado — mesma lógica do cartão: não adianta voltar pro formulário
          // e tentar de novo, a cobrança em si não existe mais pra pagar.
          clearInterval(intervalo)
          onCobrancaIndisponivelRef.current(
            status === 'EXPIRADO'
              ? 'O prazo para pagar esta cobrança expirou.'
              : 'Esta cobrança foi cancelada.'
          )
        }
      } catch {
        // Falha de rede pontual no poll não é motivo pra desistir — tenta de novo no próximo tick.
      }
    }, 4000)
    return () => clearInterval(intervalo)
  }, [pix, cobrancaId])

  // O SDK do Mercado Pago recria o Brick inteiro (empilhando um novo formulário no DOM em
  // vez de reaproveitar) sempre que `initialization`/`customization` chegam com uma
  // referência de objeto nova — o que acontecia a cada re-render deste componente (refetch
  // de useMinhaInscricao, notificação, etc.), já que eram objetos literais inline. Memoizar
  // pelas dependências reais evita o Brick duplicado.
  // `payer: { email: undefined }` (sem emailPagador) faz o SDK tentar resolver o "tipo de
  // pessoa" do pagador com um e-mail vazio e quebrar internamente — o form de cartão nunca
  // sai do esqueleto de carregamento e o console mostra "Cannot read properties of
  // undefined (reading 'message')" dentro do próprio script do Mercado Pago. Omitir
  // `payer` por completo quando não há e-mail conhecido deixa o Brick pedir o e-mail à
  // própria pessoa, do jeito documentado.
  const initialization = useMemo(
    () => ({ amount: valor, ...(emailPagador ? { payer: { email: emailPagador } } : {}) }),
    [valor, emailPagador]
  )
  const customization = useMemo(() => ({ paymentMethods: { bankTransfer: 'all' as const, creditCard: 'all' as const } }), [])

  // O componente `Payment` do SDK reagenda a criação do Brick (`useEffect` com
  // `onSubmit`/`onError` nas dependências) toda vez que essas funções chegam com uma
  // referência nova — o que acontecia aqui porque eram arrow functions inline, recriadas
  // a cada render deste componente. Cada reagendamento cria uma instância nova do Brick
  // sem garantia de que a anterior foi desmontada a tempo, o que explica os formulários
  // duplicados vistos no DOM.
  const aoEnviar = useCallback(
    async ({ formData }: { formData: { token?: string; payment_method_id: string; installments?: number; issuer_id?: string; payer?: { email?: string } } }) => {
      // Guard funcional (não lê `enviando` do closure) — mantém `enviando` fora das
      // dependências deste callback, senão o toggle true/false durante o envio recriaria
      // a função e reagendaria a criação do Brick outra vez.
      let jaEmVoo = false
      setEnviando((atual) => {
        jaEmVoo = atual
        return true
      })
      if (jaEmVoo) return
      try {
        const resposta = await cobrancaService.pagar(cobrancaId, {
          token: formData.token ?? null,
          paymentMethodId: formData.payment_method_id,
          installments: formData.installments ?? null,
          payerEmail: formData.payer?.email ?? emailPagador ?? '',
          issuerId: formData.issuer_id ?? null,
        })
        if (resposta.qrCode && resposta.qrCodeBase64) {
          // Pix: o pagamento nasce `pending` — mostra o QR em vez de fechar o checkout
          // como se já tivesse terminado (isso é o que o cartão faz, mas cartão resolve
          // aprovado/recusado na hora; Pix só confirma quando o webhook avisar).
          setPix({ mpPaymentId: resposta.mpPaymentId, qrCode: resposta.qrCode, qrCodeBase64: resposta.qrCodeBase64 })
        } else if (resposta.status === 'rejected') {
          // Achado testando o fluxo de ponta a ponta (2026-08-26): cartão recusado
          // devolve 200 com mpPaymentId igual a um aprovado — sem checar `status` aqui,
          // a pessoa via a mesma tela de "pagamento em processamento" pra um cartão que
          // o Mercado Pago já tinha recusado na hora.
          notificar.erro(
            'Pagamento recusado',
            'O cartão foi recusado pelo Mercado Pago. Confira os dados ou tente outro cartão.'
          )
          // O Brick só libera o formulário pra uma nova tentativa quando a Promise de
          // onSubmit REJEITA — resolver normalmente aqui (como fazíamos antes) faz o SDK
          // entender "terminou com sucesso" e travar o botão até um reload da página
          // (achado testando o fluxo de ponta a ponta, 2026-08-26).
          throw new Error('Pagamento recusado')
        } else {
          onPagamentoCriadoRef.current(resposta.mpPaymentId)
        }
      } catch (erro) {
        // O guard de clique duplo (jaEmVoo) usa `return` simples, sem erro — só chega aqui
        // quando algo realmente falhou (rede, API, ou o `throw` acima pro Brick se resetar).
        const jaTratado = erro instanceof Error && erro.message === 'Pagamento recusado'
        if (!jaTratado) {
          const codigo = axios.isAxiosError<ApiError>(erro) ? erro.response?.data?.error : undefined
          if (codigo && ERROS_COBRANCA_MORTA.has(codigo)) {
            // Achado testando o fluxo de ponta a ponta (2026-08-26): cobrança expirada,
            // vagas esgotadas, já paga/cancelada — nenhum desses se resolve tentando de
            // novo no mesmo formulário (a cobrança em si não existe mais pra pagar). Sem
            // isto, a pessoa via só um toast genérico "tente novamente" e martelava o
            // botão contra uma cobrança morta pra sempre.
            onCobrancaIndisponivelRef.current(
              axios.isAxiosError<ApiError>(erro) ? erro.response!.data.message : 'Esta cobrança não está mais disponível.'
            )
          } else {
            const mensagem = axios.isAxiosError<ApiError>(erro) ? erro.response?.data?.message : undefined
            notificar.erro(
              'Não foi possível processar o pagamento',
              mensagem ?? 'Confira os dados e tente novamente. Se o problema continuar, tente outro meio de pagamento.'
            )
          }
        }
        throw erro
      } finally {
        setEnviando(false)
      }
    },
    [cobrancaId, emailPagador]
  )

  // O SDK chama onError também para situações não-fatais (ex.: uma revalidação interna de
  // campo) — nesses casos o objeto chega vazio ({}), sem `message`/`cause`/nada acionável.
  // Mostrar o toast bloqueante nesses casos é falso alarme: o Brick continua funcionando por
  // baixo, mas a pessoa vê "erro no checkout" e desiste ou tenta de novo sem necessidade.
  const aoErrar = useCallback((erro: unknown) => {
    const temConteudo = erro !== null && typeof erro === 'object' && Object.keys(erro).length > 0
    if (!temConteudo) return
    console.error('Erro no Payment Brick', erro)
    notificar.erro('Erro no checkout', 'Não foi possível carregar o formulário de pagamento.')
  }, [])

  // Container com id próprio por cobrança — o id padrão do SDK ("paymentBrick_container")
  // é fixo e compartilhado entre qualquer instância do Brick na página; se duas chegarem a
  // coexistir (mesmo que por um instante, entre desmontar e montar de novo), colidem no
  // mesmo elemento.
  const idContainer = `paymentBrick_${cobrancaId}`

  function copiarCodigoPix() {
    navigator.clipboard.writeText(pix!.qrCode)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  if (pix) {
    return (
      <div className={styles.wrapper}>
        <div className={styles.pix}>
          <p className={styles.pixInstrucao}>Escaneie o QR Code com o app do seu banco:</p>
          {/* eslint-disable-next-line @next/next/no-img-element -- imagem vem em base64 direto da API do Mercado Pago, não é um asset local pro <Image> otimizar */}
          <img
            src={`data:image/png;base64,${pix.qrCodeBase64}`}
            alt="QR Code para pagamento via Pix"
            className={styles.pixQrCode}
          />
          <p className={styles.pixInstrucao}>Ou copie o código Pix (copia e cola):</p>
          <button type="button" className={styles.pixCopiar} onClick={copiarCodigoPix}>
            {copiado ? <Check size={16} /> : <Copy size={16} />}
            {copiado ? 'Copiado!' : 'Copiar código Pix'}
          </button>
          <p className={styles.pixAguardando}>Aguardando confirmação do pagamento…</p>
        </div>
      </div>
    )
  }

  return (
    // Critical 5 (revisão final de branch): o Brick não expõe uma prop "disabled" — a
    // defesa real contra clique duplo é no backend (idempotência via mpPaymentId em
    // CobrancaController.pagar), mas aqui soma-se uma camada de UX: `pointerEvents: none`
    // bloqueia clique novo enquanto `enviando`, e o guard logo no início do onSubmit
    // recusa uma segunda submissão que já esteja "em voo" (o SDK poderia disparar de novo
    // antes do clique ser bloqueado visualmente).
    <div className={styles.wrapper} style={enviando ? { pointerEvents: 'none', opacity: 0.6 } : undefined}>
      <Payment
        id={idContainer}
        initialization={initialization}
        customization={customization}
        onSubmit={aoEnviar}
        onError={aoErrar}
      />
      {enviando && <p className={styles.processando}>Processando pagamento…</p>}
    </div>
  )
}
