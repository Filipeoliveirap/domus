import { test, expect } from '@playwright/test';

test.describe('SaaS Subscription & Limit Enforcement E2E', () => {
  test('exibe a tabela de planos e valores corretamente em /planos', async ({ page }) => {
    await page.goto('/planos');

    await expect(page.getByText('Básico', { exact: true })).toBeVisible();
    await expect(page.getByText('Pro', { exact: true })).toBeVisible();
    await expect(page.getByText('Pro+', { exact: true })).toBeVisible();
    await expect(page.getByText('Enterprise', { exact: true })).toBeVisible();

    await expect(page.getByText(/79,00/)).toBeVisible();
    await expect(page.getByText(/179,00/)).toBeVisible();
    await expect(page.getByText(/299,00/)).toBeVisible();
    await expect(page.getByText(/499,00/)).toBeVisible();
  });

  test('preenche o código de convite automaticamente via parâmetro ?codigo= em /cadastro/congregacao', async ({ page }) => {
    await page.goto('/cadastro/congregacao?codigo=DOMUS-K7M9P2');

    const inputCodigo = page.locator('#codigoConvite');
    await expect(inputCodigo).toBeVisible();
    await expect(inputCodigo).toHaveValue('DOMUS-K7M9P2');
  });

  test('renderiza a página de assinatura cancelada em /assinatura-cancelada', async ({ page }) => {
    await page.goto('/assinatura-cancelada');

    await expect(page.getByRole('heading', { name: /Assinatura Cancelada/i })).toBeVisible();
    await expect(page.getByText(/A assinatura do plano Domus da sua igreja foi cancelada/i)).toBeVisible();
    await expect(page.getByRole('link', { name: /Reativar Assinatura Agora/i })).toBeVisible();
  });

  test('renderiza a tela de login do superadmin em /admin/login', async ({ page }) => {
    await page.goto('/admin/login');

    await expect(page.getByRole('heading', { name: /Domus Admin/i })).toBeVisible();
    await expect(page.getByPlaceholder('admin@domus.com')).toBeVisible();
    await expect(page.getByRole('button', { name: /Acessar Painel Admin/i })).toBeVisible();
  });

  test.describe('Mobile Viewport (iPhone 14)', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('renderiza /planos responsivo em mobile', async ({ page }) => {
      await page.goto('/planos');
      await expect(page.getByText('Básico', { exact: true })).toBeVisible();
      await expect(page.getByText('Pro', { exact: true })).toBeVisible();
    });
  });
});
