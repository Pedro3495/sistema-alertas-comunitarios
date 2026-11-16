package br.edu.alertas.areas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRiscoRepository extends JpaRepository<AreaRisco, Long> {
    List<AreaRisco> findByNomeContainingIgnoreCase(String nome);
}
