package net.guilhermejr.sistema.nfeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.nfeservice.api.response.NFEResponse;
import net.guilhermejr.sistema.nfeservice.api.response.ProdutoResponse;
import net.guilhermejr.sistema.nfeservice.component.NavegadorComponent;
import net.guilhermejr.sistema.nfeservice.exception.ExceptionDefault;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
@Service
public class NFEService {

    private final NavegadorComponent navegador;

    @Value("${sistema.nfe.url:http://nfe.sefaz.ba.gov.br/servicos/nfce/qrcode.aspx?p=}")
    private String urlBase;

    // --- Buscar -------------------------------------------------------------
    public NFEResponse buscar(String nfe) {

        String url = urlBase + nfe;

        try {

            Connection sessao = navegador.sessao();
            Document principal = navegador.abrir(sessao, url);

            // --- Quando a SEFAZ recusa a nota, ela responde com uma mensagem ---
            Element informacao = principal.selectFirst("#lblInformacao");
            if (informacao != null) {
                log.info("NFE não retornou dados: {}", informacao.text());
                return NFEResponse.builder()
                        .url(url)
                        .retornou(false)
                        .mensagem(informacao.html())
                        .build();
            }

            // --- As informações estão espalhadas em três abas ---
            Document abas = navegador.clicar(sessao, principal, "Visualizar em Abas");
            Document emitente = navegador.clicar(sessao, abas, "btn_aba_emitente");
            Document produtos = navegador.clicar(sessao, abas, "btn_aba_produtos");

            return NFEResponse.builder()
                    .url(url)
                    .retornou(true)
                    .data(data(abas))
                    .total(total(abas))
                    .cnpj(cnpj(abas))
                    .ie(ie(abas))
                    .nome(nome(emitente))
                    .chaveDeAcesso(chaveDeAcesso(produtos))
                    .informacoesComplementares(informacoesComplementares(principal))
                    .produtos(produtos(produtos))
                    .build();

        } catch (IOException ex) {

            log.error("Erro ao acessar a SEFAZ-BA: {}", ex.getMessage(), ex);
            throw new ExceptionDefault("Não foi possível consultar a NFE no site da SEFAZ-BA");

        }

    }

    // --- Data ---------------------------------------------------------------
    private String data(Document abas) {

        // --- A SEFAZ devolve a data com o fuso colado no fim: 00/00/0000 00:00:00-03:00 ---
        String valor = indice(indice(abas.select("#NFe"), ".col-6", 0), "span", 3).html();
        return valor.length() > 6 ? valor.substring(0, valor.length() - 6) : "";

    }

    // --- Total --------------------------------------------------------------
    private String total(Document abas) {
        return indice(indice(abas.select("#NFe"), ".col-6", 0), "span", 5).html();
    }

    // --- CNPJ da loja -------------------------------------------------------
    private String cnpj(Document abas) {
        return primeiro(abas.select(".fixo-nfe-cpf-cnpj"), "span").html();
    }

    // --- Inscrição estadual da loja -----------------------------------------
    private String ie(Document abas) {
        return primeiro(abas.select(".fixo-nfe-iest"), "span").html();
    }

    // --- Nome da loja -------------------------------------------------------
    private String nome(Document emitente) {

        Elements spans = indice(emitente.select("#Emitente"), ".col-2", 0).select("span");

        // --- Algumas notas trazem o nome fantasia antes da razão social ---
        if (spans.size() > 1 && !spans.get(1).html().isBlank()) {
            return spans.get(1).html();
        }

        return indice(spans, 0).html();

    }

    // --- Chave de acesso ----------------------------------------------------
    private String chaveDeAcesso(Document produtos) {
        return primeiro(produtos, "#lbl_chave_acesso").html();
    }

    // --- Informações complementares -----------------------------------------
    private String informacoesComplementares(Document principal) {

        Elements itens = principal.select("li");
        return itens.size() > 4 ? itens.get(4).html() : "";

    }

    // --- Produtos -----------------------------------------------------------
    private List<ProdutoResponse> produtos(Document pagina) {

        List<ProdutoResponse> produtos = new ArrayList<>();

        for (Element produto : pagina.select(".table_produtos")) {
            produtos.add(ProdutoResponse.builder()
                    .ean(primeiro(indice(produto.select(".col-3"), 0), "span").html())
                    .nome(primeiro(produto, ".fixo-prod-serv-descricao span").html())
                    .qtd(primeiro(produto, ".fixo-prod-serv-qtd span").html())
                    .unidade(primeiro(produto, ".fixo-prod-serv-uc span").html())
                    .valor(primeiro(produto, ".fixo-prod-serv-vb span").html())
                    .build());
        }

        return produtos;

    }

    // --- Primeiro elemento que casa com o seletor ---------------------------
    private Element primeiro(Element raiz, String seletor) {

        Element elemento = raiz.selectFirst(seletor);
        if (elemento == null) {
            naoEncontrado(seletor);
        }
        return elemento;

    }

    private Element primeiro(Elements raiz, String seletor) {

        Element elemento = raiz.select(seletor).first();
        if (elemento == null) {
            naoEncontrado(seletor);
        }
        return elemento;

    }

    // --- N-ésimo elemento que casa com o seletor ----------------------------
    private Element indice(Elements raiz, String seletor, int indice) {
        return indice(raiz.select(seletor), indice);
    }

    private Element indice(Element raiz, String seletor, int indice) {
        return indice(raiz.select(seletor), indice);
    }

    private Element indice(Elements elementos, int indice) {

        if (elementos.size() <= indice) {
            naoEncontrado("elemento " + indice);
        }
        return elementos.get(indice);

    }

    private void naoEncontrado(String seletor) {

        log.error("Não encontrado na página da SEFAZ: {}", seletor);
        throw new ExceptionDefault("Página da SEFAZ-BA fora do formato esperado");

    }

}
