package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.ConsultaConviteResponse;
import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import com.domus.api.shared.exception.ResourceNotFoundException;
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
        Igreja matrizComLock = igrejaRepository.buscarComLock(matriz.getId())
                .orElse(matriz);
        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(matrizComLock.getId());
        if (matrizComLock.getPlano() != null && congregacoesAtuais >= matrizComLock.getPlano().getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException(
                String.format("O plano %s permite no máximo %d congregações vinculadas.",
                    matrizComLock.getPlano().getNomeExibicao(), matrizComLock.getPlano().getLimiteCongregacoes())
            );
        }

        String codigo = geradorCodigoUnambiguo();
        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matrizComLock);
        convite.setCodigo(codigo);

        codigoConviteRepository.save(convite);

        matrizComLock.setCodigoVinculo(codigo);
        matrizComLock.setCodigoGeradoEm(LocalDateTime.now());
        igrejaRepository.save(matrizComLock);

        return new GerarCodigoConviteResponse(codigo);
    }

    @Transactional(readOnly = true)
    public ConsultaConviteResponse consultarCodigo(String codigo) {
        var optConvite = codigoConviteRepository.findByCodigo(codigo);
        Igreja matriz;
        if (optConvite.isPresent()) {
            matriz = optConvite.get().getMatriz();
        } else {
            var optIgreja = igrejaRepository.findByCodigoVinculo(codigo);
            if (optIgreja.isEmpty()) {
                return ConsultaConviteResponse.expirado();
            }
            matriz = optIgreja.get();
        }

        // Se o código foi rotacionado, este código não serve mais
        if (matriz.getCodigoVinculo() == null || !codigo.equalsIgnoreCase(matriz.getCodigoVinculo())) {
            return ConsultaConviteResponse.expirado();
        }

        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(matriz.getId());
        String rotuloSingular = matriz.getCongregacaoNomeSingular() != null ? matriz.getCongregacaoNomeSingular() : "Congregação";
        String rotuloPlural = matriz.getCongregacaoNomePlural() != null ? matriz.getCongregacaoNomePlural() : "Congregações";
        String planoNome = matriz.getPlano() != null ? matriz.getPlano().getNomeExibicao() : "Pro";
        int limite = matriz.getPlano() != null ? matriz.getPlano().getLimiteCongregacoes() : 3;

        if (matriz.getPlano() != null && congregacoesAtuais >= limite) {
            return ConsultaConviteResponse.limiteExcedido(matriz.getNome(), planoNome, limite, congregacoesAtuais, rotuloSingular, rotuloPlural);
        }

        UUID logoId = matriz.getLogoFoto() != null ? matriz.getLogoFoto().getId() : null;
        return ConsultaConviteResponse.valido(
                matriz.getNome(),
                null,
                planoNome,
                limite,
                congregacoesAtuais,
                logoId,
                rotuloSingular,
                rotuloPlural
        );
    }

    @Transactional
    public CodigoConviteCongregacao validarEConsumirCodigo(String codigo, Igreja igrejaFilha) {
        var optConvite = codigoConviteRepository.findByCodigo(codigo);
        Igreja matriz;
        CodigoConviteCongregacao convite = null;
        if (optConvite.isPresent()) {
            convite = optConvite.get();
            matriz = convite.getMatriz();
        } else {
            matriz = igrejaRepository.findByCodigoVinculo(codigo)
                    .orElseThrow(() -> new IllegalArgumentException("Código de convite inválido ou expirado"));
        }

        if (matriz.getCodigoVinculo() == null || !codigo.equalsIgnoreCase(matriz.getCodigoVinculo())) {
            throw new IllegalArgumentException("Código de convite inválido ou expirado");
        }

        Igreja matrizComLock = igrejaRepository.buscarComLock(matriz.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Igreja matriz não encontrada."));

        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(matrizComLock.getId());
        if (matrizComLock.getPlano() != null && congregacoesAtuais >= matrizComLock.getPlano().getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException("A igreja matriz atingiu o limite de congregações do plano atual.");
        }

        if (convite == null) {
            convite = new CodigoConviteCongregacao();
            convite.setMatriz(matrizComLock);
            convite.setCodigo(codigo);
        }
        convite.setUsadoEm(LocalDateTime.now());
        convite.setIgrejaFilha(igrejaFilha);

        igrejaFilha.setIgrejaMae(matrizComLock);
        igrejaFilha.setVinculadoEm(LocalDateTime.now());
        igrejaFilha.setStatusAssinatura(StatusAssinatura.ATIVA);
        if (matrizComLock.getPlano() != null) {
            igrejaFilha.setPlano(matrizComLock.getPlano());
        }
        igrejaRepository.save(igrejaFilha);

        return codigoConviteRepository.save(convite);
    }
}
