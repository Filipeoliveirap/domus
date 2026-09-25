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

  test.describe('Mobile Viewport (iPhone 14)', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('renderiza /planos responsivo em mobile', async ({ page }) => {
      await page.goto('/planos');
      await expect(page.getByText('Básico', { exact: true })).toBeVisible();
      await expect(page.getByText('Pro', { exact: true })).toBeVisible();
    });
  });
});
