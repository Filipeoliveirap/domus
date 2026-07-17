package com.domus.api.modules.sync;

import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.usuario.busca.UsuarioDocument;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UsuarioSincronizador implements SincronizadorEntidade {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioSearchRepository usuarioSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.USUARIO;
    }

    @Override
    public void indexar(UUID entidadeId) {
        usuarioRepository.findById(entidadeId).ifPresentOrElse(
                usuario -> {
                    usuarioSearchRepository.save(UsuarioDocument.de(usuario));
                    log.debug("Usuário indexado no Elastic. id={}", entidadeId);
                },
                () -> {
                    usuarioSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Usuário não encontrado no Postgres, removido do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        usuarioSearchRepository.deleteById(entidadeId.toString());
        log.debug("Usuário removido do Elastic. id={}", entidadeId);
    }
}