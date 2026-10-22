package com.domus.api.modules.usuario;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.igreja.StatusTenant;
import com.domus.api.modules.pessoa.Pessoa;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Poupa o SecurityFilter de bater no Postgres a cada requisição autenticada. TTL curto
 *  (2 min, ver RedisConfig) porque é dado de autorização — role/ativo não podem ficar
 *  velhos por muito tempo; UsuarioService também invalida na hora em toda mutação relevante. */
@Service
@RequiredArgsConstructor
public class PrincipalCacheService {

    private final UsuarioRepository usuarioRepository;

    @Cacheable(value = "principal", key = "#usuarioId", unless = "#result == null")
    public PrincipalCache buscar(UUID usuarioId) {
        return usuarioRepository.findPrincipalCacheById(usuarioId).orElse(null);
    }

    @CacheEvict(value = "principal", key = "#usuarioId")
    public void invalidar(UUID usuarioId) {
        // Evita leituras velhas no SecurityFilter
    }

    /** Reidrata um {@link Usuario} "casca": só os campos usados pelo principal (ver
     *  {@link PrincipalCache}) são reais — o resto (senha, pessoa.nome, etc.) fica nulo
     *  de propósito, porque nunca é lido a partir do principal autenticado. */
    public Usuario reidratar(PrincipalCache cache) {
        Igreja ig = null;
        if (cache.igrejaId() != null) {
            Igreja mae = cache.igrejaMaeId() != null ? Igreja.builder().id(cache.igrejaMaeId()).build() : null;
            ig = Igreja.builder()
                    .id(cache.igrejaId())
                    .statusTenant(cache.statusTenant() != null ? cache.statusTenant() : StatusTenant.ATIVO)
                    .statusAssinatura(cache.statusAssinatura() != null ? cache.statusAssinatura() : StatusAssinatura.ATIVA)
                    .igrejaMae(mae)
                    .motivoSuspensao(cache.motivoSuspensao())
                    .plano(cache.plano())
                    .build();
        }

        return Usuario.builder()
                .id(cache.id())
                .ativo(cache.ativo())
                .igreja(ig)
                .pessoa(Pessoa.builder().id(cache.pessoaId()).build())
                .role(Role.builder().id(cache.roleId()).nome(cache.roleNome()).build())
                .build();
    }
}
