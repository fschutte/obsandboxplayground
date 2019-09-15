package nl.brachio.openbanking.obsandboxplayground


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import io.netty.handler.ssl.SslContextBuilder
import mu.KotlinLogging
import nl.brachio.openbanking.obsandboxplayground.SecurityUtil.readPrivateKeyFile
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.io.File
import java.security.PrivateKey
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

private val OPENBANKING_API_BASE_URL = "https://matls-api.openbankingtest.org.uk"
private val OPENBANKING_TOKEN_ENDPOINT = "https://matls-sso.openbankingtest.org.uk/as/token.oauth2"


private val logger = KotlinLogging.logger {}

// WebClient.Builder is autowired by default by Spring
@Component
class ApplicationRunnerBean(val config: Config, val webClientBuilder: WebClient.Builder) : ApplicationRunner {

    val objectMapper = ObjectMapper().registerKotlinModule()
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val clientId: String by lazy { config.clientId }
    private val scope: String by lazy {config.scope }
    private val signKey: PrivateKey by lazy { readPrivateKeyFile(config.signkey) }
    private val signKeyId: String by lazy {config.signkeyId }
    private val audience: String by lazy {config.audience }

    private val webClient: WebClient by lazy {
        val sslContext = SslContextBuilder.forClient()
                .keyManager(
                        File(config.certpath),
                        File(config.keypath))
                .trustManager(File(config.trustca))  // Deze is van belang, want certificaat van server is Open Banking certificate en zit niet in default truststore
                .build()

        val httpClient = HttpClient.create().secure { s -> s.sslContext(sslContext) }
        val httpConnector = ReactorClientHttpConnector(httpClient)

        // trace http request/response
        val consumer = Consumer<ClientCodecConfigurer> { configurer ->
            configurer.defaultCodecs().enableLoggingRequestDetails(true)
        }

        webClientBuilder
                .clientConnector(httpConnector)
                .baseUrl(OPENBANKING_API_BASE_URL)
                .exchangeStrategies(ExchangeStrategies.builder().codecs(consumer).build())
                .build()

    }


    override fun run(arg0: ApplicationArguments) {

        val claimsSet = JWTClaimsSet.Builder()
                .issuer(clientId)    // this is the software statement id (aka client id)
                .subject(clientId)
                .audience(audience)
                .expirationTime(Date(Date().getTime() + 1000 * 60 * 10))
                .issueTime(Date())
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", scope)
                .build();

        logger.info("\nClaim Set : ${claimsSet.toJSONObject()}")

        // Create RSA-signer with the private key
        val signer = RSASSASigner(signKey)

        // Prepare JWS object with simple string as payload
        val jwsObject = JWSObject(
                JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signKeyId)
                        .type(JOSEObjectType.JWT)
                        .build(),
                Payload(claimsSet.toJSONObject()))

        // Compute the RSA signature
        jwsObject.sign(signer)

        // To serialize to compact form, produces something like
        // eyJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.IRMQENi4nJyp4er2L
        // mZq3ivwoAjqa1uUkSBKFIX7ATndFF5ivnt-m8uApHO4kfIFOrW7w2Ezmlg3Qd
        // maXlS9DhN0nUk_hGI3amEjkKd0BWYCB8vfUbUv0XGjQip78AI4z1PrFRNidm7
        // -jPDm5Iq0SZnjKjCNS5Q15fokXZc8u0A
        val s = jwsObject.serialize()

        logger.info("\nSerialized jws: $s")

        val doneSignal = CountDownLatch(1)

        logger.info("start()")
        callOauthToken(s)  // step 1
                .map { resp ->
                    val json = resp.body ?: throw RuntimeException("no body found")
                    json["access_token"].textValue()
                }
                .flatMap(this::callGetASPSPs)
                .subscribe({ resp ->
                    logger.info("*** GOT RESPONSE: ${resp}")
                    val json = resp.body ?: throw RuntimeException("no body found")

                    val scimObject = objectMapper.treeToValue<ScimOBAccountPaymentServiceProvidersResponse>(json)

                    scimObject.resources.forEachIndexed { index, resource ->
                        println("${index+1}. ${resource.organisation.organisationCommonName}")
                    }
//                    println("*** ${scimObject.resources[0].organisation.organisationCommonName}")


                    doneSignal.countDown()
                }, { err ->
                    err.printStackTrace()
                    doneSignal.countDown()
                })

        doneSignal.await()
    }

    private fun callOauthToken(serializedJwt: String): Mono<ResponseEntity<JsonNode>> {
        val httpMethod = HttpMethod.POST
        val path = OPENBANKING_TOKEN_ENDPOINT

        val map = LinkedMultiValueMap<String, String>().apply {
            add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            add("grant_type", "client_credentials")
            add("client_id", clientId)
            add("client_assertion", serializedJwt)
            add("scope", scope)
        }

        return webClient
                .method(httpMethod)
                .uri(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(map))
                .exchange()
                .flatMap { resp ->
                    println("ClientResponse: $resp")
                    resp.toEntity(JsonNode::class.java)
                }

    }

    private fun callGetASPSPs(accessToken: String): Mono<ResponseEntity<JsonNode>> {
        val httpMethod = HttpMethod.GET
        val path = "/scim/v2/OBAccountPaymentServiceProviders/"

        return webClient
                .method(httpMethod)
                .uri(path)
                .header("Authorization", "Bearer $accessToken")
                .exchange()
                .flatMap { resp ->
                    println("ClientResponse: $resp")
                    resp.toEntity(JsonNode::class.java)
                }

    }

}


data class ScimOBAccountPaymentServiceProvidersResponse(
        val resources: List<Resource>
)

data class Resource(
        @get:JsonProperty("urn:openbanking:organisation:1.0") val organisation: Organisation
)

data class Organisation(
        val organisationCommonName: String
)