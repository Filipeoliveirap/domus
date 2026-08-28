'use client'

import { useEffect } from 'react'
import { X, AlertTriangle } from 'lucide-react'
import { Button } from '@/components/common/button/Button'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { ImpactoMudancaPrecoResponse } from '@/types/evento.type'
import styles from './ModalImpactoRestricao.module.css'

interface Props {
  impacto: ImpactoMudancaPrecoResponse
  isLoading: boolean
  onConfirmar: () => void
  onClose: () => void
}

/**
 * Aviso antes de confirmar uma mudança de preço com gente já inscrita, nas duas
 * direções — mexe com dinheiro de verdade (estorno ou cobrança real no Mercado Pago),
 * então mostra os números antes do admin apertar "Salvar" de vez. Mesma linguagem visual
 * de ModalImpactoRestricao (reaproveita o CSS module dele).
 */
export function ModalImpactoMudancaPreco({ impacto, isLoading, onConfirmar, onClose }: Props) {
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, isLoading])

  const {
    tipo, pessoasComPagamentoPago, valorTotalAEstornar, pessoasAguardandoPagamento,
    pessoasSeraoCobradas, valorTotalACobrar,
  } = impacto
  const vaiVirarPago = tipo === 'GRATUITO_PARA_PAGO'
  const vaiVirarGratuito = tipo === 'PAGO_PARA_GRATUITO'
  const valorAumentou = tipo === 'VALOR_AUMENTOU'
  const valorDiminuiu = tipo === 'VALOR_DIMINUIU'
  // Achado ao vivo (2026-08-27): com reajustes anteriores diferentes por pessoa, as duas
  // direções podem coexistir — nesse caso mostra os dois blocos juntos, em vez de só um
  // escondendo o outro.
  const valorMisto = tipo === 'VALOR_MISTO'
  // Preço da cobrança de cada pessoa (todo mundo paga o mesmo valor/diferença) — mostrar
  // o total dava a entender que R$X era o valor da inscrição de cada uma, não a soma.
  const valorPorPessoa = pessoasSeraoCobradas > 0 ? valorTotalACobrar / pessoasSeraoCobradas : 0
  const valorEstornoPorPessoa = pessoasComPagamentoPago > 0 ? valorTotalAEstornar / pessoasComPagamentoPago : 0
  const mostrarBlocoCobranca = (vaiVirarPago || valorAumentou || valorMisto) && pessoasSeraoCobradas > 0
  const mostrarBlocoEstorno = (vaiVirarGratuito || valorDiminuiu || valorMisto) && pessoasComPagamentoPago > 0
  const mostrarBlocoAguardando = (valorAumentou || valorDiminuiu || valorMisto) && pessoasAguardandoPagamento > 0
  const mostrarBlocoConfirmadoDireto = vaiVirarGratuito && pessoasAguardandoPagamento > 0

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-impacto-mudanca-preco"
      >
        <div className={styles.header}>
          <div className={styles.headerTexto}>
            <span className={styles.iconBox}>
              <AlertTriangle size={20} aria-hidden="true" />
            </span>
            <div>
              <h2 className={styles.titulo} id="titulo-impacto-mudanca-preco">
                {vaiVirarPago && 'Este evento vai virar pago'}
                {vaiVirarGratuito && 'Este evento vai virar gratuito'}
                {valorAumentou && 'O valor da inscrição vai aumentar'}
                {valorDiminuiu && 'O valor da inscrição vai diminuir'}
                {valorMisto && 'O valor da inscrição vai mudar'}
              </h2>
              <p className={styles.subtitulo}>
                {mostrarBlocoCobranca && (
                  <>
                    {pessoasSeraoCobradas === 1
                      ? `1 pessoa já confirmada vai receber a cobrança ${vaiVirarPago ? 'de' : 'da diferença de'}`
                      : `${pessoasSeraoCobradas} pessoas já confirmadas vão receber a cobrança ${vaiVirarPago ? 'de' : 'da diferença de'}`}
                    {' '}<strong>{formatarMoeda(valorPorPessoa)}</strong>. A inscrição de cada uma
                    fica pendente até pagar; ninguém perde a vaga.
                    {(mostrarBlocoEstorno || mostrarBlocoAguardando) && ' '}
                  </>
                )}
                {mostrarBlocoEstorno && (
                  <>
                    {pessoasComPagamentoPago === 1 ? '1 pessoa já pagou' : `${pessoasComPagamentoPago} pessoas já pagaram`}
                    {' — '}<strong>{formatarMoeda(vaiVirarGratuito ? valorTotalAEstornar : valorEstornoPorPessoa)}</strong>
                    {' '}{pessoasComPagamentoPago === 1 || vaiVirarGratuito ? 'será estornado' : 'serão estornados de cada uma'}.
                    {' '}As inscrições permanecerão.
                    {mostrarBlocoAguardando && ' '}
                  </>
                )}
                {mostrarBlocoAguardando && (
                  <>
                    {pessoasAguardandoPagamento === 1 ? '1 pessoa' : `${pessoasAguardandoPagamento} pessoas`}
                    {' '}ainda aguardando pagamento {pessoasAguardandoPagamento === 1 ? 'terá' : 'terão'} o valor a pagar atualizado, sem mudança de status.
                  </>
                )}
                {mostrarBlocoConfirmadoDireto && (
                  <>
                    {pessoasAguardandoPagamento === 1 ? '1 pessoa está' : `${pessoasAguardandoPagamento} pessoas estão`}
                    {' '}aguardando pagamento — a inscrição será confirmada direto, sem cobrar nada.
                  </>
                )}
              </p>
            </div>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={isLoading}
          >
            <X size={20} />
          </button>
        </div>

        <div className={styles.footer}>
          <Button type="button" variant="secondary" size="md" isLoading={false} disabled={isLoading} onClick={onClose}>
            Cancelar
          </Button>
          <Button type="button" variant="primary" size="md" isLoading={isLoading} onClick={onConfirmar}>
            Confirmar mudança
          </Button>
        </div>
      </div>
    </div>
  )
}
