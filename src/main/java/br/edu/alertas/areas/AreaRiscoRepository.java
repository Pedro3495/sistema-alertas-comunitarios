package br.edu.alertas.areas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRiscoRepository extends JpaRepository<AreaRisco, Long> {
    List<AreaRisco> findByNomeContainingIgnoreCase(String nome);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select area from AreaRisco area where area.id = :id")
    java.util.Optional<AreaRisco> buscarComBloqueio(@org.springframework.data.repository.query.Param("id") Long id);
}
