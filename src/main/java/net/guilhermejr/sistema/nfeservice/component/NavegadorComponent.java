package net.guilhermejr.sistema.nfeservice.component;

import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.nfeservice.exception.ExceptionDefault;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Navegador mínimo para o site da SEFAZ-BA, que é ASP.NET WebForms: cada aba da
 * nota é um postback do mesmo formulário, e não uma URL própria. Para trocar de
 * aba é preciso reenviar todos os campos ocultos da página atual (__VIEWSTATE e
 * companhia) somados ao nome do botão clicado, mantendo o cookie de sessão.
 */
@Log4j2
@Component
public class NavegadorComponent {

    @Value("${sistema.nfe.userAgent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36}")
    private String userAgent;

    @Value("${sistema.nfe.timeout:30000}")
    private Integer timeout;

    // --- Abre uma sessão, que carrega o cookie entre as requisições ----------
    public Connection sessao() {

        return Jsoup.newSession()
                .userAgent(userAgent)
                .timeout(timeout)
                .maxBodySize(0);

    }

    // --- Abre uma página --------------------------------------------------------
    public Document abrir(Connection sessao, String url) throws IOException {

        log.info("Abrindo {}", url);
        return sessao.newRequest(url).get();

    }

    // --- Clica em um botão e devolve a página resultante ---------------------
    public Document clicar(Connection sessao, Document pagina, String botao) throws IOException {

        Element elemento = botao(pagina, botao);
        Element formulario = elemento.closest("form");

        if (formulario == null) {
            log.error("Botão {} está fora de um formulário", botao);
            throw new ExceptionDefault("Página da SEFAZ-BA fora do formato esperado");
        }

        String acao = formulario.absUrl("action");
        if (acao.isBlank()) {
            acao = pagina.location();
        }

        log.info("Clicando em {}", botao);
        Connection requisicao = sessao.newRequest(acao).method(Connection.Method.POST);
        preencher(requisicao, formulario, elemento);
        return requisicao.post();

    }

    // --- Procura o botão pelo id, name, value ou alt -------------------------
    private Element botao(Document pagina, String botao) {

        for (Element candidato : pagina.select("button, input[type=submit], input[type=image], input[type=button]")) {
            if (botao.equals(candidato.id())
                    || botao.equals(candidato.attr("name"))
                    || botao.equals(candidato.attr("value"))
                    || botao.equals(candidato.attr("alt"))) {
                return candidato;
            }
        }

        log.error("Botão {} não encontrado na página da SEFAZ", botao);
        throw new ExceptionDefault("Página da SEFAZ-BA fora do formato esperado");

    }

    // --- Copia os campos do formulário para a requisição ---------------------
    private void preencher(Connection requisicao, Element formulario, Element clicado) {

        for (Element campo : formulario.select("input[name], select[name], textarea[name]")) {

            String nome = campo.attr("name");
            String tipo = campo.normalName().equals("input") ? campo.attr("type").toLowerCase() : campo.normalName();

            switch (tipo) {

                // --- Só o botão efetivamente clicado é enviado ---
                case "submit", "button", "image", "reset", "file" -> { }

                case "checkbox", "radio" -> {
                    if (campo.hasAttr("checked")) {
                        requisicao.data(nome, campo.hasAttr("value") ? campo.attr("value") : "on");
                    }
                }

                case "select" -> {
                    Element opcao = campo.selectFirst("option[selected]");
                    if (opcao == null) {
                        opcao = campo.selectFirst("option");
                    }
                    if (opcao != null) {
                        requisicao.data(nome, opcao.hasAttr("value") ? opcao.attr("value") : opcao.text());
                    }
                }

                case "textarea" -> requisicao.data(nome, campo.wholeText());

                default -> requisicao.data(nome, campo.attr("value"));

            }

        }

        if (clicado.hasAttr("name")) {

            String nome = clicado.attr("name");

            // --- As abas são input type=image, e o ASP.NET só reconhece o
            //     clique se vierem as coordenadas em vez do valor ---
            if ("image".equalsIgnoreCase(clicado.attr("type"))) {
                requisicao.data(nome + ".x", "0");
                requisicao.data(nome + ".y", "0");
            } else {
                requisicao.data(nome, clicado.attr("value"));
            }

        }

    }

}
