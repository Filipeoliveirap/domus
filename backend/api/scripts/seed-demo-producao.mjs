#!/usr/bin/env node
// Popula a igreja "Batista Central" em produção (https://domusigreja.com.br) com dados de
// demonstração pro TCC: pessoas, células, eventos e financeiro. Usa a API real via HTTPS,
// autenticado como o admin da igreja (sessão em cookie + CSRF double-submit).
//
// Uso: node scripts/seed-demo-producao.mjs
// A senha é pedida no terminal (não fica em arquivo nem em variável de ambiente salva).

import { BASE_URL, EMAIL_ADMIN, Sessao, perguntarSenhaOculta } from './lib/sessao-producao.mjs'

const NOMES = [
  'Ana Paula Souza', 'Carlos Eduardo Lima', 'Beatriz Fernandes', 'Daniel Rocha',
  'Elaine Cristina Alves', 'Fábio Henrique Santos', 'Gabriela Martins', 'Heitor Costa',
  'Isabela Ribeiro', 'João Vitor Pereira', 'Karina Mendes', 'Lucas Gabriel Oliveira',
  'Mariana Teixeira', 'Nicolas Barbosa', 'Olívia Cardoso', 'Pedro Henrique Duarte',
  'Rafaela Nunes', 'Samuel Correia', 'Tatiane Gonçalves', 'Vinícius Araújo',
]

function aleatorio(lista) {
  return lista[Math.floor(Math.random() * lista.length)]
}

function dataAleatoria(anoMin, anoMax) {
  const ano = anoMin + Math.floor(Math.random() * (anoMax - anoMin + 1))
  const mes = String(1 + Math.floor(Math.random() * 12)).padStart(2, '0')
  const dia = String(1 + Math.floor(Math.random() * 27)).padStart(2, '0')
  return `${ano}-${mes}-${dia}`
}

async function main() {
  console.log(`Populando dados de demonstração em ${BASE_URL} como ${EMAIL_ADMIN}`)
  const senha = await perguntarSenhaOculta('Senha do admin (não fica visível): ')

  const sessao = new Sessao()
  await sessao.login(EMAIL_ADMIN, senha)
  console.log('Login OK.')

  console.log('Criando pessoas...')
  const pessoas = []
  for (const nome of NOMES) {
    const pessoa = await sessao.requisitar('POST', '/pessoas', {
      nome,
      vinculo: Math.random() < 0.6 ? 'MEMBRO' : 'CONGREGANTE',
      estadoCivil: aleatorio(['SOLTEIRO', 'CASADO', 'DIVORCIADO', 'VIUVO']),
      sexo: aleatorio(['HOMEM', 'MULHER']),
      dataNascimento: dataAleatoria(1955, 2008),
    })
    pessoas.push(pessoa)
  }
  console.log(`  ${pessoas.length} pessoas criadas.`)

  console.log('Criando células...')
  const nomesCelulas = ['Célula Vida Nova', 'Célula Esperança', 'Célula Shalom', 'Célula Restauração']
  const dias = ['SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA']
  const celulas = []
  for (const nome of nomesCelulas) {
    const celula = await sessao.requisitar('POST', '/celulas', {
      nome,
      diaSemana: aleatorio(dias),
      horario: '19:30',
    })
    celulas.push(celula)
  }
  for (const celula of celulas.filter(Boolean)) {
    const membros = pessoas.filter(() => Math.random() < 0.35)
    let primeiro = true
    for (const pessoa of membros) {
      const membro = await sessao.requisitar('POST', `/celulas/${celula.id}/membros`, {
        pessoaId: pessoa.id,
      })
      if (primeiro) {
        await sessao.requisitar('PUT', `/celulas/${celula.id}/membros/${membro.id}/papel`, {
          papel: 'LIDER',
        })
        primeiro = false
      }
    }
  }
  console.log(`  ${celulas.length} células criadas e povoadas.`)

  console.log('Criando eventos...')
  const eventos = [
    { titulo: 'Culto de Celebração', tipo: 'Culto', inicioEm: '2026-08-09T19:00:00' },
    { titulo: 'Conferência de Jovens', tipo: 'Conferência', inicioEm: '2026-08-22T19:30:00' },
    { titulo: 'Encontro de Casais', tipo: 'Encontro', inicioEm: '2026-07-18T19:00:00' },
    { titulo: 'Batismo nas Águas', tipo: 'Culto', inicioEm: '2026-06-14T18:00:00' },
    { titulo: 'Escola Bíblica Dominical', tipo: 'Ensino', inicioEm: '2026-08-16T09:00:00' },
  ]
  for (const evento of eventos) {
    await sessao.requisitar('POST', '/eventos', {
      ...evento,
      descricao: `${evento.titulo} — Igreja Batista Central`,
      localTexto: 'Templo Sede',
      vagas: 150,
      requerInscricao: false,
      controlaPresenca: false,
      exclusivoMembros: false,
      restritoPropriaIgreja: false,
    })
  }
  console.log(`  ${eventos.length} eventos criados.`)

  console.log('Criando categorias e movimentações financeiras...')
  const categorias = [
    { nome: 'Dízimos', tipo: 'ENTRADA' },
    { nome: 'Ofertas', tipo: 'ENTRADA' },
    { nome: 'Manutenção', tipo: 'SAIDA' },
    { nome: 'Missões', tipo: 'SAIDA' },
  ]
  const categoriasCriadas = []
  for (const c of categorias) {
    categoriasCriadas.push(await sessao.requisitar('POST', '/categorias', c))
  }
  for (let i = 0; i < 30; i++) {
    const categoria = aleatorio(categoriasCriadas)
    const pessoa = aleatorio(pessoas)
    const valor = Math.round((50 + Math.random() * 950) * 100) / 100
    await sessao.requisitar('POST', '/movimentacoes', {
      tipo: categoria.tipo,
      valor,
      categoriaId: categoria.id,
      dataMovimentacao: dataAleatoria(2026, 2026),
      contribuintes: categoria.tipo === 'ENTRADA' ? [{ pessoaId: pessoa.id, valor }] : [],
      descricao: `${categoria.nome} — lançamento de demonstração`,
    })
  }
  console.log('  4 categorias e 30 movimentações criadas.')

  console.log('Disparando reindexação (Elasticsearch)...')
  const resultado = await sessao.requisitar('POST', '/admin/reindexacao')
  console.log('  Reindexação concluída:', resultado)

  console.log('\nPronto! Dados de demonstração populados em produção.')
}

main().catch((erro) => {
  console.error('\nFalhou:', erro.message)
  process.exit(1)
})
