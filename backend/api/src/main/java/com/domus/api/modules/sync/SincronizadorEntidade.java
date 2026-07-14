package com.domus.api.modules.sync;

import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import java.util.UUID;

public interface SincronizadorEntidade {

    TipoEntidadeOutbox getTipoEntidade();

    void indexar(UUID entidadeId);

    void remover(UUID entidadeId);
}