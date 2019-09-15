package nl.brachio.openbanking.obsandboxplayground

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "obapi")
class Config {
    lateinit var keypath: String
    lateinit var certpath: String
    lateinit var signkey: String
    lateinit var trustca: String
    lateinit var signkeyId: String
    lateinit var clientId: String
    lateinit var scope: String
    lateinit var audience: String
}
