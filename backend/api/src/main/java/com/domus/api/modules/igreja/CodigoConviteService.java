package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.ConsultaConviteResponse;
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

    private static final String ALFABETO_UNAMBIGUO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private String geradorCodigoUnambiguo() {
        StringBuilder sb = new StringBuilder("DOMUS-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALFABETO_UNAMBIGUO.charAt(RANDOM.nextInt(ALFABETO_UNAMBIGUO.length())));
        }
        return sb.toString();
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

        String codigo = geradorCodigoUnambiguo();
        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo(codigo);

        codigoConviteRepository.save(convite);
        return new GerarCodigoConviteResponse(codigo);
    }

    @Transactional(readOnly = true)
    public ConsultaConviteResponse consultarCodigo(String codigo) {
        var optConvite = codigoConviteRepository.findByCodigo(codigo);
        if (optConvite.isEmpty()) {
            return ConsultaConviteResponse.expirado();
        }

        CodigoConviteCongregacao convite = optConvite.get();
        if (convite.getUsadoEm() != null) {
            return ConsultaConviteResponse.jaUtilizado(convite.getMatriz().getNome());
        }

        Igreja matriz = convite.getMatriz();
        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(matriz.getId());
        if (matriz.getPlano() != null && congregacoesAtuais >= matriz.getPlano().getLimiteCongregacoes()) {
            return ConsultaConviteResponse.limiteExcedido(matriz.getNome(), matriz.getPlano().getNomeExibicao());
        }

        UUID logoId = matriz.getLogoFoto() != null ? matriz.getLogoFoto().getId() : null;
        return ConsultaConviteResponse.valido(
                matriz.getNome(),
                null,
                matriz.getPlano() != null ? matriz.getPlano().getNomeExibicao() : "Pro",
                matriz.getPlano() != null ? matriz.getPlano().getLimiteCongregacoes() : 3,
                congregacoesAtuais,
                logoId
        );
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
