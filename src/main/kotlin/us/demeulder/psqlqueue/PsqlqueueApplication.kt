package us.demeulder.psqlqueue

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PsqlqueueApplication

fun main(args: Array<String>) {
	runApplication<PsqlqueueApplication>(*args)
}
