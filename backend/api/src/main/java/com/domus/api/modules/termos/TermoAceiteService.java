package com.domus.api.modules.termos;

import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Consentimento (Termos de Uso + Política de Privacidade) — nunca editado, só acumulado. */
@Service
@RequiredArgsConstructor
public class TermoAceiteService {

    private final TermoAceiteRepository termoAceiteRepository;

    /** Chamado antes de criar a conta — recusa a operação se não veio true. */
    public void exigirAceite(Boolean aceitouTermos) {
        if (!Boolean.TRUE.equals(aceitouTermos)) {
            throw new BusinessException("TERMOS_NAO_ACEITOS",
                    "É necessário aceitar os Termos de Uso e a Política de Privacidade para continuar.");
        }
    }

    /** Grava as duas linhas (Termos + Política) com a versão atual. */
    @Transactional
    public void registrarAceite(UUID usuarioId, String ip) {
        Usuario usuarioRef = Usuario.builder().id(usuarioId).build();
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuarioRef).tipo(TipoTermo.TERMOS_DE_USO)
                .versao(TermosConstantes.VERSAO_ATUAL).ip(ip).build());
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuarioRef).tipo(TipoTermo.POLITICA_PRIVACIDADE)
                .versao(TermosConstantes.VERSAO_ATUAL).ip(ip).build());
    }

    /** true = falta aceitar Termos e/ou Política na versão atual (nunca aceitou, ou versão antiga). */
    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {
        return termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL) < 2;
    }

    @Transactional(readOnly = true)
    public LocalDateTime dataUltimoAceite(UUID usuarioId) {
        return termoAceiteRepository.buscarUltimoAceite(usuarioId);
    }
}
