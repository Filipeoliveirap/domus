package com.domus.api.modules.evento.local;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.EventoService;
import com.domus.api.modules.evento.local.DTOs.LocalEventoRequest;
import com.domus.api.modules.evento.local.DTOs.LocalEventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalEventoService {

    private final LocalEventoRepository localEventoRepository;
    private final IgrejaRepository igrejaRepository;
    private final EventoRepository eventoRepository;
    private final EventoService eventoService;

    @Transactional(readOnly = true)
    public List<LocalEventoResponse> listar(UUID igrejaId) {
        return localEventoRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .map(l -> LocalEventoResponse.from(l, eventoRepository.countByLocalIdAndIgrejaId(l.getId(), igrejaId) > 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public LocalEventoResponse buscarDaIgreja(UUID id, UUID igrejaId) {
        LocalEvento local = localEventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Local não encontrado."));
        return LocalEventoResponse.from(local);
    }

    @Transactional
    public LocalEventoResponse criar(LocalEventoRequest data, UUID igrejaId) {
        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        LocalEvento local = LocalEvento.builder()
                .igreja(igreja)
                .nome(nome)
                .capacidade(data.capacidade())
                .cepLogradouroNumero(data.cepLogradouroNumero())
                .complementoBairroCidadeUf(data.complementoBairroCidadeUf())
                .build();

        LocalEvento salvo = localEventoRepository.save(local);
        return LocalEventoResponse.from(salvo);
    }

    @Transactional
    public LocalEventoResponse atualizar(UUID id, LocalEventoRequest data, UUID igrejaId) {
        LocalEvento local = localEventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Local não encontrado."));

        String nome = TextoUtil.capitalizar(data.nome());
        boolean nomeMudou = !TextoUtil.normalizarParaComparacao(nome)
                .equals(TextoUtil.normalizarParaComparacao(local.getNome()));
        // Só valida duplicata se o nome mudou de verdade — senão o próprio local bateria
        // consigo mesmo na comparação normalizada.
        if (nomeMudou) {
            validarNaoDuplicado(nome, igrejaId);
        }

        local.setNome(nome);
        local.setCapacidade(data.capacidade());
        local.setCepLogradouroNumero(data.cepLogradouroNumero());
        local.setComplementoBairroCidadeUf(data.complementoBairroCidadeUf());

        LocalEvento salvo = localEventoRepository.save(local);

        // EventoDocument.local reflete o nome do local — todo evento vinculado precisa
        // reindexar quando ele muda (via evento.getLocalExibicao()).
        if (nomeMudou) {
            eventoService.reindexarPorLocal(id, igrejaId);
        }

        return LocalEventoResponse.from(salvo);
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        LocalEvento local = localEventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Local não encontrado."));

        // Reindexa antes de desvincular: depois disso local_id vira NULL e a query de
        // busca por local não encontra mais os eventos afetados.
        eventoService.reindexarPorLocal(id, igrejaId);

        // Soft delete não desvincula (sem isso o proxy LAZY de local derrubaria a listagem); SQL nativo pra alcançar também eventos arquivados.
        eventoRepository.desvincularLocal(id);

        localEventoRepository.delete(local);
    }

    @Transactional(readOnly = true)
    public List<LocalEventoResponse> listarArquivados(UUID igrejaId) {
        return localEventoRepository.findArquivadosPorIgreja(igrejaId).stream()
                .map(LocalEventoResponse::from)
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        int linhas = localEventoRepository.restaurarPorId(id, igrejaId);
        if (linhas == 0) {
            throw new ResourceNotFoundException("Local não encontrado.");
        }
    }

    /**
     * Nunca bloqueia: {@code arquivar} já desvincula todo evento (inclusive arquivado) antes
     * de soft-deletar — quando um local chega até aqui, nada mais aponta pra ele.
     */
    @Transactional
    public void excluirDefinitivo(UUID id, UUID igrejaId) {
        localEventoRepository.findByIdAndIgrejaIdIncluindoArquivados(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Local não encontrado."));
        localEventoRepository.hardDeleteById(id);
    }

    private void validarNaoDuplicado(String nome, UUID igrejaId) {
        String normalizado = TextoUtil.normalizarParaComparacao(nome);
        boolean duplicado = localEventoRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .anyMatch(l -> TextoUtil.normalizarParaComparacao(l.getNome()).equals(normalizado));
        if (duplicado) {
            throw new BusinessException("LOCAL_DUPLICADO", "Já existe um local com esse nome.");
        }
    }
}
