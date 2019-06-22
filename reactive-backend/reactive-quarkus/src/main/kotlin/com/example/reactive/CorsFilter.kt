package com.example.reactive

import java.io.IOException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider


@Provider
class CorsFilter : ContainerResponseFilter {

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext,
                        responseContext: ContainerResponseContext) {
        responseContext.headers.add(
                "Access-Control-Allow-Origin", "*")
        responseContext.headers.add(
                "Access-Control-Allow-Credentials", "true")
        responseContext.headers.add(
                "Access-Control-Allow-Headers",
                "origin, content-type, accept, authorization")
        responseContext.headers.add(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD")
    }
}