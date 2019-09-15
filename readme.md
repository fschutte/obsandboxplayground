This application was generated using https://start.spring.io

## Intro

Simple demonstration of consuming the UK Open Banking Directory.

Basically this is more or less a translation of the node js program provided here: https://github.com/OpenBankingUK/obdatat/tree/env-sandbox
into a basic Spring Boot application.

Technical specifications:
https://openbanking.atlassian.net/wiki/spaces/DZ/pages/23462203/Directory+Specifications

Step by step instructions:
https://openbanking.atlassian.net/wiki/spaces/DZ/pages/996999961/Open+Banking+Directory+Usage+-+eIDAS+release+Directory+Sandbox+-+DRAFT+V1.0

Steps required:
1. in the Open Banking Sandbox environment: create a Software Statement. Note the id and key materials for both TLS and Signing.
2. update the configuration in application.yml such that the id and paths to the keys/certificates are correct
3. run the application: it should (1) get an access token and (2) perform a SCIM Query to retrieve all ASPSP entries in the directory.



## Building and running

To run your application from command line you could do something like this:

```
mvn clean spring-boot:run
```

The output gives a list of the registered ASPSPs in the sandbox directory:

    1. Ozone Financial Technology Limited
    2. Open Banking Limited (A)
    3. AIB Group (UK) plc
    4. HSBC UK Bank Plc
    5. Starling Bank Limited
    6. Barclays Bank UK Plc
    7. Bank of Scotland PLC
    ...
