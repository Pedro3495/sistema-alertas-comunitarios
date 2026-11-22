package br.edu.alertas.ocorrencias;

import java.time.Instant;
import java.util.UUID;

/** Linha da consulta (RF008): sem autor nem bytes das fotos. */
public record ResumoOcorrencia(UUID protocolo, Instant registradaEm, TipoOcorrencia tipo, SituacaoOcorrencia situacao,
        String areaNome, String localizacao) { }
