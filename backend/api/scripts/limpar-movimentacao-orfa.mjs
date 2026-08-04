#!/usr/bin/env node
// Acha e arquiva a(s) movimentação(ões) cuja categoria não existe mais (categoria_id órfão),
// que está quebrando POST /admin/reindexacao com EntityNotFoundException. Não foi criada pelo
// seed — é sujeira anterior aos nossos testes.
import { Sessao, perguntarSenhaOculta, EMAIL_ADMIN } from './lib/sessao-producao.mjs'

const CATEGORIA_ORFA = '54015bd8-3d2e-4dca-a6a2-f8d92f40d571'

async function main() {
  const senha = await perguntarSenhaOculta('Senha do admin (não fica visível): ')
  const sessao = new Sessao()
  await sessao.login(EMAIL_ADMIN, senha)
  console.log('Login OK.')

  console.log(`Buscando movimentações com categoriaId=${CATEGORIA_ORFA}...`)
  const resp = await sessao.requisitar('GET', `/movimentacoes?categoriaId=${CATEGORIA_ORFA}&size=100`)
  const encontradas = resp?.content ?? []
  console.log(`  ${encontradas.length} encontrada(s).`)

  for (const mov of encontradas) {
    await sessao.requisitar('DELETE', `/movimentacoes/${mov.id}`)
    console.log(`  arquivada: ${mov.id} (${mov.descricao ?? 'sem descrição'})`)
  }

  console.log('\nPronto. Roda scripts/reindexar.mjs de novo.')
}

main().catch((erro) => {
  console.error('\nFalhou:', erro.message)
  process.exit(1)
})
