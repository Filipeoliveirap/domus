package com.domus.api.modules.visitante;

import com.domus.api.modules.visitante.DTOs.MoverParaCelulaRequest;
import com.domus.api.modules.visitante.DTOs.ToggleRequest;
import com.domus.api.modules.visitante.DTOs.VisitanteBuscaLeveResponse;
import com.domus.api.modules.visitante.DTOs.VisitanteRequest;
import com.domus.api.modules.visitante.DTOs.VisitanteResponse;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/visitantes")
@RequiredArgsConstructor
@Validated
public class VisitanteController {

    private final VisitanteService visitanteService;
    private final VisitanteRepository visitanteRepository;
    private final UsuarioAutenticado usuarioAutenticado;

    /** Sem exigirGestao() de propósito: usado pelo modal de inscrever evento por qualquer
     *  perfil, não só quem gerencia visitantes/célula. Payload enxuto (não usa VisitanteResponse
     *  completo) pra não vazar dado de acompanhamento de célula pra quem só gerencia evento. */
    @GetMapping("/busca-leve")
    public ResponseEntity<java.util.List<VisitanteBuscaLeveResponse>> buscaLeve(
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String q,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        String filtro = (q == null || q.isBlank()) ? null : q.trim();
        var visitantes = visitanteRepository.buscaLeve(usuarioAutenticado.getIgrejaId(), filtro, pageable);
        return ResponseEntity.ok(visitantes.stream().map(VisitanteBuscaLeveResponse::from).toList());
    }

    @GetMapping
    public ResponseEntity<PagedResponse<VisitanteResponse>> listar(
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String q,
            @RequestParam(required = false) Boolean contatoRealizado,
            @RequestParam(required = false) Boolean visitaRealizada,
            @RequestParam(required = false) Boolean acompanhamentoFeito,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.listar(
                usuarioAutenticado.getIgrejaId(), q,
                contatoRealizado, visitaRealizada, acompanhamentoFeito, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VisitanteResponse> detalhe(@PathVariable UUID id) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.buscarPorId(id, usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping
    public ResponseEntity<VisitanteResponse> criar(@Valid @RequestBody VisitanteRequest data) {
        exigirGestao();
        VisitanteResponse response = visitanteService.criar(
                data, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VisitanteResponse> atualizar(@PathVariable UUID id,
                                                        @Valid @RequestBody VisitanteRequest data) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.atualizar(
                id, data, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        exigirGestao();
        visitanteService.excluir(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/contato")
    public ResponseEntity<VisitanteResponse> toggleContato(@PathVariable UUID id,
                                                            @Valid @RequestBody ToggleRequest data) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.toggleContato(
                id, data.valor(), usuarioAutenticado.getIgrejaId()));
    }

    @PutMapping("/{id}/visita")
    public ResponseEntity<VisitanteResponse> toggleVisita(@PathVariable UUID id,
                                                           @Valid @RequestBody ToggleRequest data) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.toggleVisita(
                id, data.valor(), usuarioAutenticado.getIgrejaId()));
    }

    @PutMapping("/{id}/acompanhamento")
    public ResponseEntity<VisitanteResponse> toggleAcompanhamento(@PathVariable UUID id,
                                                                   @Valid @RequestBody ToggleRequest data) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.toggleAcompanhamento(
                id, data.valor(), usuarioAutenticado.getIgrejaId()));
    }

    @PutMapping("/{id}/celula")
    public ResponseEntity<VisitanteResponse> moverParaCelula(@PathVariable UUID id,
                                                              @Valid @RequestBody MoverParaCelulaRequest data) {
        exigirGestao();
        return ResponseEntity.ok(visitanteService.moverParaCelula(
                id, data.celulaId(), usuarioAutenticado.getIgrejaId()));
    }

    private void exigirGestao() {
        if (!Permissoes.podeGerenciarVisitantes(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador pode gerenciar visitantes.");
        }
    }
}
