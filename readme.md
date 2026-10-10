# Mainframe Interface: Project 2 Starter

Welcome to the second project for SOFTENG 306. 

The project specification is available [on Canvas](https://canvas.auckland.ac.nz/courses/142477/pages/project-2-overview?module_item_id=3007942). The four deliverables for this project are:
* [Proposal](https://canvas.auckland.ac.nz/courses/142477/assignments/514280)
* [Presentation](https://canvas.auckland.ac.nz/courses/142477/assignments/514279)
* [Code](https://canvas.auckland.ac.nz/courses/142477/assignments/514277)
* [Documentation](https://canvas.auckland.ac.nz/courses/142477/assignments/514278)

## Team-Memebers

* *Daniel Peter - @DanielPeter06*
* *Kelvin Annan - @ingaKKest*
* *Neia Tererei - @rbntres*
* *Shenol Peiris - @nikopeiris*

## Building, Testing and Running LAVS

Requires a Java 17+ JDK (`java -version`). Maven does not need to be installed; use the included wrapper
(`mvnw.cmd` on Windows, `./mvnw` on macOS/Linux).

| Task | Command |
|---|---|
| Run the application | `mvnw.cmd javafx:run` |
| Run the unit tests (offline) | `mvnw.cmd test` |
| Tests + coverage report and 95% business-logic coverage check | `mvnw.cmd verify` (report: `target/site/jacoco/index.html`) |
| Also run the provided mainframe tests against the live server | `mvnw.cmd test -Plive` |

The mainframe URL is set in [lavs.properties](src/main/resources/lavs.properties) and can be overridden with
`-Dmainframe.baseUrl=...`.

### Code layout

* `lavs.ui` – JavaFX controllers (views are in `src/main/resources/lavs/ui`). No business logic.
* `lavs.service`, `lavs.validation`, `lavs.model`, `lavs.data` – business logic layer; must keep ≥95% line coverage.
* `lavs.app` – application start-up and wiring.
* `lavs.testutil.QueueStubConnection` (test) – fake mainframe connection that returns queued responses and records requests.

### Third-party libraries

* [OpenJFX](https://openjfx.io/) 21 – GPL v2 with Classpath Exception
* [OkHttp](https://square.github.io/okhttp/) and [json-simple](https://github.com/fangyidong/json-simple) – Apache 2.0 (provided)
* [JUnit 5](https://junit.org/junit5/) – EPL 2.0; [Mockito](https://site.mockito.org/) – MIT; [JaCoCo](https://www.jacoco.org/) – EPL 2.0

## Initial Repository Code

This repository initially contains the code for the mainframe interface and the unit tests. The following code is supplied:
* [mainframe](src/main/java/mainframe/client/): the interfaces for sending requests to and receiving responses from the mainframe. You will need to use the [Connection](src/main/java/mainframe/client/Connection.java) interface in your code. We have provided an implementation that connects to a web server in [HttpConnection](src/main/java/mainframe/client/HttpConnection.java).
* [tests](src/test/java/mainframe/client/): the unit tests. Most of these connect to the web server, but there is an [example unit test](src/test/java/mainframe/client/SimulateListCustomers.java) for connecting to a stub.
