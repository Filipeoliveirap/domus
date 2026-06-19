package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.DTO.IgrejaDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/igrejas")
@RequiredArgsConstructor
public class IgrejaController {
    private final IgrejaService igrejaService;

    @PostMapping("/registrar")
    public ResponseEntity<Void> cadastrarIgreja(@RequestBody @Valid RegistrarIgrejaAdminRequest data) {
        igrejaService.registrar(data);
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<IgrejaDTO> buscarIgrejaPorId(@PathVariable UUID id) {
        IgrejaDTO igreja = igrejaService.buscarPorId(id);
        return ResponseEntity.ok(igreja);
    }
}
