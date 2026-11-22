package br.edu.alertas.ocorrencias;

import java.util.List;
import java.util.UUID;

public record DadosOcorrencia(UUID envioId, TipoOcorrencia tipo, String especificacaoOutros,
        String descricao, String localizacao, Long areaId, List<byte[]> fotos) { }
