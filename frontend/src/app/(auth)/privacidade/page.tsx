import Link from 'next/link'
import styles from './page.module.css'

export default function PoliticaDePrivacidadePage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.titulo}>Política de Privacidade do Domus</h1>
        <p className={styles.versao}>Versão 1.0 — última atualização em 19/08/2026</p>

        <h2>1. Quem somos</h2>
        <p>
          O Domus processa dados pessoais para permitir que igrejas gerenciem seus membros,
          eventos e finanças. Esta política explica como tratamos esses dados, em conformidade
          com a Lei Geral de Proteção de Dados (LGPD — Lei 13.709/2018).
        </p>

        <h2>2. Controlador e operador</h2>
        <p>
          A igreja que usa o Domus é a <strong>controladora</strong> dos dados de pessoas,
          membros e visitantes que ela cadastra — decide o que coletar, por quanto tempo manter
          e com quem compartilhar. O Domus é o <strong>operador</strong>: processamos esses
          dados exclusivamente em nome da igreja, seguindo suas instruções, e nunca os usamos
          para finalidade própria (como venda a terceiros ou publicidade).
        </p>
        <p>
          Já os dados da própria conta de acesso (login, senha, aceite de termos) têm o Domus
          como controlador.
        </p>

        <h2>3. Quais dados coletamos</h2>
        <ul>
          <li>Dados de cadastro: nome, e-mail, telefone, endereço, data de nascimento.</li>
          <li>Dados financeiros da igreja (movimentações, categorias) — não dados de cartão ou conta bancária pessoal.</li>
          <li>Fotos de pessoas e eventos, quando enviadas.</li>
          <li>Dados técnicos de acesso: e-mail de login, IP no momento do aceite dos Termos, registros de auditoria de ações no sistema.</li>
        </ul>

        <h2>4. Subprocessadores</h2>
        <p>Para funcionar, o Domus usa os seguintes serviços de terceiros, todos sob contrato:</p>
        <ul>
          <li><strong>Neon</strong> — banco de dados (armazenamento dos dados cadastrados).</li>
          <li><strong>Cloudflare R2</strong> — armazenamento de fotos, em bucket privado.</li>
          <li><strong>Resend</strong> — envio de e-mails transacionais (recuperação de senha, convites, avisos).</li>
          <li><strong>Google</strong> — autenticação via login com Google (OAuth), quando você escolhe esse método.</li>
          <li><strong>Elasticsearch</strong> — busca interna dos dados, auto-hospedado, sem compartilhamento externo.</li>
        </ul>

        <h2>5. Cookies</h2>
        <p>
          Usamos apenas cookies de sessão, estritamente necessários (<code>httpOnly</code>,
          nunca acessíveis por JavaScript): um para manter você autenticado e outro para renovar
          a sessão automaticamente. <strong>Não usamos cookies de rastreamento, analytics ou
          publicidade de terceiros.</strong>
        </p>

        <h2>6. Seus direitos (LGPD)</h2>
        <p>Como titular de dados, você tem direito a:</p>
        <ul>
          <li>Confirmar a existência de tratamento e acessar seus dados.</li>
          <li>Corrigir dados incompletos, inexatos ou desatualizados.</li>
          <li>
            <strong>Solicitar a eliminação</strong> dos seus dados — o Domus já implementa esse
            direito de forma concreta: o administrador da sua igreja pode excluir definitivamente
            seu cadastro a qualquer momento, e a própria igreja pode agendar a exclusão de toda
            a conta (com carência de 10 dias cancelável), apagando todos os dados de forma
            irreversível ao final do prazo.
          </li>
          <li>Revogar consentimento, quando aplicável.</li>
        </ul>
        <p>
          Para exercer esses direitos sobre dados cadastrados por uma igreja (ex.: seu cadastro
          como membro), procure o administrador dessa igreja — ele é o controlador desses dados.
          Para dados da sua própria conta de acesso, use as opções em Configurações ou fale com
          o suporte do Domus.
        </p>

        <h2>7. Segurança</h2>
        <p>
          Senhas são armazenadas com hash (nunca em texto puro), a sessão usa cookies
          <code>httpOnly</code> protegidos contra acesso via JavaScript, e o tráfego é sempre
          criptografado (HTTPS).
        </p>

        <h2>8. Mudanças nesta Política</h2>
        <p>
          Podemos atualizar esta Política. Mudanças relevantes exigem um novo aceite, pedido
          no seu próximo acesso.
        </p>

        <h2>9. Contato</h2>
        <p>
          Dúvidas sobre esta Política ou sobre seus dados? Fale com a gente pelo suporte do
          Domus. Veja também os <Link href="/termos">Termos de Uso</Link>.
        </p>
      </div>
    </div>
  )
}
