package com.domus.api.modules.foto;

import com.domus.api.modules.foto.DTOs.FotoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/fotos")
@RequiredArgsConstructor
public class FotoController {

    private final FotoService fotoService;
    private final UsuarioAutenticado usuarioAutenticado;

    /** O tenant vem do JWT; não há como pedir a foto de outra igreja. */
    @PostMapping
    public ResponseEntity<FotoResponse> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fotoService.enviar(arquivo, usuarioAutenticado.getIgrejaId()));
    }

    /** Id de foto nunca é reaproveitado (troca cria outro), então a resposta pode ser marcada imutável. */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> ler(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "DISPLAY") TamanhoFoto tamanho) {
        byte[] bytes = fotoService.ler(id, tamanho, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(bytes);
    }
}
