/* Rascunhos guardam apenas os campos do relato e Blobs, nunca credenciais ou CSRF. */
(function () {
  'use strict';

  const LIMITE_FOTO = 5 * 1024 * 1024;
  const CAMPOS = ['tipo', 'especificacaoOutros', 'descricao', 'localizacao', 'areaId'];

  function abrirBanco(indexedDB) {
    return new Promise((resolve, reject) => {
      const pedido = indexedDB.open('alertas-ocorrencias', 1);
      pedido.onupgradeneeded = () => pedido.result.createObjectStore('rascunhos', { keyPath: 'chave' });
      pedido.onsuccess = () => resolve(pedido.result);
      pedido.onerror = () => reject(pedido.error);
      pedido.onblocked = () => reject(new Error('Armazenamento ocupado em outra aba.'));
    });
  }

  function guardar(banco, rascunho) {
    return new Promise((resolve, reject) => {
      const tx = banco.transaction('rascunhos', 'readwrite');
      tx.objectStore('rascunhos').put(rascunho);
      tx.oncomplete = () => resolve();
      tx.onabort = tx.onerror = () => reject(tx.error || new Error('Falha ao salvar rascunho.'));
    });
  }

  function recuperar(banco, chave) {
    return new Promise((resolve, reject) => {
      const tx = banco.transaction('rascunhos', 'readonly');
      const pedido = tx.objectStore('rascunhos').get(chave);
      tx.oncomplete = () => resolve(pedido.result);
      tx.onabort = tx.onerror = () => reject(tx.error);
    });
  }

  // Comparação e remoção na mesma transação: outro envio nunca é apagado.
  function removerConfirmado(banco, chave, envioId) {
    return new Promise((resolve, reject) => {
      const tx = banco.transaction('rascunhos', 'readwrite');
      const store = tx.objectStore('rascunhos');
      const pedido = store.get(chave);
      let removido = false;
      pedido.onsuccess = () => {
        if (pedido.result && pedido.result.envioId === envioId && pedido.result.tentativaIncerta === true) {
          store.delete(chave);
          removido = true;
        }
      };
      tx.oncomplete = () => resolve(removido);
      tx.onabort = tx.onerror = () => reject(tx.error);
    });
  }

  async function tipoImagem(blob) {
    const bytes = new Uint8Array(await blob.slice(0, 16).arrayBuffer());
    if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
    if ([137, 80, 78, 71, 13, 10, 26, 10].every((b, i) => bytes[i] === b)) return 'image/png';
    const texto = (inicio) => String.fromCharCode(...bytes.slice(inicio, inicio + 4));
    const erroFormato = 'Formato não aceito. Selecione uma foto JPG, JPEG, PNG, HEIC ou HEIF.';
    if (texto(4) === 'ftyp') {
      const view = new DataView(bytes.buffer);
      let tamanho = view.getUint32(0);
      let cabecalho = 8;
      if (tamanho === 1) {
        if (bytes.length < 16) throw new Error(erroFormato);
        const estendido = view.getBigUint64(8);
        if (estendido > BigInt(blob.size)) throw new Error(erroFormato);
        tamanho = Number(estendido);
        cabecalho = 16;
      } else if (tamanho === 0) tamanho = blob.size;
      if (tamanho < cabecalho + 8 || tamanho % 4 !== 0 || tamanho > blob.size) throw new Error(erroFormato);
      const marcas = new Uint8Array(await blob.slice(cabecalho, tamanho).arrayBuffer());
      let heic = false;
      let heif = false;
      for (let i = 0; i + 4 <= marcas.length; i += 4) {
        if (i === 4) continue; // versão secundária, não uma marca de formato
        const marca = String.fromCharCode(...marcas.slice(i, i + 4));
        if (['avif', 'avis'].includes(marca)) throw new Error(erroFormato);
        heic ||= ['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'hevm', 'hevs'].includes(marca);
        heif ||= ['mif1', 'msf1'].includes(marca);
      }
      if (heic) return 'image/heic';
      if (heif) return 'image/heif';
    }
    throw new Error(erroFormato);
  }

  function carregarImagem(blob) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(blob);
      const imagem = new Image();
      imagem.onload = () => { URL.revokeObjectURL(url); resolve(imagem); };
      imagem.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Não foi possível ler a foto.')); };
      imagem.src = url;
    });
  }

  async function reduzirFoto(blob, carregar = carregarImagem, criarCanvas = () => document.createElement('canvas')) {
    try {
      const imagem = await carregar(blob);
      let largura = imagem.naturalWidth;
      let altura = imagem.naturalHeight;
      if (!largura || !altura) throw new Error('Imagem sem dimensões.');
      const canvas = criarCanvas();
      for (;;) {
        canvas.width = largura;
        canvas.height = altura;
        const contexto = canvas.getContext('2d');
        if (!contexto) throw new Error('Canvas indisponível.');
        contexto.fillStyle = '#fff';
        contexto.fillRect(0, 0, largura, altura);
        contexto.drawImage(imagem, 0, 0, largura, altura);
        for (const qualidade of [0.9, 0.75, 0.6, 0.45]) {
          const reduzida = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', qualidade));
          if (!reduzida || reduzida.type !== 'image/jpeg' || !reduzida.size) throw new Error('Falha na conversão.');
          if (reduzida.size <= LIMITE_FOTO) return reduzida;
        }
        if (largura === 1 && altura === 1) throw new Error('Redução insuficiente.');
        largura = Math.max(1, Math.floor(largura * 0.75));
        altura = Math.max(1, Math.floor(altura * 0.75));
      }
    } catch (_) {
      throw new Error('Não foi possível reduzir a foto para 5 MB. Selecione outra foto.');
    }
  }

  async function prepararFotos(arquivos, reduzir = reduzirFoto) {
    if (arquivos.length < 1 || arquivos.length > 4) throw new Error('Selecione de uma a quatro fotos.');
    const fotos = [];
    for (const arquivo of arquivos) {
      const tipo = await tipoImagem(arquivo);
      const blob = arquivo.size > LIMITE_FOTO ? await reduzir(arquivo) : arquivo.slice(0, arquivo.size, tipo);
      if (!blob || !blob.size || blob.size > LIMITE_FOTO) throw new Error('A redução falhou. Selecione outra foto.');
      fotos.push({ nome: arquivo.name || 'Foto', blob });
    }
    return fotos;
  }

  function validar(dados, fotos) {
    const erros = {};
    if (!['DESASTRE', 'OBRA', 'BARREIRA_CONTENCAO', 'OUTROS'].includes(dados.tipo)) erros.tipo = 'Selecione o tipo da ocorrência.';
    if (dados.tipo === 'OUTROS' && !dados.especificacaoOutros.trim()) erros.especificacaoOutros = 'Especifique o outro tipo de ocorrência.';
    if (!dados.descricao.trim()) erros.descricao = 'Descreva o que aconteceu.';
    if (!dados.localizacao.trim()) erros.localizacao = 'Digite o endereço ou um ponto de referência.';
    if (!dados.areaId) erros.areaId = 'Selecione uma área de risco cadastrada.';
    if (fotos.length < 1 || fotos.length > 4) erros.fotos = 'Selecione de uma a quatro fotos.';
    return erros;
  }

  async function iniciarFormulario(form) {
    const elemento = id => document.getElementById(id);
    const status = elemento('rascunho-status');
    const geral = elemento('envio-erro');
    const fieldset = elemento('dados-ocorrencia');
    const botao = elemento('enviar-ocorrencia');
    const chave = form.dataset.rascunhoChave;
    let banco;
    let envioId;
    let fotos = [];
    let fotosInvalidas = false;
    let preparando = false;
    let enviando = false;
    let tentativaIncerta = false;
    let dadosTentados = null;
    let fila = Promise.resolve();
    const areaDisponivel = valor => Array.from(elemento('areaId').options).some(opcao => opcao.value === valor && valor && !opcao.disabled);
    const semAreas = !Array.from(elemento('areaId').options).some(opcao => opcao.value && !opcao.disabled);

    const mensagemIncerta = 'O envio não foi concluído. A tentativa pode já ter sido registrada. Revise os dados e as fotos e reenvie manualmente para recuperar o protocolo antes de editar.';
    const dados = () => tentativaIncerta ? dadosTentados : Object.fromEntries(CAMPOS.map(campo => [campo, elemento(campo).value]));
    const atualizarBloqueio = () => {
      for (const campo of [...CAMPOS, 'fotos']) elemento(campo).disabled = tentativaIncerta;
      botao.disabled = semAreas && !tentativaIncerta;
      if (tentativaIncerta) for (const campo of CAMPOS) elemento(campo).value = dadosTentados[campo];
    };
    const erroGeral = mensagem => { geral.textContent = mensagem; geral.hidden = false; };
    const erroCampo = (campo, mensagem) => {
      const alvo = elemento('erro-' + campo);
      if (!alvo) { erroGeral(mensagem); return; }
      alvo.textContent = mensagem;
      if (elemento(campo)) elemento(campo).setAttribute('aria-invalid', String(Boolean(mensagem)));
    };
    const atualizarOutros = () => {
      const outros = elemento('tipo').value === 'OUTROS';
      elemento('campo-outros').hidden = !outros;
      elemento('especificacaoOutros').required = outros;
    };
    const mostrarFotos = () => {
      elemento('fotos-selecionadas').replaceChildren(...fotos.map((foto, i) => {
        const item = document.createElement('li');
        item.textContent = `${i + 1}. ${foto.nome} (${(foto.blob.size / (1024 * 1024)).toFixed(2)} MB)`;
        return item;
      }));
    };
    const salvar = () => {
      const rascunho = { chave, envioId, dados: dados(), fotos, tentativaIncerta };
      fila = fila.catch(() => {}).then(() => guardar(banco, rascunho));
      fila.then(() => {
        status.textContent = tentativaIncerta ? mensagemIncerta : 'Rascunho salvo neste dispositivo. Revise e envie quando estiver pronto.';
      }, () => {
        status.textContent = 'Não foi possível salvar neste dispositivo. Mantenha esta página aberta e tente novamente; os dados podem se perder ao fechar.';
      });
      return fila;
    };

    try {
      if (!chave) throw new Error('Conta sem chave de rascunho.');
      banco = await abrirBanco(window.indexedDB);
      const anterior = await recuperar(banco, chave);
      envioId = anterior ? anterior.envioId : window.crypto.randomUUID();
      if (anterior) {
        const areaAnterior = anterior.dados.areaId;
        if (areaAnterior && !areaDisponivel(areaAnterior)) {
          const opcao = document.createElement('option');
          opcao.value = areaAnterior;
          opcao.disabled = true;
          opcao.textContent = 'Área indisponível — selecione outra área cadastrada';
          elemento('areaId').append(opcao);
        }
        for (const campo of CAMPOS) elemento(campo).value = anterior.dados[campo] || '';
        fotos = anterior.fotos;
        tentativaIncerta = anterior.tentativaIncerta === true;
        dadosTentados = tentativaIncerta ? anterior.dados : null;
        if (areaAnterior && !areaDisponivel(areaAnterior)) erroCampo('areaId', 'A área do rascunho não está mais disponível. Selecione outra área cadastrada.');
      }
      elemento('envioId').value = envioId;
      atualizarOutros();
      mostrarFotos();
      await salvar();
      status.textContent = tentativaIncerta ? mensagemIncerta : anterior
        ? 'Rascunho recuperado com as fotos. Revise todos os campos antes de enviar manualmente.'
        : 'Rascunho salvo neste dispositivo. O envio acontece somente quando você confirmar.';
      fieldset.disabled = false;
      atualizarBloqueio();
    } catch (_) {
      status.textContent = 'Não foi possível abrir ou salvar o rascunho neste dispositivo. Verifique o armazenamento do navegador e recarregue a página para tentar novamente.';
      return;
    }

    form.addEventListener('input', event => {
      if (tentativaIncerta) { atualizarBloqueio(); return; }
      if (event.target.id === 'revisao' || event.target.id === 'fotos') return;
      elemento('revisao').checked = false;
      erroCampo(event.target.id, '');
      atualizarOutros();
      salvar();
    });

    elemento('fotos').addEventListener('change', async event => {
      if (tentativaIncerta) return;
      preparando = true;
      botao.disabled = true;
      elemento('revisao').checked = false;
      status.textContent = 'Preparando as fotos. Aguarde antes de enviar.';
      // Impede duas seleções concorrentes de substituírem o mesmo rascunho.
      event.target.disabled = true;
      try {
        const novas = await prepararFotos(Array.from(event.target.files));
        fotos = novas;
        fotosInvalidas = false;
        erroCampo('fotos', '');
        mostrarFotos();
        await salvar().catch(() => {}); // A mensagem de falha local é exibida por salvar().
      } catch (erro) {
        fotosInvalidas = true;
        erroCampo('fotos', erro.message + ' As fotos anteriores do rascunho foram mantidas.');
        status.textContent = 'A seleção não foi concluída. Escolha novamente as fotos antes de enviar.';
      } finally {
        preparando = false;
        event.target.value = '';
        event.target.disabled = false;
        botao.disabled = semAreas;
      }
    });

    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (enviando || preparando) return;
      geral.hidden = true;
      for (const campo of [...CAMPOS, 'fotos', 'revisao']) erroCampo(campo, '');
      // Um replay precisa alcançar o servidor mesmo se a área deixou de existir.
      const erros = tentativaIncerta ? {} : validar(dados(), fotos);
      if (!tentativaIncerta && !areaDisponivel(dados().areaId)) erros.areaId = 'Selecione uma área de risco disponível.';
      if (!tentativaIncerta && fotosInvalidas) erros.fotos = 'Selecione novamente de uma a quatro fotos válidas.';
      if (!elemento('revisao').checked) erros.revisao = 'Revise os dados e as fotos e marque a confirmação para enviar.';
      if (Object.keys(erros).length) {
        for (const [campo, mensagem] of Object.entries(erros)) erroCampo(campo, mensagem);
        elemento(Object.keys(erros)[0]).focus();
        return;
      }
      enviando = true;
      const jaIncerta = tentativaIncerta;
      dadosTentados = dados();
      tentativaIncerta = true;
      atualizarBloqueio();
      // Campos bloqueados não entram no FormData; CSRF continua vindo do formulário.
      const multipart = new FormData(form);
      for (const campo of CAMPOS) multipart.set(campo, dadosTentados[campo]);
      multipart.set('envioId', envioId);
      multipart.delete('fotos');
      for (const [i, foto] of fotos.entries()) multipart.append('fotos', foto.blob, `foto-${i + 1}`);
      fieldset.disabled = true;
      try {
        try {
          await salvar(); // Nunca tentar a rede sem antes persistir o envioId e os Blobs.
        } catch (_) {
          if (!jaIncerta) { tentativaIncerta = false; dadosTentados = null; }
          throw new Error('Não foi possível salvar o rascunho neste dispositivo. O envio não foi iniciado. Mantenha esta página aberta e tente novamente.');
        }
        status.textContent = 'Enviando ocorrência. Aguarde a apresentação do protocolo.';
        const resposta = await window.fetch(form.action, {
          method: 'POST', body: multipart, credentials: 'same-origin', headers: { Accept: 'application/json' }
        });
        if (resposta.status === 403 || resposta.status === 401 || resposta.redirected) {
          throw new Error('O acesso não foi autorizado ou sua sessão venceu. Entre novamente na mesma conta e retome o rascunho.');
        }
        if (!(resposta.headers.get('content-type') || '').includes('application/json')) {
          throw new Error('O envio não foi confirmado. Os dados e as fotos foram preservados. Tente novamente.');
        }
        const retorno = await resposta.json();
        if (!resposta.ok) {
          if (resposta.status === 400 && retorno.campo && typeof retorno.erro === 'string') {
            const tentativa = dadosTentados;
            tentativaIncerta = false;
            dadosTentados = null;
            try {
              await salvar();
            } catch (_) {
              tentativaIncerta = true;
              dadosTentados = tentativa;
              throw new Error('Não foi possível salvar a liberação para corrigir os dados. Mantenha esta página aberta e tente novamente.');
            }
            erroCampo(retorno.campo, retorno.erro);
            if (elemento(retorno.campo)?.type !== 'hidden') elemento(retorno.campo)?.focus();
            status.textContent = 'O envio não foi concluído. Corrija o campo indicado, revise e tente novamente.';
            return;
          }
          throw new Error('O envio não foi confirmado. Revise e tente novamente com este rascunho.');
        }
        if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(retorno.protocolo)
            || retorno.detalheUrl !== '/ocorrencias/' + retorno.protocolo) {
          throw new Error('A confirmação não pôde ser apresentada. Tente novamente com este rascunho para recuperar o protocolo.');
        }
        window.location.assign(retorno.detalheUrl);
      } catch (erro) {
        status.textContent = tentativaIncerta ? mensagemIncerta : 'O envio não foi concluído. Revise os dados e as fotos para reenviar manualmente.';
        erroGeral(erro instanceof TypeError
          ? 'A conexão falhou e o envio não foi confirmado. Os dados e as fotos foram preservados. Quando a conexão voltar, revise e tente novamente.'
          : erro.message || 'Não foi possível concluir o envio. Mantenha esta página aberta e tente novamente.');
        geral.focus();
      } finally {
        enviando = false;
        fieldset.disabled = false;
        atualizarBloqueio();
        elemento('revisao').checked = false;
      }
    });
  }

  async function iniciarDetalhe(detalhe) {
    const horario = document.getElementById('horario-registro');
    if (horario) {
      const data = new Date(horario.dateTime);
      if (!Number.isNaN(data.getTime())) horario.textContent = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'long' }).format(data);
    }
    const protocolo = document.getElementById('protocolo');
    if (!protocolo || !protocolo.textContent.trim() || !detalhe.dataset.rascunhoChave || !detalhe.dataset.envioId) return;
    // O defer já colocou o protocolo no DOM; aguarda também sua primeira apresentação.
    await new Promise(resolve => window.requestAnimationFrame(() => window.requestAnimationFrame(resolve)));
    let banco;
    try {
      banco = await abrirBanco(window.indexedDB);
      await removerConfirmado(banco, detalhe.dataset.rascunhoChave, detalhe.dataset.envioId);
    } catch (_) {
      document.getElementById('rascunho-status').textContent = 'O registro foi confirmado, mas não foi possível limpar o rascunho deste dispositivo. Uma nova tentativa do mesmo envio recupera este protocolo.';
    } finally {
      banco?.close();
    }
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { LIMITE_FOTO, abrirBanco, guardar, recuperar, removerConfirmado, tipoImagem, reduzirFoto, prepararFotos, validar, iniciarFormulario, iniciarDetalhe };
  }
  if (typeof document !== 'undefined') {
    const form = document.getElementById('ocorrencia-formulario');
    const detalhe = document.getElementById('ocorrencia-detalhe');
    if (form) iniciarFormulario(form);
    if (detalhe) iniciarDetalhe(detalhe);
  }
}());
