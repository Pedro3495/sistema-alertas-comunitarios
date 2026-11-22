package br.edu.alertas.moradores;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

interface MoradorRepository extends JpaRepository<Morador, Long>, JpaSpecificationExecutor<Morador> {
    boolean existsByCpf(String cpf);
    boolean existsByCelular(String celular);
    boolean existsByCpfAndIdNot(String cpf, Long id);
    boolean existsByCelularAndIdNot(String celular, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Morador m where m.id = :id")
    Optional<Morador> buscarComBloqueio(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Morador m where m.cpf = :cpf")
    Optional<Morador> buscarPorCpfComBloqueio(String cpf);
}
