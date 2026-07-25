'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { X, ShieldCheck, Users } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { Vinculo } from '@/types/celula.type'
import styles from './ModalConverterVisitante.module.css'

interface ModalConverterVisitanteProps {
  celulaId: string
  visitanteId: string
  visitanteNome: string
  onClose: () => void
}

export function ModalConverterVisitante({ celulaId, visitanteId, visitanteNome, onClose }: ModalConverterVisitanteProps) {
  const [vinculo, setVinculo] = useState<Vinculo>('MEMBRO')
  const [loading, setLoading] = useState(false)
  const queryClient = useQueryClient()
  const router = useRouter()

  async function handleConfirmar() {
    setLoading(true)
    try {
      const pessoa = await celulaService.converterVisitante(celulaId, visitanteId, { vinculo })
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${visitanteNome} convertido. Redirecionando...`)
      // Fecha o modal (limpa o estado de "convertendo" na página) antes de navegar — evitando
      // deixá-lo montado, re-renderizando contra dados de célula que mudaram por baixo dele
      // por causa do invalidarCache logo acima. Navegação client-side (router.push), não
      // window.location.href: um reload completo aqui corria contra o refetch em andamento
      // da célula, cortando a página no meio de um estado transitório — parecia um erro
      // piscando na tela, mas era só a corrida entre o reload e o React ainda re-renderizando.
      onClose()
      router.push(`/pessoas/${pessoa.id}`)
    } catch {
      notificar.erro('Erro ao converter visitante.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={e => e.stopPropagation()}>
        <button className={styles.close} onClick={onClose}><X size={18} /></button>
        <h2 className={styles.titulo}>Converter Visitante</h2>
        <p className={styles.desc}>
          Selecione o vínculo para <strong>{visitanteNome}</strong>.
          Você será redirecionado para completar o cadastro.
        </p>

        <div className={styles.cards}>
          <label className={`${styles.card} ${vinculo === 'MEMBRO' ? styles.cardAtivo : ''}`}>
            <input type="radio" name="vinculo" value="MEMBRO" checked={vinculo === 'MEMBRO'}
              onChange={() => setVinculo('MEMBRO')} className={styles.radio} />
            <ShieldCheck size={24} />
            <h4>Membro</h4>
            <p>Compromisso formal com a igreja. Elegível para liderança e acesso completo.</p>
          </label>
          <label className={`${styles.card} ${vinculo === 'CONGREGANTE' ? styles.cardAtivo : ''}`}>
            <input type="radio" name="vinculo" value="CONGREGANTE" checked={vinculo === 'CONGREGANTE'}
              onChange={() => setVinculo('CONGREGANTE')} className={styles.radio} />
            <Users size={24} />
            <h4>Congregante</h4>
            <p>Frequentador regular que participa de células e eventos, sem vínculo formal completo.</p>
          </label>
        </div>

        <div className={styles.acoes}>
          <button className={styles.btnCancelar} onClick={onClose}>Cancelar</button>
          <button className={styles.btnConfirmar} onClick={handleConfirmar} disabled={loading}>
            {loading ? 'Convertendo…' : 'Confirmar conversão'}
          </button>
        </div>
      </div>
    </div>
  )
}
