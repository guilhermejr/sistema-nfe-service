package net.guilhermejr.sistema.nfebaservice.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.nfebaservice.api.response.NFEResponse;
import net.guilhermejr.sistema.nfebaservice.service.NFEService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RequiredArgsConstructor
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
public class NFEController {

    private final NFEService nfeService;

    @GetMapping("/{nfe}")
    public ResponseEntity<NFEResponse> buscar(@PathVariable String nfe) {

        return ResponseEntity.status(HttpStatus.OK).body(nfeService.buscar(nfe));

    }

}
