package br.edu.alertas.administracao;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AdministradorRepository extends JpaRepository<Administrador, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Administrador a where a.id = 1")
    Optional<Administrador> buscarComBloqueio();
}
