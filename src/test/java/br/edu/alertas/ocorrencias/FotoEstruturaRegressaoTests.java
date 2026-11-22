package br.edu.alertas.ocorrencias;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SpringBootTest
class FotoEstruturaRegressaoTests extends OcorrenciasFixtures {
    static Stream<Arguments> arquivosIncompletos() {
        byte[] png = imagem("png");
        byte[] jpeg = imagem("jpeg");
        int idat = chunk(png, "IDAT");
        int tamanhoIdat = ByteBuffer.wrap(png, idat, 4).getInt();
        byte[] crcInvalido = png.clone();
        crcInvalido[idat + 8 + tamanhoIdat] ^= 1;
        byte[] ihdrForjado = Arrays.copyOf(png, 33);
        ihdrForjado[16] ^= 1;
        byte[] jpegSemEntropia = Arrays.copyOf(jpeg, fimDoSegmento(jpeg, 0xda) + 2);
        jpegSemEntropia[jpegSemEntropia.length - 2] = (byte) 0xff;
        jpegSemEntropia[jpegSemEntropia.length - 1] = (byte) 0xd9;
        return Stream.of(
                Arguments.of("PNG somente assinatura e IHDR válido (33 bytes)", Arrays.copyOf(png, 33)),
                Arguments.of("PNG cortado dentro do IDAT", Arrays.copyOf(png, idat + 8 + tamanhoIdat / 2)),
                Arguments.of("PNG com CRC do IDAT inválido", crcInvalido),
                Arguments.of("PNG com zlib truncado, CRC e IEND válidos", pngComIdat(png, Arrays.copyOfRange(png, idat + 8, idat + 8 + tamanhoIdat / 2))),
                Arguments.of("PNG com scanlines insuficientes e zlib completo", pngComIdat(png, scanlinesIncompletas())),
                Arguments.of("PNG com cabeçalho IHDR falsificado", ihdrForjado),
                Arguments.of("JPEG cortado após dimensões SOF", Arrays.copyOf(jpeg, fimDoSegmento(jpeg, 0xc0))),
                Arguments.of("JPEG sem marcador EOI", Arrays.copyOf(jpeg, jpeg.length - 2)),
                Arguments.of("JPEG com cabeçalhos completos e EOI, sem entropia", jpegSemEntropia),
                Arguments.of("JPEG com assinatura falsa sem segmentos", new byte[]{(byte) 0xff, (byte) 0xd8, 32, 32, (byte) 0xff, (byte) 0xd9}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("arquivosIncompletos")
    void recusaEstruturaIncompletaSemPersistirOuAuditar(String cenario, byte[] foto) {
        var dados = new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null,
                "Descrição sintética", "Local sintético", area, List.of(foto));
        assertSoftly(soft -> {
            soft.assertThatThrownBy(() -> service.registrar(dados)).as(cenario)
                    .isInstanceOfSatisfying(ErroOcorrencia.class, erro -> {
                        assertThat(erro.getCampo()).isEqualTo("fotos");
                        assertThat(erro.getMessage()).isNotBlank();
                    });
            soft.assertThat(quantidadeOcorrencias()).as("ocorrências: %s", cenario).isZero();
            soft.assertThat(quantidadeFotos()).as("fotos: %s", cenario).isZero();
            soft.assertThat(acoes()).as("auditoria: %s", cenario).isEmpty();
        });
    }

    static Stream<Arguments> imagensCompletas() {
        return Stream.of(Arguments.of("PNG real", imagem("png"), "image/png"),
                Arguments.of("JPEG real", imagem("jpeg"), "image/jpeg"),
                Arguments.of("PNG real interlaçado Adam7", progressiva("png"), "image/png"),
                Arguments.of("JPEG real progressivo com várias varreduras", progressiva("jpeg"), "image/jpeg"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("imagensCompletas")
    void preservaAceitacaoEBytesDeImagensCompletas(String cenario, byte[] foto, String mime) throws IOException {
        // Confere a fixture completa por decodificação, independentemente do validador de produção.
        var decodificada = ImageIO.read(new ByteArrayInputStream(foto));
        assertThat(decodificada).as(cenario).isNotNull();
        assertThat(decodificada.getWidth()).isEqualTo(32);
        assertThat(decodificada.getHeight()).isEqualTo(32);
        var salvo = service.registrar(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null,
                "Descrição sintética", "Local sintético", area, List.of(foto)));
        var evidencia = service.foto(salvo.getProtocolo(), 1);
        assertThat(evidencia.getConteudo()).containsExactly(foto);
        assertThat(evidencia.getTipoMidia()).isEqualTo(mime);
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(1);
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + salvo.getProtocolo());
    }

    private static byte[] progressiva(String formato) {
        var writer = ImageIO.getImageWritersByFormatName(formato).next();
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(output);
                var parametros = writer.getDefaultWriteParam();
                assertThat(parametros.canWriteProgressive()).isTrue();
                parametros.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
                var imagem = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
                imagem.setRGB(4, 5, 0x369abc);
                writer.write(null, new IIOImage(imagem, null, null), parametros);
            }
            byte[] foto = bytes.toByteArray();
            if (formato.equals("png")) assertThat(foto[28]).as("IHDR: método de interlace Adam7").isEqualTo((byte) 1);
            else assertThat(fimDoSegmento(foto, 0xc2)).as("SOF2 progressivo").isPositive();
            return foto;
        } catch (IOException e) { throw new UncheckedIOException(e); }
        finally { writer.dispose(); }
    }

    private static int chunk(byte[] png, String nome) {
        for (int offset = 8; offset + 12 <= png.length;) {
            int tamanho = ByteBuffer.wrap(png, offset, 4).getInt();
            if (new String(png, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals(nome)) return offset;
            offset += tamanho + 12;
        }
        throw new AssertionError("Fixture não contém chunk " + nome);
    }

    private static byte[] pngComIdat(byte[] original, byte[] comprimido) {
        int inicio = chunk(original, "IDAT");
        int fim = inicio + ByteBuffer.wrap(original, inicio, 4).getInt() + 12;
        var crc = new CRC32();
        byte[] tipo = {'I', 'D', 'A', 'T'};
        crc.update(tipo);
        crc.update(comprimido);
        return ByteBuffer.allocate(inicio + comprimido.length + 12 + original.length - fim)
                .put(original, 0, inicio).putInt(comprimido.length).put(tipo).put(comprimido).putInt((int) crc.getValue())
                .put(original, fim, original.length - fim).array();
    }

    private static byte[] scanlinesIncompletas() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var zlib = new DeflaterOutputStream(bytes)) {
                // IHDR declara 32 linhas RGB de 32 pixels; entrega somente 16 linhas válidas.
                zlib.write(new byte[16 * (1 + 32 * 3)]);
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static int fimDoSegmento(byte[] jpeg, int procurado) {
        int offset = 2;
        while (offset + 4 <= jpeg.length) {
            assertThat(jpeg[offset] & 0xff).isEqualTo(0xff);
            int marcador = jpeg[offset + 1] & 0xff;
            int tamanho = (jpeg[offset + 2] & 0xff) << 8 | (jpeg[offset + 3] & 0xff);
            if (marcador == procurado) return offset + 2 + tamanho;
            offset += 2 + tamanho;
        }
        throw new AssertionError("Fixture JPEG não contém segmento " + procurado);
    }
}
