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

export function formatarCnpj(value: string): string {
  const digitos = value.replace(/\D/g, '').slice(0, 14)

  return digitos
    .replace(/(\d{2})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2')
}