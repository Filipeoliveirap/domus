import { test, expect } from '@playwright/test'

const MOCK_RESUMO = {
  venceHoje: '150.00',
  aVencerNoMes: '1250.00',
  atrasadas: '300.00',
  pagasNoMes: '450.00',
}

const MOCK_CONTA = {
  id: 'c1111111-1111-1111-1111-111111111111',
  categoriaId: 'cat-1',
  categoriaNome: 'Aluguel',
  categoriaCor: '#3b82f6',
  beneficiario: {
    pessoaId: null,
    pessoaNome: null,
    texto: 'Imobiliária Central',
    pessoaRemovida: false,
  },
  descricao: 'Aluguel do Templo - Setembro',
  valor: '1000.00',
  valorPago: '0.00',
  vencimento: '2026-09-30',
  diasParaVencimento: 5,
  atrasada: false,
  status: 'EM_ABERTO',
  observacoes: 'Nota fiscal anexada',
  anexoId: null,
  recorrencia: {
    tipo: 'MENSAL',
    parcelaAtual: 1,
    totalParcelas: 12,
  },
  podeReceberPagamento: true,
  divergeDaSerie: false,
  criadoPorNome: 'Tesoureiro João',
  criadoEm: '2026-09-01T10:00:00Z',
  atualizadoEm: '2026-09-01T10:00:00Z',
  pagamentos: [],
}

const MOCK_CONTA_PARCIAL = {
  id: 'c2222222-2222-2222-2222-222222222222',
  categoriaId: 'cat-2',
  categoriaNome: 'Energia Elétrica',
  categoriaCor: '#f59e0b',
  beneficiario: {
    pessoaId: null,
    pessoaNome: null,
    texto: 'Companhia de Luz',
    pessoaRemovida: false,
  },
  descricao: 'Conta de Luz Prédio Principal',
  valor: '500.00',
  valorPago: '200.00',
  vencimento: '2026-09-20',
  diasParaVencimento: -5,
  atrasada: true,
  status: 'PARCIAL',
  podeReceberPagamento: true,
  divergeDaSerie: false,
  criadoPorNome: 'Admin Maria',
  criadoEm: '2026-09-01T10:00:00Z',
  atualizadoEm: '2026-09-15T10:00:00Z',
  pagamentos: [
    {
      id: 'p1111111-1111-1111-1111-111111111111',
      valorPago: '200.00',
      data: '2026-09-15',
      forma: 'PIX',
      movimentacaoId: 'mov-1',
    },
  ],
}

