import readline from 'node:readline'

export const BASE_URL = 'https://domusigreja.com.br/api'
export const EMAIL_ADMIN = 'josefilipe.dev@gmail.com'

export function perguntarSenhaOculta(pergunta) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
    const stdin = process.stdin
    process.stdout.write(pergunta)
    stdin.resume()
    stdin.setRawMode?.(true)
    let senha = ''
    const onData = (buf) => {
      const codigo = buf[0]
      if (codigo === 13 || codigo === 10) {
        stdin.setRawMode?.(false)
        stdin.pause()
        stdin.removeListener('data', onData)
        process.stdout.write('\n')
        rl.close()
        resolve(senha)
      } else if (codigo === 3) {
        process.exit(1)
      } else if (codigo === 8 || codigo === 127) {
        senha = senha.slice(0, -1)
      } else {
        senha += buf.toString('utf8')
      }
    }
    stdin.on('data', onData)
  })
}

export class Sessao {
  constructor() {
    this.cookies = new Map()
  }

  guardarCookies(resposta) {
    const setCookie = resposta.headers.getSetCookie?.() ?? []
    for (const linha of setCookie) {
      const [par] = linha.split(';')
      const idx = par.indexOf('=')
      const nome = par.slice(0, idx)
      const valor = par.slice(idx + 1)
      this.cookies.set(nome, valor)
    }
  }

  headerCookie() {
    return [...this.cookies.entries()].map(([k, v]) => `${k}=${v}`).join('; ')
  }

  csrfHeader() {
    const token = this.cookies.get('XSRF-TOKEN')
    return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
  }

  async requisitar(metodo, path, corpo, tentativa = 0) {
    // Espaça as chamadas pra não estourar o RateLimitFilter (por IP, global).
    await new Promise((r) => setTimeout(r, 250))
    const resposta = await fetch(`${BASE_URL}${path}`, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        Cookie: this.headerCookie(),
        ...this.csrfHeader(),
      },
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    })
    this.guardarCookies(resposta)
    if (resposta.status === 429 && tentativa < 5) {
      const espera = 2000 * (tentativa + 1)
      console.warn(`  rate limit em ${metodo} ${path}, aguardando ${espera}ms...`)
      await new Promise((r) => setTimeout(r, espera))
      return this.requisitar(metodo, path, corpo, tentativa + 1)
    }
    const texto = await resposta.text()
    if (!resposta.ok) {
      throw new Error(`${metodo} ${path} -> ${resposta.status}: ${texto}`)
    }
    if (!texto) return undefined
    try {
      return JSON.parse(texto)
    } catch {
      console.warn(`  aviso: resposta de ${metodo} ${path} não é JSON: ${texto.slice(0, 200)}`)
      return undefined
    }
  }

  async login(email, senha) {
    // Primeiro GET pra receber o cookie XSRF-TOKEN antes do login (login está isento de
    // CSRF, mas o cookie só é emitido quando o CsrfFilter processa uma request).
    await this.requisitar('GET', '/eventos/tipos').catch(() => {})
    return this.requisitar('POST', '/auth/login', { email, senha })
  }
}
