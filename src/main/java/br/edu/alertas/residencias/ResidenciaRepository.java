package br.edu.alertas.residencias;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ResidenciaRepository extends JpaRepository<Residencia, Long>, JpaSpecificationExecutor<Residencia> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Residencia r where r.id = :id")
    Optional<Residencia> buscarComBloqueio(Long id);
}
