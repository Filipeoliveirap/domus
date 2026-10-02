'use client';

import styles from './assinatura.module.css';

export default function ConfigAssinaturaPage() {
    return (
        <div className={styles.container}>
            <h1 className={styles.titulo}>Minha Assinatura Domus</h1>

            <div className={styles.card}>
                <div className={styles.cabecalhoPlano}>
                    <div>
                        <span className={styles.badgePlano}>
                            Plano Atual
                        </span>
                        <h2 className={styles.nomePlano}>Pro (R$ 179,00 / mês)</h2>
                        <p className={styles.statusTrial}>Status: TRIAL (11 dias restantes)</p>
                    </div>
                    <span className={styles.badgeEmDia}>
                        Em dia
                    </span>
                </div>

                <div className={styles.gridConsumo}>
                    <div className={styles.boxConsumo}>
                        <p className={styles.labelConsumo}>Consumo de Pessoas Ativas</p>
                        <p className={styles.valorConsumo}>142 de 300 pessoas</p>
                        <div className={styles.barraProgressoBg}>
                            <div className={styles.barraProgressoFill} style={{ width: '47%' }}></div>
                        </div>
                    </div>

                    <div className={styles.boxConsumo}>
                        <p className={styles.labelConsumo}>Congregações Vinculadas</p>
                        <p className={styles.valorConsumo}>1 de 3 congregações</p>
                        <div className={styles.barraProgressoBg}>
                            <div className={styles.barraProgressoFill} style={{ width: '33%' }}></div>
                        </div>
                    </div>
                </div>

                <div className={styles.botoesAcao}>
                    <button className={styles.btnPrimary}>
                        Alterar Plano
                    </button>
                    <button className={styles.btnSecondary}>
                        Atualizar Cartão de Crédito
                    </button>
                    <button className={styles.btnDanger}>
                        Cancelar Assinatura
                    </button>
                </div>
            </div>
        </div>
    );
}
