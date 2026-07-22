package com.domus.api.modules.evento.local;

import com.domus.api.modules.evento.EventoRepository;
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
        // Só valida duplicata se o nome mudou de verdade — senão o próprio local bateria
        // consigo mesmo na comparação normalizada.
        if (!TextoUtil.normalizarParaComparacao(nome).equals(TextoUtil.normalizarParaComparacao(local.getNome()))) {
            validarNaoDuplicado(nome, igrejaId);
        }

        local.setNome(nome);
        local.setCapacidade(data.capacidade());
        local.setCepLogradouroNumero(data.cepLogradouroNumero());
        local.setComplementoBairroCidadeUf(data.complementoBairroCidadeUf());

        LocalEvento salvo = localEventoRepository.save(local);
        return LocalEventoResponse.from(salvo);
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        LocalEvento local = localEventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Local não encontrado."));

        // O ON DELETE SET NULL da FK evento.local_id NUNCA dispara aqui: LocalEvento usa
        // @SQLDelete (soft delete — só marca deleted_at), não um DELETE de verdade. Sem este
        // passo, o evento continuaria com local_id apontando para um local que o
        // @SQLRestriction esconde — a próxima leitura de EventoResponse.from (que resolve o
        // proxy LAZY de local) estouraria EntityNotFoundException e derrubaria a listagem
        // INTEIRA de eventos (mais dashboard e outbox do Elasticsearch).
        //
        // Por isso, antes de arquivar, "desligamos" o vínculo copiando o NOME do local para
        // local_texto — o evento não perde a informação de onde acontece (ela vira texto,
        // que é exatamente o que o campo já representa para locais ad-hoc). Preferido a
        // recusar o arquivamento: não bloqueia o usuário (o local pode ter sido descontinuado
        // de verdade) e reaproveita um caminho que já existe (localTexto).
        //
        // Usa SQL nativo (EventoRepository.desvincularLocal), NÃO findByLocalIdAndIgrejaId +
        // save: eventos ARQUIVADOS também têm que ser desvinculados (senão ficam órfãos pra
        // sempre — ver o comentário do método no repository), e o @SQLRestriction de Evento
        // esconde os arquivados de qualquer busca via JPQL/derived query.
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
