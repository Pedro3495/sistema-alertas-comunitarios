package br.edu.alertas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class EstruturaBancoTests {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void triggerImpedeAlterarRegistroDeAuditoria() {
        Long id = jdbc.queryForObject("""
                INSERT INTO registros_auditoria (acao, responsavel_tipo, responsavel_id, ocorrido_em)
                VALUES ('TESTE_TRIGGER', 'ADMINISTRADOR', 999999, now()) RETURNING id""", Long.class);
        try {
            assertThatThrownBy(() -> jdbc.update("UPDATE registros_auditoria SET acao = 'forjada' WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("registros_auditoria é imutável");
            assertThat(jdbc.queryForObject("SELECT acao FROM registros_auditoria WHERE id = ?", String.class, id))
                    .isEqualTo("TESTE_TRIGGER");
        } finally {
            jdbc.update("DELETE FROM registros_auditoria WHERE id = ?", id);
        }
    }

    @Test
    void viewResumeTodasAsOcorrenciasPorAreaESituacao() {
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM vw_resumo_ocorrencias_area", Long.class))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM ocorrencias", Long.class));
    }
}
