'use client'

import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { formatarData } from '@/lib/formats/pessoaFormat'
import { clsx } from 'clsx'
import {
  CheckCircle,
  Clock,
  RefreshCw,
  Paperclip,
  Pencil,
  Trash2,
  Banknote,
} from 'lucide-react'
import { Colapsavel } from '@/components/common/Transicao/Colapsavel'
import type { ContaResponse, StatusConta } from '@/types/contas-a-pagar'
import styles from './ContasAPagarItem.module.css'

interface Props {
  conta: ContaResponse
  destaqueId?: string | null
  onEditar: (conta: ContaResponse) => void
  onExcluir: (conta: ContaResponse) => void
  onPagar: (conta: ContaResponse) => void
  onBaixarRestante: (conta: ContaResponse) => void
  expandedId: string | null
  onToggle: (id: string) => void
}

const STATUS_CONFIG: Record<StatusConta, { rotulo: string; cor: string; Icone: React.ComponentType<{ size?: number }> }> = {
  EM_ABERTO: { rotulo: 'Em aberto', cor: 'amarelo', Icone: Clock },
  PAGA:      { rotulo: 'Paga',      cor: 'verde',    Icone: CheckCircle },
  PARCIAL:   { rotulo: 'Parcial',   cor: 'azul',     Icone: RefreshCw },
}

