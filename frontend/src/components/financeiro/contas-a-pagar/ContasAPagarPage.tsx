'use client'

import { useState } from 'react'
import { Plus } from 'lucide-react'
import { TabBar } from '@/components/common/TabBar/TabBar'
import { Button } from '@/components/common/button/Button'
import { ModalContaForm } from './ModalContaForm'
import { ModalPagamento } from './ModalPagamento'
import { ModalEscopo } from './ModalEscopo'
import { ContasAPagarResumo } from './ContasAPagarResumo'
import { ContasAPagarFiltros } from './ContasAPagarFiltros'
import { ContasAPagarTabela } from './ContasAPagarTabela'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useContasAPagar, useRegistrarPagamento, useCriarConta, useEditarConta, useExcluirConta, useResumo } from '@/hooks/financeiro/contas-a-pagar/useContasAPagar'
import { notificar } from '@/components/common/Notificacao/notificar'
import type {
  ContaResponse,
  ContaRequest,
  RegistrarPagamentoRequest,
  ListarContasFiltros,
  EscopoEdicaoSerie,
} from '@/types/contas-a-pagar'
import styles from './ContasAPagarPage.module.css'

type TabId = 'pendentes' | 'todas'

export function ContasAPagarPage() {
  const [tab, setTab] = useState<TabId>('pendentes')
  const [page, setPage] = useState(0)
  const [filtros, setFiltros] = useState<ListarContasFiltros>({})
  const [competencia, setCompetencia] = useState<string | undefined>(undefined)
  const [destaqueId, setDestaqueId] = useState<string | null>(null)

  // Modais
  const [modalFormAberto, setModalFormAberto] = useState(false)
  const [modalPagAberto, setModalPagAberto] = useState(false)
  const [modalEscopoAberto, setModalEscopoAberto] = useState(false)
  const [confirmarAberto, setConfirmarAberto] = useState(false)

  // Dados dos modais
  const [contaSelecionada, setContaSelecionada] = useState<ContaResponse | null>(null)
  const [acaoEscopo, setAcaoEscopo] = useState<'editar' | 'excluir'>('editar')
  const [escopoSelecionado, setEscopoSelecionado] = useState<EscopoEdicaoSerie>('ESTA')

  // Build filters based on active tab
  const activeFiltros: Partial<ListarContasFiltros> = {
    ...filtros,
    ...(tab === 'pendentes' && !filtros.status ? { status: 'EM_ABERTO' } : {}),
  }

  // Queries
  const { data: contas, isLoading: carregandoContas } = useContasAPagar({
    competencia: competencia ?? '',
    page,
    size: 20,
    filtros: activeFiltros,
  })

  const { data: resumo } = useResumo(competencia ?? '')

  // Mutations
  const criarConta = useCriarConta()
  const editarConta = useEditarConta()
  const excluirConta = useExcluirConta()
  const registrarPagamento = useRegistrarPagamento()

  // Helpers
  function abrirEditar(conta: ContaResponse) {
    setContaSelecionada(conta)
    setModalFormAberto(true)
  }

  function abrirExcluir(conta: ContaResponse) {
    setContaSelecionada(conta)
    setAcaoEscopo('excluir')
    if (conta.recorrencia) {
      setModalEscopoAberto(true)
    } else {
      setConfirmarAberto(true)
    }
  }

  function abrirPagar(conta: ContaResponse) {
    setContaSelecionada(conta)
    setModalPagAberto(true)
  }

  function abrirBaixarRestante(conta: ContaResponse) {
    setContaSelecionada(conta)
    setModalPagAberto(true)
  }

  async function aoCriarConta(req: ContaRequest) {
    await criarConta.mutateAsync(req)
    setModalFormAberto(false)
    if (req.recorrencia) {
      setDestaqueId(null) // série não é highlightada
    }
  }

  async function aoEditarConta(req: ContaRequest) {
    if (!contaSelecionada) return
    await editarConta.mutateAsync({ id: contaSelecionada.id, req, escopo: escopoSelecionado })
    setModalFormAberto(false)
    setModalEscopoAberto(false)
    setContaSelecionada(null)
    notificar.sucesso('Conta atualizada')
  }

  async function aoExcluirConta() {
    if (!contaSelecionada) return
    await excluirConta.mutateAsync({ id: contaSelecionada.id, escopo: escopoSelecionado })
    setConfirmarAberto(false)
    setModalEscopoAberto(false)
    setContaSelecionada(null)
    notificar.sucesso('Conta excluída')
  }

  async function aoRegistrarPagamento(req: RegistrarPagamentoRequest) {
    if (!contaSelecionada) return
    await registrarPagamento.mutateAsync({ contaId: contaSelecionada.id, req })
    setModalPagAberto(false)
    // highlight the newly paid item
    setDestaqueId(contaSelecionada.id)
    setTimeout(() => setDestaqueId(null), 3000)
  }

  const tabs: { id: TabId; rotulo: string }[] = [
    { id: 'pendentes', rotulo: 'Pendentes' },
    { id: 'todas', rotulo: 'Todas' },
  ]

  const temFiltroAtivo = !!(filtros.status || filtros.beneficiario || filtros.vencimentoAte)

  return (
    <div className={styles.pagina}>
      {/* Header */}
      <div className={styles.header}>
        <h1 className={styles.titulo}>Contas a Pagar</h1>
        <Button
          variant="primary"
          size="md"
          onClick={() => { setContaSelecionada(null); setModalFormAberto(true) }}
        >
          <Plus size={16} />
          Nova conta
        </Button>
      </div>

      {/* Resumo */}
      <ContasAPagarResumo
        data={resumo}
        carregando={carregandoContas && !contas}
      />

      {/* Filtros */}
      <ContasAPagarFiltros
        filtros={filtros}
        onChange={(novo) => {
          setFiltros((prev) => ({ ...prev, ...novo }))
          setPage(0)
        }}
        competencia={competencia}
        onCompetenciaChange={(v) => { setCompetencia(v || undefined); setPage(0) }}
        temFiltroAtivo={temFiltroAtivo}
        onLimpar={() => { setFiltros({}); setPage(0) }}
      />

      {/* Tabs */}
      <TabBar
        tabs={tabs}
        activeId={tab}
        onChange={(id) => { setTab(id); setPage(0) }}
      />

      {/* Tabela */}
      <ContasAPagarTabela
        data={contas}
        carregando={carregandoContas}
        destaqueId={destaqueId}
        onEditar={abrirEditar}
        onExcluir={abrirExcluir}
        onPagar={abrirPagar}
        onBaixarRestante={abrirBaixarRestante}
        onPageChange={setPage}
      />

      {/* Modal: formulário de conta */}
      <ModalContaForm
        aberto={modalFormAberto}
        conta={contaSelecionada}
        onClose={() => { setModalFormAberto(false); setContaSelecionada(null) }}
        onSubmit={contaSelecionada ? aoEditarConta : aoCriarConta}
      />

      {/* Modal: pagamento */}
      <ModalPagamento
        aberto={modalPagAberto}
        conta={contaSelecionada}
        onClose={() => { setModalPagAberto(false); setContaSelecionada(null) }}
        onSubmit={aoRegistrarPagamento}
      />

      {/* Modal: escopo (série) */}
      <ModalEscopo
        aberto={modalEscopoAberto}
        titulo={acaoEscopo === 'excluir' ? 'Excluir conta recorrente?' : 'Editar conta recorrente?'}
        acao={acaoEscopo}
        onClose={() => { setModalEscopoAberto(false); setContaSelecionada(null) }}
        onConfirmar={(escopo) => {
          setEscopoSelecionado(escopo)
          if (acaoEscopo === 'excluir') {
            aoExcluirConta()
          } else {
            setModalEscopoAberto(false)
            setModalFormAberto(true)
          }
        }}
        carregando={excluirConta.isPending || editarConta.isPending}
        temPagamentos={!!contaSelecionada?.pagamentos?.length}
      />

      {/* Confirmar exclusão */}
      {confirmarAberto && (
        <ModalConfirmacao
          titulo="Excluir conta?"
          mensagem={`Tem certeza que deseja excluir "${contaSelecionada?.descricao || 'esta conta'}"? Esta ação não pode ser desfeita.`}
          isLoading={excluirConta.isPending}
          onConfirmar={aoExcluirConta}
          onClose={() => { setConfirmarAberto(false); setContaSelecionada(null) }}
          perigo
        />
      )}
    </div>
  )
}
