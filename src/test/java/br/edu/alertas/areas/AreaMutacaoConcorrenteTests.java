package br.edu.alertas.areas;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class AreaMutacaoConcorrenteTests {
    @Autowired AreaRiscoService areas;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void alteracaoAguardandoExclusaoRetornaAreaAusenteEmVezDeErroOtimista() throws Exception {
        Long id = areas.cadastrar("Área Corrida Mutação " + UUID.randomUUID(), "Limites fictícios").getId();
        String marcador = "area_mutacao_" + UUID.randomUUID().toString().replace("-", "");
        AtomicReference<Future<Throwable>> alteracao = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(holder -> {
                assertThat(jdbc.queryForObject("SELECT id FROM areas_risco WHERE id = ? FOR UPDATE", Long.class, id))
                        .isEqualTo(id);
                alteracao.set(executor.submit(() -> {
                    try {
                        new TransactionTemplate(transactionManager).executeWithoutResult(waiter -> {
                            jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, marcador);
                            jdbc.execute("SET LOCAL lock_timeout = '15s'");
                            areas.alterar(id, "Nome corrigido", "Limites corrigidos");
                        });
                        return null;
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }));
                // Espera uma contenção real no PostgreSQL antes de excluir; não depende da velocidade das threads.
                await().pollInterval(Duration.ofMillis(50)).atMost(Duration.ofSeconds(10)).until(() ->
                        Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock')",
                                Boolean.class, marcador)));
                assertThat(jdbc.update("DELETE FROM areas_risco WHERE id = ?", id)).isEqualTo(1);
            });
            assertThat(alteracao.get().get(20, TimeUnit.SECONDS)).isInstanceOf(EntityNotFoundException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM areas_risco WHERE id = ?", Integer.class, id)).isZero();
        } finally {
            jdbc.update("DELETE FROM areas_risco WHERE id = ?", id);
        }
    }
}
