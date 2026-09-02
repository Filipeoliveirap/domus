import type { Endereco } from '@/types/pessoa.type'
import type { LocalEventoResponse } from '@/types/evento.type'

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

const norm = (s?: string | null) =>
  (s ?? '').trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[̀-ͯ]/g, '')

/** Já existe um endereço cadastrado que É o endereço da igreja? (herda ou tem os mesmos campos).
 *  Se sim, o botão "usar o endereço da igreja" não serve mais — ninguém cadastra duas vezes. */
export function jaExisteEnderecoDaIgreja(locais: LocalEventoResponse[], enderecoIgreja?: Endereco | null): boolean {
  if (!enderecoIgreja) return false
  const { linha1, linha2 } = enderecoIgrejaParaCamposCompactos(enderecoIgreja)
  return locais.some((l) =>
    l.enderecoHerdado
    || (norm(l.cepLogradouroNumero) === norm(linha1) && norm(l.complementoBairroCidadeUf) === norm(linha2)),
  )
}
