package br.edu.alertas.ocorrencias;

import jakarta.persistence.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

@Entity
@Table(name = "fotos_ocorrencia")
public class FotoOcorrencia {
    public static final int LIMITE_BYTES = 5 * 1024 * 1024;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false) private Ocorrencia ocorrencia;
    @Column(nullable = false) private int ordem;
    @Column(name = "tipo_midia", nullable = false, columnDefinition = "text") private String tipoMidia;
    @Column(nullable = false, columnDefinition = "bytea") private byte[] conteudo;

    protected FotoOcorrencia() { }

    FotoOcorrencia(Ocorrencia ocorrencia, int ordem, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > LIMITE_BYTES) {
            throw erro("Escolha fotos não vazias de até 5 MB. Reduza a foto antes de tentar novamente.");
        }
        this.conteudo = bytes.clone();
        this.tipoMidia = identificar(this.conteudo);
        this.ocorrencia = ocorrencia;
        this.ordem = ordem;
    }

    private static String identificar(byte[] bytes) {
        // HEIF é preservado sem decodificação nativa; validamos marcas e caixas do contêiner.
        if (bytes.length >= 16 && texto(bytes, 4).equals("ftyp")) { return heif(bytes); }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw erroFormato(); }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String formato = reader.getFormatName();
                if ((!formato.equalsIgnoreCase("JPEG") && !formato.equalsIgnoreCase("PNG"))
                        || reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) { throw erroFormato(); }
                return formato.equalsIgnoreCase("PNG") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        } catch (IOException | IndexOutOfBoundsException exception) { throw erroFormato(); }
    }

    private static String heif(byte[] bytes) {
        boolean marca = false, heic = false, meta = false, dados = false;
        int posicao = 0;
        while (posicao < bytes.length) {
            if (bytes.length - posicao < 8) { throw erroFormato(); }
            long tamanho = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, posicao, 4).getInt());
            String tipo = texto(bytes, posicao + 4);
            int cabecalho = 8;
            if (tamanho == 1) {
                if (bytes.length - posicao < 16) { throw erroFormato(); }
                tamanho = ByteBuffer.wrap(bytes, posicao + 8, 8).getLong();
                cabecalho = 16;
            } else if (tamanho == 0) { tamanho = bytes.length - posicao; }
            if (tamanho < cabecalho || tamanho > bytes.length - posicao) { throw erroFormato(); }
            if (posicao == 0) {
                if (!tipo.equals("ftyp") || tamanho < 16 || tamanho % 4 != 0) { throw erroFormato(); }
                var marcas = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");
                for (int i = 8; i + 4 <= tamanho; i += 4) {
                    if (i == 12) { continue; }
                    if (Set.of("avif", "avis").contains(texto(bytes, i))) { throw erroFormato(); }
                    String identificador = texto(bytes, i);
                    marca |= marcas.contains(identificador);
                    heic |= identificador.startsWith("he") && marcas.contains(identificador);
                }
            }
            if (tipo.equals("meta")) {
                dados |= validarMeta(bytes, posicao + cabecalho + 4, posicao + (int) tamanho);
                meta = true;
            }
            if (tipo.equals("mdat") && tamanho > cabecalho) { dados = true; }
            posicao += (int) tamanho;
        }
        if (!marca || !meta || !dados) { throw erroFormato(); }
        return heic ? "image/heic" : "image/heif";
    }

    private static boolean validarMeta(byte[] bytes, int inicio, int fim) {
        int campos = 0;
        boolean dados = false;
        for (int posicao = inicio; posicao < fim;) {
            if (fim - posicao < 8) { throw erroFormato(); }
            long tamanho = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, posicao, 4).getInt());
            int cabecalho = 8;
            if (tamanho == 1) {
                if (fim - posicao < 16) { throw erroFormato(); }
                tamanho = ByteBuffer.wrap(bytes, posicao + 8, 8).getLong();
                cabecalho = 16;
            } else if (tamanho == 0) { tamanho = fim - posicao; }
            if (tamanho < cabecalho || tamanho > fim - posicao) { throw erroFormato(); }
            switch (texto(bytes, posicao + 4)) {
                case "hdlr" -> {
                    if (tamanho < cabecalho + 12 || !texto(bytes, posicao + cabecalho + 8).equals("pict")) { throw erroFormato(); }
                    campos |= 1;
                }
                case "pitm" -> { if (tamanho < cabecalho + 6) { throw erroFormato(); } campos |= 2; }
                case "iloc" -> { if (tamanho < cabecalho + 6) { throw erroFormato(); } campos |= 4; }
                case "iinf" -> { if (tamanho < cabecalho + 6) { throw erroFormato(); } campos |= 8; }
                case "iprp" -> { if (tamanho <= cabecalho) { throw erroFormato(); } campos |= 16; }
                case "idat" -> dados |= tamanho > cabecalho;
                default -> { }
            }
            posicao += (int) tamanho;
        }
        if (campos != 31) { throw erroFormato(); }
        return dados;
    }

    private static String texto(byte[] bytes, int inicio) {
        return new String(bytes, inicio, 4, StandardCharsets.US_ASCII);
    }
    private static ErroOcorrencia erroFormato() { return erro("Escolha uma foto válida em JPG/JPEG, PNG, HEIC ou HEIF."); }
    private static ErroOcorrencia erro(String mensagem) { return new ErroOcorrencia("fotos", mensagem); }
    public int getOrdem() { return ordem; }
    public String getTipoMidia() { return tipoMidia; }
    public byte[] getConteudo() { return conteudo.clone(); }
}
