import Link from 'next/link'
import styles from './page.module.css'

export default function SegurancaPage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.titulo}>Segurança no Domus</h1>
        <p className={styles.versao}>Versão 1.0 — última atualização em 20/08/2026</p>

        <h2>1. Sobre este documento</h2>
        <p>
          Este documento descreve, de forma direta e verificável, as práticas de segurança
          que o Domus usa hoje para proteger os dados da sua igreja. Não é uma certificação
          de terceiros (como SOC 2 ou ISO 27001) — é uma declaração honesta do que
          implementamos, mantida atualizada conforme o sistema evolui.
        </p>

        <h2>2. Autenticação e sessão</h2>
        <ul>
          <li>
            Duas formas de entrar: e-mail e senha (com hash <strong>bcrypt</strong>, nunca
            texto puro) ou login com <strong>Google</strong> (OAuth).
          </li>
          <li>
            A sessão vive em cookies <code>httpOnly</code>, <code>Secure</code> e{' '}
            <code>SameSite</code> — inacessíveis a JavaScript, o que impede que um ataque de
            XSS roube o token de acesso.
          </li>
          <li>
            O token de acesso expira em 10 minutos; a renovação usa um refresh token que
            gira a cada uso e detecta reuso — se um token de refresh já usado aparecer de
            novo, todas as sessões daquele usuário são revogadas na hora, como sinal de
            token roubado.
          </li>
          <li>
            Tentativas de login malsucedidas são limitadas por conta e por IP, com bloqueio
            temporário contra força bruta.
          </li>
        </ul>

        <h2>3. Proteção contra ataques comuns</h2>
        <ul>
          <li>
            <strong>CSRF</strong> (falsificação de requisição entre sites): todo formulário,
            incluindo os públicos (login, cadastro, recuperação de senha), exige um token de
            validação que um site malicioso não consegue forjar.
          </li>
          <li>
            <strong>Rate limiting</strong>: limite de requisições por IP em toda a API, não
            só no login — protege contra varredura e sobrecarga.
          </li>
          <li>
            <strong>HTTPS obrigatório</strong>, com <code>Strict-Transport-Security</code>{' '}
            (HSTS): o navegador é instruído a nunca tentar acessar o Domus por conexão não
            criptografada, mesmo que alguém digite o endereço errado.
          </li>
          <li>
            Cabeçalhos de segurança adicionais (proteção contra clickjacking, política de
            referrer restritiva, Content-Security-Policy) em toda resposta.
          </li>
        </ul>

        <h2>4. Isolamento entre igrejas</h2>
        <p>
          Cada dado no sistema — pessoa, evento, movimentação financeira — pertence a uma
          igreja específica, identificada por um código extraído do seu token de sessão,
          nunca do conteúdo da requisição. Isso significa que uma igreja não consegue, por
          desenho, acessar ou modificar dado de outra — a checagem acontece no servidor, em
          toda operação, não é uma restrição só de tela.
        </p>

        <h2>5. Backup e continuidade</h2>
        <p>
          O banco de dados é copiado diariamente, criptografado, e enviado para
          armazenamento separado do provedor principal do banco — um backup que mora no
          mesmo lugar que o dado original não é backup de verdade. Todo backup passa por um
          teste automático de restauração antes de ser considerado válido, e cópias ficam
          retidas por até 90 dias.
        </p>

        <h2>6. Monitoramento</h2>
        <p>
          Erros do sistema são capturados automaticamente (Sentry) para permitir resposta
          rápida a falhas — sem incluir dado pessoal nos relatórios de erro. Toda requisição
          é registrada com um identificador único, o que permite investigar qualquer
          incidente reportado.
        </p>

        <h2>7. Fotos e arquivos</h2>
        <p>
          Fotos de pessoas e eventos ficam num armazenamento privado, nunca acessível por
          URL pública — toda foto passa pela própria API do Domus, que confere sua sessão
          antes de entregar o arquivo. Ao receber uma foto, o sistema reprocessa a imagem e
          descarta metadados como localização GPS do celular.
        </p>

        <h2>8. O que ainda não temos</h2>
        <p>
          Para ser transparente: o Domus ainda não passou por auditoria formal de segurança
          de terceiros (como SOC 2 ou ISO 27001) — as práticas acima são implementadas e
          mantidas pela nossa equipe, não certificadas externamente. Também não vendemos ou
          compartilhamos dado com terceiros para fins comerciais, e não usamos cookies de
          rastreamento ou publicidade.
        </p>

        <h2>9. Contato</h2>
        <p>
          Dúvidas sobre segurança ou quer reportar uma vulnerabilidade? Fale com a gente
          pelo suporte do Domus. Veja também a nossa{' '}
          <Link href="/privacidade">Política de Privacidade</Link> e os{' '}
          <Link href="/termos">Termos de Uso</Link>.
        </p>
      </div>
    </div>
  )
}
