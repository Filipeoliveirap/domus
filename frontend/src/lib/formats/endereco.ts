import type { Endereco } from '@/types/pessoa.type'

/** "Rua X, 123, Apto 2 - Centro, Recife/PE (50000-000)" — partes vazias omitidas. */
export function enderecoParaLinhaUnica(e: Endereco): string {
  const linha = [e.logradouro, e.numero, e.complemento].filter(Boolean).join(', ')
  const cidadeUf = e.cidade && e.uf ? `${e.cidade}/${e.uf}` : e.cidade || e.uf || ''
  const bairroCidade = [e.bairro, cidadeUf].filter(Boolean).join(', ')
  let out = [linha, bairroCidade].filter(Boolean).join(' - ')
  if (e.cep) out += ` (${e.cep})`
  return out
}

/** Para os 2 campos compactos do ModalLocalForm (cepLogradouroNumero / complementoBairroCidadeUf). */
export function enderecoIgrejaParaCamposCompactos(e: Endereco): { linha1: string; linha2: string } {
  const linha1 = [e.cep, e.logradouro, e.numero].filter(Boolean).join(', ')
  const cidadeUf = e.cidade && e.uf ? `${e.cidade}/${e.uf}` : e.cidade || e.uf || ''
  const linha2 = [e.complemento, e.bairro, cidadeUf].filter(Boolean).join(' - ')
  return { linha1, linha2 }
}
