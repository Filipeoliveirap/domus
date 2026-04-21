package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/igrejas")
@RequiredArgsConstructor
public class IgrejaController {
    private final IgrejaService igrejaService;

    @PostMapping("/registrar")
    public ResponseEntity<Void> cadastrarIgreja(@RequestBody @Valid RegistrarIgrejaAdminRequest data) {
        this.igrejaService.registrar(data);
        return ResponseEntity.status(201).build();
    }
}