export function ContasAPagarItem({
  conta,
  destaqueId,
  onEditar,
  onExcluir,
  onPagar,
  onBaixarRestante,
  expandedId,
  onToggle,
}: Props) {
  const config = STATUS_CONFIG[conta.status]
  const StatusIcon = config.Icone
  const ehDestaque = conta.id === destaqueId

  const vencidoBadge = conta.diasParaVencimento !== null && conta.diasParaVencimento < 0 && conta.status !== 'PAGA'

  return (
    <div
      className={clsx(styles.item, styles[`status_${conta.status.toLowerCase()}`], ehDestaque && styles.novo)}
      id={`conta-${conta.id}`}
    >
      {/* Desktop: linha única */}
      <div className={styles.linha} onClick={() => onToggle(conta.id)}>
        {/* Checkbox de seleção visual (futuro multi-select) */}
        <div className={styles.statusBadge} aria-label={`Status: ${config.rotulo}`}>
          <StatusIcon size={14} />
        </div>

        <div className={styles.descricao}>
          <span className={styles.descricaoTitulo}>{conta.descricao || '—'}</span>
          <span className={styles.beneficiario}>
            {conta.beneficiario.pessoaRemovida
              ? 'Pessoa removida do sistema'
              : conta.beneficiario.pessoaNome ?? conta.beneficiario.texto ?? '—'}
          </span>
        </div>

        <div className={styles.categoriaBadge}>
          {conta.categoriaCor && (
            <span className={styles.categoriaCor} style={{ background: conta.categoriaCor }} aria-hidden="true" />
          )}
          <span className={styles.categoriaNome}>{conta.categoriaNome}</span>
        </div>

        <div className={styles.valorColuna}>
          <span className={styles.valor}>{formatarMoeda(conta.valor)}</span>
          {conta.status === 'PARCIAL' && (
            <span className={styles.valorPago}>
              {formatarMoeda(conta.valorPago)} pago
            </span>
          )}
        </div>

        <div className={styles.vencimento}>
          <span className={clsx(styles.data, vencidoBadge && styles.vencido)}>
            {formatarData(conta.vencimento)}
          </span>
          {conta.diasParaVencimento !== null && (
            <span className={clsx(styles.dias, vencidoBadge && styles.vencido)}>
              {conta.diasParaVencimento < 0
                ? `${Math.abs(conta.diasParaVencimento)}d em atraso`
                : conta.diasParaVencimento === 0
                ? 'Vence hoje'
                : `${conta.diasParaVencimento}d`}
            </span>
          )}
        </div>

        <div className={styles.status}>
          <span className={`${styles.statusPill} ${styles[config.cor]}`}>
            {config.rotulo}
          </span>
          {conta.recorrencia && (
            <RefreshCw size={12} className={styles.iconeRecorrencia} aria-label="Conta recorrente" />
          )}
        </div>

        <div className={styles.acoes} onClick={(e) => e.stopPropagation()}>
          {conta.anexoId && (
            <Paperclip size={14} className={styles.iconeAnexo} aria-label="Tem anexo" />
          )}
          {conta.podeReceberPagamento && (
            <button
              className={styles.btnAcao}
              onClick={(e) => { e.stopPropagation(); onPagar(conta) }}
              aria-label="Registrar pagamento"
              title="Registrar pagamento"
            >
              <Banknote size={15} />
            </button>
          )}
          {conta.status === 'PARCIAL' && (
            <button
              className={styles.btnAcao}
              onClick={(e) => { e.stopPropagation(); onBaixarRestante(conta) }}
              aria-label="Baixar restante"
              title="Baixar restante"
            >
              <CheckCircle size={15} />
            </button>
          )}
          <button
            className={styles.btnAcao}
            onClick={(e) => { e.stopPropagation(); onEditar(conta) }}
            aria-label="Editar conta"
            title="Editar"
          >
            <Pencil size={15} />
          </button>
          <button
            className={`${styles.btnAcao} ${styles.btnExcluir}`}
            onClick={(e) => { e.stopPropagation(); onExcluir(conta) }}
            aria-label="Excluir conta"
            title="Excluir"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </div>

      {/* Mobile: card expandido */}
      <div className={styles.cardMobile}>
        <div className={styles.cardHeader} onClick={() => onToggle(conta.id)}>
          <div className={styles.cardTop}>
            <span className={`${styles.statusPill} ${styles[config.cor]}`}>
              {config.rotulo}
            </span>
            {conta.recorrencia && (
              <RefreshCw size={12} className={styles.iconeRecorrencia} aria-label="Recorrente" />
            )}
          </div>
          <span className={styles.cardDesc}>{conta.descricao || '—'}</span>
          <span className={styles.cardValor}>{formatarMoeda(conta.valor)}</span>
        </div>

        <Colapsavel aberto={expandedId === conta.id}>
          <div className={styles.cardDetails}>
            <div className={styles.cardRow}>
              <span className={styles.cardLabel}>Beneficiário</span>
              <span className={styles.cardValue}>
                {conta.beneficiario.pessoaRemovida
                  ? 'Pessoa removida do sistema'
                  : conta.beneficiario.pessoaNome ?? conta.beneficiario.texto ?? '—'}
              </span>
            </div>
            <div className={styles.cardRow}>
              <span className={styles.cardLabel}>Vencimento</span>
              <span className={clsx(styles.cardValue, vencidoBadge && styles.vencido)}>
                {formatarData(conta.vencimento)}
                {conta.diasParaVencimento !== null && conta.diasParaVencimento > 0 && (
                  ` · ${conta.diasParaVencimento}d`
                )}
                {conta.diasParaVencimento !== null && conta.diasParaVencimento < 0 && ` · ${Math.abs(conta.diasParaVencimento)}d em atraso`}
              </span>
            </div>
            <div className={styles.cardRow}>
              <span className={styles.cardLabel}>Categoria</span>
              <span className={styles.cardValue}>
                {conta.categoriaCor && (
                  <span
                    className={styles.categoriaCor}
                    style={{ background: conta.categoriaCor }}
                    aria-hidden="true"
                  />
                )}
                {conta.categoriaNome}
              </span>
            </div>
            <div className={styles.cardRow}>
              <span className={styles.cardLabel}>Criada em</span>
              <span className={styles.cardValue}>{formatarData(conta.criadoEm)}</span>
            </div>
            {conta.pagamentos && conta.pagamentos.length > 0 && (
              <div className={styles.pagamentosLista}>
                <span className={styles.cardLabel}>Pagamentos</span>
                {conta.pagamentos.map((p) => (
                  <div key={p.id} className={styles.pagamentoItem}>
                    <span>{formatarMoeda(p.valorPago)}</span>
                    <span>{formatarData(p.data)}</span>
                    {p.estornadoPorTexto && (
                      <span className={styles.estornadoBadge}>Estornado</span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </Colapsavel>

        <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
          {conta.podeReceberPagamento && (
            <button
              className={`${styles.btnCard} ${styles.btnPagar}`}
              onClick={() => onPagar(conta)}
            >
              <Banknote size={14} /> Pagamento
            </button>
          )}
          <button
            className={`${styles.btnCard} ${styles.btnEditar}`}
            onClick={() => onEditar(conta)}
          >
            <Pencil size={14} /> Editar
          </button>
          <button
            className={`${styles.btnCard} ${styles.btnExcluir}`}
            onClick={() => onExcluir(conta)}
          >
            <Trash2 size={14} /> Excluir
          </button>
        </div>
      </div>
    </div>
  )
}
