# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Reads an NFC-e from the Bahia tax authority's website and returns it as JSON. Converted from the PHP/Laravel service of the same name, which wrapped the `guilhermejr/busca-nfe-ba` Composer library.

| | |
|---|---|
| Port | `9011` |
| Context path | `/nfe-service` |
| Role required | none |

Part of a personal microservices system; sibling repos live at `../sistema-*`.

## It is deliberately unauthenticated

Unlike the domain services, this one does **not** depend on `seguranca-jwt` and has **no gateway route** — the same arrangement as `notificacao-service`. Its only caller is `supermercado-service`, from a `@Scheduled` task with no logged-in user, so there is no token to forward. Adding the library would 401 that caller.

If a gateway route is ever added, the service becomes publicly reachable and needs authentication first.

## The SEFAZ site is ASP.NET WebForms

This is the whole difficulty of the service, and the reason it is not a plain HTTP call.

The tabs of a nota are **not separate URLs** — they are postbacks of one form. Reading a nota means three requests that share a session cookie: open the QR Code URL, submit `Visualizar em Abas`, then from that page submit `btn_aba_emitente` and `btn_aba_produtos`.

Two traps, both of which cost time when the PHP was ported:

- **The tab buttons are `input type="image"`.** ASP.NET only registers the click if the request carries `btn_aba_emitente.x` and `.y`; sending `btn_aba_emitente=<value>` like a normal submit button is silently ignored and the server returns the *same* page. The symptom is subtle — no error, just the NFe tab again, and extraction fails later on a missing `#Emitente`.
- **Every hidden field must be echoed back.** `__VIEWSTATE`, `__EVENTVALIDATION` and friends are how the server knows which nota is being viewed. `NavegadorComponent.preencher` copies them; dropping one gets you an error page.

`NavegadorComponent` is the BrowserKit equivalent (form mechanics); `NFEService` is the DomCrawler equivalent (extraction).

## The selectors are positional and will break

Extraction is index-based — `#NFe .col-6` first match, `span` at index 3 for the date, `li` at index 4 for the complementary information. This is inherited from the PHP verbatim, on purpose: the values feed `supermercado-service`, which parses `data` with a **strict** `dd/MM/uuuu HH:mm:ss` and `total` as `1.234,56`. Changing what a selector returns changes what lands in that database.

When SEFAZ redesigns a page, expect `Página da SEFAZ-BA fora do formato esperado`. That message means a selector missed, not that the nota is bad — the log line above it names the selector.

Note `data` strips the last 6 characters, because the page renders `08/08/2020 14:58:24-03:00`.

A nota SEFAZ does not recognize is **not** an error: the page carries `#lblInformacao` and the service answers `200` with `retornou: false`. Callers branch on that field.

## Verifying a change

There is no fixture: the only real check is against the live site. A nota that still resolves:

```bash
curl "http://localhost:9011/nfe-service/29200835133777000109650010000001231796633581%7C2%7C1%7C1%7CB75D96ACC1EA7E3AAF163C0C0F01D547DCACF815"
```

The pipes **must** be sent as `%7C` — Tomcat rejects raw `|` in a path. Feign encodes them on its own, so `supermercado-service` needs no special handling.

## Configuration comes from outside

`application.yml` only bootstraps `spring.config.import`, which pulls from Vault (`secret/application`) and the config server. Both must be reachable or the service will not start.

A service also needs **its own secret** at `secret/<service-name>`, holding at minimum `eurekaHostname`. That key is *not* in the shared `secret/application` — every service carries its own, and the per-service yml in the config repo reads it with no default.

A brand-new service therefore fails on first run even with a perfectly good config-server file, and the error names Eureka rather than Vault:

```
Vault location [secret/nfe-service] not resolvable: Not found
...
Could not resolve placeholder 'eurekaHostname' in value "${eurekaHostname}"
```

Create it in **both** the dev and the prod Vault, mirroring a sibling (`vault read secret/notificacao-service` — the backend is KV v1, since `spring.cloud.vault.generic` is enabled).

`VAULT_TOKEN` is required and **has no default**. Without it Spring sends the literal string `${VAULT_TOKEN}` to Vault, gets a 403 that Spring Cloud Vault swallows (`fail-fast` is off), and startup fails much later with a misleading `${someProperty} is malformed`.

To run locally without Vault and the config server, replace the configuration entirely rather than trying to override `spring.config.import` — a command-line `--spring.config.import=` does not win:

```bash
java -jar target/*.jar --spring.config.location=file:local.properties
```

with `spring.cloud.config.enabled=false`, `spring.cloud.vault.enabled=false` and `eureka.client.enabled=false` in that file.

## Building and running

Java **21 only**; the Homebrew default JDK on this machine is newer.

```bash
export JAVA_HOME=/Users/guilhermejr/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home
./mvnw clean package
```

There is no ModelMapper here — the responses are built directly — so the JDK 21 ceiling is about matching the sibling services, not about ByteBuddy.

## Deploying

`git push origin main` **is** the deploy: a `post-receive` hook on the VPS checks out, runs `mvn clean package` inside a throwaway `maven:3.9-amazoncorretto-21` container, builds the image and restarts it via docker compose.

This repository is new — the hook, the bare repo and the compose entry on the VPS have to exist before the first push does anything.

The PHP service it replaces keeps its own names (`sistema-nfe-ba-service`, container `nfe-ba-service`, port 8000), so the two **can** run side by side during the cutover. Switching over is one Vault change: point `NFEBAHost` at `http://nfe-service:9011/nfe-service` — note the context path, which the PHP service did not have.
