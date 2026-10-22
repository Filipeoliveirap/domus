package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import com.domus.api.modules.pagamento.assinatura.dto.ResumoAssinaturaResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssinaturaService {

    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MercadoPagoSubscriptionService mpSubscriptionService;
    private final com.domus.api.modules.usuario.PrincipalCacheService principalCacheService;

    @Transactional(readOnly = true)
    public ResumoAssinaturaResponse obterResumo(UUID igrejaId) {
        Igreja igreja = buscarIgreja(igrejaId);
        Igreja mae = igreja.getIgrejaMae();
        boolean ehMatriz = (mae == null);
        String nomeMatriz = ehMatriz ? null : mae.getNome();

        long totalPessoas = pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId);
        long totalCongregacoes = ehMatriz
                ? igrejaRepository.countByIgrejaMaeId(igrejaId)
                : igrejaRepository.countByIgrejaMaeId(mae.getId());

        PlanoAssinatura plano;
        if (!ehMatriz && mae.getPlano() != null) {
            plano = mae.getPlano();
        } else {
            plano = igreja.getPlano();
        }

        String nomePlano = plano != null ? plano.getNomeExibicao() : "Sem Plano";
        BigDecimal valorMensal = plano != null ? plano.getValorMensal() : BigDecimal.ZERO;
        int limitePessoas = plano != null ? plano.getLimitePessoas() : 0;
        int limiteCongregacoes = plano != null ? plano.getLimiteCongregacoes() : 0;

        int percentualPessoas = limitePessoas > 0 ? (int) Math.min(100, (totalPessoas * 100) / limitePessoas) : 0;
        int percentualCongregacoes = limiteCongregacoes > 0 ? (int) Math.min(100, (totalCongregacoes * 100) / limiteCongregacoes) : 0;

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime cicloExpiraEm = ehMatriz ? igreja.getTrialExpiraEm() : mae.getTrialExpiraEm();

        // Se trialExpiraEm for nulo, assume 14 dias a partir da criação ou de agora
        if (cicloExpiraEm == null) {
            LocalDateTime base = igreja.getCreatedAt() != null ? igreja.getCreatedAt() : agora;
            cicloExpiraEm = base.plusDays(14);
        }

        Long diasRestantesCiclo = 0L;
        if (cicloExpiraEm.isAfter(agora)) {
            long diasCompletos = Duration.between(agora, cicloExpiraEm).toDays();
            // Se resta fração de horas/minutos mas ainda não venceu, garante pelo menos 1 dia
            diasRestantesCiclo = Math.max(1L, diasCompletos);
        }

        long diasRestantesTrial = 0L;
        StatusAssinatura status = ehMatriz ? igreja.getStatusAssinatura() : mae.getStatusAssinatura();
        if (status == null) {
            status = StatusAssinatura.ATIVA;
        }
        if (status == StatusAssinatura.TRIAL) {
            diasRestantesTrial = diasRestantesCiclo;
        }

        return new ResumoAssinaturaResponse(
            plano,
            nomePlano,
            valorMensal,
            status,
            diasRestantesTrial,
            totalPessoas,
            limitePessoas,
            percentualPessoas,
            totalCongregacoes,
            limiteCongregacoes,
            percentualCongregacoes,
            ehMatriz,
            nomeMatriz,
            cicloExpiraEm,
            diasRestantesCiclo
        );
    }

    @Transactional
    public void alterarPlano(UUID igrejaId, PlanoAssinatura novoPlano) {
        Igreja igreja = buscarIgreja(igrejaId);
        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("FILIAL_NAO_GERENCIA_PLANO",
                    "Esta igreja é uma congregação vinculada. A gestão da assinatura é de responsabilidade da matriz.");
        }

        long totalPessoas = pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId);
        if (totalPessoas > novoPlano.getLimitePessoas()) {
            throw new PlanoLimiteExcedidoException(String.format(
                "Não é possível migrar para o plano %s: a igreja possui %d pessoas ativas (limite do plano: %d).",
                novoPlano.getNomeExibicao(), totalPessoas, novoPlano.getLimitePessoas()
            ));
        }

        long totalCongregacoes = igrejaRepository.countByIgrejaMaeId(igrejaId);
        if (totalCongregacoes > novoPlano.getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException(String.format(
                "Não é possível migrar para o plano %s: a igreja possui %d congregações filhas (limite do plano: %d).",
                novoPlano.getNomeExibicao(), totalCongregacoes, novoPlano.getLimiteCongregacoes()
            ));
        }

        mpSubscriptionService.alterarPlano(igreja.getMpPreapprovalId(), novoPlano);

        igreja.setPlano(novoPlano);
        igrejaRepository.save(igreja);
        usuarioRepository.findIdsAtivosPorIgreja(igrejaId).forEach(principalCacheService::invalidar);
    }

    @Transactional
    public void atualizarCartao(UUID igrejaId, String cardTokenId) {
        Igreja igreja = buscarIgreja(igrejaId);
        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("FILIAL_NAO_GERENCIA_PLANO",
                    "Esta igreja é uma congregação vinculada. A gestão da assinatura é de responsabilidade da matriz.");
        }
        mpSubscriptionService.atualizarCartao(igreja.getMpPreapprovalId(), cardTokenId);
    }

    @Transactional
    public void cancelarAssinatura(UUID igrejaId) {
        Igreja igreja = buscarIgreja(igrejaId);
        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("FILIAL_NAO_GERENCIA_PLANO",
                    "Esta igreja é uma congregação vinculada. A gestão da assinatura é de responsabilidade da matriz.");
        }
        mpSubscriptionService.cancelarAssinatura(igreja.getMpPreapprovalId());
        igreja.setStatusAssinatura(StatusAssinatura.CANCELADA);
        igrejaRepository.save(igreja);
    }

    @Transactional
    public void reativarAssinatura(UUID igrejaId) {
        Igreja igreja = buscarIgreja(igrejaId);
        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("FILIAL_NAO_GERENCIA_PLANO",
                    "Esta igreja é uma congregação vinculada. A gestão da assinatura é de responsabilidade da matriz.");
        }
        mpSubscriptionService.reativarAssinatura(igreja.getMpPreapprovalId());
        igreja.setStatusAssinatura(StatusAssinatura.ATIVA);
        igrejaRepository.save(igreja);
    }

    @Transactional
    public void contratarPlano(UUID igrejaId, PlanoAssinatura plano, String cardTokenId) {
        Igreja igreja = buscarIgreja(igrejaId);
        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("FILIAL_NAO_GERENCIA_PLANO",
                    "Esta igreja é uma congregação vinculada. A gestão da assinatura é de responsabilidade da matriz.");
        }

        // Validação defensiva dos limites do plano contra os dados já existentes
        long totalPessoas = pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId);
        if (totalPessoas > plano.getLimitePessoas()) {
            throw new PlanoLimiteExcedidoException(String.format(
                "Não é possível contratar o plano %s: a igreja possui %d pessoas ativas (limite do plano: %d).",
                plano.getNomeExibicao(), totalPessoas, plano.getLimitePessoas()
            ));
        }

        long totalCongregacoes = igrejaRepository.countByIgrejaMaeId(igrejaId);
        if (totalCongregacoes > plano.getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException(String.format(
                "Não é possível contratar o plano %s: a igreja possui %d congregações filhas (limite do plano: %d).",
                plano.getNomeExibicao(), totalCongregacoes, plano.getLimiteCongregacoes()
            ));
        }

        igreja.setPlano(plano);

        if (cardTokenId != null && !cardTokenId.isBlank()) {
            List<Usuario> admins = usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA");
            String emailAdmin = !admins.isEmpty() ? admins.get(0).getPessoa().getEmail() : igreja.getEmailContato();

            var respAssinatura = mpSubscriptionService.criarAssinaturaTrial(
                    igreja,
                    new com.domus.api.modules.pagamento.assinatura.dto.CriarAssinaturaRequest(
                            cardTokenId.trim(),
                            emailAdmin
                    )
            );
            igreja.setMpPreapprovalId(respAssinatura.preapprovalId());
        }

        // Já foi igreja cliente ou desvinculada: sai do modo leitura e vira ATIVA (sem período de testes novo)
        igreja.setStatusAssinatura(StatusAssinatura.ATIVA);
        igreja.setMotivoSuspensao(null);
        igrejaRepository.save(igreja);
        usuarioRepository.findIdsAtivosPorIgreja(igrejaId).forEach(principalCacheService::invalidar);
        log.info("Novo plano contratado com sucesso. igreja_id={}, plano={}", igrejaId, plano);
    }

    private Igreja buscarIgreja(UUID igrejaId) {
        return igrejaRepository.findById(igrejaId)
            .orElseThrow(() -> new BusinessException("IGREJA_NAO_ENCONTRADA", "Igreja não encontrada."));
    }
}
