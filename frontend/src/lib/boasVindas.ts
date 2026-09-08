/**
 * Marca, por sessão do navegador, que a animação de boas-vindas já apareceu. Assim o
 * "fade curto" ao voltar com token só roda na primeira vez que o app abre naquela aba —
 * abrir o domínio de novo no mesmo dia não repete. `sessionStorage` some ao fechar a aba.
 *
 * Tudo em try/catch: em aba anônima ou com storage bloqueado o acesso lança — nesse caso
 * o padrão é "já viu" (não incomodar com a animação onde não dá pra lembrar que rodou).
 */
const CHAVE = 'domus:boas-vindas'

export function boasVindasJaVista(): boolean {
  try {
    return sessionStorage.getItem(CHAVE) === '1'
  } catch {
    return true
  }
}

export function marcarBoasVindasVista(): void {
  try {
    sessionStorage.setItem(CHAVE, '1')
  } catch {
    // sem storage: nada a fazer, a checagem já devolve "true"
  }
}
