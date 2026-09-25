package com.domus.api.modules.anexo;

import com.domus.api.modules.anexo.dto.AnexoUploadResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/anexos")
@RequiredArgsConstructor
public class AnexoController {

    private final AnexoService service;
    private final UsuarioAutenticado usuarioAutenticado;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou tesoureiro pode gerenciar anexos.");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnexoUploadResponse upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "arquivo", required = false) MultipartFile arquivo) {
        exigirFinanceiro();
        MultipartFile target = file != null ? file : arquivo;
        if (target == null || target.isEmpty()) {
            throw new IllegalArgumentException("Nenhum arquivo enviado.");
        }
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.upload(target, igrejaId);
    }

    @GetMapping("/{id}")
    public void baixar(@PathVariable UUID id, HttpServletResponse response) throws IOException {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        byte[] bytes = service.ler(id, igrejaId);
        Anexo anexo = service.buscarParaVincular(id, igrejaId);
        response.setContentType(anexo.getTipo());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + anexo.getNomeOriginal() + "\"");
        response.getOutputStream().write(bytes);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        service.buscarParaVincular(id, igrejaId);
        service.remover(id);
    }
}
