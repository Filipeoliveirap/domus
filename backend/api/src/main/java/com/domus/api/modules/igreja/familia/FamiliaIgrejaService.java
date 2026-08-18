package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.exception.AcessoNegadoException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Autorização da hierarquia de igrejas; enxerga só pra baixo — a filha nunca vê a mãe nem as irmãs. */
@Service
@Slf4j
@RequiredArgsConstructor
public class FamiliaIgrejaService {

    private final IgrejaRepository igrejaRepository;

    /** Mãe é quem tem pelo menos uma filha vinculada — não é quem tem código gerado. */
    @Transactional(readOnly = true)
    public boolean ehMae(UUID igrejaId) {
        return igrejaRepository.existsByIgrejaMaeId(igrejaId);
    }

    /** Filha é quem tem mãe. Usado onde só a sede pode agir (ex.: consolidado financeiro). */
    @Transactional(readOnly = true)
    public boolean ehFilha(UUID igrejaId) {
        return buscar(igrejaId).getIgrejaMae() != null;
    }

    /** Retorna {@code {eu} ∪ {minhas filhas}}. Filha ou independente → apenas {@code {eu}}. */
    @Transactional(readOnly = true)
    public List<UUID> idsDaFamilia(UUID igrejaId) {
        List<UUID> ids = new ArrayList<>();
        ids.add(igrejaId);
        ids.addAll(igrejaRepository.buscarIdsDasFilhas(igrejaId));
        return ids;
    }

    /** Com mãe: {@code {mãe} ∪ {todas as filhas dela, inclusive eu}}. Sem mãe: {@code {eu} ∪ {minhas filhas}}. */
    @Transactional(readOnly = true)
    public Set<UUID> idsDaFamiliaCompleta(UUID igrejaId) {
        Igreja igreja = buscar(igrejaId);
        UUID maeId = igreja.getIgrejaMae() != null ? igreja.getIgrejaMae().getId() : null;

        if (maeId == null) {
            Set<UUID> ids = new HashSet<>();
            ids.add(igrejaId);
            ids.addAll(igrejaRepository.buscarIdsDasFilhas(igrejaId));
            return ids;
        }

        Set<UUID> ids = new HashSet<>();
        ids.add(maeId);
        igrejaRepository.findByIgrejaMaeIdOrderByNomeAsc(maeId)
                .forEach(filha -> ids.add(filha.getId()));
        return ids;
    }

    /** A igreja pedida pertence à família de quem está perguntando? */
    @Transactional(readOnly = true)
    public boolean pertenceAFamilia(UUID igrejaSolicitanteId, UUID igrejaAlvoId) {
        if (igrejaSolicitanteId.equals(igrejaAlvoId)) {
            return true;
        }
        return igrejaRepository.findById(igrejaAlvoId)
                .map(Igreja::getIgrejaMae)
                .map(mae -> igrejaSolicitanteId.equals(mae.getId()))
                .orElse(false);
    }

    /** {@code igrejaId} ausente = minha igreja; presente, só passa se pertencer à minha família. */
    @Transactional(readOnly = true)
    public UUID resolverEscopo(UUID igrejaSolicitanteId, UUID igrejaAlvoId) {
        if (igrejaAlvoId == null) {
            return igrejaSolicitanteId;
        }
        validarAcesso(igrejaSolicitanteId, igrejaAlvoId);
        return igrejaAlvoId;
    }

    /** Barra o IDOR. Lança {@link AcessoNegadoException} se o alvo não for da família. */
    @Transactional(readOnly = true)
    public void validarAcesso(UUID igrejaSolicitanteId, UUID igrejaAlvoId) {
        if (!pertenceAFamilia(igrejaSolicitanteId, igrejaAlvoId)) {
            log.warn("Acesso cruzado negado. solicitante_igreja_id={}, alvo_igreja_id={}",
                    igrejaSolicitanteId, igrejaAlvoId);
            throw new AcessoNegadoException();
        }
    }

    /** As congregações desta igreja, para a tela de Igrejas vinculadas. */
    @Transactional(readOnly = true)
    public List<Igreja> filhasDe(UUID igrejaId) {
        return igrejaRepository.findByIgrejaMaeIdOrderByNomeAsc(igrejaId);
    }

    @Transactional(readOnly = true)
    public Igreja buscar(UUID igrejaId) {
        return igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada"));
    }
}
