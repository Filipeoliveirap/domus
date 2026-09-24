package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CodigoConviteService {

    private final CodigoConviteRepository codigoConviteRepository;
    private final IgrejaRepository igrejaRepository;

    public CodigoConviteService(CodigoConviteRepository codigoConviteRepository, IgrejaRepository igrejaRepository) {
        this.codigoConviteRepository = codigoConviteRepository;
        this.igrejaRepository = igrejaRepository;
    }

    @Transactional
    public GerarCodigoConviteResponse gerarCodigo(Igreja matriz) {
        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(matriz.getId());
        if (congregacoesAtuais >= matriz.getPlano().getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException(
                String.format("O plano %s permite no máximo %d congregações vinculadas.",
                    matriz.getPlano().getNomeExibicao(), matriz.getPlano().getLimiteCongregacoes())
            );
        }

        String codigo = "DOMUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo(codigo);

        codigoConviteRepository.save(convite);
        return new GerarCodigoConviteResponse(codigo);
    }

    @Transactional
    public CodigoConviteCongregacao validarEConsumirCodigo(String codigo, Igreja igrejaFilha) {
        CodigoConviteCongregacao convite = codigoConviteRepository.findByCodigoAndUsadoEmIsNull(codigo)
            .orElseThrow(() -> new IllegalArgumentException("Código de convite inválido ou já utilizado"));

        Igreja matriz = convite.getMatriz();
        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(matriz.getId());
        if (congregacoesAtuais >= matriz.getPlano().getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException("A igreja matriz atingiu o limite de congregações do plano atual.");
        }

        convite.setUsadoEm(LocalDateTime.now());
        convite.setIgrejaFilha(igrejaFilha);
        igrejaFilha.setIgrejaMae(matriz);
        igrejaFilha.setStatusAssinatura(StatusAssinatura.ATIVA);

        return codigoConviteRepository.save(convite);
    }
}
