package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.StatusAssinatura;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssinaturaWebhookService {

    private final IgrejaRepository igrejaRepository;

    public AssinaturaWebhookService(IgrejaRepository igrejaRepository) {
        this.igrejaRepository = igrejaRepository;
    }

    @Transactional
    public void processarWebhookAssinatura(String preapprovalId, String statusMp) {
        igrejaRepository.findByMpPreapprovalId(preapprovalId).ifPresent(igreja -> {
            switch (statusMp.toLowerCase()) {
                case "authorized" -> igreja.setStatusAssinatura(StatusAssinatura.ATIVA);
                case "paused" -> igreja.setStatusAssinatura(StatusAssinatura.PAUSADA);
                case "cancelled" -> igreja.setStatusAssinatura(StatusAssinatura.CANCELADA);
            }
            igrejaRepository.save(igreja);
        });
    }
}
