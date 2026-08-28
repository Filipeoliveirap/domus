import { useMutation } from '@tanstack/react-query'
import { pagamentoService } from '@/services/pagamento.service'
import { notificar } from '@/components/common/Notificacao/notificar'

export function useConectarMercadoPago() {
  return useMutation({
    mutationFn: pagamentoService.gerarUrlConexao,
    onSuccess: (data) => {
      window.location.href = data.urlAutorizacao
    },
    onError: () => {
      notificar.erro('Não foi possível iniciar a conexão com o Mercado Pago. Tente novamente.')
    },
  })
}
