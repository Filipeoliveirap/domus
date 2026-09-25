package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/igrejas-vinculadas")
@RequiredArgsConstructor
public class CodigoConviteController {

    private final CodigoConviteService codigoConviteService;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioAutenticado usuarioAutenticado;

    @PostMapping("/codigo-convite")
    public ResponseEntity<GerarCodigoConviteResponse> gerarCodigo() {
        Igreja matriz = igrejaRepository.findById(usuarioAutenticado.getIgrejaId())
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        GerarCodigoConviteResponse response = codigoConviteService.gerarCodigo(matriz);
        return ResponseEntity.ok(response);
    }
}
