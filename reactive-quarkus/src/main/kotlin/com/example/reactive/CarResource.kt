package com.example.reactive

import com.beust.klaxon.Klaxon
import com.example.reactive.service.DataService
import javax.inject.Inject
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.sse.Sse
import javax.ws.rs.sse.SseEventSink

val TIMEOUT = 30L

@Path("/cars")
class CarResource {

    @Inject
    lateinit var responseStream: CarStreamResponseOutput

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getCarsAsList() = DataService.getDataStream(TIMEOUT).toList().blockingGet()


    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun loadCarsAsOctetStream(): Response {
        return Response.ok().entity(responseStream).build()
    }

    @GET
    @Path("stream")
    @Produces("application/stream+json")
    fun loadCarsAsJsonStream(): Response {
        return Response.ok().entity(responseStream).build()
    }

    @GET
    @Path("sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun sse(@Context sse: Sse, @Context eventSink: SseEventSink) {
        DataService.getDataStream(TIMEOUT).subscribe({
            eventSink.send(sse.newEvent(Klaxon().toJsonString(it)))
        }, ::println, {
            eventSink.send(sse.newEventBuilder().data("done").build())
            eventSink.close()
        })
    }
}
