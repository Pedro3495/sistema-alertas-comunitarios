package br.edu.alertas;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InfraestruturaTests {
    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void conectaAoPostgresEAplicaMigracao() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).contains("PostgreSQL");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void servePaginaInicialSemCredenciais() throws Exception {
        var response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Sistema de Alertas Comunitários");
    }

    @Test
    void naoLiberaRotasDeNegocioAindaNaoImplementadas() throws Exception {
        assertThat(get("/moradores").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
