package br.edu.alertas.ocorrencias;

import jakarta.persistence.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

@Entity
@Table(name = "fotos_ocorrencia")
public class FotoOcorrencia {
    public static final int LIMITE_BYTES = 5 * 1024 * 1024;
    private static final byte[] ASSINATURA_PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
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
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) { return jpeg(bytes); }
        if (bytes.length >= 8 && Arrays.equals(bytes, 0, 8, ASSINATURA_PNG, 0, 8)) { return png(bytes); }
        throw erroFormato();
    }

    // Percorre chunks, CRC e o fluxo zlib completo, descartando a saída: sem montar a imagem em memória.
    private static String png(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        var inflater = new Inflater();
        byte[] descarte = new byte[8192];
        long esperado = 0, produzido = 0;
        boolean paleta = false, plte = false, idat = false, depoisIdat = false;
        try {
            for (int posicao = 8;;) {
                if (bytes.length - posicao < 12) { throw erroFormato(); }
                int tamanho = buffer.getInt(posicao);
                if (tamanho < 0 || tamanho > bytes.length - posicao - 12) { throw erroFormato(); }
                String tipo = texto(bytes, posicao + 4);
                var crc = new CRC32();
                crc.update(bytes, posicao + 4, tamanho + 4);
                if ((int) crc.getValue() != buffer.getInt(posicao + 8 + tamanho)) { throw erroFormato(); }
                int dados = posicao + 8;
                if (posicao == 8 != tipo.equals("IHDR")) { throw erroFormato(); }
                if (posicao == 8) {
                    if (tamanho != 13) { throw erroFormato(); }
                    esperado = bytesBrutosPng(bytes, dados);
                    paleta = bytes[dados + 9] == 3;
                }
                if (tipo.equals("PLTE")) { plte = true; }
                if (tipo.equals("IDAT")) {
                    if (depoisIdat || (paleta && !plte)) { throw erroFormato(); }
                    idat = true;
                    inflater.setInput(bytes, dados, tamanho);
                    while (!inflater.finished() && !inflater.needsInput()) {
                        int lidos = inflater.inflate(descarte);
                        if (lidos == 0 && inflater.needsDictionary()) { throw erroFormato(); }
                        produzido += lidos;
                        if (produzido > esperado) { throw erroFormato(); }
                    }
                } else if (idat) { depoisIdat = true; }
                posicao += tamanho + 12;
                if (tipo.equals("IEND")) {
                    if (tamanho != 0 || posicao != bytes.length) { throw erroFormato(); }
                    break;
                }
            }
            if (!inflater.finished() || produzido != esperado) { throw erroFormato(); }
            return "image/png";
        } catch (DataFormatException exception) {
            throw erroFormato();
        } finally { inflater.end(); }
    }

    private static long bytesBrutosPng(byte[] bytes, int ihdr) {
        var buffer = ByteBuffer.wrap(bytes);
        int largura = buffer.getInt(ihdr), altura = buffer.getInt(ihdr + 4);
        int profundidade = bytes[ihdr + 8], cor = bytes[ihdr + 9], entrelacamento = bytes[ihdr + 12];
        int canais = switch (cor) {
            case 0 -> Set.of(1, 2, 4, 8, 16).contains(profundidade) ? 1 : 0;
            case 3 -> Set.of(1, 2, 4, 8).contains(profundidade) ? 1 : 0;
            case 2, 4, 6 -> Set.of(8, 16).contains(profundidade) ? (cor == 2 ? 3 : cor == 4 ? 2 : 4) : 0;
            default -> 0;
        };
        if (largura <= 0 || altura <= 0 || canais == 0 || bytes[ihdr + 10] != 0 || bytes[ihdr + 11] != 0
                || (entrelacamento != 0 && entrelacamento != 1)) { throw erroFormato(); }
        long bits = (long) canais * profundidade;
        if (entrelacamento == 0) { return altura * (1 + (largura * bits + 7) / 8); }
        long total = 0;
        int[][] passos = {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4}, {0, 2, 2, 4}, {1, 0, 2, 2}, {0, 1, 1, 2}};
        for (int[] p : passos) {
            long colunas = largura > p[0] ? (largura - p[0] + p[2] - 1) / p[2] : 0;
            long linhas = altura > p[1] ? (altura - p[1] + p[3] - 1) / p[3] : 0;
            if (colunas > 0) { total += linhas * (1 + (colunas * bits + 7) / 8); }
        }
        return total;
    }

    // Exige SOF com dimensões, SOS com dados de entropia e EOI; não decodifica os coeficientes.
    private static String jpeg(byte[] bytes) {
        boolean quadro = false, varredura = false;
        for (int posicao = 2;;) {
            if (bytes.length - posicao < 2 || (bytes[posicao] & 0xff) != 0xff) { throw erroFormato(); }
            while (bytes.length - posicao >= 2 && (bytes[posicao + 1] & 0xff) == 0xff) { posicao++; }
            if (bytes.length - posicao < 2) { throw erroFormato(); }
            int marcador = bytes[posicao + 1] & 0xff;
            posicao += 2;
            if (marcador == 0xd9) {
                if (!varredura) { throw erroFormato(); }
                return "image/jpeg"; // Bytes após o EOI (ex.: imagens MPF) são preservados.
            }
            if (marcador == 0x01 || (marcador >= 0xd0 && marcador <= 0xd7)) { continue; }
            if (marcador == 0x00 || marcador == 0xd8 || bytes.length - posicao < 2) { throw erroFormato(); }
            int tamanho = u16(bytes, posicao);
            if (tamanho < 2 || tamanho > bytes.length - posicao) { throw erroFormato(); }
            if (marcador >= 0xc0 && marcador <= 0xcf && marcador != 0xc4 && marcador != 0xc8 && marcador != 0xcc) {
                if (tamanho < 8 || u16(bytes, posicao + 3) == 0 || u16(bytes, posicao + 5) == 0) { throw erroFormato(); }
                quadro = true;
            }
            posicao += tamanho;
            if (marcador == 0xda) {
                if (!quadro) { throw erroFormato(); }
                int inicio = posicao;
                while (bytes.length - posicao >= 2) {
                    int atual = bytes[posicao] & 0xff, proximo = bytes[posicao + 1] & 0xff;
                    if (atual != 0xff || proximo == 0xff) { posicao++; continue; }
                    if (proximo == 0x00 || (proximo >= 0xd0 && proximo <= 0xd7)) { posicao += 2; continue; }
                    break;
                }
                if (posicao == inicio || bytes.length - posicao < 2) { throw erroFormato(); }
                varredura = true;
            }
        }
    }

    private static int u16(byte[] bytes, int inicio) { return (bytes[inicio] & 0xff) << 8 | (bytes[inicio + 1] & 0xff); }

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
                if (!tipo.equals("ftyp") || tamanho < cabecalho + 8 || tamanho % 4 != 0) { throw erroFormato(); }
                var marcas = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");
                for (int i = cabecalho; i + 4 <= tamanho; i += 4) {
                    if (i == cabecalho + 4) { continue; }
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
