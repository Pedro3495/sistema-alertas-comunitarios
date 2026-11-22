package br.edu.alertas.ocorrencias;

import br.edu.alertas.administracao.AdministradorService;
import br.edu.alertas.moradores.*;
import br.edu.alertas.representantes.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

abstract class OcorrenciasFixtures {
    static final String TEMPORARIA = "temporaria-ficticia-rf006";
    static final String DEFINITIVA = "definitiva-ficticia-rf006";
    static final int LIMITE = 5 * 1024 * 1024;
    @Autowired OcorrenciaService service;
    @Autowired RepresentanteService representantes;
    @Autowired AutenticacaoRepresentanteService acesso;
    @Autowired AdministradorService administradores;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutenticacaoMoradorService acessoMorador;
    @Autowired PasswordEncoder encoder;
    final List<Long> residencias = new ArrayList<>();
    final List<Long> areasExtras = new ArrayList<>();
    final List<Long> contas = new ArrayList<>();
    Representante representante;
    AcessoRepresentante ator;
    long area;
    String loginAdmin;

    @BeforeEach
    void prepararFixturesCommitadas() {
        representante = novaConta(true);
        ator = acesso.autenticar(representante.getLogin(), DEFINITIVA).orElseThrow();
        autenticar(ator, "REPRESENTANTE");
        area = jdbc.queryForObject("INSERT INTO areas_risco(nome, descricao_localizacao_limites) VALUES (?, 'Limites fictícios') RETURNING id",
                Long.class, "Área RF006 " + UUID.randomUUID());
    }

    @AfterEach
    void limparSomenteFixturesProprias() {
        SecurityContextHolder.clearContext();
        for (long residencia : residencias) {
            String autores = "SELECT id FROM moradores WHERE residencia_id = " + residencia;
            jdbc.update("DELETE FROM verificacoes_ocorrencia WHERE ocorrencia_id IN (SELECT id FROM ocorrencias WHERE morador_id IN (" + autores + "))");
            jdbc.update("DELETE FROM fotos_ocorrencia WHERE ocorrencia_id IN (SELECT id FROM ocorrencias WHERE morador_id IN (" + autores + "))");
            jdbc.update("DELETE FROM ocorrencias WHERE morador_id IN (" + autores + ")");
            jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id IN (" + autores + ")");
        }
        for (long residencia : residencias) jdbc.update("DELETE FROM moradores WHERE residencia_id = ?", residencia);
        for (long residencia : residencias) jdbc.update("DELETE FROM residencias WHERE id = ?", residencia);
        for (long id : contas) {
            jdbc.update("DELETE FROM verificacoes_ocorrencia WHERE representante_id = ? OR ocorrencia_id IN (SELECT id FROM ocorrencias WHERE representante_id = ?)", id, id);
            jdbc.update("DELETE FROM fotos_ocorrencia WHERE ocorrencia_id IN (SELECT id FROM ocorrencias WHERE representante_id = ?)", id);
            jdbc.update("DELETE FROM ocorrencias WHERE representante_id = ?", id);
            jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", id);
            jdbc.update("DELETE FROM representantes WHERE id = ?", id);
        }
        for (long extra : areasExtras) jdbc.update("DELETE FROM areas_risco WHERE id = ?", extra);
        jdbc.update("DELETE FROM areas_risco WHERE id = ?", area);
        if (loginAdmin != null) jdbc.update("DELETE FROM administradores WHERE login = ?", loginAdmin);
    }

    Representante novaConta(boolean definitiva) {
        var conta = representantes.cadastrar("Representante sintético RF006", "Entidade fictícia",
                "rf006-" + UUID.randomUUID(), TEMPORARIA);
        contas.add(conta.getId());
        if (definitiva) acesso.trocarSenha(acesso.autenticar(conta.getLogin(), TEMPORARIA).orElseThrow(), DEFINITIVA);
        return conta;
    }

