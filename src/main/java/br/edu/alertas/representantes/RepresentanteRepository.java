package br.edu.alertas.representantes;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepresentanteRepository
        extends JpaRepository<Representante, Long>, JpaSpecificationExecutor<Representante> {
    boolean existsByLogin(String login);

    boolean existsByLoginAndIdNot(String login, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Representante r where r.login = :login")
    Optional<Representante> buscarPorLoginComBloqueio(@Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Representante r where r.id = :id")
    Optional<Representante> buscarPorIdComBloqueio(@Param("id") Long id);
}
