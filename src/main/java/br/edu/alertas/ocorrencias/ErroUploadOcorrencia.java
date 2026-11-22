package br.edu.alertas.ocorrencias;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestControllerAdvice
class ErroUploadOcorrencia {
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Map<String, String>> upload(HttpServletRequest request, MultipartException exception) {
        if (!request.getServletPath().equals("/ocorrencias")) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        return ResponseEntity.badRequest().body(Map.of("campo", "fotos", "erro",
                "Não foi possível receber as fotos. Escolha de uma a quatro fotos de até 5 MB e tente novamente."));
    }
}
