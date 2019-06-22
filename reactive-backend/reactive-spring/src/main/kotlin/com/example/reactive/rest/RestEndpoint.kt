package com.example.reactive.rest

import com.beust.klaxon.Klaxon
import com.example.reactive.dataprovider.DataProvider
import com.example.reactive.model.Car
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.time.LocalTime
import javax.servlet.http.HttpServletResponse


@Controller
class RestEndpoint {

    @Autowired
    lateinit var dataProvider: DataProvider

    @Autowired
    lateinit var streamResponse: CarStreamResponseOutput

    @GetMapping(path = ["cars"], produces = [MediaType.APPLICATION_STREAM_JSON_VALUE])
    @ResponseBody
    @CrossOrigin(origins = ["http://localhost:8081"])
    fun getCarsAsStream(): StreamingResponseBody {
        return streamResponse
    }

    @GetMapping(path = ["cars"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    @CrossOrigin(origins = ["http://localhost:8081"])
    fun getCarsAsJson(): List<Car> {
        return dataProvider.getDataStream().toList().blockingGet()
    }

    @GetMapping(path = ["cars/stream"], produces = [MediaType.APPLICATION_STREAM_JSON_VALUE])
    @ResponseBody
    @CrossOrigin(origins = ["http://localhost:8081"])
    fun getCarsAsStreamWithExtraEndpointForLocust(): StreamingResponseBody {
        return streamResponse
    }


    @GetMapping("/cars/sse")
    @ResponseBody
    @CrossOrigin(origins = ["http://localhost:8081"])
    fun sse(response: HttpServletResponse): SseEmitter {
        val emitter = SseEmitter()
        dataProvider.getDataStream().subscribe({
            emitter.send(SseEmitter.event().data(Klaxon().toJsonString(it), MediaType.APPLICATION_JSON))
        }, ::println, {
            emitter.send(SseEmitter.event().data("done"))
            emitter.complete()
        })
        return emitter
    }
}
