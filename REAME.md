# Server Sent Events vs Stream

In my last post, I explained why you should use streaming instead of blocking. But what about Server Sent Events?

I will provide the code for the post on a GitHub repo.

## Server Sent Events

Wikipedia defines Server Sent Events (SSE) as follows:

> Server-sent events (SSE) is a server push technology enabling a browser to receive automatic updates from a server via HTTP connection. The Server-Sent Events EventSource API is standardized as part of HTML5 by the W3C.
>
> -- <cite>[Wikipedia](https://en.wikipedia.org/wiki/Server-sent_events)</cite>

SSE are similar to websockets, but they are unidirectional. With classic websockets, the client can also send data to the server. This is not the case with SSE. The client can only receive data via *HTTP*. This standard is supported by all browsers (unless *IE* is considered a browser) and was defined in 2009 by [W3C](https://www.w3.org/TR/2009/WD-eventsource-20090421/).

### Server

To deliver SSE with the server, we need to define an appropriate endpoint. If we work with the JAX-RS standard, we can use the MediaType `SERVER_SENT_EVENTS`. Then the endpoint in *Quarkus* looks like this:

```java
@Path("/cars")
class CarResource {

    @Inject
    lateinit var responseStream: CarStreamResponseOutput

    @GET
    @Path("sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun getSseCars(@Context sse: Sse, @Context eventSink: SseEventSink) {
        DataService.getDataStream(TIMEOUT).subscribe({
            eventSink.send(sse.newEvent(Klaxon().toJsonString(it)))
        }, ::println, {
            eventSink.send(sse.newEventBuilder().data("done").build())
            eventSink.close()
        })
    }
}
```

Here I refer again to the [article](https://blogs.itemis.com/de/how-to-reactive-stream-mit-spring-boot-und-rxjava-in-kotlin), in which I write about observables. It also describes how observables are used. You can find the code [here](https://github.com/auryn31/spring-async-rest-example).

### Response Server

The response at the endpoint described above then looks as follows:

```json
data:{"company" : "Audi", "id" : 65868, "model" : "TT Coupé"}

data:{"company" : "Fiat", "id" : 49782, "model" : "TT Coupé"}

data:{"company" : "Kia", "id" : 5437, "model" : "Phaeton"}

data:{"company" : "Toyota", "id" : 26772, "model" : "Nova"}

data:{"company" : "VW", "id" : 38366, "model" : "Vaneo"}

data:{"company" : "Audi", "id" : 82640, "model" : "Pinto"}

data:{"company" : "Toyota", "id" : 67372, "model" : "Phaeton"}

data:{"company" : "Fiat", "id" : 15362, "model" : "Pinto"}

data:{"company" : "Fiat", "id" : 90028, "model" : "Opa"}

data:{"company" : "Chevrolet", "id" : 25461, "model" : "e-tron"}

data:done
```

We can read and evaluate this data with the client.

### Client

How to use the default is described [here](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events). For example, if you want to use it with *Vue.js*, you don't need to include an extra library. An SSE endpoint can be used with *TypeScript* in the frontend like this:

```TypeScript
let evtSource = new EventSource("http://localhost:8080/cars/sse");
evtSource.onmessage = function(e: any) {
    if(e.data == "done") {
        evtSource.close();
    } else {
        commitAsyncCar(store, JSON.parse(e.data));
    }
};
```

Here we see that the REST endpoint `http://localhost:8080/cars/sse` is addressed and the received data is committed to the *Store* on each event. If the server sends as data 'done', the listening is closed, because otherwise the browser opens the endpoint again after *3s* waiting time and tries to receive new data. But since we have all the data, we don't need to listen any further.

### Use with Spring Boot

With *Spring Boot* the standard does not exist by default. Here there is only the `APPLICATION_STREAM_JSON_VALUE`, which I also used in the post before. But this is not a *MIME* standard and therefore not supported by the browsers. To use the JSON stream, you need a library to parse the response. I used `oboe.js` in previous post.

But if we use SSE now, as we saw above, we don't have to use an extra library. So it would make sense to also use this standard with *Spring Boot*. Whether it's worth it from a speed point of view, we'll see later.

The endpoint can also be defined quite easily with *Spring Boot*, even if there is no *MIME* standard for it. Here you can either define it yourself or simply omit it. For the sake of simplicity, I'll do the second for the example.

```java
@Controller
class RestEndpoint {

    @Autowired
    lateinit var dataProvider: DataProvider

    @GetMapping("/cars/sse")
    fun getSseCars(response: HttpServletResponse): SseEmitter {
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
```

Auch hier sieht der Response, wie bei dem oberen Beispiel aus.

## Performance

To compare the different species, the endpoint of *Quarkus* is tested with *K6*. As many *10s* requests as possible are sent with 10 users. It is tested against blocking, stream and SSE. Furthermore two types are tested. One is data that is immediately available, i.e. has no delay and the other is data that simulates a load and is provided with a delay of *30ms* each. Since the endpoint always generates 10 data sets, the delay for the slow data will be *300ms*.

| Typ | RPS fast | RPS slow |
| --------- | ----------- | ------ |
| Blocking | 12873 | 32.89 |
| Stream | 9708 | 32.19 |
| SSE | 8936 | 33.99 |

Visualisiert sieht das wie folgt aus:

![fast](img/&#32;rps_sse_fast.jpeg)

![slow](img/&#32;rps_sse_slow.jpeg)

It is easy to see here that streaming is not worthwhile for data that is immediately available. The simple reason for this is that data that can be transferred as a block is already sent by the server in disassembled form. Thus one brings oneself overhead into the data, which are not necessary, and slows down the transfer rates.

However, if the data is slow, streaming has several advantages. The client can already process the data before it has all of them, and the transmission is faster because some packets are already at the client before all of the data is available. This makes the transmission of the last data faster. Of course, the network connection is very important. On a local computer, the blocking system is also almost as fast or even faster when loading the data. Downloading the data is much faster here than via the Internet.

However, streaming is also exciting in fast networks when the data is further processed. Since the data can already be processed here before the delivering service has provided all the data. This distributes the server load and the network load.

## Conclusion

SSE is an exciting standard that has existed for many years. Nevertheless, it is unfortunately used far too little.
SSE offers some advantages over the normal JSON stream:

- more information in fields
- automatic reconnection from browser
- equal fast to JSON Streaming

If you are working with a frontend, it makes sense to use SSE instead of JSON Streaming. Because more information can be sent here. In addition, the browser opens connections automatically after a TIMEOUT if they are closed or interrupted. A disadvantage is, of course, that SSE has a larger payload, which is transmitted with the TIMEOUT. However, this is very small and makes up for the advantages. Between different micro services I don't see a big advantage on SSE instead of setting JSON Stream. Here a classic stream system like Kafka would probably offer more advantages.
