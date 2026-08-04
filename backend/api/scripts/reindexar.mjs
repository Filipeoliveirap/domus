#!/usr/bin/env node
// Só dispara POST /admin/reindexacao, sem login (o endpoint está público temporariamente).
const resposta = await fetch('https://domusigreja.com.br/api/admin/reindexacao', { method: 'POST' })
const texto = await resposta.text()
console.log(resposta.status, texto)