    /** Morador fictício com senha definitiva, residência na área indicada; retorna o CPF. */
    String novoMorador(long areaResidencia) {
        long residencia = jdbc.queryForObject("INSERT INTO residencias(rua_logradouro, numero, bairro, municipio, area_id) VALUES ('Rua Fictícia', '1', 'Bairro', 'Município', ?) RETURNING id", Long.class, areaResidencia);
        residencias.add(residencia);
        var aleatorio = new Random();
        String cpf = String.format("%011d", Math.floorMod(aleatorio.nextLong(), 100_000_000_000L));
        jdbc.update("""
                INSERT INTO moradores(nome_completo, cpf, celular, residencia_id, canal_credenciais, preferencia_alertas, ativa,
                    senha_hash, senha_temporaria_expira_em, credenciais_pendentes, troca_senha_obrigatoria)
                VALUES ('Morador sintético RF006', ?, ?, ?, 'SMS', 'SMS', true, ?, now(), false, false)""",
                cpf, String.format("%011d", Math.floorMod(aleatorio.nextLong(), 100_000_000_000L)), residencia, encoder.encode(DEFINITIVA));
        return cpf;
    }

    AcessoMorador entrarComoMorador(String cpf) {
        var acesso = acessoMorador.autenticar(cpf, DEFINITIVA).orElseThrow();
        autenticar(acesso, "MORADOR");
        return acesso;
    }

    long novaArea() {
        long id = jdbc.queryForObject("INSERT INTO areas_risco(nome, descricao_localizacao_limites) VALUES (?, 'Limites fictícios') RETURNING id",
                Long.class, "Outra área RF006 " + UUID.randomUUID());
        areasExtras.add(id);
        return id;
    }

    List<String> acoesDoMorador(long id) {
        return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ? ORDER BY id", String.class, id);
    }

    Object administrador() {
        loginAdmin = "admin-rf006-" + UUID.randomUUID();
        administradores.criarPrimeiro(loginAdmin, DEFINITIVA);
        return administradores.autenticar(loginAdmin, DEFINITIVA).orElseThrow();
    }

    static void autenticar(Object principal, String autoridade) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(autoridade))));
    }

    DadosOcorrencia dados(UUID envio) {
        return new DadosOcorrencia(envio, TipoOcorrencia.DESASTRE, null, "Descrição fictícia", "Ponte fictícia", area, List.of(imagem("png")));
    }

    long quantidadeOcorrencias() {
        return contas.stream().mapToLong(id -> jdbc.queryForObject("SELECT count(*) FROM ocorrencias WHERE representante_id = ?", Long.class, id)).sum();
    }

    long quantidadeFotos() {
        return contas.stream().mapToLong(id -> jdbc.queryForObject("SELECT count(*) FROM fotos_ocorrencia f JOIN ocorrencias o ON o.id = f.ocorrencia_id WHERE o.representante_id = ?", Long.class, id)).sum();
    }

    List<String> acoes() {
        return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ? ORDER BY id", String.class, representante.getId());
    }

    static byte[] imagem(String formato) {
        try {
            var saida = new ByteArrayOutputStream();
            var imagem = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            imagem.setRGB(4, 5, 0x369abc);
            assertThat(ImageIO.write(imagem, formato, saida)).isTrue();
            return saida.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    // PNG real com chunk tEXt e CRC válido para testar o limite exato sem uma imagem enorme.
    static byte[] pngComTamanho(int tamanho) {
        byte[] original = imagem("png");
        int carga = tamanho - original.length - 12;
        byte[] texto = new byte[carga];
        Arrays.fill(texto, (byte) 'a');
        texto[1] = 0;
        var crc = new CRC32();
        byte[] tipo = {'t', 'E', 'X', 't'};
        crc.update(tipo);
        crc.update(texto);
        return ByteBuffer.allocate(tamanho).put(original, 0, original.length - 12)
                .putInt(carga).put(tipo).put(texto).putInt((int) crc.getValue())
                .put(original, original.length - 12, 12).array();
    }

    static byte[] heic() {
        // PNG sintético 32x32 criado com Python struct/zlib; convertido para HEIC pelo sips do macOS.
        return fixture("sintetica.heic");
    }

    static byte[] heif() {
        // Mesmo contêiner HEIC válido, major brand mif1 já compatível; sips reconhece HEIF 32x32.
        return fixture("sintetica.heif");
    }

    private static byte[] fixture(String nome) {
        try (var entrada = OcorrenciasFixtures.class.getResourceAsStream("/ocorrencias/" + nome)) {
            assertThat(entrada).as("fixture sintética real: %s", nome).isNotNull();
            return entrada.readAllBytes();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
