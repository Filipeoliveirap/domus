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
                .map(LocalEventoResponse::from)
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

        // Soft delete do LocalEvento não dispara ON DELETE SET NULL da FK evento.local_id.
        // Sem desvincular, o proxy LAZY de local dispararia EntityNotFoundException ao ler
        // EventoResponse e derrubaria a listagem inteira de eventos. Copiamos o nome para
        // local_texto (reaproveitando o campo ad-hoc já existente) e usamos SQL nativo para
        // alcançar também os eventos já arquivados — @SQLRestriction esconde arquivados de
        // qualquer query derivada/JPQL.
        eventoRepository.desvincularLocal(id, local.getNome());

        localEventoRepository.delete(local);
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
