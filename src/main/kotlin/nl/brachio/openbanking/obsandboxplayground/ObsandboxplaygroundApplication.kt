package nl.brachio.openbanking.obsandboxplayground

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(Config::class)
class ObsandboxplaygroundApplication

fun main(args: Array<String>) {
    runApplication<ObsandboxplaygroundApplication>(*args){
        setBannerMode(Banner.Mode.OFF)
        webApplicationType = WebApplicationType.NONE
    }
}