test.describe('Contas a Pagar — E2E', () => {
  test.beforeEach(async ({ page }) => {
    // Intercepta rotas da API para fornecer respostas mockadas confiáveis nos testes E2E
    await page.route('**/contas-a-pagar/resumo*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_RESUMO),
      })
    })

    await page.route('**/contas-a-pagar?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [MOCK_CONTA, MOCK_CONTA_PARCIAL],
          pageNumber: 0,
          pageSize: 10,
          totalElements: 2,
          totalPages: 1,
          last: true,
        }),
      })
    })

    await page.route('**/categorias*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'cat-1', nome: 'Aluguel' },
          { id: 'cat-2', nome: 'Energia Elétrica' },
        ]),
      })
    })

    await page.route('**/anexos*', async (route) => {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'anexo-uuid-999',
          tipo: 'application/pdf',
          bytes: 1024,
          nomeOriginal: 'fatura_setembro.pdf',
          url: '/anexos/anexo-uuid-999',
        }),
      })
    })
  })

  test('carrega página de contas a pagar com cards de resumo e listagem', async ({ page }) => {
    await page.goto('/financeiro/contas-a-pagar')

    // Verifica título ou cabeçalho principal
    await expect(page.getByRole('heading', { name: /contas a pagar/i })).toBeVisible()

    // Verifica se os cards de resumo foram preenchidos
    await expect(page.getByText('R$ 150,00')).toBeVisible() // Vence hoje
    await expect(page.getByText('R$ 1.250,00')).toBeVisible() // A vencer
    await expect(page.getByText('R$ 300,00')).toBeVisible() // Atrasadas

    // Verifica se os itens da listagem aparecem na tela
    await expect(page.getByText('Aluguel do Templo - Setembro')).toBeVisible()
    await expect(page.getByText('Imobiliária Central')).toBeVisible()
    await expect(page.getByText('Conta de Luz Prédio Principal')).toBeVisible()
  })

  test('abre modal de cadastro de nova conta e simula envio com sucesso', async ({ page }) => {
    let postBody: Record<string, unknown> | null = null

    await page.route('**/contas-a-pagar', async (route) => {
      if (route.request().method() === 'POST') {
        postBody = route.request().postDataJSON()
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ ...MOCK_CONTA, id: 'nova-conta-id' }),
        })
      } else {
        await route.continue()
      }
    })

    await page.goto('/financeiro/contas-a-pagar')

    // Clica no botão de nova conta
    await page.getByRole('button', { name: /nova conta/i }).click()

    // Valida abertura do modal
    const modal = page.getByRole('dialog', { name: /nova conta a pagar/i })
    await expect(modal).toBeVisible()

    // Preenche os campos do formulário
    await page.getByLabel(/descrição/i).fill('Internet Fibra Óptica')
    await page.getByLabel(/beneficiário/i).fill('Provedor Telecom')
    await page.getByLabel(/valor/i).fill('150,00')
    await page.getByLabel(/vencimento/i).fill('2026-10-10')

    // Submete o formulário
    await page.getByRole('button', { name: /cadastrar/i }).click()

    // Verifica se o formulário enviou os dados corretos
    expect(postBody).not.toBeNull()
    expect(postBody).toMatchObject({
      descricao: 'Internet Fibra Óptica',
      valor: '150.00',
      vencimento: '2026-10-10',
    })
  })

  test('abre modal de pagamento e confirma liquidação parcial/total', async ({ page }) => {
    let pagamentoBody: Record<string, unknown> | null = null

    await page.route('**/contas-a-pagar/*/pagamentos', async (route) => {
      pagamentoBody = route.request().postDataJSON()
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ ...MOCK_CONTA, status: 'PAGA', valorPago: '1000.00' }),
      })
    })

    await page.goto('/financeiro/contas-a-pagar')

    // Clica no botão de registrar pagamento do primeiro item
    const btnPagar = page.getByRole('button', { name: /registrar pagamento/i }).first()
    await btnPagar.click()

    // Modal de pagamento deve abrir
    const modal = page.getByRole('dialog', { name: /registrar pagamento/i })
    await expect(modal).toBeVisible()

    // Confirma o pagamento
    await page.getByRole('button', { name: /confirmar pagamento/i }).click()

    // Valida payload enviado
    expect(pagamentoBody).not.toBeNull()
    expect(pagamentoBody).toMatchObject({
      valor: '1000.00',
      meioPagamento: 'PIX',
    })
  })

  test('aciona baixa por abatimento (dar baixa restante)', async ({ page }) => {
    let baixaAcionada = false

    await page.route('**/contas-a-pagar/*/dar-baixa-restante', async (route) => {
      baixaAcionada = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...MOCK_CONTA_PARCIAL, status: 'PAGA' }),
      })
    })

    await page.goto('/financeiro/contas-a-pagar')

    // Na conta parcial, clica em "Baixar restante"
    const btnBaixar = page.getByRole('button', { name: /baixar restante/i }).first()
    await btnBaixar.click()

    expect(baixaAcionada).toBe(true)
  })

  test.describe('Mobile Viewport (iPhone 14 / WebKit)', () => {
    test.use({ viewport: { width: 390, height: 844 } })

    test('exibe cards responsivos no mobile e expande detalhes ao clicar', async ({ page }) => {
      await page.goto('/financeiro/contas-a-pagar')

      // Verifica visibilidade da lista responsiva
      await expect(page.getByText('Aluguel do Templo - Setembro')).toBeVisible()

      // Clica para expandir os detalhes no card mobile
      await page.getByText('Aluguel do Templo - Setembro').click()

      // Detalhes como "Imobiliária Central" e datas devem ser visíveis
      await expect(page.getByText('Imobiliária Central')).toBeVisible()
    })
  })
})
