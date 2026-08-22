package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/eventos/{eventoId}/campos-personalizados")
@RequiredArgsConstructor
public class CampoPersonalizadoController {

    private final CampoPersonalizadoService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<CampoPersonalizadoResponse>> listar(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(service.listar(eventoId, usuarioAutenticado.getIgrejaId()));
    }

    /** Só os campos que a pessoa logada ainda precisa responder — pula os mapeados que ela já
     *  tem no cadastro. Usado na auto-inscrição normal (não no modal de inscrever alguém: lá o
     *  campo é sempre pra outra pessoa/convidado, quem decide "pular ou não" é este endpoint
     *  só quando quem responde é o próprio usuário logado). */
    @GetMapping("/minha")
    public ResponseEntity<List<CampoPersonalizadoResponse>> listarParaMinhaResposta(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(service.listarParaResponderComoTitular(
                eventoId, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId()));
    }

    @PutMapping
    public ResponseEntity<List<CampoPersonalizadoResponse>> salvar(
            @PathVariable UUID eventoId,
            @Valid @RequestBody List<CampoPersonalizadoRequest> dados) {
        return ResponseEntity.ok(service.salvar(
                eventoId, usuarioAutenticado.getIgrejaId(), dados, usuarioAutenticado.getUsuarioId()));
    }
}
