import Link from 'next/link'
import styles from './page.module.css'

export default function TermosDeUsoPage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.titulo}>Termos de Uso do Domus</h1>
        <p className={styles.versao}>Versão 1.0 — última atualização em 19/08/2026</p>

        <h2>1. Aceitação dos Termos</h2>
        <p>
          Ao criar uma conta ou usar o Domus, você concorda com estes Termos de Uso e
          com a nossa <Link href="/privacidade">Política de Privacidade</Link>. Se você
          não concordar, não utilize o produto.
        </p>

        <h2>2. O que é o Domus</h2>
        <p>
          O Domus é um sistema de gestão administrativa (cadastro de pessoas, eventos,
          financeiro e busca) voltado para igrejas de pequeno e médio porte.
        </p>

        <h2>3. Quem pode usar</h2>
        <p>
          O cadastro de uma igreja cria uma conta administradora (ADMIN_IGREJA), responsável
          por conceder acesso a outras pessoas dentro da mesma igreja. Cada pessoa com acesso
          precisa aceitar estes Termos e a Política de Privacidade individualmente.
        </p>

        <h2>4. Responsabilidade pelos dados cadastrados</h2>
        <p>
          A igreja que usa o Domus é a <strong>controladora</strong> dos dados de pessoas,
          membros e visitantes que ela cadastra — é quem decide o que coletar e para quê,
          nos termos da Lei Geral de Proteção de Dados (LGPD). O Domus atua como
          <strong> operador</strong>, processando esses dados em nome da igreja, conforme
          descrito na nossa Política de Privacidade.
        </p>

        <h2>5. Uso adequado</h2>
        <p>
          Você concorda em não usar o Domus para fins ilegais, para armazenar dados sem
          autorização das pessoas envolvidas, ou para qualquer atividade que viole direitos
          de terceiros.
        </p>

        <h2>6. Exclusão de conta</h2>
        <p>
          A qualquer momento, o administrador da igreja pode agendar a exclusão definitiva
          da conta e de todos os dados associados, com carência de 10 dias cancelável, pela
          tela de Configurações. Depois do prazo, a exclusão é irreversível.
        </p>

        <h2>7. Mudanças nestes Termos</h2>
        <p>
          Podemos atualizar estes Termos. Mudanças relevantes exigem um novo aceite, que
          será pedido no seu próximo acesso.
        </p>

        <h2>8. Contato</h2>
        <p>Dúvidas sobre estes Termos? Fale com a gente pelo suporte do Domus.</p>
      </div>
    </div>
  )
}
