package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.PlanoDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/planos")
public class PlanoController {

    @GetMapping
    public ResponseEntity<List<PlanoDTO>> listarPlanos() {
        List<PlanoDTO> planos = Arrays.stream(PlanoAssinatura.values())
            .map(PlanoDTO::de)
            .toList();
        return ResponseEntity.ok(planos);
    }
}
