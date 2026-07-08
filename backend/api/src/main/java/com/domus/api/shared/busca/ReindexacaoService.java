package com.domus.api.shared.busca;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.busca.EventoDocument;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoDocument;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.busca.MembroDocument;
import com.domus.api.modules.membro.busca.MembroSearchRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.usuario.busca.UsuarioDocument;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReindexacaoService {

    private final MembroRepository membroRepository;
    private final MembroSearchRepository membroSearchRepository;
    private final EventoRepository eventoRepository;
    private final EventoSearchRepository eventoSearchRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioSearchRepository usuarioSearchRepository;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final MovimentacaoSearchRepository  movimentacaoSearchRepository;

    @Transactional(readOnly = true)
    public Map<String, Long> reindexarTudo() {
        Map<String, Long> resultado = new HashMap<>();

        resultado.put("membros", reindexarMembros());
        resultado.put("eventos", reindexarEventos());
        resultado.put("usuarios", reindexarUsuarios());
        resultado.put("movimentacoes", reindexarMovimentacoes());
        // resultado.put("categorias", reindexarCategorias());

        log.info("Reindexação completa. resultado={}", resultado);
        return resultado;
    }

    private long reindexarMembros() {
        var docs = membroRepository.findAll().stream()
                .map(MembroDocument::de)
                .toList();
        membroSearchRepository.saveAll(docs);
        log.info("Reindexados {} membros.", docs.size());
        return docs.size();
    }

    private long reindexarEventos() {
        var docs = eventoRepository.findAll().stream()
                .map(EventoDocument::de)
                .toList();
        eventoSearchRepository.saveAll(docs);
        log.info("Reindexados {} eventos.", docs.size());
        return docs.size();
    }

    private long reindexarUsuarios() {
        var docs = usuarioRepository.findAll().stream()
                .map(UsuarioDocument::de)
                .toList();
        usuarioSearchRepository.saveAll(docs);
        log.info("Reindexados {} usuários.", docs.size());
        return docs.size();
    }

    private long reindexarMovimentacoes() {
        var docs = movimentacaoRepository.findAll().stream()
                .map(MovimentacaoDocument::de)
                .toList();
        movimentacaoSearchRepository.saveAll(docs);
        log.info("Reindexadas {} movimentações.", docs.size());
        return docs.size();
    }
}