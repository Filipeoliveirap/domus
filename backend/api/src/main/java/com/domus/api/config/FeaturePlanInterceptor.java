package com.domus.api.config;

import com.domus.api.modules.igreja.FeaturePlan;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

@Component
public class FeaturePlanInterceptor {

    public boolean validarFeature(HttpServletResponse response, Igreja igreja, FeaturePlan feature) throws IOException {
        PlanoAssinatura plano = (igreja != null && igreja.getPlano() != null) ? igreja.getPlano() : PlanoAssinatura.BASICO;

        if (!plano.temFeature(feature)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            PrintWriter writer = response.getWriter();
            writer.write(String.format("{\"error\": \"A funcionalidade '%s' não está incluída no seu plano atual (%s). Faça um upgrade para liberar.\", \"feature\": \"%s\"}",
                feature.getDescricao(), plano.getNomeExibicao(), feature.name()));
            return false;
        }
        return true;
    }
}
