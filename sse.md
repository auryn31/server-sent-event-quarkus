# Server Sent Events vs Stream

In meinem letzten Beitrag erläuterte ich, wieso man Streaming statt Blocking verwenden sollte. Was ist aber nun mit Server Sent Events?

Ich werde für den Beitrag den Code auf einem GitHub-Repo zur Verfügung stellen.

## Server Sent Events

Wikipedia definiert Server Sent Events (SSE) folgendermaßen:

> Server-sent events (SSE) is a server push technology enabling a browser to receive automatic updates from a server via HTTP connection. The Server-Sent Events EventSource API is standardized as part of HTML5 by the W3C.
>
> -- <cite>[Wikipedia](https://en.wikipedia.org/wiki/Server-sent_events)</cite>

SSE sind ähnlich zu Websockets, doch sind diese Unidirektional. Bei klassischen Websockets kann der Client auch Daten an den Server senden. Dies ist bei SSE nicht der Fall. Der Client kann Daten über *HTTP* nur empfangen. Dieser Standard wird von allen Browsern (sofern man *IE* nicht als Browser betrachtet) unterstützt und wurde 2009 von [W3C](https://www.w3.org/TR/2009/WD-eventsource-20090421/) definiert.

### Server

Um mit dem Server SSE auszuliefern, müssen wir einen entsprechenden Endpunkt definieren. Arbeiten wir mit dem JAX-RS standard, können wir den MediaType `SERVER_SENT_EVENTS` verwenden. Dann sieht der Endpunkt in *Quarkus* wie folgt aus:

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

Hier beziehe ich mich wieder auf den [Beitrag](https://blogs.itemis.com/de/how-to-reactive-stream-mit-spring-boot-und-rxjava-in-kotlin), in dem ich über Observables schreibe. Dort ist auch beschrieben, wie Observables verwendet werden. Den Code dazu findest du [hier](https://github.com/auryn31/spring-async-rest-example).

### Response Server

Der Response bei dem oberhalb beschriebenen Endpunkt sieht dann wie folgt aus:

```json
data:{"company" : "Nova", "id" : 95553, "model" : "VW"}

data:{"company" : "Phaeton", "id" : 15805, "model" : "Toyota"}

data:{"company" : "Vaneo", "id" : 7641, "model" : "Mercedes"}

data:{"company" : "Pinto", "id" : 14267, "model" : "VW"}

data:{"company" : "Kuga", "id" : 57124, "model" : "Toyota"}

data:{"company" : "Opa", "id" : 33495, "model" : "Toyota"}

data:{"company" : "iMIEV", "id" : 36266, "model" : "VW"}

data:{"company" : "Vaneo", "id" : 55390, "model" : "Fiat"}

data:{"company" : "Phaeton", "id" : 79631, "model" : "VW"}

data:{"company" : "e-tron", "id" : 80601, "model" : "Toyota"}

data:done
```

Diese Daten können wir mit dem Client lesen und auswerten.

### Client

Wie man den Standard nutzt, wird [hier](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events) beschrieben. Will man es zum Beispiel mit *Vue.js* verwenden, braucht man keine extra Bibliothek einzubinden. Einen SSE Endpunkt kann man mit *TypeScript* im Frontend beispielsweise so verwenden:

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

Hier sehen wir, dass der REST Endpunkt `http://localhost:8080/cars/sse` angesprochen wird und auf jedes Event die empfangenen Daten in den *Store* commited werden. Sendet der Server als Daten 'done', wird das lauschen geschlossen, da der Browser sonst nach *3s* Wartezeit den Endpunkt wieder öffnet und versucht neue Daten zu empfangen. Da wir aber alle Daten haben, brauchen wir nicht weiter zu lauschen.

### Verwendung mit Spring Boot

Bei *Spring Boot* gibt es den Standard nicht von Haus aus. Hier gibt es nur den `APPLICATION_STREAM_JSON_VALUE`, welchen ich auch in dem Beitrag vorher verwendete. Doch dieser ist kein *MIME*-Standard und somit nicht von Haus aus von den Browsern unterstützt. Um den JSON-Stream zu verwenden, wird wieder eine Bibliothek benötigt, mit welcher der Response geparst werden kann. Ich habe in vorherigen Beitrag `oboe.js` verwendet.

Nutzen wir nun allerdings SSE, müssen wir, wie wir oben gesehen haben, keine extra Bibliothek verwenden. Also wäre es sinnvoll, auch diesen Standard mit *Spring Boot* zu verwenden. Ob es sich aus Geschwindigkeitssicht lohnt, betrachten wir später.

Der Endpunkt lässt sich auch *Spring Boot* recht einfach definieren, auch wenn es keinen *MIME*-Standard dafür gibt. Hier kann man ihn entweder selbst definieren oder einfach weglassen. Ich mache für das Beispiel einfachheitshalber zweiteres.

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

Um die verschiedenen Arten zu vergleichen, wird der Endpunkt von *Quarkus* mit *K6* getestet. Es werden *10s* so viele Anfragen wie Möglich mit 10 Usern gesendet. Es wird gegen Blocking, Stream und SSE getestet. Weiterhin werden zwei Arten getestet. Einmal Daten, die sofort zur Verfügung stehen, also kein Delay besitzen und einmal Daten, die ein Laden simulieren und mit einem Delay von je *30ms* bereitgestellt werden. Da der Endpunkt immer 10 Datensätze erzeugt, wird sich das Delay bei den langsamen Daten auf *300ms* belaufen.

| Typ | RPS schnell | RPS langsam |
| --------- | ----------- | ------ |
| Blocking | 12873 | 32.89 |
| Stream | 9708 | 32.19 |
| SSE | 8936 | 33.99 |

Visualisiert sieht das wie folgt aus:

![fast](img/&#32;rps_sse_fast.jpeg)

![slow](img/&#32;rps_sse_slow.jpeg)

Hier ist gut zu erkennen, dass es sich bei Daten, die sofort zur Verfügung stehen, nicht lohnt auf Streaming zu setzen. Das hat den einfachen Grund, dass Daten, die als ein Block übertragen werden können, schon vom Server zerlegt verschickt werden. Somit bringt man sich selbst overhead in die Daten, die nicht notwendig sind, und verlangsamt die Übertragungsraten.

Sind die Daten allerdings langsam, hat Streaming mehrere Vorteile. Der Client kann die Daten schon verarbeiten, bevor er alle hat, und die Übertragung geht schneller, da einige Pakete schon beim Client sind, bevor alle Daten zur Verfügung stehen. So ist das Übertragen der letzten Daten schneller. Hierbei kommt es natürlich erheblich auf die Netzverbindung an. Auf einem lokalen Rechner ist auch das blockende System beim Laden der Daten nahezu gleich schnell oder sogar schneller. Hier ist das Herunterladen der Daten deutlich schneller, als über das Internet.

Spannend ist das Streaming aber auch in schnellen Netzwerken, wenn die Daten weiterverarbeitet werden. Da hier die Daten bereits verarbeitet werden könne, bevor der liefernde Dienst alle Daten bereitgestellt hat. Somit wird die Serverlast und die Netzwerklast verteilt.

## Fazit

SSE ist ein spannender Standard, welcher bereits seit vielen Jahren existiert. Trotz dessen wird er leider viel zu wenig verwendet.
SSE bietet gegenüber dem normalen JSON Stream einige Vorteile:

- weitere Informationen in Feldern
- automatische reconnection vom Browser
- gleich schnell zu JSON Streaming

Wird mit einem Frontend gearbeitet, ergibt es durchaus Sinn, statt JSON Streaming auf SSE zu setzen. Da hier weitere Informationen mitgesendet werden können. Außerdem öffnet der Browser Connections automatisch nach einem TIMEOUT, sofern diese geschlossen oder unterbrochen werden. Ein Nachteil ist natürlich, dass SSE einen größeren Payload hat, welcher mit Übertragen wird. Dieser ist allerdings sehr klein und macht die Vorteile wieder wett. Zwischen verschiedenen Micro-Services sehe ich noch keinen großen Vorteil auf SSE, statt JSON Stream zu setzen. Hier würde ein klassisches Stream-System wie Kafka wohl mehr Vorteile bieten.
