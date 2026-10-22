package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.StatusAssinatura;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssinaturaWebhookService {

    private final IgrejaRepository igrejaRepository;
    private final MercadoPagoSubscriptionService mpSubscriptionService;

    public AssinaturaWebhookService(IgrejaRepository igrejaRepository,
                                    MercadoPagoSubscriptionService mpSubscriptionService) {
        this.igrejaRepository = igrejaRepository;
        this.mpSubscriptionService = mpSubscriptionService;
    }

    @Transactional
    public void processarWebhookAssinatura(String preapprovalId, String statusMp) {
        if (statusMp == null || statusMp.isBlank()) {
            statusMp = mpSubscriptionService.consultarStatus(preapprovalId);
        }

        if (statusMp == null || statusMp.isBlank()) {
            return;
        }

        final String finalStatus = statusMp;

        igrejaRepository.findByMpPreapprovalId(preapprovalId).ifPresent(igreja -> {
            StatusAssinatura novoStatus = switch (finalStatus.toLowerCase()) {
                case "authorized" -> StatusAssinatura.ATIVA;
                case "paused" -> StatusAssinatura.PAUSADA;
                case "cancelled" -> StatusAssinatura.CANCELADA;
                default -> null;
            };

            if (novoStatus != null) {
                igreja.setStatusAssinatura(novoStatus);
                igrejaRepository.save(igreja);

                // Cascateia status para congregações filhas vinculadas
                var filhas = igrejaRepository.findByIgrejaMaeId(igreja.getId());
                for (var filha : filhas) {
                    filha.setStatusAssinatura(novoStatus);
                    igrejaRepository.save(filha);
                }
            }
        });
    }
}
