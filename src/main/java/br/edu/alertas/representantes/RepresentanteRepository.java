package br.edu.alertas.representantes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RepresentanteRepository
        extends JpaRepository<Representante, Long>, JpaSpecificationExecutor<Representante> {
    boolean existsByLogin(String login);

    boolean existsByLoginAndIdNot(String login, Long id);
}
