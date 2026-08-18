export function formatarTelefone(value: string): string {
  const digitos = value.replace(/\D/g, '').slice(0, 11)

  if (digitos.length <= 2) {
    return digitos.replace(/(\d{0,2})/, '($1')
  }
  if (digitos.length <= 6) {
    return digitos.replace(/(\d{2})(\d{0,4})/, '($1) $2')
  }
  if (digitos.length <= 10) {
    return digitos.replace(/(\d{2})(\d{4})(\d{0,4})/, '($1) $2-$3')
  }
  return digitos.replace(/(\d{2})(\d{5})(\d{0,4})/, '($1) $2-$3')
}

export function formatarCep(value: string): string {
  const digitos = value.replace(/\D/g, '').slice(0, 8)
  if (digitos.length <= 5) return digitos
  return digitos.replace(/(\d{5})(\d{0,3})/, '$1-$2')
}

// Existe porque <input type="date"> renderiza no formato do SO/idioma do navegador, não da página (bug real com Chrome en-US).
export function formatarDataDigitada(value: string): string {
  const digitos = value.replace(/\D/g, '').slice(0, 8)
  if (digitos.length <= 2) return digitos
  if (digitos.length <= 4) return digitos.replace(/(\d{2})(\d{0,2})/, '$1/$2')
  return digitos.replace(/(\d{2})(\d{2})(\d{0,4})/, '$1/$2/$3')
}

/** dd/mm/aaaa → aaaa-mm-dd (ISO, o formato que o backend espera). `undefined` se incompleta. */
export function dataBRParaISO(dataBR: string): string | undefined {
  const m = dataBR.match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (!m) return undefined
  const [, dia, mes, ano] = m
  return `${ano}-${mes}-${dia}`
}

/** aaaa-mm-dd (ISO, vindo do backend) → dd/mm/aaaa (o que o campo mostra). */
export function isoParaDataBR(iso: string): string {
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!m) return ''
  const [, ano, mes, dia] = m
  return `${dia}/${mes}/${ano}`
}

/** Máscara de horário digitado (HH:mm), sempre 24h — sem ambiguidade AM/PM de locale. */
export function formatarHoraDigitada(value: string): string {
  const digitos = value.replace(/\D/g, '').slice(0, 4)
  if (digitos.length <= 2) return digitos
  return digitos.replace(/(\d{2})(\d{0,2})/, '$1:$2')
}

export function formatarCnpj(value: string): string {
  const digitos = value.replace(/\D/g, '').slice(0, 14)

  return digitos
    .replace(/(\d{2})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2')
}