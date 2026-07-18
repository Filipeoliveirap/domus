import { useState } from 'react'
import type { Endereco } from '@/types/membro.type'

interface ViaCepResposta {
  logradouro?: string
  bairro?: string
  localidade?: string
  uf?: string
  // A ViaCEP devolve "erro" como string "true" (não booleano) quando o CEP não existe.
  erro?: boolean | string
}

export function useBuscaCep() {
  const [carregando, setCarregando] = useState(false)

  async function buscar(cep: string): Promise<Endereco | null> {
    const limpo = cep.replace(/\D/g, '')
    if (limpo.length !== 8) return null

    setCarregando(true)
    try {
      const resp = await fetch(`https://viacep.com.br/ws/${limpo}/json/`)
      if (!resp.ok) return null
      const data: ViaCepResposta = await resp.json()
      if (data.erro) return null
      return {
        cep: limpo,
        logradouro: data.logradouro ?? '',
        bairro: data.bairro ?? '',
        cidade: data.localidade ?? '',
        uf: data.uf ?? '',
      }
    } catch {
      return null
    } finally {
      setCarregando(false)
    }
  }

  return { buscar, carregando }
}
