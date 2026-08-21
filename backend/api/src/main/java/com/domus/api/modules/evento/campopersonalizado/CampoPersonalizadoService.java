package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampoPersonalizadoService {

    private final CampoPersonalizadoEventoRepository campoRepository;
    private final RespostaCampoPersonalizadoRepository respostaRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;

    public List<CampoPersonalizadoResponse> listar(UUID eventoId, UUID igrejaId) {
        return campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId).stream()
                .map(CampoPersonalizadoResponse::from)
                .toList();
    }

    /** Substitui a lista inteira: cria o que não tem id, atualiza o que tem, arquiva
     *  (soft delete) o que já existia e sumiu da lista enviada. Editar campo (inclusive
     *  opções) é sempre livre, mesmo com resposta já dada — resposta guarda snapshot. */
    @Transactional
    public List<CampoPersonalizadoResponse> salvar(UUID eventoId, UUID igrejaId, List<CampoPersonalizadoRequest> dados) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<CampoPersonalizadoEvento> existentes =
                campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId);
        Map<UUID, CampoPersonalizadoEvento> porId = new HashMap<>();
        for (CampoPersonalizadoEvento c : existentes) porId.put(c.getId(), c);

        java.util.Set<UUID> idsEnviados = new java.util.HashSet<>();
        List<CampoPersonalizadoEvento> resultado = new java.util.ArrayList<>();

        for (CampoPersonalizadoRequest r : dados) {
            CampoPersonalizadoEvento campo = r.id() != null ? porId.get(r.id()) : null;
            if (campo == null) {
                campo = CampoPersonalizadoEvento.builder().igreja(evento.getIgreja()).evento(evento).build();
            } else {
                idsEnviados.add(campo.getId());
            }
            campo.setLabel(r.label());
            campo.setPlaceholder(r.placeholder());
            campo.setTipo(r.tipo());
            campo.setOpcoesComoLista(r.opcoes());
            campo.setObrigatorio(r.obrigatorio());
            campo.setVisivelAoPublico(r.visivelAoPublico());
            campo.setOrdem(r.ordem());
            resultado.add(campoRepository.save(campo));
        }

        for (CampoPersonalizadoEvento existente : existentes) {
            if (!idsEnviados.contains(existente.getId())) {
                campoRepository.delete(existente);
            }
        }

        return resultado.stream().map(CampoPersonalizadoResponse::from).toList();
    }
}
