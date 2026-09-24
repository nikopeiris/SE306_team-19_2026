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

You must also update this file to contain either the instructions for running your code or a link to the instructions.

## Initial Repository Code

This repository initially contains the code for the mainframe interface and the unit tests. The following code is supplied:
* [mainframe](src/main/java/mainframe/client/): the interfaces for sending requests to and receiving responses from the mainframe. You will need to use the [Connection](src/main/java/mainframe/client/Connection.java) interface in your code. We have provided an implementation that connects to a web server in [HttpConnection](src/main/java/mainframe/client/HttpConnection.java).
* [tests](src/test/java/mainframe/client/): the unit tests. Most of these connect to the web server, but there is an [example unit test](src/test/java/mainframe/client/SimulateListCustomers.java) for connecting to a stub.
