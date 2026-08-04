#!/usr/bin/env node
// Remove SOMENTE os registros de demonstração criados por seed-demo-producao.mjs (identificados
// por nome exato) — nunca mexe em pessoas/células reais da igreja. Pessoas são arquivadas
// (soft-delete, não existe hard-delete no sistema hoje); células de teste são apagadas de vez.
//
// Uso: node scripts/limpar-demo-producao.mjs

import { BASE_URL, EMAIL_ADMIN, Sessao, perguntarSenhaOculta } from './lib/sessao-producao.mjs'

const NOMES_DEMO = new Set([
  'Ana Paula Souza', 'Carlos Eduardo Lima', 'Beatriz Fernandes', 'Daniel Rocha',
  'Elaine Cristina Alves', 'Fábio Henrique Santos', 'Gabriela Martins', 'Heitor Costa',
  'Isabela Ribeiro', 'João Vitor Pereira', 'Karina Mendes', 'Lucas Gabriel Oliveira',
  'Mariana Teixeira', 'Nicolas Barbosa', 'Olívia Cardoso', 'Pedro Henrique Duarte',
  'Rafaela Nunes', 'Samuel Correia', 'Tatiane Gonçalves', 'Vinícius Araújo',
])

const NOMES_CELULAS_DEMO = new Set([
  'Célula Vida Nova', 'Célula Esperança', 'Célula Shalom', 'Célula Restauração',
])

async function listarTodasPessoas(sessao) {
  const todas = []
  for (let page = 0; page < 50; page++) {
    const resp = await sessao.requisitar('GET', `/pessoas?page=${page}&size=100`)
    if (!resp) break
    todas.push(...resp.content)
    if (resp.last) break
  }
  return todas
}

async function main() {
  console.log(`Limpando dados de demonstração em ${BASE_URL} como ${EMAIL_ADMIN}`)
  const senha = await perguntarSenhaOculta('Senha do admin (não fica visível): ')

  const sessao = new Sessao()
  await sessao.login(EMAIL_ADMIN, senha)
  console.log('Login OK.')

  console.log('Buscando células de demonstração...')
  const celulas = await sessao.requisitar('GET', '/celulas')
  const celulasDemo = (celulas ?? []).filter((c) => NOMES_CELULAS_DEMO.has(c.nome))
  for (const celula of celulasDemo) {
    const detalhe = await sessao.requisitar('GET', `/celulas/${celula.id}`)
    for (const membro of detalhe.membros ?? []) {
      await sessao.requisitar('DELETE', `/celulas/${celula.id}/membros/${membro.id}`)
    }
    await sessao.requisitar('DELETE', `/celulas/${celula.id}/definitivo`)
    console.log(`  célula "${celula.nome}" apagada de vez.`)
  }

  console.log('Buscando pessoas de demonstração (pode levar um tempo)...')
  const pessoas = await listarTodasPessoas(sessao)
  const pessoasDemo = pessoas.filter((p) => NOMES_DEMO.has(p.nome))
  for (const pessoa of pessoasDemo) {
    await sessao.requisitar('DELETE', `/pessoas/${pessoa.id}`)
  }
  console.log(`  ${pessoasDemo.length} pessoas de demonstração arquivadas.`)
  console.log(
    '  (arquivamento é soft-delete — a pessoa some da listagem/busca, mas o registro',
    'continua no banco; não há hard-delete de pessoa no sistema hoje.)'
  )

  console.log('\nLimpeza concluída. Pode rodar o seed de novo.')
}

main().catch((erro) => {
  console.error('\nFalhou:', erro.message)
  process.exit(1)
})
