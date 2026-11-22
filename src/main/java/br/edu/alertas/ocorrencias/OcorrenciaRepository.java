package br.edu.alertas.ocorrencias;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {
    Optional<Ocorrencia> findByProtocolo(UUID protocolo);
    Optional<Ocorrencia> findByRepresentanteIdAndEnvioId(Long representanteId, UUID envioId);
}
